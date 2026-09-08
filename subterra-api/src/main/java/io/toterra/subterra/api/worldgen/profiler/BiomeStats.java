package io.toterra.subterra.api.worldgen.profiler;

/**
 * Biome statistics for a profiled window (p.1.8.30): the census plus per-field
 * climate histograms. Immutable; both lists are copied on construction. Pure
 * data.
 * <p>
 * 被分析窗口的群系统计（p.1.8.30）：普查以及按字段的气候直方图。不可变；构造时两个列表被
 * 拷贝。纯数据。
 */
public record BiomeStats(java.util.List<BiomeEntry> census,
                         java.util.List<ClimateHistogram> climate) {

    /** Compact constructor copying both lists to immutable snapshots. */
    public BiomeStats {
        census = java.util.List.copyOf(census);
        climate = java.util.List.copyOf(climate);
    }
}