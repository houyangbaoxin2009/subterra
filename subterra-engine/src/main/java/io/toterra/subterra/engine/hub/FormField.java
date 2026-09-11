package io.toterra.subterra.engine.hub;

import java.util.Objects;

/**
 * 不可变表单字段 —— p.2.23.1 表单结构中的一个命名字段。{@code kind} 一律使用
 * {@code SchemaKind.tdName()} 规范名（{@code string/int/float/bool/table/list}）；
 * {@code widgetHint} 使用 p.2.12.4 {@code FormDataGen} 的固定 kind→widget 映射
 * （string→{@code text}、int/float→{@code number}、bool→{@code check}、
 * table→{@code group}、list→{@code repeat}）—— 由生成层（{@link HubFormGen} /
 * {@code fromTd}）保证，本记录自身仅做 null 防御。顺序由
 * {@link FormStructure#fields()} 的固定文档序承载，本记录自身不含义序。
 * <p>
 * Immutable form field — one named field in a p.2.23.1 form structure.
 * {@code kind} is always the {@code SchemaKind.tdName()} canonical name
 * ({@code string/int/float/bool/table/list}); {@code widgetHint} follows p.2.12.4
 * {@code FormDataGen}'s fixed kind→widget map (string→{@code text},
 * int/float→{@code number}, bool→{@code check}, table→{@code group},
 * list→{@code repeat}) — guaranteed by the generating layer ({@link HubFormGen} /
 * {@code fromTd}); this record itself only null-guards. Order is carried by the fixed
 * document order of {@link FormStructure#fields()}; this record carries none.
 *
 * @param name       字段名（必填，原样照抄 schema）/ the field name (required, copied verbatim from the schema).
 * @param kind       kind 规范名（{@code SchemaKind.tdName()}）/ the canonical kind name ({@code SchemaKind.tdName()}).
 * @param widgetHint 控件提示（{@code FormDataGen} 固定映射）/ the widget hint ({@code FormDataGen} fixed map).
 */
public record FormField(String name, String kind, String widgetHint) {

    /**
     * 紧凑构造：null 防御（构造阶段程序错误 → {@link IllegalArgumentException}）。
     * Compact constructor: null-guards (a construction-time program error →
     * {@link IllegalArgumentException}).
     */
    public FormField {
        Objects.requireNonNull(name, "form field name must not be null");
        Objects.requireNonNull(kind, "form field kind must not be null");
        Objects.requireNonNull(widgetHint, "form field widget hint must not be null");
    }
}
