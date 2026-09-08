package io.toterra.subterra.api.worldgen.profiler;

/**
 * The complete result of one world profile run (p.1.8.30): the run metadata, the
 * plan, the aggregated per-category statistics, the optional slice output and the
 * number of columns sampled. Immutable; held collections are copied on
 * construction. Pure data.
 * <p>
 * 一次世界档案运行的完整结果（p.1.8.30）：运行元数据、计划、按类别的聚合统计、可选的切片
 * 输出与被采样的柱数。不可变；构造时内部集合被拷贝。纯数据。
 */
public record ProfileReport(long seed, String dimension, String appVersion,
                            ProfilePlan plan, HeightStats terrain, BlockStats blocks,
                            CaveStats caves, BiomeStats biomes, SliceReport slice,
                            long columnSamples) {
}