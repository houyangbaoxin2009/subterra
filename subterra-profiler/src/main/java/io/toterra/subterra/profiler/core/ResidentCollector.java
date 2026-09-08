package io.toterra.subterra.profiler.core;

import java.util.HashSet;
import java.util.Objects;

import io.toterra.subterra.api.worldgen.profiler.ProfilePlan;
import io.toterra.subterra.api.worldgen.profiler.ProfileReport;
import io.toterra.subterra.api.worldgen.profiler.WorldSampler;
import io.toterra.subterra.profiler.core.ProfileRegion.Box;

/**
 * Incremental resident collector (p.1.8.30): samples each freshly generated
 * chunk on a chunk-load event and folds its columns into a persistent {@link
 * ProfilerAccum}. Deduplicates by chunk coordinates, so a chunk presented twice
 * is only merged once. A resident never stores the full terrain-height sample
 * (exact percentiles would need it), so a snapshot reports the percentile
 * fields as 0.0 and relies on the per-band terrain histogram instead. Snapshots
 * carry a null slice: a resident does not capture slices.
 * <p>
 * 增量常驻采集器（p.1.8.30）：在区块加载事件中采样每个新生成的区块，并把它的柱并入持久
 * 的 {@link ProfilerAccum}。按区块坐标去重，因此同一区块被送入两次只并入一次。常驻模式
 * 不保存完整地形高度样本（精确分位数需要之），故快照把分位字段报告为 0.0，改用高度带
 * 直方图。快照携带空切片：常驻模式不采集切片。
 */
public final class ResidentCollector {

    private final ProfilePlan plan;
    private final int dimMinY;
    private final int dimMaxY;
    private final ProfilerAccum accum;
    private final HashSet<Long> seen;
    private final int wXFrom;
    private final int wXTo;
    private final int wZFrom;
    private final int wZTo;

    /**
     * Builds a resident collector from a plan. Residency is driven by {@code
     * plan.residency()}: when it is false every {@link #accept} returns false and
     * nothing is accumulated. {@code dimMinY}/{@code dimMaxY} bound the vertical
     * bands.
     * <p>
     * 从计划构建常驻采集器。常驻与否由 {@code plan.residency()} 驱动：当其
     * 为 false 时每个 {@link #accept} 返回 false 且不累加任何数据。
     * {@code dimMinY}/{@code dimMaxY} 界定竖直带范围。
     *
     * @param plan    the profiling plan carrying window/step/categories/residency.
     * @param dimMinY the lowest build Y of the dimension.
     * @param dimMaxY the exclusive upper build Y of the dimension.
     */
    public ResidentCollector(ProfilePlan plan, int dimMinY, int dimMaxY) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.dimMinY = dimMinY;
        this.dimMaxY = dimMaxY;
        this.accum = new ProfilerAccum(plan.categories(), dimMinY, dimMaxY,
            plan.step(), false);
        this.seen = new HashSet<>();
        Box w = ProfileRegion.windowBox(plan.window());
        this.wXFrom = w.xFrom();
        this.wXTo = w.xTo();
        this.wZFrom = w.zFrom();
        this.wZTo = w.zTo();
    }

    /**
     * Samples one freshly generated chunk and folds its (window-clipped)
     * columns into the accumulator. Returns {@code false} when the same chunk
     * has already been accepted or when residency is disabled; returns
     * {@code true} on the first incorporation. Each column of the chunk that
     * falls inside the profiling window is sampled at full density (stride 1).
     * <p>
     * 采样一个刚生成的区块并把其（经窗口裁剪的）柱并入累加器。当同一区块已被接受或常驻被
     * 禁用时返回 {@code false}；首次并入返回 {@code true}。落在窗口内的每个柱都以全密度
     * （步长 1）采样。
     *
     * @param chunkX   the X of the chunk in chunk coordinates.
     * @param chunkZ   the Z of the chunk in chunk coordinates.
     * @param sampler  the world sampler binding for this chunk.
     * @return true when the chunk was incorporated for the first time.
     */
    public boolean accept(int chunkX, int chunkZ, WorldSampler sampler) {
        if (!plan.residency()) {
            return false;
        }
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
        if (seen.contains(key)) {
            return false;
        }
        seen.add(key);
        int cx0 = Math.max(chunkX * 16, wXFrom);
        int cx1 = Math.min(chunkX * 16 + 16, wXTo);
        int cz0 = Math.max(chunkZ * 16, wZFrom);
        int cz1 = Math.min(chunkZ * 16 + 16, wZTo);
        for (int x = cx0; x < cx1; x++) {
            for (int z = cz0; z < cz1; z++) {
                StatsEngine.columnContrib(accum, sampler, x, z);
            }
        }
        return true;
    }

    /** The number of distinct chunks incorporated so far. */
    public long chunksCovered() {
        return seen.size();
    }

    /**
     * Materializes the current snapshot. The section semantics match {@link
     * StatsEngine#scan(WorldSampler, ProfilePlan, long, String, String)}, except
     * that the terrain percentile fields are 0.0 (a resident keeps the per-band
     * histogram, not the full height sample) and the slice is always null.
     * <p>
     * 物化当前快照。段落语义与 {@link StatsEngine#scan(WorldSampler, ProfilePlan,
     * long, String, String)} 一致，但地形分位字段为 0.0（常驻保留高度带直方图而非完整
     * 高度样本），且切片恒为 null。
     *
     * @param seed       the world seed for metadata.
     * @param dimension  the dimension id for metadata.
     * @param appVersion the application version for metadata.
     * @return the current resident snapshot.
     */
    public ProfileReport snapshot(long seed, String dimension, String appVersion) {
        return StatsEngine.report(accum, plan, seed, dimension, appVersion, null);
    }

    /** Resets the accumulated data and the dedup set back to empty. */
    public void reset() {
        accum.reset();
        seen.clear();
    }
}