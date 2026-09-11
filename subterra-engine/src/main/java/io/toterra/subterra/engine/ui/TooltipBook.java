package io.toterra.subterra.engine.ui;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Tooltip book registry (p.2.22.2): a td-document-shaped, data-driven collection
 * of {@link TooltipRule}s with an O(1) {@link #lookup(String)}. The concept is
 * inspired by DataTip's data-driven "item → tooltip lines" idea
 * (github.com/cooobird/DataTip, GPL-3.0) — a clean-room reference of the idea
 * only: zero upstream code or assets are included, and the data form is td-ized
 * (tie data) instead of upstream's JSON resource packs. Pure JDK; the registry is
 * immutable once built, with fixed iteration order, no randomness and no timing.
 *
 * <p>Document shape (td, fixed field order, no timestamps): the root table
 * carries a named entry {@code tooltips = [...]} (mirroring the rules-document
 * layer) —
 * <pre>{@code
 * [
 *   tooltips = [
 *     version = 1,
 *     entries = [
 *       [ item = "minecraft:xxx", lines = [ "line1", "line2" ] ],
 *     ],
 *   ],
 * ]
 * }</pre>
 *
 * <p>{@link #fromTd(TdTable)} parses deterministically. Documented order rule:
 * entries keep their input (document) order — not lexicographic; a duplicate
 * {@code item} keeps its first occurrence (first wins). A missing or non-table
 * {@code tooltips}/{@code entries} yields an empty book; a row without a
 * non-blank {@code item} is skipped; a missing or non-table {@code lines} yields
 * an empty line list; non-scalar line elements are skipped (opaque on the string
 * contract). The {@code version} entry is metadata — written as {@code 1} by
 * {@link #toTd(List)} and not enforced on parse (schema-free).
 *
 * <p>{@link #toTd(List)} writes the canonical td text: a root {@code tooltips =
 * [...]} with {@code version = 1} then {@code entries = [...]}; each entry is a
 * nested table with {@code item} then {@code lines} (fixed field order), in the
 * given list order; a null rules/rule/line, a blank item id, or a duplicate item
 * id raises {@link IllegalArgumentException}. The output parses with
 * {@link Td#parse} and round-trips byte-identically through {@link #fromTd}:
 * same input, same bytes, run any number of times.
 *
 * <p>Tooltip 书籍注册表（p.2.22.2）：td 文档形态、数据驱动的 {@link TooltipRule} 集合，
 * 提供 O(1) {@link #lookup(String)}。概念受 DataTip 的"物品 → tooltip 行"数据驱动思想启发
 * （github.com/cooobird/DataTip，GPL-3.0）——仅为该思想的 clean-room 参考：不含任何上游
 * 代码或资产，数据形态 td 化（tie data），而非上游的 JSON 资源包。纯 JDK；注册表一经构建
 * 不可变，迭代序固定，无随机无时序。
 *
 * <p>文档形态（td，固定字段序，禁时间戳）：根表含命名条目 {@code tooltips = [...]}
 * （与规则文档层同构）——
 * <pre>{@code
 * [
 *   tooltips = [
 *     version = 1,
 *     entries = [
 *       [ item = "minecraft:xxx", lines = [ "line1", "line2" ] ],
 *     ],
 *   ],
 * ]
 * }</pre>
 *
 * <p>{@link #fromTd(TdTable)} 确定性解析。文档化排序规则：条目保持输入（文档）序——
 * 非字典序；重复 {@code item} 首现胜出。{@code tooltips}/{@code entries} 缺失或非表 →
 * 空书籍；缺少非空白 {@code item} 的行跳过；{@code lines} 缺失或非表 → 空行列表；非标量
 * 行元素跳过（字符串契约面上不透明）。{@code version} 为元数据——由 {@link #toTd(List)}
 * 写成 {@code 1}，解析时不做强制（schema-free）。
 *
 * <p>{@link #toTd(List)} 写出规范 td 文本：根表 {@code tooltips = [...]}，含
 * {@code version = 1} 与 {@code entries = [...]}；每条目为嵌套表，字段序固定为
 * {@code item} 后 {@code lines}，按给定列表序；null 的 rules/rule/line、空白 item id 或
 * 重复 item id 抛 {@link IllegalArgumentException}。输出可用 {@link Td#parse} 解析，经
 * {@link #fromTd} 逐字节往返恒等：同输入、同字节、跑任意次。
 */
public final class TooltipBook {

    private final List<TooltipRule> rules;
    private final Map<String, TooltipRule> byId;

    private TooltipBook(List<TooltipRule> rules) {
        this.rules = List.copyOf(rules);
        this.byId = new LinkedHashMap<>();
        for (TooltipRule rule : rules) {
            byId.putIfAbsent(rule.itemId(), rule); // first wins
        }
    }

    /**
     * Parses the {@code tooltips} document into a book (see class javadoc for the
     * documented order rule and skip semantics). 将 {@code tooltips} 文档解析为书籍
     * （排序规则与跳过语义见类注释）。
     *
     * @param doc the tooltip document root table. tooltip 文档根表。
     * @return the deterministic, first-wins book. 确定性、首现胜出的书籍。
     */
    public static TooltipBook fromTd(TdTable doc) {
        if (doc == null) {
            throw new IllegalArgumentException("doc must be non-null");
        }
        if (!(doc.get("tooltips") instanceof TdTable tooltips)) {
            return new TooltipBook(List.of());
        }
        if (!(tooltips.get("entries") instanceof TdTable entries)) {
            return new TooltipBook(List.of());
        }
        Map<String, TooltipRule> firstWins = new LinkedHashMap<>();
        for (TdValue item : entries.elements()) {
            if (!(item instanceof TdTable row)) {
                continue;
            }
            String itemId = scalarString(row.get("item"));
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            List<String> lines = row.get("lines") instanceof TdTable linesTable
                    ? linesOf(linesTable)
                    : List.of();
            firstWins.putIfAbsent(itemId, new TooltipRule(itemId, lines));
        }
        return new TooltipBook(List.copyOf(firstWins.values()));
    }

    /**
     * Writes the canonical td text of the given rules (see class javadoc: fixed
     * field order, {@code version = 1}, input list order, no timestamps).
     * 将给定规则写出规范 td 文本（见类注释：固定字段序、{@code version = 1}、输入列表序、
     * 禁时间戳）。
     *
     * @param rules the rules to write, in the desired document order. 待写规则，按期望文档序。
     * @return the canonical td text. 规范 td 文本。
     */
    public static String toTd(List<TooltipRule> rules) {
        if (rules == null) {
            throw new IllegalArgumentException("rules must be non-null");
        }
        TdTable.Builder entries = TdTable.builder();
        Set<String> seen = new java.util.HashSet<>();
        for (TooltipRule rule : rules) {
            if (rule == null) {
                throw new IllegalArgumentException("tooltip rule must be non-null");
            }
            if (!seen.add(rule.itemId())) {
                throw new IllegalArgumentException("duplicate tooltip item: " + rule.itemId());
            }
            TdTable.Builder lines = TdTable.builder();
            for (String line : rule.lines()) {
                if (line == null) {
                    throw new IllegalArgumentException("tooltip line must be non-null: " + rule.itemId());
                }
                lines.element(TdValue.str(line));
            }
            entries.element(TdTable.builder()
                    .put("item", rule.itemId())
                    .put("lines", lines.build())
                    .build());
        }
        TdTable root = TdTable.builder()
                .put("tooltips", TdTable.builder()
                        .put("version", TdValue.of(1L))
                        .put("entries", entries.build())
                        .build())
                .build();
        return Td.write(root);
    }

    /**
     * The tooltip lines for the given item id; {@link List#of()} when the id is
     * not registered (a hit with an empty line list also yields the empty list).
     * O(1), deterministic. 给定物品 id 的 tooltip 行；id 未注册时返回 {@link List#of()}
     * （命中但行为空同样返回空列表）。O(1)，确定性。
     *
     * @param itemId the item id, non-null. 物品 id，非空。
     * @return the lines of the first-wins rule, or the empty list on a miss.
     *         首现规则的各行，未命中时为空列表。
     */
    public List<String> lookup(String itemId) {
        Objects.requireNonNull(itemId, "itemId must be non-null");
        TooltipRule rule = byId.get(itemId);
        return rule == null ? List.of() : rule.lines();
    }

    /**
     * The registered rules in first-wins document order (immutable view).
     * 首现胜出、文档序的已注册规则（不可变视图）。
     */
    public List<TooltipRule> rules() {
        return rules;
    }

    /**
     * Number of registered rules. 已注册规则数。
     */
    public int size() {
        return rules.size();
    }

    private static List<String> linesOf(TdTable linesTable) {
        List<String> out = new ArrayList<>();
        for (TdValue element : linesTable.elements()) {
            String line = scalarString(element);
            if (line != null) {
                out.add(line); // non-scalar elements skipped (opaque)
            }
        }
        return List.copyOf(out);
    }

    /**
     * Canonical scalar rendering of a td value (same contract as the rules
     * document layer: STRING as-is / INT decimal / FLOAT via Double.toString /
     * BOOL as true|false); {@code null} for missing or table values.
     * td 标量的规范字符串渲染（与规则文档层同契约：STRING 原样 / INT 十进制 / FLOAT 的
     * Double.toString / BOOL 的 true|false）；缺失或表值返回 {@code null}。
     */
    private static String scalarString(TdValue v) {
        return v == null || v instanceof TdTable ? null : v.toString();
    }
}
