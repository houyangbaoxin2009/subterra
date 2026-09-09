package io.toterra.subterra.engine.network.bandwidth;

/**
 * 带宽削减技术枚举（p.2.4.4）：增强网络通道上三种互补带宽优化技术，
 * 由 {@link BandwidthOptimizer} 根据量化统计数据选择最优。
 * <ul>
 *   <li>{@link #DIFFERENTIAL_SYNC}：增量差分同步 — 仅发送变更字段，适合高变更率但低波动率场景。</li>
 *   <li>{@link #DECIMATION_INTERPOLATION}：状态降频插值 — 降低采样频率，客户端插值补全，适合低变更率但高波动率场景。</li>
 *   <li>{@link #ON_DEMAND_SUBSCRIBE}：按需兴趣域订阅 — 仅向订阅者发送热点域更新，高订阅者数 + 高波动率场景收益最大。</li>
 * </ul>
 * <p>
 * Bandwidth reduction techniques (p.2.4.4): three complementary bandwidth optimization
 * techniques for the enhanced network channel, selected by {@link BandwidthOptimizer} based
 * on quantified statistics.
 * <ul>
 *   <li>{@link #DIFFERENTIAL_SYNC}: Incremental differential sync — only send changed fields,
 *       best for high change ratio but low volatility scenarios.</li>
 *   <li>{@link #DECIMATION_INTERPOLATION}: State decimation + interpolation — reduce sampling
 *       frequency, client interpolates intermediate states, best for low change ratio but high
 *       volatility scenarios.</li>
 *   <li>{@link #ON_DEMAND_SUBSCRIBE}: On-demand interest-domain subscription — only send hot-domain
 *       updates to subscribed clients, maximum benefit with high subscriber count + high volatility.</li>
 * </ul>
 */
public enum BandwidthTechnique {
    /** 增量差分同步 — 仅发送变更字段。Incremental differential sync — only send changed fields. */
    DIFFERENTIAL_SYNC,
    /** 状态降频插值 — 降低采样频率，客户端插值。State decimation + interpolation — reduce frequency, client interpolates. */
    DECIMATION_INTERPOLATION,
    /** 按需兴趣域订阅 — 仅向订阅者发送更新。On-demand interest-domain subscription — only send to subscribed clients. */
    ON_DEMAND_SUBSCRIBE
}
