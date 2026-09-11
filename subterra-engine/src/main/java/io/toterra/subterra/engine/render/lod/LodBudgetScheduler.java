package io.toterra.subterra.engine.render.lod;

import io.toterra.subterra.engine.sim.budget.BudgetScheduler;
import io.toterra.subterra.engine.sim.budget.RegionKey;
import io.toterra.subterra.engine.sim.budget.TickBudget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic budget batch scheduler for the LOD view plan (p.2.28.3): a <b>thin adapter</b>
 * over the p.2.8 {@code engine.sim.budget} kernel ({@link BudgetScheduler}/{@link TickBudget}/
 * {@link RegionKey}) — it reuses that kernel's atomic three-tier accounting and never copies its
 * implementation. Semantics align with both p.2.8 (fresh-scheduler-same-stream determinism,
 * overflow-free cap tests) and p.2.28.2's {@link LodPipeline#rebuildBatch} budget parameter: a
 * batch is the fixed-order subset of candidate cells that fits every tier's cap, and the overflow
 * is deferred deterministically.
 *
 * <p>Mapping onto the kernel's tiers: the per-simulant key is the projected {@link LodLevel} so
 * the kernel's per-simulant cap becomes the <b>per-level cap</b>; the region key derives from the
 * world chunk coordinate so {@link RegionKey} cap becomes the <b>per-region cap</b>; the global cap
 * bounds the whole batch. Each candidate cell submits {@code charge(level, regionOf(chunk), units)}
 * where {@code units = blockSpan²} is the cell's area cost (coarser level = larger area = heavier).
 * Cells are consumed in a caller-chosen <b>fixed</b> order (the view plan sorts candidates
 * deterministically); on {@code DEFERRED} the cell is skipped and a deterministic
 * {@code downgradeTo} hint (the next coarser level, or {@code null} at the top) is produced — the
 * over-budget skip is therefore a pure function of the fixed key order, like p.2.8
 * {@code BudgetScheduler#charge}.
 *
 * <p>LOD 视景计划的确定性预算批次调度器（p.2.28.3）：p.2.8 {@code engine.sim.budget} 内核
 * （{@link BudgetScheduler}/{@link TickBudget}/{@link RegionKey}）之上的<b>薄适配</b>——复用其原子三
 * tier 记账，绝不复制其实现。语义同时对齐 p.2.8（全新调度器-同序流确定性、无溢出上限比较）与 p.2.28.2
 * {@link LodPipeline#rebuildBatch} 的预算参数：一个批次即满足每 tier 上限的候选小区固定序子集，溢出被
 * 确定性推迟。
 *
 * <p>到内核 tier 的映射：per-simulant 键取投影 {@link LodLevel}，故内核 per-simulant 上限即<b>每级
 * 上限</b>；区域键由世界区块坐标派生，故 {@link RegionKey} 上限即<b>每区域上限</b>；全局上限界整个批次。
 * 每个候选小区提交 {@code charge(level, regionOf(chunk), units)}，其中 {@code units = blockSpan²} 为该
 * 小区面积成本（越粗层级面积越大、成本越重）。小区以调用方所选<b>固定</b>序消费（视景计划对候选确定性排序）；
 * 遇 {@code DEFERRED} 跳过并产出确定性 {@code downgradeTo} 提示（次粗层级，或顶层为 {@code null}）——
 * 因此超预算跳过是固定键序的纯函数，同 p.2.8 {@code BudgetScheduler#charge}。
 */
public final class LodBudgetScheduler {

    private final TickBudget budget;
    private final int regionBits;

    private LodBudgetScheduler(TickBudget budget, int regionBits) {
        this.budget = budget;
        this.regionBits = regionBits;
    }

    /**
     * Creates the adapter.
     *
     * @param budget     the p.2.8 tick budget caps (all {@code >= 1}).
     * @param regionBits region bit shift for chunk coords ({@code [0, 63]}); reuses the kernel's
     *     {@link BudgetScheduler#regionOf(long, long)} semantics.
     * @throws NullPointerException     if {@code budget} is null.
     * @throws IllegalArgumentException if {@code regionBits} is outside {@code [0, 63]}.
     */
    public static LodBudgetScheduler create(TickBudget budget, int regionBits) {
        Objects.requireNonNull(budget, "budget must not be null");
        if (regionBits < 0 || regionBits > 63) {
            throw new IllegalArgumentException("regionBits must be in [0, 63]: " + regionBits);
        }
        return new LodBudgetScheduler(budget, regionBits);
    }

    /**
     * Maps a world (block) coordinate pair to its budget region via the kernel's
     * {@link BudgetScheduler#regionOf(long, long)} semantics (right-shift by regionBits).
     * / 用内核 {@link BudgetScheduler#regionOf(long, long)} 语义把世界（方块）坐标对映射到预算区域
     * （右移 regionBits）。
     */
    public RegionKey regionOf(long worldBlockX, long worldBlockZ) {
        return fresh().regionOf(worldBlockX, worldBlockZ);
    }

    /**
     * One budgeted cell request: a projected {@link LodLevel}, its chunk coordinate and the
     * area cost {@code units}. / 一个预算小区请求：投影 {@link LodLevel}、其区块坐标与面积成本
     * {@code units}。
     */
    public record CellRequest(LodLevel level, long chunkX, long chunkZ, long units) {
        public CellRequest {
            Objects.requireNonNull(level, "level must not be null");
            if (units <= 0) {
                throw new IllegalArgumentException("units must be > 0: " + units);
            }
        }
    }

    /**
     * Outcome of one cell: {@code included} is whether its charge was {@code ACCEPTED};
     * {@code downgradeTo} is the deterministic next-coarser {@link LodLevel} when skipped
     * ({@code null} on accept or at the top level). / 单小区结果：{@code included} 为其计费是否
     * {@code ACCEPTED}；{@code downgradeTo} 为被跳过时的确定性次粗 {@link LodLevel}（接受或已在顶层时为
     * {@code null}）。
     */
    public record CellOutcome(CellRequest request, boolean included, LodLevel downgradeTo) {
    }

    /**
     * Deterministic result of a budgeted batch over a fixed-order candidate list. / 对固定序候选列表做
     * 预算化批次的确定性结果。
     */
    public record BatchPlan(
            List<CellOutcome> outcomes,
            List<LodLevel> chargedLevels,
            List<RegionKey> deferredRegions,
            long globalUnitsUsed,
            int includedCount) {

        /** The accepted (non-skipped) outcomes, in fixed order. / 已接受（未跳过）结果，固定序。 */
        public List<CellOutcome> included() {
            List<CellOutcome> out = new ArrayList<>(includedCount);
            for (CellOutcome o : outcomes) {
                if (o.included()) {
                    out.add(o);
                }
            }
            return List.copyOf(out);
        }
    }

    /**
     * Runs a deterministic budgeted batch over {@code requests} in their given <b>fixed</b>
     * order: each non-broken candidate is charged against all three tiers atomically via the
     * p.2.8 kernel; {@code ACCEPTED} cells are included, {@code DEFERRED} cells are skipped with
     * a {@code downgradeTo} hint. Every call uses a fresh {@link BudgetScheduler}, so the outcome
     * is a pure function of {@code (budget, requests)} — same input, same output. The returned
     * {@link BatchPlan#includedCount()} is the deterministic batch size to hand
     * {@link LodPipeline#rebuildBatch} as its budget parameter.
     *
     * @param requests the candidate cells in fixed order; a {@code null} element is silently
     *     skipped deterministically.
     * @return the {@link BatchPlan}.
     * @throws NullPointerException if {@code requests} is null.
     */
    public BatchPlan planBatch(List<CellRequest> requests) {
        Objects.requireNonNull(requests, "requests must not be null");
        BudgetScheduler<LodLevel> s = fresh();
        List<CellOutcome> outcomes = new ArrayList<>(requests.size());
        int included = 0;
        for (CellRequest r : requests) {
            if (r == null) {
                continue;
            }
            RegionKey region = s.regionOf(r.chunkX() * 16, r.chunkZ() * 16);
            boolean accepted = s.charge(r.level(), region, r.units()) == BudgetScheduler.ChargeResult.ACCEPTED;
            LodLevel downgrade = null;
            if (!accepted && r.level().ordinal() < LodLevel.L5.ordinal()) {
                downgrade = LodLevel.values()[r.level().ordinal() + 1];
            }
            if (accepted) {
                included++;
            }
            outcomes.add(new CellOutcome(r, accepted, downgrade));
        }
        return new BatchPlan(List.copyOf(outcomes),
                List.copyOf(s.chargedSimulants()),
                List.copyOf(s.deferredRegions()),
                s.globalUnitsUsed(),
                included);
    }

    private BudgetScheduler<LodLevel> fresh() {
        return BudgetScheduler.create(budget, regionBits);
    }
}