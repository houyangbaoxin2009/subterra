package io.toterra.subterra.engine.saveverify.chunk;

/**
 * 一个确定性 zd-chunk 的划分与校验元数据：块在 {@link ChunkPlan#payload()} 中的固定位置
 * （{@code offset}/{@code length}）加 fast/strong 两档 tsha1f 校验标签。纯数据、不可变。
 * <p>
 * Deterministic chunk metadata (p.2.10.1): a chunk's fixed position in
 * {@link ChunkPlan#payload()} ({@code offset}/{@code length}) plus its fast and strong
 * tsha1f verification labels. Pure data, immutable.
 *
 * <p>偏移契约：{@code offset} 恒为 {@code index * chunkSize}；{@code length} 单独记录，
 * 最后一块可为 {@code [0, chunkSize)} 的短块（甚至是 0）。{@code offset}/{@code length}
 * 用 {@code long}，避免大 payload 下的 32 位溢出。
 *
 * @param index         块下标（从 0 开始严格升序）。
 * @param offset        块在 payload 中的起始偏移（固定 = index * chunkSize）。
 * @param length        块字节长度（最后一块可 &lt; chunkSize）。
 * @param digestFast    fast 档校验标签 = {@code Tsha1f.tsha1f(seg, 8, 48)}（8 符号）。
 * @param digestStrong  strong 档校验标签 = {@code Tsha1f.tsha1f(seg, 48, 48)}（48 符号）。
 */
public record Chunk(int index, long offset, long length, String digestFast, String digestStrong) {

    /**
     * 紧凑规范构造：拒绝负字段（划分语义中块位置/长度必非负）。
     * Compact canonical constructor: rejects negative fields (positions/lengths are
     * never negative in the partition semantics).
     */
    public Chunk {
        if (index < 0 || offset < 0 || length < 0) {
            throw new IllegalArgumentException("chunk index/offset/length must be non-negative");
        }
    }

    /**
     * 紧凑工厂。Compact factory over the canonical constructor.
     */
    public static Chunk of(int index, long offset, long length,
                           String digestFast, String digestStrong) {
        return new Chunk(index, offset, length, digestFast, digestStrong);
    }

    /** 人可读概要（含两档标签，便于自检排错）。Human-readable summary (includes both labels). */
    @Override
    public String toString() {
        return "Chunk{index=" + index + ", offset=" + offset + ", length=" + length
                + ", fast=" + digestFast + ", strong=" + digestStrong + "}";
    }
}