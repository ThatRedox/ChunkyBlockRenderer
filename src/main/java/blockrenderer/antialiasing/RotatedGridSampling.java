package blockrenderer.antialiasing;

import blockrenderer.renderer.PixelWriter;
import blockrenderer.renderer.Sampler;

public class RotatedGridSampling implements SupersamplingFilter {
    @Override
    public void renderWithSampling(int width, int height, Sampler sampler, PixelWriter imageOut) {
        double[][] RGS4 = {
                {-0.25, 0.75},
                {0.75, 0.25},
                {-0.75, -0.25},
                {0.25, -0.75}
        };
        double invSamples = 1.0 / RGS4.length;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                double r = 0, g = 0, b = 0, a = 0;

                for (double[] doubles : RGS4) {
                    double offsetX = doubles[0] * 0.5 + 0.5;
                    double offsetY = doubles[1] * 0.5 + 0.5;
                    double[] sample = sampler.getSample(x + offsetX, y + offsetY);
                    r += sample[0];
                    g += sample[1];
                    b += sample[2];
                    a += sample[3];
                }

                double[] finalPixel = new double[4];
                finalPixel[0] = r * invSamples;
                finalPixel[1] = g * invSamples;
                finalPixel[2] = b * invSamples;
                finalPixel[3] = a * invSamples;
                imageOut.setPixel(x, y, finalPixel);
            }
        }
    }
}
