package io.toterra.subterra.engine.config.rules;

import java.util.Set;

/**
 * p.2.17.1 单条校验违约 —— {@code record(String ruleName, String message, String kind)}。
 * {@code kind} 取固定词汇，必须是本类声明的四个确定性常量之一（{@link #UNKNOWN_KEY} 未知键 /
 * {@link #TYPE_VIOLATION} 类型违约 / {@link #CONFLICT} 重复提供 / {@link #OTHER} 其他）；紧凑
 * 构造拒绝 null 与未知 kind。消息为固定模板（确定性文本，无时序、无随机）。
 * <p>
 * p.2.17.1 a single validation violation — {@code record(String ruleName, String message, String
 * kind)}. {@code kind} uses the fixed vocabulary and must be one of the four deterministic
 * constants declared here ({@link #UNKNOWN_KEY} unknown key / {@link #TYPE_VIOLATION} type
 * violation / {@link #CONFLICT} duplicate / {@link #OTHER} other); the compact constructor
 * rejects null fields and unknown kinds. Messages are fixed templates (deterministic text, no
 * timing, no randomness).
 *
 * @param ruleName 相关规则键 / the rule key concerned.
 * @param message  固定模板消息 / the fixed-template message.
 * @param kind     违约分类（四个确定性常量之一）/ the violation kind (one of the four constants).
 */
public record RuleViolation(String ruleName, String message, String kind) {

    /** 未知键：specs 中未声明的提供键。Unknown key: a provided key not declared in any spec. */
    public static final String UNKNOWN_KEY = "UNKNOWN_KEY";
    /** 类型违约：提供值未通过 spec 类型 accepts。Type violation: a provided value failing the spec type. */
    public static final String TYPE_VIOLATION = "TYPE_VIOLATION";
    /** 重复提供同 key。The same key provided more than once. */
    public static final String CONFLICT = "CONFLICT";
    /** 其他（如缺失必填规则）。Other (e.g. a missing required rule). */
    public static final String OTHER = "OTHER";

    private static final Set<String> KINDS =
            Set.of(UNKNOWN_KEY, TYPE_VIOLATION, CONFLICT, OTHER);

    /**
     * 紧凑构造：ruleName/message/kind 任一为 null，或 kind 不在四个确定性常量内，一律
     * {@link IllegalArgumentException}。
     * Compact constructor: a null field, or a kind outside the four deterministic constants,
     * raises {@link IllegalArgumentException}.
     */
    public RuleViolation {
        if (ruleName == null || message == null || kind == null) {
            throw new IllegalArgumentException("rule violation fields must be non-null");
        }
        if (!KINDS.contains(kind)) {
            throw new IllegalArgumentException("unknown rule violation kind: " + kind);
        }
    }
}
