package io.toterra.subterra.optim.worldgen.pipeline.surface;

import java.util.Objects;

/**
 * A single surface rule (p.1.8.5, self-developed): a condition paired with an
 * action, mirroring the MC {@code SurfaceRules.RuleSource} seam. Pure data.
 * <p>
 * 单条表面规则（p.1.8.5，自研）：条件与动作的组合，对应 MC {@code SurfaceRules.RuleSource}。纯数据。
 *
 * @param condition the predicate deciding whether the action applies
 * @param action    the action to apply when the condition matches
 */
public record SurfaceRule(SurfaceCondition condition, SurfaceAction action) {

    /** Compact constructor enforcing non-null parts. */
    public SurfaceRule {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(action, "action");
    }
}