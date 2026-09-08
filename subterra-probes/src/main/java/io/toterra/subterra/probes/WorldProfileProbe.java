package io.toterra.subterra.probes;

import java.util.EnumSet;
import java.util.List;
import java.util.TreeMap;

import io.toterra.subterra.api.worldgen.profiler.BandBlocks;
import io.toterra.subterra.api.worldgen.profiler.BiomeEntry;
import io.toterra.subterra.api.worldgen.profiler.BiomeStats;
import io.toterra.subterra.api.worldgen.profiler.BlockEntry;
import io.toterra.subterra.api.worldgen.profiler.BlockStats;
import io.toterra.subterra.api.worldgen.profiler.CaveBand;
import io.toterra.subterra.api.worldgen.profiler.CaveStats;
import io.toterra.subterra.api.worldgen.profiler.ClimateHistogram;
import io.toterra.subterra.api.worldgen.profiler.HeightStats;
import io.toterra.subterra.api.worldgen.profiler.PositionRecord;
import io.toterra.subterra.api.worldgen.profiler.ProfileAxis;
import io.toterra.subterra.api.worldgen.profiler.ProfileCategory;
import io.toterra.subterra.api.worldgen.profiler.ProfileFormat;
import io.toterra.subterra.api.worldgen.profiler.ProfilePlan;
import io.toterra.subterra.api.worldgen.profiler.ProfileReport;
import io.toterra.subterra.api.worldgen.profiler.ProfileSink;
import io.toterra.subterra.api.worldgen.profiler.ProfileSlice;
import io.toterra.subterra.api.worldgen.profiler.ProfileStep;
import io.toterra.subterra.api.worldgen.profiler.ProfileWindow;
import io.toterra.subterra.api.worldgen.profiler.SliceCensus;
import io.toterra.subterra.api.worldgen.profiler.SlicePlane;
import io.toterra.subterra.api.worldgen.profiler.SliceReport;
import io.toterra.subterra.api.worldgen.profiler.SliceUnit;
import io.toterra.subterra.api.worldgen.profiler.WorldSampler;
import io.toterra.subterra.config.Td;
import io.toterra.subterra.config.TdTable;
import io.toterra.subterra.profiler.core.ProfileRegion;
import io.toterra.subterra.profiler.core.ProfileRegion.Box;
import io.toterra.subterra.profiler.core.ResidentCollector;
import io.toterra.subterra.profiler.core.StatsEngine;
import io.toterra.subterra.profiler.facade.ApiRegistry;
import io.toterra.subterra.profiler.facade.ProfilePlanTd;
import io.toterra.subterra.profiler.report.TdWriter;
import io.toterra.subterra.profiler.report.ZdWriter;

/**
 * Deterministic acceptance probe for the p.1.8.30 "World Profiler" pure-JDK
 * engine (subterra-profiler). Drives StatsEngine / SliceCollector /
 * ResidentCollector / ProfilePlanTd / ApiRegistry against a deterministic fake
 * WorldSampler and asserts the engine contract invariants. During run it also
 * validates the td and zd serialization (header byte layout) and an
 * ApiRegistry register/run/enable/remove lifecycle. Exit 0 = PASS, 1 = FAIL
 * (never shipped in the mod jar).
 *
 * p.1.8.30 "World Profiler" 纯 JDK 引擎（subterra-profiler）的确定性验收探针。
 * 用确定性假采样器驱动 StatsEngine / SliceCollector / ResidentCollector /
 * ProfilePlanTd / ApiRegistry，并断言引擎契约不变式；同时校验 td 与 zd 序列化
 * （头部字节布局）以及 ApiRegistry 注册/运行/开关/移除生命周期。退出码 0 = PASS，
 * 1 = FAIL（永不随 mod jar 发布）。
 */
public final class WorldProfileProbe {

