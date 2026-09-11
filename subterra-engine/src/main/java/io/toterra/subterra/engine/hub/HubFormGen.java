package io.toterra.subterra.engine.hub;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.schema.Schema;
import io.toterra.subterra.engine.schema.SchemaCodec;
import io.toterra.subterra.engine.schema.SchemaField;
import io.toterra.subterra.engine.schema.SchemaKind;
import io.toterra.subterra.engine.schema.SchemaViolationException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 表单自动生成门面（p.2.23.1，纯 JDK）—— schema → 表单结构 → td 往返的唯一入口。
 * <b>生成路径选型（设计决策，文档化）：</b>直接遍历定型 {@code Schema} 产出类型化
 * {@link FormStructure}，不重复造解析器、也不经 {@code FormDataGen} 文本再解析
 * （保证全程类型化）；kind→widget 固定映射与 p.2.12.4 {@code FormDataGen} 的类
 * Javadoc 契约逐字一致（本门面按同一契约重新声明该固定映射，两者同源同步、不可漂移，
 * 由 {@link FormRenderData#render} 与 {@code FormDataGen} 输出逐字节一致验证）。
 * <p>
 * 表单 td 文档形态（本框架数据一等语言，禁时间戳、固定字段序、逐字节一致）：
 * <pre>
 *   form = [
 *     rootKind = "&lt;kind&gt;",
 *     rootWidget = "&lt;widget&gt;",
 *     fields = [                      ; 仅 TABLE 根非空（空则省略）
 *       [ name = "&lt;name&gt;", kind = "&lt;kind&gt;", widget = "&lt;widget&gt;" ],
 *       ...
 *     ],
 *   ]
 * </pre>
 * {@code toTd}/{@code fromTd} 逐字节往返恒等：{@code fromTd(Td.parse(toTd(s))) == s}。
 * 文档级违约（缺 rootKind/rootWidget、未知 kind、字段缺 name/kind/widget、重复字段名）
 * 在 {@code fromTd} 层抛 {@link IllegalArgumentException}；schema 文档违约经
 * {@code SchemaCodec.parse} 以受检 {@link SchemaViolationException} 传播。
 * <p>
 * Form auto-generation facade (p.2.23.1, pure JDK) — the single entry from schema →
 * form structure → td round-trip. <b>Generation-path choice (a design decision,
 * documented):</b> directly walks the settled {@code Schema} to yield a typed
 * {@link FormStructure}; no parser is rebuilt and no {@code FormDataGen} text is
 * re-parsed (the whole path stays typed). The kind→widget fixed map is verbatim the
 * p.2.12.4 {@code FormDataGen} class-Javadoc contract (this facade re-declares that
 * same fixed map from the same source — they cannot drift; verified by
 * {@link FormRenderData#render} being byte-identical to {@code FormDataGen}'s output).
 * <p>
 * Form td document shape (a first-class data shape of this framework; no timestamps,
 * fixed field order, byte-identical):
 * <pre>
 *   form = [
 *     rootKind = "&lt;kind&gt;",
 *     rootWidget = "&lt;widget&gt;",
 *     fields = [                      ; non-empty only for a TABLE root (omitted when empty)
 *       [ name = "&lt;name&gt;", kind = "&lt;kind&gt;", widget = "&lt;widget&gt;" ],
 *       ...
 *     ],
 *   ]
 * </pre>
 * {@code toTd}/{@code fromTd} round-trip byte-identically:
 * {@code fromTd(Td.parse(toTd(s))) == s}. Document-level violations (missing
 * rootKind/rootWidget, unknown kind, field missing name/kind/widget, duplicate field
 * name) raise {@link IllegalArgumentException} at {@code fromTd}; schema-document
 * violations propagate as the checked {@link SchemaViolationException} from
 * {@code SchemaCodec.parse}.
 */
public final class HubFormGen {

    private HubFormGen() {
    }

    /**
     * 由定型 schema 产出类型化表单结构。TABLE 根按 schema 字段文档序逐字段映射
     * （kind 用 {@code SchemaKind.tdName()} 规范名，widget 用 {@code FormDataGen}
     * 固定映射），根为 {@code table}/{@code group}；LIST 根产出空字段列表 +
     * 元素 kind 规范名 + {@code repeat}（repeat 语义，与 {@code FormDataGen} 单行
     * 形契约一致）；标量根产出空字段列表 + 根 kind 规范名 + 固定映射控件。重复字段名
     * （仅可能来自程序化构造的 schema）由 {@link FormStructure} 构造层拒绝。
     * <p>
     * Derives a typed form structure from a settled schema. A TABLE root maps fields
     * one-by-one in schema document order (kind = the {@code SchemaKind.tdName()}
     * canonical name, widget = the {@code FormDataGen} fixed map) with root
     * {@code table}/{@code group}; a LIST root yields an empty field list + the
     * element kind canonical name + {@code repeat} (repeat semantics, matching
     * {@code FormDataGen}'s single-line shape); a scalar root yields an empty field
     * list + the root kind canonical name + the fixed-map widget. Duplicate field
     * names (possible only from a programmatically built schema) are rejected by the
     * {@link FormStructure} constructor.
     *
     * @param schema 定型 schema / the settled schema.
     * @return 类型化表单结构 / the typed form structure.
     */
    public static FormStructure fromSchema(Schema schema) {
        Objects.requireNonNull(schema, "schema must not be null");
        switch (schema.root()) {
            case TABLE -> {
                List<FormField> fields = new ArrayList<>(schema.fields().size());
                for (SchemaField f : schema.fields()) {
                    fields.add(new FormField(f.name(), f.kind().tdName(), widgetHint(f.kind())));
                }
                return new FormStructure(fields, SchemaKind.TABLE.tdName(), widgetHint(SchemaKind.TABLE));
            }
            case LIST -> {
                return new FormStructure(List.of(), schema.listOf().tdName(), widgetHint(SchemaKind.LIST));
            }
            default -> {
                return new FormStructure(List.of(), schema.root().tdName(), widgetHint(schema.root()));
            }
        }
    }

    /**
     * 便捷入口：从 schema td 文本解析 + 生成（{@code SchemaCodec.parse + fromSchema}）。
     * 文档级违约以受检 {@link SchemaViolationException} 传播。
     * <p>
     * Convenience entry: parse + generate from schema td text
     * ({@code SchemaCodec.parse + fromSchema}). Document-level violations propagate as
     * the checked {@link SchemaViolationException}.
     *
     * @param tdSource schema td 文本 / the schema td text.
     * @return 类型化表单结构 / the typed form structure.
     * @throws SchemaViolationException schema 文档违约 / a schema-document violation.
     */
    public static FormStructure fromSchemaTd(String tdSource) throws SchemaViolationException {
        Objects.requireNonNull(tdSource, "schema td source must not be null");
        return fromSchema(SchemaCodec.parse(tdSource));
    }

    /**
     * 将表单结构序列化为 td 文本（{@code form = [...]} 形态，2 空格缩进，经
     * {@link Td#write}），固定键序/字段序、禁时间戳，同结构两次调用逐字节一致；
     * {@code fields} 为空（LIST/标量根）时省略。与 {@link #fromTd(TdTable)} 逐字节
     * 往返恒等。
     * <p>
     * Serializes a form structure to td text (the {@code form = [...]} shape, 2-space
     * indent, via {@link Td#write}), fixed key/field order, no timestamps, two calls
     * on the same structure are byte-identical; an empty {@code fields} (LIST / scalar
     * root) is omitted. Round-trips byte-identically with {@link #fromTd(TdTable)}.
     *
     * @param form 表单结构 / the form structure.
     * @return td 文本 / the td text.
     */
    public static String toTd(FormStructure form) {
        Objects.requireNonNull(form, "form structure must not be null");
        TdTable.Builder b = TdTable.builder();
        b.put("rootKind", form.rootKind());
        b.put("rootWidget", form.rootWidget());
        if (!form.fields().isEmpty()) {
            TdTable.Builder fields = TdTable.builder();
            for (FormField f : form.fields()) {
                TdTable.Builder fb = TdTable.builder();
                fb.put("name", f.name());
                fb.put("kind", f.kind());
                fb.put("widget", f.widgetHint());
                fields.element(fb.build());
            }
            b.put("fields", fields.build());
        }
        return "form = " + Td.write(b.build());
    }

    /**
     * 将表单 td 文档（{@code rootKind}/{@code rootWidget} 命名项 + 可选 {@code fields}
     * 数组元素）解析为 {@link FormStructure}。文档级违约（缺 rootKind/rootWidget、未知
     * kind、字段缺 name/kind/widget、重复字段名）抛 {@link IllegalArgumentException}；
     * {@code null} 输入为程序错误。键序与字段序均按文档序固定。
     * <p>
     * Parses a form td document (named entries {@code rootKind}/{@code rootWidget} plus
     * an optional {@code fields} array) into a {@link FormStructure}. Document-level
     * violations (missing rootKind/rootWidget, unknown kind, a field missing
     * name/kind/widget, a duplicate field name) raise {@link IllegalArgumentException};
     * a {@code null} input is a program error. Key order and field order follow the
     * document order.
     *
     * @param doc 表单 td 根表 / the form td root table.
     * @return 解析出的表单结构 / the parsed form structure.
     */
    public static FormStructure fromTd(TdTable doc) {
        Objects.requireNonNull(doc, "form td table must not be null");
        TdValue rootKindVal = doc.get("rootKind");
        TdValue rootWidgetVal = doc.get("rootWidget");
        if (rootKindVal == null) {
            throw new IllegalArgumentException("form td missing rootKind");
        }
        if (rootWidgetVal == null) {
            throw new IllegalArgumentException("form td missing rootWidget");
        }
        String rootKind = rootKindVal.asString();
        if (SchemaKind.fromTd(rootKind) == null) {
            throw new IllegalArgumentException("form td unknown rootKind: " + rootKind);
        }
        List<FormField> fields = new ArrayList<>();
        TdValue fieldsVal = doc.get("fields");
        if (fieldsVal instanceof TdTable fieldsTable) {
            Set<String> seen = new HashSet<>();
            for (TdValue element : fieldsTable.elements()) {
                if (!(element instanceof TdTable ft)) {
                    throw new IllegalArgumentException("form td fields element must be a table");
                }
                TdValue nameVal = ft.get("name");
                TdValue kindVal = ft.get("kind");
                TdValue widgetVal = ft.get("widget");
                if (nameVal == null || kindVal == null || widgetVal == null) {
                    throw new IllegalArgumentException("form td field missing name/kind/widget");
                }
                String kind = kindVal.asString();
                if (SchemaKind.fromTd(kind) == null) {
                    throw new IllegalArgumentException("form td unknown field kind: " + kind);
                }
                String name = nameVal.asString();
                if (!seen.add(name)) {
                    throw new IllegalArgumentException("form td duplicate field name: " + name);
                }
                fields.add(new FormField(name, kind, widgetVal.asString()));
            }
        }
        return new FormStructure(fields, rootKind, rootWidgetVal.asString());
    }

    /**
     * kind → widgetHint 固定映射 —— 与 p.2.12.4 {@code FormDataGen} 类 Javadoc 契约
     * 逐字一致（string→{@code text}、int/float→{@code number}、bool→{@code check}、
     * table→{@code group}、list→{@code repeat}），同源同契约、不可漂移。
     * <p>
     * Fixed kind→widgetHint map — verbatim the p.2.12.4 {@code FormDataGen} class-Javadoc
     * contract (string→{@code text}, int/float→{@code number}, bool→{@code check},
     * table→{@code group}, list→{@code repeat}), from the same source contract and
     * thus unable to drift.
     */
    private static String widgetHint(SchemaKind kind) {
        return switch (kind) {
            case STRING -> "text";
            case INT, FLOAT -> "number";
            case BOOL -> "check";
            case TABLE -> "group";
            case LIST -> "repeat";
        };
    }
}
