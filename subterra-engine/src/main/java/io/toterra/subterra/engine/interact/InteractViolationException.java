package io.toterra.subterra.engine.interact;

/**
 * p.2.14.1 交互规范 td 文档违约异常（受检）—— 当一份交互规范 td 文档无法按契约解析成 {@link InteractSpec}
 * 时抛出。携带固定原因常量之一作为确定性分类，可与代码精确比对；消息可附人类可读细节但原因字段是契约锚点。
 * 只在 {@link InteractSpecParser#parse(String)} 的文档解析层抛；交互规范模型的构造结构错误（如
 * {@code null} 组件）属程序错误，走 {@link IllegalArgumentException}，不在本异常范围。不引入时间戳/随机/时序，
 * 纯单向失败信号。
 * <p>
 * p.2.14.1 checked exception raised when an interaction-spec td document cannot be parsed into an
 * {@link InteractSpec} per the contract. It carries one of the fixed reason constants as a deterministic
 * classification comparable in code; the message may add human-readable detail but the reason field is a
 * contract anchor. It is raised only at the document-parse layer of {@link InteractSpecParser#parse(String)};
 * structural construction errors of the spec model (e.g. a {@code null} component) are program errors and
 * go through {@link IllegalArgumentException} — out of scope here. No timestamp / random / timing is
 * introduced — a pure one-way failure signal.
 */
public class InteractViolationException extends Exception {

    /** 规范名缺失或为空 / the spec name is missing or blank. */
    public static final String MISSING_NAME = "MISSING_NAME";
    /** 文档形状违约（根字段缺失 / 未知字段 / 表面字段缺失或不含嵌套表）/ a document-shape violation (missing root field / unknown field / a surface field missing or not a nested table). */
    public static final String SHAPE = "SHAPE";
    /** 未知实体 kind 文本 / an unknown entity-kind text. */
    public static final String UNKNOWN_KIND = "UNKNOWN_KIND";
    /** 未知交互动作文本 / an unknown interaction-action text. */
    public static final String UNKNOWN_ACTION = "UNKNOWN_ACTION";
    /** 表面缺少态度字段 / a surface is missing its attitude field. */
    public static final String MISSING_ATTITUDE = "MISSING_ATTITUDE";
    /** 未知态度文本 / an unknown attitude text. */
    public static final String UNKNOWN_ATTITUDE = "UNKNOWN_ATTITUDE";

    private final String reason;

    /**
     * 以固定原因常量构造。Constructs with a fixed reason constant.
     *
     * @param reason 原因常量之一 / one of the fixed reason constants.
     */
    public InteractViolationException(String reason) {
        super(reason);
        this.reason = reason;
    }

    /**
     * 以固定原因常量 + 细节构造。Constructs with a fixed reason constant and detail.
     *
     * @param reason 原因常量 / a fixed reason constant.
     * @param detail 人类可读细节 / human-readable detail.
     */
    public InteractViolationException(String reason, String detail) {
        super(reason + ": " + detail);
        this.reason = reason;
    }

    /** 确定性原因分类。The deterministic reason classification. */
    public String reason() {
        return reason;
    }
}
