package io.toterra.subterra.engine.p2p.discovery;

import io.toterra.subterra.engine.p2p.NodeAddr;
import io.toterra.subterra.engine.p2p.NodeId;

/**
 * 一次路由候选：(NodeId, NodeAddr) 对。路由表 / lookup / routeTo 输出的基本单元。
 * <p>
 * A routing candidate — an (NodeId, NodeAddr) pair emitted by the routing table, {@code lookup}
 * and {@code routeTo}.
 */
public record RouteCandidate(NodeId nodeId, NodeAddr addr) {

    /** 非空校验收紧。Compact constructor — reject nulls. */
    public RouteCandidate {
        if (nodeId == null || addr == null) {
            throw new NullPointerException("nodeId and addr must not be null");
        }
    }
}