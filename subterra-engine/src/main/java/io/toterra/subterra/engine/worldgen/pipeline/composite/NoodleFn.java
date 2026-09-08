package io.toterra.subterra.engine.worldgen.pipeline.composite;

import io.toterra.subterra.engine.worldgen.pipeline.density.Density;
import io.toterra.subterra.engine.worldgen.pipeline.noise.simplex.NormalNoise;
import io.toterra.subterra.engine.worldgen.pipeline.router.PositionalRand;

/**
 * The vanilla overworld noodle-cave density arm (p.1.8.27B), transcribed from MC 1.21.1's
 * {@code data/minecraft/worldgen/density_function/overworld/caves/noodle.json} (extracted
 * from the shipped 1.21.1 client jar) and wired as the outer {@code min(...)} term of the
 * {@code overworld.json} {@code final_density}. The 1.21.1 noodle arm is a two-stage
 * {@code range_choice}: outside {@code y ∈ [-60, 321)} — or where the
 * {@code "minecraft:noodle"} mask noise is in {@code [-1e6, 0)} — the density is the solid
 * floor {@code +64.0}; otherwise it is the classic ridge-based noodle carve
 *
 * <pre>
 *   noodle = (-0.07500000000000001 - 0.025 * thickness)
 *            + 1.5 * max(|ridge_a|, |ridge_b|)
 *   thickness = noise("minecraft:noodle_thickness", xz_scale=1, y_scale=1)
 *   ridge_a/b = noise("minecraft:noodle_ridge_a/b", xz_scale=8/3, y_scale=8/3)
 * </pre>
 *
 * where {@code max(|a|,|b|)} is small only near the intersection curve of the two ridge
 * zero-surfaces — the noodle centerline — so negative (carved) values form thin tubes.
 * The {@code minecraft:interpolated} wrappers of the JSON are exact identities for
 * single-point evaluation (vanilla {@code Interpolated.compute} delegates to the wrapped
 * function; verified via javap), so they are dropped here — the same convention as the
 * rest of the composite core. All leaves are deterministic functions of the router seed
 * via {@link PositionalRand#deriveLong}, using the verified 1.21.1 noise registrations
 * from {@code data/minecraft/worldgen/noise/*.json}: {@code noodle} /
 * {@code noodle_thickness} = {@code firstOctave -8, amplitudes [1.0]};
 * {@code noodle_ridge_a} / {@code noodle_ridge_b} = {@code firstOctave -7, [1.0]}.
 * Immutable; evaluation is allocation-free and constant-time.
 *
 * <p>原生主世界"面条"洞穴密度支（p.1.8.27B），按 MC 1.21.1 的
 * {@code overworld/caves/noodle.json}（自随包发布的 1.21.1 客户端 jar 提取）转写，并作为
 * {@code overworld.json} {@code final_density} 的外层 {@code min(...)} 项接入。1.21.1 的
 * 面条支是两级 {@code range_choice}：在 {@code y ∈ [-60, 321)} 之外，或
 * {@code "minecraft:noodle"} 掩膜噪声落在 {@code [-1e6, 0)} 内时，密度为实心地板
 * {@code +64.0}；否则为经典脊线面条雕刻
 * {@code noodle = (-0.07500000000000001 - 0.025*thickness) + 1.5*max(|ridge_a|,|ridge_b|)}，
 * 其中 {@code max(|a|,|b|)} 只在两条脊线零面的交线（面条中轴线）附近取小值，故负值
 * （被雕刻）形成细管状。JSON 中的 {@code minecraft:interpolated} 包装在单点求值时是恒等
 * （原生 {@code Interpolated.compute} 直接委托被包装函数；经 javap 验证），故略去——与
 * 复合核心其余部分同一约定。所有叶子经 {@link PositionalRand#deriveLong} 由路由器种子
 * 确定派生，使用已验证的 1.21.1 噪声注册（取自 {@code worldgen/noise/*.json}）：
 * {@code noodle}/{@code noodle_thickness} = {@code firstOctave -8, amplitudes [1.0]}；
 * {@code noodle_ridge_a}/{@code noodle_ridge_b} = {@code firstOctave -7, [1.0]}。不可变；
 * 求值零分配、常数时间。
 */
public final class NoodleFn {

    private NoodleFn() {
    }

    /** Verified {@code noodle.json} y-window: noodle carving exists only for {@code y ∈ [-60, 321)}. */
    public static final double Y_MIN = -60.0;
    public static final double Y_MAX = 321.0;
    /** Verified solid floor returned where the noodle mask is solid ({@code when_in_range = 64.0}). */
    public static final double SOLID = 64.0;
    /** Verified mask window of the outer {@code range_choice} ({@code [-1e6, 0)}). */
    public static final double MASK_MIN = -1.0e6;
    public static final double MASK_MAX = 0.0;
    /** Verified ridge carve constants ({@code noodle.json} when_out_of_range). */
    public static final double RIDGE_SCALE = 1.5;
    public static final double THICKNESS_OFFSET = -0.07500000000000001;
    public static final double THICKNESS_SCALE = -0.025;
    /** Verified ridge coordinate scale ({@code 8/3}, applied to x, y and z). */
    public static final double RIDGE_COORD_SCALE = 2.6666666666666665;
    /** Verified noise registrations ({@code worldgen/noise/noodle*.json}). */
    public static final int NOODLE_FIRST_OCTAVE = -8;
    public static final int RIDGE_FIRST_OCTAVE = -7;
    private static final double[] SINGLE_AMPLITUDE = {1.0};

    /**
     * The overworld noodle-cave {@link Density} for a world seed: {@code +64.0} where the
     * mask is solid (or outside the y-window), else the ridge-based carve. Deterministic,
     * finite, allocation-free per evaluation.
     */
    public static Density overworld(long seed) {
        NormalNoise mask = NormalNoise.create(PositionalRand.deriveLong(seed, "minecraft:noodle"),
                NOODLE_FIRST_OCTAVE, SINGLE_AMPLITUDE);
        NormalNoise thickness = NormalNoise.create(PositionalRand.deriveLong(seed, "minecraft:noodle_thickness"),
                NOODLE_FIRST_OCTAVE, SINGLE_AMPLITUDE);
        NormalNoise ridgeA = NormalNoise.create(PositionalRand.deriveLong(seed, "minecraft:noodle_ridge_a"),
                RIDGE_FIRST_OCTAVE, SINGLE_AMPLITUDE);
        NormalNoise ridgeB = NormalNoise.create(PositionalRand.deriveLong(seed, "minecraft:noodle_ridge_b"),
                RIDGE_FIRST_OCTAVE, SINGLE_AMPLITUDE);
        return (x, y, z) -> {
            if (y < Y_MIN || y >= Y_MAX) {
                return SOLID;
            }
            double n = mask.getValue(x, y, z);
            if (n >= MASK_MIN && n < MASK_MAX) {
                return SOLID;
            }
            double t = thickness.getValue(x, y, z);
            double a = Math.abs(ridgeA.getValue(x * RIDGE_COORD_SCALE, y * RIDGE_COORD_SCALE, z * RIDGE_COORD_SCALE));
            double b = Math.abs(ridgeB.getValue(x * RIDGE_COORD_SCALE, y * RIDGE_COORD_SCALE, z * RIDGE_COORD_SCALE));
            return THICKNESS_OFFSET + THICKNESS_SCALE * t + RIDGE_SCALE * Math.max(a, b);
        };
    }
}
