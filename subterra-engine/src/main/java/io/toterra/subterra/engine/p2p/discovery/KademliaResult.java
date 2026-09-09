package io.toterra.subterra.engine.p2p.discovery;

import java.util.List;

/**
 * {@code lookup}/{@code routeTo} 的结构化结果：按 XOR 距离升序的有序候选清单 + 命中标志。
 * 命中仅当目标 id 确实存在于表中（已插入过的节点）；未插入的目标绝不会伪命中。
 * <p>
 * Structured result of a {@code lookup}/{@code routeTo}: the ordered candidate list (ascending
 * XOR distance) plus a hit flag. A hit is true only for a target actually present in the table
 * (a previously inserted node); a never-inserted target never yields a spurious hit.
 */
public record KademliaResult(List<RouteCandidate> candidates, boolean hit) {

    /** 非空校验收紧。Compact constructor — candidates must not be null. */
    public KademliaResult {
        if (candidates == null) {
            throw new NullPointerException("candidates");
        }
    }
}