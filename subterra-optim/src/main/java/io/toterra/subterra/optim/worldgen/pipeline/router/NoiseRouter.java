package io.toterra.subterra.optim.worldgen.pipeline.router;

import java.util.Objects;

import io.toterra.subterra.optim.worldgen.pipeline.composite.DensityComposite;
import io.toterra.subterra.optim.worldgen.pipeline.composite.SlideFn;
import io.toterra.subterra.optim.worldgen.pipeline.density.Density;
import io.toterra.subterra.optim.worldgen.pipeline.noise.simplex.NormalNoise;

/**
 * The vanilla-compatible noise-router assembly table (p.1.8.12), mirroring MC
 * 1.21.1's {@code net.minecraft.world.level.levelgen.NoiseRouter} record fields
 * and accessors (names verified via javap on the de-obfuscated 1.21.1 class). The
 * router has exactly fifteen fields, in MC order: {@code barrierNoise},
 * {@code fluidLevelFloodednessNoise}, {@code fluidLevelSpreadNoise},
 * {@code lavaNoise}, {@code temperature}, {@code vegetation}, {@code continents},
 * {@code erosion}, {@code depth}, {@code ridges},
 * {@code initialDensityWithoutJaggedness}, {@code finalDensity},
 * {@code veinToggle}, {@code veinRidged}, {@code veinGap}. Every field implements
 * the {@link Density} seam {@code (x,y,z) -> double} so the chunk-grid sibling and
 * later pipeline stages can sample each independently and deterministically.
 * <p>
 * {@link #overworld(long, int, int)} assembles the vanilla overworld recipe. The
 * per-noise seed chain uses {@link PositionalRand#deriveLong} (MD5 {@code
 * fromHashOf} semantics) with the exact vanilla {@code Noises}/amplitude lists
 * obtained from {@code net.minecraft.data.worldgen.NoiseData.bootstrap} (verified
 * via javap). The four climate families carry their real octave anchors and
 * amplitude lists; the aquifer / vein noises are registered with <em>empty</em>
 * amplitude lists in 1.21.1 (which pure-{@code NormalNoise} turns into a constant
 * zero), so here an empty list is interpreted as a deterministic single-octave
 * handle -- so the seam yields a usable, seed-sensitive field. The composite
 * fields ({@code depth}, {@code initialDensityWithoutJaggedness},
 * {@code finalDensity}) are built by the p.1.8.14 composite density-fields core
 * ({@link DensityComposite}) from the vanilla spline / slide / jaggedness /
 * shiftedNoise composition (see the {@code pipeline.composite} package); their values
 * are finite, deterministic and seed-relative, and now reproduce the vanilla composite
 * structure over the router's climate fields.
 * <p>
 * {@link #nether(long,int,int)} and {@link #end(long,int,int)} (p.1.8.20) assemble the
 * vanilla nether / end routers, mirroring {@code noise_settings/nether.json} and
 * {@code noise_settings/end.json}: most fields are the pinned constant {@code 0}, with
 * the nether's {@code temperature}/{@code vegetation} + cheese {@code finalDensity} and
 * the end's island {@code erosion}/{@code initialDensity}/{@code finalDensity} composed
 * from seed-sensitive reduced leaves over the same seam.
 * <p>
 * 与原生兼容的噪声路由器装配表（p.1.8.12），镜像 MC 1.21.1 {@code NoiseRouter}
 * record 字段与访问器（名称经 javap 对照反混淆后的 1.21.1 类验证）。路由器恰有
 * 15 个字段，按 MC 顺序：{@code barrierNoise}、{@code fluidLevelFloodednessNoise}、
 * {@code fluidLevelSpreadNoise}、{@code lavaNoise}、{@code temperature}、
 * {@code vegetation}、{@code continents}、{@code erosion}、{@code depth}、
 * {@code ridges}、{@code initialDensityWithoutJaggedness}、{@code finalDensity}、
 * {@code veinToggle}、{@code veinRidged}、{@code veinGap}。每个字段实现
 * {@link Density} 接缝 {@code (x,y,z)->double}，使块格平级模块与后续阶段可各自
 * 确定性采样。
 * {@link #overworld(long,int,int)} 组装主世界配方。每噪声种子链使用
 * {@link PositionalRand#deriveLong}（MD5 {@code fromHashOf} 语义），振幅列表取自
 * {@code net.minecraft.data.worldgen.NoiseData.bootstrap}（经 javap 验证）。四个
 * 气候族携带真实 octave 锚与振幅列表；aquifer / vein 噪声在 1.21.1 中以
 * <em>空</em>振幅注册（纯 {@code NormalNoise} 会将其退化为常量 0），故此处把空
 * 列表解释为确定性单 octave 占位，使接缝产出可用的种子敏感场。组合字段
 * （{@code depth}、{@code initialDensityWithoutJaggedness}、{@code finalDensity}）
 * 由 p.1.8.14 组合密度场核心（{@link DensityComposite}）按原生 spline / slide /
 * jaggedness / shiftedNoise 组合构建（见 {@code pipeline.composite} 包）；其值有限、
 * 确定且随种子变化，并在路由器气候字段之上复现原生组合结构。
 */
