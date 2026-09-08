package io.toterra.subterra.optim.worldgen.pipeline.router;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.toterra.subterra.optim.worldgen.pipeline.composite.DensityComposite;
import io.toterra.subterra.optim.worldgen.pipeline.noise.simplex.SimplexNoise;
import io.toterra.subterra.optim.worldgen.pipeline.composite.ShiftedNoiseFn;
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
 * fromHashOf} semantics) and every Perlin leaf looks up its octave anchor and
 * amplitude list in {@link #NOISE_REGISTRATIONS} (p.1.8.28) — the faithful 1.21.1
 * registrations transcribed exactly from the {@code data/minecraft/worldgen/noise/*.json}
 * files of the shipped client jar (the six climate families, the aquifer / ore-vein
 * router noises, and the {@code jagged} / {@code cave_entrance} terrain leaves).
 * Since p.1.8.29B the leaves without a Perlin registration are the real 1.21.1
 * density functions: the base-3d leaves ({@code old_blended_noise} DFs) are the
 * {@link BlendedNoise} octave-blend computer and the end islands are the
 * {@link EndIslandNoise} island shape. The composite
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
 * from the real {@link BlendedNoise} base-3d and {@link EndIslandNoise} leaves over the
 * same seam.
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
 * {@link PositionalRand#deriveLong}（MD5 {@code fromHashOf} 语义），每个 Perlin
 * 叶子的 octave 锚与振幅列表查表自 {@link #NOISE_REGISTRATIONS}（p.1.8.28）——
 * 即从随包客户端 jar 的 {@code data/minecraft/worldgen/noise/*.json} 精确转写的
 * 1.21.1 注册（六个气候族、aquifer / 矿脉路由器噪声、{@code jagged} /
 * {@code cave_entrance} 地形叶）。自 p.1.8.29B 起，无 Perlin 注册的叶子改用真实
 * 1.21.1 密度函数：base-3d 叶（{@code old_blended_noise} DF）为 {@link BlendedNoise}
 * 八度混合计算机，末地岛屿为 {@link EndIslandNoise} 岛形。组合字段
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

    // ---- vanilla 1.21.1 noise registrations (p.1.8.28) ----

    /**
     * A vanilla noise registration: the first-octave anchor plus the per-octave
     * amplitude list, transcribed from {@code data/minecraft/worldgen/noise/*.json}
     * of the shipped 1.21.1 client jar. The amplitude array is defensively copied
     * in and out, so instances are immutable.
     */
    public record NoiseReg(int firstOctave, double[] amplitudes) {
        /** Defensive copy of the amplitudes. */
        public NoiseReg {
            Objects.requireNonNull(amplitudes, "amplitudes");
            amplitudes = amplitudes.clone();
        }

        @Override
        public double[] amplitudes() {
            return amplitudes.clone();
        }
    }

    /**
     * The per-label noise registrations used by the router and composite leaves
     * (p.1.8.28): the sixteen vanilla 1.21.1 Perlin registrations transcribed
     * exactly from {@code data/minecraft/worldgen/noise/*.json} in the shipped
     * client jar — the six climate families ({@code temperature},
     * {@code vegetation}, {@code continentalness}, {@code erosion},
     * {@code ridge}, {@code offset}), the four aquifer and four ore-vein router
     * noises, and the {@code jagged} / {@code cave_entrance} terrain leaves —
     * The leaves without a vanilla Perlin registration (the base-3d
     * {@code old_blended_noise} families and the end-island shape) have their own
     * real 1.21.1 density functions since p.1.8.29B ({@link BlendedNoise} /
     * {@link EndIslandNoise}) and carry no registry entries here. Keyed by the
     * vanilla noise label, which is also the string hashed into the
     * per-noise seed chain; built once, unmodifiable.
     */
    public static final Map<String, NoiseReg> NOISE_REGISTRATIONS = createRegistrations();

    private static Map<String, NoiseReg> createRegistrations() {
        Map<String, NoiseReg> m = new LinkedHashMap<>();
        // climate families (noise_settings/overworld.json + density_function/overworld/*.json)
        m.put("minecraft:temperature", new NoiseReg(-10,
                new double[]{1.5, 0.0, 1.0, 0.0, 0.0, 0.0}));
        m.put("minecraft:vegetation", new NoiseReg(-8,
                new double[]{1.0, 1.0, 0.0, 0.0, 0.0, 0.0}));
        m.put("minecraft:continentalness", new NoiseReg(-9,
                new double[]{1.0, 1.0, 2.0, 2.0, 2.0, 1.0, 1.0, 1.0, 1.0}));
        m.put("minecraft:erosion", new NoiseReg(-9,
                new double[]{1.0, 1.0, 0.0, 1.0, 1.0}));
        m.put("minecraft:ridge", new NoiseReg(-7,
                new double[]{1.0, 2.0, 1.0, 0.0, 0.0, 0.0}));
        m.put("minecraft:offset", new NoiseReg(-3,
                new double[]{1.0, 1.0, 1.0, 0.0}));
        // aquifer fields (noise_settings/overworld.json noise_router)
        m.put("minecraft:aquifer_barrier", new NoiseReg(-3, new double[]{1.0}));
        m.put("minecraft:aquifer_fluid_level_floodedness", new NoiseReg(-7, new double[]{1.0}));
        m.put("minecraft:aquifer_fluid_level_spread", new NoiseReg(-5, new double[]{1.0}));
        m.put("minecraft:aquifer_lava", new NoiseReg(-1, new double[]{1.0}));
        // ore-vein fields (noise_settings/overworld.json noise_router)
        m.put("minecraft:ore_veininess", new NoiseReg(-8, new double[]{1.0}));
        m.put("minecraft:ore_vein_a", new NoiseReg(-7, new double[]{1.0}));
        m.put("minecraft:ore_vein_b", new NoiseReg(-7, new double[]{1.0}));
        m.put("minecraft:ore_gap", new NoiseReg(-5, new double[]{1.0}));
        // terrain leaves (density_function/overworld/sloped_cheese.json + caves/entrances.json)
        m.put("minecraft:jagged", new NoiseReg(-16,
                new double[]{1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0,
                        1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0}));
        m.put("minecraft:cave_entrance", new NoiseReg(-7, new double[]{0.4, 0.5, 1.0}));
        // base_3d_noise / end_islands have no Perlin registration: since p.1.8.29B they
        // are built from their real 1.21.1 density functions (BlendedNoise / EndIslandNoise),
        // so no stand-in entries exist in this registry.
        return Collections.unmodifiableMap(m);
    }

    /**
     * The 1.21.1 registration for {@code label}.
     *
     * @throws IllegalArgumentException for an unknown label.
     */
    public static NoiseReg registration(String label) {
        NoiseReg reg = NOISE_REGISTRATIONS.get(Objects.requireNonNull(label, "label"));
        if (reg == null) {
            throw new IllegalArgumentException("unknown noise label: " + label);
        }
        return reg;
    }

    /** AQUIFER_BARRIER field coordinate xz-scale (barrier_noise JSON: xz_scale=1.0). */
    public static final double BARRIER_XZ_SCALE = 1.0;
    /** AQUIFER_BARRIER field coordinate y-scale (barrier_noise JSON: y_scale=0.5). */
    public static final double BARRIER_Y_SCALE = 0.5;
    /** AQUIFER_FLUID_LEVEL_FLOODEDNESS field coordinate xz-scale (floodedness JSON: xz_scale=1.0). */
    public static final double FLOODEDNESS_XZ_SCALE = 1.0;
    /** AQUIFER_FLUID_LEVEL_FLOODEDNESS field coordinate y-scale (floodedness JSON: y_scale=0.67). */
    public static final double FLOODEDNESS_Y_SCALE = 0.67;
    /** AQUIFER_FLUID_LEVEL_SPREAD field coordinate xz-scale (spread JSON: xz_scale=1.0). */
    public static final double SPREAD_XZ_SCALE = 1.0;
    /** AQUIFER_FLUID_LEVEL_SPREAD field coordinate y-scale (spread JSON: y_scale=0.7142857142857143). */
    public static final double SPREAD_Y_SCALE = 0.7142857142857143;

    // ---- nether / end recipe constant (verified against noise_settings/nether.json & end.json) ----
    /**
     * The shared {@code final_density} scale factor (nether.json and end.json both
     * wrap the cheese in {@code mul(0.64, …)} then {@code squeeze} to [−1, 1]).
     */
    public static final double CHEESE_SCALE = 0.64;
    // The base_3d parameter sets live on BlendedNoise (OVERWORLD_*/NETHER_*/END_*),
    // and the end-island leaf on EndIslandNoise (p.1.8.29B real transcriptions).

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

        // --- per-noise climate / aquifer fields (exact vanilla labels + 1.21.1 registrations) ---
        Density contRaw = noise(worldSeed, "minecraft:continentalness", 1.0, 1.0, 1.0);
        Density erosRaw = noise(worldSeed, "minecraft:erosion", 1.0, 1.0, 1.0);
        Density ridgeRaw = noise(worldSeed, "minecraft:ridge", 1.0, 1.0, 1.0);

        // shift_x / shift_z share the SHIFT ("minecraft:offset") parameters.
        Density shiftRaw = noise(worldSeed, "minecraft:offset", 1.0, 1.0, 1.0);

        Density tempRaw = noise(worldSeed, "minecraft:temperature", 1.0, 1.0, 1.0);
        Density vegRaw = noise(worldSeed, "minecraft:vegetation", 1.0, 1.0, 1.0);

        // p.1.8.27A: the vanilla climate warp. overworld.json's five climate fields are
        // shifted_noise over shift_x/shift_z (verified via the shipped 1.21.1 client jar
        // data/minecraft/worldgen/density_function/{shift_x,shift_z}.json and the
        // ShiftA/ShiftB bytecode): shift_x(x,z) = 4*offset(0.25x, 0, 0.25z) and
        // shift_z(x,z) = 4*offset(0.25z, 0.25x, 0) — both y-independent. Every climate
        // leaf is then sampled at (x*0.25 + shift_x, 0, z*0.25 + shift_z).
        Density shiftX = shiftX(shiftRaw);
        Density shiftZ = shiftZ(shiftRaw);
        double climateScale = 0.25;
        Density temperature = ShiftedNoiseFn.shiftedNoise2d(tempRaw, shiftX, shiftZ, climateScale);
        Density vegetation = ShiftedNoiseFn.shiftedNoise2d(vegRaw, shiftX, shiftZ, climateScale);
        Density continents = ShiftedNoiseFn.shiftedNoise2d(contRaw, shiftX, shiftZ, climateScale);
        Density erosion = ShiftedNoiseFn.shiftedNoise2d(erosRaw, shiftX, shiftZ, climateScale);
        Density ridges = ShiftedNoiseFn.shiftedNoise2d(ridgeRaw, shiftX, shiftZ, climateScale);

        Density barrier = noise(worldSeed, "minecraft:aquifer_barrier", 1.0, BARRIER_XZ_SCALE, BARRIER_Y_SCALE);
        Density floodedness = noise(worldSeed, "minecraft:aquifer_fluid_level_floodedness", 1.0,
                FLOODEDNESS_XZ_SCALE, FLOODEDNESS_Y_SCALE);
        Density spread = noise(worldSeed, "minecraft:aquifer_fluid_level_spread", 1.0,
                SPREAD_XZ_SCALE, SPREAD_Y_SCALE);
        Density lava = noise(worldSeed, "minecraft:aquifer_lava", 1.0, 1.0, 1.0);

        // --- vein fields (real labels + the vanilla single-amplitude registrations) ---
        Density veininess = noise(worldSeed, "minecraft:ore_veininess", 1.0, 1.0, 1.0);
        Density veinA = noise(worldSeed, "minecraft:ore_vein_a", 1.0, 1.0, 1.0);
        Density veinB = noise(worldSeed, "minecraft:ore_vein_b", 1.0, 1.0, 1.0);
        Density gap = noise(worldSeed, "minecraft:ore_gap", 1.0, 1.0, 1.0);

        Density veinToggle = veininess;
        Density veinRidged = (x, y, z) -> Math.abs(veinA.eval(x, y, z)) + Math.abs(veinB.eval(x, y, z));
        Density veinGap = gap;

        // --- composite fields: REDUCED stand-ins (p.1.8.12), replaced below (p.1.8.14) ---
        // These laminas are used only to form a throwaway base router whose climate
        // fields feed DensityComposite; the final router carries the corrected fields.
        Density baseDepth = (x, y, z) -> 2.0 * (y - midY) / height + 0.5 * erosion.eval(x, y, z);
        Density baseInitial = (x, y, z) -> 4.0 - 1.5625 * (1.0 + 0.5 * continents.eval(x, y, z)
                + 0.5 * erosion.eval(x, y, z));
        Density baseFinal = (x, y, z) -> {
            double d = baseInitial.eval(x, y, z);
            double vertical = 2.0 * (y - midY) / height;
            return vertical + 0.5 * d + 0.25 * ridges.eval(x, y, z);
        };

        // p.1.8.14: install the vanilla-compatible composite pipeline (spline / slide /
        // jaggedness / shiftedNoise / range-choice) onto depth(8), initial(10), final(11).
        NoiseRouter base = new NoiseRouter(worldSeed, barrier, floodedness, spread, lava,
                temperature, vegetation, continents, erosion, baseDepth, ridges, baseInitial, baseFinal,
                veinToggle, veinRidged, veinGap);
        DensityComposite.Overworld comp = DensityComposite.overworld(base, minY, maxY);

        return new NoiseRouter(worldSeed, barrier, floodedness, spread, lava, temperature, vegetation,
                continents, erosion, comp.depth(), ridges, comp.initialDensityWithoutJaggedness(),
                comp.finalDensity(), veinToggle, veinRidged, veinGap);
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
     * Deterministic, immortal, allocation-free in the hot path; the base_3d leaf is
     * the real 1.21.1 {@link BlendedNoise} (nether parameter set, p.1.8.29B) and the
     * shift leaves resolve through the vanilla shifted-noise warp.
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
        Density shiftRaw = noise(worldSeed, "minecraft:offset", 1.0, 1.0, 1.0);
        Density tempRaw = noise(worldSeed, "minecraft:temperature", 1.0, 1.0, 1.0);
        Density vegRaw = noise(worldSeed, "minecraft:vegetation", 1.0, 1.0, 1.0);
        // nether.json reuses the same shift_x/shift_z warp as the overworld (p.1.8.27A).
        Density temperature = ShiftedNoiseFn.shiftedNoise2d(tempRaw, shiftX(shiftRaw), shiftZ(shiftRaw), 0.25);
        Density vegetation = ShiftedNoiseFn.shiftedNoise2d(vegRaw, shiftX(shiftRaw), shiftZ(shiftRaw), 0.25);
        Density finalDensity = cheese(BlendedNoise.nether(worldSeed), -8.0, 24.0, 104.0, 128.0, 2.5);
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
     * {@code cache_2d(end_islands)} (the real 1.21.1 {@link EndIslandNoise} island
     * shape, p.1.8.29B); and both {@code initialDensityWithoutJaggedness} and
     * {@code finalDensity} compose that island signal with two
     * {@code y_clamped_gradient} falls plus the {@code end/sloped_cheese}
     * ({@code end_islands + end/base_3d_noise}) — {@code finalDensity}
     * additionally scaled by 0.64 and {@code squeeze}d:
     *
     * <pre>
     *   islands = end_islands (8-block-grid island shape)
     *   base    = end/base_3d_noise (BlendedNoise, end parameter set)
     *   sc      = islands + base
     *   g1 = yClamp(y, 4, 32, 0, 1);  g2 = yClamp(y, 56, 312, 1, 0)
     *   initial = −0.234375 + g1 * (0.234375 + g2 * (23.4375 + (−0.703125 + islands)) − 23.4375)
     *   final   = clamp( 0.64 * (−0.234375 + g1 * (0.234375 + g2 * (23.4375 + sc) − 23.4375)), −1, 1 )
     * </pre>
     *
     * Deterministic, immortal, allocation-free in the hot path; the island / base
     * leaves are the real 1.21.1 {@link EndIslandNoise} / {@link BlendedNoise}
     * transcriptions (p.1.8.29B).
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
        Density islands = new EndIslandNoise(worldSeed);
        Density base = BlendedNoise.end(worldSeed);
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
    private static Density cheese(Density base,
                                  double g1FromY, double g1ToY, double g2FromY, double g2ToY, double water) {
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
     * label, with octave anchor and amplitude list looked up from the 1.21.1
     * {@link #NOISE_REGISTRATIONS} table, and scales coordinates by
     * {@code (xzScale, yScale, xzScale)}. */
    private static Density noise(long worldSeed, String label, double amplitude, double xzScale, double yScale) {
        NoiseReg reg = registration(label);
        NormalNoise n = NormalNoise.create(PositionalRand.deriveLong(worldSeed, label),
                reg.firstOctave(), reg.amplitudes());
        return (x, y, z) -> amplitude * n.getValue(x * xzScale, y * yScale, z * xzScale);
    }

    /**
     * Vanilla {@code shift_x} (p.1.8.27A, verified against the 1.21.1 client jar's
     * {@code DensityFunctions$ShiftA} bytecode): {@code 4 * offset(0.25x, 0, 0.25z)}.
     * y-independent, matching the {@code flat_cache(cache_2d(...))} wrapper.
     */
    private static Density shiftX(Density offset) {
        return (x, y, z) -> 4.0 * offset.eval(0.25 * x, 0.0, 0.25 * z);
    }

    /**
     * Vanilla {@code shift_z} (p.1.8.27A, verified against the 1.21.1 client jar's
     * {@code DensityFunctions$ShiftB} bytecode): {@code 4 * offset(0.25z, 0.25x, 0)}.
     * y-independent, matching the {@code flat_cache(cache_2d(...))} wrapper.
     */
    private static Density shiftZ(Density offset) {
        return (x, y, z) -> 4.0 * offset.eval(0.25 * z, 0.25 * x, 0.0);
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