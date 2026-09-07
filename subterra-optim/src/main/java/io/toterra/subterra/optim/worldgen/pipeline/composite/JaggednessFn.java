package io.toterra.subterra.optim.worldgen.pipeline.composite;

import java.util.Objects;

import io.toterra.subterra.optim.worldgen.pipeline.density.Density;

/**
 * The vanilla jaggedness term (p.1.8.14) and the terrain mapping helpers, mirroring
 * MC 1.21.1's {@code overworld/sloped_cheese.json} and {@code DensityFunctions$Mapped}
 * (verified against the shipped 1.21.1 JSON / bytecode). In 1.21.1 the terrain "cheese"
 * is
 *
 * <pre>
 *   sloped_cheese = 4 * quarter_negative((depth + jaggedness * half_negative(jagged)) * factor)
 *                   + base_3d_noise
 * </pre>
 *
 * with {@code jagged = noise("minecraft:jagged", xz_scale=1500, y_scale=0)}. This class
 * supplies {@link #apply(Density, Density)} implementing the term
 * {@code jaggedness * half_negative(jaggedNoise)}, plus the exact {@code Mapped}
 * transforms used by the assembly:
 * <ul>
 *   <li>{@link #quarterNegative}/{@link #halfNegative} — scale only the <em>negative</em>
 *       arm ({@code x > 0 ? x : x*0.25} / {@code x > 0 ? x : x*0.5});</li>
 *   <li>{@link #squeeze} — {@code clamp(x,-1,1)/2 - clamp(x,-1,1)^3/24}.</li>
 * </ul>
 *
 * <p>原生锯齿项（p.1.8.14）与地形映射辅助，镜像 MC 1.21.1 的
 * {@code overworld/sloped_cheese.json} 与 {@code DensityFunctions$Mapped}
 * （对照 1.21.1 JSON / 字节码验证）。1.21.1 的 "奶酪" 地形为
 * {@code sloped_cheese = 4*quarter_negative((depth + jaggedness*half_negative(jagged))*factor)
 * + base_3d_noise}，其中 {@code jagged = noise("minecraft:jagged", xz_scale=1500, y_scale=0)}。
 * 本类提供 {@link #apply(Density,Density)} 实现 {@code jaggedness * half_negative(jaggedNoise)}
 * 项，以及装配所用的精确 {@code Mapped} 变换：{@link #quarterNegative}/{@link #halfNegative}
 * （仅缩放<em>负</em>支，{@code x>0?x:0.25x} / {@code x>0?x:0.5x}）、{@link #squeeze}
 * （{@code clamp(x,-1,1)/2 - clamp(x,-1,1)^3/24}）。
 */
public final class JaggednessFn {

    private JaggednessFn() {
    }

    /** {@code quarter_negative(x)} = {@code x > 0 ? x : x*0.25}. */
    public static double quarterNegative(double x) {
        return x > 0.0 ? x : x * 0.25;
    }

    /** {@code half_negative(x)} = {@code x > 0 ? x : x*0.5}. */
    public static double halfNegative(double x) {
        return x > 0.0 ? x : x * 0.5;
    }

    /** {@code squeeze(x)} = {@code c/2 - c^3/24} with {@code c = clamp(x, -1, 1)}. */
    public static double squeeze(double x) {
        double c = clamp(x, -1.0, 1.0);
        return c * 0.5 - c * c * c / 24.0;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /**
     * The jaggedness term: {@code jaggednessFactor * half_negative(jaggedNoise)}.
     * In the overworld {@code jaggednessFactor} is the {@code overworld/jaggedness}
     * climate spline and {@code jaggedNoise} the {@code "minecraft:jagged"} field
     * sampled at {@code xz_scale = 1500}.
     */
    public static Density apply(Density jaggednessFactor, Density jaggedNoise) {
        Objects.requireNonNull(jaggednessFactor, "jaggednessFactor");
        Objects.requireNonNull(jaggedNoise, "jaggedNoise");
        return (x, y, z) -> jaggednessFactor.eval(x, y, z) * halfNegative(jaggedNoise.eval(x, y, z));
    }
}