public final class NoiseRouter {

    // ---- canonical field names & order (verified against 1.21.1) ----
    /** The fifteen canonical MC field names, in router order. */
    public static final String[] FIELD_NAMES = {
            "barrierNoise",
            "fluidLevelFloodednessNoise",
            "fluidLevelSpreadNoise",
            "lavaNoise",
            "temperature",
            "vegetation",
            "continents",
            "erosion",
            "depth",
            "ridges",
            "initialDensityWithoutJaggedness",
            "finalDensity",
            "veinToggle",
            "veinRidged",
            "veinGap",
    };

    // ---- vanilla overworld noise constants (from NoiseData.bootstrap) ----
    /** Continentalness: octave anchor. */
    public static final int CONTINENTS_FIRST_OCTAVE = -9;
    /** Continentalness amplitude list. */
    public static final double[] CONTINENTS_AMPLITUDES = {1.0, 2.0, 2.0, 2.0, 1.0, 1.0, 1.0, 1.0};
    /** Erosion: octave anchor. */
    public static final int EROSION_FIRST_OCTAVE = -9;
    /** Erosion amplitude list. */
    public static final double[] EROSION_AMPLITUDES = {1.0, 0.0, 1.0, 1.0};
    /** Temperature: octave anchor. */
    public static final int TEMPERATURE_FIRST_OCTAVE = -10;
    /** Temperature amplitude list. */
    public static final double[] TEMPERATURE_AMPLITUDES = {0.0, 1.0, 0.0, 0.0, 0.0};
    /** Temperature registered amplitude (1.5). */
    public static final double TEMPERATURE_AMPLITUDE = 1.5;
    /** Vegetation: octave anchor. */
    public static final int VEGETATION_FIRST_OCTAVE = -8;
    /** Vegetation amplitude list. */
    public static final double[] VEGETATION_AMPLITUDES = {1.0, 0.0, 0.0, 0.0, 0.0};
    /** Ridge: octave anchor. */
    public static final int RIDGE_FIRST_OCTAVE = -7;
    /** Ridge amplitude list. */
    public static final double[] RIDGE_AMPLITUDES = {2.0, 1.0, 0.0, 0.0, 0.0};
    /** Shift: octave anchor ({@code "minecraft:offset"}). */
    public static final int SHIFT_FIRST_OCTAVE = -3;
    /** Shift amplitude list. */
    public static final double[] SHIFT_AMPLITUDES = {1.0, 1.0, 0.0};
    /** AQUIFER_BARRIER field coordinate xz-scale. */
    public static final double BARRIER_XZ_SCALE = 0.5;
    /** AQUIFER_FLUID_LEVEL_FLOODEDNESS field coordinate xz-scale. */
    public static final double FLOODEDNESS_XZ_SCALE = 0.67;
    /** AQUIFER_FLUID_LEVEL_SPREAD field coordinate xz-scale. */
    public static final double SPREAD_XZ_SCALE = 0.7142857142857143;

