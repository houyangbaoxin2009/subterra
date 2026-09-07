package io.toterra.subterra.optim.worldgen.pipeline.router;

import io.toterra.subterra.optim.worldgen.pipeline.noise.perlin.ImprovedNoise;

/**
 * The merged 1.21.1 per-lattice smoothed sampler (p.1.8.12 helper). In 1.21.1 the
 * {@code SmoothedNoise} class was folded into {@code ImprovedNoise} as the
 * 5-argument overload {@code noise(x, y, z, yScale, yMax)} (verified via javap on
 * the de-obfuscated 1.21.1 {@code ImprovedNoise}). Shared by {@link BlendedNoise}
 * and {@link InterpolatedNoise}, which sample their octave families with that
 * overload. It evaluates on an existing subterra {@link ImprovedNoise} lattice via
 * its public {@code perm}/{@code xo}/{@code yo}/{@code zo}/gradientDot surface.
 *
 * <p>纯计算：融合的 1.21.1 逐晶格平滑采样器（p.1.8.12 辅助）。1.21.1 把
 * {@code SmoothedNoise} 并入 {@code ImprovedNoise}，成为五参重载
 * {@code noise(x,y,z,yScale,yMax)}（经 javap 对照反混淆后的 1.21.1
 * {@code ImprovedNoise} 验证）。{@link BlendedNoise} 与 {@link InterpolatedNoise}
 * 共用之，用该重载采样各自的八度族。它经由子terra {@link ImprovedNoise} 公开的
 * {@code perm}/{@code xo}/{@code yo}/{@code zo}/gradientDot 表面在其晶格上求值。
 */
final class SmoothedNoise {

    /** The fused-cell-height epsilon as in vanilla. */
    private static final double EPSILON = 1.0000000116860974E-7;

    private SmoothedNoise() {
    }

    /** {@code floor(double)}. */
    static int floor(double v) {
        int i = (int) v;
        return v < (double) i ? i - 1 : i;
    }

    /** {@code Mth.lerp}: {@code start + delta * (end - start)}. */
    static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    /** {@code Mth.smoothstep}: {@code t^3 (t (6t - 15) + 10)}. */
    static double smoothstep(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    /** {@code Mth.lerp3} (x-pairs first, then y, then z). */
    static double lerp3(double a, double b, double c, double d0, double d1, double d2, double d3,
                        double d4, double d5, double d6, double d7) {
        return lerp(c, lerp2(a, b, d0, d1, d2, d3), lerp2(a, b, d4, d5, d6, d7));
    }

    private static double lerp2(double a, double b, double d0, double d1, double d2, double d3) {
        return lerp(b, lerp(a, d0, d1), lerp(a, d2, d3));
    }

    /**
     * Vanilla 1.21.1 merged 5-arg {@code ImprovedNoise.noise(x,y,z,yScale,yMax)}:
     * offsets, floors, and — when {@code yScale != 0} — computes the "fused cell
     * height" {@code floor((yMax<dy? yMax : dy)/yScale + epsilon) * yScale} that
     * replaces the z-fraction in the gradients / z-blend. The real {@code z}
     * fraction is carried only as the (unused) final argument, exactly as the
     * decompiled body does.
     */
    static double noise(ImprovedNoise n, double x, double y, double z, double yScale, double yMax) {
        double xp = x + n.xo;
        double yp = y + n.yo;
        double zp = z + n.zo;
        int xi = floor(xp);
        int yi = floor(yp);
        int zi = floor(zp);
        double dx = xp - (double) xi;
        double dy = yp - (double) yi;
        double dz = zp - (double) zi;
        final double fused;
        if (yScale != 0.0) {
            double sy = (yMax >= 0.0 && yMax < dy) ? yMax : dy;
            fused = floor(sy / yScale + EPSILON) * yScale;
        } else {
            fused = 0.0;
        }
        return sampleAndLerp(n, xi, yi, zi, dx, dy, fused - dy, dz);
    }

    /**
     * The 8-corner trilinear sample (vanilla {@code sampleAndLerp}): gradients use
     * {@code (dx, dy, zFrac)}; the smoothstep z-blend uses {@code zFrac}; the
     * passed-too {@code dz} argument is unused, mirroring 1.21.1.
     */
    private static double sampleAndLerp(ImprovedNoise n, int xi, int yi, int zi,
                                        double dx, double dy, double zFrac, double dzUnused) {
        int i = n.perm(xi);
        int i1 = n.perm(xi + 1);
        int k = n.perm(i + yi);
        int k1 = n.perm(i + yi + 1);
        int l = n.perm(i1 + yi);
        int l1 = n.perm(i1 + yi + 1);

        double d0 = gradientDot(n, n.perm(k + zi), dx, dy, zFrac);
        double d1 = gradientDot(n, n.perm(l + zi), dx - 1.0, dy, zFrac);
        double d2 = gradientDot(n, n.perm(k1 + zi), dx, dy - 1.0, zFrac);
        double d3 = gradientDot(n, n.perm(l1 + zi), dx - 1.0, dy - 1.0, zFrac);
        double d4 = gradientDot(n, n.perm(k + zi + 1), dx, dy, zFrac - 1.0);
        double d5 = gradientDot(n, n.perm(l + zi + 1), dx - 1.0, dy, zFrac - 1.0);
        double d6 = gradientDot(n, n.perm(k1 + zi + 1), dx, dy - 1.0, zFrac - 1.0);
        double d7 = gradientDot(n, n.perm(l1 + zi + 1), dx - 1.0, dy - 1.0, zFrac - 1.0);

        return lerp3(smoothstep(dx), smoothstep(dy), smoothstep(zFrac), d0, d1, d2, d3, d4, d5, d6, d7);
    }

    private static double gradientDot(ImprovedNoise n, int hash, double dx, double dy, double dz) {
        return ImprovedNoise.gradientDot(hash, dx, dy, dz);
    }
}