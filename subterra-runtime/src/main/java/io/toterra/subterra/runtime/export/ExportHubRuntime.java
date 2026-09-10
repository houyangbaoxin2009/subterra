package io.toterra.subterra.runtime.export;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

/**
 * p.2.18.5 — 导出中枢 runtime 壳：注册 {@code /subterra export <form> [<path>]} 命令分支
 * （ExportHubCommand）与确定性 E2E 钩子——与 DatapackRuntime 的 export 钩子同模式：
 * {@code subterra.probe.exportHub} 门控（经 gradle -P → runServer system property 转发，规避
 * forked server JVM 的 stdin 双层中转），非 null 才跑 {@link ExportHubCommand#exportAllForms} 的
 * 七 form 全跑核心并逐行 INFO（禁 sleep、禁时序）。属性值即目标目录（与
 * {@code subterra.probe.export} 同构；E2E 传 build/tmp 下 staging 绝对路径）。默认纯 no-op 壳——
 * 门控缺失时对既有 export/rule marker 与启动生命周期零影响。
 * <p>
 * p.2.18.5 — the export-hub runtime shell: registers the {@code /subterra export <form> [<path>]}
 * command branch (ExportHubCommand) and the deterministic E2E hook — same pattern as the
 * DatapackRuntime export hook: gated by {@code subterra.probe.exportHub} (forwarded gradle -P →
 * runServer system property, avoiding the double stdin hop through the forked server JVM), runs
 * only when non-null — it runs the all-seven-forms core {@link ExportHubCommand#exportAllForms} and
 * logs each marker at INFO (no sleeps, no timing). The property value is the target directory (same
 * shape as {@code subterra.probe.export}; the E2E passes an absolute staging path under
 * build/tmp). A pure no-op shell by default — absent gate leaves the existing export/rule markers
 * and the boot lifecycle untouched.
 */
public final class ExportHubRuntime {

    public static final Logger LOGGER = LogUtils.getLogger();

    private ExportHubRuntime() {
    }

    /** Registers the NeoForge lifecycle listeners (call from the mod constructor). */
    public static void bootstrap() {
        NeoForge.EVENT_BUS.register(ExportHubRuntime.class);
        NeoForge.EVENT_BUS.addListener(ExportHubCommand::onRegisterCommands);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        // p.2.18.5 deterministic E2E hook: run the same all-forms hub core at startup when a target
        // directory is forwarded (subterra.probe.exportHub) — mirrors the DatapackRuntime export
        // hook, so no console command round-trips through the gradle-forked server JVM stdin.
        String pathArg = System.getProperty("subterra.probe.exportHub");
        if (pathArg == null || pathArg.isBlank()) {
            return;
        }
        for (String m : ExportHubCommand.exportAllForms(pathArg)) {
            LOGGER.info("{}", m);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // no held state -> pure no-op; symmetric empty hook kept (as RulesRuntime/DatapackRuntime).
    }
}
