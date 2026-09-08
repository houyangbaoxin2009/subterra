package io.toterra.subterra.engine.worldgen.pipeline.composite;

import io.toterra.subterra.engine.worldgen.pipeline.density.Density;
import io.toterra.subterra.engine.worldgen.pipeline.noise.simplex.NormalNoise;
import io.toterra.subterra.engine.worldgen.pipeline.router.PositionalRand;

/**
 * The vanilla overworld cave family (p.1.8.27B): the full {@code when_out_of_range} carve
 * of the {@code overworld.json} {@code final_density} {@code range_choice}, which the
 * current composite collapses to the raw {@code sloped_cheese} — leaving the overworld
 * without normal caves and prone to blocky artifacts. Transcribed faithfully from the
 * 1.21.1 {@code overworld.json} {@code final_density} tree plus the referenced
 * {@code worldgen/density_function/overworld/caves/*.json} (all extracted from the
 * shipped 1.21.1 client jar and verified via javap of {@code DensityFunctions$Noise} /
 * {@code WeirdScaledSampler} / {@code NoiseRouterData$QuantizedSpaghettiRarity}).
 *
 * <p>The vanilla cave carve (the {@code when_out_of_range} of the cheese range-choice) is
 *
 * <pre>
 *   cheeseCave   = 4*square(noise(cave_layer, xz=1, y=8))
 *                + clamp(0.27 + cave_cheese, -1, 1)
 *                  * clamp(1.5 - 0.64*sloped_cheese, 0, 0.5)
 *   caveCurve    = min( min(cheeseCave, entrances), spaghettiTotal )
 *   when_out     = max( caveCurve, pillarsRangeChoice )
 *   pillarsRC    = radius &lt; 0.03 ? -1e6 : radius
 *   radius       = 2*pillar - (~1.0 - pillar_rareness)
 * </pre>
 *
 * with {@code entrances} = {@code overworld/caves/entrances},
 * {@code spaghettiTotal} = {@code spaghetti_2d + spaghetti_roughness_function}, and
 * {@code pillars.json} {@code radius = (2*pillar(x&times;25, y&times;0.3) + (-1 - (-1)*
 * pillar_rareness)) * cube(0.55 + 0.55*pillar_thickness)}. All coordinate scales and
 * the amplitude/octave noise registrations are the verified vanilla ones; the
 * {@code minecraft:cache_once}/{@code interpolated} wrappers are exact identities at a
 * single point and are dropped (same convention as the rest of the composite core).
 * Every leaf is a deterministic function of the world seed via
 * {@link PositionalRand#deriveLong}; evaluation is allocation-free and constant-time.
 *
 * <p>原生主世界洞穴族（p.1.8.27B）：{@code overworld.json} {@code final_density}
 * {@code range_choice} 的完整 {@code when_out_of_range} 雕刻，当前复合核心把它塌缩为
 * 裸 {@code sloped_cheese}，导致主世界缺失常规洞穴并易现方块化伪影。本文按 1.21.1
 * {@code overworld.json} {@code final_density} 树及其引用的
 * {@code worldgen/density_function/overworld/caves/*.json} 忠实转写（均取自随包发布的
 * 1.21.1 客户端 jar，并经 javap 对照 {@code DensityFunctions$Noise} /
 * {@code WeirdScaledSampler} / {@code NoiseRouterData$QuantizedSpaghettiRarity} 验证）：
 * {@code cheeseCave}、{@code caveCurve = min(min(cheeseCave,entrances),spaghettiTotal)}、
 * {@code when_out = max(caveCurve, pillarsRangeChoice)}，其中
 * {@code pillarsRC = radius < 0.03 ? -1e6 : radius}，
 * {@code radius = (2*pillar(x*25,y*0.3) + (-1 - (-1)*pillar_rareness)) *
 * cube(0.55 + 0.55*pillar_thickness)}。所有坐标尺度与振幅/八度噪声注册均为已验原生值；
 * {@code cache_once}/{@code interpolated} 包装在单点求值是恒等故略去（与复合核心其余
 * 部分同一约定）。每片叶子经 {@link PositionalRand#deriveLong} 由世界种子确定派生；
 * 求值零分配、常数时间。
 */
