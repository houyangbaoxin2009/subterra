package io.toterra.subterra.engine.p2p.holepunch;

import io.toterra.subterra.engine.p2p.NodeAddr;

import java.util.ArrayList;
import java.util.List;

/**
 * p.2.30.1 打洞计划器（纯 JDK、确定性）：由「本机直连端点 + 对端候选端点 +
 * 观测端口序列 + relay 兜底地址 + 参数」产出有序 {@link PunchPlan}。PREDICTED
 * 尝试按 {@link PortPredictor#predict} 取端口（不可预测时跳过预测档、以对端
 * 自报端口走 WILDCARD 档），RELAY 兜底仅当参数开启且地址非空；延迟确定性递增
 * （base + index*step）。非法参数确定性拒绝；候选为空且无兜底时确定性拒绝。
 *
 * <p>The punch planner (p.2.30.1, pure JDK, deterministic): builds an ordered
 * {@link PunchPlan} from the local direct endpoint + remote candidates + the
 * observed port sequence + an optional relay fallback + params. PREDICTED
 * attempts take their port from {@link PortPredictor#predict} (skipped when
 * unpredictable, in favour of WILDCARD attempts on the peer-advertised ports);
 * the RELAY fallback is appended only when enabled and the address is present;
 * delays grow deterministically (base + index*step). Illegal parameters are
 * rejected deterministically; empty candidates without a fallback are rejected.
 */
public final class HolePunchPlanner {

    /** 计划参数（构造期确定性校验）。 / Plan parameters (validated at construction). */
    public record Params(int directAttempts, boolean relayFallback, int baseDelayMillis, int delayStepMillis) {
        public Params {
            if (directAttempts < 0) {
                throw new IllegalArgumentException("direct_attempts must be >= 0 (got " + directAttempts + ")");
            }
            if (baseDelayMillis < 0) {
                throw new IllegalArgumentException("base_delay_millis must be >= 0 (got " + baseDelayMillis + ")");
            }
            if (delayStepMillis < 0) {
                throw new IllegalArgumentException("delay_step_millis must be >= 0 (got " + delayStepMillis + ")");
            }
        }
    }

    /** 缺省参数：4 次直连尝试 + 兜底开启、0ms 起步、50ms 步进。 / Defaults: 4 direct attempts, fallback on, 0ms base, 50ms step. */
    public static final Params DEFAULT_PARAMS = new Params(4, true, 0, 50);

    private HolePunchPlanner() {
    }

    /**
     * 确定性计划。同输入同计划（record 深相等）。
     * / Plans deterministically; identical inputs give identical plans.
     */
    public static PunchPlan plan(List<NodeAddr.Direct> remoteCandidates, int[] observedRemotePorts,
                                 NodeAddr relayAddr, Params params) {
        if (params == null) {
            throw new IllegalArgumentException("params must not be null");
        }
        List<NodeAddr.Direct> candidates = remoteCandidates == null ? List.of() : List.copyOf(remoteCandidates);
        boolean hasRelay = params.relayFallback() && relayAddr != null;
        if (candidates.isEmpty() && !hasRelay) {
            throw new IllegalArgumentException("no candidates and no relay fallback");
        }
        int predicted = PortPredictor.predict(observedRemotePorts);
        List<PunchAttempt> attempts = new ArrayList<>();
        int index = 0;
        int direct = Math.min(params.directAttempts(), candidates.isEmpty() ? 0 : params.directAttempts());
        for (int i = 0; i < direct; i++) {
            NodeAddr.Direct base = candidates.get(i % candidates.size());
            if (predicted >= 0) {
                attempts.add(new PunchAttempt(index++, new NodeAddr.Direct(base.host(), predicted),
                        params.baseDelayMillis() + index * params.delayStepMillis(), PunchAttempt.Kind.PREDICTED));
            }
            attempts.add(new PunchAttempt(index++, base,
                    params.baseDelayMillis() + index * params.delayStepMillis(), PunchAttempt.Kind.WILDCARD));
        }
        if (hasRelay) {
            attempts.add(new PunchAttempt(index++, relayAddr,
                    params.baseDelayMillis() + index * params.delayStepMillis(), PunchAttempt.Kind.RELAY));
        }
        if (attempts.isEmpty()) {
            throw new IllegalArgumentException("plan produced no attempts (empty candidates, no relay)");
        }
        return new PunchPlan(attempts, render(attempts));
    }

    private static String render(List<PunchAttempt> attempts) {
        StringBuilder sb = new StringBuilder("punch plan attempts=").append(attempts.size());
        for (PunchAttempt a : attempts) {
            sb.append("; #").append(a.index()).append('=').append(a.kind()).append('@').append(a.target());
        }
        return sb.toString();
    }
}
