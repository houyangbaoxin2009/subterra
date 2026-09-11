package io.toterra.subterra.engine.render.lod;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * level-0 {@link LodSection} generation from a deterministic region sample
 * (p.2.28.2, clean-room self-developed). Given a {@link LodRegionSample} of {@code 16×16}
 * block columns ({@link LodLevel#L0} block span) the polygonizer builds, in fixed scan
 * order and integer arithmetic only:
 * (a) one {@link LodColumnStack} per column — the top-most surface rule takes the single
 * top sample as the sole profile segment {@code (yLow == yHigh == topHeight)}; and
 * (b) horizontal quad cells via a greedy maximal merge with a <b>pinned</b> scan order —
 * cells are visited {@code (x then z) ascending}, each un-visited cell starts the largest
 * <em>aligned power-of-two</em> quad of uniform {@code (height, colorIndex, brightness)}
 * placed at {@code (x, z)}. Both the surface rule and the merge arbitration rule are
 * <b>fixed semantics</b> to be goldened by the p.2.28.6 probes: identical input always
 * yields the identical {@link LodSection#toBytes()} payload. No floating point, no
 * randomness, no timestamps; construction is a single pass over the cells (near-linear),
 * never {@code O(n²)}.
 *
 * <p>The deterministic per-column top sampler {@link #profileTop(long, long, long)} (a pure
 * integer mixer over {@code seed}, column {@code (x, z)} yielding a packed
 * {@code (height, colorIndex, brightness)}) is the <b>JDK golden kernel</b>. It is mirrored
 * bit-for-bit by the tie primitive {@code lod$profile_top} of
 * {@code subterra_lod_pipeline.tie}; the {@link io.toterra.subterra.engine.render.lod.LodTieAccelerator}
 * swaps the JDK kernel for the DLL call without changing any output byte (same input, same
 * output). The packed layout (little-endian field masks, stable) is defined on
 * {@link LodRegionSample}.
 *
 * <p>{@code engine.render.lod} 的 level-0 {@link LodSection} 生成（p.2.28.2，clean-room 自研）。
 * 给定一个 {@code 16×16} 方块列、即 {@link LodLevel#L0} 方块跨度的确定性区域采样
 * {@link LodRegionSample}，多边形化器在固定扫描序、仅整数算术下构建：(a) 每列一个
 * {@link LodColumnStack}——顶表规则取顶层单一样本为唯一剖面段
 * {@code (yLow == yHigh == topHeight)}；以及 (b) 经贪心最大合并的水平 quad 单元，合并裁决
 * <b>钉死</b>扫描序——按 {@code (x 再 z)} 升序访问单元，每个未访问单元以 {@code (x, z)} 为原点放置
 * {@code (height, colorIndex, brightness)} 均一的<em>对齐 2 的幂</em>最大 quad。表面规则与合并裁决
 * 规则均属<b>固定语义</b>，将由 p.2.28.6 探针黄金化：相同输入恒产出相同的 {@link LodSection#toBytes()}
 * 载荷。无浮点、无随机、无时间戳；构造为单遍扫描（近线性），绝不 {@code O(n²)}。
 *
 * <p>确定性逐列顶采样 {@link #profileTop(long, long, long)}（对 {@code seed} 与列
 * {@code (x, z)} 的纯整数混合，产出打包 {@code (height, colorIndex, brightness)}）为 <b>JDK 金样内核</b>。
 * 它被 {@code subterra_lod_pipeline.tie} 的 tie 原语 {@code lod$profile_top} 逐位镜像；{@link
 * io.toterra.subterra.engine.render.lod.LodTieAccelerator} 用 DLL 调用替换 JDK 内核而不改变任何输出
 * 字节（同输入同输出）。打包布局（低端字节屏蔽、稳定）见 {@link LodRegionSample}。
 */
public final class LodPolygonizer {

    private LodPolygonizer() {
    }

    /**
     * The deterministic per-column top-surface sampler: returns a packed
     * {@code (height, colorIndex, brightness)} for column {@code (x, z)} under {@code seed}.
     * Pure integer; no floating point; bounded arithmetic that never overflows i64, so it is
     * replicated bit-for-bit by the tie primitive {@code lod$profile_top}. Fixed semantics
     * (p.2.28.6 goldens).
     * / 确定性逐列顶表面采样：对 {@code seed} 下列 {@code (x, z)} 返回打包 {@code (height, colorIndex,
     * brightness)}。纯整数；无浮点；有界算术不溢出 i64，故能被 tie 原语 {@code lod$profile_top}
     * 逐位复刻。固定语义（p.2.28.6 黄金化）。
     */
    public static long profileTop(long seed, long x, long z) {
        long s = (seed & 0x7FFFFFFFL) | 1L;                 // 31-bit odd, non-zero
        long v = s ^ (((x & 0xFFFFL) << 8) | (z & 0xFFFFL));
        v ^= v >> 16;
        v += (v << 3) + 0x9E3779B9L;                        // bounded well below 2^63
        v ^= v >> 12;
        v &= 0x000FFFFFL;                                   // keep 20 bits, keep positive
        int height = 4 + (int) (v & 0x7F);
        int color = (int) ((v >> 8) & 0xFF);
        int bright = (int) ((v >> 16) & 0x0F);
        return LodRegionSample.pack(height, color, bright);
    }

    /**
     * Generates the level-0 section for a deterministic region sample, using the sample's
     * own stored sampler (JDK kernel by default; the pipeline may re-sample with the tie
     * accelerator for the byte-identical tie path).
     * / 为确定性区域采样生成 level-0 区块，使用采样自身的采样器（缺省 JDK 内核；管线可用 tie 加速器
     * 重新采样以获得逐字节一致的 tie 路径）。
     *
     * @param sample the L0 block-column region (block span must equal {@link LodLevel#L0}'s).
     * @return the normalized L0 section.
     * @throws IllegalArgumentException if the sample block span is not L0's.
     */
    public static LodSection generate(LodRegionSample sample) {
        Objects.requireNonNull(sample, "sample must not be null");
        int span = sample.blockSpan();
        if (span != LodLevel.L0.blockSpan()) {
            throw new IllegalArgumentException("polygonizer level-0 region must span "
                    + LodLevel.L0.blockSpan() + " blocks but got " + span);
        }
        List<LodColumnStack> cols = new ArrayList<>(span * span);
        for (int x = 0; x < span; x++) {
            for (int z = 0; z < span; z++) {
                int h = sample.heightAt(x, z);
                int c = sample.colorAt(x, z);
                cols.add(new LodColumnStack(x, z,
                        List.of(new LodColumnStack.LodProfileEntry(h, h, c))));
            }
        }
        List<LodQuad> quads = meshQuads(span, sample.height(), sample.color(), sample.bright());
        LodSection.Builder b = LodSection.builder();
        b.level(LodLevel.L0).originBlockX(0).originBlockZ(0);
        cols.forEach(b::column);
        quads.forEach(b::quad);
        return b.build();
    }

    /**
     * Builds one column stack per {@code (x, z)} in {@code [0, span)} from parallel top-height
     * and color grids; each column gets a single top-most profile segment. Fixed scan order
     * ({@code x} then {@code z} ascending). / 从并行的顶部高度与颜色网格为每 {@code (x, z)}
     * （{@code [0, span)}）构建一个列柱；每列一个顶表剖面段。固定扫描序（{@code x} 再 {@code z} 升序）。
     */
    static List<LodColumnStack> buildColumns(int span, int[] height, int[] color) {
        List<LodColumnStack> cols = new ArrayList<>(span * span);
        for (int x = 0; x < span; x++) {
            for (int z = 0; z < span; z++) {
                int h = height[idx(span, x, z)];
                int c = color[idx(span, x, z)];
                cols.add(new LodColumnStack(x, z,
                        List.of(new LodColumnStack.LodProfileEntry(h, h, c))));
            }
        }
        return cols;
    }

    /**
     * Greedy maximal merge of the top surface into aligned power-of-two quads. Cells are
     * visited {@code (x, z)} ascending; each un-consumed cell starts the largest aligned
     * power-of-two quad whose cells all share the same {@code (height, color, brightness)}.
     * Pinned arbitration rule — goldened by p.2.28.6. Near-linear single pass. / 把顶表面贪心最大
     * 合并为对齐 2 的幂 quad。按 {@code (x, z)} 升序访问单元；每个未消费单元原点放置最大的对齐 2 的幂、
     * 且各单元 {@code (height, color, brightness)} 均一的 quad。钉死裁决规则——由 p.2.28.6 黄金化。
     * 近线性单遍。
     */
    static List<LodQuad> meshQuads(int span, int[] height, int[] color, int[] bright) {
        boolean[] consumed = new boolean[span * span];
        List<LodQuad> quads = new ArrayList<>();
        for (int bx = 0; bx < span; bx++) {
            for (int bz = 0; bz < span; bz++) {
                int base = idx(span, bx, bz);
                if (consumed[base]) {
                    continue;
                }
                int size = maxAlignedSize(span, bx, bz, consumed, height, color, bright);
                int h0 = height[base], c0 = color[base], b0 = bright[base];
                for (int dx = 0; dx < size; dx++) {
                    for (int dz = 0; dz < size; dz++) {
                        consumed[idx(span, bx + dx, bz + dz)] = true;
                    }
                }
                quads.add(new LodQuad(bx, bz, size, h0, c0, b0));
            }
        }
        return quads;
    }

    /** Largest aligned power-of-two quad at {@code (x, z)} covering un-consumed uniform cells. /
     *  位于 {@code (x, z)}、覆盖未消费均一单元的最大对齐 2 的幂 quad。 */
    private static int maxAlignedSize(int span, int x, int z, boolean[] consumed,
                                      int[] height, int[] color, int[] bright) {
        for (int s = span; s >= 1; s >>= 1) {
            if (x % s == 0 && z % s == 0 && x + s <= span && z + s <= span
                    && uniform(span, x, z, s, consumed, height, color, bright)) {
                return s;
            }
        }
        return 1;
    }

    private static boolean uniform(int span, int x, int z, int s, boolean[] consumed,
                                   int[] height, int[] color, int[] bright) {
        int h0 = height[idx(span, x, z)];
        int c0 = color[idx(span, x, z)];
        int b0 = bright[idx(span, x, z)];
        for (int dx = 0; dx < s; dx++) {
            for (int dz = 0; dz < s; dz++) {
                int i = idx(span, x + dx, z + dz);
                if (consumed[i] || height[i] != h0 || color[i] != c0 || bright[i] != b0) {
                    return false;
                }
            }
        }
        return true;
    }

    static int idx(int span, int x, int z) {
        return x * span + z;
    }

    /**
     * A deterministic top-surface sampler: {@code sample(seed, x, z)} returns the packed
     * {@code (height, colorIndex, brightness)} of one block column. Pure; the JDK kernel and
     * the tie DLL path both implement it and are byte-identical.
     * / 确定性顶表面采样器：{@code sample(seed, x, z)} 返回一个方块列的打包 {@code (height,
     * colorIndex, brightness)}。纯函数；JDK 内核与 tie DLL 路径均实现之且逐字节一致。
     */
    @FunctionalInterface
    public interface TopSampler {
        long sample(long seed, long x, long z);
    }

    /**
     * A deterministic region sample (p.2.28.2): {@code blockSpan × blockSpan} block columns
     * (power of two, {@code 1..512}), each with a packed {@code (height, colorIndex,
     * brightness)} derived deterministically from {@code seed} via a sampler. Frozen at
     * construction (memoized); no floating point. The packed layout is stable: bits
     * {@code 0-7}=brightness, {@code 8-15}=colorIndex, {@code 16-31}=topHeight (unsigned).
     * / 确定性区域采样（p.2.28.2）：{@code blockSpan × blockSpan} 方块列（2 的幂，{@code 1..512}），
     * 每列经由采样器从 {@code seed} 确定性导出打包 {@code (height, colorIndex, brightness)}。构造时冻结
     * （记忆化）；无浮点。打包布局稳定：位 {@code 0-7}=亮度、{@code 8-15}=颜色索引、{@code 16-31}=顶高
     * （无符号）。
     */
    public static final class LodRegionSample {

        private final int blockSpan;
        private final long seed;
        private final int[] height;
        private final int[] color;
        private final int[] bright;

        /**
         * Builds the region by sampling every column once (eager, deterministic).
         * / 通过逐列单次采样构建区域（急切、确定性）。
         */
        public LodRegionSample(int blockSpan, long seed, TopSampler sampler) {
            if (blockSpan < 1 || blockSpan > 512 || (blockSpan & (blockSpan - 1)) != 0) {
                throw new IllegalArgumentException("region blockSpan must be a power of two in "
                        + "[1, 512] but was " + blockSpan);
            }
            Objects.requireNonNull(sampler, "sampler must not be null");
            this.blockSpan = blockSpan;
            this.seed = seed;
            int n = blockSpan * blockSpan;
            this.height = new int[n];
            this.color = new int[n];
            this.bright = new int[n];
            for (int x = 0; x < blockSpan; x++) {
                for (int z = 0; z < blockSpan; z++) {
                    long p = sampler.sample(seed, x, z);
                    int i = idx(blockSpan, x, z);
                    height[i] = heightOf(p);
                    color[i] = colorOf(p);
                    bright[i] = brightOf(p);
                }
            }
        }

        /** Convenience: uses the JDK golden kernel {@link LodPolygonizer#profileTop}. /
         *  便捷：使用 JDK 金样内核 {@link LodPolygonizer#profileTop}。 */
        public LodRegionSample(int blockSpan, long seed) {
            this(blockSpan, seed, LodPolygonizer::profileTop);
        }

        public int blockSpan() {
            return blockSpan;
        }

        public long seed() {
            return seed;
        }

        public int[] height() {
            return height;
        }

        public int[] color() {
            return color;
        }

        public int[] bright() {
            return bright;
        }

        public int heightAt(int x, int z) {
            return height[idx(blockSpan, x, z)];
        }

        public int colorAt(int x, int z) {
            return color[idx(blockSpan, x, z)];
        }

        public int brightAt(int x, int z) {
            return bright[idx(blockSpan, x, z)];
        }

        /** Packs the three fields into the stable i64 layout. / 把三字段打包进稳定 i64 布局。 */
        public static long pack(int topHeight, int colorIndex, int brightness) {
            return ((long) (topHeight & 0xFFFF) << 16)
                    | (long) ((colorIndex & 0xFF) << 8)
                    | (brightness & 0xFF);
        }

        /** Unpacks topHeight (bits 16-31). / 解包顶高（位 16-31）。 */
        public static int heightOf(long packed) {
            return (int) ((packed >> 16) & 0xFFFF);
        }

        /** Unpacks colorIndex (bits 8-15). / 解包颜色索引（位 8-15）。 */
        public static int colorOf(long packed) {
            return (int) ((packed >> 8) & 0xFF);
        }

        /** Unpacks brightness (bits 0-7). / 解包亮度（位 0-7）。 */
        public static int brightOf(long packed) {
            return (int) (packed & 0xFF);
        }
    }
}