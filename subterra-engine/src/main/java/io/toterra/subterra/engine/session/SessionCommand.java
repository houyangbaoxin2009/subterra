package io.toterra.subterra.engine.session;

import java.util.Arrays;

/**
 * p.2.11.1 会话命令值对象：不可变 record。
 * <ul>
 *   <li>{@code kind}：命令动词，非空；</li>
 *   <li>{@code seq}：确定性递增序号，{@code >= 0}（p.2.11.2 的会话状态机上升级时序，p.2.11.3
 *       的帧入口循此重建单调推进）；</li>
 *   <li>{@code meta}：可空字符串，在帧层映射成 tink v2 的 ext TLV 元数据；</li>
 *   <li>{@code payload}：zd 文档字节，承载命令载荷；作值语义比较（{@code byte[]} 逐字节）。</li>
 * </ul>
 * equals/hashCode 按值（byte[] 载荷逐字节而非引用），故同一命令的不同字节载体等值。
 * <p>
 * p.2.11.1 session-command value object: an immutable record.
 * <ul>
 *   <li>{@code kind}: the command verb, non-null;</li>
 *   <li>{@code seq}: a deterministic, monotonically increasing sequence number, {@code >= 0}
 *       (the p.2.11.2 state machine advances it; the p.2.11.3 frame entry rebuilds monotonic
 *       progress from it);</li>
 *   <li>{@code meta}: a nullable string, mapped to a tink v2 ext TLV at the frame layer;</li>
 *   <li>{@code payload}: zd-document bytes carrying the command body, compared by value
 *       (byte-for-byte, not by reference).</li>
 * </ul>
 * equals/hashCode are value-based ({@code byte[]} payload compared byte-by-byte), so two command
 * objects with identical field contents are equal regardless of byte-array identity.
 */
public record SessionCommand(SessionCommandKind kind, long seq, String meta, byte[] payload) {

    /**
     * 显式紧凑构造：校验并归一化不变量。非空 {@code kind}、{@code seq >= 0}；null {@code meta}
     * 保持可空、null {@code payload} 归一为内容空（空 zd 文档）。载荷做防御性拷贝以保不可变。
     * <p>
     * Explicit compact constructor: validates and normalises the invariants. A non-null
     * {@code kind}, {@code seq >= 0}; a null {@code meta} stays null, a null {@code payload}
     * is normalised to an empty zd doc; the payload is defensively copied for immutability.
     */
    public SessionCommand {
        if (kind == null) {
            throw new IllegalArgumentException("session command kind must be non-null");
        }
        if (seq < 0) {
            throw new IllegalArgumentException("session command seq must be >= 0 but was " + seq);
        }
        payload = payload == null ? new byte[0] : payload.clone();
    }

    /** 返回载荷的防御性拷贝。Returns a defensive copy of the payload. */
    @Override
    public byte[] payload() {
        return payload.clone();
    }

    /** 按值比较：逐字节比载荷；其余字段按 record 值。Value comparison: byte-by-byte payload. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SessionCommand that)) {
            return false;
        }
        return kind == that.kind
                && seq == that.seq
                && java.util.Objects.equals(meta, that.meta)
                && Arrays.equals(payload, that.payload);
    }

    /** 按值哈希：载荷逐字节。Value hash: payload byte-by-byte. */
    @Override
    public int hashCode() {
        int result = java.util.Objects.hash(kind, seq, meta);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }
}