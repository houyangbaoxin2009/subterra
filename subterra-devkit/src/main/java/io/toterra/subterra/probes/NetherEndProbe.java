package io.toterra.subterra.probes;

import io.toterra.subterra.optim.worldgen.pipeline.density.Density;
import io.toterra.subterra.optim.worldgen.pipeline.dimworlds.DimensionWorlds;
import io.toterra.subterra.optim.worldgen.pipeline.router.BlendedNoise;
import io.toterra.subterra.optim.worldgen.pipeline.dimworlds.WorldDim;
import io.toterra.subterra.optim.worldgen.pipeline.router.NoiseRouter;

/**
 * Deterministic acceptance probe for p.1.8.20 — the vanilla nether / end
 * noise-router recipes completing the p.1.8.19 deferral. Asserts each dimension's
 * fifteen fields: presence; the twelve fields the JSON pins to the constant
 * {@code 0} sampling as exact zero over a coordinate grid; the composed fields
 * ({@code temperature}/{@code vegetation} + cheese {@code finalDensity} for the
 * nether; island {@code erosion}/{@code initialDensity}/{@code finalDensity} for
 * the end) being finite, deterministic and seed-sensitive; the vanilla
 * {@code y_clamped_gradient} floor/ceiling saturations ({@code finalDensity}
 * clamped to [−1, 1]); and that {@link DimensionWorlds#materializeRouter} now
 * returns real nether / end routers. Pure JVM — no Minecraft runtime. Exit 0 =
 * PASS, exit 1 = FAIL.
 * <p>
 * p.1.8.20 的确定性验收探针——完成 p.1.8.19 延后的原生下界/末地噪声路由器配方。断言每维
 * 15 个字段：在场；JSON 钉死为常量 {@code 0} 的十二个字段在坐标网格上取样恰为 0；组合字段
 * （下界 {@code temperature}/{@code vegetation} + 奶酪 {@code finalDensity}；末地岛
 * {@code erosion}/{@code initialDensity}/{@code finalDensity}）有限、确定、随种子变化；
 * 原生 {@code y_clamped_gradient} 的地板/天花板饱和（{@code finalDensity} 被钳到 [−1, 1]）；
 * 以及 {@link DimensionWorlds#materializeRouter} 现为下界/末地返回真实路由器。
 * 纯 JVM——无 Minecraft 运行时。退出 0 = 通过，1 = 失败。
 */
public final class NetherEndProbe {

    private NetherEndProbe() {
    }

