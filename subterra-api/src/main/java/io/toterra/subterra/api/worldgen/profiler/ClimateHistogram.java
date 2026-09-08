package io.toterra.subterra.api.worldgen.profiler;

import java.util.Arrays;
import java.util.Objects;

/**
 * A histogram of one climate/router field across a profiled window (p.1.8.30):
 * the field name, observed min/max and fixed-bucket counts. Immutable; the bucket
 * array is cloned on construction. Pure data.
 * <p>
 * 被分析窗口内某个气候/路由字段的直方图（p.1.8.30）：字段名、观测到的 min/max 与固定桶
 * 的计数。不可变；构造时桶数组被克隆。纯数据。
 */
public record ClimateHistogram(String field, double min, double max, int[] buckets,
                               int bucketCount) {

    /** Compact constructor cloning the buckets array. */
    public ClimateHistogram {
        buckets = buckets.clone();
    }

    /** Returns the buckets as a defensive copy. */
    @Override
    public int[] buckets() {
        return buckets.clone();
    }

    /** Array-aware deep equality (record equals compares arrays by reference). */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClimateHistogram that)) {
            return false;
        }
        return min == that.min && max == that.max && bucketCount == that.bucketCount
                && field.equals(that.field)
                && java.util.Arrays.equals(buckets, that.buckets);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(field, min, max, java.util.Arrays.hashCode(buckets), bucketCount);
    }
}