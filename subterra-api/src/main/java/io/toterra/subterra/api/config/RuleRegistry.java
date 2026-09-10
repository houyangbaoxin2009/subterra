package io.toterra.subterra.api.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * p.2.16.2 确定性规则注册 API（final class，静态，纯 JDK）——p.2.17 规则系统前哨。静态注册表
 * 按固定注册序追加层：{@link #register(RuleSet)} 同 key 二次注册一律以 {@link IllegalArgumentException}
 * 拒绝（禁覆盖歧义，与 engine ExportHub 的重复拒绝纪律一致），null 拒绝；{@link #resolve} /
 * {@link #resolveTiered} 实现双层解析（逐层 putAll、later layer 胜出，overrides 最后应用并对全部
 * 胜出），与 engine DatapackRules 语义完全一致（仅值型不同）；{@link #render} 按 key 字典序输出
 * {@code k=v; } 连接、空→空串。{@link #clear()} 供探针隔离。确定性：固定序、无时序、无随机。
 * <p>
 * p.2.16.2 deterministic rule registration API (final class, static, pure JDK) — the p.2.17 rule
 * system forward hook. The static registry appends layers in fixed registration order:
 * {@link #register(RuleSet)} rejects a second registration of the same key with
 * {@link IllegalArgumentException} (no override ambiguity, consistent with the engine
 * {@code ExportHub} duplicate-rejection discipline) and rejects null; {@link #resolve} /
 * {@link #resolveTiered} implement two-tier resolution (layer-by-layer putAll, later layer wins,
 * overrides applied last and winning over everything), semantically identical to the engine
 * {@code DatapackRules} (values differ in type only); {@link #render} emits {@code k=v; } joined in
 * key lexicographic order, empty → {@code ""}. {@link #clear()} isolates probe runs. Deterministic:
 * fixed order, no timing, no randomness.
 */
public final class RuleRegistry {

    private static final List<RuleSet> LAYERS = new ArrayList<>();
    private static final Map<String, String> KEYS = new LinkedHashMap<>();

    private RuleRegistry() {
    }

    /**
     * 固定注册序追加一层；同 key 已注册（或 rules 为 null）以 {@link IllegalArgumentException} 拒绝。
     * Appends a layer in fixed registration order; a key registered before (or a null rules) is
     * rejected with {@link IllegalArgumentException}.
     */
    public static void register(RuleSet rules) {
        if (rules == null) {
            throw new IllegalArgumentException("rules must be non-null");
        }
        for (Rule rule : rules.rules()) {
            if (KEYS.containsKey(rule.key())) {
                throw new IllegalArgumentException("rule already registered: " + rule.key());
            }
        }
        for (Rule rule : rules.rules()) {
            KEYS.put(rule.key(), rule.value());
        }
        LAYERS.add(rules);
    }

    /**
     * 已注册层，按注册序返回不可变副本（注册序可复现）。Registered layers as an immutable copy in
     * registration order (reproducible registration order).
     */
    public static List<RuleSet> layers() {
        return List.copyOf(LAYERS);
    }

    /**
     * 已注册规则的快照（LinkedHashMap，注册序；首现胜出语义不适用——注册期已拒绝同 key）。
     * A snapshot of the registered rules (LinkedHashMap, registration order; first-occurrence
     * semantics is moot — duplicates were rejected at registration time).
     */
    public static Map<String, String> registered() {
        return new LinkedHashMap<>(KEYS);
    }

    /**
     * 双层解析：逐层 putAll（later layer 对同 key 胜出），再 putAll(overrides)（对全部层胜出）；
     * 返回插入序 LinkedHashMap。语义与 {@code DatapackRules.resolve} 一致（值型不同）。
     * Two-tier resolve: putAll layer by layer (a later layer wins the same key), then
     * putAll(overrides) (winning over every layer); returns an insertion-ordered LinkedHashMap.
     * Semantics match {@code DatapackRules.resolve} (values differ in type only).
     */
    public static Map<String, String> resolve(List<RuleSet> layers, RuleSet overrides) {
        if (layers == null || overrides == null) {
            throw new IllegalArgumentException("layers and overrides must be non-null");
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (RuleSet layer : layers) {
            for (Rule rule : layer.rules()) {
                out.put(rule.key(), rule.value());
            }
        }
        for (Rule rule : overrides.rules()) {
            out.put(rule.key(), rule.value());
        }
        return out;
    }

    /**
     * p.2.3.5 双层配置解析：先并入全局层，overrides 对冲突 key 全部胜出（global 入内 → overrides
     * 覆盖）。与 {@code DatapackRules.resolveTiered} 一致。确定、保序（LinkedHashMap）。
     * Two-tier config resolve (p.2.3.5): global layer first, then overrides win every conflicting
     * key (global putAll, then overrides putAll). Matches {@code DatapackRules.resolveTiered}.
     * Deterministic, insertion-ordered (LinkedHashMap).
     */
    public static Map<String, String> resolveTiered(RuleSet global, RuleSet overrides) {
        if (global == null || overrides == null) {
            throw new IllegalArgumentException("global and overrides must be non-null");
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Rule rule : global.rules()) {
            out.put(rule.key(), rule.value());
        }
        for (Rule rule : overrides.rules()) {
            out.put(rule.key(), rule.value());
        }
        return out;
    }

    /**
     * 确定性渲染：key 字典序（TreeMap）输出 {@code k=v; } 连接，空 → 空串，与
     * {@code DatapackRules.render} 对齐。
     * Deterministic render: keys in lexicographic order (TreeMap), {@code k=v} joined by
     * {@code "; "}, empty → {@code ""}, aligned with {@code DatapackRules.render}.
     */
    public static String render(Map<String, String> effective) {
        if (effective == null) {
            throw new IllegalArgumentException("effective must be non-null");
        }
        if (effective.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : new TreeMap<>(effective).entrySet()) {
            if (!first) {
                sb.append("; ");
            }
            first = false;
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    /**
     * 清空静态注册表，供探针隔离（确定性复位）。
     * Clears the static registry for probe isolation (deterministic reset).
     */
    public static void clear() {
        LAYERS.clear();
        KEYS.clear();
    }
}
