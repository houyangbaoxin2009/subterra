package io.toterra.subterra.engine.worldgen.pipeline.curve;

/**
 * 基于 fBm 的确定性地表高度场。
 * Deterministic fBm-driven ground height field. Produces a terrain height from a
 * single fBm floor (evaluated at y == 0), scaled by {@code amplitude} and lifted
 * by {@code baseHeight}; a {@code seaLevel} marks the water boundary.
 */
public final class GroundSurface {

    private final FbmNoise fbm;
    private final double baseHeight;
    private final double amplitude;
    private final double seaLevel;

    /**
     * @param fbm        fBm floor used for the height field (must not be null)
     * @param baseHeight base block height added to the scaled field
     * @param amplitude  vertical variation applied to the normalized field (finite, >= 0)
     * @param seaLevel   water surface level in blocks
     */
    public GroundSurface(FbmNoise fbm, double baseHeight, double amplitude, double seaLevel) {
        if (fbm == null) {
            throw new IllegalArgumentException("fbm must not be null");
        }
        if (!(amplitude >= 0) || Double.isNaN(amplitude) || Double.isInfinite(amplitude)) {
            throw new IllegalArgumentException("amplitude must be a finite non-negative value: " + amplitude);
        }
        this.fbm = fbm;
        this.baseHeight = baseHeight;
        this.amplitude = amplitude;
        this.seaLevel = seaLevel;
    }

    /** 2D 地形高度（3D 实现：eval(x, 0, z)）。Ground height at (x, z). */
    public double height(int x, int z) {
        return fbm.eval(x, 0.0, z) * amplitude + baseHeight;
    }

    /** 该列是否低于海平面（水面下）。True when the column lies below sea level. */
    public boolean isUnderwater(int x, int z) {
        return height(x, z) < seaLevel;
    }

    /**
     * 地表标高：当列高于或等于海平面时返回真实高度，否则返回海平面标高（水面填充淹没的凹谷）。
     * Surface elevation: returns the true height when it is at/above sea level,
     * otherwise returns the sea-level elevation (water fills drowned valleys).
     */
    public double terrainAt(int x, int z) {
        double h = height(x, z);
        return h >= seaLevel ? h : seaLevel;
    }
}