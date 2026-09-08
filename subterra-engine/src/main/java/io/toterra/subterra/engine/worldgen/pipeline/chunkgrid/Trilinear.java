package io.toterra.subterra.engine.worldgen.pipeline.chunkgrid;

/**
 * Trilinear interpolation over a 2×2×2 corner block (p.1.8.13, self-developed,
 * mirroring MC 1.21.1's {@code NoiseChunk$NoiseInterpolator}). The axis order
 * and weights reproduce vanilla exactly:
 * <ol>
 * <li>interpolate Y within each of the four (X, Z) columns
 *     ({@code Mth.lerp(dY, y0, y1)}),</li>
 * <li>interpolate X (two {@code Mth.lerp(dX, x0, x1)}),</li>
 * <li>interpolate Z ({@code Mth.lerp(dZ, z0, z1)}).</li>
 * </ol>
 * The per-axis term is plain {@code lerp(a, b, t) = a + (b - a) * t} — verified
 * against {@code Mth.lerp}: {@code start + delta * (end - start)} — with the
 * deltas {@code dX = inCellX / cellWidth}, {@code dY = inCellY / cellHeight}
 * and {@code dZ = inCellZ / cellWidth}. MC 1.21.1 uses plain lerp here (no
 * smoothstep fading) on the density path; smoothstep belongs to the surface
 * step only. Pure function, allocation-free, deterministic: float order is
 * fixed by the sequential {@code lerp} composition.
 * <p>
 * 2×2×2 角块三线性插值（p.1.8.13，自研，镜像 MC 1.21.1 的
 * {@code NoiseChunk$NoiseInterpolator}）。轴顺序与权重与原版逐点一致：
 * <ol>
 * <li>在每个 (X, Z) 柱内沿 Y 插值（{@code Mth.lerp(dY, y0, y1)}），</li>
 * <li>再沿 X 插值（两次 {@code Mth.lerp(dX, x0, x1)}），</li>
 * <li>最后沿 Z 插值（{@code Mth.lerp(dZ, z0, z1)}）。</li>
 * </ol>
 * 每轴项为普通 {@code lerp(a, b, t) = a + (b - a) * t} —— 对照 {@code Mth.lerp}
 * （{@code start + delta * (end - start)}）验证 —— 其中增量
 * {@code dX = inCellX / cellWidth}、{@code dY = inCellY / cellHeight}、
 * {@code dZ = inCellZ / cellWidth}。MC 1.21.1 在密度路径上采用普通 lerp（无 smoothstep
 * 衰减）；smoothstep 仅用于地表步骤。纯函数、无分配、确定：浮点顺序由顺序 {@code lerp}
 * 复合固定。
 */
public final class Trilinear {

    private Trilinear() {
    }

    /**
     * Linear interpolation on one axis: {@code a + (b - a) * t}, byte-for-byte
     * the {@code Mth.lerp(delta, start, end)} term of MC 1.21.1.
     */
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Clamps {@code v} into {@code [0.0, 1.0]}. */
    public static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    /**
     * Interpolates the 2×2×2 corner block at normalized offsets
     * {@code (dX, dY, dZ)} with the exact vanilla order (Y → X → Z, plain lerp).
     * The low-Y plane is {@code (v000, v100, v010, v110)} and the high-Y plane
     * {@code (v001, v101, v011, v111)}; each plane lists {@code (x0,z0),
     * (x1,z0), (x0,z1), (x1,z1)}.
     *
     * <p>按准确原版顺序（Y → X → Z，普通 lerp）在归一化偏移 {@code (dX, dY, dZ)}
     * 处插值 2×2×2 角块。低 Y 平面为 {@code (v000, v100, v010, v110)}，高 Y 平面为
     * {@code (v001, v101, v011, v111)}；各平面依序列出 {@code (x0,z0)、(x1,z0)、
     * (x0,z1)、(x1,z1)}。
     */
    public static double interpolate(
            double v000, double v100, double v010, double v110,
            double v001, double v101, double v011, double v111,
            double dX, double dY, double dZ) {
        // Step 1: Y for each (X, Z) column.
        double xz00 = lerp(v000, v001, dY); // (x0, z0)
        double xz10 = lerp(v100, v101, dY); // (x1, z0)
        double xz01 = lerp(v010, v011, dY); // (x0, z1)
        double xz11 = lerp(v110, v111, dY); // (x1, z1)
        // Step 2: X.
        double z0 = lerp(xz00, xz10, dX);
        double z1 = lerp(xz01, xz11, dX);
        // Step 3: Z.
        return lerp(z0, z1, dZ);
    }
}