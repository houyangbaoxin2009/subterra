package io.toterra.subterra.optim.worldgen.pipeline.density;

import java.util.Objects;

/**
 * Density-function factory and combinators (p.1.8.3, self-developed): build
 * and compose {@link Density} trees — constant / linear ramps, arithmetic,
 * min/max, clamp, blend (mix), uniform scaling, domain offset and value
 * noise. All combinators are pure and allocation-free in the hot path.
 */
public final class Densities {

    private Densities() {
    }

    /** Constant field. */
    public static Density constant(double value) {
        return (x, y, z) -> value;
    }

    /** Linear ramp in x: {@code slope * x + intercept}. */
    public static Density linearX(double slope, double intercept) {
        return (x, y, z) -> slope * x + intercept;
    }

    /** Identity face: 1.0 * x + 0.0 (handy as a blend/offset probe base). */
    public static Density rampX() {
        return linearX(1.0, 0.0);
    }

    /** Sum. */
    public static Density add(Density a, Density b) {
        return (x, y, z) -> a.eval(x, y, z) + b.eval(x, y, z);
    }

    /** Product. */
    public static Density mul(Density a, Density b) {
        return (x, y, z) -> a.eval(x, y, z) * b.eval(x, y, z);
    }

    /** Minimum. */
    public static Density min(Density a, Density b) {
        return (x, y, z) -> Math.min(a.eval(x, y, z), b.eval(x, y, z));
    }

    /** Maximum. */
    public static Density max(Density a, Density b) {
        return (x, y, z) -> Math.max(a.eval(x, y, z), b.eval(x, y, z));
    }

    /** Clamp into [lo, hi]. */
    public static Density clamp(Density d, double lo, double hi) {
        if (lo > hi) {
            throw new IllegalArgumentException("clamp bounds inverted: lo=" + lo + " hi=" + hi);
        }
        return (x, y, z) -> clampValue(d.eval(x, y, z), lo, hi);
    }

    /** Linear blend: {@code a + (b - a) * blend} with blend in [0, 1]. */
    public static Density mix(Density a, Density b, Density blend) {
        return (x, y, z) -> {
            double t = blend.eval(x, y, z);
            return a.eval(x, y, z) + (b.eval(x, y, z) - a.eval(x, y, z)) * t;
        };
    }

    /** Uniform scaling. */
    public static Density scale(Density d, double factor) {
        return (x, y, z) -> d.eval(x, y, z) * factor;
    }

    /** Domain offset: evaluates {@code d} at {@code (x+dx, y+dy, z+dz)}. */
    public static Density offset(Density d, Density dx, Density dy, Density dz) {
        Objects.requireNonNull(d, "d");
        return (x, y, z) -> d.eval(
                x + (dx == null ? 0 : dx.eval(x, y, z)),
                y + (dy == null ? 0 : dy.eval(x, y, z)),
                z + (dz == null ? 0 : dz.eval(x, y, z)));
    }

    /** Value-noise field. */
    public static Density valueNoise(long seed, double scale) {
        ValueNoise noise = new ValueNoise(seed, scale);
        return noise::eval;
    }

    /** Reuse-friendly access. */
    public static ValueNoise noise(long seed, double scale) {
        return new ValueNoise(seed, scale);
    }

    static double clampValue(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}