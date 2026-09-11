package io.toterra.subterra.engine.time;

/**
 * Deterministic logic-level throttling of time-scaled flows (p.2.26.1, clean-room
 * self-developed). Where a {@link TimeScale} says a domain runs at {@code rate}, the
 * {@code engine.time} scheduler must decide, per tick, whether a simulated (logic) tick
 * should actually execute. This class turns a rate into a pure, replayable decision:
 * <ul>
 *   <li>{@code rate >= 1.0} (full speed or faster): every tick executes;</li>
 *   <li>{@code 0 < rate < 1.0} (slowed): exactly one tick in every
 *       {@code ceil(1 / rate)} executes, so the average rate is achieved
 *       deterministically with no randomness.</li>
 * </ul>
 * All decisions are pure functions of {@code (rate, tick)} with a fixed, unambiguous
 * arithmetic defined by {@link #nextTickDelta(double)}; the same inputs always yield the
 * same output. No wall-clock, no RNG, no iteration-order dependence.
 *
 * <p>确定性逻辑级节流（p.2.26.1，clean-room 自研）。当 {@link TimeScale} 声明某域以 {@code rate} 流速
 * 运行时，{@code engine.time} 调度器必须在每个 tick 决定该模拟（逻辑）tick 是否真正执行。本类把流速
 * 化为纯的、可回放的决策：
 * <ul>
 *   <li>{@code rate >= 1.0}（原速或更快）：每个 tick 都执行；</li>
 *   <li>{@code 0 < rate < 1.0}（减速）：每 {@code ceil(1 / rate)} 个 tick 执行恰好一个，从而以确定性、
 *       无随机方式达成平均流速。</li>
 * </ul>
 * 所有决策都是 {@code (rate, tick)} 的纯函数，由 {@link #nextTickDelta(double)} 定义明确无歧义的固定
 * 运算；同输入恒得同输出。不依赖墙钟、RNG 或迭代顺序。
 */
public final class TimeThrottle {

    private TimeThrottle() {
    }

    /**
     * Whether the logic tick at index {@code tick} should execute at the given {@code rate}
     * (this rate's domain having been resolved by {@link TimeScale#effective(TimeDomain)}).
     * Deterministic: {@code rate >= 1} executes every tick; otherwise executes exactly when
     * {@code tick % ceil(1 / rate) == 0}. {@code tick} must be {@code >= 0}; a rate that is
     * not finite and {@code > 0} is rejected.
     * / 给定 {@code rate}（该域已经 {@link TimeScale#effective(TimeDomain)} 解析）下，索引为
     * {@code tick} 的逻辑 tick 是否应执行。确定性：{@code rate >= 1} 每 tick 都执行；否则恰在
     * {@code tick % ceil(1 / rate) == 0} 时执行。{@code tick} 必须 {@code >= 0}；非有限或非 {@code > 0}
     * 的流速被拒绝。
     *
     * @param rate the effective flow rate of the domain (finite, {@code > 0}).
     * @param tick the zero-based tick index ({@code >= 0}).
     * @return {@code true} if the tick should execute.
     * @throws IllegalArgumentException if {@code rate} is not finite and {@code > 0}, or {@code tick < 0}.
     */
    public static boolean shouldTick(double rate, long tick) {
        if (!Double.isFinite(rate) || rate <= 0.0D) {
            throw new IllegalArgumentException("rate must be finite and > 0: " + rate);
        }
        if (tick < 0) {
            throw new IllegalArgumentException("tick must be >= 0: " + tick);
        }
        long interval = interval(rate);
        return tick % interval == 0L;
    }

    /**
     * The deterministic tick delta between two consecutive executions at the given rate —
     * {@code 1} for {@code rate >= 1}, otherwise {@code ceil(1 / rate)}. This is the exact
     * stride used by {@link #shouldTick(double, long)}. Deterministic for the same input.
     * / 给定流速下两次连续执行之间的确定性 tick 步长——{@code rate >= 1} 时为 {@code 1}，否则为
     * {@code ceil(1 / rate)}。这正是 {@link #shouldTick(double, long)} 所用的精确步长。同输入确定性。
     *
     * @param rate the effective flow rate (finite, {@code > 0}).
     * @return the number of ticks between consecutive executions ({@code >= 1}).
     * @throws IllegalArgumentException if {@code rate} is not finite and {@code > 0}.
     */
    public static long nextTickDelta(double rate) {
        if (!Double.isFinite(rate) || rate <= 0.0D) {
            throw new IllegalArgumentException("rate must be finite and > 0: " + rate);
        }
        return interval(rate);
    }

    /** Shared stride computation; {@code rate} is assumed already validated. */
    private static long interval(double rate) {
        if (rate >= 1.0D) {
            return 1L;
        }
        return (long) Math.ceil(1.0D / rate);
    }
}