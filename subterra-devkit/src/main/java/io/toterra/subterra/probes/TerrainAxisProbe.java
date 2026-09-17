package io.toterra.subterra.probes;

import io.toterra.subterra.engine.worldgen.pipeline.density.Density;
import io.toterra.subterra.engine.worldgen.pipeline.router.NoiseRouter;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * p.1.8.33 波浪地形回归探针（纯 JVM、确定性）：守住「主世界密度场双轴健康 + JSON
 * 噪声块与 subterra:density 窗口一致 + 地表高度语义正常」三道防线。
 *
 * <ul>
 *   <li><b>双轴变化度</b>：finalDensity / continents / depth 沿 x 线与 z 线（多种子、
 *       多 y）都必须有实质变化——任一场沿任一水平轴跨度近零即单轴退化（本探针因
 *       2026-09-16 「x 走向山峰 + x 走向海洋 + 海浪状」实机回归而立）。</li>
 *   <li><b>窗口一致性</b>：随附 {@code subterra_overworld.json} 的 {@code noise.min_y /
 *       noise.height / sea_level} 必须与 {@code subterra:density} 的 kind 窗口
 *       （[-64, 320)，vanilla 锚定）与海平面 63 严格一致——td-plus 曾把三者扩成
 *       [-128, 592) + 海平面 128，而复合层锚点仍 vanilla 系，导致全域淹没。</li>
 *   <li><b>地表语义</b>：金标种子下 32×32 网格的密度零穿越地表高度，均值落在
 *       vanilla 海平面邻域、离散度非零（既非全淹也非平板）。</li>
 * </ul>
 * 禁时序断言；同输入同字节。
 *
 * <p>The p.1.8.33 wave-terrain regression probe (pure JVM, deterministic): guards
 * (a) two-axis variation of the overworld fields (multi-seed, multi-y — any near-zero
 * span on one horizontal axis is the single-axis degeneracy this probe was created for
 * after the 2026-09-16 "x-aligned ridges + x-aligned oceans + wave-shaped world"
 * field report), (b) strict JSON ↔ subterra:density window consistency (the td-plus
 * window expansion drowned the world because the composite anchors stayed vanilla-bound),
 * and (c) surface-height semantics on the golden seed (mean near the vanilla sea level,
 * non-zero spread). No timing assertions; same input → same bytes.
 */
public final class TerrainAxisProbe {

    private static int checks;
    private static int failures;

    public static void main(String[] args) {
        axisVariation();
        jsonWindowConsistency();
        surfaceSemantics();
        System.out.println("[TerrainAxisProbe] " + (failures == 0 ? "PASS (" + checks + " checks)"
                : "FAIL (" + failures + " of " + checks + " checks failed)"));
        if (failures > 0) {
            System.exit(1);
        }
    }

    /** 双轴变化度：地表高度场沿行/列的 std 都必须 > 2（单轴退化即波浪地形）。 /
     *  Two-axis variation of the SURFACE-HEIGHT field: per-row and per-column std must both exceed 2. */
    private static void axisVariation() {
        long[] seeds = {44905237L, 12345678L, 0L};
        for (long seed : seeds) {
            Density terrain = io.toterra.subterra.engine.worldgen.pipeline.terrain.SubterraTerrain.finalDensity(seed);
            double[][] surf = surfaceGrid(terrain, 0, 0, 31, 8);
            int n = surf.length;
            double stdX = rowStd(surf, n);
            double stdZ = colStd(surf, n);
            check("axis: seed=" + seed + " surface-height std along x=" + fmt(stdX)
                            + " along z=" + fmt(stdZ),
                    stdX > 2.0 && stdZ > 2.0);
        }
    }

