package io.toterra.subterra.api.worldgen.profiler;

/**
 * The geometric axis along which a {@link ProfileSlice} is taken (p.1.8.30).
 * The perpendicular pair of axes span the full profiled range, so a slice is a
 * thin axis-aligned box. Pure data; no Minecraft runtime.
 * <p>
 * {@link ProfileSlice} 沿之采集的几何轴（p.1.8.30）。其余两根轴铺满被分析的全部范围，
 * 因此切片是一个贴着轴的薄盒子。纯数据；不依赖 Minecraft 运行时。
 */
public enum ProfileAxis {
    /** The east-west axis. */
    X,
    /** The height axis. */
    Y,
    /** The north-south axis. */
    Z,
}