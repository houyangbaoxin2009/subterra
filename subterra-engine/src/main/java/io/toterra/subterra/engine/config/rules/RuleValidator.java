package io.toterra.subterra.engine.config.rules;

import io.toterra.subterra.api.config.Rule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * p.2.17.1 确定性校验器（final class，静态，纯 JDK）。{@link #validate(Collection, Collection)}
 * 以 {@link RuleSpec} 集合为规则定义、{@code api.config.Rule} 集合为提供值，产出
 * {@link RuleReport}。契约：null 集合/元素、重复 spec 键属程序/定义错误，一律
 * {@link IllegalArgumentException}（与注册表重复拒绝纪律一致）；提供值是文档数据，只报告不抛
 * 异常。违约分类：{@code UNKNOWN_KEY}（specs 未声明的键）、{@code TYPE_VIOLATION}（首供值未
 * 通过 spec 类型 accepts）、{@code CONFLICT}（同 key 提供多次）、{@code OTHER}（spec 无
 * defaultValue 且未提供 → 缺失必填）。
 * <p>
 * 确定性：提供值先按 (key,value) 字典序规范化（对任意 Collection 输入产出同一结果；排序
 * O(n log n)，无 O(n²)），再按 key 分组（组序即 key 字典序）；同键违约按固定 kind 序
 * （UNKNOWN_KEY → TYPE_VIOLATION → CONFLICT）输出；缺失必填随后按 spec key 字典序追加。
 * 重复键以规范化首值做类型校验，并另报 CONFLICT（两个正交事实分别报告）。
 * <p>
 * p.2.17.1 deterministic validator (final class, static, pure JDK).
 * {@link #validate(Collection, Collection)} takes a {@link RuleSpec} collection as the rule
 * definitions and a collection of {@code api.config.Rule} as the provided values, producing a
 * {@link RuleReport}. Contract: null collections/elements and duplicate spec keys are
 * program/definition errors and raise {@link IllegalArgumentException} (consistent with the
 * registry duplicate-rejection discipline); provided values are document data — reported, never
 * raised. Violation kinds: {@code UNKNOWN_KEY} (a provided key not declared in any spec),
 * {@code TYPE_VIOLATION} (the first provided value failing the spec type), {@code CONFLICT} (the
 * same key provided more than once), {@code OTHER} (a spec with no defaultValue that was not
 * provided → missing required).
 * <p>
 * Deterministic: provided values are first normalized in (key,value) lexicographic order (the
 * same result for any Collection input; sorting is O(n log n), no O(n²)), then grouped per key
 * (groups in key lexicographic order); per-key violations follow the fixed kind order
 * (UNKNOWN_KEY → TYPE_VIOLATION → CONFLICT); missing required rules are then appended in
 * spec-key lexicographic order. A duplicated key is type-checked on the canonical first value and
 * separately flagged CONFLICT (two orthogonal facts, both reported).
 */
public final class RuleValidator {

    private RuleValidator() {
    }

    /**
     * 校验提供值对 specs 的合规性；分类与顺序见类 javadoc。null specs/provided、null 元素、重复
     * spec 键一律 {@link IllegalArgumentException}。
     * Validates the provided values against the specs; kinds and order as in the class javadoc.
     * A null specs/provided, a null element, or a duplicate spec key raises
     * {@link IllegalArgumentException}.
     *
     * @param specs    规则定义（键唯一）/ the rule definitions (unique keys).
     * @param provided 提供值（文档数据）/ the provided values (document data).
     * @return 确定性校验报告 / the deterministic validation report.
     */
    public static RuleReport validate(Collection<RuleSpec> specs, Collection<Rule> provided) {
        if (specs == null || provided == null) {
            throw new IllegalArgumentException("specs and provided must be non-null");
        }
        Map<String, RuleSpec> byKey = new LinkedHashMap<>();
        for (RuleSpec spec : specs) {
            if (spec == null) {
                throw new IllegalArgumentException("rule spec must be non-null");
            }
            String k = spec.key().form();
            if (byKey.containsKey(k)) {
                throw new IllegalArgumentException("duplicate rule spec: " + k);
            }
            byKey.put(k, spec);
        }
        List<Rule> ordered = new ArrayList<>(provided.size());
        for (Rule rule : provided) {
            if (rule == null) {
                throw new IllegalArgumentException("provided rule must be non-null");
            }
            ordered.add(rule);
        }
        ordered.sort(Comparator.comparing(Rule::key).thenComparing(Rule::value));

        List<RuleViolation> violations = new ArrayList<>();
        Set<String> providedKeys = new HashSet<>();
        int i = 0;
        while (i < ordered.size()) {
            String key = ordered.get(i).key();
            providedKeys.add(key);
            int j = i;
            while (j < ordered.size() && ordered.get(j).key().equals(key)) {
                j++;
            }
            Rule first = ordered.get(i);
            RuleSpec spec = byKey.get(key);
            if (spec == null) {
                violations.add(new RuleViolation(key,
                        "not declared in any spec", RuleViolation.UNKNOWN_KEY));
            } else if (!spec.type().accepts(first.value())) {
                violations.add(new RuleViolation(key,
                        "value \"" + first.value() + "\" does not match type " + spec.type().form(),
                        RuleViolation.TYPE_VIOLATION));
            }
            if (j - i > 1) {
                violations.add(new RuleViolation(key,
                        "provided " + (j - i) + " times; first occurrence wins",
                        RuleViolation.CONFLICT));
            }
            i = j;
        }
        List<String> missing = byKey.keySet().stream()
                .filter(k -> byKey.get(k).defaultValue() == null && !providedKeys.contains(k))
                .sorted()
                .toList();
        for (String k : missing) {
            violations.add(new RuleViolation(k,
                    "missing required rule: no default value and not provided",
                    RuleViolation.OTHER));
        }
        return new RuleReport(violations);
    }
}
