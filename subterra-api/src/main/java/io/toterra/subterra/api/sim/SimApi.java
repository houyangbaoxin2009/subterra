package io.toterra.subterra.api.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * p.2.33.6 对外模拟域契约门面（final class、静态纯函数、纯 JDK）：确定性接口面，供上层/域模组按
 * 「预算三 cap + 空间索引 + 确定性模拟」使用 engine.sim 域的确定性语义。语义与
 * {@code engine.sim.budget.BudgetScheduler}/{@code engine.sim.spatial.CellCoord.SpatialIndex}/
 * {@code engine.sim.core.PerSimRandom}（p.2.8.1/.2/.3）一致——本处为契约与数据面注入，engine 为实现
 * 镜像（{@code engine.sim.core.SimApiMirror}），api 不依赖 engine。所有常量/语义均从 engine 实际
 * 行为逐字对照落于此，禁止拍脑袋数值。
 * <p>确定性：{@link #budgetTiers()} 返回固定序 {@code SIMULANT, REGION, GLOBAL}；
 * {@link #regionOf} 为纯右移映射（与 {@code BudgetScheduler.regionOf} 同语义，负数经算术右移正确投影）；
 * {@link #budgetAccepted} 以无溢出比较 {@code units > cap - used} 判定三 tier 是否全在 cap 内（原子全收或全拒，
 * 同 {@code BudgetScheduler.charge} 决策）；{@link #cellOf} 为纯右移折叠（同 {@code CellCoord.of}）；
 * {@link #neighborCells} 只枚举 {@code (2*radius+1)^2} 的 Chebyshev 窗口 cell（同
 * {@code SpatialIndex.query} 的窗口遍历，复杂度正比窗口、非全局扫描）；{@link #simSeed} 逐字复现
 * {@code PerSimRandom.deriveSeed} 的确定性种子派生（同输入 → 同 long）。全部无随机、无墙钟、无迭代序依赖。
 * 无状态、无副作用。
 * <p>
 * p.2.33.6 the external simulation-domain contract facade (final class, static pure functions, pure JDK):
 * a deterministic interface surface for upper layers / domain mods to use the deterministic semantics of
 * {@code engine.sim}. Semantics match {@code engine.sim}'s budget/spatial/core (p.2.8.1/.2/.3) — this is the
 * contract and data-injection surface for the engine to mirror as its implementation
 * ({@code engine.sim.core.SimApiMirror}), and the api does not depend on the engine. Every constant/semantic
 * here is pinned verbatim from the engine's actual behaviour — nothing is guessed.
 * <p>Deterministic: {@link #budgetTiers()} returns the fixed order {@code SIMULANT, REGION, GLOBAL};
 * {@link #regionOf} is a pure right-shift map (same semantics as {@code BudgetScheduler.regionOf}, negatives
 * project correctly via arithmetic shift); {@link #budgetAccepted} decides via the overflow-free comparison
 * {@code units > cap - used} whether all three tiers stay within cap (atomic all-or-nothing, same decision as
 * {@code BudgetScheduler.charge}); {@link #cellOf} is a pure right-shift fold (same as {@code CellCoord.of});
 * {@link #neighborCells} enumerates only the {@code (2·radius+1)²} Chebyshev window cells (same window walk as
 * {@code SpatialIndex.query}, cost proportional to the window, not a global scan); {@link #simSeed} reproduces
 * {@code PerSimRandom.deriveSeed}'s deterministic seed derivation verbatim (same input → same {@code long}).
 * No randomness, no wall-clock, no iteration-order dependence. Stateless, side-effect free.
 */
public final class SimApi {

    /** A spatial region identity in the budget domain ({@code worldX >> regionBits}, {@code worldZ >> regionBits}).
     *  预算域的区块区域标识（{@code worldX >> regionBits}，{@code worldZ >> regionBits}）。 */
    public record Region(long x, long z) {
    }

    /** A cell coordinate in the partitioned spatial index's fixed-cell grid ({@code worldX >> cellBits}, …).
     *  分区空间索引固定网格中的格点坐标（{@code worldX >> cellBits}，…）。 */
    public record Cell(long x, long z) {
    }

    private SimApi() {
    }

    /**
     * The fixed-order budget tiers ({@code SIMULANT, REGION, GLOBAL}) of
     * {@code engine.sim.budget.BudgetScheduler}/{@code BudgetTier}. Deterministic; identical on every call.
     * / 预算三级（{@code SIMULANT, REGION, GLOBAL}）固定序，源自
     * {@code engine.sim.budget.BudgetScheduler}/{@code BudgetTier}。确定性；每次调用均相同。
     */
    public static List<String> budgetTiers() {
        return List.of("SIMULANT", "REGION", "GLOBAL");
    }

    /**
     * Maps a world coordinate pair to its region by right-shifting both coordinates by {@code regionBits},
     * with the arithmetic-shift sign semantics of {@code engine.sim.budget.BudgetScheduler#regionOf} (negatives
     * project to the adjacent region on the negative side). Pure and deterministic.
     * / 以 {@code regionBits} 对世界坐标对右移得到其区域（算术右移含符号语义，与
     * {@code engine.sim.budget.BudgetScheduler#regionOf} 一致，负数正确投影到负向相邻区域）。纯函数且确定性。
     *
     * @param worldX     the world X coordinate.
     * @param worldZ     the world Z coordinate.
     * @param regionBits how many bits to shift away to form a region; {@code [0, 63]}.
     * @return the resulting region.
     * @throws IllegalArgumentException if {@code regionBits} is outside {@code [0, 63]}.
     */
    public static Region regionOf(long worldX, long worldZ, int regionBits) {
        if (regionBits < 0 || regionBits > 63) {
            throw new IllegalArgumentException("regionBits must be in [0, 63]: " + regionBits);
        }
        return new Region(worldX >> regionBits, worldZ >> regionBits);
    }

    /**
     * Decides whether charging {@code units} against the per-tick budget stays within <em>all three</em> caps,
     * with the same overflow-free comparison as {@code engine.sim.budget.BudgetScheduler#charge}
     * ({@code units > cap - used}): {@code true} (= ACCEPTED) iff every {@code units <= cap - used}; otherwise
     * {@code false} (= DEFERRED, nothing recorded). Pure and deterministic.
     * / 判定对（simulant, region, global）三 tier 同时计费 {@code units} 是否全部在 cap 内，采用与
     * {@code engine.sim.budget.BudgetScheduler#charge} 相同的无溢出比较（{@code units > cap - used}）：全部
     * {@code units <= cap - used} 时返回 {@code true}（= ACCEPTED），否则 {@code false}（= DEFERRED，不记账）。纯函数且确定性。
     *
     * @param simulantCap the per-simulant cap ({@code >= 1}).
     * @param regionCap   the per-region cap ({@code >= 1}).
     * @param globalCap   the per-tick global cap ({@code >= 1}).
     * @param simUsed     the units already charged to this simulant this tick ({@code >= 0}).
     * @param regionUsed  the units already charged to this region this tick ({@code >= 0}).
     * @param globalUsed  the units already charged globally this tick ({@code >= 0}).
     * @param units       the units to charge ({@code > 0}).
     * @return {@code true} if all three tiers stay within cap, else {@code false}.
     * @throws IllegalArgumentException if any cap is {@code < 1}, {@code units <= 0}, or any {@code used} is negative.
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

    /**
     * Derives the cell holding a world position by right-shifting both coordinates by {@code cellBits}, with the
     * same semantics as {@code engine.sim.spatial.CellCoord#of}. Pure and deterministic.
     * / 以 {@code cellBits} 对世界坐标对右移折叠出所在 cell，与 {@code engine.sim.spatial.CellCoord#of}
     * 同语义。纯函数且确定性。
     *
     * @param worldX   the world X coordinate.
     * @param worldZ   the world Z coordinate.
     * @param cellBits the cell granularity in bits; {@code [0, 63]}.
     * @return the cell containing {@code (worldX, worldZ)}.
     * @throws IllegalArgumentException if {@code cellBits} is outside {@code [0, 63]}.
     */
    public static Cell cellOf(long worldX, long worldZ, int cellBits) {
        if (cellBits < 0 || cellBits > 63) {
            throw new IllegalArgumentException("cellBits must be in [0,63], was " + cellBits);
        }
        return new Cell(worldX >> cellBits, worldZ >> cellBits);
    }

    /**
     * Enumerates the {@code (2·radius+1)²} Chebyshev window cells around a center cell, in the same fixed
     * traversal order as {@code engine.sim.spatial.SpatialIndex#query} (dx outer, dz inner). Deterministic;
     * cost is proportional to the window size, never a global scan.
     * / 枚举中心 cell 周围 {@code (2·radius+1)²} 的 Chebyshev 窗口 cell，遍历序与
     * {@code engine.sim.spatial.SpatialIndex#query} 相同（外层 dx、内层 dz）。确定性；开销正比于窗口大小，
     * 绝不做全局扫描。
     *
     * @param centerX   the center cell's X index.
     * @param centerZ   the center cell's Z index.
     * @param radius    the Chebyshev window radius ({@code >= 0}).
     * @return the window cells in deterministic order.
     * @throws IllegalArgumentException if {@code radius} is negative.
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

    /**
     * The strictly per-simulant-per-tick seed, reproduced verbatim from
     * {@code engine.sim.core.PerSimRandom#deriveSeed} (its documented multiply-xorshift avalanche over the
     * {@code (worldSeed, tickNo, x, z)} triple). Identical inputs always yield the identical {@code long};
     * this is the byte-determinism basis of the incremental diff/replay contract.
     * / 严格 per-simulant-per-tick 的种子，逐字复现 {@code engine.sim.core.PerSimRandom#deriveSeed}
     * （对 {@code (worldSeed, tickNo, x, z)} 三元组的文档化乘-异或-移位雪崩）。同输入恒得同 {@code long}；
     * 是增量 diff/回放逐字节一致契约的确定性基底。
     *
     * @param worldSeed the master world seed.
     * @param tickNo    the tick the simulant advances to.
     * @param x         the simulant X coordinate.
     * @param z         the simulant Z coordinate.
     * @return the deterministic {@code long} seed.
     */
    public static long simSeed(long worldSeed, long tickNo, long x, long z) {
        long a = worldSeed
                ^ Long.rotateLeft(tickNo * KT, 11)
                ^ (x * KX)
                ^ Long.rotateLeft(z * KZ, 17);
        long b = mix(a);
        return mix(b ^ Long.rotateRight(a, 29));
    }

    // ---- PerSimRandom.deriveSeed constants (pinned verbatim from the engine). ----
    private static final long KX = 0xD1342543DE82EF95L;
    private static final long KZ = 0x2545F4914F6CDD1DL;
    private static final long KT = 0xA5A5CAFF53A04C0BL;

    /** The multiply-xorshift avalanche finalizer, identical to {@code PerSimRandom#mix}. */
    private static long mix(long x) {
        x ^= x >>> 32;
        x *= 0xD6E8FEB86659FD93L;
        x ^= x >>> 28;
        x *= 0xE7037ED1A0B428DBL;
        x ^= x >>> 32;
        x *= 0x8EABC8B3F17D0F39L;
        return x ^ (x >>> 26);
    }
}