    // ---- nether / end recipe constants (verified against noise_settings/nether.json & end.json) ----
    /**
     * The shared {@code final_density} scale factor (nether.json and end.json both
     * wrap the cheese in {@code mul(0.64, …)} then {@code squeeze} to [−1, 1]).
     */
    public static final double CHEESE_SCALE = 0.64;
    /** Nether {@code nether/base_3d_noise} density-function label. */
    public static final String NETHER_BASE_3D_LABEL = "minecraft:nether/base_3d_noise";
    /** End {@code end/base_3d_noise} density-function label. */
    public static final String END_BASE_3D_LABEL = "minecraft:end/base_3d_noise";
    /** Nether {@code nether/base_3d_noise} coordinate scales (old_blended_noise json). */
    public static final double NETHER_BASE_3D_XZ_SCALE = 0.25;
    public static final double NETHER_BASE_3D_Y_SCALE = 0.375;
    /** End {@code end/base_3d_noise} coordinate scales (old_blended_noise json). */
    public static final double END_BASE_3D_XZ_SCALE = 0.25;
    public static final double END_BASE_3D_Y_SCALE = 0.25;

    private final Density barrierNoise;
    private final Density fluidLevelFloodednessNoise;
    private final Density fluidLevelSpreadNoise;
    private final Density lavaNoise;
    private final Density temperature;
    private final Density vegetation;
    private final Density continents;
    private final Density erosion;
    private final Density depth;
    private final Density ridges;
    private final Density initialDensityWithoutJaggedness;
    private final Density finalDensity;
    private final Density veinToggle;
    private final Density veinRidged;
    private final Density veinGap;

    private final long worldSeed;

    /** The constant-zero {@code Density}, shared by every constant-0 nether/end field. */
    private static final Density C0 = (x, y, z) -> 0.0;

    private NoiseRouter(long worldSeed, Density barrierNoise, Density fluidLevelFloodednessNoise,
                        Density fluidLevelSpreadNoise, Density lavaNoise, Density temperature,
                        Density vegetation, Density continents, Density erosion, Density depth,
                        Density ridges, Density initialDensityWithoutJaggedness, Density finalDensity,
                        Density veinToggle, Density veinRidged, Density veinGap) {
        this.worldSeed = worldSeed;
        this.barrierNoise = requireNonNull(barrierNoise, "barrierNoise");
        this.fluidLevelFloodednessNoise = requireNonNull(fluidLevelFloodednessNoise, "fluidLevelFloodednessNoise");
        this.fluidLevelSpreadNoise = requireNonNull(fluidLevelSpreadNoise, "fluidLevelSpreadNoise");
        this.lavaNoise = requireNonNull(lavaNoise, "lavaNoise");
        this.temperature = requireNonNull(temperature, "temperature");
        this.vegetation = requireNonNull(vegetation, "vegetation");
        this.continents = requireNonNull(continents, "continents");
        this.erosion = requireNonNull(erosion, "erosion");
        this.depth = requireNonNull(depth, "depth");
        this.ridges = requireNonNull(ridges, "ridges");
        this.initialDensityWithoutJaggedness =
                requireNonNull(initialDensityWithoutJaggedness, "initialDensityWithoutJaggedness");
        this.finalDensity = requireNonNull(finalDensity, "finalDensity");
        this.veinToggle = requireNonNull(veinToggle, "veinToggle");
        this.veinRidged = requireNonNull(veinRidged, "veinRidged");
        this.veinGap = requireNonNull(veinGap, "veinGap");
    }