public final class CaveFamilyFn {

    private CaveFamilyFn() {
    }

    // ---- cheese arm (overworld.json) ----
    /** {@code cave_layer} coords (xz 1, y 8). */
    public static final double CAVE_LAYER_XZ = 1.0;
    public static final double CAVE_LAYER_Y = 8.0;
    /** {@code 4 * square(cave_layer)}. */
    public static final double CAVE_LAYER_AMP = 4.0;
    /** {@code cave_cheese} coords (xz 1, y 2/3). */
    public static final double CAVE_CHEESE_XZ = 1.0;
    public static final double CAVE_CHEESE_Y = 0.6666666666666666;
    public static final double CAVE_CHEESE_OFFSET = 0.27;
    public static final double SLOPED_OFFSET = 1.5;
    public static final double SLOPED_CHEESE_SCALE = -0.64;
    public static final double CHEESE_MOD_LO = -1.0;
    public static final double CHEESE_MOD_HI = 1.0;
    public static final double DEPTH_MOD_LO = 0.0;
    public static final double DEPTH_MOD_HI = 0.5;

    // ---- entrances.json ----
    public static final double ENTRANCE_XZ = 0.75;
    public static final double ENTRANCE_Y = 0.5;
    public static final double ENTRANCE_OFFSET = 0.37;
    public static final double ENTRANCE_GRAD_FROM_Y = -10.0;
    public static final double ENTRANCE_GRAD_TO_Y = 30.0;
    public static final double ENTRANCE_GRAD_FROM = 0.3;
    public static final double ENTRANCE_GRAD_TO = 0.0;
    public static final double SPAG3D_RARITY_XZ = 2.0;
    public static final double SPAG3D_THICKNESS_COEFF = -0.011499999999999996;
    public static final double SPAG3D_THICKNESS_OFFSET = -0.0765;

    // ---- spaghetti_2d.json / spaghetti_2d_thickness_modulator.json ----
    public static final double THICK_OFFSET = -0.95;
    public static final double THICK_COEFF = -0.35000000000000003;
    public static final double SPAG_THICK_XZ = 2.0;
    public static final double SPAG_THICK_Y = 1.0;
    public static final double SPAG_MOD_XZ = 2.0;
    public static final double SPAG_MOD_Y = 1.0;
    public static final double MODULATE_COEFF = 0.083;
    public static final double ELEVATION_XZ = 1.0;
    public static final double ELEVATION_Y = 0.0;
    public static final double ELEVATION_AMP = 8.0;
    public static final double ELEV_GRAD_FROM_Y = -64.0;
    public static final double ELEV_GRAD_TO_Y = 320.0;
    public static final double ELEV_GRAD_FROM = 8.0;
    public static final double ELEV_GRAD_TO = -40.0;

    // ---- spaghetti_roughness_function.json ----
    public static final double SR_OFFSET = -0.05;
    public static final double SR_MOD_COEFF = -0.05;
    public static final double SR_RIDGE_OFFSET = -0.4;

    // ---- pillars.json / overworld.json threshold ----
    public static final double PILLAR_XZ = 25.0;
    public static final double PILLAR_Y = 0.3;
    public static final double PILLAR_AMP = 2.0;
    public static final double PILLAR_RARE_XZ = 1.0;
    public static final double PILLAR_RARE_Y = 1.0;
    public static final double PILLAR_THICK_XZ = 1.0;
    public static final double PILLAR_THICK_Y = 1.0;
    public static final double PILLAR_FALL = 0.55;
    public static final double PILLAR_TRIGGER = 0.03;
    public static final double PILLAR_CARVE = -1.0e6;

