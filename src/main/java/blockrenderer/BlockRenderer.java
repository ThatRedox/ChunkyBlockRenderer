package blockrenderer;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import se.llbit.chunky.block.BlockSpec;
import se.llbit.chunky.block.MinecraftBlockProvider;
import se.llbit.chunky.chunk.BlockPalette;
import se.llbit.chunky.renderer.postprocessing.GammaCorrectionFilter;
import se.llbit.chunky.renderer.postprocessing.SimplePixelPostProcessingFilter;
import se.llbit.chunky.renderer.projection.ProjectionMode;
import se.llbit.chunky.renderer.scene.Camera;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.renderer.scene.biome.BiomeStructure;
import se.llbit.chunky.resources.OctreeFileFormat;
import se.llbit.chunky.resources.ResourcePackLoader;
import se.llbit.math.Octree;
import se.llbit.math.Ray;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.*;
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;

@Parameters(commandDescription = "Render all blocks extracted from an existing octree file.")
class CommandOctree {
    @Parameter(names = {"-t", "--textures"}, description = "Path to the texture pack to use.")
    public File textures;

    @Parameter(names = {"-i", "--input"}, description = "Path to the input octree to take blocks from.", required = true)
    public String input;

    @Parameter(names = {"-o", "--output"}, description = "Output folder path.")
    public String output = "./out/";

    @Parameter(names = {"--threads"}, description = "Number of threads to use while rendering.")
    public Integer threads;
}

@Parameters(commandDescription = "Render all blocks extracted from an existing octree file.")
class CommandBlock {
    @Parameter(names = {"-t", "--textures"}, description = "Path to the texture pack to use.")
    public File textures;

    @Parameter(description = "Blockstate to render", required = true)
    public String blockstate;

    @Parameter(names = {"-o", "--output"}, description = "Output folder path.")
    public File output = new File("out.png");
}

public class BlockRenderer {
    public final static int WIDTH = 256;
    public final static int HEIGHT = 256;

    private final static ThreadLocal<RenderState> threadState = ThreadLocal.withInitial(RenderState::new);
    private final static SimplePixelPostProcessingFilter POST_PROCESSING_FILTER = new GammaCorrectionFilter();

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

        final double yaw;
        final double pitch;
        final double roll;

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
        double[][] RGS4 = {
                {-0.25, 0.75},
                {0.75, 0.25},
                {-0.75, -0.25},
                {0.25, -0.75}
        };
        double invSamples = 1.0 / RGS4.length;

        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                double r = 0, g = 0, b = 0, a = 0;

                for (int i = 0; i < RGS4.length; i++) {
                    double offsetX = RGS4[i][0] * 0.5 + 0.5;
                    double offsetY = RGS4[i][1] * 0.5 + 0.5;

                    double u = -halfWidth + (x + offsetX) * invHeight;
                    double v = -0.5 + (y + offsetY) * invHeight;

                    state.cam.calcViewRay(state.ray, u, v);

                    if (state.octree.enterBlock(state.scene, state.ray, palette)) {
                        SimpleShader.shade(state.ray);
                        double[] pixel = {
                                state.ray.color.x,
                                state.ray.color.y,
                                state.ray.color.z,
                                1
                        };
                        POST_PROCESSING_FILTER.processPixel(pixel);

                        r += pixel[0];
                        g += pixel[1];
                        b += pixel[2];
                        a += pixel[3];
                    }
                }

                double[] finalPixel = new double[4];
                finalPixel[0] = r * invSamples * 255;
                finalPixel[1] = g * invSamples * 255;
                finalPixel[2] = b * invSamples * 255;
                finalPixel[3] = a * invSamples * 255;

                raster.setPixel(x, y, finalPixel);
            }
        }

        return img;
    }

    public static void main(String[] args) {
        CommandOctree octree = new CommandOctree();
        CommandBlock blockstate = new CommandBlock();
        JCommander jc = JCommander.newBuilder()
                .addCommand("octree", octree)
                .addCommand("blockstate", blockstate)
                .build();
        jc.parse(args);

        BlockSpec.blockProviders.add(new MinecraftBlockProvider());

        if ("octree".equals(jc.getParsedCommand())) {
            if (octree.textures != null) {
                ResourcePackLoader.loadResourcePacks(Arrays.asList(octree.textures));
            }
            BiomeStructure.registerDefaults();

            File outputFolder;
            if (octree.output != null) {
                outputFolder = new File(octree.output);
            } else {
                outputFolder = new File("./out/");
            }
            if (!outputFolder.mkdirs()) {
                throw new RuntimeException("Failed to create output folder.");
            }

            OctreeFileFormat.OctreeData data;
            try (DataInputStream dis = new DataInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(
                    octree.input))))) {
                data = OctreeFileFormat.load(
                        dis, "PACKED", "WORLD_TEXTURE_2D", (step) -> {
                        });
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(-1);
                return;
            }

            int size = data.palette.getPalette().size();

            ForkJoinPool pool;
            if (octree.threads != null) {
                pool = new ForkJoinPool(octree.threads);
            } else {
                pool = new ForkJoinPool();
            }

            AtomicInteger progress = new AtomicInteger(0);
            ReentrantLock updateLock = new ReentrantLock();
            long[] lastUpdate = new long[]{0};
            long startTime = System.currentTimeMillis();

            pool.submit(() -> IntStream.range(0, size).parallel().forEach(i -> {
                BufferedImage out = new BufferedImage(BlockRenderer.WIDTH * 2, BlockRenderer.HEIGHT, BufferedImage.TYPE_INT_ARGB);
                Graphics outGraphics = out.getGraphics();

                BufferedImage img = BlockRenderer.renderBlock(i, data.palette, BlockRenderer.Orientation.IsoTopWestNorth);
                outGraphics.drawImage(img, 0, 0, null);

                BufferedImage img2 = BlockRenderer.renderBlock(i, data.palette, BlockRenderer.Orientation.IsoBottomEastSouth);
                outGraphics.drawImage(img2, BlockRenderer.WIDTH, 0, null);

                outGraphics.dispose();

                File outF = new File(outputFolder, String.format("block_%d_%s.png", i, data.palette.get(i).name.replace(':', '_')));
                try (OutputStream stream = new BufferedOutputStream(new FileOutputStream(outF))) {
                    ImageIO.write(out, "PNG", stream);

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
        } else if ("blockstate".equals(jc.getParsedCommand())) {
            if (blockstate.textures != null) {
                ResourcePackLoader.loadResourcePacks(Arrays.asList(blockstate.textures));
            }
            BlockPalette palette = new BlockPalette();
            int i = palette.put(BlockParser.parse(blockstate.blockstate));

            BufferedImage out = new BufferedImage(BlockRenderer.WIDTH * 2, BlockRenderer.HEIGHT, BufferedImage.TYPE_INT_ARGB);
            Graphics outGraphics = out.getGraphics();

            BufferedImage img = BlockRenderer.renderBlock(i, palette, BlockRenderer.Orientation.IsoTopWestNorth);
            outGraphics.drawImage(img, 0, 0, null);

            BufferedImage img2 = BlockRenderer.renderBlock(i, palette, BlockRenderer.Orientation.IsoBottomEastSouth);
            outGraphics.drawImage(img2, BlockRenderer.WIDTH, 0, null);

            outGraphics.dispose();
            try (OutputStream stream = new BufferedOutputStream(new FileOutputStream(blockstate.output))) {
                ImageIO.write(out, "PNG", stream);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            jc.usage();
        }
    }
}
