package io.toterra.subterra.optim.worldgen.pipeline.composite;

import java.util.Objects;

import io.toterra.subterra.optim.worldgen.pipeline.density.Density;

/**
 * The vanilla shifted-noise field (p.1.8.14), mirroring MC 1.21.1's
 * {@code DensityFunctions$ShiftedNoise.compute} (verified via javap): each coordinate
 * is domain-shifted by a per-axis shift function before being scaled and fed to an
 * inner noise field,
 *
 * <pre>
 *   x' = x*xzScale + shiftX.eval(x,y,z)
 *   y' = y*yScale  + shiftY.eval(x,y,z)
 *   z' = z*xzScale + shiftZ.eval(x,y,z)
 *   return noise.get(x', y', z')
 * </pre>
 *
 * {@link #shiftedNoise2d(Density, Density, Density, double)} is the vanilla
 * {@code shifted_noise} form ({@code shift_y = 0}, {@code y_scale = 0}) used for the
 * router's {@code temperature}/{@code vegetation} fields. When the shift is all zero
 * the field reduces to exactly the un-shifted noise (identity), which the probe checks.
 *
 * <p>原生位移噪声场（p.1.8.14），镜像 MC 1.21.1 的
 * {@code DensityFunctions$ShiftedNoise.compute}（经 javap 验证）：每轴坐标先被对应移动
 * 函数做域平移，再缩放后喂给内层噪声场，公式见上。
 * {@link #shiftedNoise2d(Density,Density,Density,double)} 即原生 {@code shifted_noise}
 * 形式（{@code shift_y = 0}、{@code y_scale = 0}），用于路由器的
 * {@code temperature}/{@code vegetation} 字段。当位移全为零时该场精确退化为未平移
 * 的噪声（恒等），探针会校验该性质。
 */
public final class ShiftedNoiseFn {

    private ShiftedNoiseFn() {
    }

    /** Pure kernel; {@code shiftX/Y/Z} may be {@code null} meaning zero shift. */
    public static double compute(Density noise, Density shiftX, Density shiftY, Density shiftZ,
                                 double xzScale, double yScale, double x, double y, double z) {
        double nx = x * xzScale + (shiftX == null ? 0 : shiftX.eval(x, y, z));
        double ny = y * yScale + (shiftY == null ? 0 : shiftY.eval(x, y, z));
        double nz = z * xzScale + (shiftZ == null ? 0 : shiftZ.eval(x, y, z));
        return noise.eval(nx, ny, nz);
    }

    /** Full shifted-noise {@link Density} (null shift fields mean zero offset). */
    public static Density of(Density noise, Density shiftX, Density shiftY, Density shiftZ,
                             double xzScale, double yScale) {
        Objects.requireNonNull(noise, "noise");
        return (x, y, z) -> compute(noise, shiftX, shiftY, shiftZ, xzScale, yScale, x, y, z);
    }

    /**
     * Vanilla {@code shifted_noise}: the same xz shift applied to x and z, no y shift
     * and {@code y_scale = 0} (matches {@code shiftedNoise2d(zero, shiftX, shiftZ,
     * xzScale, holder)}).
     */
    public static Density shiftedNoise2d(Density noise, Density shiftX, Density shiftZ, double xzScale) {
        Objects.requireNonNull(noise, "noise");
        return of(noise, shiftX, null, shiftZ, xzScale, 0.0);
    }
}