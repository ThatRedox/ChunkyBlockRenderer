package blockrenderer.renderer;

import se.llbit.math.Ray;

public interface Shader {
    /**
     * Shade the given hit ray and modify its color accordingly.
     *
     * @param ray Ray after hit
     */
    void shade(Ray ray);
}
