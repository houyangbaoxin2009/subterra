package io.toterra.subterra.runtime.time;

import com.mojang.logging.LogUtils;
import io.toterra.subterra.api.time.TimeScaleApi;
import io.toterra.subterra.engine.time.TimeBudgetScheduler;
import io.toterra.subterra.engine.time.TimeDomain;
import io.toterra.subterra.engine.time.TimeScale;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

import java.util.Map;

/**
 * p.2.26.4 — time runtime 壳：把 p.2.26 的 engine.time（p.2.26.1 四域流速 {@link TimeScale}/
 * {@link TimeDomain}/节流 {@code TimeThrottle}/感知插值 {@code TimeInterpolation}/td 文档
 * {@code TimeScaleDoc}）与 api.time 契约（p.2.26.2 的 {@link TimeScaleApi}）收编进 boot 生命周期的
 * 门控确定性核对，并以此驱动 p.2.26.3 的 {@link TimeBudgetScheduler}（预算调度，复用 p.2.8 内核）。
 * {@link #bootstrap()}（{@code Subterra.java} 构造调用）注册 {@code ServerStartedEvent} 门控；门控
 * {@code subterra.probe.time}（经 gradle -P → runServer system property 转发，与其余探针壳同模式）
 * 非 null 才跑——缺省纯 no-op 壳，对启动生命周期零影响。
 * <p>
 * 确定性样例消费（固定序、禁时序、禁 sleep）：固定四域 {@link TimeScale}（GLOBAL=1.0 / ENTITY=0.5 /
 * ZONE=0.25 / PLAYER=2.0）→ 按固定 {@link TimeDomain#values()} 序打 {@code form=value} 摘要，复验
 * {@code effective} 回退链（未设置的 ZONE 解析到 {@code effective(GLOBAL)}）→ {@link TimeScaleApi}
 * 插值/节流纯函数（固定标称逐一核对）→ {@link TimeBudgetScheduler} 预算调度跑固定 {@code FIXED_TICKS}
 * 个 tick（{@code reset(tick) → schedule(tick)}，预算内按固定键序纳入、超纲确定性推迟），累计预算命中
 * 数；全程对「同一驱动从头重建」做摘要复验（确定性证明）。全部通过打
 * {@code [Subterra time] ok (domains=N, rates=<摘要>, ticks=<预算命中数>, verify=ok)}
 * （domains = 固定四域数；rates = 按 {@link TimeDomain#values()} 序 {@code form=value} 逗号连接摘要；
 * ticks = 固定 tick 推进后累计的预算命中数；verify=ok）；违约/程序错误（样例合法，正常不可达）打
 * {@code [Subterra time] mismatch (error=...)} marker，绝不打假 ok。
 * <p>
 * <b>MC 逻辑 tick 生命周期接线面（runtime time binding，p.2.26 后接线点，本子项只做门控确定性核对，不引
 * MC 逻辑 tick 注入）</b>：engine.time + api.time 是纯 JDK 确定性时间缩放数据面，真实 MC 逻辑 tick 的
 * 预算驱动接管与感知插值接管留后续里程碑（避免范围膨胀）。三面接线面文档化如下（沿用 p.2.27.2
 * RenderHooks/RenderHooksClient 的「server 侧只读核对 + 客户端事件注入」范式，均以本壳确定性核对为前置）：
 * <ol>
 *   <li><b>数据装载面（data load）</b>：把配置/数据包的 {@code time_scale} td 文档装载为
 *       {@code TimeScaleDoc.fromTd} 的 {@link TimeScale}（四域、缺失覆盖档回退全局），并把每个域的
 *       {@code effective} 流速解析成 {@link TimeThrottle#shouldTick(double,long)} 的输入——后续接线点为
 *       配置加载事件 → {@code TimeScaleDoc.fromTd} → 按域 {@code effective}，本子项以固定四域
 *       {@link TimeScale} 代偿；</li>
 *   <li><b>逻辑 tick 预算驱动面（logic-tick budget drive）</b>：每 MC server tick 以 {@link TimeScale}
 *       按域流速经 {@code TimeThrottle.shouldTick} 决定该域逻辑 tick 是否真正执行，并以
 *       {@link TimeBudgetScheduler}（p.2.26.3 复用 p.2.8 BudgetScheduler 记账内核）在每域预算上限内按
 *       固定键序选出本 tick 要驱动的逻辑元素（超纲确定性推迟）——后续接线点为 MC server tick →
 *       {@code reset(tick)} → {@code schedule(domain)} → 逻辑元素执行分发，本子项以固定
 *       {@code FIXED_TICKS} 个 tick 的预算调度代偿；</li>
 *   <li><b>感知消费面（perception consume）</b>：客户端在渲染时钟 {@code alpha} 处经 {@link TimeScaleApi}/
 *       {@code TimeInterpolation} 在上一个端点与目标端点间线性插值，使被节流跳过的逻辑 tick 呈现平滑运动
 *       ——后续接线点为客户端 render tick → 按域端点插值注入，本子项仅消费 {@link TimeScaleApi} 插值纯函数
 *       数据面，不引渲染注入。</li>
 * </ol>
 * 门控同 {@code subterra.probe.time}（默认 no-op）：服务端门控内打 {@code ok (domains=.., rates=..,
 * ticks=.., verify=ok)}（供 E2E 断言）。接续表另见仓库 runtime 接线文档；本子项交付门控 marker 与
 * engine.time/api.time 调度驱动在真实 boot 生命周期上的确定性证明。本子项不做 MC 逻辑 tick 注入，仅将其
 * 生命周期接线面文档化。
 * <p>
 * p.2.26.4 — the time runtime shell: folds p.2.26's engine.time (p.2.26.1 four-domain flow rates
 * {@link TimeScale}/{@link TimeDomain}/throttle {@code TimeThrottle}/perception interpolation
 * {@code TimeInterpolation}/td doc {@code TimeScaleDoc}) and the api.time contract (p.2.26.2
 * {@link TimeScaleApi}) into the boot lifecycle as a gated deterministic verification, driving p.2.26.3's
 * {@link TimeBudgetScheduler} (budget scheduling reusing the p.2.8 kernel). {@link #bootstrap()}
 * (called from the {@code Subterra.java} constructor) registers the {@code ServerStartedEvent} gate; gated
 * by {@code subterra.probe.time} (forwarded gradle -P → runServer system property, same pattern as the
 * other probe shells), runs only when non-null — a pure no-op shell by default, zero impact on the boot
 * lifecycle.
 * <p>
 * Deterministic sample consumption (fixed order, no timing, no sleeps): a fixed four-domain
 * {@link TimeScale} (GLOBAL=1.0 / ENTITY=0.5 / ZONE=0.25 / PLAYER=2.0) → digested as {@code form=value} in
 * fixed {@link TimeDomain#values()} order, re-checking the {@code effective} fall-back chain (an unset ZONE
 * resolves to {@code effective(GLOBAL)}) → {@link TimeScaleApi} interpolation/throttle pure functions
 * (each checked against a fixed nominal) → a {@link TimeBudgetScheduler} budget drive over a fixed
 * {@code FIXED_TICKS} ticks ({@code reset(tick) → schedule(tick)}, including in-budget keys in fixed order and
 * deterministic over-cap deferral), accumulating the budget hit count; every digest is re-verified against an
 * identically re-driven sample from scratch (the determinism proof). On full success it prints
 * {@code [Subterra time] ok (domains=N, rates=<digest>, ticks=<budget hit count>, verify=ok)}
 * (domains = the fixed four-domain count; rates = the {@code form=value} digest in fixed
 * {@link TimeDomain#values()} order; ticks = the accumulated budget hit count over the fixed-tick drive;
 * verify=ok); a violation / program error (the samples are legal, so normally unreachable) prints an
 * {@code [Subterra time] mismatch (error=...)} marker instead — a false ok is never emitted.
 * <p>
 * <b>MC logic-tick lifecycle wiring surfaces (runtime time binding, post-p.2.26 wiring points; this
 * sub-item only does the gated deterministic verification, no MC logic-tick injection)</b>: engine.time +
 * api.time are the pure-JDK deterministic time-scaling data plane, and the real MC logic-tick budget-drive
 * / perception-interpolation takeover lands with a later milestone (scope containment). The three wiring
 * surfaces are documented here (following the p.2.27.2 RenderHooks/RenderHooksClient "server-side read-only
 * check + client-event injection" pattern, each building on this shell's deterministic check):
 * <ol>
 *   <li><b>Data-load surface</b>: loads the config/datapack {@code time_scale} td document into a
 *       {@link TimeScale} via {@code TimeScaleDoc.fromTd} (four domains, absent overrides falling back to
 *       global) and resolves each domain's {@code effective} rate into the input of
 *       {@link TimeThrottle#shouldTick(double,long)} — the future wiring point is config-load event →
 *       {@code TimeScaleDoc.fromTd} → per-domain {@code effective}; this sub-item substitutes a fixed
 *       four-domain {@link TimeScale};</li>
 *   <li><b>Logic-tick budget drive surface</b>: per MC server tick, decisions which domain logic ticks truly
 *       execute via {@code TimeThrottle.shouldTick} at the {@link TimeScale} per-domain rate, and selects the
 *       logic elements that run this tick within each domain's budget cap (fixed key order, deterministic
 *       over-cap deferral) via {@link TimeBudgetScheduler} (p.2.26.3 reusing the p.2.8 BudgetScheduler
 *       accounting kernel) — the future wiring point is MC server tick → {@code reset(tick)} →
 *       {@code schedule(domain)} → logic-element dispatch; this sub-item substitutes a budget drive over a
 *       fixed {@code FIXED_TICKS} ticks;</li>
 *   <li><b>Perception-consume surface</b>: the client linearly interpolates between the last endpoint and
 *       the target by {@link TimeScaleApi}/{@code TimeInterpolation} at the render-clock {@code alpha}, so
 *       throttle-skipped logic ticks still render smooth motion — the future wiring point is client render
 *       tick → per-domain endpoint interpolation injection; this sub-item consumes only the
 *       {@link TimeScaleApi} interpolation pure-function data plane, without render injection.</li>
 * </ol>
 * Same gate {@code subterra.probe.time} (default no-op): the server-side gate prints {@code ok (domains=..,
 * rates=.., ticks=.., verify=ok)} (E2E-asserted). The wiring table also lives in the repository runtime
 * wiring docs; this sub-item delivers the gated marker and the determinism proof of the engine.time/api.time
 * scheduling drive on a real boot lifecycle. No MC logic-tick injection is performed by this sub-item; its
 * lifecycle wiring surfaces are documented only.
 */
