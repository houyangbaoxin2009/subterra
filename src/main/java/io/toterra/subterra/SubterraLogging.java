package io.toterra.subterra;

import io.toterra.subterra.config.Td;
import io.toterra.subterra.config.TdTable;
import io.toterra.subterra.log.CrashDumper;
import io.toterra.subterra.log.FileLogSink;
import io.toterra.subterra.log.LogConfig;
import io.toterra.subterra.log.LogHub;
import io.toterra.subterra.log.Logger;
import io.toterra.subterra.log.ModuleReg;
import net.neoforged.fml.CrashReportCallables;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Boot-time wiring of the logging module into the Minecraft runtime:
 * <ul>
 *   <li>loads {@code config/subterra/log.td} (falling back to defaults when
 *       absent or malformed) and configures the hub;</li>
 *   <li>bridges records into the game log (SLF4J) and optionally adds the
 *       rolling Subterra file sink;</li>
 *   <li>registers the Subterra module set with versions and dependency edges
 *       into {@link ModuleReg};</li>
 *   <li>registers a crash callable so the diagnostic block (system info +
 *       modules + recent log tail) is embedded into every crash report.</li>
 * </ul>
 */
public final class SubterraLogging {

    /** Module/version registry content, in dependency order. Keep in sync with build.gradle. */
    private static final String[][] MODULES = {
            {"subterra-launch", "p.1.1.1", ""},
            {"subterra-api", "p.1.3.0", ""},
            {"subterra-compat", "p.1.2.0", "subterra-launch"},
            {"subterra-optim", "p.1.4.0", "subterra-api"},
            {"subterra-config", "p.1.5.0", ""},
            {"subterra-log", "p.1.6.0", "subterra-config"},
            {"subterra", "p.1.6.1", "subterra-launch,subterra-api,subterra-compat,subterra-optim,subterra-config,subterra-log"},
    };

    private SubterraLogging() {
    }

    /** Called from {@code FMLCommonSetupEvent}; idempotent-safe by construction. */
    public static void bootstrap() {
        Path gameDir = FMLPaths.GAMEDIR.get();
        LogConfig cfg = loadConfig(gameDir);
        LogHub.configure(cfg);

        Slf4jLogSink.register(); // MC log bridge, always on
        if (cfg.fileEnabled()) {
            Path dir = cfg.fileDir().isAbsolute() ? cfg.fileDir() : gameDir.resolve(cfg.fileDir());
            LogHub.addSink(new FileLogSink(dir, cfg.fileKeep()));
        }

        ModuleReg reg = ModuleReg.registry();
        for (String[] m : MODULES) {
            String deps = m[2];
            reg.register(m[0], m[1], deps.isBlank() ? new String[0] : deps.split(","));
        }

        // Embed the diagnostics into every crash report's system report.
        CrashReportCallables.registerCrashCallable("Subterra Diagnostics",
                CrashDumper::diagnosticsBlock, () -> true);

        Logger.get("subterra").info("logging active: threshold " + cfg.level()
                + ", ring " + cfg.ringSize()
                + ", file " + (cfg.fileEnabled() ? cfg.fileDir() + " (keep " + cfg.fileKeep() + ")" : "off")
                + ", analysis " + (cfg.analysisEnabled() ? cfg.analysisDevice() + "/" + cfg.analysisModel() : "off"));
    }

    private static LogConfig loadConfig(Path gameDir) {
        Path config = gameDir.resolve("config/subterra/log.td");
        if (!Files.isRegularFile(config)) {
            return LogConfig.defaults();
        }
        try {
            String text = Files.readString(config, StandardCharsets.UTF_8);
            if (text.isBlank()) {
                return LogConfig.defaults();
            }
            TdTable table = Td.parse(text); // malformed input raises; caught below
            return LogConfig.fromTd(table);
        } catch (IllegalArgumentException | IOException e) {
            System.err.println("[Subterra] log.td ignored (" + e + "), using defaults");
            return LogConfig.defaults();
        }
    }
}