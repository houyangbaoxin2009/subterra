package io.toterra.subterra.engine.sim.core;

import io.toterra.subterra.engine.sim.budget.BudgetTier;
import io.toterra.subterra.engine.sim.spatial.CellCoord;

import java.util.ArrayList;
import java.util.List;

/**
 * p.2.33.6 引擎侧镜像实现（api 定义形状 / engine 镜像实现范式，对应 {@code api.sim.SimApi}）：以
 * {@code engine.sim} 的真实实现（{@link BudgetTier} / {@code BudgetScheduler.regionOf} /
 * {@code SpatialIndex.query} / {@link CellCoord#of} / {@link PerSimRandom#deriveSeed}）为<b>唯一来源</b>，
 * 暴露与 {@code api.sim.SimApi} 同语义的只读契约面——预算三级固定序、region/cell 右移映射、无溢出预算决策、
 * Chebyshev 窗口 cell 枚举与 per-simulant-per-tick 种子派生均同输入同输出（供 p.2.33.6 探针对照断言）。
 * 本镜像<em>不 import</em> api 包，仅消费 engine 自身类型并把 api 契约值逐字导出，从而在两侧分别实例化后
 * 完全一致。
 * <p>确定性：全部方法为纯函数；{@link #simSeed} 直接调用 {@link PerSimRandom#deriveSeed}（包内可见）——
 * 逐字导出 engine 的真实种子派生（而非重写一份可能漂移的实现）；{@link #neighborCells} 仅枚举
 * {@code (2*radius+1)²} 窗口并保持与 {@code SpatialIndex.query} 相同的外层 dx / 内层 dz 遍历序。无浮点、
 * 无随机、无时序。
 * <p>
 * p.2.33.6 engine-side mirror implementation (api-shapes / engine-mirrors pattern, counterpart of
 * {@code api.sim.SimApi}): using the <b>actual</b> {@code engine.sim} implementations ({@link BudgetTier} /
 * {@code BudgetScheduler.regionOf} / {@code SpatialIndex.query} / {@link CellCoord#of} /
 * {@link PerSimRandom#deriveSeed}) as the single source of truth, it exposes a read-only surface with the same
 * semantics as {@code api.sim.SimApi} — the fixed-order budget tiers, region/cell right-shift maps, the
 * overflow-free budget decision, the Chebyshev window-cell enumeration and the per-simulant-per-tick seed
 * derivation are all same-input-same-output (the p.2.33.6 probe asserts both sides). This mirror does
 * <em>not</em> import the api package; it consumes only engine types and exports the api contract values
 * verbatim, so instantiating both sides yields identical results.
 * <p>Deterministic: every method is a pure function; {@link #simSeed} calls {@link PerSimRandom#deriveSeed}
 * directly (package-visible) — exporting the engine's real seed derivation verbatim (never a re-implementation
 * that could drift); {@link #neighborCells} enumerates only the {@code (2·radius+1)²} window in the same
 * dx-outer / dz-inner traversal order as {@code SpatialIndex.query}. No float, no randomness, no timing.
 */
public final class SimApiMirror {

    /** A spatial region identity, mirroring {@code api.sim.SimApi.Region}. */
    public record Region(long x, long z) {
    }

    /** A cell coordinate, mirroring {@code api.sim.SimApi.Cell}. */
    public record Cell(long x, long z) {
    }

    private SimApiMirror() {
    }

    /** The fixed-order budget tier names, sourced verbatim from {@link BudgetTier#values()}. */
    public static List<String> budgetTiers() {
        return List.of(BudgetTier.SIMULANT.name(), BudgetTier.REGION.name(), BudgetTier.GLOBAL.name());
    }

    /**
     * Maps a world coordinate pair to its region via the same arithmetic right-shift as
     * {@code BudgetScheduler#regionOf}.
     */
    public static Region regionOf(long worldX, long worldZ, int regionBits) {
        if (regionBits < 0 || regionBits > 63) {
            throw new IllegalArgumentException("regionBits must be in [0, 63]: " + regionBits);
        }
        return new Region(worldX >> regionBits, worldZ >> regionBits);
    }

    /**
     * The atomic all-or-nothing budget decision, mirroring {@code BudgetScheduler.charge}'s overflow-free
     * comparison {@code units > cap - used} across all three tiers.
     */
    public static boolean budgetAccepted(long simulantCap, long regionCap, long globalCap,
                                         long simUsed, long regionUsed, long globalUsed, long units) {
        if (simulantCap < 1 || regionCap < 1 || globalCap < 1) {
            throw new IllegalArgumentException("budget caps must each be >= 1");
        }
        if (units <= 0) {
            throw new IllegalArgumentException("units must be > 0: " + units);
        }
        if (simUsed < 0 || regionUsed < 0 || globalUsed < 0) {
            throw new IllegalArgumentException("used units must not be negative");
        }
        return !(units > simulantCap - simUsed
                || units > regionCap - regionUsed
                || units > globalCap - globalUsed);
    }

    /** The cell holding a world position, via {@link CellCoord#of}. */
    public static Cell cellOf(long worldX, long worldZ, int cellBits) {
        CellCoord c = CellCoord.of(worldX, worldZ, cellBits);
        return new Cell(c.x(), c.z());
    }

    /**
     * The {@code (2·radius+1)²} Chebyshev window cells around a center cell, in the same dx-outer / dz-inner
     * traversal order as {@code SpatialIndex#query}.
     */
    public static List<Cell> neighborCells(long centerX, long centerZ, int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("radius must be >= 0, was " + radius);
        }
        List<Cell> out = new ArrayList<>();
        for (long dx = -radius; dx <= radius; dx++) {
            for (long dz = -radius; dz <= radius; dz++) {
                out.add(new Cell(centerX + dx, centerZ + dz));
            }
        }
        return List.copyOf(out);
    }

    /** The strictly per-simulant-per-tick seed, sourced verbatim from {@link PerSimRandom#deriveSeed}. */
    public static long simSeed(long worldSeed, long tickNo, long x, long z) {
        return PerSimRandom.deriveSeed(worldSeed, tickNo, x, z);
    }
}