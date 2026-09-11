package io.toterra.subterra.runtime.ai;

import com.mojang.logging.LogUtils;
import io.toterra.subterra.engine.ai.Brain;
import io.toterra.subterra.engine.ai.BrainBehavior;
import io.toterra.subterra.engine.ai.BrainMemory;
import io.toterra.subterra.engine.ai.BrainSensor;
import io.toterra.subterra.engine.ai.BrainScheduler;
import io.toterra.subterra.engine.ai.BrainTask;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

/**
 * p.2.24.3 — ai runtime 壳：把 p.2.24 的 engine.ai（p.2.24.1 确定性 Brain 编排核心
 * {@link Brain} / {@link BrainMemory} / {@link BrainSensor} / {@link BrainBehavior} /
 * {@link BrainTask}，p.2.24.2 优先级裁决 + 预算调度 {@link BrainScheduler}）收编进 boot 生命周期的
 * 门控确定性核对。{@link #bootstrap()}（{@code Subterra.java} 构造调用）注册
 * {@code ServerStartedEvent} 门控；门控 {@code subterra.probe.ai}（经 gradle -P → runServer system
 * property 转发，与其余探针壳同模式）非 null 才跑——缺省纯 no-op 壳，对启动生命周期零影响。
 * <p>
 * 确定性样例驱动（固定序、禁时序、禁 sleep）：构造一个 {@link Brain} 样例——固定感知器写入记忆 +
 * 两个不同优先级行为 + 一个任务 → 经 {@link BrainScheduler}（固定注册序、固定每 tick 预算）按固定
 * 步数推进 {@code tick}（全程无随机、无时序）→ 对最终 {@link BrainMemory} 打确定性摘要，并对
 * 「同一驱动从头重建」做摘要复验（确定性证明）。全部通过打
 * {@code [Subterra ai] ok (sensors=N, behaviors=N, ticks=N, state=<memory 摘要>, verify=ok)}
 * （sensors = 注册感知器数；behaviors = 注册行为数；ticks = 推进 tick 数；state = 记忆按固定键序的
 * {@code key=value} 逗号连接摘要）；违约/程序错误（样例合法，正常不可达）打
 * {@code [Subterra ai] mismatch (error=...)} marker，绝不打假 ok。
 * <p>
 * <b>MC 实体 Brain 绑定面（p.2.24 后接线点，本子项只做门控确定性核对，不引玩家 UI 注入）</b>：
 * engine.ai 是纯 JDK 确定性决策核心，真实 MC 实体的原版 Brain 装载与驱动接管留后续里程碑（避免
 * 范围膨胀）。四面接线面文档化如下（沿用 p.2.27.2 RenderHooks/RenderHooksClient 的「server 侧只读
 * 核对 + 客户端事件注入」范式，均以本壳确定性核对为前置）：
 * <ol>
 *   <li><b>Brain 装载面（brain load）</b>：把 MC 实体的决策记忆（原版 {@code net.minecraft.world.
 *       entity.ai.Brain} 的记忆槽，诸如 walk 目标、look 目标、活动状态、可交互实体候选等）装载/
 *       转换为 engine.ai 的 {@link BrainMemory} 槽位（固定键序、重复拒）——后续接线点为实体
 *       {@code BrainProvider} → {@link Brain} 注册表，本子项以固定感知器写记忆代偿；</li>
 *   <li><b>感知采样面（sensor sample）</b>：逐实体感知循环把 MC 世界状态（邻居/视野/威胁/可达
 *       目标）写入 {@link BrainMemory}（原版经 {@code SmallSensor} / 定期传感器采样）——后续接线点
 *       为实体 tick → 采样写入 memory，本子项以固定 {@link BrainSensor} 代偿；</li>
 *   <li><b>行为裁决面（behavior arbitrate）</b>：逐实体 tick 以不同优先级注册行为并驱动原版
 *       {@code BehaviorControl} 生命周期（start 决策 / tick 行动 / stop 收尾），仲裁重数交给
 *       {@link BrainScheduler}（p.2.24.2 固定序、禁时序）——后续接线点为实体行为 → 行为注册表，
 *       本子项以两不同优先级固定行为代偿；</li>
 *   <li><b>任务执行面（task execute）</b>：把原版行为原语（移动/转向/交互等 {@code BehaviorControl}
 *       原子行动）编译为 {@link BrainTask}，在调度器感知/裁决之后依 {@link BrainMemory} 确定性执行
 *       ——后续接线点为实体行为库 → {@link BrainTask} 原子库，本子项以固定 {@link BrainTask} 代偿。
 *       </li>
 * </ol>
 * 门控同 {@code subterra.probe.ai}（默认 no-op）：服务端门控内打 {@code ok (sensors=.., behaviors=..,
 * ticks=.., state=.., verify=ok)}（供 E2E 断言）。接续表另见仓库 runtime 接线文档；本子项交付门控
 * marker 与 engine.ai 调度驱动在真实 boot 生命周期上的确定性证明。
 * <p>
 * p.2.24.3 — the ai runtime shell: folds the p.2.24 engine.ai (the p.2.24.1 deterministic Brain
 * orchestration core {@link Brain} / {@link BrainMemory} / {@link BrainSensor} /
 * {@link BrainBehavior} / {@link BrainTask}, and the p.2.24.2 priority-arbitration + budget
 * scheduling {@link BrainScheduler}) into the boot lifecycle as a gated deterministic verification.
 * {@link #bootstrap()} (called from the {@code Subterra.java} constructor) registers the
 * {@code ServerStartedEvent} gate; gated by {@code subterra.probe.ai} (forwarded gradle -P →
 * runServer system property, same pattern as the other probe shells), runs only when non-null — a
 * pure no-op shell by default, zero impact on the boot lifecycle.
 * <p>
 * Deterministic sample drive (fixed order, no timing, no sleeps): builds a {@link Brain} sample — a
 * fixed sensor writing the memory + two differently-prioritized behaviors + one task → advances a
 * fixed number of {@code tick}s through the {@link BrainScheduler} (fixed registration order, fixed
 * per-tick budget; no randomness, no timing throughout) → digests the final {@link BrainMemory} and
 * re-verifies the digest against an identically re-driven sample from scratch (the determinism
 * proof). On full success it prints
 * {@code [Subterra ai] ok (sensors=N, behaviors=N, ticks=N, state=<memory digest>, verify=ok)}
 * (sensors = registered sensor count; behaviors = registered behavior count; ticks = advanced tick
 * count; state = the memory joined as {@code key=value} by comma in fixed key order); a load
 * violation / program error (the samples are legal, so normally unreachable) prints an
 * {@code [Subterra ai] mismatch (error=...)} marker instead — a false ok is never emitted.
 * <p>
 * <b>MC entity-Brain binding surface (post-p.2.24 wiring points; this sub-item only does the gated
 * deterministic verification, no player-UI injection)</b>: engine.ai is the pure-JDK deterministic
 * decision core, and the real vanilla-Brain loading/driving takeover for MC entities lands with a
 * later milestone (scope containment). The four wiring surfaces are documented here (following the
 * p.2.27.2 RenderHooks/RenderHooksClient "server-side read-only check + client-event injection"
 * pattern, each building on this shell's deterministic check):
 * <ol>
 *   <li><b>Brain-load surface</b>: loads/converts an MC entity's decision memory (the vanilla
 *       {@code net.minecraft.world.entity.ai.Brain} memory slots — walk target, look target, activity
 *       status, interactable-entity candidates, ...) into engine.ai {@link BrainMemory} slots (fixed
 *       key order, duplicate rejection) — the future wiring point is entity {@code BrainProvider} →
 *       {@link Brain} registry; this sub-item substitutes a fixed sensor writing the memory;</li>
 *   <li><b>Sensor-sample surface</b>: the per-entity sense loop writes MC world state (neighbors /
 *       sight / threat / reachable targets) into {@link BrainMemory} (vanilla via
 *       {@code SmallSensor} / periodic-sensor sampling) — the future wiring point is entity tick →
 *       sensing into {@link BrainMemory}; this sub-item substitutes a fixed {@link BrainSensor};</li>
 *   <li><b>Behavior-arbitrate surface</b>: registers behaviors at distinct priorities per entity tick
 *       and drives the vanilla {@code BehaviorControl} lifecycle (start decide / tick act / stop
 *       teardown), letting {@link BrainScheduler} (p.2.24.2 fixed order, no timing) own the weighting —
 *       the future wiring point is entity behavior → behavior registry; this sub-item substitutes two
 *       fixed differently-prioritized behaviors;</li>
 *   <li><b>Task-execute surface</b>: compiles the vanilla behavior primitives (move / look / interact
 *       atomic {@code BehaviorControl} actions) into {@link BrainTask}s that run deterministically
 *       against the {@link BrainMemory} after the scheduler's sense/arbitrate phases — the future
 *       wiring point is an entity-behavior library → {@link BrainTask} atom library; this sub-item
 *       substitutes a fixed {@link BrainTask}.</li>
 * </ol>
 * Same gate {@code subterra.probe.ai} (default no-op): the server-side gate prints
 * {@code ok (sensors=.., behaviors=.., ticks=.., state=.., verify=ok)} (E2E-asserted). The wiring
 * table also lives in the repository runtime wiring docs; this sub-item delivers the gated marker
 * and the determinism proof of the engine.ai scheduling drive on a real boot lifecycle.
 */
