package io.toterra.subterra.probes;

import io.toterra.subterra.api.worldgen.EcoDim;
import io.toterra.subterra.engine.worldgen.pipeline.density.Densities;
import io.toterra.subterra.engine.worldgen.pipeline.density.Density;
import io.toterra.subterra.engine.worldgen.pipeline.dimension.DimAlgo;
import io.toterra.subterra.engine.worldgen.pipeline.dimension.DimensionPlan;
import io.toterra.subterra.engine.worldgen.pipeline.dimension.DimensionSlot;
import io.toterra.subterra.engine.worldgen.pipeline.dimension.DimensionTerrain;
import io.toterra.subterra.engine.worldgen.pipeline.dimension.FieldRegion;
import io.toterra.subterra.engine.worldgen.pipeline.dimension.ModelBackend;
import io.toterra.subterra.engine.worldgen.pipeline.dimension.ModelFieldGenerator;
import io.toterra.subterra.engine.worldgen.pipeline.dimension.ModelPointDensity;
import io.toterra.subterra.engine.worldgen.pipeline.dimension.OverworldBounds;
import io.toterra.subterra.engine.worldgen.pipeline.dimension.WaterClass;

/**
 * Deterministic acceptance probe for the p.1.8.11 nine-dimension generator
 * (Step 7): asserts the prevalence of the five scalar vanilla-mirror slots, the
 * per-dimension {@code with()} independence, the hydro dual-slot accessors
 * (constant water level 63 + vanilla water-class fallback), the
 * {@link OverworldBounds} presets/td round-trip and {@link DimensionPlan}
 * materialisation, the interface-only model seams, and cross-instance
 * determinism of {@link DimensionTerrain#fromPlan}. Pure JVM; no randomness,
 * no timing asserts. Deterministic; exit 0 = PASS, exit 1 = FAIL.
 */
public final class DimensionProbe {

    private DimensionProbe() {
    }

    private static int failures = 0;

