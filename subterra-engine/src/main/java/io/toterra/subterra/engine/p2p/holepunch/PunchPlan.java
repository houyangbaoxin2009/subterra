package io.toterra.subterra.engine.p2p.holepunch;

import java.util.List;

/**
 * p.2.30.1 打洞计划（纯 JDK、确定性）：有序 {@link PunchAttempt} 列表 + 规范
 * 渲染（无时间戳）。同输入同计划（record 深相等）。
 *
 * <p>The punch plan (p.2.30.1, pure JDK, deterministic): an ordered {@link
 * PunchAttempt} list + a canonical render (no timestamps). Identical inputs give
 * identical plans (record deep equality).
 */
public record PunchPlan(List<PunchAttempt> attempts, String render) {

    public PunchPlan {
        if (attempts == null || attempts.isEmpty()) {
            throw new IllegalArgumentException("attempts must not be empty");
        }
        attempts = List.copyOf(attempts);
    }
}
