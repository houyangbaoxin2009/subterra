package io.toterra.subterra.engine.worldgen.pipeline.dimension;

/**
 * The water-surface height field {@code (x, z) -> y} (Step 3, hydro dual-slot):
 * a pure, deterministic 2-D sampler returning the y coordinate of the water
 * surface at an (x, z) position. Consumers are the terrain height clamp, the
 * surface underwater variants and biome zoning. Future hydrology algorithms
 * replace only the samplers (a different {@code WaterLevel} implementation or
 * {@link WaterClassSampler}), never the consumer call sites.
 * <p>
 * 水面高度场 {@code (x, z) -> y}（第 3 步，水文双插槽）：返回某 (x, z) 位置水面 y
 * 坐标的纯确定 2-D 采样器。消费方为 terrain 高度钳制、surface 水下变体与群系区划。
 * 未来水文算法只替换采样器（不同的 {@code WaterLevel} 实现或
 * {@link WaterClassSampler}），从不改动消费方调用点。
 */
@FunctionalInterface
public interface WaterLevel {

    /**
     * Returns the water-surface height y at the given position.
     *
     * @param x the x block coordinate
     * @param z the z block coordinate
     * @return the water-surface height y
     */
    double level(double x, double z);
}