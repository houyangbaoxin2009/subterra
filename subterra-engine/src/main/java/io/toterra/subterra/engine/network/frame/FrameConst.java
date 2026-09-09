package io.toterra.subterra.engine.network.frame;

/**
 * tink v2 帧载体的语言无关 ABI 常量（p.2.4.1）。这些数值是权威的，逐字抄录自 tie-main
 * 协议；本 Java 载体不得改动它们。它是后续 p.2.4 轨道里 v2 编解码器的常量锚点；同时定义了
 * v1 兼容读回退所需的保留位与 ext TLV 键。
 * <p>
 * Language-agnostic tink v2 frame ABI constants (p.2.4.1). These values are
 * authoritative, transcribed verbatim from the tie-main protocol; this Java carrier
 * MUST NOT alter them. They anchor the v2 codec on the later p.2.4 track and define
 * the reserved-bit and ext TLV keys that the v1 compatible-read fallback relies on.
 */
public final class FrameConst {

    private FrameConst() {
    }

    // ---- magic / version ----
    /** 首个魔数字节（ASCII "tk" 的 't'）。First magic byte ('t' of ASCII "tk"). */
    public static final int MAGIC_FIRST = 0x74;
    /** 第二个魔数字节（ASCII "tk" 的 'k'）。Second magic byte ('k' of ASCII "tk"). */
    public static final int MAGIC_SECOND = 0x6B;
    /** tink 帧协议版本。The tink frame protocol version. */
    public static final int FRAME_VERSION = 2;

    // ---- flags 位（1 字节）/ flag bits (one byte) ----
    /** bit0 = 强完整性子槽（32 字节）。bit0 = strong-integrity slot (32 bytes). */
    public static final int FLAG_STRONG = 0x01;
    /** bit1 = 压缩。bit1 = compressed. */
    public static final int FLAG_COMPRESSED = 0x02;
    /** bit2 = 加密。bit2 = encrypted. */
    public static final int FLAG_ENCRYPTED = 0x04;
    /** bit3 = 流式。bit3 = streaming. */
    public static final int FLAG_STREAM = 0x08;
    /** bit4 = 流式末帧。bit4 = stream-end. */
    public static final int FLAG_STREAM_END = 0x10;
    /**
     * bit5..bit7 保留位掩码：保留位务必为 0，否则该帧被拒绝。
     * Reserved bit mask (bit5..bit7): reserved bits MUST be 0 or the frame is rejected.
     */
    public static final int FLAG_RESERVED_MASK = 0xE0;

    // ---- 完整性子槽大小 / integrity-slot sizes ----
    /** 快速完整性子槽字节数。Fast-integrity slot size in bytes. */
    public static final int INTEGRITY_FAST_BYTES = 8;
    /** 强完整性子槽字节数。Strong-integrity slot size in bytes. */
    public static final int INTEGRITY_STRONG_BYTES = 32;

    // ---- ext TLV 键（u16 BE 键，u16 BE 值长）/ ext TLV keys (u16 BE key, u16 BE length) ----
    /** ext TLV: tsha1 模型。tsha1 model. */
    public static final int EXT_TSHA1_MODEL = 1;
    /** ext TLV: tsha1 位宽。tsha1 bits. */
    public static final int EXT_TSHA1_BITS = 2;
    /** ext TLV: 压缩算法。compression algorithm. */
    public static final int EXT_COMPRESS_ALGO = 3;
    /** ext TLV: 加密算法。encryption algorithm. */
    public static final int EXT_ENC_ALGO = 4;
    /** ext TLV: 流会话 id。stream session id. */
    public static final int EXT_STREAM_SESSION = 5;
    /** ext TLV: 流序号。stream sequence number. */
    public static final int EXT_STREAM_SEQ = 6;
    /** ext TLV: 流总帧数。stream total frames. */
    public static final int EXT_STREAM_TOTAL = 7;
    /** ext TLV: 扩展元数据。extended metadata. */
    public static final int EXT_EXT_META = 9;
}