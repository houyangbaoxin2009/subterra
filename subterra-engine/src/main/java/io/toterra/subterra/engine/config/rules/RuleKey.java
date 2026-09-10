package io.toterra.subterra.engine.config.rules;

/**
 * p.2.17.1 类型化规则键 —— {@code record(String name)}。紧凑构造拒绝 null 与空白名，保证键始终
 * 良构；{@link #form()} 即名称本身（键的唯一确定性文本形态）。纯 JDK。
 * <p>
 * p.2.17.1 typed rule key — {@code record(String name)}. The compact constructor rejects a null
 * or blank name so a key is always well-formed; {@link #form()} is the name itself (the key's one
 * deterministic text form). Pure JDK.
 *
 * @param name 规则名（非空白）/ the rule name (non-blank).
 */
public record RuleKey(String name) {

    /**
     * 紧凑构造：null 或空白名一律 {@link IllegalArgumentException}。
     * Compact constructor: a null or blank name raises {@link IllegalArgumentException}.
     */
    public RuleKey {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("rule key name must be non-null and non-blank");
        }
    }

    /** 键的确定性文本形态（即名称）。The key's deterministic text form (the name itself). */
    public String form() {
        return name;
    }
}
