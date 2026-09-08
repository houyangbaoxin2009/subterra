package io.toterra.subterra.api.worldgen.profiler;

/**
 * The block census of a profiled window (p.1.8.30): a grand block total, a flat
 * per-id tally and the same tally grouped by vertical band. Immutable; lists are
 * copied on construction. Pure data.
 * <p>
 * 被分析窗口的方块普查（p.1.8.30）：总方块数、按 id 的扁平统计，以及按竖直带分组的同一
 * 统计。不可变；构造时列表被拷贝。纯数据。
 */
public record BlockStats(long total, java.util.List<BlockEntry> byId,
                         java.util.List<BandBlocks> byBand) {

    /** Compact constructor copying both lists to immutable snapshots. */
    public BlockStats {
        byId = java.util.List.copyOf(byId);
        byBand = java.util.List.copyOf(byBand);
    }
}