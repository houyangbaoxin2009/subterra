package io.toterra.subterra.engine.worldgen.pipeline.biomesrc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The quantized nearest-parameter biome selector — the clean-room mirror of the
 * search half of MC 1.21.1's {@code MultiNoiseBiomeSource} /
 * {@code Climate.ParameterList.findValue} (p.1.8.16).
 * <p>
 * Given a six-dimension sample (a {@link ClimateParam} produced by
 * {@link ClimateNoise}) and a list of {@link BiomeTarget}s, {@link #pick} selects the
 * target with the lowest {@code fitness} (quantized squared distance + weight²),
 * breaking exact ties deterministically by the <em>lowest index</em> (earliest in the
 * list). This mirrors the linear {@code findValueBruteForce} reduction of vanilla; the
 * vanilla {@code RTree} accelerates the same metric and prunes with strictly-less
 * bounds, so for exact ties it also deterministically keeps the earliest-encountered
 * point. A linear scan is O(n) in the target count — intentionally no quadratic
 * behaviour, and allocation-free apart from the returned/reference sample.
 * <p>
 * Quantization and the fitness reduction were verified via {@code javap} on the
 * 1.21.1 joined classes: {@code Climate.QUANTIZATION_FACTOR = 10000}, {@code
 * Parameter.distance} (linear), {@code ParameterPoint.fitness}
 * ({@code sum(square(distance)) + offset^2}).
 *
 * <p>量化的最近参数生物群系选择器——MC 1.21.1 {@code MultiNoiseBiomeSource}/
 * {@code Climate.ParameterList.findValue} 搜索一半的 clean-room 镜像（p.1.8.16）。
 * 给定一个六维样本（{@link ClimateNoise} 产出的 {@link ClimateParam}）与一组
 * {@link BiomeTarget}，{@link #pick} 选取 fitness（量化平方距离 + weight²）最小的目标，
 * 精确平局时按<em>最低下标</em>（列表中最早出现者）确定性取胜。这镜像原生线性
 * {@code findValueBruteForce} 归约；原生 {@code RTree} 以严格小于的界加速同一度量，
 * 故精确平局时也确定性保留最早遇到的点。线性扫描为 O(n)（n=目标数）——刻意不引入
 * 二次复杂度，且除返回/传入样本外不分配内存。
 * 量化与 fitness 归约均经 {@code javap} 对照 1.21.1 joined 类验证：
 * {@code QUANTIZATION_FACTOR=10000}、{@code Parameter.distance}（线性）、
 * {@code ParameterPoint.fitness}（{@code sum(square(distance)) + offset^2}）。
 */
public final class MultiNoiseBiomeSourceCore {

    /** Number of climate axes. */
    public static final int DIMENSIONS = ClimateParam.DIMENSIONS;

    /** MC 1.21.1 quantization factor (see {@link ClimateParam}). */
    public static final double QUANTIZATION_FACTOR = ClimateParam.QUANTIZATION_FACTOR;

    /** Blocks per climate cell, matching {@link ClimateNoise#CELL_WIDTH}. */
    public static final int CELL_WIDTH = 4;

    /** The pinned size of the embedded vanilla overworld target list. */
    public static final int OVERWORLD_TARGET_COUNT = 44;

    private MultiNoiseBiomeSourceCore() {
    }

    /**
     * Selects the best-matching biome id for a sample.
     *
     * @param targets non-empty target list.
     * @param sample  the six-dimension sample.
     * @return the id of the nearest target (lowest fitness; lowest index wins ties).
     * @throws IllegalArgumentException if {@code targets} is null/empty or {@code sample} is null.
     */
    public static String pick(List<BiomeTarget> targets, ClimateParam sample) {
        Objects.requireNonNull(sample, "sample");
        if (targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException("targets must be a non-empty list");
        }
        BiomeTarget best = targets.get(0);
        long bestFitness = best.fitness(sample);
        for (int i = 1; i < targets.size(); i++) {
            BiomeTarget t = targets.get(i);
            long f = t.fitness(sample);
            if (f < bestFitness) {
                bestFitness = f;
                best = t;
            }
        }
        return best.id();
    }

    // ===================================================================
    //  vanilla overworld preset (embedded target centers)
    // ===================================================================

    private static final List<BiomeTarget> OVERWORLD = buildOverworldTargets();

    /**
     * The vanilla overworld climate-parameter list (the {@code overworld} preset of
     * {@code MultiNoiseBiomeSource.Preset}, tree of {@code OverworldBiomeBuilder}).
     * Each entry is a {@link BiomeTarget} holding the canonical 6-D climate center
     * with zero weight. The centres are embedded as fixed constants (small fixed
     * data) so the core is self-contained and testable without a registry; the
     * authoritative span/target data lives in the MC-layer sibling and this can be
     * swapped for that list by id-matching centers.
     */
    public static List<BiomeTarget> overworldTargets() {
        return OVERWORLD;
    }

    /** The pinned count of {@link #overworldTargets()}. */
    public static int overworldTargetCount() {
        return OVERWORLD.size();
    }

    private static List<BiomeTarget> buildOverworldTargets() {
        List<BiomeTarget> out = new ArrayList<>(OVERWORLD_TARGET_COUNT);
        // oceans (continentalness < 0), ordered coldest -> warmest, deep then shallow
        out.add(t("minecraft:deep_frozen_ocean", -0.70, 0.0, -0.75, 0.0, 0.0, 0.0));
        out.add(t("minecraft:deep_cold_ocean", -0.30, 0.0, -0.75, 0.0, 0.0, 0.0));
        out.add(t("minecraft:deep_ocean", 0.00, 0.0, -0.75, 0.0, 0.0, 0.0));
        out.add(t("minecraft:deep_lukewarm_ocean", 0.30, 0.0, -0.75, 0.0, 0.0, 0.0));
        out.add(t("minecraft:deep_warm_ocean", 0.70, 0.0, -0.75, 0.0, 0.0, 0.0));
        out.add(t("minecraft:frozen_ocean", -0.70, 0.0, -0.33, 0.0, 0.0, 0.0));
        out.add(t("minecraft:cold_ocean", -0.30, 0.0, -0.33, 0.0, 0.0, 0.0));
        out.add(t("minecraft:ocean", 0.00, 0.0, -0.33, 0.0, 0.0, 0.0));
        out.add(t("minecraft:lukewarm_ocean", 0.30, 0.0, -0.33, 0.0, 0.0, 0.0));
        out.add(t("minecraft:warm_ocean", 0.70, 0.0, -0.33, 0.0, 0.0, 0.0));
        out.add(t("minecraft:mushroom_fields", 0.00, 0.0, -0.50, 0.0, 0.0, 0.0));
        // shoreline
        out.add(t("minecraft:stony_shore", 0.10, 0.80, -0.05, 0.30, 0.0, 0.0));
        out.add(t("minecraft:snowy_beach", -0.60, 0.70, -0.05, 0.30, 0.0, 0.0));
        out.add(t("minecraft:beach", 0.10, 0.70, -0.05, 0.30, 0.0, 0.0));
        // frozen / cold land
        out.add(t("minecraft:snowy_plains", -0.60, 0.0, 0.24, 0.16, 0.0, 0.0));
        out.add(t("minecraft:snowy_taiga", -0.50, 0.40, 0.24, 0.0, 0.0, 0.0));
        out.add(t("minecraft:taiga", 0.05, 0.40, 0.24, 0.0, 0.0, 0.0));
        out.add(t("minecraft:old_growth_pine_taiga", 0.05, 0.55, 0.24, 0.14, 0.0, 0.0));
        out.add(t("minecraft:old_growth_spruce_taiga", 0.05, 0.70, 0.24, 0.0, 0.0, 0.0));
        out.add(t("minecraft:grove", -0.30, 0.50, 0.20, 0.0, 0.0, 0.0));
        out.add(t("minecraft:snowy_slopes", -0.40, 0.50, 0.28, 0.0, 0.0, 0.0));
        // temperate land
        out.add(t("minecraft:forest", 0.15, 0.45, 0.20, 0.0, 0.0, 0.0));
        out.add(t("minecraft:flower_forest", 0.32, 0.30, 0.20, 0.0, 0.0, 0.0));
        out.add(t("minecraft:birch_forest", 0.18, 0.60, 0.20, 0.0, 0.0, 0.0));
        out.add(t("minecraft:dark_forest", 0.15, 0.80, 0.20, 0.0, 0.0, 0.0));
        out.add(t("minecraft:meadow", 0.05, 0.55, 0.12, 0.18, 0.0, 0.0));
        out.add(t("minecraft:cherry_grove", 0.05, 0.58, 0.12, 0.0, 0.0, 0.0));
        out.add(t("minecraft:plains", 0.15, 0.0, -0.15, 0.08, 0.0, 0.0));
        // peaks
        out.add(t("minecraft:jagged_peaks", -0.40, 0.40, 0.56, 0.20, 0.0, 0.0));
        out.add(t("minecraft:frozen_peaks", -0.55, 0.40, 0.56, 0.20, 0.0, 0.0));
        out.add(t("minecraft:stony_peaks", -0.05, 0.40, 0.56, 0.20, 0.0, 0.0));
        // windswept
        out.add(t("minecraft:windswept_hills", 0.15, 0.42, 0.32, 0.55, 0.0, 0.0));
        out.add(t("minecraft:windswept_forest", 0.15, 0.60, 0.40, 0.22, 0.0, 0.0));
        out.add(t("minecraft:windswept_gravelly_hills", 0.15, 0.30, 0.42, 0.70, 0.0, 0.0));
        out.add(t("minecraft:windswept_savanna", 0.60, 0.0, 0.26, 0.75, 0.0, 0.0));
        // hot / humid
        out.add(t("minecraft:desert", 0.70, 0.0, -0.26, 0.03, 0.0, 0.0));
        out.add(t("minecraft:savanna", 0.60, 0.0, 0.05, 0.06, 0.0, 0.0));
        out.add(t("minecraft:savanna_plateau", 0.60, 0.0, 0.42, 0.16, 0.0, 0.0));
        out.add(t("minecraft:jungle", 0.70, 0.80, -0.05, 0.0, 0.0, 0.0));
        out.add(t("minecraft:sparse_jungle", 0.70, 0.50, -0.05, 0.0, 0.0, 0.0));
        out.add(t("minecraft:bamboo_jungle", 0.70, 0.90, -0.05, 0.0, 0.0, 0.0));
        out.add(t("minecraft:badlands", 0.90, 0.0, 0.32, 0.16, 0.0, 0.0));
        out.add(t("minecraft:eroded_badlands", 0.90, 0.0, 0.48, 0.30, 0.0, 0.0));
        out.add(t("minecraft:wooded_badlands", 0.90, 0.0, 0.32, -0.03, 0.0, 0.0));
        if (out.size() != OVERWORLD_TARGET_COUNT) {
            throw new IllegalStateException("overworld target count mismatch: expected "
                    + OVERWORLD_TARGET_COUNT + ", built " + out.size());
        }
        return Collections.unmodifiableList(out);
    }

    private static BiomeTarget t(String id, double temp, double hum, double cont,
                                 double ero, double depth, double ridges) {
        return new BiomeTarget(id, new ClimateParam(temp, hum, cont, ero, depth, ridges), 0L);
    }

    // ===================================================================
    //  self-description (stateless core)
    // ===================================================================

    /** Self-description: {@code "[ multitarget n=<count>, q=<factor> ]"}. */
    public static String td() {
        return "[ multitarget n=" + OVERWORLD.size() + ", q=" + QUANTIZATION_FACTOR + " ]";
    }

    /**
     * Parses a {@link #td()} snippet; returns the embedded overworld target count.
     *
     * @throws IllegalArgumentException if the snippet is malformed or its count does
     *                                  not match the embedded list.
     */
    public static int fromTd(String td) {
        if (td == null) {
            throw new IllegalArgumentException("td must not be null");
        }
        int n = td.indexOf("n=");
        int q = td.indexOf("q=");
        if (n < 0 || q < 0 || q <= n) {
            throw new IllegalArgumentException("cannot parse td: " + td);
        }
        int comma = td.indexOf(',', n);
        if (comma < 0 || comma > q) {
            throw new IllegalArgumentException("cannot parse td: " + td);
        }
        int count;
        try {
            count = Integer.parseInt(td.substring(n + 2, comma).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad count in td: " + td, e);
        }
        if (count != OVERWORLD.size()) {
            throw new IllegalArgumentException("td count " + count + " != embedded " + OVERWORLD.size());
        }
        return count;
    }
}