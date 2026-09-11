package io.toterra.subterra.engine.render.lod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Facade of the LOD generation pipeline (p.2.28.2, clean-room self-developed):
 * {@link #generate} turns a deterministic {@link LodPolygonizer.LodRegionSample} into an L0
 * {@link LodSection}; {@link #merge} combines four level-{@code k} child sections into one
 * level-{@code k+1} parent; {@link #rebuildBatch} splits an arbitrary dirty set (as ordered by
 * {@link LodDirtyTracker}) into at most {@code budget} cells processed this batch in a
 * <b>fixed</b> block order, deferring the overflow to the next batch — deterministic, bounded,
 * no timing. The pipeline swaps the JDK golden kernels for the byte-identical tie primitives
 * when a tie library is present (see {@link LodTieAccelerator}); without one it degrades to a
 * deterministic JDK-only path (never throws, never changes output bytes).
 *
 * <p>Batch semantics inherit p.2.7's bounded-batch / key-order-merge ideas and p.2.8's budget
 * idea: the batch is a pure partition of the <em>ordered</em> dirty set by the budget — exactly
 * the first {@code min(budget, n)} cells in the deterministic fixed order are included, the
 * rest are deferred. No wall-clock, no scheduling state; the same set and the same budget always
 * give the same boundary.
 *
 * <p>{@code engine.render.lod} 生成门面（p.2.28.2，clean-room 自研）：{@link #generate} 把确定性
 * {@link LodPolygonizer.LodRegionSample} 转为 L0 {@link LodSection}；{@link #merge} 把四个 level-{@code k}
 * 子段合并为一个 level-{@code k+1} 父段；{@link #rebuildBatch} 把任意脏集合（按 {@link LodDirtyTracker}
 * 之序）切分为本批次最多 {@code budget} 个单元、按<b>固定</b>方块序处理，溢出留待下一批——确定性、有界、无时序。
 * 当 tie 库在场时管线把 JDK 金样内核换成逐字节一致的 tie 原语（见 {@link LodTieAccelerator}）；缺省无库则
 * 退化到仅 JDK 的确定性路径（绝不抛错、绝不改变输出字节）。
 *
 * <p>批次语义承接 p.2.7 的有界批次/键序归并思想与 p.2.8 的预算思想：批次即按预算对<em>有序</em>脏集合的纯
 * 划分——恰好取确定性固定序的前 {@code min(budget, n)} 个单元，其余推迟。无墙钟、无调度状态；相同集合与相同
 * 预算恒给相同边界。
 */
public final class LodPipeline {

    private final LodTieAccelerator accelerator;

    /**
     * Creates a pipeline with an optional tie accelerator (may be skipped). / 构造带可选 tie 加速器
     * （可为跳过态）的管线。
     */
    public LodPipeline(LodTieAccelerator accelerator) {
        this.accelerator = accelerator;
    }

    /** Creates a pipeline with the accelerator resolved from the {@code subterra.tie.lib} property. /
     *  以 {@code subterra.tie.lib} 属性解析加速器构造管线。 */
    public LodPipeline() {
        this(LodTieAccelerator.fromProperties());
    }

    /**
     * Generates the level-0 section. If the tie accelerator is active its
     * {@code lod$profile_top} primitive supersedes the JDK kernel; otherwise the JDK golden
     * kernel {@link LodPolygonizer#profileTop} is used. Both paths are byte-identical.
     * / 生成 level-0 区块。tie 加速器在场时其 {@code lod$profile_top} 原语取代 JDK 内核；否则用 JDK 金样
     * 内核 {@link LodPolygonizer#profileTop}。两路径逐字节一致。
     */
    public LodSection generate(LodPolygonizer.LodRegionSample sample) {
        Objects.requireNonNull(sample, "sample must not be null");
        if (accelerator != null && accelerator.active()) {
            LodPolygonizer.TopSampler tieSampler = (s, x, z) ->
                    accelerator.profileTop(s, x, z).orElseThrow();
            return LodPolygonizer.generate(
                    new LodPolygonizer.LodRegionSample(sample.blockSpan(), sample.seed(), tieSampler));
        }
        return LodPolygonizer.generate(sample);
    }

    /**
     * Merges four level-{@code k} children into a level-{@code k+1} parent, using the tie
     * {@code lod$merge_corner} when active and the JDK golden kernel otherwise (identical bytes).
     * / 把四个 level-{@code k} 子段合并为 level-{@code k+1} 父段，tie 在场用 {@code lod$merge_corner}，
     * 否则用 JDK 金样内核（字节一致）。
     */
    public LodSection merge(int parentOriginBlockX, int parentOriginBlockZ,
                            LodSection a, LodSection b, LodSection c, LodSection d) {
        if (accelerator != null && accelerator.active()) {
            return LodMergerWithTie.merge(parentOriginBlockX, parentOriginBlockZ, a, b, c, d, accelerator);
        }
        return LodMerger.merge(parentOriginBlockX, parentOriginBlockZ, a, b, c, d);
    }

    /**
     * Deterministic bounded-batch rebuild split: given an arbitrary dirty set (deduplicated and
     * ordered by {@link LodDirtyTracker.Cell#compareTo}), returns an {@link LodRebuildBatch} whose
     * {@code included} holds the first {@code min(budget, n)} cells (fixed block order) and whose
     * {@code deferred} holds the remainder. A non-positive {@code budget} yields an empty included
     * batch and defers everything. Pure; no timing.
     * / 确定性有界批次重组切分：给定任意脏集合（去重并按 {@link LodDirtyTracker.Cell#compareTo} 排序），返回
     * {@link LodRebuildBatch}，其 {@code included} 为前 {@code min(budget, n)} 个单元（固定方块序），
     * {@code deferred} 为其余。非正 {@code budget} 得空 included 批次并全部推迟。纯函数；无时序。
     */
    public LodRebuildBatch rebuildBatch(Collection<LodDirtyTracker.Cell> dirty, int budget) {
        List<LodDirtyTracker.Cell> ordered = new ArrayList<>(dirty);
        ordered.sort(null);
        // dedup while preserving fixed order
        List<LodDirtyTracker.Cell> dedup = new ArrayList<>(ordered.size());
        for (LodDirtyTracker.Cell cell : ordered) {
            if (dedup.isEmpty() || !dedup.get(dedup.size() - 1).equals(cell)) {
                dedup.add(cell);
            }
        }
        int cap = Math.max(0, budget);
        int take = Math.min(cap, dedup.size());
        List<LodDirtyTracker.Cell> included = new ArrayList<>(dedup.subList(0, take));
        List<LodDirtyTracker.Cell> deferred = new ArrayList<>(dedup.subList(take, dedup.size()));
        return new LodRebuildBatch(included, deferred);
    }

    /**
     * A budgeted rebuild split: {@code included} (fixed block order, size ≤ budget) vs
     * {@code deferred} (the remaining cells). / 预算化重组切分：{@code included}（固定方块序，size ≤
     * budget）对 {@code deferred}（其余单元）。
     */
    public record LodRebuildBatch(List<LodDirtyTracker.Cell> included,
                                  List<LodDirtyTracker.Cell> deferred) {
    }

    /**
     * Delegates the 2×2 corner merge to the tie primitive, wiring the four child packed cell
     * values and reassembling the parent columns/quads with the same fixed meshing as the JDK
     * path. Byte-identical to {@link LodMerger#merge}. / 把 2×2 corner 合并委托给 tie 原语，接好四个子
     * 段打包单元值，并以与 JDK 路径相同的固定网格化重建父段列/quad。与 {@link LodMerger#merge} 逐字节一致。
     */
    private static final class LodMergerWithTie {
        static LodSection merge(int pox, int poz,
                                LodSection a, LodSection b, LodSection c, LodSection d,
                                LodTieAccelerator acc) {
            LodLevel childLevel = a.level();
            int childSpan = childLevel.blockSpan();
            int parentSpan = childSpan * 2;
            LodLevel parentLevel = LodLevel.values()[childLevel.ordinal() + 1];

            long[][] pa = packedGrid(a), pb = packedGrid(b), pc = packedGrid(c), pd = packedGrid(d);
            int[] height = new int[parentSpan * parentSpan];
            int[] color = new int[parentSpan * parentSpan];
            int[] bright = new int[parentSpan * parentSpan];
            for (int px = 0; px < parentSpan; px++) {
                for (int pz = 0; pz < parentSpan; pz++) {
                    int rx = px & (childSpan - 1);
                    int rz = pz & (childSpan - 1);
                    long winner = acc.mergeCorner(pa[rx][rz], pb[rx][rz], pc[rx][rz], pd[rx][rz])
                            .orElseThrow();
                    int i = LodPolygonizer.idx(parentSpan, px, pz);
                    height[i] = LodPolygonizer.LodRegionSample.heightOf(winner);
                    color[i] = LodPolygonizer.LodRegionSample.colorOf(winner);
                    bright[i] = LodPolygonizer.LodRegionSample.brightOf(winner);
                }
            }
            List<LodColumnStack> cols = LodPolygonizer.buildColumns(parentSpan, height, color);
            List<LodQuad> quads = LodPolygonizer.meshQuads(parentSpan, height, color, bright);
            LodSection.Builder sb = LodSection.builder();
            sb.level(parentLevel).originBlockX(pox).originBlockZ(poz);
            cols.forEach(sb::column);
            quads.forEach(sb::quad);
            return sb.build();
        }

        static long[][] packedGrid(LodSection s) {
            int span = s.level().blockSpan();
            long[][] grid = new long[span][span];
            for (LodColumnStack col : s.columnStacks()) {
                LodColumnStack.LodProfileEntry top = col.entries().get(col.entries().size() - 1);
                grid[col.x()][col.z()] = LodPolygonizer.LodRegionSample.pack(top.yHigh(), top.colorIndex(), 0);
            }
            for (LodQuad q : s.quads()) {
                for (int dx = 0; dx < q.size(); dx++) {
                    for (int dz = 0; dz < q.size(); dz++) {
                        long base = grid[q.originX() + dx][q.originZ() + dz];
                        int h = LodPolygonizer.LodRegionSample.heightOf(base);
                        int c = LodPolygonizer.LodRegionSample.colorOf(base);
                        grid[q.originX() + dx][q.originZ() + dz] =
                                LodPolygonizer.LodRegionSample.pack(h, c, q.facingBits() & 0x0F);
                    }
                }
            }
            return grid;
        }
    }
}