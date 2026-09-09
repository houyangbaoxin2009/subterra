package io.toterra.subterra.engine.pcg;

import java.util.List;

/**
 * p.2.13.1 确定性选择器工具（final 工具类，静态方法）——把「从池中选一」与「按权重从池中选一」
 * 全部折叠成语义固定、同 seed → 同选择的确定性操作。选择只以 {@code pool} 的固有顺序 + 源
 * {@code nextInt} 为输入，无随机、无时序、无全局状态。
 * <p>
 * 契约锚点（也是确定性判据）：同一个 {@code (source, pool)} 两次独立调用 → 两次选择逐位一致；
 * {@code pool} 顺序不同 → 同一源下映射到的下标相同但选出的条目随顺序变化（选择由位置决定）。
 * <p>
 * p.2.13.1 deterministic picker utility (final utility class, static methods) — folds
 * "pick one from a pool" and "pick one from a pool by weight" into fixed-semantic,
 * same-seed → same-pick deterministic operations. A pick takes only the pool's inherent
 * order plus the source's {@code nextInt} as input: no randomness, no timing, no global state.
 * <p>
 * Contract anchors (also the determinism criteria): two independent calls on the same
 * {@code (source, pool)} → bit-identical picks; a different pool order maps the same index
 * but the returned entry changes with the order (the pick is decided by position).
 */
public final class PcgPick {

    private PcgPick() {
        // utility class; no instantiation / 工具类，禁止实例化
    }

    /**
     * 从池中确定性选一：下标 = {@code source.nextInt(pool.size())}，按 {@code pool} 固定顺序取值。
     * 空池 → 受检 {@link PcgException#EMPTY_POOL}。
     * <p>
     * Deterministically picks one entry: index = {@code source.nextInt(pool.size())}, resolved by
     * the pool's fixed order. An empty pool → checked {@link PcgException#EMPTY_POOL}.
     *
     * @param source the deterministic source (supplies the {@code nextInt} for the pick).
     * @param pool   the pool, taken in its list order.
     * @return the picked entry.
     * @throws PcgException with reason {@code EMPTY_POOL} if the pool is empty.
     */
    public static String pick(PcgSource source, List<String> pool) throws PcgException {
        if (pool == null || pool.isEmpty()) {
            throw new PcgException(PcgException.EMPTY_POOL, "pool is null or empty");
        }
        int index = source.nextInt(pool.size());
        return pool.get(index);
    }

    /**
     * 按权重确定性选一：下标 = 权重前缀区间（按 {@code items}/{@code weights} 固定顺序累加）内
     * 命中的位置，命中位置由 {@code source.nextInt(totalWeight)} 决定——同源同权重序 → 同选择。
     * 空池 → {@link PcgException#EMPTY_POOL}；任何非正权重 → {@link PcgException#NEGATIVE_WEIGHT}；
     * 两序列长度不等 → {@link PcgException#BOUND}。
     * <p>
     * Deterministically picks one entry by weight: the index is the position whose cumulative
     * weight interval (in the fixed order of {@code items}/{@code weights}) contains
     * {@code source.nextInt(totalWeight)} — same source and weight order → same pick.
     * An empty pool → {@link PcgException#EMPTY_POOL}; any non-positive weight →
     * {@link PcgException#NEGATIVE_WEIGHT}; unequal sequence lengths → {@link PcgException#BOUND}.
     *
     * @param source  the deterministic source.
     * @param items   the pool entries, in fixed order.
     * @param weights the parallel weights, in the same fixed order.
     * @return the picked entry.
     * @throws PcgException on empty pool / non-positive weight / length mismatch.
     */
    public static String pickWeighted(PcgSource source, List<String> items, List<Long> weights) throws PcgException {
        if (items == null || items.isEmpty()) {
            throw new PcgException(PcgException.EMPTY_POOL, "items is null or empty");
        }
        if (weights == null || weights.size() != items.size()) {
            throw new PcgException(PcgException.BOUND,
                    "weights size " + (weights == null ? "null" : weights.size()) + " != items size " + items.size());
        }
        // Single-pass prefix sum; branchless accumulation. No O(n^2).
        long[] prefix = new long[items.size()];
        long total = 0L;
        for (int i = 0; i < weights.size(); i++) {
            long w = weights.get(i);
            if (w <= 0L) {
                throw new PcgException(PcgException.NEGATIVE_WEIGHT, "weight[" + i + "] = " + w);
            }
            total += w;
            prefix[i] = total;
        }
        // Draw uniformly in [0, total): nextDouble()<1 always (Xoroshiro128++), so the floor
        // product stays in range even when total is large. Deterministic for a fixed seed+pool.
        long draw = (long) (source.nextDouble() * (double) total);
        int lo = 0;
        int hi = items.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (prefix[mid] <= draw) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return items.get(lo);
    }
}

