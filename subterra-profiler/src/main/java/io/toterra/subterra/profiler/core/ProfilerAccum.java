package io.toterra.subterra.profiler.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import io.toterra.subterra.api.worldgen.profiler.ProfileCategory;
import io.toterra.subterra.api.worldgen.profiler.ProfileStep;

/**
 * Mutable incremental accumulator shared by {@link StatsEngine} and {@link
 * ResidentCollector} (p.1.8.30). One instance gathers the four category
 * statistics column by column; the accumulated state is merged exactly like a
 * normal scan's, so a resident snapshot is semantically identical to a one-shot
 * window scan. Terrain heights are retained only when {@code pctMode} is set
 * (one-shot scan needing exact percentiles); a resident never stores the full
 * height sample and falls back to the per-band histogram.
 * <p>
 * 供 {@link StatsEngine} 与 {@link ResidentCollector} 共用的可变增量累加器（p.1.8.30）。
 * 单个实例逐列收集四类统计，其累计状态与普通扫描结果完全一致，因此常驻快照与一次性窗口
 * 扫描语义相同。仅当 {@code pctMode} 开启时（一次性扫描需要精确分位数）才保留全部高度
 * 样本；常驻模式不保存完整高度样本，改用高度带直方图。
 */
final class ProfilerAccum {

    /** The base (lowest build) Y of the profiled volume. */
    final int yFrom;

    /** The exclusive upper Y of the profiled volume. */
    final int yTo;

    /** Number of 32-wide vertical bands over {@code [yFrom, yTo)}. */
    final int bandCount;

    /** The horizontal sampling stride (unchunked columns). */
    final int stepXz;

    /** The vertical sampling stride. */
    final int stepY;

    /** Whether the terrain category is enabled. */
    final boolean terrainOn;

    /** Whether the blocks category is enabled. */
    final boolean blocksOn;

    /** Whether the caves category is enabled. */
    final boolean cavesOn;

    /** Whether the biome category is enabled. */
    final boolean biomeOn;

    /** Whether to retain the full terrain-height sample for percentiles. */
    final boolean pctMode;

    /** Number of sampled columns so far. */
    long columnSamples;

    /** Per-band terrain-height column counts (index = band). */
    long[] terrainBands;

    /** true until the first height value has been folded in. */
    boolean firstHeight = true;

    /** Observed minimum terrain height. */
    int heightMin;

    /** Observed maximum terrain height. */
    int heightMax;

    /** Sum of sampled terrain heights (for the mean). */
    long heightSum;

    /** Number of columns whose surface sits above sea level. */
    long landColumns;

    /** Number of columns whose surface sits at or below sea level. */
    long oceanColumns;

    /** Full terrain-height sample, retained only in {@link #pctMode}. */
    ArrayList<Integer> heights;

    /** Grand block count across the window. */
    long blockTotal;

    /** Flat per-id block tally. */
    HashMap<String, Long> blockById;

    /** Per-band (index = band) per-id block tally. */
    HashMap<Integer, HashMap<String, Long>> blockByBand;

    /** Per-band (index = band) air/fluid/solid cave tallies (long[3]). */
    HashMap<Integer, long[]> caveBands;

    /** Crossing-length histogram over 32 buckets. */
    long[] crossingsHist;

    /** Count of karst openings to the surface. */
    long surfaceOpenings;

    /** Flat per-id biome tally. */
    HashMap<String, Long> biomeCensus;

    /** Per-climate-field running values. */
    ArrayList<ClimateAcc> climate;

    /** Builds a fresh accumulator for the given categories and volume. */
    ProfilerAccum(Set<ProfileCategory> cats, int yFrom, int yTo, ProfileStep step,
                  boolean pctMode) {
        this.yFrom = yFrom;
        this.yTo = yTo;
        this.bandCount = StatsEngine.bandCount(yFrom, yTo);
        this.stepXz = step.xz();
        this.stepY = step.y();
        this.terrainOn = cats.contains(ProfileCategory.TERRAIN);
        this.blocksOn = cats.contains(ProfileCategory.BLOCKS);
        this.cavesOn = cats.contains(ProfileCategory.CAVES);
        this.biomeOn = cats.contains(ProfileCategory.BIOME);
        this.pctMode = pctMode;
        if (terrainOn) {
            terrainBands = new long[bandCount];
            heights = pctMode ? new ArrayList<>() : null;
        }
        if (blocksOn) {
            blockById = new HashMap<>();
            blockByBand = new HashMap<>();
        }
        if (cavesOn) {
            caveBands = new HashMap<>();
            crossingsHist = new long[32];
        }
        if (biomeOn) {
            biomeCensus = new HashMap<>();
            climate = new ArrayList<>();
            for (String f : StatsEngine.CLIMATE_FIELDS) {
                climate.add(new ClimateAcc(f));
            }
        }
    }

    /** Resets every accumulated value back to its empty state. */
    void reset() {
        columnSamples = 0;
        if (terrainOn) {
            java.util.Arrays.fill(terrainBands, 0L);
            firstHeight = true;
            heightMin = 0;
            heightMax = 0;
            heightSum = 0;
            landColumns = 0;
            oceanColumns = 0;
            if (heights != null) {
                heights.clear();
            }
        }
        if (blocksOn) {
            blockTotal = 0;
            blockById.clear();
            blockByBand.clear();
        }
        if (cavesOn) {
            caveBands.clear();
            java.util.Arrays.fill(crossingsHist, 0L);
            surfaceOpenings = 0;
        }
        if (biomeOn) {
            biomeCensus.clear();
            for (ClimateAcc c : climate) {
                c.reset();
            }
        }
    }

    /** Running per-field climate collector. */
    static final class ClimateAcc {

        /** The router field name. */
        final String field;

        /** Running minimum observed value. */
        double min = Double.POSITIVE_INFINITY;

        /** Running maximum observed value. */
        double max = Double.NEGATIVE_INFINITY;

        /** Every observed value, kept for the final equal-width bucketization. */
        final ArrayList<Double> values = new ArrayList<>();

        ClimateAcc(String field) {
            this.field = field;
        }

        /** Folds one observed value into the collector. */
        void add(double v) {
            if (v < min) {
                min = v;
            }
            if (v > max) {
                max = v;
            }
            values.add(v);
        }

        /** Clears all observed values. */
        void reset() {
            min = Double.POSITIVE_INFINITY;
            max = Double.NEGATIVE_INFINITY;
            values.clear();
        }
    }
}