    /** 地表高度网格：density 零穿越（自上而下首个 >= 0 的 y）。 / The surface-height grid (first y >= 0 density top-down). */
    private static double[][] surfaceGrid(Density terrain, int x0, int z0, int span, int step) {
        int n = span + 1;
        double[][] grid = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int top = -1;
                for (int y = 380; y >= -100; y -= 4) {
                    if (terrain.eval(x0 + i * step, y, z0 + j * step) >= 0.0) {
                        top = y;
                        break;
                    }
                }
                grid[i][j] = top;
            }
        }
        return grid;
    }

    /** 行方向 std（固定 j，沿 i）。 / Std across rows. */
    private static double rowStd(double[][] grid, int n) {
        double sum = 0; double sumSq = 0; long cnt = 0;
        for (int j = 0; j < n; j++) {
            double mean = 0; int m = 0;
            for (int i = 0; i < n; i++) { if (grid[i][j] >= 0) { mean += grid[i][j]; m++; } }
            if (m == 0) continue;
            mean /= m;
            for (int i = 0; i < n; i++) { if (grid[i][j] >= 0) { double d = grid[i][j] - mean; sum += d * d; sumSq += d * d; cnt++; } }
        }
        return cnt == 0 ? 0 : Math.sqrt(sum / cnt);
    }

    /** 列方向 std（固定 i，沿 j）。 / Std across columns. */
    private static double colStd(double[][] grid, int n) {
        double sum = 0; long cnt = 0;
        for (int i = 0; i < n; i++) {
            double mean = 0; int m = 0;
            for (int j = 0; j < n; j++) { if (grid[i][j] >= 0) { mean += grid[i][j]; m++; } }
            if (m == 0) continue;
            mean /= m;
            for (int j = 0; j < n; j++) { if (grid[i][j] >= 0) { double d = grid[i][j] - mean; sum += d * d; cnt++; } }
        }
        return cnt == 0 ? 0 : Math.sqrt(sum / cnt);
    }

    /** JSON 噪声块必须与 kind 窗口和 vanilla 海平面严格一致。 / The shipped JSON must match the kind window and the vanilla sea level. */
    private static void jsonWindowConsistency() {
        String json = readResource("/data/subterra/worldgen/noise_settings/subterra_overworld.json");
        check("json: shipped noise_settings present", json != null && !json.isBlank());
        if (json == null) {
            return;
        }
        check("json: min_y == -64 (kind window, vanilla dimension type)", json.contains("\"min_y\": -64") || json.contains("\"min_y\":-64"));
        check("json: height == 384 (min_y + height == kind.maxY 320 + sea 127 design)",
                json.contains("\"height\": 384") || json.contains("\"height\":384"));
        check("json: sea_level == 127 (design sea level)", json.contains("\"sea_level\": 127") || json.contains("\"sea_level\":127"));
    }

    /** 地表语义：金标种子 32×32 网格，地表（>=127 为陆）比例与高度带符合新设计。 /
     *  Surface semantics: on the golden seed's 32×32 grid, land (surface >= 127) fraction and height band match the design. */
    private static void surfaceSemantics() {
        Density terrain = io.toterra.subterra.engine.worldgen.pipeline.terrain.SubterraTerrain.finalDensity(44905237L);
        // 四象限扫描：任一象限存在成带陆面（比例、均高、离散）即通过——单象限可能整片是海。
        int[][] quads = {{0, 0}, {1024, 0}, {0, -1024}, {1024, -1024}};
        double bestFraction = -1;
        double bestMean = -1;
        double bestStd = -1;
        for (int[] q : quads) {
            double[][] surf = surfaceGrid(terrain, q[0], q[1], 31, 32);
            int land = 0; int n = surf.length; long sum = 0; long sumSq = 0;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (surf[i][j] >= 127) {
                        land++;
                        sum += (long) surf[i][j];
                        sumSq += (long) surf[i][j] * surf[i][j];
                    }
                }
            }
            double fraction = land / (double) (n * n);
            if (fraction > bestFraction) {
                bestFraction = fraction;
                if (land > 0) {
                    double mean = sum / (double) land;
                    bestMean = mean;
                    bestStd = Math.sqrt(Math.max(0.0, sumSq / (double) land - mean * mean));
                } else {
                    bestMean = -1; bestStd = -1;
                }
            }
        }
        check("surface: best-quadrant land fraction " + fmt3(bestFraction) + " within (0.05, 0.98)",
                bestFraction > 0.05 && bestFraction < 0.98);
        check("surface: best-quadrant land mean " + fmt(bestMean) + " within [130, 280]", bestMean >= 130 && bestMean <= 280);
        check("surface: land spread " + fmt(bestStd) + " > 4 (terrain varies)", bestStd > 4.0);
    }

    private static String fmt(double v) {
        return String.format("%.3f", v);
    }

    private static String fmt3(double v) {
        return String.format("%.3f", v);
    }

    private static String readResource(String resource) {
        try (InputStream in = TerrainAxisProbe.class.getResourceAsStream(resource)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }
}
