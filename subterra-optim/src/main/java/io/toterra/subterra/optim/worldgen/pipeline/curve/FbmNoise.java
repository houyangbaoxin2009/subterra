package io.toterra.subterra.optim.worldgen.pipeline.curve;

import io.toterra.subterra.optim.worldgen.pipeline.density.ValueNoise;

/**
 * 确定性分形噪声 (fBm)：叠加多个不同种子、不同频率、不同振幅的 value-noise 层。
 * Deterministic fractal (fBm) value noise: octaves of value noise with per-octave
 * seed, frequency doubling and decaying amplitude, normalized so the result
 * stays within roughly [-1, 1]. O(octaves) per eval, constant memory.
 */
public final class FbmNoise {

    /** Golden-ratio constant (SplitMix) used to derive per-octave seeds. */
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    private final ValueNoise[] layers;
    private final double norm;
    private final double persistence;

    /**
     * @param seed        reproducible lattice seed (each octave derives its own sub-seed)
     * @param scale       base lattice cell size in blocks (must be finite, > 0)
     * @param octaves     number of noise layers (must be >= 1)
     * @param persistence amplitude decay factor between octaves (must be (0, 1])
     * @throws IllegalArgumentException on invalid scale / octaves / persistence
     */
    public FbmNoise(long seed, double scale, int octaves, double persistence) {
        if (!(scale > 0) || Double.isNaN(scale) || Double.isInfinite(scale)) {
            throw new IllegalArgumentException("scale must be a finite positive value: " + scale);
        }
        if (octaves < 1) {
            throw new IllegalArgumentException("octaves must be >= 1: " + octaves);
        }
        if (Double.isNaN(persistence) || Double.isInfinite(persistence)
                || persistence <= 0.0 || persistence > 1.0) {
            throw new IllegalArgumentException("persistence must be in (0, 1]: " + persistence);
        }
        this.persistence = persistence;
        this.layers = new ValueNoise[octaves];
        double total = 0.0;
        double amp = 1.0; // p^0 == 1.0 exactly (covers persistence==0, 0^0 semantics)
        for (int o = 0; o < octaves; o++) {
            long octSeed = seed + GOLDEN * (long) (o + 1);
            double octScale = scale * Math.pow(0.5, o); // 2^-o -> frequency doubling
            layers[o] = new ValueNoise(octSeed, octScale);
            total += amp;
            amp *= persistence;
        }
        // Geometric series sum of amplitudes: (1 - p^octaves) / (1 - p).
        this.norm = total;
    }

    /**
     * 评估 (x, y, z) 处的确定性分形噪声，输出近似在 [-1, 1]。
     * Evaluate the deterministic fBm field, normalized to approximately [-1, 1].
     */
    public double eval(double x, double y, double z) {
        double acc = 0.0;
        double amp = 1.0;
        for (int o = 0; o < layers.length; o++) {
            acc += amp * layers[o].eval(x, y, z);
            amp *= persistence;
        }
        return acc / norm;
    }
}