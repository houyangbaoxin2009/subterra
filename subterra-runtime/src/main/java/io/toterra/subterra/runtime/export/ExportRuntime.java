package io.toterra.subterra.runtime.export;

import com.mojang.logging.LogUtils;
import io.toterra.subterra.runtime.datapack.DatapackRuntime;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

/**
 * p.2.19.5 — export runtime 壳：把 p.2.18.5 ExportHubRuntime（{@code subterra.probe.exportHub} 门控）
 * 与 p.2.2.7 DatapackRuntime 的 export 启动钩子（{@code subterra.probe.export} 门控）收编进同一壳。
 * {@code bootstrap()} 单点注册统一命令（{@link ExportCommandCore#onRegisterCommands}，唯一一处
 * RegisterCommandsEvent 注册）并挂两个 ServerStarted 确定性门控：
 * <ul>
 *   <li>{@code subterra.probe.export} → datapack 全量导出核心
 *       {@link ExportCommandCore#exportFrom}（markers {@code export cmd ...} 逐字符不变）；</li>
 *   <li>{@code subterra.probe.exportHub} → 七 form 全跑核心 {@link ExportCommandCore#exportAllForms}
 *       （markers {@code export hub ...} 逐字符不变）。</li>
 * </ul>
 * 属性值即目标目录（与 p.2.2.7 / p.2.18.5 同构；E2E 传 build/tmp 下 staging 绝对路径）。默认纯
 * no-op 壳——门控缺失时对既有 export/rule marker 与启动生命周期零影响。注意：export 门控依赖
 * DatapackRuntime 的活跃 registrar（ServerStarted 装载），因此 Subterra 构造里
 * DatapackRuntime.bootstrap() 必须先于本壳 bootstrap（同 LOWEST 优先级按注册序触发）。
 *
 * <p>p.2.19.5 — the export runtime shell: folds the p.2.18.5 ExportHubRuntime (the
 * {@code subterra.probe.exportHub} gate) and the p.2.2.7 DatapackRuntime export startup hook (the
 * {@code subterra.probe.export} gate) into one shell. {@code bootstrap()} registers the unified
 * command from a single point ({@link ExportCommandCore#onRegisterCommands}, the one
 * RegisterCommandsEvent registration) and wires two ServerStarted deterministic gates:
 * <ul>
 *   <li>{@code subterra.probe.export} → the datapack all-packs export core
 *       {@link ExportCommandCore#exportFrom} (markers {@code export cmd ...} character-for-character
 *       unchanged);</li>
 *   <li>{@code subterra.probe.exportHub} → the all-seven-forms hub core
 *       {@link ExportCommandCore#exportAllForms} (markers {@code export hub ...} unchanged).</li>
 * </ul>
 * The property value is the target directory (same shape as p.2.2.7 / p.2.18.5; the E2E passes an
 * absolute staging path under build/tmp). A pure no-op shell by default — absent gates leave the
 * existing export/rule markers and the boot lifecycle untouched. Note: the export gate needs the
 * active registrar of DatapackRuntime (loaded at ServerStarted), so in the Subterra constructor
 * DatapackRuntime.bootstrap() must run before this shell's bootstrap (same LOWEST priority fires in
 * registration order).
 */
public final class ExportRuntime {

    public static final Logger LOGGER = LogUtils.getLogger();

    private ExportRuntime() {
    }

    /** Registers the NeoForge lifecycle listeners (call from the mod constructor). */
    public static void bootstrap() {
        NeoForge.EVENT_BUS.register(ExportRuntime.class);
        NeoForge.EVENT_BUS.addListener(ExportCommandCore::onRegisterCommands);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        // p.2.2.7 deterministic E2E hook (folded in from DatapackRuntime): run the datapack
        // all-packs export + rehydrate-identity core at startup when a target path is forwarded
        // (subterra.probe.export) — same core and markers the console command emits, no stdin
        // round-trip through the gradle-forked server JVM.
        String exportPath = System.getProperty("subterra.probe.export");
        if (exportPath != null && !exportPath.isBlank()) {
            try {
                ExportCommandCore.exportFrom(DatapackRuntime.activeRegistrar(), exportPath);
            } catch (Throwable t) {
                LOGGER.error("export hook failed: {}", t.toString());
            }
        }
        // p.2.18.5 deterministic E2E hook (folded in from ExportHubRuntime): run the all-seven-forms
        // hub core at startup when a target directory is forwarded (subterra.probe.exportHub) —
        // mirrors the datapack export hook.
        String hubPath = System.getProperty("subterra.probe.exportHub");
        if (hubPath != null && !hubPath.isBlank()) {
            try {
                for (String m : ExportCommandCore.exportAllForms(hubPath)) {
                    LOGGER.info("{}", m);
                }
            } catch (Throwable t) {
                LOGGER.error("export hub hook failed: {}", t.toString());
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // no held state -> pure no-op; symmetric empty hook kept (as RulesRuntime/DatapackRuntime).
    }
}
