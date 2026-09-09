// Self-contained clean-room implementation of the p.2.8.1 tick-budget tiering
// contract (engine.sim.budget). No net.minecraft / net.neoforged dependency.
package io.toterra.subterra.engine.sim.budget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Deterministic three-tier tick-budget scheduler over comparable simulant keys
 * and {@link RegionKey} regions. Each tick a caller repeatedly asks to
 * {@link #charge(K, RegionKey, long)} a number of units against a (simulant,
 * region) pair. The charge is accounted against all three tiers at once — the
 * per-simulant cap, the per-region cap, and the per-tick global cap — and:
 *
 * <ul>
 *   <li>if <em>every</em> tier would stay within its cap, all three tiers are
 *       recorded and {@link ChargeResult#ACCEPTED} is returned; or</li>
 *   <li>if <em>any</em> tier would exceed its cap, the whole request is rejected
 *       {@link ChargeResult#DEFERRED} and nothing is recorded (atomic
 *       all-or-nothing), and the affected region is flagged in
 *       {@link #deferredRegions()}.</li>
 * </ul>
 *
 * <p><b>Determinism.</b> {@link #charge(K, RegionKey, long)} is a pure function of
 * the current budget state, the simulant key, the region, and the units. State
 * advances only on {@code ACCEPTED} calls, in call order; the decision never
 * depends on wall-clock, thread timing, or iteration order. Given the same
 * request sequence (in key order) a fresh scheduler produces the identical
 * accept/defer decision stream, and both {@link #chargedSimulants()} and
 * {@link #deferredRegions()} are emitted in deterministic natural order. This is
 * the forward sentinel for the p.2.26 {@code engine.time.BudgetScheduler}, so the
 * ABI here is aligned with that future surface.
 *
 * <p>Overflow safety: per-tier usage is bounded by {@link TickBudget#simulantUnits()}
 * etc., so all over-cap tests are evaluated as {@code units > cap - used} — an
 * overflow-free comparison — with no {@code long}+ arithmetic that could wrap.
 *
 * <p>p.2.8.1 可比较 simulant 键与 {@link RegionKey} 区域之上的确定性三级每 tick 预算调度器。
 * 每 tick 调用方反复对某个 (simulant, region) 对 {@link #charge(K, RegionKey, long)}
 * 计费若干单位。计费会三 tier 同时核对——per-simulant 上限、per-region 上限、每 tick 全局
 * 上限——并且：
 *
 * <ul>
 *   <li>若<em>所有</em> tier 都不超过其上限：三 tier 同时记账并返回
 *       {@link ChargeResult#ACCEPTED}；或</li>
 *   <li>若<em>任一</em> tier 会超上限：整笔请求被拒为 {@link ChargeResult#DEFERRED}
 *       且完全不记账（原子全有或全无），并会把受影响区域记入
 *       {@link #deferredRegions()}。</li>
 * </ul>
 *
 * <p><b>确定性。</b> {@link #charge(K, RegionKey, long)} 是当前预算状态、simulant 键、区域、
 * 单位数量的纯函数。状态只随 {@code ACCEPTED} 调用按调用序推进；决策绝不依赖墙钟、线程时序
 * 或迭代顺序。给定同一请求序列（按 key 序），全新调度器会产出逐位一致的 accept/defer 决策
 * 流，且 {@link #chargedSimulants()} 与 {@link #deferredRegions()} 均按确定性自然序输出。
 * 这是 p.2.26 {@code engine.time.BudgetScheduler} 的前哨，因此此处 ABI 与该未来接口对齐。
 *
 * <p>溢出安全：各 tier 用量受 {@link TickBudget#simulantUnits()} 等约束，故所有超限判断都以
 * {@code units > cap - used} 形式求值——一种无溢出的比较——不出现可能回绕的 {@code long}+ 运算。
 *
 * @param <K> the comparable simulant key type.
 */
public final class BudgetScheduler<K extends Comparable<K>> {

    /**
     * Outcome of a single {@link #charge(K, RegionKey, long)} request.
     */
    public enum ChargeResult {
        /** All three tiers stayed within their caps; the units were recorded. */
        ACCEPTED,

        /** At least one tier would have been exceeded; nothing was recorded. */
        DEFERRED
    }

    private final TickBudget budget;
    private final int regionBits;
    private final long simulantUnits;
    private final long regionUnits;
    private final long globalUnits;

    /** Per-simulant used units keyed naturally so {@link #chargedSimulants()} is deterministic. */
    private final TreeMap<K, Long> simulantUsed = new TreeMap<>();
    /** Per-region used units. */
    private final TreeMap<RegionKey, Long> regionUsed = new TreeMap<>();
    /** Regions deferred at least once this tick, naturally ordered and deduplicated. */
    private final TreeSet<RegionKey> deferredRegions = new TreeSet<>();
    /** Per-tick global used units. */
    private long globalUsed = 0L;

    private BudgetScheduler(TickBudget budget, int regionBits) {
        this.budget = budget;
        this.regionBits = regionBits;
        this.simulantUnits = budget.simulantUnits();
        this.regionUnits = budget.regionUnits();
        this.globalUnits = budget.globalUnits();
    }

    /**
     * Creates a scheduler.
     *
     * @param budget     the tick budget caps; must be non-null and each cap {@code >= 1}.
     * @param regionBits how many low bits the world coordinates are shifted away to form
     *                   a region; must be in {@code [0, 63]}.
     * @return a new scheduler with empty per-tick state.
     * @throws IllegalArgumentException if {@code regionBits} is outside {@code [0, 63]}.
     * @throws NullPointerException     if {@code budget} is null.
     */
    public static <K extends Comparable<K>> BudgetScheduler<K> create(TickBudget budget, int regionBits) {
        Objects.requireNonNull(budget, "budget");
        if (regionBits < 0 || regionBits > 63) {
            throw new IllegalArgumentException("regionBits must be in [0, 63]: " + regionBits);
        }
        return new BudgetScheduler<>(budget, regionBits);
    }

    /**
     * Maps a world coordinate pair to its region by right-shifting both coordinates
     * by the configured {@code regionBits}. Arithmetic shift sign-extends, so
     * negative coordinates map correctly (adjacent cell on the negative side).
     * A deterministic pure function of {@code (worldX, worldZ)} alone.
     *
     * @param worldX the world X coordinate.
     * @param worldZ the world Z coordinate.
     * @return the resulting {@link RegionKey}.
     */
    public RegionKey regionOf(long worldX, long worldZ) {
        return new RegionKey(worldX >> regionBits, worldZ >> regionBits);
    }

    /**
     * Charges {@code units} against the (simulant, region) pair across all three
     * tiers atomically. A {@code units <= 0} request is rejected with
     * {@link IllegalArgumentException} before any state is touched. Otherwise the
     * decision is a pure function of the current budget state, the key, the region
     * and the units: if every tier stays within cap it returns ACCEPTED and records
     * all three; if any tier would exceed cap it returns DEFERRED without recording
     * anything (the region is flagged in {@link #deferredRegions()}).
     *
     * @param simulantKey the simulant key (non-null).
     * @param region      the region key (non-null).
     * @param units       the number of units to charge; must be {@code > 0}.
     * @return {@link ChargeResult#ACCEPTED} if recorded, {@link ChargeResult#DEFERRED} otherwise.
     * @throws IllegalArgumentException if {@code units <= 0}.
     * @throws NullPointerException     if either key is null.
     */
    public ChargeResult charge(K simulantKey, RegionKey region, long units) {
        Objects.requireNonNull(simulantKey, "simulantKey");
        Objects.requireNonNull(region, "region");
        if (units <= 0) {
            throw new IllegalArgumentException("units must be > 0: " + units);
        }
        long usedSim = simulantUsed.getOrDefault(simulantKey, 0L);
        long usedReg = regionUsed.getOrDefault(region, 0L);
        // Overflow-free tests: used is always within its cap, so cap - used >= 0.
        if (units > simulantUnits - usedSim
                || units > regionUnits - usedReg
                || units > globalUnits - globalUsed) {
            deferredRegions.add(region);
            return ChargeResult.DEFERRED;
        }
        simulantUsed.put(simulantKey, usedSim + units);
        regionUsed.put(region, usedReg + units);
        globalUsed += units;
        return ChargeResult.ACCEPTED;
    }

    /**
     * @return the keys accepted this tick, in natural (ascending key) order.
     */
    public List<K> chargedSimulants() {
        return new ArrayList<>(simulantUsed.keySet());
    }

    /**
     * @return the regions deferred at least once this tick, deduplicated and in
     *         natural (ascending x, then z) order.
     */
    public List<RegionKey> deferredRegions() {
        return new ArrayList<>(deferredRegions);
    }

    /**
     * @return the total units accepted across all tiers this tick.
     */
    public long globalUnitsUsed() {
        return globalUsed;
    }
}