    private static Density requireNonNull(Density d, String name) {
        return Objects.requireNonNull(d, name);
    }

    /** The world seed this router was built from. */
    public long worldSeed() {
        return worldSeed;
    }

    public Density barrierNoise() {
        return barrierNoise;
    }

    public Density fluidLevelFloodednessNoise() {
        return fluidLevelFloodednessNoise;
    }

    public Density fluidLevelSpreadNoise() {
        return fluidLevelSpreadNoise;
    }

    public Density lavaNoise() {
        return lavaNoise;
    }

    public Density temperature() {
        return temperature;
    }

    public Density vegetation() {
        return vegetation;
    }

    public Density continents() {
        return continents;
    }

    public Density erosion() {
        return erosion;
    }

    public Density depth() {
        return depth;
    }

    public Density ridges() {
        return ridges;
    }

    public Density initialDensityWithoutJaggedness() {
        return initialDensityWithoutJaggedness;
    }

    public Density finalDensity() {
        return finalDensity;
    }

    public Density veinToggle() {
        return veinToggle;
    }

    public Density veinRidged() {
        return veinRidged;
    }

    public Density veinGap() {
        return veinGap;
    }

    /** The field at canonical index {@code i} (0..14). */
    public Density fieldAt(int i) {
        switch (i) {
            case 0:return barrierNoise;
            case 1:return fluidLevelFloodednessNoise;
            case 2:return fluidLevelSpreadNoise;
            case 3:return lavaNoise;
            case 4:return temperature;
            case 5:return vegetation;
            case 6:return continents;
            case 7:return erosion;
            case 8:return depth;
            case 9:return ridges;
            case 10:return initialDensityWithoutJaggedness;
            case 11:return finalDensity;
            case 12:return veinToggle;
            case 13:return veinRidged;
            case 14:return veinGap;
            default:throw new IllegalArgumentException("no router field at index " + i);
        }
    }

    /** The number of fields (15). */
    public int fieldCount() {
        return 15;
    }

    // ===================================================================
    //  overworld recipe
    // ===================================================================

    /** Overworld with the vanilla block range {@code minY=-64, maxY=320}. */
    public static NoiseRouter overworld(long worldSeed) {
        return overworld(worldSeed, -64, 320);
    }

