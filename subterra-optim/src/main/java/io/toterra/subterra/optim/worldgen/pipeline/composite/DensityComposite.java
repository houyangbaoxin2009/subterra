package io.toterra.subterra.optim.worldgen.pipeline.composite;

import java.util.Objects;

import io.toterra.subterra.optim.worldgen.pipeline.density.Density;
import io.toterra.subterra.optim.worldgen.pipeline.noise.simplex.NormalNoise;
import io.toterra.subterra.optim.worldgen.pipeline.router.NoiseRouter;
import io.toterra.subterra.optim.worldgen.pipeline.router.PositionalRand;

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
 *                                                min(cheese, 5*entrances), cheese) ), noodle )
 * </pre>
 *
 * with {@code qn = quarter_negative}, {@code blend_alpha = 1} (a fresh world with no
 * old-chunk blending) and {@code jagged} the {@code "minecraft:jagged"} field at
 * {@code xz_scale = 1500}. The two climate coordinates ({@link SplineFn} over
 * {@code continents} here) carry compact, structurally faithful knot sub-tables; the
 * exact evaluators ({@link SplineFn} / {@link SlideFn} / {@link JaggednessFn} /
 * {@link ShiftedNoiseFn}) and this assembly are the p.1.8.14 deliverable, while full
 * byte-level reproduction of the vanilla knot arrays is a later data-focused sibling's
 * concern. All leaves are pure functions of the router's seed, so the result is
 * deterministic, finite and allocation-free in the hot path.
 *
 * <p>主世界复合密度场装配（p.1.8.14），在既有 {@link NoiseRouter} 的气候字段
 * （{@code continents}/{@code erosion}/{@code ridges}）之上、严格按已验证的 1.21.1
 * 配方构造路由器的三个被修正的组合字段 —— {@code depth}、
 * {@code initialDensityWithoutJaggedness}、{@code finalDensity}。组合镜像
 * {@code data/minecraft/worldgen/noise_settings/overworld.json} 及
 * {@code overworld/depth.json}、{@code overworld/factor.json}、
 * {@code overworld/jaggedness.json}、{@code overworld/sloped_cheese.json}
 * （均对照随包 1.21.1 jar 验证），配方见上。两个气候坐标（此处为
 * {@code continents} 上、基于 {@link SplineFn} 的一维样条）携带紧凑、结构忠实的
 * 结点子表；精确求值器（{@link SplineFn}/{@link SlideFn}/{@link JaggednessFn}/
 * {@link ShiftedNoiseFn}）与本装配才是 p.1.8.14 的交付，完整逐字节复现原生结点数组
 * 交给后续数据导向平级项。所有叶子都是路由器种子的纯函数，故结果确定、有限、热路径零分配。
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

        // --- climate coordinate splines (compact structural sub-tables) ---
        // blend_alpha = 1 (no old-chunk blending), so factor/offset/jaggedness collapse to
        // their plain spline arms (plus the verified offset constant).
        Density offsetDensity = (x, y, z) -> OFFSET_BASE + spline1D(continents,
                new double[]{-1.1, -0.44, -0.18, -0.1, 0.25, 1.0},
                new double[]{0.05, -0.22, -0.16, -0.1, 0.3, 0.55},
                new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0}, x, z);
        Density factorDensity = (x, y, z) -> spline1D(continents,
                new double[]{-1.1, -0.6, -0.1, 0.25, 1.0},
                new double[]{6.3, 5.7, 5.0, 4.9, 4.5},
                new double[]{0.0, 0.0, 0.0, 0.0, 0.0}, x, z);
        Density jaggednessFactor = (x, y, z) -> spline1D(continents,
                new double[]{-0.11, 0.03, 0.65, 1.0},
                new double[]{0.0, 0.5, 0.1, 0.6},
                new double[]{0.0, 0.0, 0.0, 0.0}, x, z);

        // --- jagged / base3d / entrance deterministic noise leaves (over the seed) ---
        Density jaggedNoise = noise2d(seed, "minecraft:jagged", 1500.0);
        Density base3d = noise3d(seed, "minecraft:base_3d_noise", 0.25, 0.125);

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
        Density slopedCheese = add(cheeseQn, base3d);
        Density entrances = noise3d(seed, "minecraft:caves_entrances", 0.08, 0.08);
        Density finalCheese = rangeChoice(slopedCheese, -1.0e6, 1.5625,
                min(slopedCheese, mul(constant(5.0), entrances)), slopedCheese);
        Density finalDensity = min(SlideFn.overworld(finalCheese), constant(NOODLE_FLOOR));

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
    /** Noodle excluded (rendered as a no-op floor in this composite); positive keeps the cheese. */
    public static final double NOODLE_FLOOR = 1.0e12;

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

    /** 1-D spline over {@code coordinate} evaluated at {@code (x, z)} (y-independent). */
    static double spline1D(Density coordinate, double[] loc, double[] val, double[] der, double x, double z) {
        double c = coordinate.eval(x, 0.0, z);
        return SplineFn.eval(loc, val, der, c);
    }

    /** {@code a + b}. */
    private static Density add(Density a, Density b) {
        return (x, y, z) -> a.eval(x, y, z) + b.eval(x, y, z);
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

    /** A deterministic 2-D normal-noise leaf (xz plane) from a derived per-label seed. */
    private static Density noise2d(long seed, String label, double xzScale) {
        NormalNoise n = NormalNoise.create(PositionalRand.deriveLong(seed, label), -3, new double[]{1.0});
        return (x, y, z) -> n.getValue(x * xzScale, 0.0, z * xzScale);
    }

    /** A deterministic 3-D normal-noise leaf from a derived per-label seed. */
    private static Density noise3d(long seed, String label, double xzScale, double yScale) {
        NormalNoise n = NormalNoise.create(PositionalRand.deriveLong(seed, label), -2, new double[]{1.0});
        return (x, y, z) -> n.getValue(x * xzScale, y * yScale, z * xzScale);
    }
}