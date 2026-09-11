package io.toterra.subterra.engine.render.lod;

import java.util.List;
import java.util.Objects;

/**
 * Recursive mip merge of four same-level {@link LodSection} children into one coarser
 * {@link LodSection} (p.2.28.2, clean-room self-developed). Given the four level-{@code k}
 * quadrants {@code a}(\low x, low z), {@code b}(high x, low z), {@code c}(low x, high z),
 * {@code d}(high x, high z), it produces a level-{@code k+1} section whose block span doubles.
 * Each parent block column reduces the <b>2×2 corner</b> of the four children at the same
 * relative cell {@code (px & (span-1), pz & (span-1))} via {@link #mergeCorner(long, long,
 * long, long)}: the winner is the corner with the maximum top height, ties resolved by first
 * occurrence in the <b>pinned</b> corner order {@code a, b, c, d} (= {@code (x,z)} lexicographic
 * first-found); the winner's full packed {@code (height, colorIndex, brightness)} becomes the
 * parent cell. Same-range same-color adjacent segments are then merged with the identical
 * fixed scan order of {@link LodPolygonizer#meshQuads}. Integer-only; outputs still encode with
 * the pinned {@link LodSection#toBytes()} format (level = {@code k+1}, origin given explicitly).
 * Deterministic: identical children produce an identical parent, byte for byte.
 *
 * <p>The corner kernel {@link #mergeCorner} is the <b>JDK golden</b>, mirrored bit-for-bit by
 * the tie primitive {@code lod$merge_corner} (pinned DLL boundary); both consume the same 2×2
 * packing and produce the same i64 winner. No second encoding is ever introduced — the
 * pipeline reuses {@link LodSection}'s fixed format end to end.
 *
 * <p>{@code engine.render.lod} 的同级四 {@link LodSection} 子段的递归 mip 合并（p.2.28.2，clean-room
 * 自研）→ 一个更粗的 {@link LodSection}。给定四个 level-{@code k} 象限 {@code a}(\low x, low z)、
 * {@code b}(high x, low z)、{@code c}(low x, high z)、{@code d}(high x, high z)，产出 level-{@code k+1}
 * 区块，其方块跨度翻倍。每个父方块列把四个子段在相同相对单元 {@code (px & (span-1), pz & (span-1))}
 * 上的 <b>2×2 corner</b> 经 {@link #mergeCorner(long, long, long, long)} 归约：胜者为顶高最大的 corner，
 * 并列时按 <b>钉死</b> 的 corner 序 {@code a, b, c, d}（= {@code (x,z)} 字典序首现）取首现；胜者的完整打包
 * {@code (height, colorIndex, brightness)} 即父单元。同范围同色相邻段随后以与
 * {@link LodPolygonizer#meshQuads} 相同的固定扫描序合并。仅整数；输出仍用钉死的
 * {@link LodSection#toBytes()} 格式编码（level = {@code k+1}，origin 显式给定）。确定性：相同子段恒产生
 * 逐字节相同的父段。
 *
 * <p>corner 内核 {@link #mergeCorner} 为 <b>JDK 金样</b>，被 tie 原语 {@code lod$merge_corner}
 * （钉死 DLL 边界）逐位镜像；两者消费相同的 2×2 打包并产生相同的 i64 胜者。绝不引入第二套编码——管线端到端
 * 复用 {@link LodSection} 的固定格式。
 */
public final class LodMerger {

    private LodMerger() {
    }

    /** The highest level that may be merged (L5 has no coarser child). / 可合并的最高层级（L5 无更粗子级）。 */
    public static final LodLevel MAX_INPUT_LEVEL = LodLevel.L4;

    /**
     * 2×2 corner merge decision (JDK golden kernel): select the corner with the maximum top
     * height; ties resolve to the <b>first</b> in the pinned order {@code a, b, c, d}. The
     * winner's full packed value (height, colorIndex, brightness) is returned intact. Pure
     * integer; ties are stable. Mirrored bit-for-bit by tie {@code lod$merge_corner}. Fixed
     * semantics — goldened by p.2.28.6.
     * / 2×2 corner 合并裁决（JDK 金样内核）：取顶高最大者；并列时取钉死序 {@code a, b, c, d} 的<b>首现</b>。
     * 返回胜者的完整打包值（height, colorIndex, brightness）。纯整数；并列稳定。被 tie {@code
     * lod$merge_corner} 逐位镜像。固定语义——由 p.2.28.6 黄金化。
     */
    public static long mergeCorner(long a, long b, long c, long d) {
        long best = a;
        if (heightOf(b) > heightOf(best)) {
            best = b;
        }
        if (heightOf(c) > heightOf(best)) {
            best = c;
        }
        if (heightOf(d) > heightOf(best)) {
            best = d;
        }
        return best;
    }

