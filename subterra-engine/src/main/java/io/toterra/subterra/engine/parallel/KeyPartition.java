// Async dispatch design derives from C2ME (MIT, Copyright (c) ishland, https://github.com/ishland/C2ME);
// implementation is original Subterra code.
package io.toterra.subterra.engine.parallel;

import java.util.Objects;

/**
 * Pure deterministic partition of arbitrary keys into a small fixed number of
 * "banks". This is the key-agnostic generalization of the p.2.6 chunk partition
 * (io.toterra.subterra.engine.worldgen.async.ChunkPartition): bank assignment is
 * a pure function of the key and the bank count alone — never of runtime, thread,
 * or scheduler state — so the same key always maps to the same bank, which is the
 * foundation of the deterministic parallel-execution contract.
 *
 * <p>Three overloads cover the common key shapes: a single {@code long} hash, an
 * {@code (a, b)} coordinate pair (kept bit-for-bit identical to
 * {@code ChunkPartition.bankIndex(a, b, k)} so the chunk-coordinate domain stays
 * interoperable), and an arbitrary {@link Object} key. The generic Object overload
 * requires callers to use stable immutable value objects whose
 * {@code equals}/{@code hashCode} contract never changes, otherwise determinism
 * across runs cannot be guaranteed.
 *
 * <p>p.2.7.1 通用确定性键分区底座：把任意键静止地划分到少量"bank"。这是 p.2.6 区块分区
 * （io.toterra.subterra.engine.worldgen.async.ChunkPartition）的键无关泛化：bank 分配
 * 只依赖键与 bank 数量，绝不依赖运行时/线程/调度状态，因此同一键总是落到同一 bank——
 * 这是确定性并行执行契约的基础。
 *
 * <p>三个重载覆盖常见键形态：单个 {@code long} 哈希、{@code (a, b)} 坐标对（与
 * {@code ChunkPartition.bankIndex(a, b, k)} 逐位一致，保持区块坐标域互操作），以及任意
 * {@link Object} 键。通用 Object 重载要求调用方使用 equals/hashCode 稳定的不可变值对象，
 * 否则跨运行确定性无法保证。
 */
public final class KeyPartition {

    /** Upper bound placed on {@link #bankCount(int)} so bank arrays stay tiny. */
    public static final int MAX_BANKS = 8;

    private KeyPartition() {
    }

    /**
     * Normalizes a requested parallelism into a bounded power-of-two-ish bank
     * count in {@code [1, MAX_BANKS]}. Returns the smallest power of two that is
     * at least the (clamped) requested parallelism, capped at {@link #MAX_BANKS}.
     * A non-positive input yields {@code 1}. Semantics identical to
     * {@code ChunkPartition.bankCount}.
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
     * Maps a single {@code long} hash to a bank index in {@code [0, bankCount)}.
     *
     * <p>Formula: a single-word SplitMix finalizer applied to the hash, then
     * reduced modulo the bank count. The mix is a pure integer function of the
     * hash only, so the mapping is deterministic across threads and runs.
     *
     * @param hash      the key hash.
     * @param bankCount the bank count ({@code >= 1}).
     * @return a bank index in {@code [0, bankCount)}.
     */
    public static int bankIndex(long hash, int bankCount) {
        int k = Math.max(1, bankCount);
        long v = hash * 0x9E3779B97F4A7C15L;
        v ^= v >>> 32;
        v ^= v >>> 16;
        return (int) (v & 0x7FFFFFFFL) % k;
    }

    /**
     * Maps an {@code (a, b)} coordinate pair to a bank index in {@code [0, bankCount)}.
     *
     * <p>Bit-for-bit identical to {@code ChunkPartition.bankIndex(a, b, bankCount)}
     * (p.2.6) so the chunk-coordinate domain remains fully interoperable: both
     * halves are mixed with two distinct odd large multipliers and folded
     * together with xor, then reduced modulo the bank count. The mix is a pure
     * integer function of {@code (a, b)} only.
     *
     * @param a         the first key word (e.g. chunk X).
     * @param b         the second key word (e.g. chunk Z).
     * @param bankCount the bank count ({@code >= 1}).
     * @return a bank index in {@code [0, bankCount)}.
     */
    public static int bankIndex(long a, long b, int bankCount) {
        int k = Math.max(1, bankCount);
        long v = (a * 0x9E3779B97F4A7C15L) ^ (b * 0xBF58476D1CE4E5B9L);
        v ^= v >>> 32;
        v ^= v >>> 16;
        return (int) (v & 0x7FFFFFFFL) % k;
    }

    /**
     * Maps an arbitrary {@link Object} key to a bank index in {@code [0, bankCount)}.
     *
     * <p>The key's {@code hashCode()} is run through the Murmur3 fmix32 finalizer
     * so small integer-ish hash values still spread well across banks, then
     * reduced modulo the bank count. Callers must use stable immutable value
     * objects whose {@code equals}/{@code hashCode} contract never changes,
     * otherwise cross-run determinism is not guaranteed.
     *
     * @param key       the key (non-null).
     * @param bankCount the bank count ({@code >= 1}).
     * @return a bank index in {@code [0, bankCount)}.
     */
    public static int bankIndex(Object key, int bankCount) {
        Objects.requireNonNull(key, "key");
        int h = key.hashCode();
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return (h & 0x7FFFFFFF) % Math.max(1, bankCount);
    }
}
