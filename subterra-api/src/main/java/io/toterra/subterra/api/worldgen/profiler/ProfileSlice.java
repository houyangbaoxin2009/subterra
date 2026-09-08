package io.toterra.subterra.api.worldgen.profiler;

/**
 * An axis-aligned slice box (p.1.8.30): the {@code [start, end)} interval along
 * {@code axis}, measured in blocks or chunks per {@code unit}; the other two axes
 * span the full profiled range. {@code end} is normalized by the caller as
 * {@code start + length}; this value only stores start/end.
 * <p>
 * 轴向切片盒（p.1.8.30）：沿 {@code axis} 的 {@code [start, end)} 区间，单位由
 * {@code unit} 决定（方块或区块）；另两根轴铺满该维全范围。{@code end} 由调用方按
 * {@code start + length} 归一；这里只存 start/end。
 */
public record ProfileSlice(ProfileAxis axis, int start, int end, SliceUnit unit,
                           boolean planes, boolean positions) {

    /** Compact constructor enforcing a non-empty interval. */
    public ProfileSlice {
        if (start >= end) {
            throw new IllegalArgumentException("start must be < end: " + start + ".." + end);
        }
    }
}