    /**
     * Assembles the vanilla overworld router for {@code worldSeed} over
     * {@code [minY, maxY)}. All fifteen fields are finite, deterministic and
     * world-seed-sensitive (the four climate families and the aquifer/vein fields
     * use the exact vanilla labels, octave anchors and amplitude lists; the
     * composite fields are reduced stand-ins, documented above).
     *
     * @param worldSeed the master world seed.
     * @param minY      minimum block Y (inclusive).
     * @param maxY      maximum block Y (exclusive).
     * @throws IllegalArgumentException if {@code minY >= maxY}.
     */
    public static NoiseRouter overworld(long worldSeed, int minY, int maxY) {
        if (minY >= maxY) {
            throw new IllegalArgumentException("bad Y range: minY=" + minY + " maxY=" + maxY);
        }
        double height = (double) (maxY - minY);
        double midY = (minY + maxY) / 2.0;

        // --- per-noise climate / aquifer fields (exact vanilla labels + data) ---
        Density cont = noise(worldSeed, "minecraft:continents",
                CONTINENTS_FIRST_OCTAVE, CONTINENTS_AMPLITUDES, 1.0, 1.0, 1.0);
        Density eros = noise(worldSeed, "minecraft:erosion",
                EROSION_FIRST_OCTAVE, EROSION_AMPLITUDES, 1.0, 1.0, 1.0);
        Density ridge = noise(worldSeed, "minecraft:ridge",
                RIDGE_FIRST_OCTAVE, RIDGE_AMPLITUDES, 1.0, 1.0, 1.0);

        // shift_x / shift_z share the SHIFT ("minecraft:offset") parameters.
        Density shift = noise(worldSeed, "minecraft:offset",
                SHIFT_FIRST_OCTAVE, SHIFT_AMPLITUDES, 1.0, 1.0, 1.0);

        Density tempRaw = noise(worldSeed, "minecraft:temperature",
                TEMPERATURE_FIRST_OCTAVE, TEMPERATURE_AMPLITUDES, TEMPERATURE_AMPLITUDE, 1.0, 1.0);
        Density vegRaw = noise(worldSeed, "minecraft:vegetation",
                VEGETATION_FIRST_OCTAVE, VEGETATION_AMPLITUDES, 1.0, 1.0, 1.0);

        // temperature()/vegetation() = shiftedNoise2d(shiftX, shiftZ, 0.25, ...):
        // domain-shift x/z by 0.25 * the shift noise (reduced to a 3-D shift).
        double shiftScale = 0.25;
        Density temperature = shifted2d(tempRaw, shift, shiftScale);
        Density vegetation = shifted2d(vegRaw, shift, shiftScale);

        Density barrier = noise(worldSeed, "minecraft:aquifer_barrier", -3, single(), 1.0,
                BARRIER_XZ_SCALE, 1.0);
        Density floodedness = noise(worldSeed, "minecraft:aquifer_fluid_level_floodedness", -7, single(), 1.0,
                FLOODEDNESS_XZ_SCALE, 1.0);
        Density spread = noise(worldSeed, "minecraft:aquifer_fluid_level_spread", -5, single(), 1.0,
                SPREAD_XZ_SCALE, 1.0);
        Density lava = noise(worldSeed, "minecraft:aquifer_lava", -1, single(), 1.0, 1.0, 1.0);

        // --- vein fields (real labels, empty amplitudes -> single octave stand-in) ---
        Density veininess = noise(worldSeed, "minecraft:ore_veininess", -8, single(), 1.0, 1.0, 1.0);
        Density veinA = noise(worldSeed, "minecraft:ore_vein_a", -7, single(), 1.0, 1.0, 1.0);
        Density veinB = noise(worldSeed, "minecraft:ore_vein_b", -7, single(), 1.0, 1.0, 1.0);
        Density gap = noise(worldSeed, "minecraft:ore_gap", -5, single(), 1.0, 1.0, 1.0);

        Density veinToggle = veininess;
        Density veinRidged = (x, y, z) -> Math.abs(veinA.eval(x, y, z)) + Math.abs(veinB.eval(x, y, z));
        Density veinGap = gap;

        // --- composite fields: REDUCED stand-ins (p.1.8.12), replaced below (p.1.8.14) ---
        // These laminas are used only to form a throwaway base router whose climate
        // fields feed DensityComposite; the final router carries the corrected fields.
        Density baseDepth = (x, y, z) -> 2.0 * (y - midY) / height + 0.5 * eros.eval(x, y, z);
        Density baseInitial = (x, y, z) -> 4.0 - 1.5625 * (1.0 + 0.5 * cont.eval(x, y, z) + 0.5 * eros.eval(x, y, z));
        Density baseFinal = (x, y, z) -> {
            double d = baseInitial.eval(x, y, z);
            double vertical = 2.0 * (y - midY) / height;
            return vertical + 0.5 * d + 0.25 * ridge.eval(x, y, z);
        };

        // p.1.8.14: install the vanilla-compatible composite pipeline (spline / slide /
        // jaggedness / shiftedNoise / range-choice) onto depth(8), initial(10), final(11).
        NoiseRouter base = new NoiseRouter(worldSeed, barrier, floodedness, spread, lava,
                temperature, vegetation, cont, eros, baseDepth, ridge, baseInitial, baseFinal,
                veinToggle, veinRidged, veinGap);
        DensityComposite.Overworld comp = DensityComposite.overworld(base, minY, maxY);

        return new NoiseRouter(worldSeed, barrier, floodedness, spread, lava, temperature, vegetation,
                cont, eros, comp.depth(), ridge, comp.initialDensityWithoutJaggedness(), comp.finalDensity(),
                veinToggle, veinRidged, veinGap);
    }

