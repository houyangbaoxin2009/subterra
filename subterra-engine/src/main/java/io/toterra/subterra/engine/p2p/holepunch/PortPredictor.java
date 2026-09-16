package io.toterra.subterra.engine.p2p.holepunch;

/**
 * p.2.30.1 NAT 端口预测器（纯 JDK、确定性）：从对端观测到的「本机出站外部端口
 * 序列」分类 NAT 行为并预测下一个外部端口。三类策略：PRESERVE（保持，序列恒定）、
 * INCREMENT（固定增量）、RANDOM（不可预测，预测值 {@code -1}，交由 WILDCARD/RELAY）。
 * 空序列按 RANDOM。同输入同分类同预测。
 *
 * <p>The NAT port predictor (p.2.30.1, pure JDK, deterministic): classifies the NAT
 * behaviour from the peer-observed sequence of this host's outbound external ports
 * and predicts the next one. Three strategies: PRESERVE (stable sequence),
 * INCREMENT (constant delta), RANDOM (unpredictable, prediction {@code -1}; fall
 * through to WILDCARD/RELAY). An empty sequence is RANDOM. Identical inputs give
 * identical classifications and predictions.
 */
public final class PortPredictor {

    /** NAT 行为分类。 / The NAT behaviour classification. */
    public enum Strategy {
        PRESERVE,
        INCREMENT,
        RANDOM
    }

    private PortPredictor() {
    }

    /** 确定性分类。 / The deterministic classification. */
    public static Strategy classify(int[] observed) {
        if (observed == null || observed.length == 0) {
            return Strategy.RANDOM;
        }
        boolean stable = true;
        for (int v : observed) {
            if (v != observed[0]) {
                stable = false;
                break;
            }
        }
        if (stable) {
            return Strategy.PRESERVE;
        }
        if (observed.length >= 3) {
            long delta = (long) observed[1] - observed[0];
            boolean constantDelta = true;
            for (int i = 2; i < observed.length; i++) {
                if ((long) observed[i] - observed[i - 1] != delta) {
                    constantDelta = false;
                    break;
                }
            }
            if (constantDelta && delta != 0) {
                return Strategy.INCREMENT;
            }
        }
        return Strategy.RANDOM;
    }

    /** 确定性预测：PRESERVE=末值；INCREMENT=末值+增量（clamp 到 [0, 65535]）；RANDOM=-1。 /
     *  The deterministic prediction: PRESERVE=last; INCREMENT=last+delta (clamped to [0, 65535]); RANDOM=-1. */
    public static int predict(int[] observed) {
        Strategy s = classify(observed);
        if (s == Strategy.RANDOM) {
            return -1;
        }
        int last = observed[observed.length - 1];
        if (s == Strategy.INCREMENT) {
            long next = last + ((long) observed[1] - observed[0]);
            if (next < 0) {
                next = 0;
            }
            if (next > 65535) {
                next = 65535;
            }
            return (int) next;
        }
        return last;
    }
}
