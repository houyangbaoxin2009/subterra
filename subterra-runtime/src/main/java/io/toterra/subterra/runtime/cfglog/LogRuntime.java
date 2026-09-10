package io.toterra.subterra.runtime.cfglog;

import com.mojang.logging.LogUtils;
import io.toterra.subterra.engine.log.LogHub;
import io.toterra.subterra.engine.log.LogRing;
import io.toterra.subterra.engine.log.Logger;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * p.2.19.3 — 日志接线壳（cfglog 迁入的另一翼）：只验证 {@code engine.log} 核心（{@link Logger} /
 * {@link LogHub} / {@link LogRing}）在 MC 壳内可装载，并打一条确定性门控 marker。门控与
 * {@link ConfigRuntime} 共用同一属性 {@code subterra.probe.cfglog}（经 gradle -P → runServer
 * system property 转发），非 null 才跑；缺省纯 no-op 壳。
 * <p>
 * 与既有日志接线的边界（不重复接线）：{@code io.toterra.subterra.SubterraLogging#bootstrap} 已在
 * {@code FMLCommonSetupEvent} 完成 hub 配置（{@link LogHub#configure}，读 {@code config/subterra/log.td}
 * 缺省回落）、SLF4J 桥（{@code Slf4jLogSink}，engine.log 记录 → MC {@code logs/latest.log}）、可选
 * {@code FileLogSink}、{@code ModuleReg} 注册与 {@code CrashDumper} crash callable 嵌入。本壳不复刻
 * 上述任何一步——只做门控下的装载验证 + marker 收编：经 {@link Logger#get} 打一条确定性记录
 * {@code [Subterra cfglog] log ok (ring=N, level=...)}（N = {@link LogRing#capacity}，level =
 * hub 阈值——均来自已装载配置，固定且确定；固定序、禁时序、禁 sleep），记录经 {@link LogHub} 进
 * ring 并被 Slf4jLogSink 桥接进 MC log。
 * <p>
 * crash-export 只做壳占位：{@code CrashDumper} 已由 SubterraLogging 注册为 crash callable，真实
 * crash 导出接线留待 p.2.19 后子项，本壳不触发任何 dump（避免范围膨胀）。
 * <p>
 * p.2.19.3 — the logging wiring shell (the other wing of the cfglog fold-in): only verifies that
 * the {@code engine.log} core ({@link Logger} / {@link LogHub} / {@link LogRing}) loads inside the
 * MC shell, then prints one deterministic gated marker. The gate shares the same property as
 * {@link ConfigRuntime}, {@code subterra.probe.cfglog} (forwarded gradle -P → runServer system
 * property); runs only when non-null; a pure no-op shell by default.
 * <p>
 * Boundary with the existing log wiring (no duplicate wiring): {@code io.toterra.subterra.
 * SubterraLogging#bootstrap} already configures the hub at {@code FMLCommonSetupEvent}
 * ({@link LogHub#configure}, reading {@code config/subterra/log.td} with a defaults fallback), the
 * SLF4J bridge ({@code Slf4jLogSink}, engine.log records → MC {@code logs/latest.log}), the
 * optional {@code FileLogSink}, {@code ModuleReg} registration and the {@code CrashDumper} crash
 * callable. This shell re-implements none of that — it only performs the gated load verification
 * plus a marker fold-in: it emits one deterministic record
 * {@code [Subterra cfglog] log ok (ring=N, level=...)} via {@link Logger#get} (N =
 * {@link LogRing#capacity}, level = the hub threshold — both from the already-loaded config, fixed
 * and deterministic; fixed order, no timing, no sleeps). The record flows through {@link LogHub}
 * into the ring and is bridged into the MC log by Slf4jLogSink.
 * <p>
 * Crash-export stays a shell placeholder: {@code CrashDumper} is already registered as a crash
 * callable by SubterraLogging; the real crash-export wiring lands in a post-p.2.19 sub-item, so
 * this shell never triggers a dump (no scope creep).
 */
public final class LogRuntime {

    /** 探针 marker 前缀 / probe marker prefix. */
    public static final String MARKER = "[Subterra cfglog]";

    public static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    /** engine.log 门控记录器（与 SubterraLogging 共用名）。The engine.log gated logger (same name
     * as SubterraLogging). */
    private static final Logger ENGINE_LOG = Logger.get("subterra");

    private LogRuntime() {
    }

    /** 注册 NeoForge 生命周期监听（mod 构造调用）。Registers the NeoForge lifecycle listeners (call
     * from the mod constructor). */
    public static void bootstrap() {
        NeoForge.EVENT_BUS.register(LogRuntime.class);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        // p.2.19.3 deterministic E2E hook: verify the engine.log core loads inside the MC shell
        // (hub/ring/threshold all live off the engine core) and emit one fixed record when a probe
        // flag is forwarded (subterra.probe.cfglog) — mirrors the other probe-shell gates.
        String probe = System.getProperty("subterra.probe.cfglog");
        if (probe == null || probe.isBlank()) {
            return;
        }
        LogHub hub = LogHub.hub(); // configured by SubterraLogging at commonSetup
        int ring = hub.ring().capacity();
        String level = hub.threshold().name();
        ENGINE_LOG.info(MARKER + " log ok (ring=" + ring + ", level=" + level + ")");
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // no held state -> pure no-op; symmetric empty hook kept (as RulesRuntime/DatapackRuntime).
    }
}
