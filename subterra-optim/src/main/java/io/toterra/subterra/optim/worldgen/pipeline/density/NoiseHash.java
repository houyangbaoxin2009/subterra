package io.toterra.subterra.optim.worldgen.pipeline.density;

/**
 * Deterministic 64-bit integer hashing for the noise lattice (p.1.8.3).
 * SplitMix64-style finalizer: bijective, avalanche-strong, reproducible
 * across runs and JVMs. Pure JDK; constant time.
 */
public final class NoiseHash {

    private NoiseHash() {
    }

    private static final double INV_2_POW_54 = 0x1.0p-54; // 2^-54 -> [0, 1)

    /** SplitMix64 finalizer. */
    public static long scramble(long x) {
        x += 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }

    /** Maps a hash to [0, 1). */
    public static double toUnit(long hash) {
        return (hash >>> 11) * INV_2_POW_54;
    }

    /** Maps a hash to [-1, 1]. */
    public static double toSigned(long hash) {
        return toUnit(hash) * 2.0 - 1.0;
    }

    /** One hash per integer lattice point (seed + coords mixed). */
    public static long gridHash(long seed, long x, long y, long z) {
        long mixed = seed
                ^ scramble(x * 0x27D4EB2F165667C5L)
                ^ scramble(y * 0x9E3779B97F4A7C15L)
                ^ scramble(z * 0xB7E151628AED2A6BL);
        return scramble(mixed);
    }
}