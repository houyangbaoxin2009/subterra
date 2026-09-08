package io.toterra.subterra.api.worldgen.profiler;

/**
 * One rendered raster plane of a {@link ProfileSlice} (p.1.8.30): a tagged grid
 * with a fixed axisIndex identifying which axis of the slice it belongs to. When
 * plane capture is disabled ({@code -1}) the row list is empty. Pure data.
 * <p>
 * {@link ProfileSlice} 的一个已渲染栅格平面（p.1.8.30）：带标记的网格，用固定的
 * axisIndex 标识它属于切片的哪根轴。当关闭平面采集（{@code -1}）时行列表为空。纯数据。
 */
public record SlicePlane(int axisIndex, java.util.List<String> rows) {

    /** Compact constructor copying the rows to an immutable snapshot. */
    public SlicePlane {
        rows = java.util.List.copyOf(rows);
    }
}