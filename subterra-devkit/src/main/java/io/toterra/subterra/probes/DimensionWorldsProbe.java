package io.toterra.subterra.probes;

import io.toterra.subterra.api.worldgen.EcoDim;
import io.toterra.subterra.engine.worldgen.pipeline.dimension.DimensionPlan;
import io.toterra.subterra.engine.worldgen.pipeline.dimension.DimAlgo;
import io.toterra.subterra.engine.worldgen.pipeline.dimension.OverworldBounds;
import io.toterra.subterra.engine.worldgen.pipeline.dimworlds.DimensionSection;
import io.toterra.subterra.engine.worldgen.pipeline.dimworlds.DimensionWorlds;
import io.toterra.subterra.engine.worldgen.pipeline.dimworlds.WorldDim;
import io.toterra.subterra.engine.worldgen.pipeline.density.Density;
import io.toterra.subterra.engine.worldgen.pipeline.router.NoiseRouter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic acceptance probe for p.1.8.19 — the td dimension-wiring core:
 * the three primary worlds' ids and coordinate scales, the pinned vanilla
 * 1.21.1 dimension-type flags and windows, the {@code td()} /
 * {@code fromTd(String)} round-trip and overrides, parser rejections,
 * {@code materializeRouter(seed, WorldDim)} (overworld plus the p.1.8.20 nether /
 * end routers), and {@code validate()} health-checking. Pure JVM — no Minecraft
 * runtime. Exit 0 = PASS, exit 1 = FAIL.
 * <p>
 * p.1.8.19 td 维度接线核心的确定性验收探针：三个主世界的 id 与坐标尺度、钉定的原生
 * 1.21.1 维度型标志与窗口、{@code td()} / {@code fromTd(String)} 往返与覆盖、解析拒绝、
 * {@code materializeRouter(seed, WorldDim)}（主世界 + p.1.8.20 下界/末地路由器）与
 * {@code validate()} 健康检查。纯 JVM——无 Minecraft 运行时。退出 0 = 通过，1 = 失败。
 */
public final class DimensionWorldsProbe {

