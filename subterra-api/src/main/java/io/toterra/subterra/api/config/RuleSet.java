package io.toterra.subterra.api.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * p.2.16.2 确定性有序规则集合（不可变、固定序，纯 JDK）。构造即固化：{@link #of(List)} 按给定
 * 注册序保留每条规则，同 key 仅保留首现（与 DatapackRules.fromManifest 的 putIfAbsent 语义
 * 对齐）；{@link #rules()} 按注册序返回不可变副本；{@link #get(String)} 首现胜出，缺则返回
 * {@code null}。null 集合/元素一律以 {@link IllegalArgumentException} 拒绝。
 * <p>
 * p.2.16.2 a deterministic ordered rule collection (immutable, fixed order, pure JDK). Order is
 * frozen at construction: {@link #of(List)} keeps every rule in the given registration order,
 * keeping only the first occurrence of a key (mirroring {@code DatapackRules.fromManifest}'s
 * putIfAbsent semantics); {@link #rules()} returns an immutable copy in registration order;
 * {@link #get(String)} lets the first occurrence win and returns {@code null} when absent. A null
 * list or null element is rejected with {@link IllegalArgumentException}.
 */
public final class RuleSet {

    private static final RuleSet EMPTY = new RuleSet(List.of(), Map.of());

    private final List<Rule> rules;
    private final Map<String, String> byKey;

    private RuleSet(List<Rule> rules, Map<String, String> byKey) {
        this.rules = rules;
        this.byKey = byKey;
    }

    /**
     * 由给定列表构造（保注册序；同 key 首现胜出）。null 列表或 null 元素拒绝。
     * Builds from the given list (registration order preserved; first occurrence of a key wins).
     * A null list or null element is rejected.
     */
    public static RuleSet of(List<Rule> rules) {
        if (rules == null) {
            throw new IllegalArgumentException("rules must be non-null");
        }
        Map<String, Rule> unique = new LinkedHashMap<>();
        for (Rule rule : rules) {
            if (rule == null) {
                throw new IllegalArgumentException("rule must be non-null");
            }
            unique.putIfAbsent(rule.key(), rule); // first occurrence wins
        }
        List<Rule> order = List.copyOf(unique.values());
        Map<String, String> byKey = new LinkedHashMap<>();
        for (Rule rule : order) {
            byKey.put(rule.key(), rule.value());
        }
        return new RuleSet(order, Map.copyOf(byKey));
    }

    /**
     * 空规则集（共享不可变实例）。The empty rule set (a shared immutable instance).
     */
    public static RuleSet empty() {
        return EMPTY;
    }

    /**
     * 按注册序返回不可变副本。Returns an immutable copy in registration order.
     */
    public List<Rule> rules() {
        return rules;
    }

    /**
     * 首现胜出的值；缺则 {@code null}。The first-occurrence-winning value, or {@code null} when absent.
     */
    public String get(String key) {
        return byKey.get(key);
    }
}
