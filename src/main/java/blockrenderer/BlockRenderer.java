package blockrenderer;

import se.llbit.chunky.chunk.BlockPalette;
import se.llbit.chunky.renderer.postprocessing.PreviewFilter;
import se.llbit.chunky.renderer.projection.ProjectionMode;
import se.llbit.chunky.renderer.scene.Camera;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.math.Octree;
import se.llbit.math.Ray;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;

public class BlockRenderer {
    public final static int WIDTH = 256;
    public final static int HEIGHT = 256;

    public final static Scene scene = new Scene();
    static {
        scene.setBiomeColorsEnabled(false);
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
        Octree octree = new Octree("PACKED", 1);
        octree.set(block, 0, 0, 0);

        Camera cam = scene.camera();
        cam.setProjectionMode(ProjectionMode.PARALLEL);
        cam.getPosition().set(0.5, 0.5, 0.5);
        cam.setFoV(2);
        orientation.setCamera(cam);

        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        WritableRaster raster = img.getRaster();

        double halfWidth = WIDTH / (2.0 * HEIGHT);
        double invHeight = 1.0 / HEIGHT;

        for (int x = 0; x < WIDTH; x++) {
            Ray ray = new Ray();
            for (int y = 0; y < HEIGHT; y++) {
                cam.calcViewRay(ray,
                        -halfWidth + x * invHeight,
                        -0.5 + y * invHeight);
                if (octree.enterBlock(scene, ray, palette)) {
                    double[] pixel = new double[] {ray.color.x, ray.color.y, ray.color.z};
                    PreviewFilter.INSTANCE.processPixel(pixel);
                    raster.setPixel(x, y, new double[] {pixel[0] * 255, pixel[1] * 255, pixel[2] * 255, 255});
                } else {
                    raster.setPixel(x, y, new double[] {0, 0, 0, 0});
                }
            }
        }

        return img;
    }
}
