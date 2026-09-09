package io.toterra.subterra.engine.session;

/**
 * p.2.11.3 会话命令外部进程帧协议入口的坏帧/无法解码异常（受检）。携带固定的原因常量之一作为
 * 确定性分类，可与代码精确比对；消息可附人类可读细节但分类字段是契约锚点。该异常只在帧层（结构
 * 校验：魔数/版本/保留位/长度/完整性/ext 键/信封）抛，顺序与重放拒绝属于 p.2.11.2 状态机层
 * （{@code SessionMachine} 的 dispatch seq 门禁），不在此异常范围。不引入时间戳/随机/时序，纯
 * 单向失败信号。
 * <p>
 * p.2.11.3 checked exception for a bad or undecodable external-process session frame at the
 * frame-protocol entry. It carries one of the fixed reason constants as a deterministic
 * classification comparable in code; the message may add human-readable detail but the reason
 * field is a contract anchor. The exception is raised only at the frame layer (structure checks:
 * magic / version / reserved bits / length / integrity / ext keys / envelope); ordering &amp;
 * replay rejection belongs to the p.2.11.2 state-machine layer ({@code SessionMachine}'s dispatch
 * seq gate) and is out of scope here. No timestamp / random / timing is introduced — a pure
 * one-way failure signal.
 */
public class SessionFrameException extends Exception {

    /** 帧首两字节非 tink magic {@code "tk"} / first two bytes do not form the tink magic {@code "tk"}. */
    public static final String BAD_MAGIC = "BAD_MAGIC";
    /** 帧版本字节非 tink v2（=2）/ the frame version byte is not tink v2 (=2). */
    public static final String BAD_VERSION = "BAD_VERSION";
    /** 帧保留位 bit5..bit7 非 0 / reserved bits bit5..bit7 are not clear. */
    public static final String RESERVED_BITS = "RESERVED_BITS";
    /** 帧完整性子槽校验不匹配 / the frame integrity slot does not verify. */
    public static final String INTEGRITY = "INTEGRITY";
    /** 帧长度不足或越界 / the frame length is short or out of bounds. */
    public static final String LENGTH = "LENGTH";
    /** 所需 ext TLV 键缺失或不适格（session 标签 / seq / key9 meta） / a required ext TLV key is absent or malformed. */
    public static final String EXT = "EXT";
    /** key9 扩展元数据非法（非规范 UTF-8）或与信封 meta 不一致 / key9 ext meta is invalid or inconsistent. */
    public static final String META = "META";
    /** 帧内信封（zd 文档）无法解码为会话命令（封装 {@link SessionEnvelopeException}）/ the inner envelope failed to decode. */
    public static final String ENVELOPE = "ENVELOPE";

    private final String reason;

    /**
     * 以固定原因常量构造。Constructs with a fixed reason constant.
     *
     * @param reason 原因常量之一 / one of the fixed reason constants.
     */
    public SessionFrameException(String reason) {
        super(reason);
        this.reason = reason;
    }

    /**
     * 以固定原因常量 + 细节构造。Constructs with a fixed reason constant and detail.
     *
     * @param reason 原因常量 / a fixed reason constant.
     * @param detail 人类可读细节 / human-readable detail.
     */
    public SessionFrameException(String reason, String detail) {
        super(reason + ": " + detail);
        this.reason = reason;
    }

    /**
     * 以固定原因常量 + 细节 + 根因构造（用于封装信封解码失败）。Constructs with a fixed reason
     * constant, detail and cause (used to wrap envelope-decode failures).
     *
     * @param reason 原因常量 / a fixed reason constant.
     * @param detail 人类可读细节 / human-readable detail.
     * @param cause  根因（如 {@link SessionEnvelopeException}）/ the underlying cause.
     */
    public SessionFrameException(String reason, String detail, Throwable cause) {
        super(reason + ": " + detail, cause);
        this.reason = reason;
    }

    /** 确定性原因分类。The deterministic reason classification. */
    public String reason() {
        return reason;
    }
}