package io.toterra.subterra.engine.p2p.plan;

import io.toterra.subterra.engine.network.bandwidth.BandwidthOptimizer;
import io.toterra.subterra.engine.network.bandwidth.BandwidthTechnique;
import io.toterra.subterra.engine.network.crypto.EncryptionConfig;
import io.toterra.subterra.engine.network.strategy.PayloadLevel;
import io.toterra.subterra.engine.network.strategy.PayloadStrategy;
import io.toterra.subterra.engine.network.strategy.StrategySelector;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * p.2.5.6 stateless 通道编排门面（engine.p2p.plan，纯 JDK）：把 p.2.4 已落地的载荷策略 + 带宽优化
 * 细粒度组件确定性编排进 P2P 通道语境。除 {@code final} 常量外无任何实例状态——真正的可变计数器
 * （{@code BandwidthOptimizer} 命中计数）在 {@link #planFor} 内部按调用局部新建，绝不被规划器持久持有，
 * 因此同输入恒同输出，且不污染/不依赖任何共享子组件实例。
 * <p>
 * 编排规则（固定、确定性）：
 * <ul>
 *   <li><b>层级</b>：{@code StrategySelector.select} 按 {@code ChannelProfile.freq} 决定性选 L1/L2/L3。</li>
 *   <li><b>技术</b>：局部新建 {@code BandwidthOptimizer} 并对 {@code stats} 调 {@code optimize}，取 argmax
 *       技术（固定枚举序破平局），计数按调用局部单调 +1。</li>
 *   <li><b>加密</b>：{@code encrypt = (level == L3) && config.enabled()}（局域网可信开关）；
 *       未可信的 L3 剖面给 {@code encrypt=false} 且 {@code encryptionDegraded=true}（降级标记）。</li>
 *   <li><b>订阅门控</b>：{@code subscribedTo}——兴趣域 ∈ 所需域名集合。</li>
 *   <li><b>语义标签</b>：{@code P2pSemantics.of(level, technique)} 反射到 P2P 语境。</li>
 * </ul>
 * 纯函数 / 确定性：同输入 → 同输出；失败/计数不参与选择。不碰策略/带宽组件本身，只是把决策折射到
 * P2P 通道上下文。纯 JDK，无 MC。禁止 O(n²)：全流程对输入长度线性。
 * <p>
 * p.2.5.6 stateless channel-orchestration facade (engine.p2p.plan, pure JDK): deterministically wires the
 * p.2.4 payload-strategy and bandwidth-optimizer fine-grained components into a P2P channel context. Beyond
 * {@code final} constants it holds no instance state — the only mutable counters (the
 * {@code BandwidthOptimizer} hit counts) are created fresh per {@link #planFor} call, never persisted by the
 * planner, so equal input yields equal output and no shared sub-component instance is touched. The rules above
 * are fixed and fully deterministic. Pure function: same input → same output; failures/counters never take part
 * in selection. It only refracts decisions into a P2P context — it never modifies the strategy/bandwidth
 * components themselves. Linear in input size, no O(n²).
 */
public final class ChannelPlanner {

    private ChannelPlanner() {
    }

    /** 默认所在域名集合始终包含剖面兴趣域。Default wanted-domain set always contains the profile's own domain. */
    public static PlanReport planFor(ChannelProfile profile) {
        return planFor(profile, List.of(profile.domain()), EncryptionConfig.on());
    }

    /** 带可用域名集合的规划（订阅门控按此过滤）。Plan with an explicit wanted-domain set (subscription gate filters against it). */
    public static PlanReport planFor(ChannelProfile profile, Collection<String> wantedDomains) {
        return planFor(profile, wantedDomains, EncryptionConfig.on());
    }

    /**
     * 完整规划：给定剖面 + 所需域名集合 + 局域网可信加密开关，确定性产出 {@link PlanReport}。为保持
     * stateless 与确定性，内部默认对每次调用局部新建一个 {@code BandwidthOptimizer}（传入确定性加密钩子），
     * 其命中计数随调用单调、不复用于选择；编排不持有任何实例状态，也不触碰未显式传入的优化器。
     */
    public static PlanReport planFor(
            ChannelProfile profile, Collection<String> wantedDomains, EncryptionConfig config) {
        return planFor(profile, wantedDomains, config, null);
    }

    /**
     * 完整规划（可注入带宽优化器实例）：当调用方想观察「选择发生了 / 计数单调」时，可传入一个由调用方
     * 持有的 {@code BandwidthOptimizer}，规划会经它执行 {@code optimize} 并递增其中计数。传给 {@code null}
     * 则退化到默认局部新优化器。计数只反映「选择发生」的单调过程，绝不参与选择本身——技术完全由
     * {@code stats} 纯函数派生的，因此无论是否注入优化器、无论其历史计数如何，同输入恒同决策（确定性）。
     */
    public static PlanReport planFor(
            ChannelProfile profile, Collection<String> wantedDomains, EncryptionConfig config,
            BandwidthOptimizer optimizer) {
        Objects.requireNonNull(profile, "channel profile must not be null");
        boolean trusted = config != null && config.enabled();

        // 1) 层级：策略选择器按频度决定（确定性）。
        StrategySelector.Descriptor descriptor =
                new StrategySelector.Descriptor(profile.freq(), profile.domain(), profile.kindFlags());
        PayloadStrategy strategy = StrategySelector.select(descriptor);
        PayloadLevel level = strategy.level();

        // 2) 带宽技术：默认局部 fresh 优化器（stateless 规划器，计数不复用于选择）；显式注入则复用调用方实例。
        if (optimizer == null) {
            optimizer = new BandwidthOptimizer(() -> trusted);
        }
        BandwidthOptimizer.Decision bw = optimizer.optimize(profile.stats());
        BandwidthTechnique technique = bw.technique();

        // 3) 订阅门控。
        boolean subscribed = StrategySelector.subscribedTo(strategy, wantedDomains);

        // 4) 加密（L3 且局域网可信）或降级标记。
        boolean l3 = level == PayloadLevel.L3;
        boolean encrypt = l3 && trusted;
        boolean degraded = l3 && !trusted;

        // 5) P2P 语义标签 + 依据说明（确定性字符串）。
        P2pSemantics effect = P2pSemantics.of(level, technique);
        String basis = basis(level, technique, encrypt, degraded, subscribed, bw);
        return new PlanReport(level, technique, encrypt, degraded, subscribed, effect, basis);
    }

    /** 确定性依据说明（不含计数，纯输入派生；供探针断言与日志）。Deterministic basis text (no counters, pure input-derived). */
    private static String basis(PayloadLevel level, BandwidthTechnique technique,
                                boolean encrypt, boolean degraded, boolean subscribed,
                                BandwidthOptimizer.Decision bw) {
        StringBuilder sb = new StringBuilder(64);
        sb.append("level=").append(level)
                .append(" freq=").append(bw.descriptor().frequency())
                .append(" technique=").append(technique);
        if (encrypt) {
            sb.append(" encrypt=on(tls-trust)");
        }
        if (degraded) {
            sb.append(" encrypt=off(degraded-untrusted)");
        }
        sb.append(" subscribed=").append(subscribed);
        return sb.toString();
    }
}