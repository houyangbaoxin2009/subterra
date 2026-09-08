package io.toterra.subterra.engine.worldgen.pipeline.dimension;

import io.toterra.subterra.engine.worldgen.pipeline.density.Density;

/**
 * The stateless default water-class sampler (Step 3): returns
 * {@link WaterClass#OCEAN} where the terrain dimension's field sits below the
 * sea level and {@link WaterClass#NONE} otherwise — a vanilla-equivalent
 * fallback. It never returns a {@link WaterClass#isReserved() reserved} class.
 * Stateless (the terrain {@link Density} is immutable and {@code seaLevel} is a
 * scalar), deterministic and allocation-free in {@link #sample}.
 * <p>
 * 无状态的默认水域类型采样器（第 3 步）：terrain 维场低于海平面时返回
 * {@link WaterClass#OCEAN}，否则返回 {@link WaterClass#NONE}——原版等价回推。
 * 永不返回 {@link WaterClass#isReserved() 预留}类型。无状态（terrain {@link Density}
 * 不可变、{@code seaLevel} 为标量）、确定且 {@link #sample} 无分配。
 *
 * @param terrainHeight the terrain dimension field, used as a 2-D height proxy
 * @param seaLevel      the water/land boundary height
 */
public record VanillaWaterClass(Density terrainHeight, double seaLevel) implements WaterClassSampler {

    /** Compact constructor rejecting a null terrain field via IllegalArgumentException. */
    public VanillaWaterClass {
        if (terrainHeight == null) {
            throw new IllegalArgumentException("terrainHeight must not be null");
        }
    }

    /**
     * Classifies the position as {@link WaterClass#OCEAN} when the terrain field
     * falls below {@link #seaLevel()}, else {@link WaterClass#NONE}. The field is
     * evaluated on a canonical y reference plane, since the default terrain slot
     * is a 2-D height proxy.
     */
    @Override
    public WaterClass sample(double x, double z) {
        return terrainHeight.eval(x, 0.0, z) < seaLevel ? WaterClass.OCEAN : WaterClass.NONE;
    }
}