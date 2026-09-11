package io.toterra.subterra.engine.render.lod;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic immutable view snapshot (p.2.28.3): given a fixed {@link LodDistanceSelector},
 * a {@link LodViewCuller.ViewFrustum}, a player chunk coordinate and a set of candidate chunk
 * coordinates, it produces one immutable {@link ViewPlan} in which every candidate is assigned
 * (in fixed sorted order):
 *
 * <ul>
 *   <li>an expected {@link LodLevel} (from {@link LodDistanceSelector});</li>
 *   <li>a frustum {@code geometryCulled} flag (from {@link LodViewCuller#passesFrustum});</li>
 *   <li>a budget {@code includedInBatch} decision and deterministic {@code downgradeTo} hint
 *       (from {@link LodBudgetScheduler#planBatch}).</li>
 * </ul>
 *
 * The candidates are first sorted by {@code (x, z)} so every stage iterates in one fixed order,
 * and all arithmetic is integer. Same inputs always give a byte-identical plan (same-input /
 * same-output). {@link #budgetQuota()} is the deterministic batch size to pass to
 * {@link LodPipeline#rebuildBatch}'s budget parameter.
 *
 * <p>A reusable planner ({@link ViewPlanner}) with pre-sized buffers is provided so repeated
 * planning does not reallocate (memory-first), and results are exported as either an immutable
 * snapshot or read directly from the planner's fixed buffers.
 *
 * <p>确定性不可变视景快照（p.2.28.3）：给定固定 {@link LodDistanceSelector}、
 * {@link LodViewCuller.ViewFrustum}、玩家区块坐标与一组候选区块坐标，产出一个不可变 {@link ViewPlan}，
 * 其中每个候选（按固定排序序）被赋予：
 *
 * <ul>
 *   <li>期望 {@link LodLevel}（来自 {@link LodDistanceSelector}）；</li>
 *   <li>视锥剔除 {@code geometryCulled} 标志（来自 {@link LodViewCuller#passesFrustum}）；</li>
 *   <li>预算 {@code includedInBatch} 决策与确定性 {@code downgradeTo} 提示
 *       （来自 {@link LodBudgetScheduler#planBatch}）。</li>
 * </ul>
 *
 * 候选先按 {@code (x, z)} 排序使每阶段固定序遍历，全部为整数运算。同输入恒得逐字节一致的计划（同输入同输出）。
 * {@link #budgetQuota()} 为传给 {@link LodPipeline#rebuildBatch} 预算参数的确定性批次大小。
 *
 * <p>提供带预分配缓冲的可复用规划器（{@link ViewPlanner}）使多次规划不重新分配（内存优先），结果既可作为不可变
 * 快照导出，也可直接从规划器的固定缓冲读取。
 *
 * @see #plan
 */
public final class ViewPlan {

    /** Block size of a chunk. / 一个区块的方块数。 */
    private static final int BLOCKS_PER_CHUNK = 16;

    private final List<Coord> coords;
    private final byte[] levelOrdinals;
    private final boolean[] geometryCulled;
    private final boolean[] budgetIncluded;
    private final byte[] downgradeOrdinals; // -1 when none

    private ViewPlan(List<Coord> coords, byte[] levelOrdinals, boolean[] geometryCulled,
                     boolean[] budgetIncluded, byte[] downgradeOrdinals) {
        this.coords = List.copyOf(coords);
        this.levelOrdinals = levelOrdinals;
        this.geometryCulled = geometryCulled;
        this.budgetIncluded = budgetIncluded;
        this.downgradeOrdinals = downgradeOrdinals;
    }

    /**
     * A chunk coordinate, naturally ordered by x then z. / 一个区块坐标，按 x 再 z 自然排序。
     */
    public record Coord(long x, long z) implements Comparable<Coord> {

        @Override
        public int compareTo(Coord other) {
            Objects.requireNonNull(other, "other");
            int c = Long.compare(x, other.x);
            return c != 0 ? c : Long.compare(z, other.z);
        }
    }

    /**
     * Builds the full deterministic plan. Candidates are deduplicated and sorted by
     * {@code (x, z)}; for each in that fixed order the projected level, frustum cull and budget
     * decision are computed. The budget stage runs only over non-frustum-culled candidates after
     * sorting. {@code downgradeTo} is produced only for budget-skipped (over-budget) cells.
     *
     * @param distanceSelector the fixed distance→level selector.
     * @param frustum          the fixed view frustum ({@code null} disables geometry culling).
     * @param playerChunkX, playerChunkZ the player chunk coordinate.
     * @param candidates       candidate chunk coordinates (any order; dedup+sort internally).
     * @param scheduler        the budget scheduler (billed only on kept cells).
     * @return an immutable {@link ViewPlan}.
     * @throws NullPointerException if {@code distanceSelector}, {@code candidates} or
     *     {@code scheduler} is null.
     */
    public static ViewPlan plan(LodDistanceSelector distanceSelector,
                                LodViewCuller.ViewFrustum frustum,
                                long playerChunkX, long playerChunkZ,
                                List<Coord> candidates,
                                LodBudgetScheduler scheduler) {
        Objects.requireNonNull(distanceSelector, "distanceSelector must not be null");
        Objects.requireNonNull(candidates, "candidates must not be null");
        Objects.requireNonNull(scheduler, "scheduler must not be null");
        ViewPlanner p = new ViewPlanner(distanceSelector, frustum, playerChunkX, playerChunkZ, scheduler);
        p.plan(candidates);
        return p.toPlan();
    }

    /** A reusable planner over fixed-sized buffers (memory-first reuse). / 固定缓冲的可复用规划器（内存优先复用）。 */
    public static final class ViewPlanner {

        private final LodDistanceSelector selector;
        private final LodViewCuller.ViewFrustum frustum;
        private final long playerChunkX;
        private final long playerChunkZ;
        private final LodBudgetScheduler scheduler;

        private final List<Coord> coords = new ArrayList<>();
        private final List<Byte> levelOrd = new ArrayList<>();
        private final List<Boolean> culled = new ArrayList<>();
        private final List<Boolean> included = new ArrayList<>();
        private final List<Byte> downgrade = new ArrayList<>();

        /**
         * Creates a reusable planner bound to fixed view parameters.
         * @param distanceSelector the fixed distance→level selector.
         * @param frustum          the fixed view frustum ({@code null} to skip geometry culling).
         * @param playerChunkX, playerChunkZ the player chunk coordinate.
         * @param scheduler        the budget scheduler.
         */
        public ViewPlanner(LodDistanceSelector distanceSelector,
                           LodViewCuller.ViewFrustum frustum,
                           long playerChunkX, long playerChunkZ,
                           LodBudgetScheduler scheduler) {
            this.selector = Objects.requireNonNull(distanceSelector, "distanceSelector");
            this.frustum = frustum;
            this.playerChunkX = playerChunkX;
            this.playerChunkZ = playerChunkZ;
            this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        }

        /**
         * Recomputes the plan for a new candidate set, reusing this planner's buffers (previous
         * results are cleared). Deterministic: sort → level → cull → budget, all in fixed order.
         * / 对新的候选集合重算计划，复用本规划器缓冲（清空先前结果）。确定性：排序→层级→剔除→预算，
         * 全程固定序。
         */
        public void plan(List<Coord> candidates) {
            Objects.requireNonNull(candidates, "candidates");
            coords.clear();
            levelOrd.clear();
            culled.clear();
            included.clear();
            downgrade.clear();
            // deterministic dedup + sort
            ArrayList<Coord> ordered = new ArrayList<>(candidates);
            ordered.sort(null);
            ArrayList<Coord> dedup = new ArrayList<>(ordered.size());
            for (Coord c : ordered) {
                if (dedup.isEmpty() || !dedup.get(dedup.size() - 1).equals(c)) {
                    dedup.add(c);
                }
            }
            List<LodBudgetScheduler.CellRequest> requests = new ArrayList<>(dedup.size());
            for (Coord c : dedup) {
                LodLevel level = selector.levelForChunk(playerChunkX, playerChunkZ, c.x(), c.z());
                boolean geom = frustum == null || passesFrustumFor(level, c);
                boolean budgeted = false;
                int downg = -1;
                if (geom) {
                    long blockSpan = (long) level.blockSpan();
                    long units = blockSpan * blockSpan;
                    requests.add(new LodBudgetScheduler.CellRequest(
                            level, c.x(), c.z(), units));
                    budgeted = true;
                }
                coords.add(c);
                levelOrd.add((byte) level.ordinal());
                culled.add(!geom);
                included.add(budgeted);
                downgrade.add((byte) -1); // filled after budget stage for skipped cells
            }
            // budget stage over kept cells (in the same fixed order)
            LodBudgetScheduler.BatchPlan batch = scheduler.planBatch(requests);
            int reqIdx = 0;
            for (int i = 0; i < coords.size(); i++) {
                if (!included.get(i)) {
                    continue; // geometry-culled -> not billed, no downgrade
                }
                LodBudgetScheduler.CellOutcome out = batch.outcomes().get(reqIdx++);
                if (!out.included()) {
                    included.set(i, false);
                    downgrade.set(i, out.downgradeTo() == null ? (byte) -1
                            : (byte) out.downgradeTo().ordinal());
                }
            }
        }

        private boolean passesFrustumFor(LodLevel level, Coord c) {
            // representative section AABB at that chunk's LOD cell, world-height tall
            int chunkSide = level.chunkSpan();
            return LodViewCuller.passesFrustum(frustum,
                    LodViewCuller.sectionBoundsFromChunk((int) c.x(), (int) c.z(), chunkSide));
        }

        /** Exports an immutable snapshot of the planner's current result. / 导出规划器当前结果的不可变快照。 */
        public ViewPlan toPlan() {
            return new ViewPlan(coords,
                    toByteArray(levelOrd), toBoolArray(culled),
                    toBoolArray(included), toByteArray(downgrade));
        }

        /** @return the fixed-order coordinates of the last plan. / 上次计划的固定序坐标。 */
        public List<Coord> coords() {
            return coords;
        }

        /** @return the expected level ordinal per coordinate (byte 0..5). / 每坐标期望层级序（byte 0..5）。 */
        public byte levelOrdinal(int i) {
            return levelOrd.get(i);
        }

        /** @return the expected level per coordinate. / 每坐标期望层级。 */
        public LodLevel level(int i) {
            return LodLevel.values()[levelOrd.get(i)];
        }

        /** @return whether coordinate {@code i} is geometry-culled (frustum). / 坐标 {@code i} 是否被视锥剔除。 */
        public boolean geometryCulled(int i) {
            return culled.get(i);
        }

        /** @return whether coordinate {@code i} was accepted into the budget batch. / 坐标 {@code i} 是否进入预算批次。 */
        public boolean includedInBatch(int i) {
            return included.get(i);
        }

        /** @return the budget-skip downgrade {@link LodLevel} for {@code i}, or {@code null} if none. /
         *  坐标 {@code i} 的预算跳过降级 {@link LodLevel}，无则 {@code null}。 */
        public LodLevel downgradeTo(int i) {
            byte d = downgrade.get(i);
            return d < 0 ? null : LodLevel.values()[d];
        }

        private static byte[] toByteArray(List<Byte> list) {
            byte[] a = new byte[list.size()];
            for (int i = 0; i < a.length; i++) {
                a[i] = list.get(i);
            }
            return a;
        }

        private static boolean[] toBoolArray(List<Boolean> list) {
            boolean[] a = new boolean[list.size()];
            for (int i = 0; i < a.length; i++) {
                a[i] = list.get(i);
            }
            return a;
        }
    }

    /** @return the fixed sorted coordinates of this plan. / 本计划的固定排序坐标。 */
    public List<Coord> coords() {
        return coords;
    }

    /** @return the size of the plan (number of candidate cells). / 计划规模（候选小区数）。 */
    public int size() {
        return coords.size();
    }

    /** @return the expected level of cell {@code i}. / 单元格 {@code i} 的期望级。 */
    public LodLevel level(int i) {
        return LodLevel.values()[levelOrdinals[i]];
    }

    /** @return whether cell {@code i} is geometry-culled by the frustum. / 单元格 {@code i} 是否被视锥剔除。 */
    public boolean geometryCulled(int i) {
        return geometryCulled[i];
    }

    /** @return whether cell {@code i} was included in the budget batch. / 单元格 {@code i} 是否进入预算批次。 */
    public boolean includedInBatch(int i) {
        return budgetIncluded[i];
    }

    /** @return the budget-skip downgrade level for {@code i}, or {@code null} if none. /
     *  单元格 {@code i} 的预算跳过降级级，无则 {@code null}。 */
    public LodLevel downgradeTo(int i) {
        byte d = downgradeOrdinals[i];
        return d < 0 ? null : LodLevel.values()[d];
    }

    /** @return how many cells were accepted into the budget batch (deterministic batch size). /
     *  进入预算批次的单元格数（确定性批次大小）。 */
    public int budgetQuota() {
        int n = 0;
        for (boolean b : budgetIncluded) {
            if (b) {
                n++;
            }
        }
        return n;
    }

    /**
     * The width in blocks of the {@code i}-th cell's standard section span.
     * / 第 {@code i} 个单元格标准区块跨度（方块宽）。
     */
    public int blockSpan(int i) {
        return level(i).blockSpan();
    }

    /**
     * Serializes the plan to a td table in fixed key order ({@code coords, levels, culled,
     * budget, downgrade}) with no timestamps, byte-stable for the same plan (p.2.28.3 
     * cn config view, mirroring {@link LodConfigDoc#toTd}). / 将计划按固定键序写为 td 表
     * （{@code coords, levels, culled, budget, downgrade}）、无时间戳，对同一计划逐字节稳定
     *（p.2.28.3 配置视角，镜像 {@link LodConfigDoc#toTd}）。
     */
    public TdTable toTd() {
        TdTable.Builder b = TdTable.builder();
        TdTable.Builder cb = TdTable.builder();
        for (Coord c : coords) {
            TdTable.Builder p = TdTable.builder();
            p.element(TdValue.of(c.x()));
            p.element(TdValue.of(c.z()));
            cb.element(p.build());
        }
        b.put("coords", cb.build());
        TdTable.Builder lb = TdTable.builder();
        for (int i = 0; i < coords.size(); i++) {
            lb.element(TdValue.of(levelOrdinals[i]));
        }
        b.put("levels", lb.build());
        TdTable.Builder gl = TdTable.builder();
        for (boolean v : geometryCulled) {
            gl.element(TdValue.of(v));
        }
        b.put("culled", gl.build());
        TdTable.Builder bl = TdTable.builder();
        for (boolean v : budgetIncluded) {
            bl.element(TdValue.of(v));
        }
        b.put("budget", bl.build());
        TdTable.Builder dl = TdTable.builder();
        for (byte d : downgradeOrdinals) {
            dl.element(TdValue.of(d));
        }
        b.put("downgrade", dl.build());
        return b.build();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof ViewPlan p
                && coords.equals(p.coords)
                && java.util.Arrays.equals(levelOrdinals, p.levelOrdinals)
                && java.util.Arrays.equals(geometryCulled, p.geometryCulled)
                && java.util.Arrays.equals(budgetIncluded, p.budgetIncluded)
                && java.util.Arrays.equals(downgradeOrdinals, p.downgradeOrdinals));
    }

    @Override
    public int hashCode() {
        int h = coords.hashCode();
        h = 31 * h + java.util.Arrays.hashCode(levelOrdinals);
        h = 31 * h + java.util.Arrays.hashCode(geometryCulled);
        h = 31 * h + java.util.Arrays.hashCode(budgetIncluded);
        return 31 * h + java.util.Arrays.hashCode(downgradeOrdinals);
    }
}