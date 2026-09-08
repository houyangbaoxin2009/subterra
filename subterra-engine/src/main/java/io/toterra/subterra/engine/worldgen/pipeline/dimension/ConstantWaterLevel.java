package io.toterra.subterra.engine.worldgen.pipeline.dimension;

/**
 * The flat, seed-independent water level (Step 3): a {@link WaterLevel} that
 * returns a single constant height at every (x, z) position. The default hydro
 * construction uses {@link DimensionTerrain#HYDRO_SEA_LEVEL} (63), matching
 * vanilla. Immutable and deterministic.
 * <p>
 * 平坦的、与种子无关的水位（第 3 步）：在每个 (x, z) 都返回同一常量的
 * {@link WaterLevel}。默认水文构造使用 {@link DimensionTerrain#HYDRO_SEA_LEVEL}
 * （63），与原版一致。不可变、确定。
 *
 * @param value the constant water-surface height y
 */
public record ConstantWaterLevel(double value) implements WaterLevel {

    /** {@inheritDoc} — always returns the wrapped {@code value}. */
    @Override
    public double level(double x, double z) {
        return value;
    }
}