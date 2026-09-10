package io.toterra.subterra.engine.config.rules;

/**
 * p.2.17.1 类型化规则定义 —— {@code record(RuleKey key, RuleType type, String defaultValue, String
 * description)}。{@code defaultValue} 若给定（非 null）必须通过 {@code type.accepts}，否则
 * {@link IllegalArgumentException}；{@code null} 表示无默认 → 该规则必填（校验见
 * {@link RuleValidator}）。{@code description} 为人类可读说明（可为空串，不可 null）。
 * <p>
 * 值载体直接复用 {@code api.config.Rule}（record key,value 字符串）——本子项不再造值类型；
 * TABLE 型值在字符串契约面按不透明处理（TABLE 规格无法携带字符串 defaultValue）。
 * <p>
 * p.2.17.1 typed rule definition — {@code record(RuleKey key, RuleType type, String
 * defaultValue, String description)}. A non-null {@code defaultValue} must pass
 * {@code type.accepts}, otherwise {@link IllegalArgumentException}; {@code null} means no default
 * → the rule is required (see {@link RuleValidator}). {@code description} is human-readable
 * (may be empty, never null).
 * <p>
 * The value carrier reuses {@code api.config.Rule} (record of key,value strings) directly — this
 * sub-item creates no duplicate value type; TABLE values stay opaque on the string contract (a
 * TABLE spec cannot carry a string defaultValue).
 *
 * @param key          规则键 / the rule key.
 * @param type         规则值类型 / the rule value type.
 * @param defaultValue 默认值（null = 必填；给定则须通过 {@code type.accepts}）/ the default value
 *                     (null = required; when given, must pass {@code type.accepts}).
 * @param description  人类可读说明（不可 null，可为空串）/ the human-readable description
 *                     (never null, may be empty).
 */
public record RuleSpec(RuleKey key, RuleType type, String defaultValue, String description) {

    /**
     * 紧凑构造：key/type/description 任一为 null，或 defaultValue 非 null 却未通过 type.accepts，
     * 一律 {@link IllegalArgumentException}。
     * Compact constructor: a null key/type/description, or a non-null defaultValue failing
     * {@code type.accepts}, raises {@link IllegalArgumentException}.
     */
    public RuleSpec {
        if (key == null || type == null || description == null) {
            throw new IllegalArgumentException("rule spec key, type and description must be non-null");
        }
        if (defaultValue != null && !type.accepts(defaultValue)) {
            throw new IllegalArgumentException("default value does not match rule type: " + key.form());
        }
    }
}
