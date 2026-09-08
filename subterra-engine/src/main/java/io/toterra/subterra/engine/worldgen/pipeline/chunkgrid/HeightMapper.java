package io.toterra.subterra.engine.worldgen.pipeline.chunkgrid;

/**
 * Density→height "find surface" mapper (p.1.8.13, self-developed): for a column
 * at {@code (x, z)} it scans the interpolated density grid from the top of the
 * window downwards and locates the surface — mirroring MC's
 * {@code getHeight}/{@code getFloorHeight} column-evaluation semantics where a
 * block is solid when the interpolated density is non-negative.
 * <p>
 * Semantics (deterministic, pure over {@code DensityGrid} + settings):
 * <ul>
 * <li>{@link #getSurfaceY} returns the highest solid block y in the window,
 *     or {@code minY} (the void) when the column is entirely air;</li>
 * <li>{@link #getHeight} returns the first non-solid y above the topmost solid
 *     (i.e. surface + 1); for an entirely solid column that equals
 *     {@code minY + height} (the build ceiling), for an empty column it equals
 *     {@code minY} — matching vanilla's all-solid→buildLimit / all-empty→minY;</li>
 * <li>{@link #isUnderwater} reports whether the surface lies below the sea
 *     level.</li>
 * </ul>
 * A column scan is linear in the window height (each {@code valueAt} is O(1)),
 * i.e. O(height) block steps, no O(n²).
 * <p>
 * 密度→高度"找地表"映射器（p.1.8.13，自研）：对 {@code (x, z)} 处的一柱，从窗口顶部向下
 * 扫描插值密度网格以定位地表 —— 镜像 MC 的 {@code getHeight}/{@code getFloorHeight}
 * 柱求值语义：当插值密度非负时方块视为实心。
 * <p>
 * 语义（确定、对 {@code DensityGrid} 与设置的纯函数）：
 * <ul>
 * <li>{@link #getSurfaceY} 返回窗口内最高实心方块的 y，整柱为空（空气）时返回
 *     {@code minY}（虚空）；</li>
 * <li>{@link #getHeight} 返回最高实心方块之上第一个非实心 y（即地表 + 1）；整柱实心时
 *     等于 {@code minY + height}（建筑顶），空柱时等于 {@code minY} —— 与
 *     原版全实心→建筑顶 / 全空→minY 一致；</li>
 * <li>{@link #isUnderwater} 报告地表是否位于海平面之下。</li>
 * </ul>
 * 柱扫描按窗口高度线性（每个 {@code valueAt} 为 O(1)），即 O(height) 方块步，无 O(n²)。
 */
public final class HeightMapper {

    private final GridSettings settings;

    public HeightMapper(GridSettings settings) {
        this.settings = settings;
    }

    /** The grid configuration this mapper was built for. */
    public GridSettings settings() {
        return settings;
    }

    /**
     * Highest solid block y in the column, or {@code minY} when entirely air.
     * Complexity: O(height) block steps (each {@code valueAt} is O(1)).
     */
    public int getSurfaceY(DensityGrid grid, int x, int z) {
        int top = settings.maxY() - 1;
        for (int y = top; y >= settings.minY(); y--) {
            if (grid.valueAt(x, y, z) >= 0.0) {
                return y;
            }
        }
        return settings.minY();
    }

    /**
     * The height of the column: first non-solid y above the topmost solid, with
     * the {@code minY} sentinel standing for "no surface found". All solid →
     * {@code minY + height} (build ceiling); all air → {@code minY} (void).
     * Note: a column whose surface would fall exactly on {@code minY} is
     * reported as the void sentinel by construction (documented convention).
     */
    public int getHeight(DensityGrid grid, int x, int z) {
        int surface = getSurfaceY(grid, x, z);
        return surface == settings.minY() ? settings.minY() : surface + 1;
    }

    /** True when the surface lies below the sea level ({@code surface < seaLevel}). */
    public boolean isUnderwater(DensityGrid grid, int x, int z) {
        return getSurfaceY(grid, x, z) < settings.seaLevel();
    }
}