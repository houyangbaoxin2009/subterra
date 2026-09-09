package io.toterra.subterra.engine.session;

/**
 * p.2.11.1 会话命令信封的畸形/无法解码异常（受检）。携带固定的原因常量之一作为分类，
 * 异常消息可附人类可读细节但原因字段是确定性的、可与代码精确比对；不引入任何时间戳 /
 * 随机 / 时序，纯单向失败信号。
 * <p>
 * p.2.11.1 checked exception for a malformed or undecodable session-command envelope. It carries
 * one of the fixed reason constants as its classification; the message may add human-readable
 * detail but the reason is deterministic and exactly comparable in code. No timestamp / random /
 * timing is introduced — a pure one-way failure signal.
 */
public class SessionEnvelopeException extends Exception {

    /** 信封结构畸形：非 zd v2、行序/键位错位、字段 kind 不符、数量不符或格式化非法。 */
    public static final String MALFORMED = "MALFORMED";
    /** 命令动词 ordinal 越界 / 未知。 */
    public static final String UNKNOWN_KIND = "UNKNOWN_KIND";
    /** 信封/载荷长度不足或越界。 */
    public static final String LENGTH = "LENGTH";
    /** 载荷容器段解码失败（Base64 非法）。 */
    public static final String PAYLOAD = "PAYLOAD";

    private final String reason;

    /**
     * 以固定原因常量构造。Constructs with a fixed reason constant.
     *
     * @param reason 原因常量（{@link #MALFORMED} / {@link #UNKNOWN_KIND} / {@link #LENGTH} /
     *              {@link #PAYLOAD}）之一 / one of the fixed reason constants.
     */
    public SessionEnvelopeException(String reason) {
        super(reason);
        this.reason = reason;
    }

    /**
     * 以固定原因常量 + 细节构造。Constructs with a fixed reason constant and detail.
     *
     * @param reason 原因常量 / a fixed reason constant.
     * @param detail 人类可读细节 / human-readable detail.
     */
    public SessionEnvelopeException(String reason, String detail) {
        super(reason + ": " + detail);
        this.reason = reason;
    }

    /** 确定性原因分类。The deterministic reason classification. */
    public String reason() {
        return reason;
    }
}