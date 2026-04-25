package blockrenderer.antialiasing;

import blockrenderer.renderer.PixelWriter;
import blockrenderer.renderer.Sampler;

public interface SupersamplingFilter {
    void renderWithSampling(int width, int height, Sampler sampler, PixelWriter imageOut);
}