    // ---- verified noise registrations (worldgen/noise/*.json) ----
    public static final int CAVE_LAYER_OCTAVE = -8;
    public static final int CAVE_CHEESE_OCTAVE = -8;
    public static final int ENTRANCE_OCTAVE = -7;
    public static final int SPAG3D_RARITY_OCTAVE = -11;
    public static final int SPAG3D_1_OCTAVE = -7;
    public static final int SPAG3D_2_OCTAVE = -7;
    public static final int SPAG3D_THICKNESS_OCTAVE = -8;
    public static final int SPAG_THICK_OCTAVE = -11;
    public static final int SPAG_MOD_OCTAVE = -11;
    public static final int ELEVATION_OCTAVE = -8;
    public static final int SR_MOD_OCTAVE = -8;
    public static final int SR_OCTAVE = -5;
    public static final int PILLAR_OCTAVE = -7;
    public static final int PILLAR_RARE_OCTAVE = -8;
    public static final int PILLAR_THICK_OCTAVE = -8;

    private static final double[] ONE = {1.0};
    private static final double[] TWO = {1.0, 1.0};
    private static final double[] CAVE_CHEESE_AMPS = {0.5, 1.0, 2.0, 1.0, 2.0, 1.0, 0.0, 2.0, 0.0};
    private static final double[] ENTRANCE_AMPS = {0.4, 0.5, 1.0};

    /**
     * The vanilla {@code overworld/caves/entrances} density function (entrances.json,
     * p.1.8.28): {@code min(0.37 + cave_entrance(xz 0.75, y 0.5) + grad(y, -10→30,
     * 0.3→0), spaghetti_roughness + clamp(max(type1 samplers) + thicknessBias, -1, 1))}.
     * The {@code cache_once} wrapper is an exact identity for single-point evaluation
     * and is dropped (same convention as the rest of the composite core). Shared by
     * the cave family's inner {@code min} and — scaled by 5 — by the cheese
     * range-choice's {@code when_in_range} arm in {@code overworld.json}.
     */
    public static Density entrances(long seed) {
        NormalNoise entrance = noise(seed, "minecraft:cave_entrance", ENTRANCE_OCTAVE, ENTRANCE_AMPS);
        NormalNoise spag3dRarity = noise(seed, "minecraft:spaghetti_3d_rarity", SPAG3D_RARITY_OCTAVE, ONE);
        NormalNoise spag3d1 = noise(seed, "minecraft:spaghetti_3d_1", SPAG3D_1_OCTAVE, ONE);
        NormalNoise spag3d2 = noise(seed, "minecraft:spaghetti_3d_2", SPAG3D_2_OCTAVE, ONE);
        NormalNoise spag3dThickness = noise(seed, "minecraft:spaghetti_3d_thickness", SPAG3D_THICKNESS_OCTAVE, ONE);
        NormalNoise srMod = noise(seed, "minecraft:spaghetti_roughness_modulator", SR_MOD_OCTAVE, ONE);
        NormalNoise sr = noise(seed, "minecraft:spaghetti_roughness", SR_OCTAVE, ONE);
        return (x, y, z) -> {
            double entA = ENTRANCE_OFFSET
                    + entrance.getValue(x * ENTRANCE_XZ, y * ENTRANCE_Y, z * ENTRANCE_XZ)
                    + SlideFn.grad(y, ENTRANCE_GRAD_FROM_Y, ENTRANCE_GRAD_TO_Y,
                    ENTRANCE_GRAD_FROM, ENTRANCE_GRAD_TO);
            // inner: spaghetti_roughness + clamp(max(type1 samplers) + thicknessBias, -1,1)
            double r3 = rarity1(spag3dRarity.getValue(x * SPAG3D_RARITY_XZ, y, z * SPAG3D_RARITY_XZ));
            double s3a = r3 * Math.abs(spag3d1.getValue(x / r3, y / r3, z / r3));
            double s3b = r3 * Math.abs(spag3d2.getValue(x / r3, y / r3, z / r3));
            double th3 = SPAG3D_THICKNESS_OFFSET
                    + SPAG3D_THICKNESS_COEFF * spag3dThickness.getValue(x, y, z);
            double spagRough = (SR_OFFSET + SR_MOD_COEFF * srMod.getValue(x, y, z))
                    * (SR_RIDGE_OFFSET + Math.abs(sr.getValue(x, y, z)));
            double entInner = clamp(spagRough + Math.max(s3a, s3b) + th3, -1.0, 1.0);
            return Math.min(entA, entInner);
        };
    }

