package io.toterra.subterra.profiler.core;

import java.util.ArrayList;
import java.util.List;

import io.toterra.subterra.api.worldgen.profiler.BandBlocks;
import io.toterra.subterra.api.worldgen.profiler.BiomeEntry;
import io.toterra.subterra.api.worldgen.profiler.BiomeStats;
import io.toterra.subterra.api.worldgen.profiler.BlockEntry;
import io.toterra.subterra.api.worldgen.profiler.BlockStats;
import io.toterra.subterra.api.worldgen.profiler.CaveBand;
import io.toterra.subterra.api.worldgen.profiler.CaveStats;
import io.toterra.subterra.api.worldgen.profiler.ClimateHistogram;
import io.toterra.subterra.api.worldgen.profiler.HeightStats;
import io.toterra.subterra.api.worldgen.profiler.ProfilePlan;
import io.toterra.subterra.api.worldgen.profiler.ProfileReport;
import io.toterra.subterra.api.worldgen.profiler.SliceReport;
import io.toterra.subterra.api.worldgen.profiler.WorldSampler;
import io.toterra.subterra.profiler.core.ProfileRegion.Box;

/**
 * The pure-JDK world-profiling engine (p.1.8.30): a single deterministic scan
 * over the window that accumulates the four category statistics (terrain,
 * blocks, caves, biome) and optionally delegates a slice capture to {@link
 * SliceCollector}. The per-column contribution logic is also shared with {@link
 * ResidentCollector} so a one-shot scan and an incremental resident produce
 * semantically identical section data. Allocation-free in the vertical hot path;
 * no shared mutable state, so it is thread-safe.
 * <p>
 * 纯 JDK 世界档案引擎（p.1.8.30）：对窗口做一次确定性的扫描，累加四类统计（地形、方块、
 * 洞穴、群系），并可选择把切片采集委托给 {@link SliceCollector}。逐列贡献逻辑同样被
 * {@link ResidentCollector} 复用，因此一次性扫描与增量常驻产生语义一致的段落数据。竖直
 * 热路径零分配；无共享可变状态，故线程安全。
 */
public final class StatsEngine {

    /** The seven climate/router fields sampled per biome column. */
    static final String[] CLIMATE_FIELDS = {
        "temperature", "vegetation", "continentalness", "erosion",
        "ridge", "depth", "finalDensity"
    };

    /** The air block ids classified as cave air space. */
    private static final java.util.Set<String> AIR_SET = java.util.Set.of(
        "minecraft:air", "minecraft:cave_air", "minecraft:void_air");

    /** The nominal vertical band width in blocks. */
    private static final int BAND_HEIGHT = 32;

    /** The width of the crossing histogram. */
    private static final int CROSSINGS_BUCKETS = 32;

    private StatsEngine() {
    }

    /**
     * Runs one profiling scan of the window described by {@code plan}, returning
     * the completed {@link ProfileReport}. Columns are traversed left-to-right
     * for {@code x} and then forward for {@code z} at {@code plan.step().xz()}
     * stride; each column is vertically probed downward from its surface at
     * {@code plan.step().y()} stride. When {@code plan.slice()} is non-null the
     * slice is captured via {@link SliceCollector}; otherwise the report carries
     * a null slice. The result is deterministic regardless of the order in which
     * the maps were populated.
     * <p>
     * 对 {@code plan} 描述的窗口执行一次档案扫描，返回完成的 {@link ProfileReport}。
     * 柱阵列按 {@code x} 从左到右、随后 {@code z} 向前以 {@code plan.step().xz()} 步长
     * 遍历；每柱从地表以 {@code plan.step().y()} 步长向下竖直采样。当 {@code
     * plan.slice()} 非空时经 {@link SliceCollector} 采集切片；否则报告携带空切片。无论
     * 各 Map 的填充顺序如何，结果都是确定的。
     *
     * @param s         the world sampler binding.
     * @param plan      the profiling configuration.
     * @param seed      the world seed for metadata.
     * @param dimension the dimension id for metadata.
     * @param appVersion the application version for metadata.
     * @return the completed report.
     */
    public static ProfileReport scan(WorldSampler s, ProfilePlan plan,
                                     long seed, String dimension, String appVersion) {
        Box w = ProfileRegion.windowBox(plan.window());
        int xFrom = w.xFrom();
        int xTo = w.xTo();
        int zFrom = w.zFrom();
        int zTo = w.zTo();
        int stepX = plan.step().xz();
        int stepZ = plan.step().xz();

        ProfilerAccum accum = new ProfilerAccum(
            plan.categories(), s.minY(), s.maxY(), plan.step(), true);

        for (int x = xFrom; x < xTo; x += stepX) {
            for (int z = zFrom; z < zTo; z += stepZ) {
                columnContrib(accum, s, x, z);
            }
        }

        SliceReport slice = null;
        if (plan.slice() != null) {
            slice = SliceCollector.collect(s, plan.slice(), s.minY(), s.maxY(), xFrom, xTo);
        }
        return report(accum, plan, seed, dimension, appVersion, slice);
    }