    private static void check(String name, boolean ok) {
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

    private static boolean rejects(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static boolean finite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    public static void main(String[] args) {
        // ---- 五个标量维默认存在 + 原版镜像确定性 -------------------------
        DimensionTerrain def = new DimensionTerrain(12345L);
        boolean allPresent = true;
        for (EcoDim dim : EcoDim.ALL) {
            if (def.slot(dim) == null) {
                allPresent = false;
            }
        }
        check("all nine slots present", allPresent && def.slots().size() == EcoDim.ALL.size());

        Density terrain = def.slot(EcoDim.of("terrain")).density();
        double pin = terrain.eval(3.5, 2.0, -4.5);
        check("terrain slot same-instance reproducible", pin == terrain.eval(3.5, 2.0, -4.5));
        check("terrain slot cross-instance reproducible",
                near(pin, new DimensionTerrain(12345L).slot(EcoDim.of("terrain")).density().eval(3.5, 2.0, -4.5)));
        check("terrain value finite/non-NaN", finite(pin));

        boolean nonDegenerate = false;
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (int x = -20; x <= 20; x += 4) {
            for (int z = -20; z <= 20; z += 4) {
                double v = terrain.eval(x, 0.0, z);
                lo = Math.min(lo, v);
                hi = Math.max(hi, v);
            }
        }
        check("terrain field non-degenerate (varies)", finite(lo) && finite(hi) && hi - lo > 1.0e-6);

        // ---- per-dimension with() swap 独立性 -----------------------------
        DimensionTerrain swapped = def.with(EcoDim.of("terrain"), Densities.constant(42.0), DimAlgo.CONSTANT);
        check("with swap terrain -> terrain exactly 42",
                near(swapped.slot(EcoDim.of("terrain")).density().eval(3.5, 2.0, -4.5), 42.0)
                        && near(swapped.slot(EcoDim.of("terrain")).density().eval(-7.5, 6.5, 0.5), 42.0));

        double climateDef = def.slot(EcoDim.of("climate")).density().eval(3.5, 2.0, -4.5);
        check("with swap terrain leaves climate unchanged",
                near(swapped.slot(EcoDim.of("climate")).density().eval(3.5, 2.0, -4.5), climateDef));

        check("with returns new instance; receiver untouched",
                near(def.slot(EcoDim.of("terrain")).density().eval(3.5, 2.0, -4.5), pin));

        check("with swap leaves other scalar dim intact",
                near(swapped.slot(EcoDim.of("litho")).density().eval(1.5, 1.5, 1.5),
                        def.slot(EcoDim.of("litho")).density().eval(1.5, 1.5, 1.5)));

        // ---- 水位常量 63 + 原版水体类型回推 --------------------------------
        boolean water63 = true;
        for (int i = 0; i < 5; i++) {
            double x = i * 13.0 - 30.0;
            double z = i * 7.0 + 3.0;
            if (def.waterLevel().level(x, z) != 63.0) {
                water63 = false;
            }
        }
        check("water level constant 63", water63 && def.waterLevel().level(0, 0) == 63.0);

        boolean noReserved = true;
        boolean iffBelow = true;
        for (double x = -40.0; x <= 40.0; x += 8.0) {
            for (double z = -40.0; z <= 40.0; z += 8.0) {
                WaterClass wc = def.waterClass().sample(x, z);
                if (wc.isReserved()) {
                    noReserved = false;
                }
                boolean ocean = def.slot(EcoDim.of("terrain")).density().eval(x, 0.0, z) < 63.0;
                if ((wc == WaterClass.OCEAN) != ocean) {
                    iffBelow = false;
                }
            }
        }
        check("vanilla water class never reserved", noReserved);
        check("vanilla water class OCEAN iff below sea level", iffBelow);

        // ---- OverworldBounds：预设 / 校验 / td 往返 -------------------------
        OverworldBounds vanilla = OverworldBounds.vanilla();
        check("bounds vanilla height 384", vanilla.height() == 384);
        check("bounds vanilla minY/seaLevel/buildLimit",
                vanilla.minY() == -64 && vanilla.seaLevel() == 63 && vanilla.buildLimit() == 320);
        OverworldBounds tall = OverworldBounds.tall();
        check("bounds tall height 512", tall.height() == 512);
        check("bounds tall minY/seaLevel/buildLimit",
                tall.minY() == -64 && tall.seaLevel() == 63 && tall.buildLimit() == 448);

        check("bounds rejects height <= 0",
                rejects(() -> new OverworldBounds(-64, 0, 63, 320)));
        check("bounds rejects seaLevel >= height",
                rejects(() -> new OverworldBounds(-64, 64, 64, 63)));
        check("bounds rejects buildLimit overflow",
                rejects(() -> new OverworldBounds(-64, 384, 63, 321)));

        check("bounds td round-trip vanilla",
                OverworldBounds.fromTd(vanilla.td()).equals(vanilla));
        check("bounds td round-trip tall",
                OverworldBounds.fromTd(tall.td()).equals(tall));
        check("bounds td rejects unknown key",
                rejects(() -> OverworldBounds.fromTd(
                        "[ minY=-64, height=384, seaLevel=63, buildLimit=320, foo=1 ]")));

        // ---- DimensionPlan：默认全 VANILLA / td 往返 / materialize --------
        DimensionPlan defPlan = DimensionPlan.defaultPlan();
        boolean allVanilla = true;
        for (EcoDim dim : EcoDim.ALL) {
            if (defPlan.algo(dim) != DimAlgo.VANILLA) {
                allVanilla = false;
            }
        }
        check("defaultPlan all VANILLA", allVanilla && defPlan.entries().isEmpty());

        DimensionPlan formulaPlan = DimensionPlan.fromTd(
                "[ terrain=[formula, \"x\", scale=1, height=1] ]");
        check("formula plan td round-trip",
                DimensionPlan.fromTd(formulaPlan.td()).equals(formulaPlan));

        DimensionTerrain mat = formulaPlan.materialize(123L);
        boolean formulaEval = true;
        for (int i = 0; i < 4; i++) {
            double x = i - 1.5;
            double y = i * 2.0;
            double z = i + 0.5;
            double v = mat.slot(EcoDim.of("terrain")).density().eval(x, y, z);
            if (!near(v, x)) {
                formulaEval = false;
            }
        }
        check("formula materialize eval == x", formulaEval);

        DimensionTerrain constMat = DimensionPlan.fromTd(
                "[ litho=[constant, 1234.5] ]").materialize(7L);
        check("constant entry yields constant",
                near(constMat.slot(EcoDim.of("litho")).density().eval(-9.5, 1.5, 44.0), 1234.5)
                        && near(constMat.slot(EcoDim.of("litho")).density().eval(2.0, 2.0, 2.0), 1234.5));

        check("plan rejects unknown dimension",
                rejects(() -> DimensionPlan.fromTd("[ bogus=[constant, 5] ]")));
        check("plan rejects unknown algo",
                rejects(() -> DimensionPlan.fromTd("[ terrain=[foo, 5] ]")));
        check("plan rejects blank formula",
                rejects(() -> DimensionPlan.fromTd("[ terrain=[formula, \"\"] ]")));
        check("plan rejects constant with two params",
                rejects(() -> DimensionPlan.fromTd("[ terrain=[constant, 1, 2] ]")));

        // ---- 模型接缝（仅接口） ------------------------------------------
        check("fieldRegion rejects mismatched data length",
                rejects(() -> new FieldRegion(0, 0, 0, 2, 2, 2, new double[7])));

        FieldRegion smpRegion = new FieldRegion(0, 0, 0, 2, 2, 2,
                new double[]{0, 1, 2, 3, 4, 5, 6, 7});
        check("fieldRegion sample out-of-box throws",
                rejects(() -> smpRegion.sample(2.0, 0.0, 0.0)));

        ModelFieldGenerator gen = region -> {
            for (int i = 0; i < region.data().length; i++) {
                region.data()[i] = i;
            }
        };
        ModelFieldGenerator.RegionCoverer coverer =
                bounds -> new FieldRegion(0, 0, 0, 2, 2, 2, new double[8]);
        Density fieldDensity = ModelFieldGenerator.toDensity(gen, coverer, OverworldBounds.vanilla());
        check("toDensity returns region value in-bounds",
                fieldDensity.eval(0, 0, 0) == 0.0 && fieldDensity.eval(1, 1, 1) == 7.0);
        check("toDensity deterministic same coordinate",
                fieldDensity.eval(1, 1, 1) == fieldDensity.eval(1, 1, 1));
        check("toDensity cross-instance deterministic",
                near(fieldDensity.eval(1, 0, 1),
                        ModelFieldGenerator.toDensity(gen, coverer, OverworldBounds.vanilla()).eval(1, 0, 1)));
        check("toDensity throws outside buffered region",
                rejects(() -> fieldDensity.eval(2.0, 0.0, 0.0)));

        ModelPointDensity model = (x, y, z) -> x + y + z;
        Density pointDensity = model.toDensity();
        check("pointDensity toDensity forwards prediction",
                pointDensity.eval(1, 2, 3) == 6.0 && pointDensity.eval(-1.5, 0.5, 2.0) == 1.0);

        check("modelBackend available()==false", !ModelBackend.available());

        // ---- 确定性：fromPlan 双次求值 -------------------------------------
        DimensionPlan detPlan = DimensionPlan.fromTd(
                "[ terrain=[formula, \"x*z + y\", scale=1, height=1] ]");
        DimensionTerrain t1 = DimensionTerrain.fromPlan(detPlan, 123L);
        DimensionTerrain t2 = DimensionTerrain.fromPlan(detPlan, 123L);
        boolean det = true;
        for (int i = 0; i < 4; i++) {
            double x = i - 1.0;
            double y = i * 3.0;
            double z = i + 2.0;
            double v = t1.slot(EcoDim.of("terrain")).density().eval(x, y, z);
            if (!near(v, x * z + y) || v != t2.slot(EcoDim.of("terrain")).density().eval(x, y, z)) {
                det = false;
            }
        }
        check("fromPlan deterministic + eval matches formula", det);

        // ---- 种子敏感：不同 seed 的默认 terrain 镜像产生不同场 --------------
        DimensionTerrain s1 = DimensionTerrain.fromPlan(DimensionPlan.defaultPlan(), 1L);
        DimensionTerrain s2 = DimensionTerrain.fromPlan(DimensionPlan.defaultPlan(), 2L);
        boolean anyDiff = false;
        boolean allFinite = true;
        for (int i = 0; i < 4; i++) {
            double x = i * 3.7;
            double y = i * 1.3;
            double z = i * 5.1;
            double v1 = s1.slot(EcoDim.of("terrain")).density().eval(x, y, z);
            double v2 = s2.slot(EcoDim.of("terrain")).density().eval(x, y, z);
            allFinite &= Double.isFinite(v1) && Double.isFinite(v2);
            anyDiff |= !near(v1, v2);
        }
        check("default vanilla mirrors seed-sensitive across seeds", anyDiff && allFinite);

        if (failures == 0) {
            System.out.println("[DimensionProbe] PASS (nine-dimension generator, " + 40 + " checks)");
            System.exit(0);
        } else {
            System.out.println("[DimensionProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }
}