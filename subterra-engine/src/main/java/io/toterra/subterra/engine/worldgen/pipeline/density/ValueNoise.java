package io.toterra.subterra.engine.worldgen.pipeline.density;

/**
 * Deterministic 3-D value noise (p.1.8.3, self-developed, no upstream code):
 * lattice-point hashes (via {@link NoiseHash}) are trilinearly interpolated
 * with a smooth fade, giving a [-1, 1] field that is perfectly reproducible
 * for a fixed seed. Evaluation is constant-time (8 lattice hashes, no
 * allocation) — no O(n²), deterministic.
 */
public final class ValueNoise {

    private final long seed;
    private final double invScale;

    /**
     * @param seed  reproducible lattice hash seed
     * @param scale lattice cell size in blocks (must be > 0)
     */
    public ValueNoise(long seed, double scale) {
        if (!(scale > 0) || Double.isNaN(scale) || Double.isInfinite(scale)) {
            throw new IllegalArgumentException("scale must be a finite positive value: " + scale);
        }
        this.seed = seed;
        this.invScale = 1.0 / scale;
    }

    public long seed() {
        return seed;
    }

    /** Evaluates the noise field at block coordinates. */
    public double eval(double x, double y, double z) {
        double fx = x * invScale;
        double fy = y * invScale;
        double fz = z * invScale;
        long x0 = (long) Math.floor(fx);
        long y0 = (long) Math.floor(fy);
        long z0 = (long) Math.floor(fz);
        double tx = fx - x0;
        double ty = fy - y0;
        double tz = fz - z0;
        double u = fade(tx);
        double v = fade(ty);
        double w = fade(tz);

        double n000 = grid(x0, y0, z0);
        double n100 = grid(x0 + 1, y0, z0);
        double n010 = grid(x0, y0 + 1, z0);
        double n110 = grid(x0 + 1, y0 + 1, z0);
        double n001 = grid(x0, y0, z0 + 1);
        double n101 = grid(x0 + 1, y0, z0 + 1);
        double n011 = grid(x0, y0 + 1, z0 + 1);
        double n111 = grid(x0 + 1, y0 + 1, z0 + 1);

        double nx00 = lerp(u, n000, n100);
        double nx10 = lerp(u, n010, n110);
        double nx01 = lerp(u, n001, n101);
        double nx11 = lerp(u, n011, n111);
        double nxy0 = lerp(v, nx00, nx10);
        double nxy1 = lerp(v, nx01, nx11);
        return lerp(w, nxy0, nxy1);
    }

    /** Lattice-point value in [-1, 1]. */
    public double grid(long x, long y, long z) {
        return NoiseHash.toSigned(NoiseHash.gridHash(seed, x, y, z));
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double fade(double t) {
        // 6t^5 - 15t^4 + 10t^3 (smootherstep)
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }
}