    /**
     * Folds one column contribution into an accumulator. Reading the surface
     * height is shared by every enabled category; the vertical block/cave
     * sampling and the biome/climate sampling are dispatched per enabled
     * category. This is the shared routine used by both {@link #scan(WorldSampler,
     * ProfilePlan, long, String, String)} and {@link ResidentCollector#accept}.
     * <p>
     * 把单个柱的贡献并入累加器。读取地表高度为每个启用的类别所共用；竖直方块/洞穴采样
     * 与群系/气候采样按启用的类别分发。此例程为 {@link #scan(WorldSampler, ProfilePlan,
     * long, String, String)} 与 {@link ResidentCollector#accept} 共用。
     */
    static void columnContrib(ProfilerAccum a, WorldSampler s, int x, int z) {
        a.columnSamples++;
        boolean anyWork = a.terrainOn || a.blocksOn || a.cavesOn || a.biomeOn;
        if (!anyWork) {
            return;
        }
        int ys = s.surfaceY(x, z);
        if (a.terrainOn) {
            if (a.firstHeight) {
                a.heightMin = ys;
                a.heightMax = ys;
                a.firstHeight = false;
            } else {
                if (ys < a.heightMin) {
                    a.heightMin = ys;
                }
                if (ys > a.heightMax) {
                    a.heightMax = ys;
                }
            }
            a.heightSum += ys;
            if (a.heights != null) {
                a.heights.add(ys);
            }
            a.terrainBands[clamp((ys - a.yFrom) / BAND_HEIGHT, 0, a.bandCount - 1)]++;
            if (ys > s.seaLevel()) {
                a.landColumns++;
            } else {
                a.oceanColumns++;
            }
        }
        if (a.blocksOn || a.cavesOn) {
            long cross = 0;
            boolean prevSolid = false;
            for (int y = ys; y >= a.yFrom; y -= a.stepY) {
                String b = s.block(x, y, z);
                boolean air = isAir(b);
                boolean fluid = isFluid(b);
                int bi = clamp((y - a.yFrom) / BAND_HEIGHT, 0, a.bandCount - 1);
                if (a.blocksOn) {
                    a.blockTotal++;
                    a.blockById.merge(b, 1L, Long::sum);
                    a.blockByBand.computeIfAbsent(bi, k -> new java.util.HashMap<>())
                        .merge(b, 1L, Long::sum);
                }
                if (a.cavesOn) {
                    long[] cb = a.caveBands.computeIfAbsent(bi, k -> new long[3]);
                    if (air) {
                        cb[0]++;
                    } else if (fluid) {
                        cb[1]++;
                    } else {
                        cb[2]++;
                    }
                    if (prevSolid && air) {
                        cross++;
                    }
                }
                prevSolid = !air && !fluid;
            }
            if (a.cavesOn) {
                a.crossingsHist[clamp((int) cross, 0, CROSSINGS_BUCKETS - 1)]++;
                if (isAir(s.block(x, ys - 1, z))) {
                    a.surfaceOpenings++;
                }
            }
        }
        if (a.biomeOn) {
            String id = s.biome(x, ys, z);
            a.biomeCensus.merge(id, 1L, Long::sum);
            int subY = a.yFrom + BAND_HEIGHT;
            for (ProfilerAccum.ClimateAcc c : a.climate) {
                c.add(s.routerField(c.field, x, ys, z));
                c.add(s.routerField(c.field, x, subY, z));
            }
        }
    }

    /**
     * Materializes a {@link ProfileReport} from an accumulator. Categories not
     * present in the plan are filled with empty/zero sections; the terrain
     * section uses 0.0 stats and a single all-zero band when disabled. When the
     * accumulator carries no full height sample (resident mode) the percentile
     * fields are reported as 0.0. Pure and deterministic.
     * <p>
     * 从累加器物化为 {@link ProfileReport}。未在计划中启用的类别以空/零填充；地形关闭时
     * 统计字段用 0.0 并给出单个全零带。当累加器未携带完整高度样本（常驻模式）时，分位
     * 字段被报告为 0.0。纯且确定。
     */
    static ProfileReport report(ProfilerAccum a, ProfilePlan plan,
                                long seed, String dimension, String appVersion,
                                SliceReport slice) {
        HeightStats terrain = terrainStats(a);
        BlockStats blocks = blockStats(a);
        CaveStats caves = caveStats(a);
        BiomeStats biomes = biomeStats(a);
        return new ProfileReport(seed, dimension, appVersion, plan,
            terrain, blocks, caves, biomes, slice, a.columnSamples);
    }

    private static HeightStats terrainStats(ProfilerAccum a) {
        if (!a.terrainOn) {
            return new HeightStats(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                new int[1], BAND_HEIGHT, 0, 0);
        }
        int n = (int) a.columnSamples;
        double minv = a.firstHeight ? 0.0 : a.heightMin;
        double maxv = a.firstHeight ? 0.0 : a.heightMax;
        double meanv = n > 0 ? (double) a.heightSum / n : 0.0;
        double p5 = 0.0, p25 = 0.0, p50 = 0.0, p75 = 0.0, p95 = 0.0;
        if (a.heights != null && !a.heights.isEmpty()) {
            int[] sorted = toSortedInts(a.heights);
            p5 = percentile(sorted, 5);
            p25 = percentile(sorted, 25);
            p50 = percentile(sorted, 50);
            p75 = percentile(sorted, 75);
            p95 = percentile(sorted, 95);
        }
        int[] bandCounts = new int[a.terrainBands.length];
        for (int i = 0; i < bandCounts.length; i++) {
            bandCounts[i] = (int) a.terrainBands[i];
        }
        return new HeightStats(minv, maxv, meanv, p5, p25, p50, p75, p95,
            bandCounts, BAND_HEIGHT, a.landColumns, a.oceanColumns);
    }

