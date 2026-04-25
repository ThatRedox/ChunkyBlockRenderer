package blockrenderer.antialiasing;

import blockrenderer.renderer.PixelWriter;
import blockrenderer.renderer.Sampler;

public class NoSupersampling implements SupersamplingFilter {
    @Override
    public void renderWithSampling(int width, int height, Sampler sampler, PixelWriter imageOut) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                imageOut.setPixel(x, y, sampler.getSample(x, y));
            }
        }
    }
}
