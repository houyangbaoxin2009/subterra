package io.toterra.subterra.optim.worldgen.pipeline.dimension;

/**
 * The water-class field {@code (x, z) -> WaterClass} (Step 3, hydro dual-slot):
 * a pure, deterministic 2-D sampler classifying a position's water body type.
 * The default is {@link VanillaWaterClass}, which yields only {@link WaterClass#NONE}
 * and {@link WaterClass#OCEAN}; future hydrology algorithms replace the sampler.
 * <p>
 * 水域类型场 {@code (x, z) -> WaterClass}（第 3 步，水文双插槽）：对位置做水体类型
 * 分类的纯确定 2-D 采样器。默认为 {@link VanillaWaterClass}，只产出
 * {@link WaterClass#NONE} 与 {@link WaterClass#OCEAN}；未来水文算法替换采样器。
 */
@FunctionalInterface
public interface WaterClassSampler {

    /**
     * Returns the water class at the given position.
     *
     * @param x the x block coordinate
     * @param z the z block coordinate
     * @return the water class at that position
     */
    WaterClass sample(double x, double z);
}