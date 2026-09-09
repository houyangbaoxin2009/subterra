package io.toterra.subterra.engine.p2p.plan;

import io.toterra.subterra.engine.network.bandwidth.BandwidthStats;
import io.toterra.subterra.engine.network.strategy.StrategySelector;

import java.util.Objects;

/**
 * p.2.5.6 一次 P2P 通道剖面的不可变描述（编排输入，p2p.plan）：把 p.2.4 已落地的载荷策略与带宽优化
 * 输入形态折射到 P2P 通道语境。字段即 {@link StrategySelector} 的描述符 + {@link BandwidthOptimizer}
 * 的量化统计，一次说清「频度 / 兴趣域 / 载荷种类 / 带宽统计」。
 * <ul>
 *   <li>{@code freq}：频率类（HOT→L1、WARM→L2、COLD→L3，决定性层级）。</li>
 *   <li>{@code domain}：兴趣域（订阅门控依据）。</li>
 *   <li>{@code kindFlags}：载荷种类自由标志（原样透传，不参与层级推导）。</li>
 *   <li>{@code stats}：带宽优化器量化输入（复用 {@link BandwidthStats} 形态，按需包一层，避免改名撞车）。</li>
 * </ul>
 * 纯 JDK、确定性；本类只圈数据，不做决策（决策在 {@link ChannelPlanner}）。
 * <p>
 * p.2.5.6 immutable description of one P2P channel profile (orchestration input, p2p.plan): refracts the
 * p.2.4 payload-strategy and bandwidth-optimizer input shapes into a P2P channel context. The fields are
 * exactly a {@link StrategySelector} descriptor plus a {@link BandwidthOptimizer} quantified stats snapshot,
 * describing frequency / interest domain / payload kind / bandwidth stats in one pass. Pure JDK and
 * deterministic; this type only carries data (decisions belong to {@link ChannelPlanner}).
 *
 * @param freq       频率类 / frequency class (decides the drop-in level)
 * @param domain     兴趣域 / interest domain (subscription-gate key)
 * @param kindFlags  载荷种类自由标志 / free-form positive payload-kind flags
 * @param stats      带宽优化输入 / bandwidth-optimizer quantified input
 */
public record ChannelProfile(
        StrategySelector.Frequency freq,
        String domain,
        int kindFlags,
        BandwidthStats stats) {

    public ChannelProfile {
        Objects.requireNonNull(freq, "freq must not be null");
        Objects.requireNonNull(domain, "domain must not be null");
        if (kindFlags < 0) {
            throw new IllegalArgumentException("kind flags must be non-negative: " + kindFlags);
        }
        Objects.requireNonNull(stats, "bandwidth stats must not be null");
    }
}