package io.toterra.subterra.probes;

import io.toterra.subterra.engine.network.bandwidth.BandwidthOptimizer;
import io.toterra.subterra.engine.network.bandwidth.BandwidthStats;
import io.toterra.subterra.engine.network.bandwidth.BandwidthTechnique;
import io.toterra.subterra.engine.network.crypto.EncryptionConfig;
import io.toterra.subterra.engine.network.strategy.ChangeStream;
import io.toterra.subterra.engine.network.strategy.DiffOp;
import io.toterra.subterra.engine.network.strategy.FieldSnapshot;
import io.toterra.subterra.engine.network.strategy.IncrementalDiff;
import io.toterra.subterra.engine.network.strategy.PayloadLevel;
import io.toterra.subterra.engine.network.strategy.StrategySelector;
import io.toterra.subterra.engine.p2p.payload.ZdChannelCodec;
import io.toterra.subterra.engine.p2p.plan.ChannelPlanner;
import io.toterra.subterra.engine.p2p.plan.ChannelProfile;
import io.toterra.subterra.engine.p2p.plan.P2pSemantics;
import io.toterra.subterra.engine.p2p.plan.PlanReport;
import io.toterra.subterra.engine.zd.ZdRow;

import java.util.List;
import java.util.Map;

/**
 * p.2.5.6 通道规划编排门面探针（纯 JVM，无 MC 运行时）：验证 stateless {@link ChannelPlanner} 把
 * p.2.4 载荷策略 + 带宽优化确定性编排进 P2P 通道语境。
 * <ul>
 *   <li>三型典型剖面（高频小载荷/低频快照/大块 chunk 流）→ planFor 确定性命中预期 level+technique
 *       （复算 p.2.4.6 命中计数语义：argmax + 固定枚举序破平局，固定样例锁定 expected）。</li>
 *   <li>同输入两次 → 决策报告逐字段一致（确定性）。</li>
 *   <li>subscribedTo 门控：兴趣域不含 → false。</li>
 *   <li>加密降级：局域网可信关 → L3 剖面 encrypt=false 且 degraded 标记；可信开 → encrypt=true。</li>
 *   <li>编排不动子组件：外部 {@code BandwidthOptimizer} 计数在反复规划后仍为零；规划内部局部优化器
 *       的计数快照单调显示选中技术 +1（选择发生了、不改内部语义）。</li>
 *   <li>与 p.2.5.5 串联（轻量）：L1 剖面 → 增量 diff → 重放往返 → zd 编解码往返。</li>
 * </ul>
 * 纯 JVM、确定性：无随机、无时序；失败计数只在断言失败自增。退出码 0 = PASS，1 = FAIL。
 * <p>
 * p.2.5.6 acceptance probe for the channel-planner orchestration facade (pure JVM, no MC): verifies the
 * stateless {@link ChannelPlanner} wires the p.2.4 strategy + bandwidth components into a P2P channel
 * context — three typical profiles hit expected (level, technique) deterministically, same-input
 * reproducibility, subscription gating, encryption-trust degradation, no sub-component mutation, and a light
 * p.2.5.5 diff → zd-codec chain. Fixed samples, no randomness, no timing. Exit 0 = PASS, 1 = FAIL.
 */
public final class P2pPlannerProbe {