    /**
     * The full overworld cave-carve {@link Density} for a world seed, i.e. the entire
     * {@code when_out_of_range} of the {@code final_density} cheese range-choice
     * ({@code max(min(min(cheeseCave, entrances), spaghettiTotal), pillarsRC)}),
     * where {@code slopedCheese} is the composite's cheese field and {@code min} picks
     * the most negative (most carved) arm. Deterministic, immutable, allocation-free.
     */
    public static Density overworld(long seed, Density slopedCheese) {
        // cheese leaves
        NormalNoise layer = noise(seed, "minecraft:cave_layer", CAVE_LAYER_OCTAVE, ONE);
        NormalNoise cheese = noise(seed, "minecraft:cave_cheese", CAVE_CHEESE_OCTAVE, CAVE_CHEESE_AMPS);
        // entrances.json (shared with the cheese range-choice's when_in_range arm)
        Density entrances = entrances(seed);
        // spaghetti leaves
        NormalNoise spagThick = noise(seed, "minecraft:spaghetti_2d_thickness", SPAG_THICK_OCTAVE, ONE);
        NormalNoise spagMod = noise(seed, "minecraft:spaghetti_2d_modulator", SPAG_MOD_OCTAVE, ONE);
        NormalNoise elevation = noise(seed, "minecraft:spaghetti_2d_elevation", ELEVATION_OCTAVE, ONE);
        NormalNoise spag = noise(seed, "minecraft:spaghetti_2d", -7, ONE);
        // roughness leaves (spaghetti_roughness_function.json — also used by entrances)
        NormalNoise srMod = noise(seed, "minecraft:spaghetti_roughness_modulator", SR_MOD_OCTAVE, ONE);
        NormalNoise sr = noise(seed, "minecraft:spaghetti_roughness", SR_OCTAVE, ONE);
        // pillar leaves
        NormalNoise pillar = noise(seed, "minecraft:pillar", PILLAR_OCTAVE, TWO);
        NormalNoise pillarRare = noise(seed, "minecraft:pillar_rareness", PILLAR_RARE_OCTAVE, ONE);
        NormalNoise pillarThick = noise(seed, "minecraft:pillar_thickness", PILLAR_THICK_OCTAVE, ONE);

        return (x, y, z) -> {
            // ---- cheeseCave ----
            double l = layer.getValue(x * CAVE_LAYER_XZ, y * CAVE_LAYER_Y, z * CAVE_LAYER_XZ);
            double c = cheese.getValue(x * CAVE_CHEESE_XZ, y * CAVE_CHEESE_Y, z * CAVE_CHEESE_XZ);
            double cm = clamp(CAVE_CHEESE_OFFSET + c, CHEESE_MOD_LO, CHEESE_MOD_HI);
            double sc = slopedCheese.eval(x, y, z);
            double dm = clamp(SLOPED_OFFSET + SLOPED_CHEESE_SCALE * sc, DEPTH_MOD_LO, DEPTH_MOD_HI);
            double cheeseCave = CAVE_LAYER_AMP * l * l + cm * dm;

            // ---- entrances (entrances.json; cache_once = identity) ----
            double entrancesVal = entrances.eval(x, y, z);

            // ---- spaghetti_2d (spaghetti_2d.json) ----
            double t = THICK_OFFSET + THICK_COEFF * spagThick.getValue(x * SPAG_THICK_XZ, y * SPAG_THICK_Y, z * SPAG_THICK_XZ);
            double mod = spagMod.getValue(x * SPAG_MOD_XZ, y * SPAG_MOD_Y, z * SPAG_MOD_XZ);
            double r2 = rarity2(mod);
            double spagScaled = r2 * Math.abs(spag.getValue(x / r2, y / r2, z / r2));
            double elevGrad = ELEVATION_AMP * elevation.getValue(x * ELEVATION_XZ, 0.0, z * ELEVATION_XZ)
                    + SlideFn.grad(y, ELEV_GRAD_FROM_Y, ELEV_GRAD_TO_Y, ELEV_GRAD_FROM, ELEV_GRAD_TO);
            double spagTerm1 = spagScaled + MODULATE_COEFF * t;
            double spagTerm2 = cube(Math.abs(elevGrad) + t);
            double spaghetti2d = clamp(Math.max(spagTerm1, spagTerm2), -1.0, 1.0);

            // ---- spaghettiTotal = spaghetti_2d + spaghetti_roughness ----
            double spagRough = (SR_OFFSET + SR_MOD_COEFF * srMod.getValue(x, y, z))
                    * (SR_RIDGE_OFFSET + Math.abs(sr.getValue(x, y, z)));
            double spaghetti = spaghetti2d + spagRough;

            // ---- cave curve: min(min(cheeseCave, entrances), spaghetti) ----
            double family = Math.min(Math.min(cheeseCave, entrancesVal), spaghetti);

            // ---- pillars ----
            double p = PILLAR_AMP * pillar.getValue(x * PILLAR_XZ, y * PILLAR_Y, z * PILLAR_XZ);
            double rare = pillarRare.getValue(x * PILLAR_RARE_XZ, y * PILLAR_RARE_Y, z * PILLAR_RARE_XZ);
            double radius = (p + (-1.0 - (-1.0) * rare))
                    * cube(PILLAR_FALL + PILLAR_FALL * pillarThick.getValue(x * PILLAR_THICK_XZ, y * PILLAR_THICK_Y, z * PILLAR_THICK_XZ));
            double pillarsRC = radius < PILLAR_TRIGGER ? PILLAR_CARVE : radius;

            // ---- when_out_of_range = max(caveCurve, pillarsRC) ----
            return Math.max(family, pillarsRC);
        };
    }

