package io.toterra.subterra.optim.worldgen.pipeline.surface;

import java.util.List;
import java.util.Objects;

/**
 * Ordered surface-rule evaluator (p.1.8.5, self-developed, deterministic
 * counterpart of the MC {@code SurfaceRules} engine): walks the rule list and
 * applies the first matching rule, falling back to a default state.
 * <p>
 * 有序表面规则求值器（p.1.8.5，自研，MC {@code SurfaceRules} 的确定性等价实现）：按序遍历规则，
 * 命中首条匹配规则并回退到默认状态。
 */
public final class SurfaceEvaluator {

    private SurfaceEvaluator() {
    }

    /**
     * Evaluates the rule list for the given context.
     * <p>
     * Walks the rules in order; for the first rule whose condition matches, returns
     * its action output, or continues to the next rule when that output is
     * {@code null}. If no rule matches (or every match declines), returns
     * {@code defaultState}. O(rules) — no rule is evaluated twice.
     *
     * @param c             the block context
     * @param rules         the ordered rule list (must be non-null)
     * @param defaultState  the fallback block-state id when nothing matches
     * @return the resolved block-state id, never {@code null}
     */
    public static String evaluate(SurfaceContext c, List<SurfaceRule> rules, String defaultState) {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(defaultState, "defaultState");
        for (SurfaceRule rule : rules) {
            if (rule.condition().test(c)) {
                String applied = rule.action().apply(c);
                if (applied != null) {
                    return applied;
                }
                // Declined (null): keep scanning subsequent rules.
            }
        }
        return defaultState;
    }
}