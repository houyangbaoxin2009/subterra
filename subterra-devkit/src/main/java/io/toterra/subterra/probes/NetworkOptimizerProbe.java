package io.toterra.subterra.probes;

import io.toterra.subterra.engine.network.bandwidth.BandwidthOptimizer;
import io.toterra.subterra.engine.network.bandwidth.BandwidthOptimizer.Decision;
import io.toterra.subterra.engine.network.bandwidth.BandwidthStats;
import io.toterra.subterra.engine.network.bandwidth.BandwidthTechnique;
import io.toterra.subterra.engine.network.bandwidth.ScaledScore;
import io.toterra.subterra.engine.network.strategy.PayloadLevel;

import java.util.Arrays;

/**
 * p.2.4.4 带宽削减量化优化器探针（纯 JVM，无 MC 运行时）：验证 {@link BandwidthOptimizer} 的权重选择模型、
 * 确定性、命中计数与加密开关正交性。
 * <p>
 * 选择模型（探针所编码的规范）：{@code BandwidthStats} 经 {@link BandwidthStats#of} 钳制到 [0,1]；
 * <ul>
 *   <li>scoreDiff = W_DIFF_RATIO * diffRatio * (1 - stateVolatility)：变更比例高且状态稳定 → 差分同步。</li>
 *   <li>scoreDec  = W_VOLATILITY * stateVolatility * (1 - diffRatio)：波动高且变更比例低 → 降频插值。</li>
 *   <li>scoreSub  = W_SUBSCRIBER_CAP * min(1, subscriberCount / SUBSCRIBER_CAP) * stateVolatility：
 *       订阅者饱和后即令负荷向订阅域卸载，波动高收益最大 → 按需订阅。</li>
 *   <li>argmax + 固定枚举序打破平局（确定性，无随机）。</li>
 * </ul>
 * 断言：
 * <ol>
 *   <li>得分/选择确定性：相同统计两次 → 技术与会得逐项相同。</li>
 *   <li>高 diffRatio + 低波动 → DIFFERENTIAL_SYNC。</li>
 *   <li>低 diffRatio + 高波动 → DECIMATION_INTERPOLATION。</li>
 *   <li>高订阅者数 + 高波动 → ON_DEMAND_SUBSCRIBE。</li>
 *   <li>计数：初始全零；一次 optimize 恰好只让命中技术 +1；非法（越界）统计不递增计数。</li>
 *   <li>组合确定性：50 个统计元组跑两遍 → 技术序列一致、最终计数一致。</li>
 *   <li>加密开关：L3 统计 + hook=true → encryptionApplicable=true；false → false；计数与开关正交。</li>
 * </ol>
 * 退出码 0 = PASS，1 = FAIL。
 * <p>
 * p.2.4.4 bandwidth reduction optimizer probe (pure JVM, no MC runtime): verifies the weighted
 * selection model, determinism, hit counters, and encryption-toggle orthogonality of
 * {@link BandwidthOptimizer}.
 * <p>
 * Selection model (the spec encoded here): {@code BandwidthStats} clamps ratios to [0,1] via
 * {@link BandwidthStats#of};
 * <ul>
 *   <li>scoreDiff = W_DIFF_RATIO * diffRatio * (1 - stateVolatility): high change ratio, stable state → differential sync.</li>
 *   <li>scoreDec  = W_VOLATILITY * stateVolatility * (1 - diffRatio): high volatility, low change ratio → decimation.</li>
 *   <li>scoreSub  = W_SUBSCRIBER_CAP * min(1, subscriberCount / SUBSCRIBER_CAP) * stateVolatility:
 *       once subscribers saturate, load is offloaded onto the subscribed domains, highest benefit at high volatility → on-demand.</li>
 *   <li>argmax + fixed enum order tie-break (deterministic, no randomness).</li>
 * </ul>
 * Exit 0 = PASS.
 */
public final class NetworkOptimizerProbe {

    private NetworkOptimizerProbe() {
    }