    /** A single-octave-{1.0} amplitude list stand-in for empty vanilla lists. */
    private static double[] single() {
        return new double[]{1.0};
    }

    // ===================================================================
    //  nether recipe
    // ===================================================================

    /** Vanilla nether router with the vanilla block window {@code [0, 256)}. */
    public static NoiseRouter nether(long worldSeed) {
        return nether(worldSeed, 0, 256);
    }

    /**
     * Assembles the vanilla nether router (p.1.8.20), mirroring the fifteen-field
     * overview of {@code noise_settings/nether.json}: the aquifer / lava /
     * climate / vein fields that nether.json pins to the constant {@code 0.0}
     * ({@code barrier}, the two fluid levels, {@code lava}, {@code continents},
     * {@code erosion}, {@code depth}, {@code ridges},
     * {@code initialDensityWithoutJaggedness}, and the three vein fields) are
     * rendered as the constant-zero {@code Density}; {@code temperature} and
     * {@code vegetation} are the nether.json {@code shifted_noise} over the
     * overworld {@code temperature}/{@code vegetation} noises (xz_scale 0.25);
     * and {@code finalDensity} is the nether cheese — a {@code base_3d_noise}
     * leaf wrapped in two {@code y_clamped_gradient} falls, an offset chain and
     * {@code mul(0.64, …)} then {@code squeeze} (clamped to [−1, 1]):
     *
     * <pre>
     *   base = nether/base_3d_noise (xz 0.25, y 0.375)
     *   final = clamp( 0.64 * (2.5 + g1(a) * (−2.5 + 0.9375 + g2(a) * (base − 0.9375))),
     *                  −1, 1 )
     *   g1 = yClamp(y, −8, 24, 0, 1);  g2 = yClamp(y, 104, 128, 1, 0)
     * </pre>
     *
     * Deterministic, immortal, allocation-free in the hot path; the base_3d / shift
     * leaves are seed-sensitive reduced stand-ins in the same style as the overworld
     * factory (per the p.1.8.20 deferred recipe batch).
     *
     * @param worldSeed the master world seed.
     * @param minY      minimum block Y (inclusive).
     * @param maxY      maximum block Y (exclusive).
     * @throws IllegalArgumentException if {@code minY >= maxY}.
     */
    public static NoiseRouter nether(long worldSeed, int minY, int maxY) {
        if (minY >= maxY) {
            throw new IllegalArgumentException("bad Y range: minY=" + minY + " maxY=" + maxY);
        }
        Density zero = C0;
        Density shift = noise(worldSeed, "minecraft:offset",
                SHIFT_FIRST_OCTAVE, SHIFT_AMPLITUDES, 1.0, 1.0, 1.0);
        Density tempRaw = noise(worldSeed, "minecraft:temperature",
                TEMPERATURE_FIRST_OCTAVE, TEMPERATURE_AMPLITUDES, TEMPERATURE_AMPLITUDE, 1.0, 1.0);
        Density vegRaw = noise(worldSeed, "minecraft:vegetation",
                VEGETATION_FIRST_OCTAVE, VEGETATION_AMPLITUDES, 1.0, 1.0, 1.0);
        Density temperature = shifted2d(tempRaw, shift, 0.25);
        Density vegetation = shifted2d(vegRaw, shift, 0.25);
        Density finalDensity = cheese(worldSeed, NETHER_BASE_3D_LABEL, NETHER_BASE_3D_XZ_SCALE,
                NETHER_BASE_3D_Y_SCALE, -8.0, 24.0, 104.0, 128.0, 2.5);
        return new NoiseRouter(worldSeed, zero, zero, zero, zero, temperature, vegetation,
                zero, zero, zero, zero, zero, finalDensity, zero, zero, zero);
    }

