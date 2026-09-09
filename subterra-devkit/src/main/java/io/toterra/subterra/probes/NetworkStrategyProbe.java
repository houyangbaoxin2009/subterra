package io.toterra.subterra.probes;

import io.toterra.subterra.engine.network.strategy.ChangeStream;
import io.toterra.subterra.engine.network.strategy.DiffOp;
import io.toterra.subterra.engine.network.strategy.FieldSnapshot;
import io.toterra.subterra.engine.network.strategy.IncrementalDiff;
import io.toterra.subterra.engine.network.strategy.IncrementalDiff.Counts;
import io.toterra.subterra.engine.network.strategy.IncrementalDiff.Result;
import io.toterra.subterra.engine.network.strategy.PayloadLevel;
import io.toterra.subterra.engine.network.strategy.PayloadStrategy;
import io.toterra.subterra.engine.network.strategy.StrategySelector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * p.2.4.3 三级载荷策略核心探针（纯 JVM，无 MC 运行时）：{@link StrategySelector} 确定性选择、{@link
 * IncrementalDiff} 差分与往返、{@link ChangeStream} 有序变更流与确定性命中、兴趣域订阅门控。
 * 断言：
 * <ol>
 *   <li>选择器确定性：同一描述符两次 → 策略逐字段相同；hot→L1(differential)、warm→L2、cold→L3(encrypted)。</li>
 *   <li>差分：base→next 的 ops 与 added/changed/removed/same 计数正确、op 按 key 字典序确定；{@code apply(base, ops)}
 *       == next（往返精确成立）。</li>
 *   <li>无变更差分：zero ops、计数全 same。</li>
 *   <li>变更流：3 次顺序字段更新 → seq 1..3 有序；replay(base) == 末端快照；重建两次得到的 ops 列表逐项相同
 *       （确定性命中）。</li>
 *   <li>兴趣域：region_a 与 region_b 策略仅 domain 不同；订阅过滤确定性保留所需域名。</li>
 * </ol>
 * 退出码 0 = PASS，1 = FAIL。
 * <p>
 * p.2.4.3 three-tier payload-strategy core probe (pure JVM, no MC runtime): deterministic
 * {@link StrategySelector} selection, {@link IncrementalDiff} diffing &amp; round-trip,
 * {@link ChangeStream} ordered stream determinism, and interest-domain subscription gating.
 * Exit 0 = PASS.
 */
public final class NetworkStrategyProbe {

