package io.toterra.subterra.engine.hub;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 不可变表单结构 —— p.2.23.1 由定型 schema 产出的类型化表单。{@code fields} 为
 * 固定文档序（与 schema {@code fields} 文档序一致，仅 TABLE 根非空，与
 * {@code FormDataGen} 形契约一致）；{@code rootKind}/{@code rootWidget} 表达根行
 * 语义（承 {@code FormDataGen} 形契约）：
 * <ul>
 *   <li><b>TABLE 根：</b>{@code rootKind="table"}、{@code rootWidget="group"}，无独立根行，
 *       渲染即每字段一行。</li>
 *   <li><b>LIST 根（repeat 语义）：</b>{@code rootKind}=元素 kind 规范名
 *       （{@code schema.listOf().tdName()}）、{@code rootWidget="repeat"}，渲染单行
 *       {@code field[] kind <rootKind> widget repeat}。</li>
 *   <li><b>标量根：</b>{@code rootKind}=根 kind 规范名、{@code rootWidget}=固定映射控件，
 *       渲染单行 {@code field kind <rootKind> widget <rootWidget>}。</li>
 * </ul>
 * 构造约束：{@code fields} 不可变固定序（防御拷贝），重复字段名拒绝（确定性契约，
 * 任何合法 schema / form td 都不会产生重复名）。{@code fields()} 返回不可变固定序
 * 列表。
 * <p>
 * Immutable form structure — the typed form derived from a settled schema (p.2.23.1).
 * {@code fields} is in fixed document order (same order as the schema's {@code fields};
 * non-empty only for a TABLE root, matching {@code FormDataGen}'s per-root shapes);
 * {@code rootKind}/{@code rootWidget} express the root-line semantics (carried over
 * from {@code FormDataGen}'s shapes):
 * <ul>
 *   <li><b>TABLE root:</b> {@code rootKind="table"}, {@code rootWidget="group"}, no
 *       standalone root line — rendering is one line per field.</li>
 *   <li><b>LIST root (repeat semantics):</b> {@code rootKind} = the element kind
 *       canonical name ({@code schema.listOf().tdName()}), {@code rootWidget="repeat"},
 *       rendered as a single line {@code field[] kind <rootKind> widget repeat}.</li>
 *   <li><b>Scalar root:</b> {@code rootKind} = the root kind canonical name,
 *       {@code rootWidget} = the fixed-map widget, rendered as a single line
 *       {@code field kind <rootKind> widget <rootWidget>}.</li>
 * </ul>
 * Construction constraints: {@code fields} is immutable and fixed-order (defensive
 * copy); duplicate field names are rejected (determinism contract — no legal schema /
 * form td can produce duplicates). {@code fields()} returns an immutable, fixed-order
 * list.
 *
 * @param fields     字段列表（固定文档序，不可变）/ the field list (fixed document order, immutable).
 * @param rootKind   根 kind 规范名 / the root kind canonical name.
 * @param rootWidget 根控件提示 / the root widget hint.
 */
public record FormStructure(List<FormField> fields, String rootKind, String rootWidget) {

    /**
     * 紧凑构造：null 防御 + 字段列表防御拷贝 + 重复字段名拒绝（O(n) 线性扫描，非 O(n²)）。
     * Compact constructor: null-guards, a defensive copy of the field list, and
     * duplicate-name rejection (a single O(n) linear scan, not O(n²)).
     */
    public FormStructure {
        Objects.requireNonNull(fields, "form fields must not be null");
        Objects.requireNonNull(rootKind, "root kind must not be null");
        Objects.requireNonNull(rootWidget, "root widget must not be null");
        fields = List.copyOf(fields);
        Set<String> seen = new HashSet<>();
        for (FormField f : fields) {
            if (!seen.add(f.name())) {
                throw new IllegalArgumentException("duplicate form field name: " + f.name());
            }
        }
    }
}