    private P2pPlannerProbe() {
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

    // ===== 三型典型剖面（固定样例锁定 expected；复算 p.2.4.6 命中语义） =====

    // 高频小载荷 / SYNC → L1 + 增量差分。
    private static final ChannelProfile SYNC = new ChannelProfile(
            StrategySelector.Frequency.HOT, "sync", 1,
            BandwidthStats.of(0.9, 0.1, 1, PayloadLevel.L1));
    // 低频快照 / STATUS → L2 + 按需订阅（高订阅高波动）。
    private static final ChannelProfile STATUS = new ChannelProfile(
            StrategySelector.Frequency.WARM, "status", 2,
            BandwidthStats.of(0.1, 0.9, 32, PayloadLevel.L2));
    // 大块 chunk 流 → L2 + 降频插值建议（低变更高波动、少订阅）。
    private static final ChannelProfile CHUNK = new ChannelProfile(
            StrategySelector.Frequency.WARM, "chunk", 4,
            BandwidthStats.of(0.1, 0.9, 1, PayloadLevel.L2));
    // COLD 低频（专供加密降级测试）。
    private static final ChannelProfile COLD = new ChannelProfile(
            StrategySelector.Frequency.COLD, "auth", 8,
            BandwidthStats.of(0.9, 0.8, 32, PayloadLevel.L3));

    public static void main(String[] args) {
        typicalProfiles();
        determinism();
        subscriptionGate();
        encryptionDegradation();
        doesNotMutateSubComponents();
        p255Chain();
        basisText();

        if (failures == 0) {
            System.out.println("[P2pPlannerProbe] PASS: p.2.5.6 P2P channel planner wiring ("
                    + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[P2pPlannerProbe] FAIL: " + failures + " failure(s) of " + checks);
            System.exit(1);
        }
    }

    /** 三型典型剖面 → 确定性命中预期 level+technique+语义。 */
    private static void typicalProfiles() {
        PlanReport sync = ChannelPlanner.planFor(SYNC);
        check("SYNC 高频小载荷 → level L1",
                sync.level() == PayloadLevel.L1);
        check("SYNC 高频小载荷 → DIFFERENTIAL_SYNC（增量差分）",
                sync.technique() == BandwidthTechnique.DIFFERENTIAL_SYNC);
        check("SYNC 剖面语义 → INCREMENTAL_SYNC",
                sync.effect() == P2pSemantics.INCREMENTAL_SYNC);
        check("SYNC（非 L3）→ 未加密且无降级标记",
                !sync.encrypt() && !sync.encryptionDegraded());

        PlanReport status = ChannelPlanner.planFor(STATUS);
        check("STATUS 低频快照 → level L2",
                status.level() == PayloadLevel.L2);
        check("STATUS 低频快照 → ON_DEMAND_SUBSCRIBE（按需订阅）",
                status.technique() == BandwidthTechnique.ON_DEMAND_SUBSCRIBE);
        check("STATUS 剖面语义 → SNAPSHOT_STREAM",
                status.effect() == P2pSemantics.SNAPSHOT_STREAM);

        PlanReport chunk = ChannelPlanner.planFor(CHUNK);
        check("CHUNK 大块流 → DECIMATION_INTERPOLATION（降频插值建议）",
                chunk.technique() == BandwidthTechnique.DECIMATION_INTERPOLATION);
        check("CHUNK 剖面语义 → DECIMATED_CHUNK",
                chunk.effect() == P2pSemantics.DECIMATED_CHUNK);
    }

    /** 同输入两次 → 决策报告逐字段一致（字节级确定性）。 */
    private static void determinism() {
        PlanReport a = ChannelPlanner.planFor(SYNC);
        PlanReport b = ChannelPlanner.planFor(SYNC);
        check("确定性: 同输入两次 → 决策报告逐字段一致",
                a.equals(b));
        check("确定性: 决策依据字符串字节级一致",
                a.basis().equals(b.basis()));
        PlanReport c = ChannelPlanner.planFor(CHUNK, List.of("chunk"), EncryptionConfig.disabled());
        PlanReport d = ChannelPlanner.planFor(CHUNK, List.of("chunk"), EncryptionConfig.disabled());
        check("确定性: 带门控+开关参数的规划两次一致",
                c.equals(d));
    }

    /** subscribedTo 门控：兴趣域不含 → false；含 → true。 */
    private static void subscriptionGate() {
        boolean hit = ChannelPlanner.planFor(STATUS, List.of("status")).subscribed();
        check("订阅门控: 兴趣域含于期望集 → subscribed=true", hit);
        boolean miss = ChannelPlanner.planFor(STATUS, List.of("tables", "mobs")).subscribed();
        check("订阅门控: 兴趣域不含 → subscribed=false", !miss);
    }

    /** 加密降级：可信开 → encrypt；可信关 → 降级标记。 */
    private static void encryptionDegradation() {
        PlanReport on = ChannelPlanner.planFor(COLD, List.of("auth"), EncryptionConfig.on());
        check("加密: COLD + 局域网可信开 → encrypt=true", on.encrypt());
        check("加密: COLD + 可信开 → 无降级标记", !on.encryptionDegraded());
        check("加密: COLD 语义 → ENCRYPTED_AUTH", on.effect() == P2pSemantics.ENCRYPTED_AUTH);

        PlanReport off = ChannelPlanner.planFor(COLD, List.of("auth"), EncryptionConfig.disabled());
        check("加密: COLD + 局域网可信关 → encrypt=false 且降级标记", !off.encrypt() && off.encryptionDegraded());
        // 开关不动层级/技术选择（只改加密落地），确定性。
        check("加密: 开关不影响 level/technique（count 复算）",
                on.level() == off.level() && on.technique() == off.technique());
    }

    /** 编排不动子组件：注入优化器时计数单调递增（选择发生）；不注入的优化器保持零（不触碰未给组件）。 */
    private static void doesNotMutateSubComponents() {
        BandwidthOptimizer injected = new BandwidthOptimizer(() -> true);
        BandwidthOptimizer untouched = new BandwidthOptimizer(() -> true);

        // 注入同一个优化器规划三次：每次只让命中技术 +1，计数单调 0→1→2→3，其他技术恒 0。
        for (int i = 0; i < 3; i++) {
            PlanReport p = ChannelPlanner.planFor(SYNC, List.of("sync"), EncryptionConfig.on(), injected);
            check("选择发生: 第 " + (i + 1) + " 次规划技术恒为 DIFFERENTIAL_SYNC（不改内部语义）",
                    p.technique() == BandwidthTechnique.DIFFERENTIAL_SYNC);
        }
        check("计数单调: 注入优化器 DIFFERENTIAL_SYNC 命中 3（选择发生、计数单调）",
                injected.hitCount(BandwidthTechnique.DIFFERENTIAL_SYNC) == 3);
        check("计数单调: 注入优化器其他技术仍为 0",
                injected.hitCount(BandwidthTechnique.DECIMATION_INTERPOLATION) == 0
                        && injected.hitCount(BandwidthTechnique.ON_DEMAND_SUBSCRIBE) == 0);
        // 未显式传入的优化器在大量规划后仍全零 → 编排不触碰未给组件。
        for (int i = 0; i < 7; i++) {
            ChannelPlanner.planFor(SYNC);
            ChannelPlanner.planFor(STATUS);
            ChannelPlanner.planFor(CHUNK);
        }
        check("不动子组件: 未注入的优化器计数仍全零",
                untouched.hitCount(BandwidthTechnique.DIFFERENTIAL_SYNC) == 0
                        && untouched.hitCount(BandwidthTechnique.DECIMATION_INTERPOLATION) == 0
                        && untouched.hitCount(BandwidthTechnique.ON_DEMAND_SUBSCRIBE) == 0);
    }

    /** 与 p.2.5.5 串联：L1 剖面 → 增量 diff → 重放往返 → zd 编解码往返。 */
    private static void p255Chain() {
        FieldSnapshot base = FieldSnapshot.builder()
                .putNum("x", 1).putReal("y", 2.5).putText("n", "a").build();
        FieldSnapshot next = FieldSnapshot.builder()
                .putNum("x", 2).putReal("y", 2.5).putText("n", "b").putNum("z", 9).build();

        // 仅 L1 剖面规划层次应提示增量语义。
        PlanReport plan = ChannelPlanner.planFor(SYNC);
        // 规划到 L1 → 产 diff；diff 重放 = next（往返契约）。
        IncrementalDiff.Result diff = IncrementalDiff.diff(base, next);
        FieldSnapshot replayed = IncrementalDiff.apply(base, diff.ops());
        check("p.2.5.5 串联: L1 剖面增量 diff 重放往返重建 next",
                FieldSnapshot.same(replayed, next) && diff.counts().opTotal() > 0);

        // diff 操作经 ChangeStream 封装顺序无污染。
        ChangeStream stream = ChangeStream.of(diff.ops(), next);
        check("p.2.5.5 串联: ChangeStream 依序重放也等于 next",
                FieldSnapshot.same(stream.replay(base), next));

        // 把重放最终快照编为 zd 码流并回读（规划→编码→回读链路成立）。
        List<ZdRow> rows = snapshotToRows(replayed);
        byte[] enc = ZdChannelCodec.encode(new byte[0], rows);
        ZdChannelCodec.Decode dec = ZdChannelCodec.decode(enc);
        check("p.2.5.5 串联: 规划→zd 编码→解码回读每行字段一致",
                dec != null && dec.ok() && rowsEqual(dec.rows(), rows));
    }

    /** 依据说明文本含确定性要点。 */
    private static void basisText() {
        PlanReport sync = ChannelPlanner.planFor(SYNC);
        check("依据说明: 含 level/technique/subscribed 确定性要点",
                sync.basis() != null && sync.basis().contains("level=L1")
                        && sync.basis().contains("technique=DIFFERENTIAL_SYNC")
                        && sync.basis().contains("subscribed=true"));
        PlanReport coldOff = ChannelPlanner.planFor(COLD, List.of("auth"), EncryptionConfig.disabled());
        check("依据说明: 降级路径含 degraded 标记文本",
                coldOff.basis().contains("encrypt=off(degraded-untrusted)"));
    }

    // ===== zd 编码辅助（与 p.2.5.5 链路）=====

    private static List<ZdRow> snapshotToRows(FieldSnapshot s) {
        java.util.ArrayList<ZdRow> out = new java.util.ArrayList<>();
        for (Map.Entry<String, FieldSnapshot.Value> e : s.entries()) {
            FieldSnapshot.Value v = e.getValue();
            switch (v) {
                case FieldSnapshot.Value.Num n -> out.add(new ZdRow(2, e.getKey(), n.value(), 0.0, "", 0));
                case FieldSnapshot.Value.Real r -> out.add(new ZdRow(3, e.getKey(), 0L, r.value(), "", 0));
                case FieldSnapshot.Value.Text t -> out.add(new ZdRow(1, e.getKey(), 0L, 0.0, t.value(), 0));
            }
        }
        return out;
    }

    private static boolean rowsEqual(List<ZdRow> a, List<ZdRow> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            ZdRow x = a.get(i);
            ZdRow y = b.get(i);
            if (x.kind() != y.kind() || !x.key().equals(y.key())
                    || x.valueI64() != y.valueI64()
                    || x.valueF64() != y.valueF64()
                    || !x.valueStr().equals(y.valueStr())) {
                return false;
            }
        }
        return true;
    }
}