    private DimensionWorldsProbe() {
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

    private static boolean rejects(String td) {
        try {
            DimensionWorlds.fromTd(td);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    public static void main(String[] args) {
        long seed = 0x5EED_1_2_3_4_5_6_7_8L;

        // ============ (a) WorldDim ids / coordinate-scale pins ============
        check("three primary worlds registered", WorldDim.ALL.size() == 3);
        check("overworld id", WorldDim.OVERWORLD.id().equals("minecraft:overworld"));
        check("nether id", WorldDim.THE_NETHER.id().equals("minecraft:the_nether"));
        check("end id", WorldDim.THE_END.id().equals("minecraft:the_end"));
        check("world lookup known",
                WorldDim.of("minecraft:overworld") == WorldDim.OVERWORLD
                        && WorldDim.of("minecraft:the_nether") == WorldDim.THE_NETHER
                        && WorldDim.of("minecraft:the_end") == WorldDim.THE_END);
        check("world lookup unknown", WorldDim.of("minecraft:bogus") == null);
        check("overworld coordinate scale 1.0", WorldDim.OVERWORLD.coordinateScale() == 1.0);
        check("nether coordinate scale 8.0", WorldDim.THE_NETHER.coordinateScale() == 8.0);
        check("end coordinate scale 1.0", WorldDim.THE_END.coordinateScale() == 1.0);
        check("every world has strictly positive scale",
                WorldDim.ALL.stream().allMatch(w -> w.coordinateScale() > 0.0));

        // ============ (b) default vanilla triple ============
        DimensionWorlds d = DimensionWorlds.defaultWorlds();
        check("sections cover all three worlds",
                d.sections().size() == 3 && d.sections().keySet().containsAll(WorldDim.ALL));
        OverworldBounds ow = d.boundsFor(WorldDim.OVERWORLD);
        OverworldBounds nether = d.boundsFor(WorldDim.THE_NETHER);
        OverworldBounds end = d.boundsFor(WorldDim.THE_END);
        check("overworld window -64/384/63/320",
                ow.minY() == -64 && ow.height() == 384 && ow.seaLevel() == 63 && ow.buildLimit() == 320);
        check("overworld window equals vanilla bounds", ow.equals(OverworldBounds.vanilla()));
        check("nether window 0/256/32/256",
                nether.minY() == 0 && nether.height() == 256 && nether.seaLevel() == 32 && nether.buildLimit() == 256);
        check("end window 0/256/0/256",
                end.minY() == 0 && end.height() == 256 && end.seaLevel() == 0 && end.buildLimit() == 256);
        check("overworld scale canonical", d.section(WorldDim.OVERWORLD).coordinateScale() == 1.0);
        check("nether scale canonical", d.section(WorldDim.THE_NETHER).coordinateScale() == 8.0);
        check("overworld flags pinned",
                d.section(WorldDim.OVERWORLD).flags().natural()
                        && !d.section(WorldDim.OVERWORLD).flags().ultrawarm()
                        && d.section(WorldDim.OVERWORLD).flags().hasSkylight()
                        && !d.section(WorldDim.OVERWORLD).flags().hasCeiling()
                        && d.section(WorldDim.OVERWORLD).flags().bedWorks()
                        && !d.section(WorldDim.OVERWORLD).flags().respawnAnchorWorks()
                        && !d.section(WorldDim.OVERWORLD).flags().piglinSafe());
        check("nether flags pinned",
                d.section(WorldDim.THE_NETHER).flags().ultrawarm()
                        && d.section(WorldDim.THE_NETHER).flags().hasCeiling()
                        && !d.section(WorldDim.THE_NETHER).flags().hasSkylight()
                        && !d.section(WorldDim.THE_NETHER).flags().natural()
                        && !d.section(WorldDim.THE_NETHER).flags().bedWorks()
                        && d.section(WorldDim.THE_NETHER).flags().respawnAnchorWorks()
                        && d.section(WorldDim.THE_NETHER).flags().piglinSafe());
        check("end flags pinned",
                !d.section(WorldDim.THE_END).flags().natural()
                        && !d.section(WorldDim.THE_END).flags().ultrawarm()
                        && !d.section(WorldDim.THE_END).flags().hasSkylight()
                        && !d.section(WorldDim.THE_END).flags().hasCeiling()
                        && !d.section(WorldDim.THE_END).flags().bedWorks()
                        && !d.section(WorldDim.THE_END).flags().respawnAnchorWorks());

        // ============ (c) td() / fromTd(String) round-trip + overrides ============
        check("default td round-trips to equal instance", DimensionWorlds.fromTd(d.td()).equals(d));
        check("default td round-trip hashCode matches", DimensionWorlds.fromTd(d.td()).hashCode() == d.hashCode());
        check("overworld9n returns overworld section", d.overworld9n() == d.section(WorldDim.OVERWORLD));
        check("vanilla() section equals td-parsed section",
                DimensionSection.vanilla(WorldDim.THE_NETHER)
                        .equals(DimensionSection.fromTd(WorldDim.THE_NETHER, "")));
        DimensionWorlds h = DimensionWorlds.fromTd("[minecraft:overworld=[height=600]]");
        check("override height keeps other fields vanilla",
                h.boundsFor(WorldDim.OVERWORLD).height() == 600
                        && h.boundsFor(WorldDim.OVERWORLD).minY() == -64
                        && h.boundsFor(WorldDim.OVERWORLD).seaLevel() == 63);
        DimensionWorlds flag = DimensionWorlds.fromTd("[minecraft:the_end=[hasRaids=false, piglinSafe=true]]");
        check("override flags reflect",
                !flag.section(WorldDim.THE_END).flags().hasRaids()
                        && flag.section(WorldDim.THE_END).flags().piglinSafe()
                        && !flag.section(WorldDim.THE_END).flags().natural());
        DimensionWorlds custom = DimensionWorlds.fromTd(
                "[minecraft:overworld=[coordScale=2.0, plan=[terrain=[constant, 5]]]]");
        check("custom plan parsed",
                custom.plan(WorldDim.OVERWORLD).algo(EcoDim.of("terrain")) == DimAlgo.CONSTANT);
        check("custom scale applied", custom.section(WorldDim.OVERWORLD).coordinateScale() == 2.0);

        // ============ (d) parser rejections ============
        check("reject unknown world id", rejects("[bogus=[minY=-64]]"));
        check("reject unknown td key", rejects("[minecraft:overworld=[foo=1]]"));
        check("reject zero coordinate scale", rejects("[minecraft:overworld=[coordScale=0]]"));
        check("reject negative coordinate scale", rejects("[minecraft:overworld=[coordScale=-4.0]]"));
        check("reject invalid window (height 0)", rejects("[minecraft:overworld=[height=0]]"));
        check("reject sea level outside window",
                rejects("[minecraft:overworld=[minY=0, height=64, seaLevel=200, buildLimit=64]]"));
        check("reject malformed body (no '=')", rejects("[minecraft:overworld=[notapair]]"));
        check("reject null td", rejects(null));

        // ============ (e) materializeRouter(seed, WorldDim) ============
        NoiseRouter r1 = d.materializeRouter(seed, WorldDim.OVERWORLD);
        NoiseRouter r2 = d.materializeRouter(seed, WorldDim.OVERWORLD);
        check("overworld router built", r1 != null && r1.fieldCount() == 15);
        check("overworld router deterministic across fresh calls",
                r1.finalDensity().eval(8.5, 63.0, -12.5) == r2.finalDensity().eval(8.5, 63.0, -12.5));
        double f1 = r1.finalDensity().eval(-120.0, 40.0, 300.0);
        double f2 = r1.finalDensity().eval(0.0, -60.0, 0.0);
        check("overworld finalDensity finite at pinned coords",
                finite(r1.finalDensity().eval(8.5, 63.0, -12.5)) && finite(f1) && finite(f2));
        NoiseRouter r3 = d.materializeRouter(seed + 123L, WorldDim.OVERWORLD);
        check("overworld finalDensity seed-sensitive",
                !near(r3.finalDensity().eval(8.5, 63.0, -12.5),
                        r1.finalDensity().eval(8.5, 63.0, -12.5)));
        check("router worldSeed matches seed", r1.worldSeed() == seed);
        // p.1.8.20: nether/end now materialise real routers (previously deferred to null).
        NoiseRouter nr = d.materializeRouter(seed, WorldDim.THE_NETHER);
        NoiseRouter er = d.materializeRouter(seed, WorldDim.THE_END);
        check("nether router built (non-null, 15 fields)", nr != null && nr.fieldCount() == 15);
        check("end router built (non-null, 15 fields)", er != null && er.fieldCount() == 15);
        check("nether router deterministic across fresh calls",
                nr.finalDensity().eval(8.5, 63.0, -12.5) == d.materializeRouter(seed, WorldDim.THE_NETHER)
                        .finalDensity().eval(8.5, 63.0, -12.5));
        check("end router deterministic across fresh calls",
                er.finalDensity().eval(8.5, 63.0, -12.5) == d.materializeRouter(seed, WorldDim.THE_END)
                        .finalDensity().eval(8.5, 63.0, -12.5));
        check("nether finalDensity finite", finite(nr.finalDensity().eval(8.5, 63.0, -12.5)));
        check("end finalDensity finite", finite(er.finalDensity().eval(8.5, 63.0, -12.5)));
        boolean allFieldsDeterministic = true;
        boolean allFieldsFinite = true;
        for (int i = 0; i < 15; i++) {
            Density a = r1.fieldAt(i);
            Density b = r2.fieldAt(i);
            allFieldsDeterministic &= a.eval(8.5, 63.0, -12.5) == b.eval(8.5, 63.0, -12.5);
            allFieldsFinite &= finite(a.eval(8.5, 63.0, -12.5)) && finite(a.eval(0.0, 0.0, 0.0));
        }
        check("all 15 router fields deterministic across fresh calls", allFieldsDeterministic);
        check("all 15 router fields finite", allFieldsFinite);

        // ============ (f) validate() health-check ============
        check("default table validates clean", d.validate().isEmpty());
        DimensionWorlds tampered = tamperedScaleZero();
        List<String> issues = tampered.validate();
        check("validate flags zero coordinate scale",
                !issues.isEmpty() && issues.stream().anyMatch(i -> i.contains("overworld") && i.contains("scale")));
        DimensionWorlds rt = DimensionWorlds.fromTd(d.td());
        check("clean table validates clean too", rt.validate().isEmpty());

        if (failures == 0) {
            System.out.println("[DimensionWorldsProbe] PASS (dimension wiring core, " + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[DimensionWorldsProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }

    /** A table whose overworld section carries a zero coordinate scale (tampered). */
    private static DimensionWorlds tamperedScaleZero() {
        Map<WorldDim, DimensionSection> s = new LinkedHashMap<>();
        Map<WorldDim, DimensionPlan> p = new LinkedHashMap<>();
        for (WorldDim w : WorldDim.ALL) {
            if (w == WorldDim.OVERWORLD) {
                s.put(w, new DimensionSection(w, 0.0, OverworldBounds.vanilla(), w.flags()));
            } else {
                s.put(w, DimensionSection.vanilla(w));
            }
            p.put(w, DimensionPlan.defaultPlan());
        }
        return new DimensionWorlds(s, p);
    }
}