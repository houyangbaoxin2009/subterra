package io.toterra.subterra.engine.worldgen.pipeline.composite;

import java.util.Objects;

import io.toterra.subterra.engine.worldgen.pipeline.density.Density;
import io.toterra.subterra.engine.worldgen.pipeline.noise.simplex.NormalNoise;
import io.toterra.subterra.engine.worldgen.pipeline.router.BlendedNoise;
import io.toterra.subterra.engine.worldgen.pipeline.router.NoiseRouter;
import io.toterra.subterra.engine.worldgen.pipeline.router.PositionalRand;

/**
 * The overworld composite density-fields assembly (p.1.8.14), constructing the router's
 * three corrected composite fields — {@code depth}, {@code initialDensityWithoutJaggedness}
 * and {@code finalDensity} — over an existing {@link NoiseRouter}'s climate fields
 * ({@code continents}/{@code erosion}/{@code ridges}) exactly per the verified 1.21.1
 * recipe. The composition mirrors {@code data/minecraft/worldgen/noise_settings/
 * overworld.json} plus {@code overworld/depth.json}, {@code overworld/factor.json},
 * {@code overworld/jaggedness.json} and {@code overworld/sloped_cheese.json}
 * (all verified against the shipped 1.21.1 jar):
 *
 * <pre>
 *   depth  = yClamp(y, minY..maxY, 1.5, -1.5) + offset(continents, erosion, ...)
 *   depthNoJag = depth                                     // initial uses NO jaggedness
 *   initial = overworldSlide( clamp(4*qn(depthNoJag*factor) - 0.703125, -64, 64), y )
 *   cheese  = 4*qn((depth + jaggedness*halfNeg(jagged)) * factor) + base3d
 *   final   = min( overworldSlide( rangeChoice(cheese, -1e6, 1.5625,
 *                                                min(cheese, 5*entrances),
 *                                                caveFamily(cheese)) ),
 *                   noodle )
 * </pre>
 *
 * with {@code qn = quarter_negative}, {@code blend_alpha = 1} (a fresh world with no
 * old-chunk blending) and {@code jagged} the {@code "minecraft:jagged"} field at
 * {@code xz_scale = 1500}. The {@code offset} and {@code factor} climate terms are the
 * faithful 1.21.1 nested {@code continents → erosion → ridge} splines
 * ({@link ClimateSpline}, {@link ClimateSpline#OFFSET} / {@link ClimateSpline#FACTOR}),
 * sampled with the router's real {@code ridges()} field at the surface point
 * (p.1.8.27A upgrades the p.1.8.26 2-D reduction that fixed the ridge axis at folded = 0),
 * and the jaggedness multiplier is the same nested spline ({@link ClimateSpline#JAGGEDNESS})
 * instead of the old compact 1-D arm. Since p.1.8.27B the {@code when_out_of_range} of the
 * cheese range-choice is the vanilla cave family ({@link CaveFamilyFn}: cheese/spaghetti/
 * pillars + entrances) and the outer {@code min} is the noodle-cave arm
 * ({@link NoodleFn}), closing the "noodle/normal-caves disabled" artifact gap; since
 * p.1.8.28 the {@code when_in_range} companion is the faithful
 * {@code min(sloped_cheese, 5 * entrances)} over the real entrances.json density function
 * (the superseded p.1.8.26 {@code Spline2D} 2-D spline reduction was disposed of in the
 * same batch), and every Perlin leaf — {@code jagged} included — resolves its octave
 * anchor and amplitudes from {@link NoiseRouter#NOISE_REGISTRATIONS}. All
 * leaves are pure functions of the router's seed, so the result is deterministic,
 * finite and allocation-free in the hot path.

 * <p>主世界复合密度场装配（p.1.8.14），在既有 {@link NoiseRouter} 的气候字段
 * （{@code continents}/{@code erosion}/{@code ridges}）之上、严格按已验证的 1.21.1
 * 配方构造路由器的三个被修正的组合字段 —— {@code depth}、
 * {@code initialDensityWithoutJaggedness}、{@code finalDensity}。配方见上。
 * {@code offset}、{@code factor} 气候项采用真实的 1.21.1 嵌套
 * {@code continents → erosion → ridge} 样条（{@link ClimateSpline}，
 * {@link ClimateSpline#OFFSET} / {@link ClimateSpline#FACTOR}），并在表面点采样路由器的
 * 真实 {@code ridges()} 场（p.1.8.27A 将 p.1.8.26 的二维归约升级为真实 ridge 扭曲；
 * 旧归约把 ridge 轴固定在 folded=0，且该二维归约已在 p.1.8.28 一并处置）；锯齿倍数为
 * 同一嵌套样条（{@link ClimateSpline#JAGGEDNESS}），取代旧的一维紧凑支。自 p.1.8.27B
 * 起，奶酪 range-choice 的 {@code when_out_of_range} 采用原生洞穴族（{@link CaveFamilyFn}：
 * cheese/spaghetti/pillars + entrances）、外层 {@code min} 采用面条洞穴支
 * （{@link NoodleFn}），从而闭合"面条/常规洞穴被禁用"的伪影缺口；自 p.1.8.28 起
 * {@code when_in_range} 伴臂为忠实的 {@code min(sloped_cheese, 5*entrances)}（真实
 * entrances.json 密度函数），且每个 Perlin 叶（含 {@code jagged}）的 octave 锚与振幅
 * 均查表自 {@link NoiseRouter#NOISE_REGISTRATIONS}。所有叶子都是
 * 路由器种子的纯函数，故结果确定、有限、热路径零分配。
 */
