package blockrenderer.renderer;

import se.llbit.chunky.renderer.postprocessing.SimplePixelPostProcessingFilter;

import java.awt.image.WritableRaster;
import java.util.Arrays;

public class WritableRasterPixelWriter implements PixelWriter {
    private final WritableRaster raster;
    private final SimplePixelPostProcessingFilter postProcessor;

    public WritableRasterPixelWriter(WritableRaster raster, SimplePixelPostProcessingFilter postProcessor) {
        this.raster = raster;
        this.postProcessor = postProcessor;
    }

    @Override
    public void setPixel(int x, int y, double[] color) {
        double[] finalPixel = Arrays.copyOf(color, 4);
        postProcessor.processPixel(finalPixel);
        finalPixel[0] *= 255;
        finalPixel[1] *= 255;
        finalPixel[2] *= 255;
        finalPixel[3] *= 255;
        raster.setPixel(x, y, finalPixel);
    }
}
