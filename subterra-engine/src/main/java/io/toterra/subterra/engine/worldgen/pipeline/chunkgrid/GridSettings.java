package io.toterra.subterra.engine.worldgen.pipeline.chunkgrid;

import io.toterra.subterra.engine.worldgen.pipeline.dimension.OverworldBounds;

/**
 * Immutable grid configuration (p.1.8.13, self-developed, mirroring the block
 * space a single per-chunk {@code NoiseChunk} covers): horizontal and vertical
 * cell sizes, the vertical window {@code [minY, minY + height)}, the sea level,
 * the horizontal cell count and the grid's north-west origin. Only the
 * {@link OverworldBounds} accessors {@code minY()}/{@code height()}/
 * {@code seaLevel()} are read, so this class depends on that record's field
 * surface alone.
 * <p>
 * Verified against MC 1.21.1 (named joined jar, {@code NoiseSettings}): the
 * overworld noise size is {@code size_horizontal=1, size_vertical=2}, and
 * {@code getCellWidth = toBlock(size_horizontal) = 4}, {@code getCellHeight =
 * toBlock(size_vertical) = 8} — hence the defaults below. {@code NoiseChunk}
 * derives {@code cellCountY = floorDiv(height, cellHeight)}; vanilla heights
 * (384, 512, …) are multiples of 16 and therefore always divisible by the cell
 * height, which is why this grid enforces {@code height % cellHeight == 0}.
 * <p>
 * 不可变网格配置（p.1.8.13，自研，镜像单个按区块 {@code NoiseChunk} 覆盖的方块空间）：
 * 水平与垂直单元尺寸、纵向窗口 {@code [minY, minY + height)}、海平面、水平单元数与
 * 网格西北原点。仅读取 {@link OverworldBounds} 的 {@code minY()}/{@code height()}/
 * {@code seaLevel()} 访问器，故本类只依赖该记录字段面。
 * <p>
 * 对照 MC 1.21.1 验证（命名合并 jar，{@code NoiseSettings}）：主世界噪声尺寸为
 * {@code size_horizontal=1, size_vertical=2}，且 {@code getCellWidth = toBlock(水平尺寸)
 * = 4}、{@code getCellHeight = toBlock(垂直尺寸) = 8} —— 故采用下列默认值。
 * {@code NoiseChunk} 推得 {@code cellCountY = floorDiv(height, cellHeight)}；原版高度
 * （384、512…）皆为 16 的倍数，恒能被单元高度整除，因此本网格强制
 * {@code height % cellHeight == 0}。
 *
 * @param cellWidth   horizontal cell size in blocks (overworld default 4)
 * @param cellHeight  vertical cell size in blocks (overworld default 8)
 * @param minY        lowest block y of the window (inclusive)
 * @param height      vertical extent in blocks (&gt; 0, multiple of {@code cellHeight})
 * @param seaLevel    water surface y (inside {@code [minY, minY + height)})
 * @param cellCountXZ horizontal cell count (default 4 = one 16×16 chunk)
 * @param originX     block x of the grid's south-west corner cell
 * @param originZ     block z of the grid's south-west corner cell
 */
public record GridSettings(
        int cellWidth,
        int cellHeight,
        int minY,
        int height,
        int seaLevel,
        int cellCountXZ,
        int originX,
        int originZ) {

    /** Overworld horizontal cell width (MC {@code NoiseSettings.size_horizontal=1}). */
    public static final int DEFAULT_CELL_WIDTH = 4;

    /** Overworld vertical cell height (MC {@code NoiseSettings.size_vertical=2}). */
    public static final int DEFAULT_CELL_HEIGHT = 8;

    /** Horizontal cells per standard 16×16 chunk ({@code 16 / 4}). */
    public static final int DEFAULT_CELL_COUNT_XZ = 4;

    /**
     * Compact constructor validating the grid invariant via
     * IllegalArgumentException: positive cell sizes, positive height that is a
     * multiple of {@code cellHeight} (as vanilla always satisfies), a positive
     * horizontal cell count, and a sea level inside the window.
     */
    public GridSettings {
        if (cellWidth <= 0) {
            throw new IllegalArgumentException("cellWidth must be > 0: " + cellWidth);
        }
        if (cellHeight <= 0) {
            throw new IllegalArgumentException("cellHeight must be > 0: " + cellHeight);
        }
        if (height <= 0) {
            throw new IllegalArgumentException("height must be > 0: " + height);
        }
        if (height % cellHeight != 0) {
            throw new IllegalArgumentException(
                    "height must be a multiple of cellHeight (" + cellHeight + "): " + height);
        }
        if (cellCountXZ <= 0) {
            throw new IllegalArgumentException("cellCountXZ must be > 0: " + cellCountXZ);
        }
        if (seaLevel < minY || seaLevel >= minY + height) {
            throw new IllegalArgumentException(
                    "seaLevel must be in [" + minY + ", " + (minY + height) + "): " + seaLevel);
        }
    }

    /**
     * Default overworld grid over the given vertical window: cell 4×4×8, one
     * chunk horizontally ({@code cellCountXZ=4}) and south-west corner at block
     * origin (0, 0).
     */
    public static GridSettings from(OverworldBounds bounds) {
        return new GridSettings(
                DEFAULT_CELL_WIDTH,
                DEFAULT_CELL_HEIGHT,
                bounds.minY(),
                bounds.height(),
                bounds.seaLevel(),
                DEFAULT_CELL_COUNT_XZ,
                0,
                0);
    }

    /** Number of cells along Y: {@code height / cellHeight} (always divisible). */
    public int cellCountY() {
        return height / cellHeight;
    }

    /** The block y just above the window's top ({@code minY + height}). */
    public int maxY() {
        return minY + height;
    }

    /** Horizontal block extent: {@code cellCountXZ * cellWidth}. */
    public int xSize() {
        return cellCountXZ * cellWidth;
    }

    /** Horizontal block extent along Z: {@code cellCountXZ * cellWidth}. */
    public int zSize() {
        return cellCountXZ * cellWidth;
    }
}