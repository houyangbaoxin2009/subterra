package io.toterra.subterra.engine.render.lod;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic LOD seam / silhouette seam rules (p.2.28.4, clean-room self-developed):
 * the fixed-geometry rules that keep level-0 mesh edges aligned to the full-detail block
 * grid and adjacent sections' profile / quad edges crack-free, plus a deterministic
 * distance-fog strength function tiered by {@link LodDistanceSelector}. Everything is pure
 * integer / bit arithmetic on block-space coordinates — no floating point, no trigonometry,
 * no timing, no randomness, no hash order. Every function is a pure function of its inputs
 * (same input &rarr; same output / same bytes), and is intentionally <em>global</em> (zero
 * imported MC/GL types); the exact constants below are the golden target the p.2.28.6 probe
 * exercises, so they must never be re-guessed.
 *
 * <p><b>Why this matters.</b> The LOD renderer sweeps sections at coarser levels
 * ({@code L1..L5}) as well as the full-detail block grid. Without deterministic edge
 * rules two neighboring sections — or a level-0 edge and the underlying full-detail blocks —
 * can quantize their shared boundary at different integer coordinates and tear (gaps /
 * T-junction cracks). The rules here pin that quantization: edges always live on the integer
 * block grid, the shared edge coordinate is the same integer on both sides, a smaller quad
 * that meets a larger neighbor's edge sets the {@code stitch} bit (the runtime then adds the
 * stitch/degenerate triangle), and distance fog is a monotone, per-tier integer function.
 *
 * <p>确定性 LOD 接缝/裂缝规则（p.2.28.4，clean-room 自研）：让 level-0 网格边缘与全细节方块网格对齐、
 * 让相邻 section 的剖面/quad 边缘无缝（防裂隙）的固定几何规则，外加一条按 {@link LodDistanceSelector}
 * 分档的确定性距离雾强度函数。全程为方块空间坐标上的纯整数/位运算——无浮点、无三角、无时序、无随机、
 * 无哈希序。每个函数都是其输入的纯函数（同输入&rarr;同输出/同字节），并且刻意保持<em>全局</em>（零 MC/GL
 * import）；下方常量即为 p.2.28.6 探针黄金化的对象，绝不可重猜。
 *
 * <p><b>为何重要。</b>LOD 渲染会以较粗层级（{@code L1..L5}）与全细节方块网格并行扫描区块。若无确定性边缘
 * 规则，两个相邻 section——或一条 level-0 边缘与下方的全细节方块——可能把共享边界量化到不同的整数坐标而
 * 撕裂（gap / T 型裂缝）。此处钉死该量化：边缘恒位于整数方块网格、共享边缘坐标两侧为同一整数、与较大邻居
 * 边缘相接的较小 quad 置 {@code stitch} 位（runtime 据此补 stitch/退化三角），距离雾为单调、分档的整数函数。
 */
public final class LodSeamRules {

    /** Block side of a full-detail (level-0) chunk. / 全细节（level-0）区块的一方块边长。 */
    public static final int FULL_DETAIL_STEP = 16;

    /** Maximum fog strength (0..255), opaque. / 最大雾强度（0..255），不透明。 */
    public static final int FOG_MAX = 255;

    private LodSeamRules() {
    }

    /* ------------------------------------------------------------------
     * level-0 edges &rarr; full-detail block grid alignment
     * level-0 边缘 &rarr; 全细节方块网格对齐
     * ---------------------------------------------------------------- */

    /**
     * Floors a block coordinate to the full-detail (16-block) grid: a level-0 mesh edge must
     * land on a full-detail block boundary so it overlaps the underlying full-detail mesh with
     * zero gap. Pure bit mask; deterministic. Same input, same output.
     * / 把方块坐标向下取整到全细节（16 方块）网格：level-0 网格边缘必须落在全细节方块边界上，使其与下层
     * 全细节网格零间隙重叠。纯位掩码；确定性。同输入、同输出。
     */
    public static int floorToFullDetail(int blockCoord) {
        return blockCoord & ~(FULL_DETAIL_STEP - 1);
    }

    /**
     * Ceils a block coordinate to the full-detail (16-block) grid. Pure bit mask;
     * deterministic. / 把方块坐标向上取整到全细节（16 方块）网格。纯位掩码；确定性。
     */
    public static int ceilToFullDetail(int blockCoord) {
        return (blockCoord + (FULL_DETAIL_STEP - 1)) & ~(FULL_DETAIL_STEP - 1);
    }

    /**
     * Whether a block coordinate already sits on a full-detail grid line.
     * / 该方块坐标是否已落在全细节网格线上。
     */
    public static boolean isFullDetailAligned(int blockCoord) {
        return (blockCoord & (FULL_DETAIL_STEP - 1)) == 0;
    }

    /* ------------------------------------------------------------------
     * crack-free: shared-edge vertex integer rounding + stitch bit
     * 防裂隙：共享边缘顶点取整规则 + stitch 位
     * ---------------------------------------------------------------- */

    /**
     * Snaps an edge vertex to the integer block grid. All LOD edges are already integers, so
     * this is the deterministic identity — it is the pinned <em>rule</em> (edges never carry
     * sub-block fractional coordinates), not an arithmetic op. Exposed explicitly so the
     * rounding rule is a named contract the p.2.28.6 probe can assert.
     * / 把边缘顶点取整到整数方块网格。所有 LOD 边缘本就是整数，故此处为确定性恒等——它是被钉死的<em>规则</em>
     * （边缘绝不携带亚方块分数坐标），而非算术操作。显式暴露使该取整规则成为 p.2.28.6 探针可断言的具名契约。
     */
    public static int snapEdge(int blockCoord) {
        return blockCoord;
    }

