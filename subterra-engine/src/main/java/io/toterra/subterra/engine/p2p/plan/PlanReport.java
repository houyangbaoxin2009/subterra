package io.toterra.subterra.engine.p2p.plan;

import io.toterra.subterra.engine.network.bandwidth.BandwidthTechnique;
import io.toterra.subterra.engine.network.strategy.PayloadLevel;

/**
 * p.2.5.6 一次通道规划的决策报告（p2p.plan）：由 stateless {@link ChannelPlanner} 确定性产出，供探针断言
 * 与后续 runtime 壳消费。
 * <ul>
 *   <li>{@code level}：所选载荷层级（经 {@code StrategySelector} 按频度确定性选择）。</li>
 *   <li>{@code technique}：带宽技术建议（{@code BandwidthOptimizer.optimize} 的 argmax 决策）。</li>
 *   <li>{@code encrypt}：是否加密（L3 且局域网可信开关开启）。</li>
 *   <li>{@code encryptionDegraded}：L3 但局域网可信关闭时的降级标记（encrypt=false 且此标记为 true）。</li>
 *   <li>{@code subscribed}：订阅门控（兴趣域是否在所需域名集合中，L1/L2 确定性过滤）。</li>
 *   <li>{@code effect}：P2P 语义标签（level+technique 的固定映射）。</li>
 *   <li>{@code basis}：人类可读的决策依据说明（确定性字符串）。</li>
 * </ul>
 * 事件顺序 deterministic、记录等值按字段；同输入恒同输出。
 * <p>
 * p.2.5.6 decision report of one channel plan (p2p.plan), produced deterministically by the stateless
 * {@link ChannelPlanner} for probe assertions and later runtime-shell consumption: selected level, bandwidth
 * technique, encryption ((L3 ∧ trusted) or a degradation marker), subscription gate, P2P semantic effect, and a
 * human-readable basis. Records compare by field; same input always yields the same report.
 *
 * @param level               所载层级 / selected payload level
 * @param technique           带宽技术建议 / selected bandwidth technique
 * @param encrypt             是否加密 / whether the channel is encrypted
 * @param encryptionDegraded  L3 但未可信的降级标记 / degradation marker (L3 yet untrusted)
 * @param subscribed          订阅门控结果 / subscription-gate result
 * @param effect              P2P 语义标签 / P2P semantic effect label
 * @param basis               决策依据说明 / human-readable decision basis
 */
public record PlanReport(
        PayloadLevel level,
        BandwidthTechnique technique,
        boolean encrypt,
        boolean encryptionDegraded,
        boolean subscribed,
        P2pSemantics effect,
        String basis) {
}