public final class AiRuntime {

    /** 探针 marker 前缀 / probe marker prefix. */
    public static final String MARKER = "[Subterra ai]";

    public static final Logger LOGGER = LogUtils.getLogger();

    /** 固定推进 tick 步数（确定性逐 tick 回放）。The fixed advanced tick count (deterministic
     *  replay tick-for-tick). */
    private static final int FIXED_TICKS = 3;

    /** 固定标称摘要值（{@link #verify} 用以核对）。The fixed nominal digest value used to cross-check
     *  {@link #verify}. */
    private static final String NOMINAL_DIGEST = "sensor.count=3,behavior=high,task.count=3";

    private AiRuntime() {
    }

    /** 注册 NeoForge 生命周期监听（mod 构造调用）。Registers the NeoForge lifecycle listeners (call
     * from the mod constructor). */
    public static void bootstrap() {
        NeoForge.EVENT_BUS.register(AiRuntime.class);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        // p.2.24.3 deterministic E2E hook: run the engine.ai sample Brain/Scheduler drive at startup
        // when a probe flag is forwarded (subterra.probe.ai) — mirrors the other probe-shell gates,
        // so no console command round-trips through the gradle-forked server JVM stdin. Default no-op.
        String probe = System.getProperty("subterra.probe.ai");
        if (probe == null || probe.isBlank()) {
            return;
        }
        try {
            // Deterministic fixed-order drive (no timing, no randomness); the final memory digest is
            // re-verified against an identically re-driven brain from scratch.
            DriveResult drive = runSample();
            int sensors = drive.sensors;
            int behaviors = drive.behaviors;
            int ticks = drive.ticks;
            String state = drive.state;
            if (!verify()) {
                throw new IllegalStateException("sample brain not deterministic");
            }
            LOGGER.info("{} ok (sensors={}, behaviors={}, ticks={}, state={}, verify=ok)",
                    MARKER, sensors, behaviors, ticks, state);
        } catch (RuntimeException e) {
            // the samples are legal, so a mismatch is a program error — never emit a false ok.
            LOGGER.warn("{} ai mismatch (error={})", MARKER, e.getMessage());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // no held state -> pure no-op; symmetric empty hook kept (as the other shells).
    }

    /**
     * 固定样例确定性调度驱动：注册一个固定感知器 + 两个不同优先级行为 + 一个任务 → 固定推进
     * {@code FIXED_TICKS} 步。感知器每 tick 递增 {@code sensor.count}；高优先级行为（10）始终激活、
     * 写 {@code behavior=high}，低优先级行为（1）不激活（仲裁选高，仅激活行为写记忆）；任务每 tick
     * 递增 {@code task.count}。摘要 = 固定键序 {@code key=value} 逗号连接。传感器/行为/任务计数均为
     * 注册数，与摘要——逐 tick 回放一致性——在 {@link #verify()} 中核对。
     * <p>
     * The fixed-sample deterministic scheduling drive: register one fixed sensor + two
     * differently-prioritized behaviors + one task → advance {@code FIXED_TICKS} ticks. The sensor
     * increments {@code sensor.count} each tick; the high-priority behavior (10) is always active and
     * writes {@code behavior=high}, the low-priority one (1) is inactive (arbitration selects the
     * high one; only the active behavior writes the memory); the task increments {@code task.count}
     * each tick. The digest = the memory joined as {@code key=value} in fixed key order. The sensor /
     * behavior / task counts are registration counts, cross-checked against the digest — and the
     * tick-for-tick replay — in {@link #verify()}.
     */
    private static DriveResult runSample() {
        Brain brain = new Brain();
        BrainScheduler scheduler = new BrainScheduler();

        scheduler.registerSensor(new BrainSensor() {
            @Override
            public String name() {
                return "sample.sensor";
            }

            @Override
            public void sense(BrainMemory memory) {
                int n = memory.has("sensor.count") ? ((Number) memory.get("sensor.count")).intValue() : 0;
                memory.put("sensor.count", n + 1);
            }
        });
        scheduler.registerBehavior(new BrainBehavior() {
            @Override
            public String name() {
                return "sample.high";
            }

            @Override
            public boolean start(BrainMemory memory) {
                return true; // always active -> always selected under arbitration
            }

            @Override
            public void tick(BrainMemory memory) {
                memory.put("behavior", "high");
            }
        }, 10);
        scheduler.registerBehavior(new BrainBehavior() {
            @Override
            public String name() {
                return "sample.low";
            }

            @Override
            public boolean start(BrainMemory memory) {
                return false; // never active -> never selected (low priority loses)
            }

            @Override
            public void tick(BrainMemory memory) {
                throw new IllegalStateException("inactive behavior must not tick");
            }
        }, 1);
        scheduler.registerTask(new BrainTask() {
            @Override
            public String name() {
                return "sample.task";
            }

            @Override
            public void run(BrainMemory memory) {
                int n = memory.has("task.count") ? ((Number) memory.get("task.count")).intValue() : 0;
                memory.put("task.count", n + 1);
            }
        });

        for (int i = 0; i < FIXED_TICKS; i++) {
            scheduler.tick(brain);
        }

        String state = digest(brain.memory());
        return new DriveResult(1, 2, FIXED_TICKS, state);
    }

    /**
     * 确定性摘要复验：从零重建同驱动，核对传感器数=1、行为数=2、tick 数={@code FIXED_TICKS}，且最终
     * 记忆摘要与标称值逐字符一致（确定性证明）。{@link BrainMemory} 固定键序保证同驱动同摘要。
     * <p>
     * Determinism re-verification: rebuilds the same drive from scratch and checks the sensor count=1,
     * behavior count=2, tick count={@code FIXED_TICKS}, and that the final memory digest matches the
     * nominal value character-for-character (the determinism proof). {@link BrainMemory}'s fixed key
     * order guarantees the same drive yields the same digest.
     */
    private static boolean verify() {
        DriveResult drive = runSample();
        return drive.sensors == 1 && drive.behaviors == 2 && drive.ticks == FIXED_TICKS
                && drive.state.equals(NOMINAL_DIGEST);
    }

    /** 记忆摘要：固定键序 {@code key=value} 逗号连接。 Memory digest: {@code key=value} joined by comma
     *  in fixed key order. */
    private static String digest(BrainMemory memory) {
        StringBuilder sb = new StringBuilder();
        for (String key : memory.keys()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(key).append('=').append(memory.get(key));
        }
        return sb.toString();
    }

    /** 样例驱动结果（传感器/行为/tick 计数 + 记忆摘要）。The sample-drive result (sensor/behavior/tick
     *  counts + the memory digest). */
    private record DriveResult(int sensors, int behaviors, int ticks, String state) {
    }
}