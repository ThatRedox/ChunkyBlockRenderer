package blockrenderer;

import org.apache.commons.cli.*;
import se.llbit.chunky.block.BlockSpec;
import se.llbit.chunky.block.MinecraftBlockProvider;
import se.llbit.chunky.resources.OctreeFileFormat;
import se.llbit.chunky.resources.TexturePackLoader;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;

public class Main {
    public static void main(String[] args) throws IOException, ParseException {
        Options options = new Options();
        options.addOption("t", "textures", true, "Path to the texture pack to use.");
        options.addRequiredOption("i", "input", true, "Path to the input octree to take blocks from.");
        options.addOption("o", "output", true, "Output folder path.");

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
        outputFolder.mkdirs();

        OctreeFileFormat.OctreeData data = OctreeFileFormat.load(
                new DataInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(
                        cmd.getOptionValue("i"))))), "PACKED");

        int size = data.palette.getPalette().size();
        AtomicInteger progress = new AtomicInteger(0);

        IntStream.range(0, size).forEach(i -> {
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
                System.out.printf("%d / %d (%.2f %%)\r", z, size, 100 * (double) z / size);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}
