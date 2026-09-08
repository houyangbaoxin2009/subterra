package io.toterra.subterra.api.worldgen.profiler;

import java.util.Arrays;
import java.util.Objects;

/**
 * Cave/cavern statistics for a profiled window (p.1.8.30): per-band air/fluid/
 * solid breakdown, a crossing-length histogram and the count of karst openings to
 * the surface. Immutable; the histogram array is cloned on construction. Pure
 * data.
 * <p>
 * 被分析窗口的洞穴/溶洞统计（p.1.8.30）：按带的空气/流体/固体拆分、连通长度直方图与通向
 * 地表的开口数。不可变；构造时直方图数组被克隆。纯数据。
 */
public record CaveStats(java.util.List<CaveBand> bands, long[] crossingsHistogram,
                        long surfaceOpenings) {

    /** Compact constructor copying the band list and cloning the histogram. */
    public CaveStats {
        bands = java.util.List.copyOf(bands);
        crossingsHistogram = crossingsHistogram.clone();
    }

    /** Returns the histogram as a defensive copy. */
    @Override
    public long[] crossingsHistogram() {
        return crossingsHistogram.clone();
    }

    /** Array-aware deep equality (record equals compares arrays by reference). */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CaveStats that)) {
            return false;
        }
        return surfaceOpenings == that.surfaceOpenings
                && bands.equals(that.bands)
                && java.util.Arrays.equals(crossingsHistogram, that.crossingsHistogram);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(bands, java.util.Arrays.hashCode(crossingsHistogram), surfaceOpenings);
    }
}