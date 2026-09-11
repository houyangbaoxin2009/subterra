package io.toterra.subterra.engine.time;

import java.util.Objects;

/**
 * An immutable flow-rate assignment for one {@link TimeDomain} (p.2.26.1, clean-room
 * self-developed). The {@code rate} is a dimensionless speed multiplier relative to
 * vanilla time-flow: {@code 1.0} is normal speed (unchanged), {@code > 1} speeds time
 * up within that domain (more ticks per wall-clock unit), {@code < 1} slows it down.
 * A rate must be finite and strictly {@code > 0}.
 * <p>
 * Deterministic and finite: the rate is validated at canonical-constructor time as
 * {@link Double#isFinite(double) finite} and {@code > 0}; {@link #clampRate(double)}
 * clamps any rate into the documented closed range {@code [0.1, 10.0]} so derived
 * flows never leave the support. Same input yields the identical double bits.
 *
 * <p>针对单个 {@link TimeDomain} 的不可变流速档位（p.2.26.1，clean-room 自研）。{@code rate} 是相对
 * 原版时间流速的无量纲倍率：{@code 1.0} 为原速（不变），{@code > 1} 加快该域时间流速（每墙钟单位更多
 * tick），{@code < 1} 减慢。流速必须有限且严格 {@code > 0}。
 * <p>确定性与有限性：流速在规范构造时校验为 {@link Double#isFinite(double) 有限}且 {@code > 0}；
 * {@link #clampRate(double)} 将任意流速收敛到文档化闭区间 {@code [0.1, 10.0]}，使派生流速永不越界。
 * 同输入恒得同 double 位。
 *
 * @param domain the time domain this rate applies to. 该流速所作用的域。
 * @param rate   the flow-rate multiplier (finite, {@code > 0}). 流速倍率（有限、{@code > 0}）。
 */
public record FlowRate(TimeDomain domain, double rate) {

    /** Lower bound of the supported rate range. / 支持流速区间的下界。 */
    public static final double MIN_RATE = 0.1D;
    /** Upper bound of the supported rate range. / 支持流速区间的上界。 */
    public static final double MAX_RATE = 10.0D;

    /**
     * @throws IllegalArgumentException if {@code domain} is null, or {@code rate} is not
     *                                  finite and {@code > 0}. 若 {@code domain} 为 null，
     *                                  或 {@code rate} 非有限且非 {@code > 0} 时抛出。
     */
    public FlowRate {
        Objects.requireNonNull(domain, "domain must not be null");
        if (!Double.isFinite(rate) || rate <= 0.0D) {
            throw new IllegalArgumentException("rate must be finite and > 0: " + rate);
        }
    }

    /**
     * Clamps a rate into the documented closed range {@code [0.1, 10.0]}. Deterministic:
     * for any finite input the result is the nearest value within the support. Non-finite
     * values are mapped at the range edges: {@code NaN} is treated as neutral {@code 1.0},
     * positive infinity as {@link #MAX_RATE} and negative infinity as {@link #MIN_RATE}.
     * / 将流速收敛到文档化闭区间 {@code [0.1, 10.0]}。确定性：任意有限输入的结果为该范围内最近值。
     * 非有限值映射到区间边缘：{@code NaN} 视为中性 {@code 1.0}，正无穷为 {@link #MAX_RATE}，负无穷为
     * {@link #MIN_RATE}。
     */
    public static double clampRate(double rate) {
        if (Double.isNaN(rate)) {
            return 1.0D;
        }
        if (rate >= MAX_RATE) {
            return MAX_RATE;
        }
        if (rate <= MIN_RATE) {
            return MIN_RATE;
        }
        return rate;
    }
}