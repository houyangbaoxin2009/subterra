package io.toterra.subterra.engine.network.bandwidth;

import io.toterra.subterra.engine.network.strategy.PayloadLevel;

import java.util.Objects;

/**
 * 带宽削减优化器的量化输入（p.2.4.4）：不可变量的快照，描述通道当前负载特征。
 * {@code diffRatio} 与 {@code stateVolatility} 经 {@link #of} 钳制到 [0,1]，保证确定性；
 * 越界输入不会被静默接受（构建辅助最终收敛到界内）。
 * <ul>
 *   <li>diffRatio：字段变更比例（0..1），全字段变化为 1.0，无变化为 0.0。</li>
 *   <li>stateVolatility：状态变化频率抽象测度（0..1，每秒变化次数归一化，抽象而非精确时钟）。</li>
 *   <li>subscriberCount：兴趣域订阅者数量（≥0）。</li>
 *   <li>impliedLevel：由上层载荷策略推得的层级（{@link PayloadLevel}），映射频率类。</li>
 * </ul>
 * <p>
 * Quantified input for the bandwidth optimizer (p.2.4.4): an immutable snapshot describing the
 * current channel load characteristics. {@code diffRatio} and {@code stateVolatility} are clamped
 * to [0,1] by {@link #of} for determinism; out-of-range inputs are never silently accepted (the
 * builder helper eventually converges them into range).
 * <ul>
 *   <li>diffRatio: fraction of fields changed (0..1), 1.0 = every field changed, 0.0 = unchanged.</li>
 *   <li>stateVolatility: abstract measure of state-change frequency (0..1, state-views per second
 *       normalised, abstract rather than a precise clock).</li>
 *   <li>subscriberCount: number of interest-domain subscribers (≥ 0).</li>
 *   <li>impliedLevel: level inferred downstream by the payload strategy ({@link PayloadLevel}),
 *       mapping to a frequency class.</li>
 * </ul>
 *
 * @param diffRatio        字段变更比例，钳制到 [0,1]。Fraction of fields changed, clamped to [0,1].
 * @param stateVolatility  状态波动率，钳制到 [0,1]。State volatility, clamped to [0,1].
 * @param subscriberCount  兴趣域订阅者数（≥0）。Interest-domain subscriber count (≥ 0).
 * @param impliedLevel     载荷策略推得层级。Payload-strategy-inferred level.
 */
public record BandwidthStats(
        double diffRatio,
        double stateVolatility,
        int subscriberCount,
        PayloadLevel impliedLevel) {

    /**
     * 紧凑构造：跳过钳制（仅在需要已钳制输入时使用；否则用 {@link #of}）。Compact constructor:
     * no clamping (only for callers that already pass clamped inputs; prefer {@link #of}).
     */
    public BandwidthStats {
        Objects.requireNonNull(impliedLevel, "implied payload level must not be null");
        if (subscriberCount < 0) {
            throw new IllegalArgumentException("subscriber count must be non-negative: " + subscriberCount);
        }
    }

    /**
     * 确定性构建：将 diffRatio/stateVolatility 钳制到 [0,1]，subscriberCount 钳制到 ≥0。
     * Clamped, deterministic build: clamps {@code diffRatio}/{@code stateVolatility} to [0,1], and
     * {@code subscriberCount} to {@code ≥ 0}.
     */
    public static BandwidthStats of(
            double diffRatio, double stateVolatility, int subscriberCount, PayloadLevel impliedLevel) {
        Objects.requireNonNull(impliedLevel, "implied payload level must not be null");
        double dr = clamp01(diffRatio);
        double sv = clamp01(stateVolatility);
        int sc = Math.max(0, subscriberCount);
        return new BandwidthStats(dr, sv, sc, impliedLevel);
    }

    /** 钳制到 [0,1]（NaN 视为 0.0，确定性）。Clamp to [0,1] (NaN → 0.0, deterministic). */
    private static double clamp01(double v) {
        if (Double.isNaN(v)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, v));
    }
}