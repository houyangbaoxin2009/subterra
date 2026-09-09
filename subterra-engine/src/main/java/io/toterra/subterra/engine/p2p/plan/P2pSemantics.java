package io.toterra.subterra.engine.p2p.plan;

import io.toterra.subterra.engine.network.bandwidth.BandwidthTechnique;
import io.toterra.subterra.engine.network.strategy.PayloadLevel;

/**
 * p.2.5.6 P2P 语义标签（p2p.plan）：把「层级 + 带宽技术」的通用决策折射到 P2P 通道语境的语义说明，
 * 由 {@link ChannelPlanner#effect} 按 (level, technique) 固定映射产出（确定性）。给 runtime 壳一个
 * 无需理解底层策略/带宽组件即可消费的 P2P 语义提示。
 * <ul>
 *   <li>{@link #INCREMENTAL_SYNC}：SYNC 类高频小载荷 → L1 + 增量差分（仅送变更字段）。</li>
 *   <li>{@link #SNAPSHOT_STREAM}：STATUS 类中/低频快照 → L2 + 按需兴趣域订阅（热点域按需卸载）。</li>
 *   <li>{@link #DECIMATED_CHUNK}：大 CHUNK 流 → 降频插值建议（降低采样频率，客户端插值补全）。</li>
 *   <li>{@link #ENCRYPTED_AUTH}：COLD 低频 → L3 加密（决策由局域网可信开关落地，见 {@code encrypt}）。</li>
 * </ul>
 * 纯 JDK、确定性；本枚举只做固定映射，不含任何会话/传输状态。
 * <p>
 * p.2.5.6 P2P semantic label (p2p.plan): refracts the generic (level + bandwidth-technique) decision into a
 * P2P-channel-context semantic hint, produced by {@link ChannelPlanner#effect} from a fixed (level, technique)
 * mapping (deterministic). It gives the runtime shell a semantic cue to consume without needing to understand
 * the underlying strategy/bandwidth components. Pure JDK, deterministic, and stateless (no session/transport
 * state).
 */
public enum P2pSemantics {

    /** SYNC 高频小载荷 → L1 + 增量差分。Hot SYNC payload → L1 + incremental diff. */
    INCREMENTAL_SYNC,
    /** STATUS 中/低频快照 → L2 + 按需订阅。Mid/low-frequency STATUS snapshot → L2 + on-demand subscribe. */
    SNAPSHOT_STREAM,
    /** 大 CHUNK 流 → 降频插值建议。Large CHUNK stream → decimation + interpolation hint. */
    DECIMATED_CHUNK,
    /** COLD 低频 → L3 加密（开关落地）。Cold low-frequency → L3 encrypted (toggle-gated). */
    ENCRYPTED_AUTH;

    /**
     * 固定、确定性的 (level, technique) → 语义映射。Fixed, deterministic (level, technique) → semantic map.
     */
    static P2pSemantics of(PayloadLevel level, BandwidthTechnique technique) {
        if (level == PayloadLevel.L3) {
            return ENCRYPTED_AUTH;
        }
        if (level == PayloadLevel.L1) {
            return INCREMENTAL_SYNC;
        }
        // L2
        return switch (technique) {
            case DIFFERENTIAL_SYNC -> INCREMENTAL_SYNC;
            case ON_DEMAND_SUBSCRIBE -> SNAPSHOT_STREAM;
            case DECIMATION_INTERPOLATION -> DECIMATED_CHUNK;
        };
    }
}