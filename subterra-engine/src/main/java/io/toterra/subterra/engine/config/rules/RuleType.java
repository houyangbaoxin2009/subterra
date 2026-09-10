package io.toterra.subterra.engine.config.rules;

/**
 * p.2.17.1 确定性规则值类型枚举 —— 类型化规则核心。权威序（也是 {@link #values()} 序）固定为
 * {@code BOOLEAN, INT, FLOAT, STRING, TABLE}，依赖枚举序的逻辑以此序为锚点，绝不重排。每型提供
 * 小写注册名 {@link #form()}（{@code boolean/int/float/string/table}）与 {@link #fromForm(String)}
 * （未知注册名一律 {@link IllegalArgumentException}），以及字符串契约面的接受判定
 * {@link #accepts(String)}：BOOLEAN 只接受 {@code true}/{@code false}；INT 接受可被
 * {@link Long#parseLong} 解析的十进制长整；FLOAT 接受可被 {@link Double#parseDouble} 解析的
 * double；STRING 接受任意非 null 字符串；TABLE 表示 td 表值，契约中按不透明值处理，不接受字符串
 * raw（表值由消费方经 {@code engine.config.TdValue} 路径自行处理）。{@code null} 一律不接受。
 * <p>
 * p.2.17.1 deterministic rule value-type enum — the typed-rule core. The authoritative order
 * (also the {@link #values()} order) is fixed as {@code BOOLEAN, INT, FLOAT, STRING, TABLE}; any
 * logic depending on enum order anchors on it and never reorders. Each type exposes a lowercase
 * registration name {@link #form()} ({@code boolean/int/float/string/table}) and
 * {@link #fromForm(String)} (an unknown name raises {@link IllegalArgumentException}), plus a
 * string-contract acceptance test {@link #accepts(String)}: BOOLEAN accepts only
 * {@code true}/{@code false}; INT accepts a decimal long parseable by {@link Long#parseLong};
 * FLOAT accepts a double parseable by {@link Double#parseDouble}; STRING accepts any non-null
 * string; TABLE denotes a td table value, treated opaquely on this contract surface, and never
 * accepts a string raw (table values are handled by consumers through the
 * {@code engine.config.TdValue} path). {@code null} is never accepted.
 */
public enum RuleType {

    /** 布尔标量：仅 {@code true}/{@code false}。Boolean scalar: only {@code true}/{@code false}. */
    BOOLEAN("boolean"),
    /** 整数标量：十进制长整。Integer scalar: a decimal long. */
    INT("int"),
    /** 浮点标量：double。Float scalar: a double. */
    FLOAT("float"),
    /** 字符串标量：任意非 null 文本。String scalar: any non-null text. */
    STRING("string"),
    /** td 表值，契约中按不透明值处理（不接受字符串 raw）。A td table value, opaque on the contract surface. */
    TABLE("table");

    private final String form;

    RuleType(String form) {
        this.form = form;
    }

    /** 小写注册名。The lowercase registration name. */
    public String form() {
        return form;
    }

    /**
     * 由小写注册名解析类型；未知文本（含 null）一律 {@link IllegalArgumentException}。
     * Resolves the type from its lowercase registration name; unknown text (including null)
     * raises {@link IllegalArgumentException}.
     *
     * @param text 小写注册名 / the lowercase registration name.
     * @return 匹配的类型 / the matching type.
     */
    public static RuleType fromForm(String text) {
        for (RuleType t : values()) {
            if (t.form.equals(text)) {
                return t;
            }
        }
        throw new IllegalArgumentException("unknown rule type form: " + text);
    }

    /**
     * 该类型是否接受给定字符串值（字符串契约面判定）：BOOLEAN 仅 {@code true}/{@code false}；
     * INT 为 {@link Long#parseLong} 可解析；FLOAT 为 {@link Double#parseDouble} 可解析；
     * STRING 接受任意非 null 字符串；TABLE 一律不接受；{@code null} 一律不接受。
     * Whether this type accepts the given string value (string-contract test): BOOLEAN accepts
     * only {@code true}/{@code false}; INT must be parseable by {@link Long#parseLong}; FLOAT
     * must be parseable by {@link Double#parseDouble}; STRING accepts any non-null string; TABLE
     * never accepts; {@code null} is never accepted.
     *
     * @param raw 待判定的字符串值 / the string value under test.
     * @return 是否接受 / whether it is accepted.
     */
    public boolean accepts(String raw) {
        if (raw == null) {
            return false;
        }
        return switch (this) {
            case BOOLEAN -> "true".equals(raw) || "false".equals(raw);
            case INT -> isLong(raw);
            case FLOAT -> isDouble(raw);
            case STRING -> true;
            case TABLE -> false;
        };
    }

    private static boolean isLong(String raw) {
        try {
            Long.parseLong(raw);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isDouble(String raw) {
        try {
            Double.parseDouble(raw);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