    /** {@code clamp(v, lo, hi)}. */
    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** {@code x*x*x}. */
    private static double cube(double x) {
        return x * x * x;
    }

    /**
     * {@code type_1} rarity: {@code getSpaghettiRarity3D} (javap-verified) maps the field
     * to {@code 0.75, 1, 1.5, 2}.
     */
    static double rarity1(double v) {
        if (v < -0.5) {
            return 0.75;
        }
        if (v < 0.0) {
            return 1.0;
        }
        if (v < 0.5) {
            return 1.5;
        }
        return 2.0;
    }

    /**
     * {@code type_2} rarity: {@code getSphaghettiRarity2D} (javap-verified) maps the field
     * to {@code 0.5, 0.75, 1, 2, 3}.
     */
    static double rarity2(double v) {
        if (v < -0.75) {
            return 0.5;
        }
        if (v < -0.5) {
            return 0.75;
        }
        if (v < 0.5) {
            return 1.0;
        }
        if (v < 0.75) {
            return 2.0;
        }
        return 3.0;
    }

    /** A {@link NormalNoise} leaf from a derived per-label seed (single/standard octave). */
    private static NormalNoise noise(long seed, String label, int firstOctave, double[] amps) {
        return NormalNoise.create(PositionalRand.deriveLong(seed, label), firstOctave, amps);
    }
}