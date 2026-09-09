package io.toterra.subterra.engine.schema;

import java.util.Objects;

/**
 * 不可变字段描述 —— p.2.12.1 td schema 模型中的一个命名字段。防御性校验：{@code name} 与
 * {@code kind} 均禁止为 null（构造阶段程序错误 → {@link IllegalArgumentException}）；构造后不可变，
 * 顺序由 {@link Schema#fields()} 的文档序承载，本记录自身不含义序。
 * <p>
 * Immutable field descriptor — one named field in the p.2.12.1 td schema model. Defensive
 * validation: neither {@code name} nor {@code kind} may be null (a construction-time program error
 * → {@link IllegalArgumentException}); immutable once built. Order is carried by the document order
 * of {@link Schema#fields()}; this record itself carries no ordering.
 *
 * @param name 字段名（必填）/ the field name (required).
 * @param kind 字段类型（必填）/ the field kind (required).
 */
public record SchemaField(String name, SchemaKind kind) {

    /**
     * 紧凑构造：校验并（对不可变引用）必要时防御拷贝。Compact constructor: validates and defensively
     * copies (for mutable references) as needed.
     */
    public SchemaField {
        Objects.requireNonNull(name, "field name must not be null");
        Objects.requireNonNull(kind, "field kind must not be null");
    }
}