public final class DensityComposite {

    private DensityComposite() {
    }

    /** Result bundle: the three corrected fields plus a minimal self-description. */
    public record Overworld(Density depth,
                            Density initialDensityWithoutJaggedness,
                            Density finalDensity,
                            String td) {
        public Overworld {
            Objects.requireNonNull(depth, "depth");
            Objects.requireNonNull(initialDensityWithoutJaggedness, "initialDensityWithoutJaggedness");
            Objects.requireNonNull(finalDensity, "finalDensity");
            Objects.requireNonNull(td, "td");
        }
    }

    /** Overworld with the vanilla block range {@code minY = -64, maxY = 320}. */
    public static Overworld overworld(NoiseRouter router) {
        return overworld(router, -64, 320);
    }

    /** Assembles all three composite fields over {@code router} for {@code [minY, maxY)}. */
    public static Overworld overworld(NoiseRouter router, int minY, int maxY) {
        if (minY >= maxY) {
            throw new IllegalArgumentException("bad Y range: minY=" + minY + " maxY=" + maxY);
        }
        Objects.requireNonNull(router, "router");
        long seed = router.worldSeed();

        Density continents = router.continents();
        Density erosion = router.erosion();
        Density ridges = router.ridges();

        // --- climate coordinate splines (p.1.8.27A): faithful 3-axis continents×erosion×ridge ---
        // blend_alpha = 1 (no old-chunk blending), so offset collapses to
        // spline(continents,erosion,ridge) + OFFSET_BASE and factor to
        // spline(continents,erosion,ridge); the vanilla nested splines are evaluated
        // by {@link ClimateSpline} with the real {@code ridges()} field sampled at the
        // surface point (the p.1.8.26 2-D spline reduction, disposed in p.1.8.28,
        // fixed the ridge axis at folded = 0; this upgrades it to the actual ridge warp).
        // p.1.8.32 perf: each spline eval samples the three 2-D climate leaves ONCE per
        // (x,z) into locals (the leaves are y-independent; re-evaluating them inside the
        // spline call was pure duplication and dominated the per-corner tree cost).
        // p.1.8.32 perf: the three 2-D climate splines are re-sampled at the SAME (x,z)
        // corner by many arms of the tree (depth/initial/cheese/cave); per-thread single-slot
        // 2-D caches collapse those repeats (pure dedup, bit-identical values).
        Density offsetDensity = CachedDensity.cached2d((x, y, z) -> OFFSET_BASE
                + ClimateSpline.OFFSET.eval(co(continents, x, z), co(erosion, x, z), co(ridges, x, z)));
        Density factorDensity = CachedDensity.cached2d((x, y, z) -> ClimateSpline.FACTOR.eval(
                co(continents, x, z), co(erosion, x, z), co(ridges, x, z)));
        Density jaggednessFactor = CachedDensity.cached2d((x, y, z) -> ClimateSpline.JAGGEDNESS.eval(
                co(continents, x, z), co(erosion, x, z), co(ridges, x, z)));

        // --- jagged / base3d noise leaves (over the seed) ---
        // jagged uses its faithful 1.21.1 registration (-16, [1×16], p.1.8.28); base_3d_noise
        // is the real 1.21.1 old_blended_noise density function (p.1.8.29B): the octave-blend
        // BlendedNoise built over the per-world terrain stream with the overworld parameter
        // set (xz 0.25 / y 0.125 / xz_factor 80 / y_factor 160 / smear 8).
        Density jaggedNoise = noise2d(seed, "minecraft:jagged", 1500.0);
        Density base3d = BlendedNoise.overworld(seed);

        // --- depth field: gradient + offset (faithful) ---
        Density depthGrad = (x, y, z) -> SlideFn.grad(y, minY, maxY, 1.5, -1.5);
        Density depth = add(depthGrad, offsetDensity);

        // --- initial_density_without_jaggedness (NO jaggedness) ---
        Density depthFactorNoJag = mul(depth, factorDensity);
        Density noJagRaw = (x, y, z) -> clamp(4.0 * JaggednessFn.quarterNegative(
                depthFactorNoJag.eval(x, y, z)) - 0.703125, -64.0, 64.0);
        Density initialDensity = SlideFn.overworld(noJagRaw);

        // --- sloped cheese (WITH jaggedness) and final_density ---
        Density jaggedTerm = JaggednessFn.apply(jaggednessFactor, jaggedNoise);
        Density cheeseDepth = add(depth, jaggedTerm);
        Density cheeseQn = (x, y, z) -> 4.0 * JaggednessFn.quarterNegative(
                cheeseDepth.eval(x, y, z) * factorDensity.eval(x, y, z));
        // p.1.8.32 perf: sloped_cheese is re-evaluated several times per corner (the
        // range-choice input, the when_in branch, and the cheese-cave arm). A per-thread
        // single-slot cache collapses those repeats to one tree evaluation; the value is
        // unchanged (pure deduplication of equal-input calls).
        Density slopedCheese = CachedDensity.cached3d(add(cheeseQn, base3d));
        // p.1.8.28: the when_in_range companion is the faithful entrances.json density
        // function (was a bare "minecraft:caves_entrances" noise stand-in); vanilla's
        // overworld.json pins this arm to min(sloped_cheese, 5 * entrances).
        Density entrances = CaveFamilyFn.entrances(seed);
        // p.1.8.27B: the when_out_of_range of the cheese range-choice is the vanilla cave
        // family (cheese/spaghetti/pillars + entrances) instead of the raw sloped cheese;
        // and the outer final_density term is the noodle-cave arm (was a no-op NOODLE_FLOOR).
        Density caveFamily = CaveFamilyFn.overworld(seed, slopedCheese);
        Density finalCheese = rangeChoice(slopedCheese, -1.0e6, 1.5625,
                min(slopedCheese, mul(constant(5.0), entrances)), caveFamily);
        Density noodle = NoodleFn.overworld(seed);
        Density finalDensity = min(SlideFn.overworld(finalCheese), noodle);

        String td = "[ seed = " + seed + ", minY = " + minY + ", maxY = " + maxY + " ]";
        return new Overworld(depth, initialDensity, finalDensity, td);
    }

