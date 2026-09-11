package io.toterra.subterra.engine.scale;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Data-pack scale rules, expressed in the framework's {@code td} data language. The
 * document shape is
 * <pre>
 *   scale_rules = [ version = 1, entries = [
 *       [ type = "width", tag = "minecraft:zombie", scale = 1.5 ],
 *       ...
 *   ] ]
 * </pre>
 * where each entry maps one entity-type {@code tag} to one dimension
 * {@code type = scale} value (rule "override" semantics, mirroring Pehkui's
 * "highest wins, rules do not stack" behaviour).
 * <p>
 * Deterministic: {@link #fromTd(TdTable)} parses entries in document order with
 * conflict resolution of duplicate {@code type + tag} as <em>first occurrence wins</em>;
 * {@link #toTd()} writes {@code version} then {@code entries} with fixed per-entry field
 * order {@code type, tag, scale}, no timestamps. A document without duplicates round-trips
 * to a stable, byte-identical normalized form.
 *
 * <p>以框架 {@code td} 数据语言表达的数据包缩放规则。文档形态为
 * <pre>
 *   scale_rules = [ version = 1, entries = [
 *       [ type = "width", tag = "minecraft:zombie", scale = 1.5 ],
 *       ...
 *   ] ]
 * </pre>
 * 每条规则把实体类型 {@code tag} 映射为该类型下某一维度 {@code type} 的缩放值（「规则覆盖」语义，
 * 与 Pehkui「高优先级胜出、规则不叠加」行为一致）。
 * <p>确定性：{@link #fromTd(TdTable)} 按文档序解析，重复 {@code type + tag} 采取「首现胜出」；
 * {@link #toTd()} 先写 {@code version} 再写 {@code entries}，每条固定字段序 {@code type, tag, scale}，
 * 无时间戳。无重复的文档可往返为稳定、逐字节一致的规范化形态。
 */
public record ScaleRuleDocument(List<ScaleRuleEntry> entries) {

    /** One scale rule: scale {@code type} of entities matching {@code tag} to {@code scale}.
     *  / 一条缩放规则：将匹配 {@code tag} 的实体的 {@code type} 维度设置为 {@code scale}。 */
    public record ScaleRuleEntry(ScaleType type, String tag, double scale) {

        public ScaleRuleEntry {
            Objects.requireNonNull(type, "type must not be null");
            if (tag == null) {
                throw new IllegalArgumentException("tag must not be null");
            }
            if (!Double.isFinite(scale) || scale <= 0.0D) {
                throw new IllegalArgumentException("rule scale must be finite and > 0: " + scale);
            }
        }
    }

    /**
     * Parses a {@code scale_rules} table. Requires a {@code version = 1} named entry and
     * an {@code entries} table whose array elements are entry tables ({@code type} string,
     * {@code tag} string, {@code scale} number). Duplicate {@code type + tag} entries are
     * dropped, keeping the first occurrence. Deterministic for the same document.
     * / 解析 {@code scale_rules} 表。要求存在 {@code version = 1} 命名项与 {@code entries}
     * 表，其数组元素为规则表（{@code type} 字符串、{@code tag} 字符串、{@code scale} 数字）。
     * 重复 {@code type + tag} 的条目被丢弃，保留首现。对同一文档确定性。
     */
    public static ScaleRuleDocument fromTd(TdTable root) {
        Objects.requireNonNull(root, "root must not be null");
        TdValue version = root.get("version");
        if (version == null || (version instanceof TdValue.Scalar s && s.kind() != TdValue.Kind.INT) || version.asInt() != 1L) {
            throw new IllegalArgumentException("scale_rules must declare version = 1");
        }
        TdValue entriesVal = root.get("entries");
        if (!(entriesVal instanceof TdTable entriesT)) {
            throw new IllegalArgumentException("scale_rules must declare an 'entries' table");
        }
        List<ScaleRuleEntry> rules = new ArrayList<>();
        Set<ScaleKey> seen = new HashSet<>();
        for (TdValue elem : entriesT.elements()) {
            if (!(elem instanceof TdTable entryT)) {
                throw new IllegalArgumentException("each scale rule must be a table");
            }
            ScaleType type = readType(entryT);
            String tag = readString(entryT, "tag", true);
            double scale = readFloat(entryT);
            if (seen.add(new ScaleKey(type, tag))) {
                rules.add(new ScaleRuleEntry(type, tag, scale));
            }
        }
        return new ScaleRuleDocument(List.copyOf(rules));
    }

    private static ScaleType readType(TdTable entry) {
        TdValue v = entry.get("type");
        if (v == null) {
            throw new IllegalArgumentException("scale rule is missing 'type'");
        }
        return ScaleType.fromForm(v.asString());
    }

    private static String readString(TdTable entry, String key, boolean nonBlank) {
        TdValue v = entry.get(key);
        if (v == null) {
            throw new IllegalArgumentException("scale rule is missing '" + key + "'");
        }
        String s = v.asString();
        if (nonBlank && s.isBlank()) {
            throw new IllegalArgumentException("scale rule '" + key + "' must not be blank");
        }
        return s;
    }

    private static double readFloat(TdTable entry) {
        TdValue v = entry.get("scale");
        if (v == null) {
            throw new IllegalArgumentException("scale rule is missing 'scale'");
        }
        double scale = v.asFloat();
        if (!Double.isFinite(scale) || scale <= 0.0D) {
            throw new IllegalArgumentException("scale rule 'scale' must be finite and > 0: " + scale);
        }
        return scale;
    }

    /**
     * Writes this document back to a {@link TdTable}: {@code version} first, then
     * {@code entries} preserving document order with fixed per-entry field order
     * {@code type, tag, scale}. Deterministic and timestamp-free. Feed the result to
     * {@link Td#write(TdTable)} for the canonical td text.
     * / 将本文档写回 {@link TdTable}：先 {@code version}，再按文档序的 {@code entries}，每条固定
     * 字段序 {@code type, tag, scale}。确定性且无时间戳。产物交由 {@link Td#write(TdTable)}
     * 输出规范 td 文本。
     */
    public TdTable toTd() {
        TdTable.Builder b = TdTable.builder();
        b.put("version", TdValue.of(1L));
        TdTable.Builder eb = TdTable.builder();
        for (ScaleRuleEntry e : entries) {
            TdTable.Builder entry = TdTable.builder();
            entry.put("type", TdValue.str(e.type().form()));
            entry.put("tag", TdValue.str(e.tag()));
            entry.put("scale", TdValue.of(e.scale()));
            eb.element(entry.build());
        }
        b.put("entries", eb.build());
        return b.build();
    }

    /** A rule applying to the given entity-type tag; null when none. /
     *  命中给定实体类型标签的规则；无则返回 null。 */
    public ScaleRuleEntry rule(ScaleType type, String tag) {
        for (ScaleRuleEntry e : entries) {
            if (e.type() == type && e.tag().equals(tag)) {
                return e;
            }
        }
        return null;
    }

    /**
     * Applies the rules that match {@code entityTags} to {@code base}, using
     * {@link ScaleData#with(ScaleType, double)} "override" semantics (values replace, are
     * never multiplied), iterating rules in document order so a later matching rule
     * overrides an earlier one for the same dimension. Deterministic: {@code entityTags}
     * membership is order-independent and the rule iteration order is fixed.
     * / 将命中 {@code entityTags} 的规则按 {@link ScaleData#with(ScaleType, double)} 的「覆盖」
     * 语义（替换值、绝不相乘）应用到 {@code base}，按文档序迭代，故对同一维度的后命中规则覆盖先命中
     * 规则。确定性：{@code entityTags} 的成员判定与顺序无关，且规则迭代序固定。
     */
    public ScaleData applyRules(ScaleData base, Set<String> entityTags) {
        Objects.requireNonNull(base, "base must not be null");
        Objects.requireNonNull(entityTags, "entityTags must not be null");
        ScaleData result = base;
        for (ScaleRuleEntry e : entries) {
            if (entityTags.contains(e.tag())) {
                result = result.with(e.type(), e.scale());
            }
        }
        return result;
    }

    private record ScaleKey(ScaleType type, String tag) {
    }
}