    private WorldProfileProbe() {
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

    /** Deterministic fake world sampler: no randomness, every call repeatable. */
    static final class FakeWorld implements WorldSampler {
        @Override
        public int surfaceY(int x, int z) {
            return 63 + Math.floorMod(x * 17 + z * 31, 31);
        }

        @Override
        public String block(int x, int y, int z) {
            int surf = surfaceY(x, z);
            boolean cave = Math.floorMod(x / 2 + z / 2, 3) == 0;
            if (cave && y >= 30 && y < 35) {
                return "minecraft:air";
            }
            boolean water = Math.floorMod(x, 5) == 0 && Math.floorMod(z, 7) == 0;
            if (water && y >= 20 && y < 24) {
                return "minecraft:water";
            }
            if (y >= surf) {
                return "minecraft:air";
            }
            if (y == surf) {
                return "minecraft:grass_block";
            }
            if (y >= surf - 6) {
                return "minecraft:dirt";
            }
            return "minecraft:stone";
        }

        @Override
        public String biome(int x, int y, int z) {
            return ((Math.floorMod(x + z + 1024, 32) / 16) & 1) == 0
                    ? "minecraft:plains" : "minecraft:ocean";
        }

        @Override
        public double routerField(String fieldName, int x, int y, int z) {
            return fieldName.hashCode() % 7 + x * 0.001 + z * 0.002 + y * 0.003;
        }

        @Override
        public int seaLevel() {
            return 63;
        }

        @Override
        public int minY() {
            return -64;
        }

        @Override
        public int maxY() {
            return 320;
        }
    }

    private static ProfilePlan buildPlan(ProfileSink sink, boolean withSlice, boolean residency) {
        return new ProfilePlan(
                residency,
                new ProfileWindow(0, 0, 1),
                EnumSet.allOf(ProfileCategory.class),
                new ProfileStep(4, 1),
                withSlice ? new ProfileSlice(ProfileAxis.X, -8, 8, SliceUnit.BLOCK, true, true) : null,
                List.of(ProfileFormat.ZD),
                sink);
    }

    /**
     * Array-aware report equivalence, kept as a component-level fallback. The
     * HeightStats/CaveStats/ClimateHistogram records now implement deep
     * {@code equals}/{@code hashCode} (array components compared with
     * {@code Arrays.equals}), so {@link ProfileReport#equals} is content-based; this
     * helper is retained for finer-grained diagnostics.
     */
    private static boolean reportsDeepEqual(ProfileReport a, ProfileReport b) {
        if (a.seed() != b.seed() || !a.dimension().equals(b.dimension())
                || !a.appVersion().equals(b.appVersion())
                || a.columnSamples() != b.columnSamples()
                || !a.plan().equals(b.plan())) {
            return false;
        }
        if (!heightStatsEqual(a.terrain(), b.terrain())) {
            return false;
        }
        if (!a.blocks().equals(b.blocks())) {
            return false;
        }
        if (!caveStatsEqual(a.caves(), b.caves())) {
            return false;
        }
        if (!biomeStatsEqual(a.biomes(), b.biomes())) {
            return false;
        }
        if (a.slice() == null && b.slice() == null) {
            return true;
        }
        return a.slice() != null && b.slice() != null && a.slice().equals(b.slice());
    }

    private static boolean heightStatsEqual(HeightStats x, HeightStats y) {
        return x.min() == y.min() && x.max() == y.max() && x.mean() == y.mean()
                && x.p5() == y.p5() && x.p25() == y.p25() && x.p50() == y.p50()
                && x.p75() == y.p75() && x.p95() == y.p95()
                && x.bandHeight() == y.bandHeight()
                && x.landColumns() == y.landColumns() && x.oceanColumns() == y.oceanColumns()
                && java.util.Arrays.equals(x.bandCounts(), y.bandCounts());
    }

    private static boolean caveStatsEqual(CaveStats x, CaveStats y) {
        return x.surfaceOpenings() == y.surfaceOpenings()
                && java.util.Arrays.equals(x.crossingsHistogram(), y.crossingsHistogram())
                && x.bands().equals(y.bands());
    }

        private static boolean biomeStatsEqual(BiomeStats x, BiomeStats y) {
        if (!x.census().equals(y.census())) {
            return false;
        }
        if (x.climate().size() != y.climate().size()) {
            return false;
        }
        for (int i = 0; i < x.climate().size(); i++) {
            ClimateHistogram hx = x.climate().get(i);
            ClimateHistogram hy = y.climate().get(i);
            if (!hx.field().equals(hy.field()) || hx.min() != hy.min() || hx.max() != hy.max()
                    || hx.bucketCount() != hy.bucketCount()
                    || !java.util.Arrays.equals(hx.buckets(), hy.buckets())) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        FakeWorld fake = new FakeWorld();
        ProfilePlan mainPlan = buildPlan(ProfileSink.NONE, true, false);
        boolean nonEmptySection;

        // ---- 1 determinism --------------------------------------------------
        ProfileReport r1 = StatsEngine.scan(fake, mainPlan, 42L, "minecraft:overworld", "probe");
        ProfileReport r2 = StatsEngine.scan(fake, mainPlan, 42L, "minecraft:overworld", "probe");
        check("deterministic: two scans of same plan+sampler are deeply equal",
                r1.equals(r2));
        nonEmptySection = r1.terrain().bandCounts().length > 0
                && r1.blocks().total() > 0
                && r1.biomes().census().size() == 2
                && !r1.caves().bands().isEmpty();
        check("non-empty sections (all four categories open)", nonEmptySection);

        // ---- 2 structure ----------------------------------------------------
        Box w = ProfileRegion.windowBox(mainPlan.window());
        int stepXz = mainPlan.step().xz();
        int colsX = (w.xTo() - w.xFrom()) / stepXz;
        int colsZ = (w.zTo() - w.zFrom()) / stepXz;
        check("column samples == (window/step)^2 == 144",
                r1.columnSamples() == 144L && colsX == 12 && colsZ == 12 && colsX * colsZ == 144);
        HeightStats t = r1.terrain();
        check("terrain bandHeight == 32, mean finite, min<=max",
                t.bandHeight() == 32 && Double.isFinite(t.mean())
                        && t.min() <= t.max());
        check("terrain percentiles ordered within [0,320]",
                0.0 <= t.p5() && t.p5() <= t.p50() && t.p50() <= t.p95() && t.p95() <= 320.0);

        // ---- 3 blocks self-consistency --------------------------------------
        check("blocks: byId sum == total AND byBand sum == total",
                blocksByIdSum(r1.blocks()) == r1.blocks().total()
                        && blocksByBandSum(r1.blocks()) == r1.blocks().total());

        // ---- 4 caves --------------------------------------------------------
        CaveStats c = r1.caves();
        boolean histOk = c.crossingsHistogram().length == 32;
        long histSum = 0;
        for (long v : c.crossingsHistogram()) {
            histSum += v;
        }
        boolean bandNonEmpty = true;
        for (CaveBand cb : c.bands()) {
            if (cb.air() + cb.fluid() + cb.solid() <= 0) {
                bandNonEmpty = false;
            }
        }
        check("caves: crossings histogram len==32 and sum>0", histOk && histSum > 0);
        check("caves: surfaceOpenings >= 0", c.surfaceOpenings() >= 0);
        check("caves: every CaveBand air+fluid+solid > 0", bandNonEmpty);

        // ---- 5 biome --------------------------------------------------------
        BiomeStats bs = r1.biomes();
        TreeMap<String, Long> biomeExpected = countBiomes(fake, w, stepXz);
        check("biomes: census has exactly 2 ids", bs.census().size() == 2);
        boolean ratioOk = true;
        for (BiomeEntry e : bs.census()) {
            long expect = biomeExpected.getOrDefault(e.id(), 0L);
            if (e.ratioN() != expect || e.ratioD() != r1.columnSamples()) {
                ratioOk = false;
            }
        }
        check("biomes: ratioN/ratioD matches per-biome column count (ratioD==columns)",
                ratioOk);
        boolean climateOk = bs.climate().size() == 7;
        for (ClimateHistogram h : bs.climate()) {
            if (h.buckets().length != h.bucketCount() || h.bucketCount() != 32
                    || h.min() > h.max()) {
                climateOk = false;
            }
        }
        check("biomes: 7 climate fields, each buckets==bucketCount==32, min<=max",
                climateOk);

        // ---- 6+7 slice --------------------------------------------------------
        SliceReport sr = r1.slice();
        check("slice captured (non-null) with census/planes/positions",
                r1.slice() != null
                        && r1.slice().census() != null
                        && !r1.slice().planes().isEmpty()
                        && !r1.slice().positions().isEmpty());
        check("slice: census byId sum == census total", sliceCensusSum(sr.census()) == sr.census().total());
        boolean posInBox = true;
        boolean posBlockMatch = true;
        for (PositionRecord p : sr.positions()) {
            if (p.x() < sr.slice().start() || p.x() >= sr.slice().end()
                    || p.y() < fake.minY() || p.y() >= fake.maxY()
                    || p.z() < w.zFrom() || p.z() >= w.zTo()) {
                posInBox = false;
            }
            if (!p.block().equals(fake.block(p.x(), p.y(), p.z()))) {
                posBlockMatch = false;
            }
        }
        check("slice: every position inside box and matches sampler.block",
                posInBox && posBlockMatch);
        int yWidth = fake.maxY() - fake.minY();
        int zWidth = w.zTo() - w.zFrom();
        SlicePlane p0 = sr.planes().get(0);
        boolean planeShape = p0.rows().size() == yWidth
                && p0.rows().get(0).split(",", -1).length == zWidth;
        int p0y = fake.minY() + 3;
        int p0z = w.zFrom() + 5;
        String token = p0.rows().get(p0y - fake.minY()).split(",", -1)[p0z - w.zFrom()];
        check("slice: first plane row-count==y width, tokens==z width, token matches sampler",
                planeShape
                        && p0.axisIndex() >= sr.slice().start()
                        && p0.axisIndex() < sr.slice().end()
                        && token.equals(fake.block(p0.axisIndex(), p0y, p0z)));
        check("slice: positions aggregate == census byId (sorted pairwise)",
                positionsMatchCensus(sr));

        // ---- 8 residency -----------------------------------------------------
        ProfilePlan rplan = buildPlan(ProfileSink.NONE, false, true);
        ResidentCollector rc = new ResidentCollector(rplan, fake.minY(), fake.maxY());
        boolean a1 = rc.accept(-1, -1, fake);
        boolean a2 = rc.accept(0, 0, fake);
        boolean a3 = rc.accept(1, 1, fake);
        boolean a4 = rc.accept(0, -1, fake);
        check("resident: 4 distinct in-window chunks accepted first time", a1 && a2 && a3 && a4);
        boolean a5 = rc.accept(0, 0, fake);
        check("resident: duplicate chunk rejected, chunksCovered()==4",
                !a5 && rc.chunksCovered() == 4);
        ProfileReport snap = rc.snapshot(123L, "minecraft:overworld", "probe");
        check("resident: snapshot columnSamples>0, slice==null, HeightStats.p50==0.0",
                snap.columnSamples() > 0 && snap.slice() == null && snap.terrain().p50() == 0.0);
        boolean a6 = rc.accept(5, 5, fake);
        check("resident: out-of-window chunk counted in dedup (clips to 0 columns)",
                a6 && rc.chunksCovered() == 5);

        // ---- 9 td output -----------------------------------------------------
        String tdText = TdWriter.write(r1);
        boolean tdOk = false;
        try {
            TdTable root = Td.parse(tdText);
            tdOk = tdText.contains("meta =") && tdText.contains("terrain =")
                    && tdText.contains("column_samples =")
                    && root.keys().contains("plan")
                    && root.keys().contains("terrain")
                    && root.keys().contains("meta");
        } catch (Exception e) {
            tdOk = false;
        }
        check("td: non-empty, contains meta/terrain/column_samples, parses with root key plan",
                tdOk);

        // ---- 10 zd output ----------------------------------------------------
        byte[] zd = ZdWriter.write(r1);
        byte[] hdr = {(byte) 0x54, (byte) 0x49, (byte) 0x45, (byte) 0x44, (byte) 0x42,
                (byte) 0x5A, (byte) 0x44, (byte) 0x00, (byte) 0x02, (byte) 0x00};
        boolean hdrOk = zd.length > 10;
        for (int i = 0; i < hdr.length; i++) {
            if (zd[i] != hdr[i]) {
                hdrOk = false;
            }
        }
        check("zd: length>10 and 10-byte TIEDBZD v2 header exact", hdrOk);
        check("zd: first record kind tag == 0x0A at bytes[10]",
                zd.length >= 11 && (zd[10] & 0xFF) == 0x0A);

        // ---- 11 ProfilePlanTd round-trip ------------------------------------
        String tdDoc = "[\n"
                + "  residency = false,\n"
                + "  window = [ center = [0, 0], radius = 1 ],\n"
                + "  categories = [ \"biome\", \"blocks\", \"caves\", \"terrain\" ],\n"
                + "  step = [ xz = 4, y = 1 ],\n"
                + "  slice = [ axis = \"x\", start = -8, length = 16, unit = \"block\", planes = true, positions = true ],\n"
                + "  format = [ \"zd\" ],\n"
                + "  sink = \"run\"\n"
                + "]";
        ProfilePlan pFromSource = ProfilePlanTd.fromSource(tdDoc);
        ProfilePlan pBack = ProfilePlanTd.fromTd(ProfilePlanTd.toTd(pFromSource));
        check("ProfilePlanTd: hand-written shape parses and double round-trip stable",
                pFromSource.slice() != null
                        && pFromSource.slice().start() == -8
                        && pFromSource.slice().end() == 8
                        && pFromSource.equals(pBack));

        // ---- 12 ApiRegistry --------------------------------------------------
        ApiRegistry api = new ApiRegistry();
        FakeWorld apiWorld = new FakeWorld();
        api.setSamplerSource(() -> apiWorld);
        api.setSinkRoot(null);
        api.setRunContext(42L, "minecraft:overworld", "probe");
        ProfilePlan apiPlan = buildPlan(ProfileSink.NONE, false, false);
        api.register("t", apiPlan);
        boolean ran = api.run("t");
        ProfileReport apiR = api.report("t");
        check("ApiRegistry: run(\"t\") true and report non-null with columnSamples>0",
                ran && apiR != null && apiR.columnSamples() > 0);
        api.setEnabled("t", false);
        check("ApiRegistry: setEnabled(false) then run returns false", !api.run("t"));
        api.remove("t");
        check("ApiRegistry: after remove report returns null", api.report("t") == null);

        // ---- 13 plan negative validation -------------------------------------
        boolean stepNeg = false;
        try {
            new ProfileStep(0, 1);
        } catch (IllegalArgumentException e) {
            stepNeg = true;
        }
        check("plan negative: ProfileStep(xz=0) rejected", stepNeg);
        boolean sliceNeg = false;
        try {
            new ProfileSlice(ProfileAxis.X, 5, 5, SliceUnit.BLOCK, false, false);
        } catch (IllegalArgumentException e) {
            sliceNeg = true;
        }
        check("plan negative: ProfileSlice(start>=end) rejected", sliceNeg);

        if (failures == 0) {
            System.out.println("[WorldProfileProbe] PASS (world profiler, " + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[WorldProfileProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }

    private static long blocksByIdSum(BlockStats s) {
        long sum = 0;
        for (BlockEntry e : s.byId()) {
            sum += e.count();
        }
        return sum;
    }

    private static long blocksByBandSum(BlockStats s) {
        long sum = 0;
        for (BandBlocks bb : s.byBand()) {
            for (BlockEntry e : bb.entries()) {
                sum += e.count();
            }
        }
        return sum;
    }

    private static long sliceCensusSum(SliceCensus c) {
        long sum = 0;
        for (BlockEntry e : c.byId()) {
            sum += e.count();
        }
        return sum;
    }

    private static TreeMap<String, Long> countBiomes(WorldSampler s, Box w, int stepXz) {
        TreeMap<String, Long> out = new TreeMap<>();
        for (int x = w.xFrom(); x < w.xTo(); x += stepXz) {
            for (int z = w.zFrom(); z < w.zTo(); z += stepXz) {
                int ys = s.surfaceY(x, z);
                out.merge(s.biome(x, ys, z), 1L, Long::sum);
            }
        }
        return out;
    }

    private static boolean positionsMatchCensus(SliceReport sr) {
        TreeMap<String, Long> agg = new TreeMap<>();
        for (PositionRecord p : sr.positions()) {
            agg.merge(p.block(), 1L, Long::sum);
        }
        if (agg.size() != sr.census().byId().size()) {
            return false;
        }
        long[] idA = agg.keySet().stream().mapToLong(k -> agg.get(k)).toArray();
        long[] idB = sr.census().byId().stream().mapToLong(e -> e.count()).toArray();
        return java.util.Arrays.equals(idA, idB);
    }
}