    private NetworkStrategyProbe() {
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

    public static void main(String[] args) {
        selectorDeterminism();
        incrementalDiff();
        noChangeDiff();
        changeStream();
        interestDomain();

        if (failures == 0) {
            System.out.println("[NetworkStrategyProbe] PASS (three-tier payload strategy core, " + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[NetworkStrategyProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }

    private static void selectorDeterminism() {
        StrategySelector.Descriptor hotA = new StrategySelector.Descriptor(StrategySelector.Frequency.HOT, "region_a", 4);
        StrategySelector.Descriptor warmA = new StrategySelector.Descriptor(StrategySelector.Frequency.WARM, "region_a", 4);
        StrategySelector.Descriptor coldA = new StrategySelector.Descriptor(StrategySelector.Frequency.COLD, "region_a", 4);

        PayloadStrategy h1 = StrategySelector.select(hotA);
        PayloadStrategy h2 = StrategySelector.select(hotA);
        check("选择器: 同一描述符两次 → 策略逐字段相同", h1.equals(h2));

        check("选择器: hot → L1 且 differential=true",
                StrategySelector.select(hotA).level() == PayloadLevel.L1
                        && StrategySelector.select(hotA).differential()
                        && !StrategySelector.select(hotA).encrypted());
        check("选择器: warm → L2",
                StrategySelector.select(warmA).level() == PayloadLevel.L2
                        && !StrategySelector.select(warmA).differential()
                        && !StrategySelector.select(warmA).encrypted());
        PayloadStrategy cold = StrategySelector.select(coldA);
        check("选择器: cold → L3 且 encrypted=true",
                cold.level() == PayloadLevel.L3
                        && cold.encrypted()
                        && !cold.differential());

        // Descriptor 校验：kind 负数抛 IAE。
        boolean throwsIae;
        try {
            StrategySelector.select(new StrategySelector.Descriptor(StrategySelector.Frequency.HOT, "d", -1));
            throwsIae = false;
        } catch (IllegalArgumentException e) {
            throwsIae = true;
        }
        check("选择器: 负 kindFlags 抛 IllegalArgumentException", throwsIae);
    }

    private static void incrementalDiff() {
        // base: hp=100, name=alice, x=1.5
        FieldSnapshot base = FieldSnapshot.builder()
                .putNum("hp", 100L)
                .putText("name", "alice")
                .putReal("x", 1.5)
                .build();
        // next: hp=90 (changed), name removed, x=1.5 (same), y=2 (added)
        FieldSnapshot next = FieldSnapshot.builder()
                .putNum("hp", 90L)
                .putNum("y", 2L)
                .putReal("x", 1.5)
                .build();

        Result r = IncrementalDiff.diff(base, next);

        Counts c = r.counts();
        check("差分: 计数 added=1, changed=1, removed=1, same=1",
                c.added() == 1 && c.changed() == 1 && c.removed() == 1 && c.same() == 1);

        // 期望 op：按 key 字典序 hp(changed→SET), name(UNSET), y(added→SET)；x 为 same 不出 op。
        // 但注意 name 与 y 的字典序：sorted keys = [hp, name, x, y] → ops=[set hp, unset name, set y]。
        List<DiffOp> expected = new ArrayList<>();
        expected.add(DiffOp.set("hp", FieldSnapshot.Value.num(90L)));
        expected.add(DiffOp.unset("name"));
        expected.add(DiffOp.set("y", FieldSnapshot.Value.num(2L)));
        check("差分: op 列表按 key 字典序、逐项等于预期", r.ops().equals(expected));

        // 确定性：再算一次 → 相同 op 列表。
        Result r2 = IncrementalDiff.diff(base, next);
        check("差分: 等价输入两次 → op 列表逐项相同", r.ops().equals(r2.ops()));

        // 往返：apply(base, ops) == next。
        FieldSnapshot rebuilt = IncrementalDiff.apply(base, r.ops());
        check("差分: apply(base, ops) == next（往返精确）", rebuilt.equals(next));
        check("差分: rebuilt 的 key 集合与 next 一致", rebuilt.sortedKeys().equals(next.sortedKeys()));
    }

    private static void noChangeDiff() {
        FieldSnapshot snap = FieldSnapshot.builder()
                .putNum("a", 7L)
                .putReal("b", 3.25)
                .putText("c", "hi")
                .build();
        Result r = IncrementalDiff.diff(snap, snap);
        Counts c = r.counts();
        check("无变更: ops 为空", r.ops().isEmpty());
        check("无变更: 计数 same=3 且 added/changed/removed 为 0",
                c.same() == 3 && c.added() == 0 && c.changed() == 0 && c.removed() == 0);
        check("无变更: apply(base, []) == base", IncrementalDiff.apply(snap, List.of()).equals(snap));
    }

    private static void changeStream() {
        // 起点 a=0, b=0
        FieldSnapshot base = FieldSnapshot.builder()
                .putNum("a", 0L)
                .putNum("b", 0L)
                .build();
        // 3 次顺序更新 → seq 1..3
        DiffOp op1 = DiffOp.set("a", FieldSnapshot.Value.num(1L));
        DiffOp op2 = DiffOp.set("b", FieldSnapshot.Value.num(2L));
        DiffOp op3 = DiffOp.set("a", FieldSnapshot.Value.num(3L));
        // 末端快照 a=3, b=2
        FieldSnapshot finalSnap = FieldSnapshot.builder()
                .putNum("a", 3L)
                .putNum("b", 2L)
                .build();

        List<DiffOp> ops = List.of(op1, op2, op3);
        ChangeStream s = ChangeStream.of(ops, finalSnap);

        check("变更流: seq 1..n 单调有序（列表下标+1）",
                s.size() == 3 && s.seqOf(op1) == 1 && s.seqOf(op2) == 2 && s.seqOf(op3) == 3);
        check("变更流: replay(base) == 末端快照", s.replay(base).equals(finalSnap));
        check("变更流: 依序应用后 a=3,b=2",
                s.replay(base).containsKey("a")
                        && s.replay(base).value("a").equals(FieldSnapshot.Value.num(3L))
                        && s.replay(base).value("b").equals(FieldSnapshot.Value.num(2L)));

        // 确定性：重建两次 → 相同的 ops 列表与末端快照。
        ChangeStream s2 = ChangeStream.of(ops, finalSnap);
        check("变更流: 重建两次 → ops 列表逐项相同", s.ops().equals(s2.ops()));
        check("变更流: 重建两次 → 流整体相等", s.equals(s2));
    }

    private static void interestDomain() {
        PayloadStrategy a = StrategySelector.select(new StrategySelector.Descriptor(
                StrategySelector.Frequency.HOT, "region_a", 0));
        PayloadStrategy b = StrategySelector.select(new StrategySelector.Descriptor(
                StrategySelector.Frequency.HOT, "region_b", 0));

        boolean sameExceptDomain = a.level() == b.level()
                && a.differential() == b.differential()
                && a.encrypted() == b.encrypted()
                && a.rateRank() == b.rateRank();
        check("兴趣域: region_a 与 region_b 除 domain 外其余字段相同", sameExceptDomain);
        check("兴趣域: region_a 与 region_b 策略不相等（domain 不同）", !a.equals(b));

        // 订阅过滤：所需域名 [region_a, region_c]，对三个策略过滤后仅保留 region_a，且两次运行确定一致。
        PayloadStrategy c = StrategySelector.select(new StrategySelector.Descriptor(
                StrategySelector.Frequency.HOT, "region_c", 0));
        List<PayloadStrategy> all = List.of(a, b, c);
        Set<String> wanted = Set.of("region_a", "region_c");

        List<PayloadStrategy> f1 = filter(all, wanted);
        List<PayloadStrategy> f2 = filter(all, wanted);
        check("兴趣域: 订阅过滤仅保留所需域名（region_a、region_c）",
                f1.size() == 2 && f1.contains(a) && f1.contains(c) && !f1.contains(b));
        check("兴趣域: 订阅过滤两次运行结果确定一致", f1.equals(f2));
    }

    private static List<PayloadStrategy> filter(List<PayloadStrategy> strategies, Set<String> wantedDomains) {
        List<PayloadStrategy> out = new ArrayList<>();
        for (PayloadStrategy s : strategies) {
            if (StrategySelector.subscribedTo(s, wantedDomains)) {
                out.add(s);
            }
        }
        return out;
    }
}