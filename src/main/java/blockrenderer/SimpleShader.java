package blockrenderer;

import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.math.QuickMath;
import se.llbit.math.Ray;
import se.llbit.math.Vector3;

public class SimpleShader {
    private static final Vector3 sun;
    private static final double emittance = Math.pow(1.25, Scene.DEFAULT_GAMMA);

    static {
        sun = new Vector3(1, 1, 1);
        sun.normalize();
    }

    public static void shade(Ray ray) {
        Vector3 n = ray.getNormal();
        double shading = QuickMath.max(0.3, n.x * sun.x + n.y * sun.y + n.z * sun.z) * emittance;
        ray.color.x *= shading;
        ray.color.y *= shading;
        ray.color.z *= shading;
    }
}
