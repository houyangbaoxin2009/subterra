package io.toterra.subterra.engine.schema;

/**
 * p.2.12.1 td schema 文档违约异常（受检）—— 当一份 schema td 文档无法按契约解析成 {@link Schema}
 * 时抛出。携带固定原因常量之一作为确定性分类，可与代码精确比对；消息可附人类可读细节但原因字段是契约锚点。
 * 只在 {@link SchemaCodec#parse(String)} 的文档解析层抛（未知 kind / 缺 root / 字段 kind / 重复字段）；
 * schema 模型的构造结构错误（如 TABLE 根无字段）属程序错误，走 {@link IllegalArgumentException}，不在本异常范围。
 * 不引入时间戳/随机/时序，纯单向失败信号。
 * <p>
 * p.2.12.1 checked exception raised when a schema td document cannot be parsed into a {@link Schema}
 * per the contract. It carries one of the fixed reason constants as a deterministic classification
 * comparable in code; the message may add human-readable detail but the reason field is a contract anchor.
 * It is raised only at the document-parse layer of {@link SchemaCodec#parse(String)} (unknown kind / missing
 * root / field kind / duplicate field). Structural construction errors of the schema model (e.g. a TABLE
 * root without fields) are program errors and go through {@link IllegalArgumentException} — out of scope
 * here. No timestamp / random / timing is introduced — a pure one-way failure signal.
 */
public class SchemaViolationException extends Exception {

    /** root 行缺失 / the root line is missing. */
    public static final String MISSING_ROOT = "MISSING_ROOT";
    /** 未知 kind 文本（root 或 listOf）/ an unknown kind text (root or listOf). */
    public static final String UNKNOWN_KIND = "UNKNOWN_KIND";
    /** 某字段的 kind 文本未知 / an unknown kind text for a field. */
    public static final String FIELD_KIND = "FIELD_KIND";
    /** fields 块中出现重复字段名 / a duplicate field name appears in the fields block. */
    public static final String DUPLICATE_FIELD = "DUPLICATE_FIELD";

    private final String reason;

    /**
     * 以固定原因常量构造。Constructs with a fixed reason constant.
     *
     * @param reason 原因常量之一 / one of the fixed reason constants.
     */
    public SchemaViolationException(String reason) {
        super(reason);
        this.reason = reason;
    }

    /**
     * 以固定原因常量 + 细节构造。Constructs with a fixed reason constant and detail.
     *
     * @param reason 原因常量 / a fixed reason constant.
     * @param detail 人类可读细节 / human-readable detail.
     */
    public SchemaViolationException(String reason, String detail) {
        super(reason + ": " + detail);
        this.reason = reason;
    }

    /** 确定性原因分类。The deterministic reason classification. */
    public String reason() {
        return reason;
    }
}