    // ===================================================================
    //  end recipe
    // ===================================================================

    /** Vanilla end router with the vanilla block window {@code [0, 256)}. */
    public static NoiseRouter end(long worldSeed) {
        return end(worldSeed, 0, 256);
    }

    /**
     * Assembles the vanilla end router (p.1.8.20), mirroring the fifteen-field
     * overview of {@code noise_settings/end.json}: the twelve fields end.json pins
     * to the constant {@code 0.0} ({@code barrier}, the two fluid levels,
     * {@code lava}, {@code temperature}, {@code vegetation}, {@code continents},
     * {@code depth}, {@code ridges}, and the three vein fields) are rendered as
     * the constant-zero {@code Density}. {@code erosion} is
     * {@code cache_2d(end_islands)} (a reduced 2-D seed-sensitive leaf); and both
     * {@code initialDensityWithoutJaggedness} and {@code finalDensity} compose that
     * island signal with two {@code y_clamped_gradient} falls plus the
     * {@code end/sloped_cheese} ({@code end_islands + end/base_3d_noise}) —
     * {@code finalDensity} additionally scaled by 0.64 and {@code squeeze}d:
     *
     * <pre>
     *   islands = end_islands (2-D, seed-sensitive reduced leaf)
     *   base    = end/base_3d_noise (xz 0.25, y 0.25)
     *   sc      = islands + base
     *   g1 = yClamp(y, 4, 32, 0, 1);  g2 = yClamp(y, 56, 312, 1, 0)
     *   initial = −0.234375 + g1 * (0.234375 + g2 * (23.4375 + (−0.703125 + islands)) − 23.4375)
     *   final   = clamp( 0.64 * (−0.234375 + g1 * (0.234375 + g2 * (23.4375 + sc) − 23.4375)), −1, 1 )
     * </pre>
     *
     * Deterministic, immortal, allocation-free in the hot path; the island / base
     * leaves are seed-sensitive reduced stand-ins (the island shape is structurally
     * per-coordinate in the same reduced style as the overworld factory).
     *
     * @param worldSeed the master world seed.
     * @param minY      minimum block Y (inclusive).
     * @param maxY      maximum block Y (exclusive).
     * @throws IllegalArgumentException if {@code minY >= maxY}.
     */
    public static NoiseRouter end(long worldSeed, int minY, int maxY) {
        if (minY >= maxY) {
            throw new IllegalArgumentException("bad Y range: minY=" + minY + " maxY=" + maxY);
        }
        Density zero = C0;
        Density islands = noise2d(worldSeed, "minecraft:end_islands", 1.0);
        Density base = noise3d(worldSeed, END_BASE_3D_LABEL, END_BASE_3D_XZ_SCALE, END_BASE_3D_Y_SCALE);
        Density initial = endInitial(islands);
        Density finalDensity = endFinal(islands, base);
        return new NoiseRouter(worldSeed, zero, zero, zero, zero, zero, zero, zero,
                islands, zero, zero, initial, finalDensity, zero, zero, zero);
    }

    /** {@code initial_density_without_jaggedness} of the end.json recipe. */
    private static Density endInitial(Density islands) {
        return (x, y, z) -> {
            double e = islands.eval(x, 0.0, z);
            double g1 = SlideFn.grad(y, 4.0, 32.0, 0.0, 1.0);
            double g2 = SlideFn.grad(y, 56.0, 312.0, 1.0, 0.0);
            return -0.234375 + g1 * (0.234375 + g2 * (23.4375 + (-0.703125 + e)) - 23.4375);
        };
    }

    /** {@code final_density} (cheese + squeeze) of the end.json recipe. */
    private static Density endFinal(Density islands, Density base) {
        return (x, y, z) -> {
            double sc = islands.eval(x, 0.0, z) + base.eval(x, y, z);
            double g1 = SlideFn.grad(y, 4.0, 32.0, 0.0, 1.0);
            double g2 = SlideFn.grad(y, 56.0, 312.0, 1.0, 0.0);
            double raw = CHEESE_SCALE * (-0.234375 + g1 * (0.234375 + g2 * (23.4375 + sc) - 23.4375));
            return clamp01(raw);
        };
    }

