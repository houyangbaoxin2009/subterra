package io.toterra.subterra.optim.worldgen.pipeline.noise;

import java.util.Random;

/**
 * Deterministic legacy PRNG (p.1.8.6), bit-identical to Minecraft 1.21.1's
 * {@code net.minecraft.world.level.levelgen.LegacyRandomSource}.
 * <p>
 * MC's {@code LegacyRandomSource} is a thin, thread-safe decorator over the
 * classic linear-congruential generator with modulus {@code 2^48}, multiplier
 * {@code 25214903917} and increment {@code 11} — the very same LCG as
 * {@link java.util.Random}. Its {@code nextDouble} is
 * {@code ((next(26) << 27) + next(27)) * 2^-53}, {@code nextLong} is
 * {@code (next(32) as long << 32) + next(32)}, {@code nextInt(bound)} is the
 * classic rejection algorithm, and {@code nextGaussian} carries the cached
 * Marsaglia-polar spare. Delegating to {@link java.util.Random} therefore
 * reproduces every 1.21.1 legacy sequence exactly, deterministically and
 * stably across JDKs.
 * <p>
 * 确定性的旧版 PRNG（p.1.8.6），与 Minecraft 1.21.1 的
 * {@code LegacyRandomSource} 逐位一致：本质是模 {@code 2^48}、
 * 乘数 {@code 25214903917}、增 {@code 11} 的线性同余发生器——正是
 * {@link java.util.Random} 同款。委托给 {@link java.util.Random} 即可
 * 逐位复现全部 1.21.1 旧版序列。
 */
public final class LegacyRandom {

    /** The wrapped classic LCG (bit-for-bit the MC {@code LegacyRandomSource}). */
    private final Random random;

    /**
     * @param seed the master seed; a {@code java.util.Random} seeded with it
     *             produces the exact MC legacy sequence.
     */
    public LegacyRandom(long seed) {
        this.random = new Random(seed);
    }

    /** Next 32-bit value ({@code (int) next(32)}). */
    public int nextInt() {
        return random.nextInt();
    }

    /**
     * Next integer in {@code [0, bound)} using the classic unbiased rejection
     * algorithm (power-of-two shortcut included).
     *
     * @param bound exclusive upper bound, must be positive.
     * @throws IllegalArgumentException if {@code bound <= 0}.
     */
    public int nextInt(int bound) {
        return random.nextInt(bound);
    }

    /** Next 64-bit value ({@code (next(32) << 32) + next(32)}). */
    public long nextLong() {
        return random.nextLong();
    }

    /** Next double in {@code [0, 1)} ({@code ((next(26)<<27) + next(27)) / 2^53}). */
    public double nextDouble() {
        return random.nextDouble();
    }

    /** Next normally-distributed double (cached Marsaglia-polar spare carried). */
    public double nextGaussian() {
        return random.nextGaussian();
    }
}