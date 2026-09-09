// Self-contained clean-room implementation of the p.2.8.1 tick-budget tiering
// contract (engine.sim.budget). No net.minecraft / net.neoforged dependency.
package io.toterra.subterra.engine.sim.budget;

import java.util.Objects;

/**
 * Immutable identity of a spatial region used as a {@link BudgetScheduler} tier
 * key. A region is derived from a world coordinate pair via
 * {@link BudgetScheduler#regionOf(long, long)}, which right-shifts both
 * coordinates by the configured {@code regionBits} — a deterministic pure
 * function, so the same world cell always maps to the same region regardless of
 * tick, thread, or wall-clock.
 *
 * <p>Natural order is lexicographic by {@code x} then {@code z}, so ordered sets
 * and {@link BudgetScheduler#deferredRegions()} come out deterministically.
 *
 * <p>p.2.8.1 用作 {@link BudgetScheduler} 三级键的空间区域不可变标识。区域由世界坐标对经
 * {@link BudgetScheduler#regionOf(long, long)} 派生（按配置的 {@code regionBits} 对两坐标
 * 右移）——该右移是确定性纯函数，因此同一世界格子在任意 tick/线程/墙钟下恒映射到同一区域。
 *
 * <p>自然序为按 {@code x} 再到 {@code z} 的字典序，故有序集合与
 * {@link BudgetScheduler#deferredRegions()} 输出是确定性的。
 *
 * @param x the region's X index (world X <code>&gt;&gt; regionBits</code>).
 * @param z the region's Z index (world Z <code>&gt;&gt; regionBits</code>).
 */
public record RegionKey(long x, long z) implements Comparable<RegionKey> {

    @Override
    public int compareTo(RegionKey other) {
        Objects.requireNonNull(other, "other");
        int c = Long.compare(x, other.x);
        return c != 0 ? c : Long.compare(z, other.z);
    }
}