    /** The nether cheese {@code final_density} (base_3d + twin y-gradients + squeeze). */
    private static Density cheese(long worldSeed, String baseLabel, double xzScale, double yScale,
                                  double g1FromY, double g1ToY, double g2FromY, double g2ToY, double water) {
        Density base = noise3d(worldSeed, baseLabel, xzScale, yScale);
        return (x, y, z) -> {
            double b = base.eval(x, y, z);
            double g1 = SlideFn.grad(y, g1FromY, g1ToY, 0.0, 1.0);
            double g2 = SlideFn.grad(y, g2FromY, g2ToY, 1.0, 0.0);
            double raw = CHEESE_SCALE * (water + g1 * (-water + 0.9375 + g2 * (b - 0.9375)));
            return clamp01(raw);
        };
    }

    private static double clamp01(double v) {
        return v < -1.0 ? -1.0 : (v > 1.0 ? 1.0 : v);
    }

    /** Builds a {@link Density} that samples a {@link NormalNoise} seeded from the
     * label and scales coordinates by {@code (xzScale, yScale, xzScale)}. */
    private static Density noise(long worldSeed, String label, int firstOctave, double[] amps,
                                 double amplitude, double xzScale, double yScale) {
        NormalNoise n = NormalNoise.create(PositionalRand.deriveLong(worldSeed, label), firstOctave, amps);
        return (x, y, z) -> amplitude * n.getValue(x * xzScale, y * yScale, z * xzScale);
    }

    /** shiftedNoise2d-style domain shift: sample at {@code (x + s*shift, y, z + s*shift)}. */
    private static Density shifted2d(Density base, Density shift, double s) {
        return (x, y, z) -> base.eval(x + s * shift.eval(x, y, z), y, z + s * shift.eval(x, y, z));
    }

    /** A deterministic 3-D normal-noise leaf from a derived per-label seed (reduced stand-in). */
    private static Density noise3d(long worldSeed, String label, double xzScale, double yScale) {
        NormalNoise n = NormalNoise.create(PositionalRand.deriveLong(worldSeed, label), -2, single());
        return (x, y, z) -> n.getValue(x * xzScale, y * yScale, z * xzScale);
    }

    /** A deterministic 2-D normal-noise leaf (xz plane) from a derived per-label seed. */
    private static Density noise2d(long worldSeed, String label, double xzScale) {
        NormalNoise n = NormalNoise.create(PositionalRand.deriveLong(worldSeed, label), -3, single());
        return (x, y, z) -> n.getValue(x * xzScale, 0.0, z * xzScale);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("NoiseRouter[worldSeed=").append(worldSeed);
        for (String name : FIELD_NAMES) {
            sb.append(", ").append(name);
        }
        return sb.append(']').toString();
    }

    /** Minimal self-description accepted by {@link #fromTd}: {@code "[ seed = <w> ]"}. */
    public String td() {
        return "[ seed = " + worldSeed + " ]";
    }

    /** Rebuilds the overworld router from a {@link #td()} snippet (default range). */
    public static NoiseRouter fromTd(String td) {
        if (td == null) {
            throw new IllegalArgumentException("td must not be null");
        }
        int lb = td.indexOf('[');
        int rb = td.indexOf(']');
        if (lb < 0 || rb < 0 || rb <= lb) {
            throw new IllegalArgumentException("cannot parse td: " + td);
        }
        String body = td.substring(lb + 1, rb);
        int eq = body.indexOf('=');
        long seed;
        try {
            seed = Long.parseLong(body.substring(eq + 1).trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("bad seed in td: " + td, e);
        }
        return overworld(seed);
    }
}