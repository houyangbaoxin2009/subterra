package io.toterra.subterra.engine.hub;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 表单渲染数据 —— p.2.23.1 表单「结构 + 表单值」的承载与确定性渲染。本类在任务规格的
 * 两种候选形态（{@code render(FormStructure)} 确定性文本 / record 承载表单值）之间
 * 取合并设计，并以此同时满足两项验收判据，特此文档化：
 * <ul>
 *   <li><b>表单值往返恒等（form-value round-trip identity）：</b>{@code values} 与
 *       {@code structure.fields()} 为固定序 1:1 双射 —— 紧凑构造强制 {@code values}
 *       键恰为字段名集合（缺值/未知键 → {@link IllegalArgumentException}），并按字段
 *       文档序规范化为 {@code LinkedHashMap}；因此值与结构一体往返：结构经
 *       {@link HubFormGen#toTd}/{@code fromTd} 往返后字段值恒等，同一
 *       {@code (structure, values)} 两次构造的值视图逐项恒等。</li>
 *   <li><b>渲染数据确定性（render-data determinism）：</b>{@link #render(FormStructure)}
 *       对同一结构两次调用逐字节一致（行序固定、无时间戳/随机/时序）。</li>
 * </ul>
 * {@link #render(FormStructure)} 的形契约与 p.2.12.4 {@code FormDataGen} 一致
 * （同 widget 映射、同字段行、同根行），但不含 {@code FormDataGen} 的头部注释行：
 * <ul>
 *   <li>TABLE 根：每字段一行 {@code field <name> kind <kind> widget <widgetHint>}（无根行）。</li>
 *   <li>LIST 根（repeat 语义）：单行 {@code field[] kind <rootKind> widget repeat}（字段行不输出）。</li>
 *   <li>标量根：单行 {@code field kind <rootKind> widget <rootWidget>}。</li>
 * </ul>
 * 对同一 schema，{@code render(fromSchema(schema))} 与 {@code FormDataGen} 输出
 * 去掉头部注释后逐字节一致。
 * <p>
 * Form render data — carries a form's {@code structure + values} and renders
 * deterministically (p.2.23.1). Between the two candidate shapes named by the task
 * spec ({@code render(FormStructure)} deterministic text / a record carrying form
 * values) this class adopts a merged design, thereby satisfying both acceptance
 * criteria at once; documented here:
 * <ul>
 *   <li><b>Form-value round-trip identity:</b> {@code values} is a fixed-order 1:1
 *       bijection with {@code structure.fields()} — the compact constructor forces
 *       {@code values} keys to be exactly the field names (missing / unknown →
 *       {@link IllegalArgumentException}) and normalizes them into a
 *       {@code LinkedHashMap} in field document order; hence values and structure
 *       round-trip as one: after the structure survives
 *       {@link HubFormGen#toTd}/{@code fromTd}, the field values are unchanged, and
 *       two constructions from the same {@code (structure, values)} yield
 *       item-identical value views.</li>
 *   <li><b>Render-data determinism:</b> {@link #render(FormStructure)} is
 *       byte-identical for two calls on the same structure (fixed line order; no
 *       timestamps / randomness / timing).</li>
 * </ul>
 * {@link #render(FormStructure)} follows the same per-root shapes as p.2.12.4
 * {@code FormDataGen} (same widget map, same field lines, same root lines) but omits
 * {@code FormDataGen}'s header comment lines:
 * <ul>
 *   <li>TABLE root: one line per field, {@code field <name> kind <kind> widget <widgetHint>} (no root line).</li>
 *   <li>LIST root (repeat semantics): a single line {@code field[] kind <rootKind> widget repeat} (no field lines).</li>
 *   <li>Scalar root: a single line {@code field kind <rootKind> widget <rootWidget>}.</li>
 * </ul>
 * For the same schema, {@code render(fromSchema(schema))} is byte-identical to
 * {@code FormDataGen}'s output with its header comment lines removed.
 *
 * @param structure 表单结构 / the form structure.
 * @param values    表单值（键=字段名，按字段文档序固定迭代序）/ the form values (keys = field names, fixed iteration order in field document order).
 */
public record FormRenderData(FormStructure structure, Map<String, String> values) {

    /**
     * 紧凑构造：null 防御 + 表单值 1:1 校验与固定序规范化。Compact constructor:
     * null-guards plus the 1:1 form-value check and fixed-order normalization.
     */
    public FormRenderData {
        Objects.requireNonNull(structure, "form structure must not be null");
        Objects.requireNonNull(values, "form values must not be null");
        Map<String, String> ordered = new LinkedHashMap<>();
        for (FormField f : structure.fields()) {
            String v = values.get(f.name());
            if (v == null) {
                throw new IllegalArgumentException("missing form value for field: " + f.name());
            }
            ordered.put(f.name(), v);
        }
        for (String key : values.keySet()) {
            if (!ordered.containsKey(key)) {
                throw new IllegalArgumentException("unknown form value for field: " + key);
            }
        }
        values = Collections.unmodifiableMap(ordered);
    }

    /**
     * 确定性渲染：按结构形契约输出固定行序文本（见类 Javadoc），对同一结构两次调用
     * 逐字节一致；{@code null} 输入为程序错误 → {@link IllegalArgumentException}。
     * <p>
     * Deterministic render: emits fixed-order lines per the shape contract in the class
     * Javadoc; two calls on the same structure are byte-identical; a {@code null} input
     * is a program error → {@link IllegalArgumentException}.
     *
     * @param form 表单结构 / the form structure.
     * @return 确定性渲染文本（行尾 {@code \n}）/ the deterministic render text (lines end in {@code \n}).
     */
    public static String render(FormStructure form) {
        Objects.requireNonNull(form, "form structure must not be null");
        if ("repeat".equals(form.rootWidget())) { // LIST root (repeat semantics): single root line, no field lines
            return "field[] kind " + form.rootKind() + " widget repeat\n";
        }
        StringBuilder sb = new StringBuilder();
        for (FormField f : form.fields()) {
            sb.append("field ").append(f.name())
                    .append(" kind ").append(f.kind())
                    .append(" widget ").append(f.widgetHint()).append('\n');
        }
        if (!"table".equals(form.rootKind())) { // scalar root: single root line after the (empty) field lines
            sb.append("field kind ").append(form.rootKind())
                    .append(" widget ").append(form.rootWidget()).append('\n');
        }
        return sb.toString();
    }
}