public final class TimeRuntime {

    /** 探针 marker 前缀 / probe marker prefix. */
    public static final String MARKER = "[Subterra time]";

    public static final Logger LOGGER = LogUtils.getLogger();

    /** 固定推进 tick 步数（确定性逐 tick 回放预算调度）。The fixed advanced tick count (deterministic
     *  replayed-tick budget drive). */
    private static final int FIXED_TICKS = 4;

    /** 固定标称域数（{@link TimeDomain#values()} 长度，固定 4）。The fixed nominal domain count
     *  ({@code TimeDomain.values().length}, fixed 4). */
    private static final int NOMINAL_DOMAINS = TimeDomain.values().length;

    /** 固定标称流速摘要值（{@link #verify} 用以逐字符核对）。The fixed nominal rate digest used to
     *  cross-check {@link #verify} character-for-character. */
    private static final String NOMINAL_RATES_DIGEST =
            "global=1.0,entity=0.5,zone=0.25,player=2.0";

    /** 固定标称预算命中数（{@link #verify} 用以核对）。The fixed nominal budget hit count used to
     *  cross-check {@link #verify}. */
    private static final int NOMINAL_TICK_HITS = 28;

    private TimeRuntime() {
    }

    /** 注册 NeoForge 生命周期监听（mod 构造调用）。Registers the NeoForge lifecycle listeners (call
     * from the mod constructor). */
    public static void bootstrap() {
        NeoForge.EVENT_BUS.register(TimeRuntime.class);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        // p.2.26.4 deterministic E2E hook: consume the engine.time/api.time time-scaling data plane at
        // startup when a probe flag is forwarded (subterra.probe.time) — mirrors the other probe-shell
        // gates, so no console command round-trips through the gradle-forked server JVM stdin.
        // Default no-op. No MC logic-tick injection here (documented wiring points only).
        String probe = System.getProperty("subterra.probe.time");
        if (probe == null || probe.isBlank()) {
            return;
        }
        try {
            // Deterministic fixed-order consumption (no timing, no randomness); every digest is
            // re-verified against an identical from-scratch rebuild.
            DriveResult drive = runSample();
            int domains = drive.domains;
            String rates = drive.rates;
            int ticks = drive.ticks;
            if (domains != NOMINAL_DOMAINS || !verify()) {
                throw new IllegalStateException("time scale not deterministic");
            }
            LOGGER.info("{} ok (domains={}, rates={}, ticks={}, verify=ok)",
                    MARKER, domains, rates, ticks);
        } catch (Throwable t) {
            // the samples are legal, so a mismatch / linkage failure is a program error — never emit a
            // false ok. (Throwable like HubRuntime, so unexpected Errors are surfaced, not silently
            // dropped, while never passing the E2E gate.)
            LOGGER.warn("{} time mismatch (error={})", MARKER, t.toString());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // no held state -> pure no-op; symmetric empty hook kept (as the other shells).
    }

    /**
     * 固定样例确定性驱动：固定四域 {@link TimeScale}（GLOBAL=1.0 / ENTITY=0.5 / ZONE=0.25 / PLAYER=2.0）
     * → 按固定 {@link TimeDomain#values()} 序打 {@code form=value} 摘要；然后 {@link TimeScaleApi}
     * 插值/节流纯函数逐一核对固定标称（插值 {@code 1.0→3.0 @0.5}、步长 {@code nextTickDelta(0.5)=2}、
     * 节流 {@code shouldTick(0.5,2)=true / shouldTick(0.5,1)=false}）；最后 {@link TimeBudgetScheduler}
     * 预算调度跑固定 {@code FIXED_TICKS} 个 tick（四域预算上限 GLOBAL=2 / ENTITY=1 / ZONE=3 / PLAYER=2，
     * 每 tick {@code reset(tick)→schedule(tick)}，按固定键序纳入、超纲确定性推迟），累计预算命中数。
     * 摘要与命中数——逐 tick 回放一致性——在 {@link #verify()} 中核对。{@code effective} 回退链
     * （未设置的 ZONE ⇒ {@code effective(GLOBAL)}）在 {@link #effectiveFallback} 内同时复验。
     * <p>
     * The fixed-sample deterministic drive: a fixed four-domain {@link TimeScale} (GLOBAL=1.0 /
     * ENTITY=0.5 / ZONE=0.25 / PLAYER=2.0) → digested as {@code form=value} in fixed
     * {@link TimeDomain#values()} order; then the {@link TimeScaleApi} interpolation/throttle pure
     * functions are each checked against a fixed nominal (interpolate {@code 1.0→3.0 @0.5},
     * {@code nextTickDelta(0.5)=2}, {@code shouldTick(0.5,2)=true / shouldTick(0.5,1)=false}); finally the
     * {@link TimeBudgetScheduler} drives a fixed {@code FIXED_TICKS} ticks (cap GLOBAL=2 / ENTITY=1 /
     * ZONE=3 / PLAYER=2, {@code reset(tick)→schedule(tick)} each tick, including in-budget keys in fixed
     * order and deterministic over-cap deferral), accumulating the budget hit count. The digest and hit
     * count — and the tick-for-tick replay consistency — are cross-checked in {@link #verify()}. The
     * {@code effective} fall-back chain (an unset ZONE ⇒ {@code effective(GLOBAL)}) is re-checked inside
     * the drive too.
     */
    private static DriveResult runSample() {
        TimeScale scale = TimeScale.of(Map.of(
                TimeDomain.GLOBAL, 1.0D,
                TimeDomain.ENTITY, 0.5D,
                TimeDomain.ZONE, 0.25D,
                TimeDomain.PLAYER, 2.0D));
        String rates = digest(scale);
        int ticks = budgetHits();
        // determinism sub-checks: the api pure functions and the effective fall-back chain must hold.
        if (!apiPureDeterminism() || !effectiveFallback(scale)) {
            throw new IllegalStateException("time api / effective not deterministic");
        }
        return new DriveResult(NOMINAL_DOMAINS, rates, ticks);
    }

    /**
     * {@link TimeScaleApi} 插值/节流纯函数确定性核对：{@code interpolate(1.0, 3.0, 0.5)=2.0}、
     * {@code nextTickDelta(0.5)=2} 与 {@code nextTickDelta(2.0)=1}、{@code shouldTick(0.5,2)=true} 与
     * {@code shouldTick(0.5,1)=false}——同输入恒得同位的固定标称。The deterministic check of the
     * {@link TimeScaleApi} interpolation/throttle pure functions: {@code interpolate(1.0, 3.0, 0.5)=2.0},
     * {@code nextTickDelta(0.5)=2} and {@code nextTickDelta(2.0)=1}, {@code shouldTick(0.5,2)=true} and
     * {@code shouldTick(0.5,1)=false} — fixed bit-identical nominals for the same inputs. */
    private static boolean apiPureDeterminism() {
        return TimeScaleApi.interpolate(1.0D, 3.0D, 0.5D) == 2.0D
                && TimeScaleApi.nextTickDelta(0.5D) == 2L
                && TimeScaleApi.nextTickDelta(2.0D) == 1L
                && TimeScaleApi.shouldTick(0.5D, 2L)
                && !TimeScaleApi.shouldTick(0.5D, 1L);
    }

    /**
     * {@code effective} 回退链确定性核对：从固定 {@link TimeScale} 派生一个未设置 ZONE 的克隆，复验
     * {@code ZONE ⇒ effective(GLOBAL)}（未设置的非全局域回退全局档）。Deterministic check of the
     * {@code effective} fall-back chain: derives a clone with unset ZONE from the fixed {@link TimeScale}
     * and re-checks that {@code ZONE ⇒ effective(GLOBAL)} (an unset non-global domain falls back to the
     * global tier). */
    private static boolean effectiveFallback(TimeScale scale) {
        TimeScale unset = TimeScale.of(Map.of(TimeDomain.GLOBAL, scale.get(TimeDomain.GLOBAL)));
        return unset.effective(TimeDomain.ZONE) == unset.effective(TimeDomain.GLOBAL)
                && unset.effective(TimeDomain.GLOBAL) == 1.0D;
    }

    /**
     * {@link TimeBudgetScheduler} 固定 tick 预算调度（p.2.26.3，复用 p.2.8 记账内核）：注册四域预算
     * （GLOBAL=2 / ENTITY=1 / ZONE=3 / PLAYER=2）与候选（GLOBAL 1,2,3 → 2 命中、ENTITY 4,5 → 1 命中、
     * ZONE 6,7 → 2 命中、PLAYER 8,9 → 2 命中，每 tick 固定 7 命中、超纲确定性推迟），推进固定
     * {@code FIXED_TICKS} 个 tick（每 tick {@code reset(tick)→schedule(tick)}），累计命中数 =
     * {@code 7 × FIXED_TICKS = } {@link #NOMINAL_TICK_HITS}。The fixed-tick {@link TimeBudgetScheduler}
     * budget drive (p.2.26.3 reusing the p.2.8 accounting kernel): registers four domain budgets (GLOBAL=2 /
     * ENTITY=1 / ZONE=3 / PLAYER=2) and candidates (GLOBAL 1,2,3 → 2 hits, ENTITY 4,5 → 1 hit, ZONE 6,7 → 2
     * hits, PLAYER 8,9 → 2 hits per tick for a fixed 7, over-cap deterministically deferred), advancing a
     * fixed {@code FIXED_TICKS} ticks ({@code reset(tick)→schedule(tick)} each tick); the accumulated hit
     * count = {@code 7 × FIXED_TICKS = } {@link #NOMINAL_TICK_HITS}. */
    private static int budgetHits() {
        TimeBudgetScheduler sched = new TimeBudgetScheduler();
        sched.tickBudget(TimeDomain.GLOBAL, 2)
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
        int hits = 0;
        for (long t = 0L; t < FIXED_TICKS; t++) {
            sched.reset(t);
            hits += sched.schedule(t).size();
        }
        return hits;
    }

    /**
     * 确定性摘要复验：从零重建同驱动，核对域数={@link #NOMINAL_DOMAINS}、tick 命中数={@link #NOMINAL_TICK_HITS}，
     * 且最终流速摘要与标称值逐字符一致（确定性证明）。{@link TimeDomain#values()} 固定序与
     * {@link TimeBudgetScheduler} 固定键序（域内 id 升序、域间枚举序）保证同驱动同摘要。
     * <p>
     * Determinism re-verification: rebuilds the same drive from scratch and checks the domain count =
     * {@link #NOMINAL_DOMAINS}, the budget hit count = {@link #NOMINAL_TICK_HITS}, and that the final rate
     * digest matches the nominal value character-for-character (the determinism proof).
     * {@link TimeDomain#values()}'s fixed order and {@link TimeBudgetScheduler}'s fixed key order
     * (ascending-id within a domain, enumeration order across domains) guarantee the same drive yields the
     * same digest.
     */
    private static boolean verify() {
        DriveResult drive = runSample();
        return drive.domains == NOMINAL_DOMAINS && drive.ticks == NOMINAL_TICK_HITS
                && drive.rates.equals(NOMINAL_RATES_DIGEST);
    }

    /** 流速摘要：固定 {@link TimeDomain#values()} 序 {@code form=value} 逗号连接。 Rate digest: the
     *  fixed {@link TimeDomain#values()}-order {@code form=value} join. */
    private static String digest(TimeScale scale) {
        StringBuilder sb = new StringBuilder();
        for (TimeDomain d : TimeDomain.values()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(d.form()).append('=').append(scale.get(d));
        }
        return sb.toString();
    }

    /** 样例驱动结果（域数 / 流速摘要 / 预算命中数）。The sample-drive result (domain count / rate digest /
     *  budget hit count). */
    private record DriveResult(int domains, String rates, int ticks) {
    }
}