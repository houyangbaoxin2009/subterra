// p.2.26.5: deterministic time-scaling wiring probe — anchors the p.2.26 engine.time plane
// (p.2.26.1 TimeDomain/FlowRate/TimeScale/TimeThrottle/TimeInterpolation/TimeScaleDoc), the
// p.2.26.2 api.time contract surface (TimeDomain/TimeScaleSpec/TimeScaleApi mirror), the
// p.2.26.3 tick-budget scheduler (TimeBudgetScheduler/TimeBudgetKey reusing the p.2.8 kernel)
// and the p.2.26.4 runtime.time shell (TimeRuntime) to fixed-order / hardcoded / byte-identical
// assertions. Pure JVM — no MC runtime, no timestamps / random / timing; exit 0 = PASS,
// exit 1 = FAIL. NOT shipped in the mod jar.
package io.toterra.subterra.probes;

import io.toterra.subterra.api.time.TimeScaleApi;
import io.toterra.subterra.api.time.TimeScaleSpec;
import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.time.TimeBudgetKey;
import io.toterra.subterra.engine.time.TimeBudgetScheduler;
import io.toterra.subterra.engine.time.TimeDomain;
import io.toterra.subterra.engine.time.TimeInterpolation;
import io.toterra.subterra.engine.time.TimeScale;
import io.toterra.subterra.engine.time.TimeScaleDoc;
import io.toterra.subterra.engine.time.TimeThrottle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * p.2.26.5 — 时间缩放确定性/接线探针：把 p.2.26 的 engine.time 数据面（p.2.26.1 四域固定序
 * {@link TimeDomain}、回退链流速 {@link TimeScale}、确定性节流 {@link TimeThrottle}、同 double 位插值
 * {@link TimeInterpolation}、td 化文档 {@link TimeScaleDoc}）、p.2.26.2 的 api.time 契约面
 * （{@link TimeScaleSpec}/{@link TimeScaleApi} 镜像）、p.2.26.3 的 {@link TimeBudgetScheduler} 预算调度
 * 与 p.2.26.4 的 runtime.time 壳（{@code TimeRuntime}）锚定为纯 JVM 断言。纯 JVM——不碰 MC、无时序、
 * 无随机；exit 0 = PASS，exit 1 = FAIL；不进 mod jar。五节：
 * <ol>
 *   <li><b>核心确定性</b>：{@link TimeDomain#values()} 四域固定序（逐名核对 GLOBAL→ENTITY→ZONE→PLAYER）
 *       + {@code form()}/{@code fromForm()} 往返（含大小写不敏感与未知/空拒绝）；{@link TimeScale}
 *       with/get/effective 确定性——GLOBAL 回退链（未设非全局域回退全局、未设全局→1.0、isSet），连跑两遍 +
 *       重建摘要逐字符一致，非法值（≤0 / NaN）拒绝；{@link TimeThrottle#shouldTick(double,long)} 固定模式
 *       （rate>=1 全执行、rate<1 每 ceil(1/rate) 恰一次）与期望序列硬编码核对 + {@code nextTickDelta}
 *       硬编码；{@link TimeInterpolation#interpolate(double,double,double)} 同 double 位（硬编码期望值抽查 +
 *       连跑两遍）+ {@code of/snapshot} 固定序 + 非法输入拒绝。</li>
 *   <li><b>api 契约镜像</b>：api.time.{@code TimeDomain} 与 engine.time.{@link TimeDomain} 语义一致
 *       （四域同名同序、form()/fromForm() 行为一致）；{@link TimeScaleSpec}/{@link TimeScaleApi} 与 engine
 *       镜像行为对照（同输入同输出，double 以 {@code doubleToLongBits} 逐位比较）。</li>
 *   <li><b>预算调度</b>：{@link TimeBudgetScheduler} 固定 key 序（TreeMap 域序 + 域内 id 升序，期望序列
 *       硬编码）、超预算确定性跳过（GLOBAL/3 与 ENTITY/5 恒不出现）、同 tick 同输出（独立实例逐键一致）、
 *       连跑两遍同字节、reset 原子记账（每 tick 恒 7 命中、4 tick 累计 28 与 TimeRuntime 标称一致）、窗口
 *       语义（未 reset 先 schedule / tick 不匹配 → IAE）、重复 tickBudget / 无预算 register / cap&lt;1 拒绝。</li>
 *   <li><b>td 文档</b>：{@link TimeScaleDoc} fromTd/toTd 往返恒等 + 规范化 toTd→fromTd→toTd 连跑两遍同字节
 *       + 缺省 global=1.0 / 未知键忽略 / 缺失覆盖档保持未设置 + 非法值拒绝。</li>
 *   <li><b>接线契约（静态盘点）</b>：runtime.time 的 {@code TimeRuntime} 类存在（只加载不初始化，纯 JVM 不
 *       触发 MC {@code LogUtils} 静态初始化）+ 类字节含门控属性串 {@code subterra.probe.time} + marker 前缀
 *       {@code [Subterra time]}；AsyncE2EProbe 类字节含 {@code [Subterra time]} timeOk 断言字面量——真实
 *       boot 生命周期由 AsyncE2EProbe timeOk 槽覆盖。</li>
 * </ol>
 * 确定性纪律：固定序、无时序、无随机；全部线性遍历（禁 O(n²)）；失败计数只在失败路径自增；全过输出
 * {@code [TimeProbe] PASS (n checks)} exit 0，否则 FAIL exit 1。
 *
 * <p>p.2.26.5 — deterministic time-scaling wiring probe: anchors the p.2.26 engine.time data plane
 * (the p.2.26.1 four-domain fixed-order {@link TimeDomain}, fall-back-chain {@link TimeScale},
 * deterministic {@link TimeThrottle}, bit-identical {@link TimeInterpolation}, td-ized
 * {@link TimeScaleDoc}), the p.2.26.2 api.time contract surface (the {@link TimeScaleSpec} /
 * {@link TimeScaleApi} mirror), the p.2.26.3 {@link TimeBudgetScheduler} budget scheduling and the
 * p.2.26.4 runtime.time shell ({@code TimeRuntime}) to pure-JVM assertions. Pure JVM — no MC runtime,
 * no timing, no randomness; exit 0 = PASS, exit 1 = FAIL; never shipped in the mod jar. Five sections:
 * <ol>
 *   <li><b>Core determinism</b>: {@link TimeDomain#values()} fixed four-domain order (name-checked
 *       GLOBAL→ENTITY→ZONE→PLAYER) + full {@code form()}/{@code fromForm()} round-trip (case-insensitive,
 *       unknown/blank rejected); {@link TimeScale} with/get/effective determinism — the GLOBAL fall-back
 *       chain (an unset non-global domain falls back to global, an unset GLOBAL resolves to 1.0, isSet),
 *       char-identical digest across two runs plus a from-scratch rebuild, invalid (≤0 / NaN) rejection;
 *       {@link TimeThrottle#shouldTick(double,long)} fixed pattern (rate&gt;=1 executes every tick,
 *       rate&lt;1 exactly once per ceil(1/rate)) vs. hardcoded expected sequences, plus hardcoded
 *       {@code nextTickDelta} nominals; {@link TimeInterpolation#interpolate(double,double,double)}
 *       double-bit-identical (hardcoded expected-value spot checks + two runs) + {@code of/snapshot}
 *       fixed order + invalid-input rejection.</li>
 *   <li><b>api contract mirror</b>: api.time {@code TimeDomain} vs engine.time {@link TimeDomain}
 *       (same four names in the same order, identical form()/fromForm() behavior); {@link TimeScaleSpec} /
 *       {@link TimeScaleApi} vs the engine mirror (same input → same output, doubles compared bit-for-bit
 *       via {@code doubleToLongBits}).</li>
 *   <li><b>Budget scheduling</b>: {@link TimeBudgetScheduler} fixed key order (TreeMap domain order +
 *       ascending id within a domain, hardcoded expected sequence), deterministic over-budget skip
 *       (GLOBAL/3 and ENTITY/5 never appear), same tick → same output (independent instances, key-for-key),
 *       byte-identical across two runs, atomic reset accounting (exactly 7 hits per tick, 28 over
 *       4 ticks — matching the TimeRuntime nominal), window semantics (schedule-before-reset / tick
 *       mismatch → IAE), duplicate tickBudget / register-without-budget / cap&lt;1 rejection.</li>
 *   <li><b>td document</b>: {@link TimeScaleDoc} fromTd/toTd round-trip identity + normalized
 *       toTd→fromTd→toTd twice byte-identical + default global=1.0 / unknown-key ignore / absent
 *       overrides stay unset + invalid-value rejection.</li>
 *   <li><b>Wiring contract (static inventory)</b>: the runtime.time {@code TimeRuntime} class is present
 *       (load-only, never initialized — the pure JVM must not trigger the MC {@code LogUtils} static
 *       init), its class bytes carry the gate string {@code subterra.probe.time} and the marker prefix
 *       {@code [Subterra time]}; the AsyncE2EProbe class bytes carry the {@code [Subterra time]} timeOk
 *       assertion literal — the real boot lifecycle is covered by the AsyncE2EProbe timeOk slot.</li>
 * </ol>
 * Determinism discipline: fixed order, no timing, no randomness; all traversals linear (no O(n²));
 * failures are counted only on failing paths; PASS only when all checks pass, then exit 0, else FAIL
 * with counts and exit 1.
 */
public final class TimeProbe {

    private TimeProbe() {
    }

    private static int checks = 0;
    private static int failures = 0;

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
        try {
            coreDeterminism();
            apiContractMirror();
            budgetScheduling();
            tdDocument();
            wiringContract();
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] probe exception: " + e);
            e.printStackTrace(System.out);
        }
        if (failures == 0) {
            System.out.println("[TimeProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[TimeProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    /** 动作抛出 IAE 为真。True iff the action raises IllegalArgumentException. */
    private static boolean throwsIAE(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    /** 固定四域 {@link TimeDomain} 序的流速摘要 {@code form=value} 逗号连接（确定性复验载体）。A rate
     *  digest in fixed {@link TimeDomain} order ({@code form=value} join) — a determinism-replay carrier. */
    private static String scaleDigest(TimeScale scale) {
        StringBuilder sb = new StringBuilder();
        for (TimeDomain d : TimeDomain.values()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(d.form()).append('=').append(scale.get(d));
        }
        return sb.toString();
    }

    /** 固定键序摘要：{@code domain/id} 逗号连接（确定性复验载体）。Key-order digest ({@code domain/id}
     *  join) — a determinism-replay carrier. */
    private static String keyDigest(List<TimeBudgetKey> keys) {
        StringBuilder sb = new StringBuilder();
        for (TimeBudgetKey k : keys) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(k.domain().form()).append('/').append(k.simulantId());
        }
        return sb.toString();
    }

    /** 布尔 tick 序列 → T/F 字符串（硬编码核对载体）。Boolean tick sequence → a T/F string (hardcoded
     *  cross-check carrier). */
    private static String seq(boolean[] ticks) {
        StringBuilder sb = new StringBuilder(ticks.length);
        for (boolean b : ticks) {
            sb.append(b ? 'T' : 'F');
        }
        return sb.toString();
    }

    /** 固定 {@code (rate, tick)} 窗口内 {@code ceil(1/rate)} 步长下恰一次执行的统计核对。Counts exactly
     *  one execution per {@code ceil(1/rate)}-wide window. */
    private static boolean oncePerWindow(double rate, int window, int ticks) {
        for (int start = 0; start + window <= ticks; start += window) {
            int c = 0;
            for (int i = start; i < start + window; i++) {
                if (TimeThrottle.shouldTick(rate, i)) {
                    c++;
                }
            }
            if (c != 1) {
                return false;
            }
        }
        return true;
    }

    /** 与 TimeRuntime 同构的固定预算驱动（p.2.26.3 复用 p.2.8 记账内核）。The fixed budget drive
     *  isomorphic to TimeRuntime (p.2.26.3 reusing the p.2.8 accounting kernel). */
    private static TimeBudgetScheduler fixedScheduler() {
        return new TimeBudgetScheduler()
                .tickBudget(TimeDomain.GLOBAL, 2)
                .register(TimeDomain.GLOBAL, 1L)
                .register(TimeDomain.GLOBAL, 2L)
                .register(TimeDomain.GLOBAL, 3L)
                .tickBudget(TimeDomain.ENTITY, 1)
                .register(TimeDomain.ENTITY, 4L)
                .register(TimeDomain.ENTITY, 5L)
                .tickBudget(TimeDomain.ZONE, 3)
                .register(TimeDomain.ZONE, 6L)
                .register(TimeDomain.ZONE, 7L)
                .tickBudget(TimeDomain.PLAYER, 2)
                .register(TimeDomain.PLAYER, 8L)
                .register(TimeDomain.PLAYER, 9L);
    }

    // ---------- (1) core determinism (p.2.26.1) ----------

    private static void coreDeterminism() {
        // TimeDomain: fixed values() order (4) + form/fromForm full round-trip + case-insensitive
        // parsing and unknown/blank rejection.
        List<String> expected = List.of("GLOBAL", "ENTITY", "ZONE", "PLAYER");
        TimeDomain[] vals = TimeDomain.values();
        boolean fixedOrder = vals.length == 4;
        for (int i = 0; i < vals.length && i < expected.size(); i++) {
            fixedOrder = fixedOrder && vals[i].name().equals(expected.get(i));
        }
        check("TimeDomain: values() 固定序（4 域，逐名核对 GLOBAL→ENTITY→ZONE→PLAYER）", fixedOrder);
        boolean roundTrip = true;
        for (TimeDomain d : TimeDomain.values()) {
            roundTrip = roundTrip && TimeDomain.fromForm(d.form()) == d;
        }
        check("TimeDomain: form()/fromForm() 全量往返（4 域各一次）", roundTrip);
        check("TimeDomain: fromForm 大小写不敏感 + 空白容忍（\" Player \"→PLAYER）",
                TimeDomain.fromForm(" Player ") == TimeDomain.PLAYER
                        && TimeDomain.fromForm("ZONE") == TimeDomain.ZONE);
        check("TimeDomain: 未知/空/null form 拒绝（确定性）",
                throwsIAE(() -> TimeDomain.fromForm("nether"))
                        && throwsIAE(() -> TimeDomain.fromForm(""))
                        && throwsIAE(() -> TimeDomain.fromForm(null)));

        // TimeScale: with/get/effective determinism — the GLOBAL fall-back chain.
        TimeScale scale = TimeScale.of(Map.of(TimeDomain.GLOBAL, 2.0, TimeDomain.ENTITY, 0.5));
        check("TimeScale: with/get 确定性（GLOBAL=2.0, ENTITY=0.5；未设 ZONE/PLAYER→1.0）",
                scale.get(TimeDomain.GLOBAL) == 2.0
                        && scale.get(TimeDomain.ENTITY) == 0.5
                        && scale.get(TimeDomain.ZONE) == 1.0
                        && scale.get(TimeDomain.PLAYER) == 1.0);
        check("TimeScale: 未设非全局域回退全局（effective(ZONE)=effective(GLOBAL)=2.0）",
                scale.effective(TimeDomain.ZONE) == scale.effective(TimeDomain.GLOBAL)
                        && scale.effective(TimeDomain.ZONE) == 2.0
                        && scale.effective(TimeDomain.PLAYER) == 2.0);
        check("TimeScale: 未设全局→1.0（neutral() 与仅设 ENTITY 皆然）",
                TimeScale.neutral().effective(TimeDomain.GLOBAL) == 1.0
                        && TimeScale.neutral().effective(TimeDomain.ZONE) == 1.0
                        && TimeScale.of(Map.of(TimeDomain.ENTITY, 0.5)).effective(TimeDomain.GLOBAL) == 1.0
                        && TimeScale.of(Map.of(TimeDomain.ENTITY, 0.5)).effective(TimeDomain.ZONE) == 1.0);
        check("TimeScale: isSet 确定性（GLOBAL/ENTITY=true, ZONE/PLAYER=false）",
                scale.isSet(TimeDomain.GLOBAL) && scale.isSet(TimeDomain.ENTITY)
                        && !scale.isSet(TimeDomain.ZONE) && !scale.isSet(TimeDomain.PLAYER));
        TimeScale derived = scale.with(TimeDomain.ZONE, 3.0);
        check("TimeScale: with 派生（ZONE=3.0 → isSet+effective；PLAYER 仍回退 2.0）",
                derived.isSet(TimeDomain.ZONE) && derived.effective(TimeDomain.ZONE) == 3.0
                        && derived.get(TimeDomain.ZONE) == 3.0
                        && !derived.isSet(TimeDomain.PLAYER)
                        && derived.effective(TimeDomain.PLAYER) == derived.effective(TimeDomain.GLOBAL)
                        && derived.effective(TimeDomain.PLAYER) == 2.0);
        List<TimeDomain> keyed = new ArrayList<>(scale.rates().keySet());
        check("TimeScale: rates() 四域齐备 + 固定枚举序键",
                keyed.equals(List.of(TimeDomain.GLOBAL, TimeDomain.ENTITY, TimeDomain.ZONE, TimeDomain.PLAYER))
                        && scale.rates().size() == 4);
        check("TimeScale: 非法值拒绝（of: 0/负/NaN；with: 0）",
                throwsIAE(() -> TimeScale.of(Map.of(TimeDomain.GLOBAL, 0.0)))
                        && throwsIAE(() -> TimeScale.of(Map.of(TimeDomain.GLOBAL, -1.0)))
                        && throwsIAE(() -> TimeScale.of(Map.of(TimeDomain.GLOBAL, Double.NaN)))
                        && throwsIAE(() -> scale.with(TimeDomain.GLOBAL, 0.0)));
        String d1 = scaleDigest(scale);
        String d2 = scaleDigest(TimeScale.of(Map.of(TimeDomain.GLOBAL, 2.0, TimeDomain.ENTITY, 0.5)));
        check("TimeScale: 连跑两遍 + 重建摘要逐字符一致（同 double 位）",
                d1.equals(d2) && d1.equals("global=2.0,entity=0.5,zone=1.0,player=1.0"));

        // TimeThrottle: rate >= 1 runs every tick; rate < 1 runs exactly once per ceil(1/rate).
        boolean full = true;
        for (int i = 0; i < 10; i++) {
            full = full && TimeThrottle.shouldTick(1.0D, i) && TimeThrottle.shouldTick(2.0D, i);
        }
        check("TimeThrottle: rate>=1 全执行（1.0/2.0 的 tick 0..9 恒 true）", full);
        boolean[] half = new boolean[10];
        boolean[] quarter = new boolean[8];
        boolean[] fifth = new boolean[10];
        for (int i = 0; i < half.length; i++) {
            half[i] = TimeThrottle.shouldTick(0.5D, i);
        }
        for (int i = 0; i < quarter.length; i++) {
            quarter[i] = TimeThrottle.shouldTick(0.25D, i);
        }
        for (int i = 0; i < fifth.length; i++) {
            fifth[i] = TimeThrottle.shouldTick(0.2D, i);
        }
        check("TimeThrottle: rate=0.5 期望序列硬编码 TFTFTFTFTF（窗口内恰一次）",
                seq(half).equals("TFTFTFTFTF") && oncePerWindow(0.5D, 2, 10));
        check("TimeThrottle: rate=0.25 期望序列硬编码 TFFFTFFF", seq(quarter).equals("TFFFTFFF"));
        check("TimeThrottle: rate=0.2 期望序列硬编码 TFFFFTFFFF", seq(fifth).equals("TFFFFTFFFF"));
        check("TimeThrottle: nextTickDelta 硬编码（0.5→2, 0.25→4, 0.2→5, 0.1→10, 0.3→4, 1.0/2.0→1）",
                TimeThrottle.nextTickDelta(0.5D) == 2L
                        && TimeThrottle.nextTickDelta(0.25D) == 4L
                        && TimeThrottle.nextTickDelta(0.2D) == 5L
                        && TimeThrottle.nextTickDelta(0.1D) == 10L
                        && TimeThrottle.nextTickDelta(0.3D) == 4L
                        && TimeThrottle.nextTickDelta(1.0D) == 1L
                        && TimeThrottle.nextTickDelta(2.0D) == 1L);
        check("TimeThrottle: 非法 rate/tick 拒绝（0/负/NaN/+∞/tick<0）",
                throwsIAE(() -> TimeThrottle.shouldTick(0.0D, 0))
                        && throwsIAE(() -> TimeThrottle.shouldTick(-1.0D, 0))
                        && throwsIAE(() -> TimeThrottle.shouldTick(Double.NaN, 0))
                        && throwsIAE(() -> TimeThrottle.shouldTick(Double.POSITIVE_INFINITY, 0))
                        && throwsIAE(() -> TimeThrottle.shouldTick(1.0D, -1))
                        && throwsIAE(() -> TimeThrottle.nextTickDelta(0.0D)));

        // TimeInterpolation: bit-identical linear form, hardcoded expected values.
        check("TimeInterpolation: 硬编码期望值抽查（0.5/2.0/12.5/2.0/外推 15.0）",
                TimeInterpolation.interpolate(0.0D, 1.0D, 0.5D) == 0.5D
                        && TimeInterpolation.interpolate(1.0D, 3.0D, 0.5D) == 2.0D
                        && TimeInterpolation.interpolate(10.0D, 20.0D, 0.25D) == 12.5D
                        && TimeInterpolation.interpolate(-4.0D, 4.0D, 0.75D) == 2.0D
                        && TimeInterpolation.interpolate(0.0D, 10.0D, 1.5D) == 15.0D);
        check("TimeInterpolation: 连跑两遍同 double 位（doubleToLongBits）",
                Double.doubleToLongBits(TimeInterpolation.interpolate(1.0D, 3.0D, 0.5D))
                        == Double.doubleToLongBits(TimeInterpolation.interpolate(1.0D, 3.0D, 0.5D)));
        TimeInterpolation interp = TimeInterpolation.of(Map.of(
                TimeDomain.ENTITY, new TimeInterpolation.Endpoint(0.0D, 10.0D),
                TimeDomain.ZONE, new TimeInterpolation.Endpoint(5.0D, 7.0D)));
        Map<TimeDomain, TimeInterpolation.Endpoint> snap = interp.snapshot().endpoints();
        List<TimeDomain> snapKeys = new ArrayList<>(snap.keySet());
        check("TimeInterpolation.of: 缺省中性端点 + snapshot 固定序（四域齐备、values() 序）",
                interp.interpolate(TimeDomain.ENTITY, 0.5D) == 5.0D
                        && interp.interpolate(TimeDomain.ZONE, 0.5D) == 6.0D
                        && interp.interpolate(TimeDomain.GLOBAL, 0.5D) == 0.0D
                        && interp.interpolate(TimeDomain.PLAYER, 0.5D) == 0.0D
                        && snapKeys.equals(List.of(TimeDomain.GLOBAL, TimeDomain.ENTITY,
                                TimeDomain.ZONE, TimeDomain.PLAYER)));
        check("TimeInterpolation: Endpoint 插值 + 非法输入拒绝（NaN 端点/alpha）",
                new TimeInterpolation.Endpoint(1.0D, 3.0D).interpolate(0.5D) == 2.0D
                        && throwsIAE(() -> new TimeInterpolation.Endpoint(Double.NaN, 1.0D))
                        && throwsIAE(() -> new TimeInterpolation.Endpoint(1.0D, Double.NaN))
                        && throwsIAE(() -> TimeInterpolation.interpolate(Double.NaN, 1.0D, 0.5D))
                        && throwsIAE(() -> new TimeInterpolation.Endpoint(1.0D, 3.0D).interpolate(Double.NaN)));
    }

    // ---------- (2) api contract mirror (p.2.26.2) ----------

    private static void apiContractMirror() {
        // api.time.TimeDomain vs engine.time.TimeDomain: same four names in the same order + the same
        // form() outputs + the same fromForm() behavior.
        io.toterra.subterra.api.time.TimeDomain[] apiVals = io.toterra.subterra.api.time.TimeDomain.values();
        TimeDomain[] engVals = TimeDomain.values();
        boolean sameOrder = apiVals.length == 4 && engVals.length == 4;
        boolean sameForms = true;
        for (int i = 0; i < 4; i++) {
            sameOrder = sameOrder && apiVals[i].name().equals(engVals[i].name());
            sameForms = sameForms && apiVals[i].form().equals(engVals[i].form());
        }
        check("api.time.TimeDomain: 与 engine.time.TimeDomain 同名同序 + form() 一致（4 域）",
                sameOrder && sameForms);
        check("api.time.TimeDomain: fromForm 往返一致（大小写不敏感 + 未知拒绝）",
                io.toterra.subterra.api.time.TimeDomain.fromForm(" PLAYER ") == io.toterra.subterra.api.time.TimeDomain.PLAYER
                        && io.toterra.subterra.api.time.TimeDomain.fromForm("entity") == io.toterra.subterra.api.time.TimeDomain.ENTITY
                        && throwsIAE(() -> io.toterra.subterra.api.time.TimeDomain.fromForm("nether")));

        // TimeScaleSpec vs TimeScale: same input → same output (fall-back chain included, bit-for-bit).
        Map<TimeDomain, Double> erates = new EnumMap<>(TimeDomain.class);
        erates.put(TimeDomain.GLOBAL, 2.0D);
        erates.put(TimeDomain.ENTITY, 0.5D);
        TimeScale es = TimeScale.of(erates);
        Map<io.toterra.subterra.api.time.TimeDomain, Double> arates =
                new EnumMap<>(io.toterra.subterra.api.time.TimeDomain.class);
        arates.put(io.toterra.subterra.api.time.TimeDomain.GLOBAL, 2.0D);
        arates.put(io.toterra.subterra.api.time.TimeDomain.ENTITY, 0.5D);
        TimeScaleSpec aspec = TimeScaleSpec.of(arates);
        check("TimeScaleSpec vs TimeScale: of/get/effective/isSet 同输入同输出（回退链同 double 位）",
                Double.doubleToLongBits(aspec.get(io.toterra.subterra.api.time.TimeDomain.ZONE))
                        == Double.doubleToLongBits(es.get(TimeDomain.ZONE))
                        && Double.doubleToLongBits(aspec.effective(io.toterra.subterra.api.time.TimeDomain.ZONE))
                        == Double.doubleToLongBits(es.effective(TimeDomain.ZONE))
                        && Double.doubleToLongBits(aspec.effective(io.toterra.subterra.api.time.TimeDomain.GLOBAL))
                        == Double.doubleToLongBits(es.effective(TimeDomain.GLOBAL))
                        && aspec.isSet(io.toterra.subterra.api.time.TimeDomain.ZONE) == es.isSet(TimeDomain.ZONE)
                        && aspec.isSet(io.toterra.subterra.api.time.TimeDomain.ENTITY) == es.isSet(TimeDomain.ENTITY));
        check("TimeScaleSpec vs TimeScale: with 派生镜像一致",
                Double.doubleToLongBits(
                        aspec.with(io.toterra.subterra.api.time.TimeDomain.ZONE, 3.0D)
                                .effective(io.toterra.subterra.api.time.TimeDomain.ZONE))
                        == Double.doubleToLongBits(es.with(TimeDomain.ZONE, 3.0D).effective(TimeDomain.ZONE))
                        && Double.doubleToLongBits(
                        aspec.with(io.toterra.subterra.api.time.TimeDomain.ZONE, 3.0D)
                                .effective(io.toterra.subterra.api.time.TimeDomain.PLAYER))
                        == Double.doubleToLongBits(es.with(TimeDomain.ZONE, 3.0D).effective(TimeDomain.PLAYER)));

        // TimeScaleApi vs engine: interpolation bit-identical, throttle decisions identical.
        double[][] interpSamples = {
                {1.0D, 3.0D, 0.5D},
                {0.0D, 1.0D, 0.5D},
                {10.0D, 20.0D, 0.25D},
                {-4.0D, 4.0D, 0.75D},
                {0.0D, 10.0D, 1.5D},
        };
        boolean interpMirror = true;
        for (double[] s : interpSamples) {
            interpMirror = interpMirror
                    && Double.doubleToLongBits(TimeScaleApi.interpolate(s[0], s[1], s[2]))
                    == Double.doubleToLongBits(TimeInterpolation.interpolate(s[0], s[1], s[2]));
        }
        check("TimeScaleApi vs engine: interpolate 同 double 位（5 组输入）", interpMirror);
        double[] throttleRates = {0.5D, 0.25D, 0.2D, 0.1D, 1.0D, 2.0D};
        boolean throttleMirror = true;
        for (double r : throttleRates) {
            throttleMirror = throttleMirror
                    && TimeScaleApi.nextTickDelta(r) == TimeThrottle.nextTickDelta(r);
            for (int t = 0; t < 6; t++) {
                throttleMirror = throttleMirror
                        && TimeScaleApi.shouldTick(r, t) == TimeThrottle.shouldTick(r, t);
            }
        }
        check("TimeScaleApi vs engine: shouldTick/nextTickDelta 镜像一致（6 组 rate × 6 tick）", throttleMirror);
    }

    // ---------- (3) budget scheduling (p.2.26.3) ----------

    private static void budgetScheduling() {
        List<TimeBudgetKey> expected = List.of(
                new TimeBudgetKey(TimeDomain.GLOBAL, 1L),
                new TimeBudgetKey(TimeDomain.GLOBAL, 2L),
                new TimeBudgetKey(TimeDomain.ENTITY, 4L),
                new TimeBudgetKey(TimeDomain.ZONE, 6L),
                new TimeBudgetKey(TimeDomain.ZONE, 7L),
                new TimeBudgetKey(TimeDomain.PLAYER, 8L),
                new TimeBudgetKey(TimeDomain.PLAYER, 9L));
        TimeBudgetScheduler sched = fixedScheduler();
        sched.reset(0L);
        List<TimeBudgetKey> run = sched.schedule(0L);
        check("TimeBudgetScheduler: 固定 key 序（TreeMap 域序 + 域内 id 升序，期望序列硬编码）",
                run.equals(expected)
                        && keyDigest(run).equals("global/1,global/2,entity/4,zone/6,zone/7,player/8,player/9"));
        boolean noOverBudget = run.size() == 7;
        for (TimeBudgetKey k : run) {
            noOverBudget = noOverBudget
                    && !(k.domain() == TimeDomain.GLOBAL && k.simulantId() == 3L)
                    && !(k.domain() == TimeDomain.ENTITY && k.simulantId() == 5L);
        }
        check("TimeBudgetScheduler: 超预算确定性跳过（GLOBAL/3 与 ENTITY/5 恒不出现）", noOverBudget);

        // Same tick → same output across independent instances; byte-identical across a rebuild.
        TimeBudgetScheduler schedB = fixedScheduler();
        schedB.reset(0L);
        check("TimeBudgetScheduler: 同 tick 同输出（独立实例逐键一致）", schedB.schedule(0L).equals(run));
        check("TimeBudgetScheduler: 连跑两遍同字节（重建摘要一致）",
                keyDigest(fixedScheduler().reset(0L).schedule(0L)).equals(keyDigest(run)));

        // reset atomic accounting: every tick re-opens a zeroed window — 7 hits per tick, 28 over 4 ticks
        // (matching the TimeRuntime NOMINAL_TICK_HITS nominal).
        int hits = 0;
        boolean perTick = true;
        for (long t = 0L; t < 4L; t++) {
            int n = fixedScheduler().reset(t).schedule(t).size();
            perTick = perTick && n == 7;
            hits += n;
        }
        check("TimeBudgetScheduler: reset 原子记账（每 tick 恒 7 命中；4 tick 累计 28 与 TimeRuntime 标称一致）",
                perTick && hits == 28);

        // Window semantics: schedule requires a matching reset first.
        TimeBudgetScheduler win = new TimeBudgetScheduler().tickBudget(TimeDomain.GLOBAL, 2)
                .register(TimeDomain.GLOBAL, 1L);
        check("TimeBudgetScheduler: 窗口语义（未 reset 先 schedule → IAE；tick 不匹配 → IAE）",
                throwsIAE(() -> win.schedule(0L))
                        && (win.reset(0L) == win) && throwsIAE(() -> win.schedule(1L))
                        && win.schedule(0L).size() == 1);
        check("TimeBudgetScheduler: 重复 tickBudget / 无预算 register / cap<1 拒绝",
                throwsIAE(() -> new TimeBudgetScheduler().tickBudget(TimeDomain.GLOBAL, 2)
                        .tickBudget(TimeDomain.GLOBAL, 3))
                        && throwsIAE(() -> new TimeBudgetScheduler().tickBudget(TimeDomain.GLOBAL, 2)
                        .register(TimeDomain.ENTITY, 1L))
                        && throwsIAE(() -> new TimeBudgetScheduler().tickBudget(TimeDomain.GLOBAL, 0)));
    }

    // ---------- (4) td document (TimeScaleDoc round-trip) ----------

    private static void tdDocument() {
        TimeScale scale = TimeScale.of(Map.of(TimeDomain.GLOBAL, 2.0, TimeDomain.ENTITY, 0.5));
        TdTable td = TimeScaleDoc.toTd(scale);
        TimeScale back = TimeScaleDoc.fromTd(td);
        check("TimeScaleDoc: toTd→fromTd 往返恒等（equals + rates + isSet 一致）",
                back.equals(scale)
                        && back.get(TimeDomain.GLOBAL) == 2.0
                        && back.get(TimeDomain.ENTITY) == 0.5
                        && !back.isSet(TimeDomain.ZONE)
                        && back.effective(TimeDomain.ZONE) == 2.0);
        String w1 = Td.write(TimeScaleDoc.toTd(TimeScaleDoc.fromTd(td)));
        String w2 = Td.write(TimeScaleDoc.toTd(TimeScaleDoc.fromTd(td)));
        check("TimeScaleDoc: toTd→fromTd→toTd 规范化恒等 + 连跑两遍同字节",
                w1.equals(w2) && w1.equals(Td.write(td)));

        TdTable onlyEntity = TdTable.builder().put("entity", TdValue.of(0.5)).build();
        TimeScale parsed = TimeScaleDoc.fromTd(onlyEntity);
        TdTable unknownKey = TdTable.builder().put("extra", TdValue.of(99.0)).put("global", TdValue.of(2.0)).build();
        check("TimeScaleDoc: fromTd 缺省 global=1.0 + 未知键忽略 + 缺失覆盖档保持未设置",
                parsed.isSet(TimeDomain.GLOBAL) && parsed.get(TimeDomain.GLOBAL) == 1.0
                        && !parsed.isSet(TimeDomain.ZONE)
                        && parsed.effective(TimeDomain.ZONE) == 1.0
                        && TimeScaleDoc.fromTd(unknownKey).get(TimeDomain.GLOBAL) == 2.0);
        check("TimeScaleDoc: 非法值拒绝（global≤0）",
                throwsIAE(() -> TimeScaleDoc.fromTd(
                        TdTable.builder().put("global", TdValue.of(-1.0)).build())));
    }

    // ---------- (5) wiring contract (static inventory, load-only) ----------

    private static void wiringContract() {
        String timeRuntime = "io.toterra.subterra.runtime.time.TimeRuntime";
        check("wiring: runtime.time.TimeRuntime 类存在（只加载不初始化，纯 JVM 不触发 MC LogUtils）",
                classExists(timeRuntime));
        check("wiring: TimeRuntime 类字节含门控属性串 'subterra.probe.time'",
                classBytesContain(timeRuntime, "subterra.probe.time"));
        check("wiring: TimeRuntime 类字节含 marker 前缀 '[Subterra time]'",
                classBytesContain(timeRuntime, "[Subterra time]"));
        // 真实生命周期由 boot E2E 覆盖：AsyncE2EProbe timeOk 槽断言字面量的静态存在性。
        check("wiring: AsyncE2EProbe 类字节含 timeOk 断言（'[Subterra time]' marker，真实 boot 生命周期覆盖）",
                classBytesContain("io.toterra.subterra.probes.AsyncE2EProbe", "[Subterra time]"));
    }

    /** 只加载不初始化地确认类存在。Loads without initializing — presence only. */
    private static boolean classExists(String fqcn) {
        try {
            Class.forName(fqcn, false, TimeProbe.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** 断言类的 .class 字节包含某字符串字面量（常量池 UTF-8，纯 ASCII，ISO-8859-1 逐字节映射，
     *  线性 contains、无 O(n²)）。Asserts the class bytes contain a string literal (constant-pool
     *  UTF-8; the strings are pure ASCII so ISO-8859-1 is a byte-identity mapping — a linear
     *  contains scan, no O(n²)). */
    private static boolean classBytesContain(String fqcn, String literal) {
        String resource = "/" + fqcn.replace('.', '/') + ".class";
        try (InputStream in = TimeProbe.class.getResourceAsStream(resource)) {
            if (in == null) {
                return false;
            }
            byte[] bytes = in.readAllBytes();
            return new String(bytes, StandardCharsets.ISO_8859_1).contains(literal);
        } catch (IOException e) {
            return false;
        }
    }
}
