package io.toterra.subterra.profiler.core;

import io.toterra.subterra.api.worldgen.profiler.ProfileAxis;
import io.toterra.subterra.api.worldgen.profiler.ProfileSlice;
import io.toterra.subterra.api.worldgen.profiler.ProfileWindow;
import io.toterra.subterra.api.worldgen.profiler.SliceUnit;

/**
 * Geometric helpers that turn a {@link ProfileWindow} or {@link ProfileSlice}
 * into an axis-aligned block {@link Box} (p.1.8.30). All arithmetic is done on
 * {@code int} and stays safe within the radius in whole chunks.
 * <p>
 * 把 {@link ProfileWindow} 或 {@link ProfileSlice} 解析成轴向对齐的方块 {@link Box}
 * 的几何助手（p.1.8.30）。全部算术都是 {@code int}，在整区块计半径内保持安全。
 */
public final class ProfileRegion {

    private ProfileRegion() {
    }

    /**
     * An axis-aligned half-open box of integer block coordinates:
     * {@code [xFrom, xTo) x [yFrom, yTo) x [zFrom, zTo)}. Pure data.
     * <p>
     * 一个轴对齐、以整型方块坐标表示的半开盒子：
     * {@code [xFrom, xTo) x [yFrom, yTo) x [zFrom, zTo)}。纯数据。
     */
    public record Box(int xFrom, int xTo, int yFrom, int yTo, int zFrom, int zTo) {
    }

    /**
     * The full block-coordinate window box for a {@link ProfileWindow}: a
     * {@code (2*radiusChunks+1)} x {@code (2*radiusChunks+1)} area of chunks
     * centered on the window center, expanded from chunk space into block space
     * (16 blocks/chunk):
     *
     * <pre>  x : [centerX - 16*radiusChunks, centerX + 16*radiusChunks + 16)
     *   z : [centerZ - 16*radiusChunks, centerZ + 16*radiusChunks + 16)
     *   y : empty (the caller supplies vertical bounds separately)</pre>
     *
     * The result is half-open.
     * <p>
     * {@link ProfileWindow} 对应的完整方块坐标窗口盒：以窗口中心为中心的一个
     * {@code (2*radiusChunks+1)} x {@code (2*radiusChunks+1)} 区块区域，从区块空间
     * 展开到方块空间（16 方块/区块）。结果为半开。Y 范围为空，由调用方另行提供竖直范围。
     *
     * @param w the profiling window.
     * @return the half-open block box.
     */
    public static Box windowBox(ProfileWindow w) {
        int r = w.radiusChunks();
        int xFrom = Math.subtractExact(w.centerX(), 16 * r);
        int xTo = Math.addExact(w.centerX(), 16 * r + 16);
        int zFrom = Math.subtractExact(w.centerZ(), 16 * r);
        int zTo = Math.addExact(w.centerZ(), 16 * r + 16);
        return new Box(xFrom, xTo, 0, 0, zFrom, zTo);
    }

    /**
     * The box of a {@link ProfileSlice}: {@code [start, end)} along the slice
     * axis (converted from block or chunk units via {@link SliceUnit}, 16
     * blocks/chunk), while the two perpendicular axes span their full passed
     * ranges. Per this class's convention the horizontal full range is supplied
     * once in {@code (zFrom, zTo)} and applied to whichever horizontal axes are
     * not fixed by the slice; the vertical full range is always {@code
     * (yFrom, yTo)}:
     *
     * <pre>
     *   axis X : Box(start, end, yFrom, yTo, zFrom, zTo)
     *   axis Y : Box(zFrom, zTo, start, end, zFrom, zTo)
     *   axis Z : Box(zFrom, zTo, yFrom, yTo, start, end)
     * </pre>
     *
     * The vertical and horizontal caller ranges are supplied by the caller (the
     * MC-side dimension bounds); a pure-JDK probe may pass any values. The
     * result is half-open.
     * <p>
     * {@link ProfileSlice} 的盒：沿切片轴的 {@code [start, end)}（按 {@link
     * SliceUnit} 从方块或区块单位换算，16 方块/区块），而另两轴铺满其传入的完整范围。
     * 按本类约定，水平完整范围在 {@code (zFrom, zTo)} 中提供一次，并应用到未被切片固定
     * 的水平轴；竖直完整范围恒为 {@code (yFrom, yTo)}。竖直/水平调用方范围由调用方
     * （MC 侧维度界）提供；纯 JDK 探针可传任意值。结果为半开。
     *
     * @param s    the slice definition.
     * @param yFrom the low end of the vertical full range.
     * @param yTo   the exclusive high end of the vertical full range.
     * @param zFrom the low end of the horizontal full range.
     * @param zTo   the exclusive high end of the horizontal full range.
     * @return the half-open block box.
     */
    public static Box sliceBox(ProfileSlice s, int yFrom, int yTo, int zFrom, int zTo) {
        int scale = s.unit() == SliceUnit.CHUNK ? 16 : 1;
        int start = s.start() * scale;
        int end = s.end() * scale;
        switch (s.axis()) {
            case X:
                return new Box(start, end, yFrom, yTo, zFrom, zTo);
            case Y:
                return new Box(zFrom, zTo, start, end, zFrom, zTo);
            case Z:
            default:
                return new Box(zFrom, zTo, yFrom, yTo, start, end);
        }
    }
}