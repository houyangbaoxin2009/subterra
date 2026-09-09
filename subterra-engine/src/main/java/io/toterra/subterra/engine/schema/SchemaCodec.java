package io.toterra.subterra.engine.schema;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.util.Objects;

/**
 * schema 的 td 编解码（p.2.12.1）—— 以 td 文档为 schema 的单一事实源。文档契约为：
 * <pre>
 *   root   = "table" | "string" | "int" | "float" | "bool" | "list"   ; 必填
 *   listOf = "&lt;kind&gt;"                                                ; 可选（LIST 根必填）
 *   fields = [ name = "&lt;kind&gt;", ... ]                              ; 可选（TABLE 根必填），行序即字段序
 * </pre>
 * 解析层违约（未知 kind / 缺 root / 字段 kind / 重复字段）抛受检
 * {@link SchemaViolationException}；{@code null} 输入属程序错误抛 {@link IllegalArgumentException}。
 * 确定性往返是验收判据：{@code parse(toTd(schema))} 与 schema 恒等，且同一 schema 两次
 * {@code toTd} 逐字节一致（编解码皆经由 {@link Td}，键序与字段序固定）。
 * <p>
 * Schema &lt;-&gt; td codec (p.2.12.1) — the td document is the single source of truth for a schema.
 * Document contract:
 * <pre>
 *   root   = "table" | "string" | "int" | "float" | "bool" | "list"   ; required
 *   listOf = "&lt;kind&gt;"                                                ; optional (required for a LIST root)
 *   fields = [ name = "&lt;kind&gt;", ... ]                              ; optional (required for a TABLE root), line order = field order
 * </pre>
 * Document-level violations (unknown kind / missing root / field kind / duplicate field) raise the checked
 * {@link SchemaViolationException}; a {@code null} input is a program error and raises
 * {@link IllegalArgumentException}. A deterministic round-trip is the acceptance criterion:
 * {@code parse(toTd(schema))} equals the schema, and two {@code toTd} calls for the same schema are
 * byte-identical (both codecs run through {@link Td}, with fixed key and field order).
 */
public final class SchemaCodec {

    private SchemaCodec() {
    }

    /**
     * 将一份 schema td 文档解析为 {@link Schema}。解析层违约抛出；建模不变量由
     * {@link Schema} 紧凑构造器强制。Parses a schema td document into a {@link Schema}. Document-level
     * violations throw; modeling invariants are enforced by the {@link Schema} compact constructor.
     *
     * @param tdSource schema td 文本（null → {@link IllegalArgumentException}）/ the schema td text (null → {@link IllegalArgumentException}).
     * @return 解析出的 schema / the parsed schema.
     * @throws SchemaViolationException 文档违约 / a document-level violation.
     */
    public static Schema parse(String tdSource) throws SchemaViolationException {
        Objects.requireNonNull(tdSource, "schema td source must not be null");
        TdTable doc = Td.parse(tdSource);

        TdValue rootVal = doc.get("root");
        if (rootVal == null) {
            throw new SchemaViolationException(SchemaViolationException.MISSING_ROOT, "missing root line");
        }
        SchemaKind root = SchemaKind.fromTd(rootVal.asString());
        if (root == null) {
            throw new SchemaViolationException(SchemaViolationException.UNKNOWN_KIND, "root=" + rootVal.asString());
        }

        SchemaKind listOf = null;
        TdValue listOfVal = doc.get("listOf");
        if (listOfVal != null) {
            listOf = SchemaKind.fromTd(listOfVal.asString());
            if (listOf == null) {
                throw new SchemaViolationException(SchemaViolationException.UNKNOWN_KIND, "listOf=" + listOfVal.asString());
            }
        }

        java.util.List<SchemaField> fields = new java.util.ArrayList<>();
        TdValue fieldsVal = doc.get("fields");
        if (fieldsVal != null && fieldsVal instanceof TdTable fieldsTable) {
            java.util.List<String> duplicates = fieldsTable.duplicates();
            if (!duplicates.isEmpty()) {
                throw new SchemaViolationException(SchemaViolationException.DUPLICATE_FIELD,
                        "field=" + duplicates.get(0));
            }
            for (String fieldName : fieldsTable.keys()) {
                TdValue fieldKindVal = fieldsTable.get(fieldName);
                SchemaKind fieldKind = SchemaKind.fromTd(fieldKindVal == null ? "" : fieldKindVal.asString());
                if (fieldKind == null) {
                    throw new SchemaViolationException(SchemaViolationException.FIELD_KIND, "field=" + fieldName);
                }
                fields.add(new SchemaField(fieldName, fieldKind));
            }
        }
        return new Schema("", root, fields, listOf);
    }

    /**
     * 将 {@link Schema} 序列化为 td 文本（2 空格缩进，经 {@link Td#write}），与 {@link #parse(String)}
     * 逐字节往返恒定。Serializes a {@link Schema} to td text (2-space indent, via {@link Td#write}),
     * byte-stable round-tripping with {@link #parse(String)}.
     *
     * @param schema 待序列化的 schema / the schema to serialize.
     * @return td 文本 / the td text.
     */
    public static String toTd(Schema schema) {
        Objects.requireNonNull(schema, "schema must not be null");
        TdTable.Builder b = TdTable.builder();
        b.put("root", schema.root().tdName());
        if (schema.listOf() != null) {
            b.put("listOf", schema.listOf().tdName());
        }
        if (schema.root() == SchemaKind.TABLE) {
            TdTable.Builder fields = TdTable.builder();
            for (SchemaField f : schema.fields()) {
                fields.put(f.name(), f.kind().tdName());
            }
            b.put("fields", fields.build());
        }
        return Td.write(b.build());
    }
}
