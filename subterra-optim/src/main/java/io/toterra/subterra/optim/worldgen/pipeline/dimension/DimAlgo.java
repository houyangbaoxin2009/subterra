package io.toterra.subterra.optim.worldgen.pipeline.dimension;

/**
 * Algorithm identifier for a dimension slot (p.1.8.2, Step 2). Each value
 * describes how the slot's {@link io.toterra.subterra.optim.worldgen.pipeline.density.Density}
 * field is computed:
 * <ul>
 *   <li>{@link #VANILLA} — vanilla-compatible mirror noise (octave Perlin,
 *       normal noise, etc.)</li>
 *   <li>{@link #FORMULA} — math formula via the pipeline's formula engine
 *       (p.1.8.10, enters via {@link io.toterra.subterra.optim.worldgen.pipeline.dimension.DimensionSlot#with})</li>
 *   <li>{@link #CONSTANT} — constant value with no spatial variation</li>
 *   <li>{@link #MODEL} — learned model / neural network (reserved, not yet
 *       implemented)</li>
 * </ul>
 * <p>
 * 维度槽的算法标识（p.1.8.2，第 2 步）。每值描述该槽的
 * {@link io.toterra.subterra.optim.worldgen.pipeline.density.Density} 场如何计算。
 */
public enum DimAlgo {

    VANILLA,
    FORMULA,
    CONSTANT,
    MODEL;

    /**
     * Resolves a td token (case-insensitive) to a {@link DimAlgo}.
     *
     * @param token the token string; null or blank resolves to {@code null}
     * @return the matching enum constant, or {@code null} for a blank token
     * @throws IllegalArgumentException if the token is non-blank but matches no
     *         algorithm (unknown algorithm, per the Step 2 rejection contract)
     */
    public static DimAlgo fromTd(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        for (DimAlgo algo : values()) {
            if (algo.name().equalsIgnoreCase(token.trim())) {
                return algo;
            }
        }
        throw new IllegalArgumentException("unknown DimAlgo td token: " + token);
    }
}