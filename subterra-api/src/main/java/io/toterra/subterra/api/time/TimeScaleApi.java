package io.toterra.subterra.api.time;

/**
 * p.2.26.2 对外时间缩放 API 门面（final class、静态纯函数、纯 JDK）：确定性接口面，供上层/域模组做时间缩放
 * 插值、逻辑级节流与时钟步长判定。语义与 {@code engine.time} 的 {@code TimeInterpolation}/{@code TimeThrottle}
 * （p.2.26.1）一致——本处为契约与数据面注入，engine 为实现镜像，api 不依赖 engine。
 * <p>确定性：{@link #interpolate(double, double, double)} 为固定线性式 {@code from + (to - from) * alpha}，
 * 同输入恒得同 double 位；{@link #shouldTick} 与 {@link #nextTickDelta} 以固定无歧义运算
 * （{@code rate >= 1} 每 tick 执行，否则每 {@code ceil(1 / rate)} 执行恰好一个）给出纯、可回放决策，无随机、
 * 无墙钟、无迭代顺序依赖。注解 {@code @SafeVarargs}-free、无状态、无副作用。
 * <p>
 * p.2.26.2 the external time-scaling API facade (final class, static pure functions, pure JDK): a
 * deterministic interface surface for upper layers / domain mods to do time-scaled interpolation,
 * logic-level throttling and clock-stride decisions. Semantics match {@code engine.time}
 * {@code TimeInterpolation}/{@code TimeThrottle} (p.2.26.1) — this is the contract and data-injection surface
 * for the engine to mirror as its implementation, and the api does not depend on the engine.
 * <p>Deterministic: {@link #interpolate(double, double, double)} is the fixed linear form
 * {@code from + (to - from) * alpha}, so the same inputs always produce the same double bit pattern;
 * {@link #shouldTick} and {@link #nextTickDelta} give pure, replayable decisions via a fixed, unambiguous
 * arithmetic ({@code rate >= 1} executes every tick, otherwise exactly one in every {@code ceil(1 / rate)}),
 * with no randomness, no wall-clock and no iteration-order dependence. Stateless, side-effect free.
 */
public final class TimeScaleApi {

    /** Lower bound of the supported rate range. / 支持流速区间的下界。 */
    public static final double MIN_RATE = 0.1D;
    /** Upper bound of the supported rate range. / 支持流速区间的上界。 */
    public static final double MAX_RATE = 10.0D;

    private TimeScaleApi() {
    }

    /**
     * Pure linear interpolation {@code from + (to - from) * alpha}. Deterministic: for the same inputs the
     * returned double bit pattern is identical. {@code alpha} is typically in {@code [0, 1]} but any finite
     * value is accepted (extrapolation); all three inputs must be finite.
     * / 纯线性插值 {@code from + (to - from) * alpha}。确定性：同输入恒得同 double 位。{@code alpha} 通常处于
     * {@code [0, 1]}，但接受任一有限值（外推）；三个输入必须有限。
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
     * Whether the logic tick at index {@code tick} should execute at the given {@code rate} (this rate's
     * domain having been resolved by {@link TimeScaleSpec#effective(TimeDomain)}). Deterministic:
     * {@code rate >= 1} executes every tick; otherwise executes exactly when {@code tick % ceil(1 / rate) == 0}.
     * {@code tick} must be {@code >= 0}; a rate that is not finite and {@code > 0} is rejected.
     * / 给定 {@code rate}（该域已经 {@link TimeScaleSpec#effective(TimeDomain)} 解析）下，索引为 {@code tick} 的
     * 逻辑 tick 是否应执行。确定性：{@code rate >= 1} 每 tick 都执行；否则恰在 {@code tick % ceil(1 / rate) == 0}
     * 时执行。{@code tick} 必须 {@code >= 0}；非有限或非 {@code > 0} 的流速被拒绝。
     *
     * @param rate the effective flow rate of the domain (finite, {@code > 0}).
     * @param tick the zero-based tick index ({@code >= 0}).
     * @return {@code true} if the tick should execute.
     * @throws IllegalArgumentException if {@code rate} is not finite and {@code > 0}, or {@code tick < 0}.
     */
    public static boolean shouldTick(double rate, long tick) {
        validateRate(rate);
        if (tick < 0) {
            throw new IllegalArgumentException("tick must be >= 0: " + tick);
        }
        long interval = nextTickDelta(rate);
        return tick % interval == 0L;
    }

    /**
     * The deterministic tick delta between two consecutive executions at the given rate — {@code 1} for
     * {@code rate >= 1}, otherwise {@code ceil(1 / rate)}. This is the exact stride used by
     * {@link #shouldTick(double, long)}. Deterministic for the same input.
     * / 给定流速下两次连续执行之间的确定性 tick 步长——{@code rate >= 1} 时为 {@code 1}，否则为
     * {@code ceil(1 / rate)}。这正是 {@link #shouldTick(double, long)} 所用的精确步长。同输入确定性。
     *
     * @param rate the effective flow rate (finite, {@code > 0}).
     * @return the number of ticks between consecutive executions ({@code >= 1}).
     * @throws IllegalArgumentException if {@code rate} is not finite and {@code > 0}.
     */
    public static long nextTickDelta(double rate) {
        validateRate(rate);
        return interval(rate);
    }

    /**
     * Validates a flow rate as finite and {@code > 0}. Shared by {@link FlowRateSpec} and
     * {@link TimeScaleSpec}. / 将流速校验为有限且 {@code > 0}。由 {@link FlowRateSpec} 与
     * {@link TimeScaleSpec} 共用。
     *
     * @param rate the flow rate to validate.
     * @throws IllegalArgumentException if {@code rate} is not finite and {@code > 0}.
     */
    static void validateRate(double rate) {
        if (!Double.isFinite(rate) || rate <= 0.0D) {
            throw new IllegalArgumentException("flow rate must be finite and > 0: " + rate);
        }
    }

    /** Shared stride computation; {@code rate} is assumed already validated. */
    private static long interval(double rate) {
        if (rate >= 1.0D) {
            return 1L;
        }
        return (long) Math.ceil(1.0D / rate);
    }
}