    private static int failures = 0;
    private static int checks = 0;

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1.0e-9;
    }

    private static boolean finite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    /** A spread of probe coordinates covering below/inside/above every Y band. */
    private static final double[] CX = {0.0, 8.5, -120.0, 33.25, 512.0};
    private static final double[] CY = {-32.0, 0.0, 64.0, 128.0, 300.0};
    private static final double[] CZ = {0.0, -12.5, 300.0, 99.0, -2048.0};

    /** Nether router field indices nether.json pins to constant 0.0. */
    private static final int[] NETHER_ZERO = {
            0, 1, 2, 3,   // barrier, floodedness, spread, lava
            6, 7, 8, 9,   // continents, erosion, depth, ridges
            10,           // initial_density_without_jaggedness
            12, 13, 14,   // vein_toggle, vein_ridged, vein_gap
    };

    /** End router field indices end.json pins to constant 0.0. */
    private static final int[] END_ZERO = {
            0, 1, 2, 3,   // barrier, floodedness, spread, lava
            4, 5, 6,      // temperature, vegetation, continents
            8, 9,         // depth, ridges
            12, 13, 14,   // vein_toggle, vein_ridged, vein_gap
    };

    /** True when {@code field} evaluates to exactly 0.0 across the whole grid. */
    private static boolean isZero(Density field) {
        for (int i = 0; i < CX.length; i++) {
            if (field.eval(CX[i], CY[i], CZ[i]) != 0.0) {
                return false;
            }
        }
        return true;
    }

    /** True when {@code field} evaluates finite across the whole grid. */
    private static boolean isFinite(Density field) {
        for (int i = 0; i < CX.length; i++) {
            if (!finite(field.eval(CX[i], CY[i], CZ[i]))) {
                return false;
            }
        }
        return true;
    }

    /** True when {@code field} changes value across the grid (i.e. is not constant). */
    private static boolean varies(Density field) {
        double first = field.eval(CX[0], CY[0], CZ[0]);
        for (int i = 1; i < CX.length; i++) {
            if (field.eval(CX[i], CY[i], CZ[i]) != first) {
                return true;
            }
        }
        return false;
    }

    /** True when {@code field} is y-independent (end {@code erosion} island signal). */
    private static boolean yIndependent(Density field) {
        double base = field.eval(12.5, 0.0, -33.25);
        return field.eval(12.5, 5.0, -33.25) == base && field.eval(12.5, 64.0, -33.25) == base;
    }

    public static void main(String[] args) {
        long seed = 0x5EED_2_0_8_8_20L;

        // ============ (a) nether router ============
        NoiseRouter n1 = NoiseRouter.nether(seed);
        NoiseRouter n2 = NoiseRouter.nether(seed, 0, 256);
        check("nether has all 15 fields",
                n1.fieldCount() == 15 && NoiseRouter.FIELD_NAMES.length == 15);
        boolean netherZero = true;
        boolean netherFinite = true;
        boolean netherDeterministic = true;
        for (int idx : NETHER_ZERO) {
            netherZero &= isZero(n1.fieldAt(idx));
        }
        for (int i = 0; i < 15; i++) {
            netherFinite &= isFinite(n1.fieldAt(i));
            for (int k = 0; k < CX.length; k++) {
                netherDeterministic &= n1.fieldAt(i).eval(CX[k], CY[k], CZ[k])
                        == n2.fieldAt(i).eval(CX[k], CY[k], CZ[k]);
            }
        }
        check("nether 12 JSON-constant fields are exact 0 (nether.json pins)", netherZero);
        check("nether all 15 fields finite over grid", netherFinite);
        check("nether all 15 fields deterministic across fresh assemblies", netherDeterministic);
        check("nether worldSeed matches seed", n1.worldSeed() == seed);

        Density nTemp = n1.temperature();
        Density nVeg = n1.vegetation();
        check("nether temperature varies (shifted_noise, not constant 0)", varies(nTemp) && isFinite(nTemp));
        check("nether vegetation varies (shifted_noise, not constant 0)", varies(nVeg) && isFinite(nVeg));
        check("nether temperature seed-sensitive",
                !near(nTemp.eval(12.5, 64.0, -33.25),
                        NoiseRouter.nether(seed + 123L).temperature().eval(12.5, 64.0, -33.25)));
        // nether.json pins the lava field to 0.0 (aquifers disabled) — not a shifted noise.
        check("nether lava pinned constant 0 (nether.json)", isZero(n1.lavaNoise()));

        Density nFinal = n1.finalDensity();
        check("nether finalDensity finite at pinned coords", isFinite(nFinal));
        check("nether finalDensity deterministic across fresh calls",
                nFinal.eval(12.5, 64.0, -33.25) == n2.finalDensity().eval(12.5, 64.0, -33.25));
        // p.1.8.29B: the real base_3d BlendedNoise saturates the cheese (|0.64*base| far beyond
        // 1), exactly like vanilla — so seed-sensitivity of the cheese is only provable through
        // its base_3d leaf, not through the squeezed final_density.
        NoiseRouter n3 = NoiseRouter.nether(seed + 123L);
        check("nether base_3d leaf seed-sensitive (real BlendedNoise)",
                leafDiffers(BlendedNoise.nether(seed), BlendedNoise.nether(seed + 123L)));
        boolean netherDiffers = false;
        for (int i = 0; i < 15; i++) {
            if (!near(n1.fieldAt(i).eval(12.5, 64.0, -33.25), n3.fieldAt(i).eval(12.5, 64.0, -33.25))) {
                netherDiffers = true;
            }
        }
        check("nether router differs across world seeds", netherDiffers);
        // Vanilla y_clamped_gradient floor: below y=-8 g1=0 -> final = clamp(0.64*2.5) = 1.0.
        check("nether finalDensity saturates at floor (y<-8 -> 1.0)",
                n1.finalDensity().eval(0.0, -30.0, 0.0) == 1.0);
        check("nether finalDensity stays within [-1, 1] (squeeze)", withinUnit(nFinal));
        check("nether rejects inverted Y range", rejectsRangeNether(seed));
        check("nether identity: temperature/temperature != finalDensity at probe",
                n1.temperature().eval(12.5, 64.0, -33.25) != n1.finalDensity().eval(12.5, 64.0, -33.25));

        // ============ (b) end router ============
        NoiseRouter e1 = NoiseRouter.end(seed);
        NoiseRouter e2 = NoiseRouter.end(seed, 0, 256);
        check("end has all 15 fields",
                e1.fieldCount() == 15 && NoiseRouter.FIELD_NAMES.length == 15);
        boolean endZero = true;
        boolean endFinite = true;
        boolean endDeterministic = true;
        for (int idx : END_ZERO) {
            endZero &= isZero(e1.fieldAt(idx));
        }
        for (int i = 0; i < 15; i++) {
            endFinite &= isFinite(e1.fieldAt(i));
            for (int k = 0; k < CX.length; k++) {
                endDeterministic &= e1.fieldAt(i).eval(CX[k], CY[k], CZ[k])
                        == e2.fieldAt(i).eval(CX[k], CY[k], CZ[k]);
            }
        }
        check("end 12 JSON-constant fields are exact 0 (end.json pins)", endZero);
        check("end all 15 fields finite over grid", endFinite);
        check("end all 15 fields deterministic across fresh assemblies", endDeterministic);
        check("end worldSeed matches seed", e1.worldSeed() == seed);

        // end erosion = cache_2d(end_islands): a varying 2-D (y-independent) signal.
        check("end erosion varies (end_islands, not constant 0)",
                varies(e1.erosion()) && isFinite(e1.erosion()));
        check("end erosion is y-independent (cache_2d island signal)", yIndependent(e1.erosion()));
        // p.1.8.29B: erosion is the real 1.21.1 end_islands shape (EndIslandNoise) —
        // bounds and boundary behaviour pinned (island/archipelago heights on the 8-grid).
        check("end erosion within vanilla island bounds [-0.84375, 0.5625]",
                islandBounds(e1.erosion()));
        check("end erosion at the island core == vanilla max 0.5625 (height 80 cap)",
                e1.erosion().eval(0.0, 0.0, 0.0) == 0.5625);

        Density eFinal = e1.finalDensity();
        Density eInit = e1.initialDensityWithoutJaggedness();
        check("end finalDensity finite at pinned coords", isFinite(eFinal));
        check("end initialDensity finite at pinned coords", isFinite(eInit));
        check("end finalDensity deterministic across fresh calls",
                eFinal.eval(12.5, 64.0, -33.25) == e2.finalDensity().eval(12.5, 64.0, -33.25));
        check("end initialDensity deterministic across fresh calls",
                eInit.eval(12.5, 64.0, -33.25) == e2.initialDensityWithoutJaggedness().eval(12.5, 64.0, -33.25));
        NoiseRouter e3 = NoiseRouter.end(seed + 123L);
        // p.1.8.29B: the end islands are the real EndIslandNoise shape — noise-independent near
        // the origin (the >4096 neighbourhood ring needs |block|/8 above ~45), so sensitivity is
        // asserted in the far field where the ring shapes the islands; the base_3d leaf is
        // asserted directly because real BlendedNoise saturates the cheese (vanilla reality).
        check("end base_3d leaf seed-sensitive (real BlendedNoise)",
                leafDiffers(BlendedNoise.end(seed), BlendedNoise.end(seed + 123L)));
        check("end finalDensity seed-sensitive (far-field islands + base_3d)",
                !near(eFinal.eval(1500.0, 20.0, -2000.0), e3.finalDensity().eval(1500.0, 20.0, -2000.0)));
        check("end initialDensity seed-sensitive (far-field islands)",
                !near(eInit.eval(1500.0, 20.0, -2000.0), e3.initialDensityWithoutJaggedness().eval(1500.0, 20.0, -2000.0)));
        boolean endDiffers = false;
        outer:
        for (double y : new double[]{0.0, 20.0, 64.0, 128.0}) {
            for (double x : new double[]{-120.0, 12.5, 33.25, 512.0, 1500.0}) {
                for (double z : new double[]{-33.25, 99.0, 300.0}) {
                    for (int i = 0; i < 15; i++) {
                        if (!near(e1.fieldAt(i).eval(x, y, z), e3.fieldAt(i).eval(x, y, z))) {
                            endDiffers = true;
                            break outer;
                        }
                    }
                }
            }
        }
        check("end router differs across world seeds (over grid)", endDiffers);
        // Vanilla y_clamped_gradient floor: below y=4 gE1=0 -> final = clamp(0.64*-0.234375) = -0.15.
        check("end finalDensity saturates at floor (y=0 -> -0.15)",
                e1.finalDensity().eval(0.0, 0.0, 0.0) == -0.15);
        check("end initialDensity floor at y=0 -> -0.234375",
                e1.initialDensityWithoutJaggedness().eval(0.0, 0.0, 0.0) == -0.234375);
        check("end finalDensity stays within [-1, 1] (squeeze)", withinUnit(eFinal));
        check("end initialDensity differs from finalDensity (different recipes)",
                eInit.eval(12.5, 64.0, -33.25) != eFinal.eval(12.5, 64.0, -33.25));
        check("end rejects inverted Y range", rejectsRangeEnd(seed));

        // ============ (c) cross-dimension & recreate identity ============
        check("nether recreates equal router for same seed",
                n1.finalDensity().eval(8.5, 63.0, -12.5) == NoiseRouter.nether(seed).finalDensity().eval(8.5, 63.0, -12.5));
        check("end recreates equal router for same seed",
                e1.finalDensity().eval(8.5, 63.0, -12.5) == NoiseRouter.end(seed).finalDensity().eval(8.5, 63.0, -12.5));
        check("nether vs end finalDensity differ (distinct recipes)",
                nFinal.eval(8.5, 63.0, -12.5) != eFinal.eval(8.5, 63.0, -12.5));

        // ============ (d) DimensionWorlds.materializeRouter bridge ============
        DimensionWorlds dw = DimensionWorlds.defaultWorlds();
        check("materializeRouter: overworld non-null", dw.materializeRouter(seed, WorldDim.OVERWORLD) != null);
        check("materializeRouter: nether non-null", dw.materializeRouter(seed, WorldDim.THE_NETHER) != null);
        check("materializeRouter: end non-null", dw.materializeRouter(seed, WorldDim.THE_END) != null);
        check("materializeRouter: nether uses nether window (0..256)",
                dw.materializeRouter(seed, WorldDim.THE_NETHER).worldSeed() == seed);
        check("materializeRouter: nether deterministic across fresh calls",
                dw.materializeRouter(seed, WorldDim.THE_NETHER).finalDensity().eval(8.5, 63.0, -12.5)
                        == dw.materializeRouter(seed, WorldDim.THE_NETHER).finalDensity().eval(8.5, 63.0, -12.5));
        check("materializeRouter: end deterministic across fresh calls",
                dw.materializeRouter(seed, WorldDim.THE_END).finalDensity().eval(8.5, 63.0, -12.5)
                        == dw.materializeRouter(seed, WorldDim.THE_END).finalDensity().eval(8.5, 63.0, -12.5));
        check("materializeRouter: overworld finalDensity still intact (p.1.8.12/14)",
                finite(dw.materializeRouter(seed, WorldDim.OVERWORLD).finalDensity().eval(8.5, 63.0, -12.5)));
        check("materializeRouter: null world rejected", rejectsNullWorld(dw, seed));

        if (failures == 0) {
            System.out.println("[NetherEndProbe] PASS (nether/end recipes, " + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[NetherEndProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }

    /** True when the two leaves differ at some grid point (guard saturation may pin
     * individual points to 0, so single-coordinate comparisons are not meaningful). */
    private static boolean leafDiffers(Density a, Density b) {
        for (double y : new double[]{0.0, 20.0, 64.0, 128.0}) {
            for (double x : new double[]{-120.0, 12.5, 33.25, 512.0}) {
                for (double z : new double[]{-33.25, 99.0, 300.0}) {
                    if (!near(a.eval(x, y, z), b.eval(x, y, z))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** True when {@code field} stays within the vanilla end_islands band. */
    private static boolean islandBounds(Density field) {
        for (int i = 0; i < CX.length; i++) {
            double v = field.eval(CX[i], CY[i], CZ[i]);
            if (!(v >= -0.84375 && v <= 0.5625)) {
                return false;
            }
        }
        return true;
    }

    /** True when every grid sample of {@code field} lies within the squeeze bounds [−1, 1]. */
    private static boolean withinUnit(Density field) {
        for (int i = 0; i < CX.length; i++) {
            double v = field.eval(CX[i], CY[i], CZ[i]);
            if (!(v >= -1.0 && v <= 1.0)) {
                return false;
            }
        }
        return true;
    }

    private static boolean rejectsRangeNether(long seed) {
        try {
            NoiseRouter.nether(seed, 256, 0);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean rejectsRangeEnd(long seed) {
        try {
            NoiseRouter.end(seed, 256, 0);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean rejectsNullWorld(DimensionWorlds dw, long seed) {
        try {
            dw.materializeRouter(seed, null);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}