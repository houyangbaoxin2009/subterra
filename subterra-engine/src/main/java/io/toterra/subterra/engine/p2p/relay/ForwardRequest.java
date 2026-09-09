package io.toterra.subterra.engine.p2p.relay;

import io.toterra.subterra.engine.p2p.NodeId;

/**
 * p.2.5.8 志愿 relay 兜底转发的源请求（record）：{@code source} 源节点 + 目标 relay 节点
 * {@code relayId} + 目标端点 token {@code token} + 变更载荷 {@code payload} + {@code hop} 计数。
 * 语义（打洞失败兜底）：源节点在直连不可达时向 relay 提交本请求，relay 依据 token 判定是否转发；
 * 失败从不静默（token 未登记 / 错配 → {@link ForwardResult.Status#REJECTED}，hop 超限 →
 * {@link ForwardResult.Status#DROPPED}(loop) 防环）。{@code payload} 做防御性拷贝（record 值语义）。
 * <p>
 * p.2.5.8 Voluntary-relay fallback forward request (record): {@code source} origin node + target
 * relay node {@code relayId} + target endpoint {@code token} + carried {@code payload} + {@code hop}
 * count. Semantics (hole-punch fallback): the origin submits this request to a relay when direct
 * delivery is unreachable and the relay decides on the {@code token}; failure is never silent
 * (unregistered / mismatched token → {@link ForwardResult.Status#REJECTED}, hop over-limit →
 * {@link ForwardResult.Status#DROPPED}(loop) to break cycles). {@code payload} is defensively copied.
 */
public record ForwardRequest(NodeId source, NodeId relayId, String token, byte[] payload, int hop) {

    public static final int MAX_HOPS = 3;

    public ForwardRequest {
        if (source == null) {
            throw new NullPointerException("source");
        }
        if (relayId == null) {
            throw new NullPointerException("relayId");
        }
        if (token == null) {
            throw new NullPointerException("token");
        }
        if (hop < 0) {
            throw new IllegalArgumentException("hop must be >= 0, got " + hop);
        }
        payload = payload == null ? new byte[0] : payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    /** 是否超过尽力而为的最大 hop 计数（> {@code MAX_HOPS}）。Whether the hop count exceeds {@link #MAX_HOPS}. */
    public boolean hopLimitExceeded() {
        return hop > MAX_HOPS;
    }
}