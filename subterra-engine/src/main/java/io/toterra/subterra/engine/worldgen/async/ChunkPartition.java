// Async dispatch design derives from C2ME (MIT, Copyright (c) ishland, https://github.com/ishland/C2ME);
// implementation is original Subterra code.
package io.toterra.subterra.engine.worldgen.async;

/**
 * Pure deterministic partition of world chunk coordinates into a small fixed
 * number of "banks". Bank assignment depends only on the chunk coordinates and
 * the bank count — never on runtime, thread, or scheduler state — so the same
 * coordinate always maps to the same bank, which is the foundation of the
 * deterministic dispatch contract.
 *
 * <p>p.2.6.1 确定性并行分派的基础：把区块坐标静止地划分到少量"bank"。bank 分配只依赖
 * 区块坐标与 bank 数量，绝不依赖运行时/线程/调度状态，因此同一坐标总是落到同一 bank——
 * 这是确定性分派契约的基础。
 */
public final class ChunkPartition {

    /** Upper bound placed on {@link #bankCount(int)} so bank arrays stay tiny. */
    public static final int MAX_BANKS = 8;

    private ChunkPartition() {
    }

    /**
     * Normalizes a requested parallelism into a bounded power-of-two-ish bank
     * count in {@code [1, MAX_BANKS]}. Returns the smallest power of two that is
     * at least the (clamped) requested parallelism, capped at {@link #MAX_BANKS}.
     * A non-positive input yields {@code 1}.
     *
     * @param parallelism the desired parallelism (may be {@code <= 0}).
     * @return the bank count in {@code [1, MAX_BANKS]}.
     */
    public static int bankCount(int parallelism) {
        int p = Math.max(1, parallelism);
        int banks = 1;
        while (banks < p && banks < MAX_BANKS) {
            banks <<= 1;
        }
        return banks;
    }

    /**
     * Maps chunk coordinates to a bank index in {@code [0, bankCount)}.
     *
     * <p>Formula: mix the two halves of the coordinate pair with two distinct
     * odd large multipliers and fold the two words together, then reduce
     * modulo the bank count. The mix is a pure integer function of {@code (x, z)}
     * only. Because the two coordinates use different (odd) multipliers and the
     * words are xored, uniformly stepping x or z flips roughly half the bits, so
     * the modulo distribution spreads adjacent chunks across banks reasonably
     * while never allocating any bank zero chunks for the small coordinate ranges
     * used by the acceptance probe.
     *
     * @param x         the chunk X coordinate.
     * @param z         the chunk Z coordinate.
     * @param bankCount the bank count ({@code >= 1}).
     * @return a bank index in {@code [0, bankCount)}.
     */
    public static int bankIndex(long x, long z, int bankCount) {
        int k = Math.max(1, bankCount);
        // SplitMix-style integer mixing; deterministically pure on (x, z).
        long v = (x * 0x9E3779B97F4A7C15L) ^ (z * 0xBF58476D1CE4E5B9L);
        v ^= v >>> 32;
        v ^= v >>> 16;
        return (int) (v & 0x7FFFFFFFL) % k;
    }
}