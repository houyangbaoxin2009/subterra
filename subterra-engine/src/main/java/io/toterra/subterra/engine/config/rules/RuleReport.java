package io.toterra.subterra.engine.config.rules;

import java.util.List;

/**
 * p.2.17.1 确定性校验报告 —— {@code record(List<RuleViolation> violations)}。空列表 = 通过
 * （{@link #ok()} 为 true）；{@link #violationCount()} 为违约数。构造固化为不可变副本；
 * {@link #ok()} 直接由空集判定（无时序、无随机）。非法数据不抛异常，仅以违约列表报告。
 * <p>
 * p.2.17.1 deterministic validation report — {@code record(List<RuleViolation> violations)}. An
 * empty list means passed ({@link #ok()} is true); {@link #violationCount()} is the number of
 * violations. The constructor freezes an immutable copy; {@link #ok()} is derived from emptiness
 * (no timing, no randomness). Invalid data is never raised; it is only reported as violations.
 *
 * @param violations 违约列表（固定序，不可变）/ the violation list (fixed order, immutable).
 */
public record RuleReport(List<RuleViolation> violations) {

    /**
     * 通过工厂（空违约列表）。The pass factory (an empty violation list).
     */
    public static RuleReport pass() {
        return new RuleReport(List.of());
    }

    /**
     * 紧凑构造：null 列表一律 {@link IllegalArgumentException}，并固化为不可变副本。
     * Compact constructor: a null list raises {@link IllegalArgumentException}; the list is
     * frozen into an immutable copy.
     */
    public RuleReport {
        if (violations == null) {
            throw new IllegalArgumentException("violations must be non-null");
        }
        violations = List.copyOf(violations);
    }

    /** 违约数（确定性）。The number of violations (deterministic). */
    public int violationCount() {
        return violations.size();
    }

    /** 空列表 = 通过。An empty list means passed. */
    public boolean ok() {
        return violations.isEmpty();
    }
}
