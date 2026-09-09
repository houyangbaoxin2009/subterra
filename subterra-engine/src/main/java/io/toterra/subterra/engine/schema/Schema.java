package io.toterra.subterra.engine.schema;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.util.List;
import java.util.Objects;

/**
 * 不可变 schema —— p.2.12.1 td schema 模型的根。构造约束（invariant）在紧凑构造器中强制：
 * 根类型为 {@link SchemaKind#TABLE} 时 {@code fields} 非空且为固定文档序；根为
 * {@link SchemaKind#LIST} 时 {@code listOf} 必填；标量根（string/int/float/bool）时
 * {@code fields} 与 {@code listOf} 皆需为空。违反即视为构造阶段程序错误抛
 * {@link IllegalArgumentException}（数据非法不在这里抛，走 {@link SchemaReport}）。
 * <p>
 * Immutable schema — the root of the p.2.12.1 td schema model. The structural invariant is
 * enforced in the compact constructor: when the root kind is {@link SchemaKind#TABLE},
 * {@code fields} is non-empty and in fixed document order; when the root is {@link SchemaKind#LIST},
 * {@code listOf} is required; for scalar roots (string/int/float/bool) both {@code fields} and
 * {@code listOf} must be empty. A violation is treated as a construction-time program error and
 * raises {@link IllegalArgumentException} (invalid data is not raised here — it flows through
 * {@link SchemaReport}).
 * <p>
 * 提供 {@link #validate(TdTable)}（按 schema 确定性校验一份数据 td 文档，返回
 * {@link SchemaReport}）与 {@link #walk()}（按固定文档序暴露字段）。Deterministic data validation
 * via {@link #validate(TdTable)} and field traversal via {@link #walk()}.
 *
 * @param name   schema 名 / the schema name.
 * @param root   根类型 / the root kind.
 * @param fields 字段列表（仅 TABLE 根非空，固定文档序）/ the field list (non-empty only for a TABLE root, in fixed document order).
 * @param listOf LIST 根的元素 kind（仅 LIST 根非空）/ the element kind of a LIST root (non-empty only for a LIST root).
 */
public record Schema(String name, SchemaKind root, List<SchemaField> fields, SchemaKind listOf) {

    /**
     * 紧凑构造：空值防御 + 结构不变量校验 + 字段列表防御拷贝。Compact constructor: null-guards, the
     * structural invariant check, and a defensive copy of the field list.
     */
    public Schema {
        Objects.requireNonNull(name, "schema name must not be null");
        Objects.requireNonNull(root, "schema root must not be null");
        Objects.requireNonNull(fields, "schema fields must not be null");
        fields = List.copyOf(fields);
        if (root == SchemaKind.TABLE && fields.isEmpty()) {
            throw new IllegalArgumentException("a TABLE-root schema requires at least one field");
        }
        if (root == SchemaKind.LIST) {
            Objects.requireNonNull(listOf, "a LIST-root schema requires listOf");
        } else if (root != SchemaKind.TABLE && (listOf != null || !fields.isEmpty())) {
            throw new IllegalArgumentException("a scalar-root schema must not carry fields or listOf");
        }
        if (root == SchemaKind.TABLE && listOf != null) {
            throw new IllegalArgumentException("a TABLE-root schema must not carry listOf");
        }
    }

    /**
     * 固定序遍历：返回字段列表（不可变，文档序）。Fixed-order traversal: returns the field list
     * (immutable, in document order).
     *
     * @return 文档序字段列表 / the field list in document order.
     */
    public List<SchemaField> walk() {
        return fields;
    }

    /**
     * 按 schema 确定性校验一份数据 td 文档。不抛异常：非法数据一律返回
     * {@code valid=false} 并携带固定 reason（{@code field type mismatch} /
     * {@code missing field} / {@code unknown field}）与 fieldPath。判定顺序确定：
     * TABLE 根按字段文档序先查缺失、再查类型，随后按数据键序拒绝未知字段；LIST 根按元素序查类型；
     * 标量根视为始终通过（无字段可查）。
     * <p>
     * Deterministically validates a data td document against this schema. Never throws: invalid data is
     * always reported as {@code valid=false} with a fixed reason ({@code field type mismatch} /
     * {@code missing field} / {@code unknown field}) and a fieldPath. The ruling order is fixed: a TABLE
     * root checks missing-then-type over fields in document order, then rejects unknown fields in data key
     * order; a LIST root checks element kinds in element order; a scalar root always passes (no fields to
     * check).
     *
     * @param data 待校验的数据 td 文档 / the data td document to validate.
     * @return 确定性校验结果 / the deterministic validation verdict.
     */
    public SchemaReport validate(TdTable data) {
        Objects.requireNonNull(data, "data must not be null");
        switch (root) {
            case LIST -> {
                List<TdValue> elements = data.elements();
                for (int i = 0; i < elements.size(); i++) {
                    if (!matchesScalar(elements.get(i), listOf)) {
                        return SchemaReport.FAIL("field type mismatch", "element[" + i + "]");
                    }
                }
                return SchemaReport.PASS();
            }
            case TABLE -> {
                for (SchemaField f : fields) {
                    TdValue v = data.get(f.name());
                    if (v == null) {
                        return SchemaReport.FAIL("missing field", f.name());
                    }
                    if (!matchesValue(v, f.kind())) {
                        return SchemaReport.FAIL("field type mismatch", f.name());
                    }
                }
                for (String key : data.keys()) {
                    if (!containsField(key)) {
                        return SchemaReport.FAIL("unknown field", key);
                    }
                }
                return SchemaReport.PASS();
            }
            default -> SchemaReport.PASS();
        }
        return SchemaReport.PASS();
    }

    private boolean containsField(String name) {
        for (SchemaField f : fields) {
            if (f.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 表（含 LIST 容器）或标量是否与某 kind 匹配。field kind 为 TABLE/LIST 时要求值为表；标量 kind 则
     * 按 kind 判定（正值语义上允许 INT→FLOAT、INT→BOOL）。Whether a table (incl. LIST container) or scalar
     * matches a kind. A TABLE/LIST field kind requires a table value; a scalar kind is matched by kind
     * (valued semantics allow INT→FLOAT, INT→BOOL).
     */
    private static boolean matchesValue(TdValue v, SchemaKind kind) {
        if (v instanceof TdTable) {
            return kind == SchemaKind.TABLE || kind == SchemaKind.LIST;
        }
        return matchesScalar(v, kind);
    }

    /** 标量是否匹配某标量 kind。Whether a scalar matches a scalar kind. */
    private static boolean matchesScalar(TdValue v, SchemaKind kind) {
        if (v instanceof TdTable) {
            return false;
        }
        TdValue.Scalar s = v.scalar();
        return switch (kind) {
            case STRING -> s.kind() == TdValue.Kind.STRING;
            case INT -> s.kind() == TdValue.Kind.INT;
            case FLOAT -> s.kind() == TdValue.Kind.FLOAT || s.kind() == TdValue.Kind.INT;
            case BOOL -> s.kind() == TdValue.Kind.BOOL || s.kind() == TdValue.Kind.INT;
            default -> false;
        };
    }
}
