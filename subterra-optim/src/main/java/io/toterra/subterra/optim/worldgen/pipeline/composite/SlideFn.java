package io.toterra.subterra.optim.worldgen.pipeline.composite;

import java.util.Objects;

import io.toterra.subterra.optim.worldgen.pipeline.density.Density;

/**
 * The vanilla overworld terrain "slide" composition (p.1.8.14), mirroring MC 1.21.1's
 * {@code NoiseRouterData.slide(DensityFunction,int,int,int,int,double,int,int,double)}
 * and the {@code data/minecraft/worldgen/noise_settings/overworld.json} routers'
 * {@code initial_density_without_jaggedness} / {@code final_density} expressions
 * (verified against the shipped 1.21.1 JSON). A single bottom band lerps the density
 * toward {@code bottomValue} below the floor, and a single top band lerps it toward
 * {@code topValue} above the ceiling:
 *
 * <pre>
 *   gTop    = yClamp(y, topFrom, topTo, 1.0, 0.0)   // 1 deep, 0 at the ceiling
 *   f       = lerpRaw(raw, topValue, gTop)           // = topValue + (raw-topValue)*gTop
 *   gBottom = yClamp(y, bottomFrom, bottomTo, 0.0, 1.0) // 0 at the floor, 1 above
 *   out     = lerpRaw(f, bottomValue, gBottom)       // = bottomValue + (f-bottomValue)*gBottom
 * </pre>
 *
 * For the overworld ({@link #overworldSlide(double, double)}) the verified constants are
 * {@code bottomFrom=-64, bottomTo=-40, bottomValue=0.1171875} and {@code topFrom=240,
 * topTo=256, topValue=-0.078125} (equivalently the JSON
 * {@code 0.1171875 + gB*(-0.1171875 + (-0.078125 + gT*(0.078125 + raw)))}).
 *
 * <p>原生主世界地形"滑移"组合（p.1.8.14），镜像 MC 1.21.1 的
 * {@code NoiseRouterData.slide(...)} 及随包发布的
 * {@code overworld.json} 路由器的 {@code initial_density_without_jaggedness} /
 * {@code final_density} 表达式（对照 1.21.1 JSON 验证）。单一底部带把密度在地板之下
 * 向 {@code bottomValue} 插值，单一顶部带在天花板之上向 {@code topValue} 插值，公式
 * 见上。主世界（{@link #overworldSlide}）的已验常量为 {@code bottomFrom=-64, bottomTo=-40,
 * bottomValue=0.1171875} 与 {@code topFrom=240, topTo=256, topValue=-0.078125}
 * （等价于 JSON {@code 0.1171875 + gB*(-0.1171875 + (-0.078125 + gT*(0.078125 + raw)))}）。
 */
public final class SlideFn {

    private SlideFn() {
    }

    /** {@code yClampedGradient(fromY, toY, fromValue, toValue)}: piecewise-linear, clamped beyond. */
    public static double grad(double y, double fromY, double toY, double fromValue, double toValue) {
        if (y <= fromY) {
            return fromValue;
        }
        if (y >= toY) {
            return toValue;
        }
        return fromValue + (toValue - fromValue) * ((y - fromY) / (toY - fromY));
    }

    /** Linear blend {@code a + (b - a) * t}. */
    private static double mix(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /**
     * Applies the two-band slide to a raw density value at height {@code y}.
     *
     * @param raw         the raw density (e.g. the clamped {@code quarter_negative} term).
     * @param bottomFrom  floor band start (inclusive).
     * @param bottomTo    floor band end; below {@code bottomFrom} the output is {@code bottomValue}.
     * @param bottomValue the floor target value.
     * @param topFrom     ceiling band start.
     * @param topTo       ceiling band end; above {@code topTo} the output is {@code topValue}.
     * @param topValue    the ceiling target value.
     */
    public static double slide(double raw, double y,
                               double bottomFrom, double bottomTo, double bottomValue,
                               double topFrom, double topTo, double topValue) {
        double gTop = grad(y, topFrom, topTo, 1.0, 0.0);
        double f = mix(topValue, raw, gTop);
        double gBottom = grad(y, bottomFrom, bottomTo, 0.0, 1.0);
        return mix(bottomValue, f, gBottom);
    }

    /** The exact overworld slide with the verified 1.21.1 constants. */
    public static double overworldSlide(double raw, double y) {
        return slide(raw, y, -64.0, -40.0, 0.1171875, 240.0, 256.0, -0.078125);
    }

    /** Returns a {@link Density} that slides {@code raw} at the evaluated {@code y}. */
    public static Density of(Density raw, double bottomFrom, double bottomTo, double bottomValue,
                             double topFrom, double topTo, double topValue) {
        Objects.requireNonNull(raw, "raw");
        return (x, y, z) -> slide(raw.eval(x, y, z), y,
                bottomFrom, bottomTo, bottomValue, topFrom, topTo, topValue);
    }

    /** {@link Density} form of {@link #overworldSlide(double, double)}. */
    public static Density overworld(Density raw) {
        Objects.requireNonNull(raw, "raw");
        return (x, y, z) -> overworldSlide(raw.eval(x, y, z), y);
    }
}