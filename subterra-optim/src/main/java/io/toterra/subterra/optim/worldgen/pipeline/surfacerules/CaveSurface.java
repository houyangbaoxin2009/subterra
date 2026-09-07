package io.toterra.subterra.optim.worldgen.pipeline.surfacerules;

/**
 * Surface side an {@code stone_depth} check anchors to (p.1.8.15, clean-room).
 * Mirrors MC {@code net.minecraft.world.level.levelgen.placement.CaveSurface}:
 * {@code floor} counts layers above the solid ground, {@code ceiling} counts
 * layers below an overhang ceiling. Pure data, deterministic.
 * <p>
 * {@code stone_depth} 检查锚定的表面侧面（p.1.8.15，洁净房）。对应 MC
 * {@code net.minecraft.world.level.levelgen.placement.CaveSurface}：{@code floor} 统计实心地面上
 * 方的层数，{@code ceiling} 统计悬挂顶棚下方的层数。纯数据、确定。
 */
public enum CaveSurface {
    /** The floor (ground) side of a solid surface. 实心表面的地面侧。 */
    FLOOR,
    /** The ceiling (overhang) side of a solid surface. 实心表面的顶棚侧。 */
    CEILING;
}