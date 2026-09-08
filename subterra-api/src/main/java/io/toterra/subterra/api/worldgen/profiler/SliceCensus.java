package io.toterra.subterra.api.worldgen.profiler;

/**
 * The block census restricted to a {@link ProfileSlice} (p.1.8.30): a per-id
 * tally, a per-band tally and the sliced block total. Immutable; both lists are
 * copied on construction. Pure data.
 * <p>
 * 限定在 {@link ProfileSlice} 内的方块普查（p.1.8.30）：按 id 的统计、按带的统计与被切
 * 块方块总数。不可变；构造时两个列表被拷贝。纯数据。
 */
public record SliceCensus(java.util.List<BlockEntry> byId,
                          java.util.List<BandBlocks> byBand, long total) {

    /** Compact constructor copying both lists to immutable snapshots. */
    public SliceCensus {
        byId = java.util.List.copyOf(byId);
        byBand = java.util.List.copyOf(byBand);
    }
}