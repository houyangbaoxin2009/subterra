package io.toterra.subterra.engine.saveverify.chunk;

import java.util.ArrayList;
import java.util.List;

import io.toterra.subterra.engine.network.integrity.Tsha1f;

/**
 * zd 区块确定性划分与内容自检（p.2.10.1，corruption self-check 的块级基础）。纯静态工具，
 * 无内部状态：同输入（payload + chunkSize）恒产出字节级一致的 {@link ChunkPlan}。
 * <p>
 * Deterministic zd-chunk partitioning + content self-check (p.2.10.1, the block-level base of the
 * corruption self-check). A pure static utility with no internal state: identical input
 * ({@code payload} + {@code chunkSize}) always yields a byte-identical {@link ChunkPlan}.
 *
 * <p>划分规则：从 offset 0 起按固定 {@code chunkSize} 切块，最后一块可短；块顺序只由偏移决定
 * （固定排序、无时序）。对每一块分别计算 fast（n=8）与 strong（n=48）两档 tsha1f 标签。
 * 自检把 payload 重新分块并逐块逐字段比对（index/offset/length/digestFast/digestStrong），
 * 单字节翻转、截断、换序重排都会确定性失败。
 *
 * <p>性能：partition 对每字节只处理常数次（整数组切块按段拷贝，总开销 O(len)；每块 tsha1f 亦
 * O(块长)，合计 O(len)），无 O(n²)。空 payload（len=0）→ 空 chunks，自检通过。
 */
public final class ChunkVerifier {

    /** Fast 档位长（tsha1f n 参数）。Fast-tier bit length. */
    public static final int FAST_N = 8;

    /** Strong 档位长（tsha1f n 参数）。Strong-tier bit length. */
    public static final int STRONG_N = 48;

    /** tsha1f 输出进制（固定 base 48）。tsha1f output base, fixed at 48. */
    public static final int BASE = 48;

    private ChunkVerifier() {
    }

    /**
     * 确定性划分 payload 为固定大小 zd 块。确定性 partition: slice {@code payload} into fixed-size
     * zd chunks.
     *
     * <p>块 {#code i} 的 {@code offset = i * chunkSize}、{@code length = min(chunkSize, len - offset)}；
     * 每块以独立数组计算 fast/strong 标签（见 {@link #segmentTsha1f}）。空 payload → 空 chunks。
     *
     * @param payload   受保护内容字节；null 视为空载荷。
     * @param chunkSize 固定切块大小，必须 &gt; 0，否则抛 {@link IllegalArgumentException}。
     * @return 与输入一一确定的 {@link ChunkPlan}。
     */
    public static ChunkPlan partition(byte[] payload, int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be > 0, got " + chunkSize);
        }
        byte[] p = payload == null ? new byte[0] : payload;
        int len = p.length;
        int n = (int) ((len + (long) chunkSize - 1) / chunkSize);
        List<Chunk> chunks = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            long offset = (long) i * chunkSize;
            int length = (int) Math.min((long) chunkSize, len - offset);
            chunks.add(new Chunk(
                    i,
                    offset,
                    length,
                    segmentTsha1f(p, offset, length, FAST_N),
                    segmentTsha1f(p, offset, length, STRONG_N)));
        }
        return new ChunkPlan(p, chunks, chunkSize);
    }

    /**
     * 内容自检：对 {@code plan.payload()} 按 {@code plan.chunkSize()} 重新分块并逐块重算标签，与
     * {@code plan.chunks()} 逐字段比对。任一字段不符即返回失败原因。
     * Content self-check: re-partition {@code plan.payload()} with {@code plan.chunkSize()} and
     * recompute each chunk's labels, comparing field-by-field against {@code plan.chunks()}.
     * The first mismatch yields the failure reason.
     *
     * <p>单字节翻转 / 截断 / 换序重排都会导致某字段比对失败，从而确定性判负。空 payload → 通过。
     *
     * @param plan 待校验的划分计划；null 视为失败。
     * @return {@code null} = 通过；否则为人类可读的失败原因字符串。
     */
    public static String verify(ChunkPlan plan) {
        if (plan == null) {
            return "plan is null";
        }
        ChunkPlan expected = partition(plan.payload(), plan.chunkSize());
        if (expected.size() != plan.size()) {
            return "chunk count mismatch: expected " + expected.size() + " (recomputed), got "
                    + plan.size() + " (recorded)";
        }
        for (int i = 0; i < expected.size(); i++) {
            String err = compareChunk(plan.chunks().get(i), expected.chunks().get(i));
            if (err != null) {
                return err;
            }
        }
        return null;
    }

    /**
     * 对 payload 的 {@code [off, off+len)} 段计算 tsha1f（n 档、base 48）：把该段拷贝为独立
     * 数组再调用 {@link Tsha1f#tsha1f}。整段拷贝总开销 O(len)（每字节至多被拷一次），热路径
     * 不引入 O(n²)。
     *
     * <p>Computes tsha1f (tier {@code n}, base 48) over the {@code [off, off+len)} subrange of
     * {@code payload}: copies the segment into a standalone array, then calls
     * {@link Tsha1f#tsha1f}. Total copy cost across all chunks is O(len) (each byte copied at
     * most once); no O(n²) in the hot path.
     */
    private static String segmentTsha1f(byte[] payload, long off, int len, int n) {
        byte[] seg = new byte[len];
        System.arraycopy(payload, (int) off, seg, 0, len);
        return Tsha1f.tsha1f(seg, n, BASE);
    }

    /** 逐字段比对两块；一致 → null，否则失败原因。Field-by-field compare of two chunks. */
    private static String compareChunk(Chunk actual, Chunk expected) {
        if (actual.index() != expected.index()) {
            return "chunk[" + expected.index() + "] index mismatch: " + actual.index();
        }
        if (actual.offset() != expected.offset()) {
            return "chunk[" + expected.index() + "] offset mismatch: expected " + expected.offset()
                    + ", got " + actual.offset();
        }
        if (actual.length() != expected.length()) {
            return "chunk[" + expected.index() + "] length mismatch: expected " + expected.length()
                    + ", got " + actual.length();
        }
        if (!expected.digestFast().equals(actual.digestFast())) {
            return "chunk[" + expected.index() + "] fast digest mismatch: expected "
                    + expected.digestFast() + ", got " + actual.digestFast();
        }
        if (!expected.digestStrong().equals(actual.digestStrong())) {
            return "chunk[" + expected.index() + "] strong digest mismatch: expected "
                    + expected.digestStrong() + ", got " + actual.digestStrong();
        }
        return null;
    }
}