    private static int failures = 0;
    private static int checks = 0;

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    // 常用测试元组。Common test tuples.
    // 高变更 + 低波动 → 差分同步。High change + low volatility → DIFFERENTIAL_SYNC.
    private static final BandwidthStats DIFF_STATS = BandwidthStats.of(0.9, 0.1, 1, PayloadLevel.L1);
    // 低变更 + 高波动 → 降频插值。Low change + high volatility → DECIMATION_INTERPOLATION.
    private static final BandwidthStats DECIM_STATS = BandwidthStats.of(0.1, 0.9, 1, PayloadLevel.L2);
    // 高订阅 + 高波动 → 按需订阅。High subscribers + high volatility → ON_DEMAND_SUBSCRIBE.
    private static final BandwidthStats SUB_STATS = BandwidthStats.of(0.1, 0.9, 32, PayloadLevel.L3);
    // L3 频率统计（专供加密开关测试）。L3-frequency stats (for the encryption-toggle test).
    private static final BandwidthStats ENC_L3_STATS = BandwidthStats.of(0.9, 0.1, 1, PayloadLevel.L3);

    public static void main(String[] args) {
        scoreSelectionDeterminism();
        differentialHighChangeLowVolatility();
        decimationLowChangeHighVolatility();
        onDemandHighSubscribersHighVolatility();
        countersStartZeroIncrementWinnerInvalidNoOp();
        combinedDeterminism();
        encryptionToggleOrthogonal();

        if (failures == 0) {
            System.out.println("[NetworkOptimizerProbe] PASS (bandwidth optimizer, " + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[NetworkOptimizerProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }

    private static void scoreSelectionDeterminism() {
        ScaledScore[] s1 = BandwidthOptimizer.scoresOf(DIFF_STATS);
        ScaledScore[] s2 = BandwidthOptimizer.scoresOf(DIFF_STATS);
        check("确定性: 相同统计两次 → 技术+得分逐项相同", sameScores(s1, s2));

        BandwidthOptimizer o = new BandwidthOptimizer(() -> true);
        BandwidthTechnique t1 = o.optimize(DIFF_STATS).technique();
        BandwidthTechnique t2 = o.optimize(DIFF_STATS).technique();
        check("确定性: 相同统计两次 optimize → 技术一致", t1 == t2);
    }

    private static void differentialHighChangeLowVolatility() {
        BandwidthOptimizer o = new BandwidthOptimizer(() -> true);
        Decision d = o.optimize(DIFF_STATS);
        check("高变更+低波动 → 差分同步",
                d.technique() == BandwidthTechnique.DIFFERENTIAL_SYNC);
    }

    private static void decimationLowChangeHighVolatility() {
        BandwidthOptimizer o = new BandwidthOptimizer(() -> true);
        Decision d = o.optimize(DECIM_STATS);
        check("低变更+高波动 → 降频插值",
                d.technique() == BandwidthTechnique.DECIMATION_INTERPOLATION);
    }

    private static void onDemandHighSubscribersHighVolatility() {
        BandwidthOptimizer o = new BandwidthOptimizer(() -> true);
        Decision d = o.optimize(SUB_STATS);
        check("高订阅+高波动 → 按需订阅",
                d.technique() == BandwidthTechnique.ON_DEMAND_SUBSCRIBE);
    }

    private static void countersStartZeroIncrementWinnerInvalidNoOp() {
        // 初始全零。
        BandwidthOptimizer fresh = new BandwidthOptimizer(() -> true);
        check("计数: 新建优化器计数全零",
                fresh.hitCount(BandwidthTechnique.DIFFERENTIAL_SYNC) == 0
                        && fresh.hitCount(BandwidthTechnique.DECIMATION_INTERPOLATION) == 0
                        && fresh.hitCount(BandwidthTechnique.ON_DEMAND_SUBSCRIBE) == 0);

        // 一次 optimize 只让命中技术 +1。
        Decision d = fresh.optimize(DIFF_STATS);
        check("计数: 命中技术计数 +1", fresh.hitCount(d.technique()) == 1);
        check("计数: 其余技术计数仍为 0",
                fresh.hitCount(BandwidthTechnique.DIFFERENTIAL_SYNC) == 1
                        && fresh.hitCount(BandwidthTechnique.DECIMATION_INTERPOLATION) == 0
                        && fresh.hitCount(BandwidthTechnique.ON_DEMAND_SUBSCRIBE) == 0);
        // 决策快照与实时计数一致。
        check("计数: 决策携带的计数快照正确",
                Arrays.equals(d.hitCountersSnapshot(), new long[]{1, 0, 0}));

        // 非法（越界 diffRatio）→ 抛 IAE 且计数不变。
        BandwidthOptimizer bad = new BandwidthOptimizer(() -> true);
        boolean threwIae;
        try {
            bad.optimize(new BandwidthStats(1.7, 0.3, 2, PayloadLevel.L2));
            threwIae = false;
        } catch (IllegalArgumentException e) {
            threwIae = true;
        }
        check("非法路径: 越界 diffRatio 抛 IllegalArgumentException", threwIae);
        check("非法路径: 越界统计不递增计数",
                bad.hitCount(BandwidthTechnique.DIFFERENTIAL_SYNC) == 0
                        && bad.hitCount(BandwidthTechnique.DECIMATION_INTERPOLATION) == 0
                        && bad.hitCount(BandwidthTechnique.ON_DEMAND_SUBSCRIBE) == 0);
    }

    private static void combinedDeterminism() {
        BandwidthTechnique[] seq1 = runCombined(new BandwidthOptimizer(() -> true));
        BandwidthTechnique[] seq2 = runCombined(new BandwidthOptimizer(() -> true));
        check("组合确定性: 50 个元组两次得到的命中序列一致", Arrays.equals(seq1, seq2));

        // 用固定 50 元组统计最终计数；两次结果一致（在 runCombined 中经相同输入增量得到）。
        BandwidthOptimizer a = new BandwidthOptimizer(() -> true);
        BandwidthOptimizer b = new BandwidthOptimizer(() -> true);
        runCombined(a);
        runCombined(b);
        check("组合确定性: 50 个元组两次得到的最终计数一致",
                Arrays.equals(a.hitCountersSnapshot(), b.hitCountersSnapshot()));
        check("组合确定性: 命中计数总和等于处理元组数",
                a.hitCount(BandwidthTechnique.DIFFERENTIAL_SYNC)
                        + a.hitCount(BandwidthTechnique.DECIMATION_INTERPOLATION)
                        + a.hitCount(BandwidthTechnique.ON_DEMAND_SUBSCRIBE) == 50);
    }

    private static BandwidthTechnique[] runCombined(BandwidthOptimizer o) {
        BandwidthTechnique[] seq = new BandwidthTechnique[50];
        for (int i = 0; i < 50; i++) {
            double dr = (i * 7L % 101) / 100.0;
            double sv = (i * 13L % 101) / 100.0;
            int subs = (i * 5) % 40;
            PayloadLevel level = PayloadLevel.ofRank(i % 3);
            seq[i] = o.optimize(BandwidthStats.of(dr, sv, subs, level)).technique();
        }
        return seq;
    }

    private static void encryptionToggleOrthogonal() {
        // L3 统计：encryptionApplicable 仅由 L3 与开关决定；计数与开关正交。
        BandwidthOptimizer on = new BandwidthOptimizer(() -> true);
        BandwidthOptimizer off = new BandwidthOptimizer(() -> false);

        Decision onD = on.optimize(ENC_L3_STATS);
        Decision offD = off.optimize(ENC_L3_STATS);
        check("加密: L3 统计 + 开关=true → applicable", onD.encryptionApplicable());
        check("加密: L3 统计 + 开关=false → 不适用", !offD.encryptionApplicable());
        check("加密: 同统计下两开关选中技术一致",
                onD.technique() == offD.technique());
        check("加密: 计数与开关正交（两优化器最终计数一致）",
                Arrays.equals(on.hitCountersSnapshot(), off.hitCountersSnapshot()));
    }

    private static boolean sameScores(ScaledScore[] a, ScaledScore[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i].technique() != b[i].technique()
                    || a[i].score() != b[i].score()) {
                return false;
            }
        }
        return true;
    }
}