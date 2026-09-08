package io.toterra.subterra.engine.worldgen.pipeline.curve;

/**
 * 大陆度/海拔曲线（noise_settings 面的确定性等价）：把地表地形固化为可测整数偏置对象。
 * Continentality / elevation curve (deterministic equivalent of the
 * noise_settings face): a measurable, integer-typed terrain surface object built
 * on {@link GroundSurface}. Included semantics:
 * <ul>
 *   <li>{@code surfaceY} = {@code floor} of the ground height (for integer asserts);</li>
 *   <li>{@code isOcean} = a column is underwater, i.e. {@code height < seaLevel}
 *       (a column sitting exactly at sea level is NOT ocean).</li>
 * </ul>
 *
 * @param continentality fBm floor driving the terrain (not null)
 * @param seaLevel       water surface level (blocks)
 * @param baseHeight     base block height lifted onto the scaled field
 * @param amplitude      vertical variation (must be >= 0)
 */
public record TerrainProfile(FbmNoise continentality, int seaLevel, int baseHeight, int amplitude) {

    /**
     * @throws IllegalArgumentException when continentality is null or amplitude < 0
     */
    public TerrainProfile {
        if (continentality == null) {
            throw new IllegalArgumentException("continentality must not be null");
        }
        if (amplitude < 0) {
            throw new IllegalArgumentException("amplitude must be >= 0: " + amplitude);
        }
    }

    /** Delegate producing the raw continuous ground height for a column. */
    private GroundSurface ground() {
        return new GroundSurface(continentality, baseHeight, amplitude, seaLevel);
    }

    /** 地面整型标高：地表高度的向下取整（供探针断言整数）。Integer surface level. */
    public int surfaceY(int x, int z) {
        return (int) Math.floor(ground().height(x, z));
    }

    /** 是否海洋列（高度低于海平面）。True when the column is ocean (below sea level). */
    public boolean isOcean(int x, int z) {
        return ground().isUnderwater(x, z);
    }
}