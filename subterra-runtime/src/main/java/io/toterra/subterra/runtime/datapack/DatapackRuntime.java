package io.toterra.subterra.runtime.datapack;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * MC-shell datapack runtime (p.2.2 block 2): wires the engine.datapack loader
 * into the dedicated-server lifecycle. On {@code ServerStartedEvent} the
 * configured datapacks directory is scanned and every td pack is loaded (pure
 * td direct loading); registration of tags / lang / recipes / tie functions
 * happens in the registrar. The source directory resolves in order:
 * {@code -Dsubterra.datapacks} property → {@code SUBTERRA_DATAPACKS} env →
 * {@code datapacks} under the server working directory (the dev run dir).
 */
public final class DatapackRuntime {

    public static final Logger LOGGER = LogUtils.getLogger();

    private static DatapackRegistrar registrar;

    private DatapackRuntime() {
    }

    /** Registers the NeoForge lifecycle listeners (call from the mod constructor). */
    public static void bootstrap() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(DatapackRuntime.class);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(DatapackExportCommand::onRegisterCommands);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        DatapackRegistrar reg = DatapackRegistrar.forServer(event.getServer());
        registrar = reg;
        try {
            reg.loadPacks(resolveDatapacksDir());
            reg.registerContent();
            // p.2.2.7 deterministic E2E hook: run the same export + rehydrate-identity
            // core at startup when a target path is forwarded (marks every pack).
            String exportPath = System.getProperty("subterra.probe.export");
            if (exportPath != null && !exportPath.isBlank()) {
                DatapackExportCommand.exportFrom(reg, exportPath);
            }
        } catch (Throwable t) {
            LOGGER.error("[Subterra datapack] load failed: {}", t.toString());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (registrar != null) {
            registrar.close();
            registrar = null;
        }
    }

    /** Resolves the datapacks source directory (prop → env → cwd convention). */
    public static Path resolveDatapacksDir() {
        String prop = System.getProperty("subterra.datapacks");
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop).toAbsolutePath().normalize();
        }
        String env = System.getenv("SUBTERRA_DATAPACKS");
        if (env != null && !env.isBlank()) {
            return Path.of(env).toAbsolutePath().normalize();
        }
        return Path.of("datapacks");
    }

    /** The active registrar for the current server (command entry point). */
    public static DatapackRegistrar activeRegistrar() {
        return registrar;
    }
}