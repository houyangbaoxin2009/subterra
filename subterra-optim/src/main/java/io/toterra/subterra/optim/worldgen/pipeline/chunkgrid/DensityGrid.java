package io.toterra.subterra.optim.worldgen.pipeline.chunkgrid;

import io.toterra.subterra.optim.worldgen.pipeline.density.Density;
import java.util.Objects;

/**
 * The 4×4×8 cell sampler (p.1.8.13, self-developed, mirroring how MC 1.21.1's
 * {@code NoiseChunk} turns a final density into a cached corner grid): samples
 * the given {@link Density} at every cell corner of the
 * {@code [minY, minY + height]} window and interpolates between them with
 * {@link Trilinear}. Allocation and {@code Density.eval} happen only at build
 * time; every query is allocation-free.
 * <p>
 * Cells span {@code cellWidth × cellHeight × cellWidth} blocks. There are
 * {@code cellCountXZ + 1} corner samples per horizontal axis
 * ({@code cellCountXZ = settings.cellCountXZ()}) and
 * {@code cellCountY + 1} samples vertically ({@code cellCountY =
 * height / cellHeight}). Corner {@code (i, j, k)} sits at
 * {@code (originX + i*cellWidth, minY + j*cellHeight, originZ + k*cellWidth)},
 * matching {@code NoiseChunk.cellStartBlockY = (cellNoiseMinY + j) * cellHeight}
 * with {@code cellNoiseMinY = floorDiv(minY, cellHeight)}. Cell-index arithmetic
 * ({@link #cellX}/{@link #cellY}/{@link #cellZ}) mirrors MC's {@code cellStartX}
 * family via {@link Math#floorDiv}.
 * <p>
 * Boundary semantics: for block coords outside the {@code [minY, minY + height)}
 * window (or beyond the horizontal grid), {@link #valueAt} clamps to the edge
 * cell so the returned value stays constant at the boundary sample — the
 * "constant above top / below bottom" clamp of this core.
 * <p>
 * 4×4×8 单元采样器（p.1.8.13，自研，镜像 MC 1.21.1 {@code NoiseChunk} 将最终密度转为缓存
 * 角网格的方式）：在 {@code [minY, minY + height]} 窗口的每个单元角采样给定
 * {@link Density}，并用 {@link Trilinear} 在角之间插值。分配与 {@code Density.eval}
 * 仅在构建时发生；每次查询均无分配。
 * <p>
 * 单元占据 {@code cellWidth × cellHeight × cellWidth} 方块。每条水平轴有
 * {@code cellCountXZ + 1} 个角采样（{@code cellCountXZ = settings.cellCountXZ()}），
 * 垂直方向 {@code cellCountY + 1} 个（{@code cellCountY = height / cellHeight}）。角
 * {@code (i, j, k)} 位于 {@code (originX + i*cellWidth, minY + j*cellHeight,
 * originZ + k*cellWidth)}，与 {@code NoiseChunk.cellStartBlockY =
 * (cellNoiseMinY + j) * cellHeight}（其中 {@code cellNoiseMinY = floorDiv(minY,
 * cellHeight)}）一致。单元索引运算（{@link #cellX}/{@link #cellY}/{@link #cellZ}）通过
 * {@link Math#floorDiv} 镜像 MC 的 {@code cellStartX} 族。
 * <p>
 * 边界语义：对于超出 {@code [minY, minY + height]} 窗口（或超出水平网格）的方块坐标，
 * {@link #valueAt} 收敛到边缘单元，使返回值在边界采样处保持恒定 —— 即本核心的
 * "顶部之上 / 底部之下取常量"收敛。
 */
public final class DensityGrid {

    private final GridSettings settings;
    private final Density density;
    private final double[][][] corners; // indexed [cellX][cellY][cellZ]
    private final int cellCountXZ;
    private final int cellCountY;

