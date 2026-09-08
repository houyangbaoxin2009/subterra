package io.toterra.subterra.api.worldgen.profiler;

/**
 * The block tally restricted to one vertical band {@code [yFrom, yTo) } of the
 * profiled volume (p.1.8.30). Immutable; the entry list is copied on construction.
 * Pure data.
 * <p>
 * 被采集体积的某个竖直带 {@code [yFrom, yTo) } 内受限的方块统计（p.1.8.30）。不可变；
 * 构造时条目列表被拷贝。纯数据。
 */
public record BandBlocks(int yFrom, int yTo, java.util.List<BlockEntry> entries) {

    /** Compact constructor copying the entry list to an immutable snapshot. */
    public BandBlocks {
        entries = java.util.List.copyOf(entries);
    }
}