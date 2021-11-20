package blockrenderer;

import org.apache.commons.cli.*;
import se.llbit.chunky.block.BlockSpec;
import se.llbit.chunky.block.MinecraftBlockProvider;
import se.llbit.chunky.chunk.BlockPalette;
import se.llbit.chunky.renderer.postprocessing.PreviewFilter;
import se.llbit.chunky.renderer.projection.ProjectionMode;
import se.llbit.chunky.renderer.scene.Camera;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.resources.OctreeFileFormat;
import se.llbit.chunky.resources.TexturePackLoader;
import se.llbit.math.Octree;
import se.llbit.math.Ray;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;

public class BlockRenderer {
    public final static int WIDTH = 256;
    public final static int HEIGHT = 256;

    private final static ThreadLocal<RenderState> threadState = ThreadLocal.withInitial(RenderState::new);

    private static class RenderState {
        public Scene scene;
        public Octree octree;
        public Camera cam;
        public Ray ray;

        public RenderState() {
            scene = new Scene();
            scene.setBiomeColorsEnabled(false);
            octree = new Octree("PACKED", 1);
            cam = scene.camera();
            ray = new Ray();
        }
    }

    public enum Orientation {
        IsoTopWestNorth(45, 45, 180),
        IsoBottomEastSouth(-45, 225, 0),
        IsoZeros(0, 0, 0);

        double yaw, pitch, roll;
        Orientation(double yaw, double pitch, double roll) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
        }

        void setCamera(Camera cam) {
            cam.setView(Math.toRadians(yaw), Math.toRadians(pitch), Math.toRadians(roll));
        }
    }

    public static BufferedImage renderBlock(int block, BlockPalette palette, Orientation orientation) {
        RenderState state = threadState.get();

        state.octree.set(block, 0, 0, 0);

        state.cam.setProjectionMode(ProjectionMode.PARALLEL);
        state.cam.getPosition().set(0.5, 0.5, 0.5);
        state.cam.setFoV(2);
        orientation.setCamera(state.cam);

        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        WritableRaster raster = img.getRaster();

        double halfWidth = WIDTH / (2.0 * HEIGHT);
        double invHeight = 1.0 / HEIGHT;
        double[] pixel = new double[4];
        double[] zeroPixel = new double[4];

        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                state.cam.calcViewRay(state.ray,
                        -halfWidth + x * invHeight,
                        -0.5 + y * invHeight);
                if (state.octree.enterBlock(state.scene, state.ray, palette)) {
                    pixel[0] = state.ray.color.x;
                    pixel[1] = state.ray.color.y;
                    pixel[2] = state.ray.color.z;
                    pixel[3] = 1;
                    PreviewFilter.INSTANCE.processPixel(pixel);
                    for (int i = 0; i < pixel.length; i++) pixel[i] *= 255;
                    raster.setPixel(x, y, pixel);
                } else {
                    raster.setPixel(x, y, zeroPixel);
                }
            }
        }

        return img;
    }

    public static void main(String[] args) throws IOException, ParseException {
        Options options = new Options();
        options.addOption("t", "textures", true, "Path to the texture pack to use.");
        options.addRequiredOption("i", "input", true, "Path to the input octree to take blocks from.");
        options.addOption("o", "output", true, "Output folder path.");
        options.addOption(null, "threads", true, "Number of threads to use while rendering.");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        BlockSpec.blockProviders.add(new MinecraftBlockProvider());
        if (cmd.hasOption("t")) {
            TexturePackLoader.loadTexturePacks(cmd.getOptionValue("t"), false);
        }

        File outputFolder;
        if (cmd.hasOption("o")) {
            outputFolder = new File(cmd.getOptionValue("o"));
        } else {
            outputFolder = new File("./out/");
        }
        if (!outputFolder.mkdirs()) {
            throw new RuntimeException("Failed to create output folder.");
        }

        OctreeFileFormat.OctreeData data = OctreeFileFormat.load(
                new DataInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(
                        cmd.getOptionValue("i"))))), "PACKED");

        int size = data.palette.getPalette().size();

        ForkJoinPool pool;
        if (cmd.hasOption("threads")) {
            pool = new ForkJoinPool(Integer.parseInt(cmd.getOptionValue("threads")));
        } else {
            pool = new ForkJoinPool();
        }

        AtomicInteger progress = new AtomicInteger(0);
        ReentrantLock updateLock = new ReentrantLock();
        long[] lastUpdate = new long[] {0};
        long startTime = System.currentTimeMillis();

        pool.submit(() -> IntStream.range(0, size).parallel().forEach(i -> {
            BufferedImage out = new BufferedImage(BlockRenderer.WIDTH * 2, BlockRenderer.HEIGHT, BufferedImage.TYPE_INT_ARGB);
            Graphics outGraphics = out.getGraphics();

            BufferedImage img = BlockRenderer.renderBlock(i, data.palette, BlockRenderer.Orientation.IsoTopWestNorth);
            outGraphics.drawImage(img, 0, 0, null);

            BufferedImage img2 = BlockRenderer.renderBlock(i, data.palette, BlockRenderer.Orientation.IsoBottomEastSouth);
            outGraphics.drawImage(img2, BlockRenderer.WIDTH, 0, null);

            outGraphics.dispose();

            try {
                File outF = new File(outputFolder, String.format("block_%d_%s.png", i, data.palette.get(i).name.replace(':', '_')));
                OutputStream stream = new BufferedOutputStream(new FileOutputStream(outF));
                ImageIO.write(out, "PNG", stream);
                stream.close();

                int z = progress.incrementAndGet();
                long time = System.currentTimeMillis();
                if (time > (lastUpdate[0] + 100) && updateLock.tryLock()) {
                    lastUpdate[0] = time;
                    double t = time - startTime;
                    t /= 1000;
                    double eta = ((t / z) * size) - t;
                    System.out.printf("%d / %d\t\t%.2f %%\t\tET: %d min, %d sec\tETA: %d min, %d sec\r",
                            z, size, 100 * (double) z / size,
                            (int) (t / 60), (int) (t % 60), (int) (eta / 60), (int) (eta % 60));
                    updateLock.unlock();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        })).join();
    }
}