    /**
     * Builds the grid by evaluating {@code density} at every corner of the
     * window. O(cellCountXZ² × cellCountY) allocations-and-evaluations.
     */
    public DensityGrid(GridSettings settings, Density density) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.density = Objects.requireNonNull(density, "density");
        this.cellCountXZ = settings.cellCountXZ();
        this.cellCountY = settings.cellCountY();
        int cw = settings.cellWidth();
        int ch = settings.cellHeight();
        int ox = settings.originX();
        int oy = settings.minY();
        int oz = settings.originZ();
        this.corners = new double[cellCountXZ + 1][cellCountY + 1][cellCountXZ + 1];
        for (int i = 0; i <= cellCountXZ; i++) {
            double bx = ox + i * cw;
            for (int j = 0; j <= cellCountY; j++) {
                double by = oy + j * ch;
                for (int k = 0; k <= cellCountXZ; k++) {
                    corners[i][j][k] = density.eval(bx, by, oz + k * cw);
                }
            }
        }
    }

    /** The grid configuration this sampler was built from. */
    public GridSettings settings() {
        return settings;
    }

    /** The underlying density (retained for reference; not queried on hot path). */
    public Density density() {
        return density;
    }

    /** Horizontal cell count. */
    public int cellCountXZ() {
        return cellCountXZ;
    }

    /** Vertical cell count ({@code height / cellHeight}). */
    public int cellCountY() {
        return cellCountY;
    }

    /** The raw corner value at cell-corners {@code (i, j, k)}. */
    public double corner(int i, int j, int k) {
        return corners[i][j][k];
    }

    /** Cell index along X for block {@code x} (floor-division, mirrors cellStartX). */
    public int cellX(int x) {
        return Math.floorDiv(x - settings.originX(), settings.cellWidth());
    }

    /** Cell index along Y for block {@code y} (mirrors MC's vertical cell index). */
    public int cellY(int y) {
        return Math.floorDiv(y - settings.minY(), settings.cellHeight());
    }

    /** Cell index along Z for block {@code z} (floor-division, mirrors cellStartZ). */
    public int cellZ(int z) {
        return Math.floorDiv(z - settings.originZ(), settings.cellWidth());
    }

    /** The block x of {@code x}'s cell's low corner ({@code originX + cellX(x)*cellWidth}). */
    public int cellStartX(int x) {
        return settings.originX() + cellX(x) * settings.cellWidth();
    }

    /** The block y of {@code y}'s cell's low corner ({@code minY + cellY(y)*cellHeight}). */
    public int cellStartY(int y) {
        return settings.minY() + cellY(y) * settings.cellHeight();
    }

    /** The block z of {@code z}'s cell's low corner ({@code originZ + cellZ(z)*cellWidth}). */
    public int cellStartZ(int z) {
        return settings.originZ() + cellZ(z) * settings.cellWidth();
    }

    /**
     * Interpolated density at block {@code (x, y, z)}, allocating nothing.
     * Coords outside the window (or the horizontal grid) are clamped to the
     * edge cell, keeping the result constant at the boundary sample.
     * <p>
     * 方块 {@code (x, y, z)} 处的插值密度，无任何分配。超出窗口（或水平网格）的坐标
     * 收敛到边缘单元，使结果在边界采样处保持恒定。
     */
    public double valueAt(int x, int y, int z) {
        int cx = clamp(cellX(x), 0, cellCountXZ - 1);
        int cy = clamp(cellY(y), 0, cellCountY - 1);
        int cz = clamp(cellZ(z), 0, cellCountXZ - 1);
        int cw = settings.cellWidth();
        int ch = settings.cellHeight();
        double dX = Trilinear.clamp01((x - (settings.originX() + cx * cw)) / (double) cw);
        double dY = Trilinear.clamp01((y - (settings.minY() + cy * ch)) / (double) ch);
        double dZ = Trilinear.clamp01((z - (settings.originZ() + cz * cw)) / (double) cw);
        int i1 = cx + 1, j1 = cy + 1, k1 = cz + 1;
        double[][][] c = corners;
        return Trilinear.interpolate(
                c[cx][cy][cz], c[i1][cy][cz], c[cx][cy][k1], c[i1][cy][k1],
                c[cx][j1][cz], c[i1][j1][cz], c[cx][j1][k1], c[i1][j1][k1],
                dX, dY, dZ);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}