    /**
     * Merges four same-level child sections into a coarser parent section.
     * / 把四个同级子段合并为更粗的父段。
     *
     * @param parentOriginBlockX the parent's block-space origin x. / 父段方块空间原点 x。
     * @param parentOriginBlockZ the parent's block-space origin z. / 父段方块空间原点 z。
     * @param a                  low-x, low-z quadrant (non-null).
     * @param b                  high-x, low-z quadrant (non-null).
     * @param c                  low-x, high-z quadrant (non-null).
     * @param d                  high-x, high-z quadrant (non-null).
     * @return the level-{@code k+1} parent section.
     * @throws IllegalArgumentException if the children differ in level or the level is too coarse.
     */
    public static LodSection merge(int parentOriginBlockX, int parentOriginBlockZ,
                                   LodSection a, LodSection b, LodSection c, LodSection d) {
        Objects.requireNonNull(a, "a must not be null");
        Objects.requireNonNull(b, "b must not be null");
        Objects.requireNonNull(c, "c must not be null");
        Objects.requireNonNull(d, "d must not be null");
        LodLevel childLevel = a.level();
        if (b.level() != childLevel || c.level() != childLevel || d.level() != childLevel) {
            throw new IllegalArgumentException("merged children must share the same level");
        }
        if (childLevel.ordinal() > MAX_INPUT_LEVEL.ordinal()) {
            throw new IllegalArgumentException("cannot merge at or above level " + childLevel.name());
        }
        int childSpan = childLevel.blockSpan();
        int parentSpan = childSpan * 2;
        LodLevel parentLevel = LodLevel.values()[childLevel.ordinal() + 1];

        long[][] pa = packedGrid(a);
        long[][] pb = packedGrid(b);
        long[][] pc = packedGrid(c);
        long[][] pd = packedGrid(d);

        int[] height = new int[parentSpan * parentSpan];
        int[] color = new int[parentSpan * parentSpan];
        int[] bright = new int[parentSpan * parentSpan];
        for (int px = 0; px < parentSpan; px++) {
            for (int pz = 0; pz < parentSpan; pz++) {
                int rx = px & (childSpan - 1); // childSpan power of two -> fast modulo
                int rz = pz & (childSpan - 1);
                long winner = mergeCorner(pa[rx][rz], pb[rx][rz], pc[rx][rz], pd[rx][rz]);
                int i = LodPolygonizer.idx(parentSpan, px, pz);
                height[i] = LodPolygonizer.LodRegionSample.heightOf(winner);
                color[i] = LodPolygonizer.LodRegionSample.colorOf(winner);
                bright[i] = LodPolygonizer.LodRegionSample.brightOf(winner);
            }
        }

        List<LodColumnStack> cols = LodPolygonizer.buildColumns(parentSpan, height, color);
        List<LodQuad> quads = LodPolygonizer.meshQuads(parentSpan, height, color, bright);
        LodSection.Builder sb = LodSection.builder();
        sb.level(parentLevel).originBlockX(parentOriginBlockX).originBlockZ(parentOriginBlockZ);
        cols.forEach(sb::column);
        quads.forEach(sb::quad);
        return sb.build();
    }

    /**
     * Reconstructs each child cell's packed {@code (height, colorIndex, brightness)} from its
     * pinned {@link LodSection}: height/color come from the column stack's top-most profile
     * segment; brightness comes from the quad that covers the cell. Deterministic. / 从子段的钉死
     * {@link LodSection} 重建每单元的打包 {@code (height, colorIndex, brightness)}：高度/颜色取自列柱最顶
     * 剖面段；亮度取覆盖该单元的 quad。确定性。
     */
    private static long[][] packedGrid(LodSection s) {
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

    private static int heightOf(long packed) {
        return LodPolygonizer.LodRegionSample.heightOf(packed);
    }
}