package io.toterra.subterra.engine.time;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic perception-level interpolation for time-scaled rendering (p.2.26.1,
 * clean-room self-developed). While the logic layer {@link TimeThrottle} skips ticks, the
 * perception layer continues to present smooth motion by linearly interpolating between the
 * last known endpoint values at a clock alpha. All math is pure and side-effect free.
 * <p>
 * Determinism: {@link #interpolate(double, double, double)} is the fixed linear form
 * {@code from + (to - from) * alpha}, so the same inputs always produce the same double
 * bit pattern. {@link #timeScale()}... a builder over fixed per-domain endpoints; the
 * aggregate {@link #snapshot()} returns the fixed-order {@link #values()} enumeration
 * order regardless of the build order. No randomness, no timing, no iteration-order
 * dependence.
 *
 * <p>时间缩放渲染的确定性感知级插值（p.2.26.1，clean-room 自研）。逻辑层 {@link TimeThrottle} 会跳过
 * tick，感知层则按时钟 alpha 在上一个已知端点与目标端点之间线性插值，从而持续呈现平滑运动。全部运算为
 * 纯、无副作用。
 * <p>确定性：{@link #interpolate(double, double, double)} 为固定线性式 {@code from + (to - from) * alpha}，
 * 故同输入恒得同 double 位。顶部对固定按域端点建模；聚合 {@link #snapshot()} 与构建顺序无关，恒以固定
 * {@link #values()} 枚举序返回。无随机、无时序、无迭代顺序依赖。
 */
public final class TimeInterpolation {

    /** Explicit per-domain interpolation endpoints, keyed in fixed enumeration order. */
    private final Map<TimeDomain, Endpoint> endpoints;

    private TimeInterpolation(Map<TimeDomain, Endpoint> endpoints) {
        this.endpoints = endpoints;
    }

    /**
     * Pure linear interpolation {@code from + (to - from) * alpha}. Deterministic: for the
     * same inputs the returned double bit pattern is identical. {@code alpha} is typically in
     * {@code [0, 1]} but any finite value is accepted (extrapolation); all three inputs must
     * be finite.
     * / 纯线性插值 {@code from + (to - from) * alpha}。确定性：同输入恒得同 double 位。
     * {@code alpha} 通常处于 {@code [0, 1]}，但接受任一有限值（外推）；三个输入必须有限。
     *
     * @param from  the start (previous) value. 起始（上一）值。
     * @param to    the end (target) value. 目标值。
     * @param alpha the interpolation factor. 插值因子。
     * @return {@code from + (to - from) * alpha}.
     * @throws IllegalArgumentException if any input is not finite.
     */
    public static double interpolate(double from, double to, double alpha) {
        if (!Double.isFinite(from) || !Double.isFinite(to) || !Double.isFinite(alpha)) {
            throw new IllegalArgumentException("interpolation inputs must be finite");
        }
        return from + (to - from) * alpha;
    }

    /**
     * Builds a {@link TimeInterpolation} from the given per-domain endpoints. The map is
     * normalized to the fixed {@link TimeDomain} enumeration order; absent domains are
     * recorded as neutral endpoints ({@code from = to = 0.0}). Values must be finite.
     * / 由给定按域端点构建 {@link TimeInterpolation}。表统一归一化为固定 {@link TimeDomain} 枚举序；
     * 缺失域记录为中性端点（{@code from = to = 0.0}）。取值必须有限。
     */
    public static TimeInterpolation of(Map<TimeDomain, Endpoint> endpoints) {
        Objects.requireNonNull(endpoints, "endpoints must not be null");
        EnumMap<TimeDomain, Endpoint> normalized = new EnumMap<>(TimeDomain.class);
        for (TimeDomain d : TimeDomain.values()) {
            Endpoint e = endpoints.get(d);
            normalized.put(d, e == null ? new Endpoint(0.0D, 0.0D) : e);
        }
        return new TimeInterpolation(Collections.unmodifiableMap(normalized));
    }

    /**
     * Interpolates the given domain between its stored {@link Endpoint} values at
     * {@code alpha}, using {@link #interpolate(double, double, double)}. Deterministic.
     * / 以 {@code alpha} 在存储的 {@link Endpoint} 值之间对给定域插值，复用
     * {@link #interpolate(double, double, double)}。确定性。
     */
    public double interpolate(TimeDomain domain, double alpha) {
        Objects.requireNonNull(domain, "domain must not be null");
        Endpoint e = endpoints.get(domain);
        return e.interpolate(alpha);
    }

    /**
     * An immutable, fixed-order perception snapshot: all four domains present in true
     * {@link TimeDomain#values()} order with their current {@link Endpoint} values.
     * Deterministic for the same instance.
     * / 固定序感知快照：四域齐备、以真正 {@link TimeDomain#values()} 序给出当前 {@link Endpoint} 值。
     * 对同一实例确定性。
     */
    public Snapshot snapshot() {
        LinkedHashMap<TimeDomain, Endpoint> ordered = new LinkedHashMap<>();
        for (TimeDomain d : TimeDomain.values()) {
            ordered.put(d, endpoints.get(d));
        }
        return new Snapshot(ordered);
    }

    /** A perception snapshot: per-domain endpoints in fixed enumeration order. Immutable.
     *  / 感知快照：按固定枚举序的按域端点。不可变。 */
    public record Snapshot(Map<TimeDomain, Endpoint> endpoints) {

        public Snapshot {
            Objects.requireNonNull(endpoints, "endpoints must not be null");
            LinkedHashMap<TimeDomain, Endpoint> ordered = new LinkedHashMap<>();
            for (TimeDomain d : TimeDomain.values()) {
                Endpoint e = endpoints.get(d);
                if (e == null) {
                    throw new IllegalArgumentException("snapshot missing endpoint for " + d);
                }
                ordered.put(d, e);
            }
            endpoints = Collections.unmodifiableMap(ordered);
        }

        /** The stored endpoint for a domain (never null; all domains present). /
         *  某域存储的端点（非 null；四域齐备）。 */
        public Endpoint get(TimeDomain domain) {
            return endpoints.get(Objects.requireNonNull(domain, "domain must not be null"));
        }
    }

    /**
     * An immutable interpolation endpoint pair. Both values must be finite.
     * / 不可变插值端点对。两个取值必须有限。
     *
     * @param from previous (start) value. 上一（起始）值。
     * @param to   target (end) value. 目标值。
     */
    public record Endpoint(double from, double to) {

        public Endpoint {
            if (!Double.isFinite(from) || !Double.isFinite(to)) {
                throw new IllegalArgumentException("endpoint values must be finite");
            }
        }

        /** Linear interpolation between {@code from} and {@code to} at {@code alpha}. /
         *  {@code from} 与 {@code to} 之间在 {@code alpha} 处的线性插值。 */
        public double interpolate(double alpha) {
            if (!Double.isFinite(alpha)) {
                throw new IllegalArgumentException("alpha must be finite");
            }
            return TimeInterpolation.interpolate(from, to, alpha);
        }
    }
}