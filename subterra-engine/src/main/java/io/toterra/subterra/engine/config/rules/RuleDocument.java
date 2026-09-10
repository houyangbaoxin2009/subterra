package io.toterra.subterra.engine.config.rules;

import io.toterra.subterra.api.config.Rule;
import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * p.2.17.1 规则文档形态（final class，静态工具，纯 JDK）。规则文档 = td 表：根表含命名条目
 * {@code rules = [ [ k = .., v = .. ], ... ]}，每行是带 {@code k}/{@code v} 的嵌套表
 * （字段名与顺序固定：先 k 后 v）。值载体直接复用 {@code api.config.Rule}（record key,value
 * 字符串）——本子项不再造值类型；td 标量值以规范化字符串渲染（STRING 原样 / INT 十进制 /
 * FLOAT 的 Double.toString / BOOL 的 true|false），表值在字符串契约面按不透明处理（fromTd
 * 跳过，消费方经 TdValue 路径自行处理）。语义对齐 {@code DatapackRules.fromManifest}：k 非空白、
 * v 缺失跳过、首现胜出、畸形条目静默跳过。
 * <p>
 * toTd 输出确定性文本：行序 = spec 注册序（键在 values 中者）后接字典序（values 中未在 specs
 * 声明的键）；值一律以双引号字符串写出（禁时间戳）；输出可用 {@link Td#parse} 解析并经
 * {@link #fromTd} 往返恒等。
 * <p>
 * p.2.17.1 rule document shape (final class, static tools, pure JDK). A rule document is a td
 * table: the root table carries a named entry {@code rules = [ [ k = .., v = .. ], ... ]}, each
 * row being a nested table with {@code k}/{@code v} (field names and order fixed: k then v). The
 * value carrier reuses {@code api.config.Rule} (record of key,value strings) directly — this
 * sub-item creates no duplicate value type; td scalars render into canonical strings (STRING
 * as-is / INT decimal / FLOAT via Double.toString / BOOL as true|false), and table values stay
 * opaque on the string contract (fromTd skips them; consumers handle them through the TdValue
 * path). Semantics align with {@code DatapackRules.fromManifest}: a non-blank k is required, a
 * missing v is skipped, first occurrence wins, malformed entries are skipped silently.
 * <p>
 * toTd emits deterministic text: row order = spec registration order (for keys present in
 * values) then lexicographic order (for keys not declared in any spec); every value is written
 * as a double-quoted string (no timestamps); the output parses with {@link Td#parse} and
 * round-trips identically via {@link #fromTd}.
 */
public final class RuleDocument {

    private RuleDocument() {
    }

    /**
     * 读取文档的 {@code rules} 表为规则列表（首现胜出；缺失或非表 → 空列表）。k 非空白、v 缺失
     * 跳过、表值跳过。见类 javadoc。
     * Reads the document's {@code rules} table into a rule list (first occurrence wins; absent or
     * non-table → an empty list). A blank k, a missing v, or a table v is skipped. See the class
     * javadoc.
     *
     * @param doc 规则文档根表 / the rule document root table.
     * @return 确定性规则列表（首现胜出）/ the deterministic rule list (first occurrence wins).
     */
    public static List<Rule> fromTd(TdTable doc) {
        if (doc == null) {
            throw new IllegalArgumentException("doc must be non-null");
        }
        TdValue rulesValue = doc.get("rules");
        if (!(rulesValue instanceof TdTable rulesTable)) {
            return List.of();
        }
        Map<String, Rule> firstWins = new LinkedHashMap<>();
        for (TdValue item : rulesTable.elements()) {
            if (!(item instanceof TdTable row)) {
                continue; // schema-free: skip silently
            }
            TdValue kValue = row.get("k");
            String k = kValue != null ? kValue.asString() : "";
            if (k.isBlank()) {
                continue; // needs a non-blank key
            }
            TdValue vValue = row.get("v");
            if (vValue == null) {
                continue; // missing value → skip
            }
            String v = scalarString(vValue);
            if (v == null) {
                continue; // table value → opaque on the string contract → skip
            }
            firstWins.putIfAbsent(k, new Rule(k, v));
        }
        return List.copyOf(firstWins.values());
    }

    /**
     * 生成规则文档的规范 td 文本：根表 {@code rules = [...]}；行序与字符串化见类 javadoc。
     * 重复 spec 键、null values/键值一律 {@link IllegalArgumentException}；输出可用
     * {@link Td#parse} 解析。
     * Writes the canonical td text of a rule document: root {@code rules = [...]}; row order and
     * stringification as in the class javadoc. A duplicate spec key, or a null values/entry,
     * raises {@link IllegalArgumentException}; the output parses with {@link Td#parse}.
     *
     * @param specs  规则定义（键唯一，行序锚点）/ the rule definitions (unique keys, row-order anchor).
     * @param values 待写值（键 → 字符串值）/ the values to write (key → string value).
     * @return 规范 td 文本（确定性、禁时间戳）/ the canonical td text (deterministic, no timestamps).
     */
    public static String toTd(List<RuleSpec> specs, Map<String, String> values) {
        if (specs == null || values == null) {
            throw new IllegalArgumentException("specs and values must be non-null");
        }
        Set<String> emitted = new HashSet<>();
        List<Map.Entry<String, String>> rows = new ArrayList<>();
        for (RuleSpec spec : specs) {
            if (spec == null) {
                throw new IllegalArgumentException("rule spec must be non-null");
            }
            String k = spec.key().form();
            if (!emitted.add(k)) {
                throw new IllegalArgumentException("duplicate rule spec: " + k);
            }
            if (values.containsKey(k)) {
                rows.add(Map.entry(k, valueOrThrow(values, k)));
            }
        }
        List<String> unknown = new ArrayList<>(values.keySet());
        Collections.sort(unknown);
        for (String k : unknown) {
            if (k == null) {
                throw new IllegalArgumentException("rule key must be non-null");
            }
            if (!emitted.contains(k)) {
                rows.add(Map.entry(k, valueOrThrow(values, k)));
            }
        }
        TdTable.Builder rulesTable = TdTable.builder();
        for (Map.Entry<String, String> row : rows) {
            rulesTable.element(TdTable.builder()
                    .put("k", row.getKey())
                    .put("v", row.getValue())
                    .build());
        }
        TdTable root = TdTable.builder().put("rules", rulesTable.build()).build();
        return Td.write(root);
    }

    /**
     * 文档化示例（bilingual javadoc 与验收引用）：{@link #toTd} 对一个固定小样本的确定性输出，
     * 恒等于 {@link Td#parse} 可解析、{@link #fromTd} 可往返的形态。
     * A documented sample (for the bilingual javadoc and acceptance): the deterministic
     * {@link #toTd} output for a fixed small sample — always parseable by {@link Td#parse} and
     * round-trippable via {@link #fromTd}.
     *
     * @return 固定示例的规范 td 文本 / the canonical td text of a fixed sample.
     */
    public static String sampleDoc() {
        List<RuleSpec> specs = List.of(
                new RuleSpec(new RuleKey("enable_structures"), RuleType.BOOLEAN, "true",
                        "whether world structures generate"),
                new RuleSpec(new RuleKey("max_radius"), RuleType.INT, "128",
                        "maximum scan radius in blocks"));
        return toTd(specs, Map.of("enable_structures", "true", "max_radius", "128"));
    }

    private static String scalarString(TdValue v) {
        return v instanceof TdTable ? null : v.toString();
    }

    private static String valueOrThrow(Map<String, String> values, String key) {
        String v = values.get(key);
        if (v == null) {
            throw new IllegalArgumentException("rule value must be non-null: " + key);
        }
        return v;
    }
}
