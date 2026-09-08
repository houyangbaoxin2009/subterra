package io.toterra.subterra.api.worldgen.profiler;

import java.util.Arrays;
import java.util.Objects;

/**
 * Summary statistics of the terrain surface-height sample for a profiled window
 * (p.1.8.30). Holds distribution percentiles, a per-band histogram and column
 * tallies split into land and ocean columns. Pure data.
 * <p>
 * 被分析窗口内地形表面高度样本的汇总统计（p.1.8.30）。持有分布百分位数、按高度带划分的
 * 直方图，以及按陆地/海洋柱拆分的高度计数。纯数据。
 */
public record HeightStats(double min, double max, double mean,
                          double p5, double p25, double p50, double p75, double p95,
                          int[] bandCounts, int bandHeight, long landColumns, long oceanColumns) {

    /** Compact constructor cloning the histogram array. */
    public HeightStats {
        bandCounts = bandCounts.clone();
    }

    /** Returns the histogram as a defensive copy. */
    @Override
    public int[] bandCounts() {
        return bandCounts.clone();
    }

    /** Array-aware deep equality (record equals compares arrays by reference). */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HeightStats that)) {
            return false;
        }
        return min == that.min && max == that.max && mean == that.mean
                && p5 == that.p5 && p25 == that.p25 && p50 == that.p50
                && p75 == that.p75 && p95 == that.p95
                && bandHeight == that.bandHeight
                && landColumns == that.landColumns
                && oceanColumns == that.oceanColumns
                && java.util.Arrays.equals(bandCounts, that.bandCounts);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(min, max, mean, p5, p25, p50, p75, p95,
                java.util.Arrays.hashCode(bandCounts), bandHeight, landColumns, oceanColumns);
    }
}