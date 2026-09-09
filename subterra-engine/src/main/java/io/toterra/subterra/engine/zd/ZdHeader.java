package io.toterra.subterra.engine.zd;

import java.nio.charset.StandardCharsets;

/**
 * zd v2 头载体（p.2.3.1）。抽象并泛化自 profiler 报告里的 {@code ZdWriter} 头部钉点：
 * 10 字节 = {@code "TIEDBZD"}(7) + 版本数字字节 0x00 0x02（base-48，v2 = d1=0, d2=2，bit
 * 高位在前）+ 1 字节 flags。语言无关规范与 tie-main {@code compiler/tdzd.tie} 一致；本 Java
 * 载体保持与该规范逐字节一致，作为 p.2.3 全轨 zd 载荷的通用头。
 * <p>
 * zd v2 carrier for the header (p.2.3.1). Abstracted and generalised from the
 * header anchor in the profiler report {@code ZdWriter}: 10 bytes =
 * {@code "TIEDBZD"}(7) + version digit bytes 0x00 0x02 (base-48, v2 = d1=0, d2=2,
 * bits high-order-first per the pinned spec) + a 1-byte flags field. The
 * language-agnostic spec matches tie-main {@code compiler/tdzd.tie}; this Java
 * carrier keeps the exact bytes as the generic zd v2 header for the p.2.3 track.
 * <p>
 * 注意/Note：字典 / 列式 / ext / 流式 / 压缩位 (below) <b>当前仅声明 flags，实际编码为最小
 * 子集</b>（本子项不实现压缩/列式/字典变体；后续子项按需扩展）。写侧只做「置位」，读侧把
 * flags 原样暴露给调用方。
 * <p>
 * 头规范钉点 = 10 字节：字典 / 列式 / 压缩等变体当前仅置位 flags，编码为最小子集。
 * The header spec anchor is the 10 bytes: dictionary / columnar / compressed
 * variants are currently declared as flags only — the actual encoding is the
 * minimal subset (this sub-item does not implement compression; later sub-items
 * extend on demand).
 */
public final class ZdHeader {

    /** The leading 7-byte ASCII magic {@code TIEDBZD}. */
    public static final String MAGIC = "TIEDBZD";
    private static final byte[] MAGIC_BYTES = MAGIC.getBytes(StandardCharsets.US_ASCII);

    // ---- flags bit 定义 / flag bits ----
    /** bit0 = dictionary (字典变体字典表). */
    public static final int FLAG_DICTIONARY = 1 << 0;
    /** bit1 = columnar (列式变体). */
    public static final int FLAG_COLUMNAR = 1 << 1;
    /** bit2 = ext (扩展标记). */
    public static final int FLAG_EXT = 1 << 2;
    /** bit3 = streaming (流式). */
    public static final int FLAG_STREAMING = 1 << 3;
    /** bit4 = compressed (压缩变体). */
    public static final int FLAG_COMPRESSED = 1 << 4;

    private static final int OFFSET_FLAGS = 9;

    private ZdHeader() {
    }

    /**
     * 以给定的 flags 组合写出 10 字节 zd v2 头，恒为版本 0x00 0x02。
     * Writes the 10-byte zd v2 header for the given flags combination, always
     * version 0x00 0x02.
     */
    public static byte[] writeZ(boolean dict, boolean columnar, boolean ext,
                                boolean streaming, boolean compressed) {
        int flags = (dict ? FLAG_DICTIONARY : 0)
                | (columnar ? FLAG_COLUMNAR : 0)
                | (ext ? FLAG_EXT : 0)
                | (streaming ? FLAG_STREAMING : 0)
                | (compressed ? FLAG_COMPRESSED : 0);
        return writeZ(flags);
    }

    /**
     * 以紧凑的 flags 整数（只取低 5 位）写出 10 字节 zd v2 头。
     * Writes the 10-byte zd v2 header from a compact flags int (only the low
     * 5 bits are honoured).
     */
    public static byte[] writeZ(int flags) {
        byte[] head = new byte[10];
        System.arraycopy(MAGIC_BYTES, 0, head, 0, MAGIC_BYTES.length);
        head[7] = 0x00; // base-48 digit d1
        head[8] = 0x02; // base-48 digit d2  -> v2
        head[OFFSET_FLAGS] = (byte) (flags & 0x1F);
        return head;
    }

    /**
     * 校验 {code head} 是合法的 zd v2 头（魔数 + 版本）。非 zd v2 / 长度不足 / null 返回
     * false，不抛异常。Returns whether {@code head} is a valid zd v2 header prefix
     * (magic + version); mismatches / short buffers / null return false, never throw.
     */
    public static boolean isZd(byte[] head) {
        return isZd(head, 0);
    }

    /**
     * 从偏移 {@code off} 起校验 zd v2 头。Same as {@link #isZd(byte[])} but starting at
     * the given offset.
     */
    public static boolean isZd(byte[] head, int off) {
        if (head == null || head.length < off + 10) {
            return false;
        }
        for (int i = 0; i < MAGIC_BYTES.length; i++) {
            if (head[off + i] != MAGIC_BYTES[i]) {
                return false;
            }
        }
        return (head[off + 7] & 0xFF) == 0x00 && (head[off + 8] & 0xFF) == 0x02;
    }

    /**
     * 解析头部版本号（base-48：d1*48 + d2）。非法魔数 / 版本 / 长度抛
     * {@link IllegalArgumentException}。Parses the version from the header
     * (base-48: d1*48 + d2). Invalid magic / version / short buffer raises
     * {@link IllegalArgumentException}.
     */
    public static int parseVersion(byte[] head, int off) {
        requireValid(head, off);
        return ((head[off + 7] & 0xFF) * 48) + (head[off + 8] & 0xFF);
    }

    /**
     * 读取头部 flags 字节。非法魔数 / 长度抛 {@link IllegalArgumentException}。
     * Reads the flags byte. Invalid magic / short buffer raise
     * {@link IllegalArgumentException}.
     */
    public static int flags(byte[] head, int off) {
        requireValid(head, off);
        return head[off + OFFSET_FLAGS] & 0xFF;
    }

    private static void requireValid(byte[] head, int off) {
        if (head == null || head.length < off + 10) {
            throw new IllegalArgumentException("zd header too short: " + (head == null ? "null" : head.length));
        }
        for (int i = 0; i < MAGIC_BYTES.length; i++) {
            if (head[off + i] != MAGIC_BYTES[i]) {
                throw new IllegalArgumentException("not a zd payload (bad magic)");
            }
        }
        if ((head[off + 7] & 0xFF) != 0x00 || (head[off + 8] & 0xFF) != 0x02) {
            throw new IllegalArgumentException("unsupported zd version bytes "
                    + (head[off + 7] & 0xFF) + " " + (head[off + 8] & 0xFF));
        }
    }
}