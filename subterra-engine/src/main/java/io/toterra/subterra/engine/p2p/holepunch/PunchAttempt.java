package io.toterra.subterra.engine.p2p.holepunch;

import io.toterra.subterra.engine.p2p.NodeAddr;

/**
 * p.2.30.1 打洞单次尝试（纯 JDK、确定性）：指向一个 {@link NodeAddr} 目标、
 * 固定延迟与类别（PREDICTED 端口预测 / WILDCARD 对端自报端口 / RELAY 兜底）。
 *
 * <p>A single punch attempt (p.2.30.1, pure JDK, deterministic): one {@link NodeAddr}
 * target with a fixed delay and a kind (PREDICTED port prediction / WILDCARD
 * peer-advertised port / RELAY fallback).
 */
public record PunchAttempt(int index, NodeAddr target, int delayMillis, Kind kind) {

    /** 尝试类别。 / The attempt kind. */
    public enum Kind {
        PREDICTED,
        WILDCARD,
        RELAY
    }

    public PunchAttempt {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0 (got " + index + ")");
        }
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        if (delayMillis < 0) {
            throw new IllegalArgumentException("delayMillis must be >= 0 (got " + delayMillis + ")");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
    }
}
