package io.toterra.subterra.engine.hub;

import io.toterra.subterra.api.config.Rule;
import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.rules.RuleDocument;
import io.toterra.subterra.engine.config.rules.RuleSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 表单值 ↔ 配置文档转换（p.2.23.2，final class，静态工具，纯 JDK）——Hub 配置编辑面与
 * 热重载面的共享适配层，仅做形状转换、不新增任何存储语义：
 * <ul>
 *   <li>{@link #formValuesToTd(List, Map)}：表单值（{@code Map<String,String>}，键=规则键）→
 *       规则文档 td 文本，直接委托 {@link RuleDocument#toTd}（行序 = spec 注册序 + 未知键
 *       字典序、值一律双引号字符串、禁时间戳、固定字段序，见该 javadoc）。</li>
 *   <li>{@link #tdToFormValues(String)}：规则文档 td 文本 → 有序表单值
 *       （{@code LinkedHashMap}），经 {@link Td#parse} + {@link RuleDocument#fromTd}
 *       （首现胜出、畸形条目静默跳过、表值跳过）。</li>
 * </ul>
 * 往返恒等：{@code tdToFormValues(formValuesToTd(specs, m))} 与 {@code m} 逐项值恒等（返回
 * Map 的迭代序 = 文档行序 = spec 注册序后接未知键字典序，确定性）；同输入两次调用逐字节
 * 一致（连跑两遍同字节）。{@code null} specs/values/tdText 或重复 spec 键（见
 * {@link RuleDocument#toTd}）一律 {@link IllegalArgumentException}。
 * <p>
 * Form-value ↔ config-document conversion (p.2.23.2, final class, static tools, pure JDK) —
 * the shared adapter layer of the Hub config-edit surface and the hot-reload surface; it only
 * converts shapes and adds no storage semantics:
 * <ul>
 *   <li>{@link #formValuesToTd(List, Map)}: form values ({@code Map<String,String>}, keys =
 *       rule keys) → the rule-document td text, delegating directly to
 *       {@link RuleDocument#toTd} (row order = spec registration order + unknown keys in
 *       lexicographic order, every value written as a double-quoted string, no timestamps,
 *       fixed field order; see its javadoc).</li>
 *   <li>{@link #tdToFormValues(String)}: rule-document td text → ordered form values
 *       ({@code LinkedHashMap}), via {@link Td#parse} + {@link RuleDocument#fromTd}
 *       (first occurrence wins, malformed entries skipped silently, table values skipped).</li>
 * </ul>
 * Round-trip identity: {@code tdToFormValues(formValuesToTd(specs, m))} is item-for-item
 * value-identical to {@code m} (the returned Map iterates in document order = spec
 * registration order then unknown keys lexicographically; deterministic); two calls on the
 * same input are byte-identical (same bytes on a second run). A null specs/values/tdText or a
 * duplicate spec key (see {@link RuleDocument#toTd}) raises {@link IllegalArgumentException}.
 */
public final class HubFormValues {

    private HubFormValues() {
    }

    /**
     * 表单值 → 规则文档 td 文本：委托 {@link RuleDocument#toTd}（规格为行序锚点；值为表单值
     * 全集，键=规则键）。确定性、禁时间戳、固定字段序；同输入两次调用逐字节一致。
     * <p>
     * Form values → the rule-document td text: delegates to {@link RuleDocument#toTd}
     * (specs as the row-order anchor; values = the full form-value set, keys = rule keys).
     * Deterministic, no timestamps, fixed field order; two calls on the same input are
     * byte-identical.
     *
     * @param specs       规则定义（键唯一，行序锚点）/ the rule definitions (unique keys, row-order anchor).
     * @param formValues  表单值（键 → 字符串值）/ the form values (key → string value).
     * @return 规范规则文档 td 文本 / the canonical rule-document td text.
     */
    public static String formValuesToTd(List<RuleSpec> specs, Map<String, String> formValues) {
        return RuleDocument.toTd(specs, formValues);
    }

    /**
     * 规则文档 td 文本 → 有序表单值：{@link Td#parse} + {@link RuleDocument#fromTd}（首现胜出、
     * 畸形条目静默跳过、表值跳过）；迭代序 = 文档行序，确定性。{@code null} 文本一律
     * {@link IllegalArgumentException}。
     * <p>
     * Rule-document td text → ordered form values: {@link Td#parse} +
     * {@link RuleDocument#fromTd} (first occurrence wins, malformed entries skipped silently,
     * table values skipped); iteration order = document order, deterministic. A null text
     * raises {@link IllegalArgumentException}.
     *
     * @param tdText 规则文档 td 文本（不可 null）/ the rule-document td text (never null).
     * @return 有序表单值（键 → 字符串值）/ the ordered form values (key → string value).
     */
    public static Map<String, String> tdToFormValues(String tdText) {
        if (tdText == null) {
            throw new IllegalArgumentException("tdText must be non-null");
        }
        List<Rule> rules = RuleDocument.fromTd(Td.parse(tdText));
        Map<String, String> out = new LinkedHashMap<>(rules.size());
        for (Rule rule : rules) {
            out.put(rule.key(), rule.value());
        }
        return out;
    }
}
