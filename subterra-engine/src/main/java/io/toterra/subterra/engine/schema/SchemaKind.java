package io.toterra.subterra.engine.schema;

/**
 * 确定性类型枚举 —— p.2.12.1 td schema 模型的基础 kind 集。权威序（也是
 * {@link #values()} 序与 td 文档契约序）固定为
 * {@code STRING, INT, FLOAT, BOOL, TABLE, LIST}；任何依赖枚举顺序的逻辑都以
 * 此序为锚点，绝不重排。也提供 td 文档中该 kind 的规范化文本名
 * （{@code string/int/float/bool/table/list}），用于 {@link SchemaCodec} 的编解码。
 * <p>
 * Deterministic type enum — the fundamental kind set of the p.2.12.1 td schema
 * model. The authoritative order (which is also the {@link #values()} order and the
 * td-document contract order) is fixed as {@code STRING, INT, FLOAT, BOOL, TABLE, LIST};
 * any logic depending on enum order anchors on this order and never reorders it. It also
 * supplies the canonical text name of a kind in a td document
 * ({@code string/int/float/bool/table/list}) for {@link SchemaCodec} codec use.
 */
public enum SchemaKind {

    /** 字符串 scalar。String scalar. */
    STRING("string"),
    /** 整数 scalar。Integer scalar. */
    INT("int"),
    /** 浮点 scalar。Float scalar. */
    FLOAT("float"),
    /** 布尔 scalar。Boolean scalar. */
    BOOL("bool"),
    /** 表（命名字段集合）。Table (named-field set). */
    TABLE("table"),
    /** 表数组（元素集合，配合 {@link Schema#listOf()} 的标量元素 kind）。Table array. */
    LIST("list");

    private final String tdName;

    SchemaKind(String tdName) {
        this.tdName = tdName;
    }

    /** 该 kind 在 td 文档中的规范化名称。The canonical kind name in a td document. */
    public String tdName() {
        return tdName;
    }

    /**
     * 由 td 规范化名称解析 kind；未知文本返回 {@code null}。Resolves the kind from its canonical td name;
     * returns {@code null} for an unknown text.
     *
     * @param text td 规范化名称 / the canonical td name.
     * @return 匹配的 kind 或 null / the matching kind, or {@code null}.
     */
    public static SchemaKind fromTd(String text) {
        for (SchemaKind k : values()) {
            if (k.tdName.equals(text)) {
                return k;
            }
        }
        return null;
    }
}
