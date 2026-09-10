package io.toterra.subterra.api.config;

/**
 * p.2.16.2 单条配置规则——不可变 key→value 绑定（纯 JDK）。{@code key} 为规则名，{@code value}
 * 为字符串型的标量表达：标量值以规范化字符串表达；表格值在此契约面不展开（由消费方在 p.2.17
 * 自行处理）。紧凑构造拒绝 null key/value，保证 Rule 始终良构，null 不会渗入注册表或渲染。
 * <p>
 * p.2.16.2 a single config rule — an immutable key→value binding (pure JDK). {@code key} is the
 * rule name; {@code value} is its string-typed scalar rendering: scalars are expressed in a
 * canonical string form; table values are not expanded on this contract surface (consumers
 * handle them themselves in p.2.17). The compact constructor rejects a null key or value so a
 * {@code Rule} is always well-formed and nulls can never leak into a registry or rendering.
 *
 * @param key   规则名 / the rule name.
 * @param value 规范化字符串值 / the canonical string-typed value.
 */
public record Rule(String key, String value) {

    /**
     * 紧凑构造：key/value 任一为 null 即抛 {@link IllegalArgumentException}。
     * Compact constructor: rejects a null key or value with {@link IllegalArgumentException}.
     */
    public Rule {
        if (key == null || value == null) {
            throw new IllegalArgumentException("rule key and value must be non-null");
        }
    }
}