    private static BlockStats blockStats(ProfilerAccum a) {
        if (!a.blocksOn) {
            return new BlockStats(0, List.of(), List.of());
        }
        List<BlockEntry> byId = sortedEntries(a.blockById);
        List<BandBlocks> byBand = new ArrayList<>();
        for (int k : sortedKeys(a.blockByBand)) {
            byBand.add(new BandBlocks(a.yFrom + BAND_HEIGHT * k,
                Math.min(a.yFrom + BAND_HEIGHT * (k + 1), a.yTo),
                sortedEntries(a.blockByBand.get(k))));
        }
        return new BlockStats(a.blockTotal, byId, byBand);
    }

    private static CaveStats caveStats(ProfilerAccum a) {
        if (!a.cavesOn) {
            return new CaveStats(List.of(), new long[CROSSINGS_BUCKETS], 0);
        }
        List<CaveBand> bands = new ArrayList<>();
        for (int k : sortedKeys(a.caveBands)) {
            long[] cb = a.caveBands.get(k);
            bands.add(new CaveBand(a.yFrom + BAND_HEIGHT * k,
                a.yFrom + BAND_HEIGHT * (k + 1), cb[0], cb[1], cb[2]));
        }
        return new CaveStats(bands, a.crossingsHist.clone(), a.surfaceOpenings);
    }

    private static BiomeStats biomeStats(ProfilerAccum a) {
        if (!a.biomeOn) {
            return new BiomeStats(List.of(), List.of());
        }
        long ratioD = Math.max(1L, a.columnSamples);
        List<BiomeEntry> census = new ArrayList<>();
        for (var e : sortedEntries(a.biomeCensus)) {
            census.add(new BiomeEntry(e.id(), e.count(), ratioD));
        }
        List<ClimateHistogram> climate = new ArrayList<>();
        for (ProfilerAccum.ClimateAcc c : a.climate) {
            climate.add(climateHist(c));
        }
        return new BiomeStats(census, climate);
    }

    private static ClimateHistogram climateHist(ProfilerAccum.ClimateAcc c) {
        int[] buckets = new int[32];
        double mn = c.values.isEmpty() ? 0.0 : c.min;
        double mx = c.values.isEmpty() ? 0.0 : c.max;
        for (double v : c.values) {
            int idx;
            if (mx <= mn) {
                idx = 0;
            } else {
                idx = clamp((int) ((v - mn) / (mx - mn) * 31.0), 0, 31);
            }
            buckets[idx]++;
        }
        return new ClimateHistogram(c.field, mn, mx, buckets, 32);
    }

    /** Clamps {@code v} into {@code [lo, hi]}. */
    static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (Math.min(v, hi));
    }

    /** The number of 32-wide bands covering {@code [yFrom, yTo)}. */
    static int bandCount(int yFrom, int yTo) {
        int span = yTo - yFrom;
        return span <= 0 ? 1 : (span + BAND_HEIGHT - 1) / BAND_HEIGHT;
    }

    /** Whether a block id belongs to the air set. */
    static boolean isAir(String b) {
        return AIR_SET.contains(b);
    }

    /** Whether a block id is a water or lava fluid. */
    static boolean isFluid(String b) {
        return b.startsWith("minecraft:water") || b.startsWith("minecraft:lava");
    }

    /** Sorts a block tally into dictionary-ordered entries. */
    static List<BlockEntry> sortedEntries(java.util.Map<String, Long> m) {
        List<String> keys = new ArrayList<>(m.keySet());
        keys.sort(null);
        List<BlockEntry> out = new ArrayList<>(keys.size());
        for (String k : keys) {
            out.add(new BlockEntry(k, m.get(k)));
        }
        return out;
    }

    /** Sorts an integer-keyed tally's keys ascending. */
    static List<Integer> sortedKeys(java.util.Map<Integer, ?> m) {
        List<Integer> keys = new ArrayList<>(m.keySet());
        keys.sort(null);
        return keys;
    }

    /** The p-th percentile of a sorted int sample via linear target index. */
    private static double percentile(int[] sorted, int p) {
        int n = sorted.length;
        if (n == 0) {
            return 0.0;
        }
        int idx = (int) ((p / 100.0) * (n - 1));
        return sorted[clamp(idx, 0, n - 1)];
    }

    /** Sorts a boxed int list into a primitive array. */
    private static int[] toSortedInts(java.util.List<Integer> values) {
        int[] out = new int[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i);
        }
        java.util.Arrays.sort(out);
        return out;
    }
}