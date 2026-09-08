package io.toterra.subterra.engine.worldgen.pipeline.surfacerules;

import io.toterra.subterra.engine.worldgen.pipeline.surface.SurfaceContext;

/**
 * Extended, immutable snapshot of a single block position for evaluating the
 * vanilla {@code SurfaceRules} vocabulary (p.1.8.15, clean-room). Mirrors the
 * p.1.8.5 {@link SurfaceContext} fields (x/y/z, biomeTag, density) and adds the
 * per-column surface state the richer vanilla conditions need: surfaceDepth,
 * stoneDepthAbove/stoneDepthBelow, the water height, a secondary (turbulence)
 * scalar and a surface-height provider for setback (steep) checks. Pure data;
 * the surface-height provider may be {@code null}, in which case steepness checks
 * deterministically report "flat".
 * <p>
 * 求解 vanilla {@code SurfaceRules} 词汇所需的扩展不可变方块位置快照（p.1.8.15，洁净房）。
 * 继承 p.1.8.5 {@link SurfaceContext} 字段（x/y/z、biomeTag、density），并补充更丰富的
 * vanilla 条件所需的分列表面状态：surfaceDepth、stoneDepthAbove/Below、水体高度、
 * 次级（湍流）标量与用于陡坡判定的表面高度提供者。纯数据；表面高度提供者可空，此时
 * 陡坡判定确定性地视为"平坦"。
 *
 * @param x                block x-coordinate
 * @param y                block y-coordinate
 * @param z                block z-coordinate
 * @param biomeTag         biome tag (may be {@code null})
 * @param density          injected density value (default {@code 0})
 * @param surfaceDepth     surface-depth trail count ({@code 0} = on the solid surface)
 * @param stoneDepthAbove  number of stone layers above the block on the floor side
 * @param stoneDepthBelow  number of stone layers below the block on the ceiling side
 * @param waterHeight      water surface y (or {@link #NO_WATER} when no water source)
 * @param surfaceSecondary secondary-depth scalar in [-1, 1] for stone-depth variants
 * @param surfaceHeightAt  column surface-height provider (x,z) -> y (nullable)
 */
public record VSurfaceContext(
        int x,
        int y,
        int z,
        String biomeTag,
        double density,
        int surfaceDepth,
        int stoneDepthAbove,
        int stoneDepthBelow,
        int waterHeight,
        double surfaceSecondary,
        HeightAt surfaceHeightAt) {

    /** Sentinel meaning "no water source in this column" (matches MC {@code Context} default). */
    public static final int NO_WATER = Integer.MIN_VALUE;

    /** Column surface-height provider: {@code (x, z) -> surfaceY}. */
    @FunctionalInterface
    public interface HeightAt {
        int height(int x, int z);
    }

    /**
     * Position-only context: no biome, zero density/surface, no water, flat ground.
     * 仅坐标上下文：无群系、零密度/表面、无水、平地。
     */
    public static VSurfaceContext at(int x, int y, int z) {
        return new VSurfaceContext(x, y, z, null, 0, 0, 0, 0, NO_WATER, 0, null);
    }

    /** Position with biome tag. 坐标与群系标签。 */
    public static VSurfaceContext at(int x, int y, int z, String biomeTag) {
        return new VSurfaceContext(x, y, z, biomeTag, 0, 0, 0, 0, NO_WATER, 0, null);
    }

    /** Copy with the surface-depth trail count set. 复制并设置表面深度计数。 */
    public VSurfaceContext withSurfaceDepth(int d) {
        return new VSurfaceContext(x, y, z, biomeTag, density, d, stoneDepthAbove, stoneDepthBelow, waterHeight, surfaceSecondary, surfaceHeightAt);
    }

    /** Copy with the floor-side stone-depth layers set. 复制并设置地表侧岩层深度。 */
    public VSurfaceContext withStoneDepthAbove(int d) {
        return new VSurfaceContext(x, y, z, biomeTag, density, surfaceDepth, d, stoneDepthBelow, waterHeight, surfaceSecondary, surfaceHeightAt);
    }

    /** Copy with the ceiling-side stone-depth layers set. 复制并设置顶棚侧岩层深度。 */
    public VSurfaceContext withStoneDepthBelow(int d) {
        return new VSurfaceContext(x, y, z, biomeTag, density, surfaceDepth, stoneDepthAbove, d, waterHeight, surfaceSecondary, surfaceHeightAt);
    }

    /** Copy with the water surface y set ({@code NO_WATER} clears it). 复制并设置水体高度。 */
    public VSurfaceContext withWaterHeight(int h) {
        return new VSurfaceContext(x, y, z, biomeTag, density, surfaceDepth, stoneDepthAbove, stoneDepthBelow, h, surfaceSecondary, surfaceHeightAt);
    }

    /** Copy with the column surface-height provider set. 复制并设置表面高度提供者。 */
    public VSurfaceContext withHeightAt(HeightAt provider) {
        return new VSurfaceContext(x, y, z, biomeTag, density, surfaceDepth, stoneDepthAbove, stoneDepthBelow, waterHeight, surfaceSecondary, provider);
    }

    /** Copy with the secondary-depth scalar set. 复制并设置次级深度标量。 */
    public VSurfaceContext withSurfaceSecondary(double s) {
        return new VSurfaceContext(x, y, z, biomeTag, density, surfaceDepth, stoneDepthAbove, stoneDepthBelow, waterHeight, s, surfaceHeightAt);
    }

    /** Copy with the density set. 复制并设置密度。 */
    public VSurfaceContext withDensity(double d) {
        return new VSurfaceContext(x, y, z, biomeTag, d, surfaceDepth, stoneDepthAbove, stoneDepthBelow, waterHeight, surfaceSecondary, surfaceHeightAt);
    }

    /** The surface height of this exact column, or {@code 0} when no provider is set. */
    public int surfaceHeight() {
        return surfaceHeightAt == null ? 0 : surfaceHeightAt.height(x, z);
    }

    /** Projects this extended context onto the p.1.8.5 seam context. 投影到 p.1.8.5 接缝上下文。 */
    public SurfaceContext toLegacy() {
        return new SurfaceContext(x, y, z, biomeTag, density);
    }
}