    /** Verified vanilla offset constant ({@code overworld/offset.json}). */
    public static final double OFFSET_BASE = -0.5037500262260437;

    /** Verified vanilla initial-density shift before the slide ({@code overworld.json}). */
    public static final double DEPTH_SHIFT = -0.703125;
    /** Verified vanilla slide constants ({@code overworld.json}). */
    public static final double SLIDE_BOTTOM_VALUE = 0.1171875;
    public static final double SLIDE_TOP_VALUE = -0.078125;
    /** Verified {@code sloped_cheese} factor and the cheese range-choice cut-off. */
    public static final double CHEESE_SCALE = 4.0;
    public static final double CHEESE_RANGE_MIN = -1.0e6;
    public static final double CHEESE_RANGE_MAX = 1.5625;

    /** Rebuilds an {@link Overworld} from a {@code td()} snippet (default block range). */
    public static Overworld fromTd(String source) {
        if (source == null) {
            throw new IllegalArgumentException("td must not be null");
        }
        long seed = 0;
        int minY = -64;
        int maxY = 320;
        String body = source.substring(source.indexOf('[') + 1, source.indexOf(']'));
        for (String part : body.split(",")) {
            String[] kv = part.trim().split("=");
            if (kv.length != 2) {
                throw new IllegalArgumentException("bad token in td: " + part);
            }
            try {
                switch (kv[0].trim()) {
                    case "seed" -> seed = Long.parseLong(kv[1].trim());
                    case "minY" -> minY = Integer.parseInt(kv[1].trim());
                    case "maxY" -> maxY = Integer.parseInt(kv[1].trim());
                    default -> throw new IllegalArgumentException("unknown key in td: " + kv[0].trim());
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("bad value in td: " + part, e);
            }
        }
        return overworld(NoiseRouter.overworld(seed, minY, maxY), minY, maxY);
    }

    // ============================ composition helpers ============================

    /** {@code a + b}. */
    private static Density add(Density a, Density b) {
        return (x, y, z) -> a.eval(x, y, z) + b.eval(x, y, z);
    }

    /** The 2-D climate leaf at (x, z) (y is dropped, mirroring vanilla 2-D sampling). */
    private static double co(Density leaf, double x, double z) {
        return leaf.eval(x, 0.0, z);
    }

    /** {@code a * b}. */
    private static Density mul(Density a, Density b) {
        return (x, y, z) -> a.eval(x, y, z) * b.eval(x, y, z);
    }

    /** {@code Math.min}. */
    private static Density min(Density a, Density b) {
        return (x, y, z) -> Math.min(a.eval(x, y, z), b.eval(x, y, z));
    }

    private static Density constant(double v) {
        return (x, y, z) -> v;
    }

    /** Vanilla {@code RangeChoice}: {@code in} when {@code min <= input < max}, else {@code out}. */
    static Density rangeChoice(Density input, double minInclusive, double maxExclusive,
                               Density whenInRange, Density whenOutOfRange) {
        return (x, y, z) -> {
            double v = input.eval(x, y, z);
            return (v >= minInclusive && v < maxExclusive)
                    ? whenInRange.eval(x, y, z)
                    : whenOutOfRange.eval(x, y, z);
        };
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** A deterministic 2-D normal-noise leaf (xz plane) from a derived per-label seed,
     * with the label's {@link NoiseRouter.NoiseReg} registration. */
    private static Density noise2d(long seed, String label, double xzScale) {
        NoiseRouter.NoiseReg reg = NoiseRouter.registration(label);
        NormalNoise n = NormalNoise.create(PositionalRand.deriveLong(seed, label),
                reg.firstOctave(), reg.amplitudes());
        return (x, y, z) -> n.getValue(x * xzScale, 0.0, z * xzScale);
    }
}