package io.toterra.subterra.engine.scaffold;

/**
 * p.2.12.2 注册码生成器违约异常（受检）—— 当 {@link RegisterCodeGen} 的生成（parse 后）层无法按确定性
 * 规则为注册接线文本产出有效行时抛出。携带固定原因常量之一作为确定性分类（本子项当前锚定
 * {@link #EMPTY_NAME}），可与代码精确比对；消息可附人类可读细节但原因字段是契约锚点。
 * <p>
 * 本异常专用于本子项「schema → 注册表接线文本」的解析/生成层：模型已经由
 * {@code engine.schema.Schema} 紧凑构造器强制结构合法（字段名非空、LIST 根带 listOf、标量根无字段），
 * 因此本异常只在真实可触达的违约处抛——例如 TABLE 根下某字段名为空/空白（注册目标名缺失）。它不吞并也不
 * 代替 p.2.12.1 的 {@code SchemaViolationException}：原始 td 文本的文档级违约（未知 kind / 缺 root /
 * 重复字段）仍在 {@link RegisterCodeGen#generate(String)} 里作为 {@code SchemaViolationException} 传播。
 * 不引入时间戳 / 随机 / 时序，纯单向失败信号。
 * <p>
 * p.2.12.2 checked exception raised when the generation (post-parse) layer of {@link RegisterCodeGen}
 * cannot produce a valid line of registration-wiring text under the deterministic rules. It carries one
 * of the fixed reason constants (currently anchored at {@link #EMPTY_NAME}) as a deterministic
 * classification comparable in code; the message may add human-readable detail but the reason field is a
 * contract anchor.
 * <p>
 * Scoped to this subitem's {@code schema → register-wiring text} parse/generation layer: the model is
 * already guaranteed structurally valid by the {@code engine.schema.Schema} compact constructor (non-null
 * field names, a LIST root carries listOf, a scalar root carries no fields), so this exception is raised
 * only where a genuine, reachable violation occurs — e.g. a blank / empty field name under a TABLE root
 * (no registration target). It neither absorbs nor replaces the p.2.12.1 {@code SchemaViolationException}:
 * document-level violations of the raw td text (unknown kind / missing root / duplicate field) still
 * propagate as {@code SchemaViolationException} from {@link RegisterCodeGen#generate(String)}. No
 * timestamp / random / timing is introduced — a pure one-way failure signal.
 */
public class GenViolationException extends Exception {

    /** 注册目标名（TABLE 根字段名）为空 / 空白。The registration target name (a TABLE-root field name) is empty / blank. */
    public static final String EMPTY_NAME = "EMPTY_NAME";

    private final String reason;

    /**
     * 以固定原因常量构造。Constructs with a fixed reason constant.
     *
     * @param reason 原因常量之一 / one of the fixed reason constants.
     */
    public GenViolationException(String reason) {
        super(reason);
        this.reason = reason;
    }

    /**
     * 以固定原因常量 + 细节构造。Constructs with a fixed reason constant and detail.
     *
     * @param reason 原因常量 / a fixed reason constant.
     * @param detail 人类可读细节 / human-readable detail.
     */
    public GenViolationException(String reason, String detail) {
        super(reason + ": " + detail);
        this.reason = reason;
    }

    /** 确定性原因分类。The deterministic reason classification. */
    public String reason() {
        return reason;
    }
}