    /**
     * Two sections' shared edge coincides iff they quantize it to the same integer block
     * coordinate (crack-free). / 两 section 的共享边缘一致当且仅当它们把它量化到同一整数方块坐标（防裂隙）。
     */
    public static boolean edgesCoincide(int edgeA, int edgeB) {
        return edgeA == edgeB;
    }

    /**
     * A coarse edge of size {@code coarseSize} can be tessellated by a finer edge of size
     * {@code fineSize} without fractional vertices iff the coarser is an integer multiple of
     * the finer (power-of-two greedy quads always satisfy this). / 尺寸为 {@code coarseSize} 的较粗边缘
     * 能被尺寸为 {@code fineSize} 的较细边缘无损细分当且仅当较粗者是较细者的整数倍（2 的幂贪心 quad 恒满足）。
     */
    public static boolean tessellationCompatible(int coarseSize, int fineSize) {
        return coarseSize >= fineSize && coarseSize % fineSize == 0;
    }

    /**
     * A T-junction {@code stitch} is required when two abutting quads have different sizes,
     * because the smaller one's corner falls in the middle of the larger one's edge.
     * / 两个相接 quad 尺寸不同时必须做 T 型 {@code stitch}，因为较细者的角点落在较粗者边缘中部。
     */
    public static boolean stitchRequired(int sizeA, int sizeB) {
        return sizeA != sizeB;
    }

    /**
     * The deterministic stitch bit for one quad within its section: {@code 1} when the quad is
     * strictly smaller than the section's largest quad (its shared edges may be subdivided by
     * larger neighbours, needing a stitch vertex), else {@code 0}. Pure function of
     * {@code (quadSize, sectionMaxQuadSize)}; same input &rarr; same bit. This bit is what the
     * LOD vertex {@code meta} nibble carries at {@code (1&lt;&lt;4)}.
     * / 某 quad 在其 section 内的确定性 stitch 位：当该 quad 严格小于本 section 最大 quad 时为 {@code 1}
     * （其共享边缘可能被较大邻居细分，需补 stitch 顶点），否则为 {@code 0}。为 {@code (quadSize,
     * sectionMaxQuadSize)} 的纯函数；同输入&rarr;同位。该位即 LOD 顶点 {@code meta} 半字节在
     * {@code (1&lt;&lt;4)} 处携带的位。
     */
    public static int stitchBit(int quadSize, int sectionMaxQuadSize) {
        return (sectionMaxQuadSize > 0 && quadSize < sectionMaxQuadSize) ? 1 : 0;
    }

    /* ------------------------------------------------------------------
     * distance fog &rarr; monotone deterministic strength per tier
     * 距离雾 &rarr; 按档单调的确定性强度
     * ---------------------------------------------------------------- */

    /**
     * Deterministic integer fog strength for a squared block distance, tiered by the
     * {@link LodDistanceSelector} ladder: {@code 0} at (or below) zero / the first cut, rising
     * strictly through the tiers, and {@link #FOG_MAX} beyond the last cut. Monotone
     * non-decreasing in distance — farther &rarr; stronger fog — and a pure integer/long
     * function (no {@code Math.sqrt}, no float), so the same {@code (distSqBlocks, tiers)}
     * always yields the same strength. This is the p.2.28.6 golden target.
     * / 按 {@link LodDistanceSelector} 阶梯分档的确定性整数雾强度：在零（或首个切分）处为 {@code 0}、
     * 逐档严格上升、超出末档为 {@link #FOG_MAX}。随距离单调不减——越远雾越浓——且为纯整数/long 函数
     * （无 {@code Math.sqrt}、无浮点），故相同的 {@code (distSqBlocks, tiers)} 恒得相同强度。此即
     * p.2.28.6 的黄金目标。
     *
     * @param tiers the selector's cuts in ascending-level order (must be ascending in
     *     {@code distance}); an empty list is treated as "no fog".
     *     / 选择器的切分，按升层级序（{@code distance} 亦须升序）；空列表视为「无雾」。
     */
    public static int fogStrength(long distSqBlocks, List<LodDistanceSelector.Tier> tiers) {
        Objects.requireNonNull(tiers, "tiers must not be null");
        if (distSqBlocks <= 0 || tiers.isEmpty()) {
            return 0;
        }
        int idx = 0;
        for (LodDistanceSelector.Tier t : tiers) {
            long cutSq = (long) t.distance() * (long) t.distance();
            if (distSqBlocks < cutSq) {
                return fogForTierIndex(idx, tiers.size());
            }
            idx++;
        }
        return FOG_MAX;
    }

    /**
     * Convenience: fog strength for a selector's own ladder (its {@link LodDistanceSelector#tiers()}
     * in ascending order). / 便捷重载：对某选择器自身阶梯（其 {@link LodDistanceSelector#tiers()} 升序）求雾强度。
     */
    public static int fogStrength(long distSqBlocks, LodDistanceSelector selector) {
        Objects.requireNonNull(selector, "selector must not be null");
        return fogStrength(distSqBlocks, selector.tiers());
    }

    /**
     * Fog strength at the {@code idx}-th tier (0-based), strictly increasing in {@code idx}
     * and ending at {@link #FOG_MAX}. {@code (FOG_MAX*(idx+1))/len} with {@code 0<=idx<len}.
     * / 第 {@code idx} 档（0 基）的雾强度，随 {@code idx} 严格递增且末档为 {@link #FOG_MAX}。
     * 即 {@code (FOG_MAX*(idx+1))/len}，其中 {@code 0<=idx<len}。
     */
    public static int fogForTierIndex(int idx, int tierCount) {
        if (idx < 0 || idx >= tierCount) {
            throw new IllegalArgumentException("tier index must be in [0, " + tierCount + ") but was " + idx);
        }
        return (FOG_MAX * (idx + 1)) / tierCount;
    }
}