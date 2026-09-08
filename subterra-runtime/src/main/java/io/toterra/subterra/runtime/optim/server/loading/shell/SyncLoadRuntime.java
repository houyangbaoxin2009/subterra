// Clean-room re-key of the ServerCore "reduce-sync-loads" concept (GPL family
// surface re-written from contract, no upstream code): the master switch is
// wired into the shared servercore.td; per-call-site rewrites land
// incrementally behind the guard.
package io.toterra.subterra.runtime.optim.server.loading.shell;

import com.mojang.logging.LogUtils;
import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.optim.server.loading.SyncLoadGuard.Setting;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runtime wiring for {@link SyncLoadGuard}: loads the {@code loading} table
 * of {@code config/subterra/servercore.td} once at mod construction and holds
 * the {@link Setting} for the (incrementally added) call-site shells. The
 * switch defaults to off — vanilla synchronous loading is preserved until a
 * given call-site rewrite is actually wired behind it.
 */
public final class SyncLoadRuntime {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile Setting setting = Setting.off();

    private SyncLoadRuntime() {
    }

    public static void bootstrap(ModContainer container) {
        Setting loaded = load(FMLPaths.GAMEDIR.get());
        setting = loaded;
        LOGGER.info("[subterra_servercore] sync-load guard {} (call-site rewrites land behind it)",
                loaded.reduceSyncLoads() ? "enabled" : "off");
    }

    public static Setting setting() {
        return setting;
    }

    private static Setting load(Path gameDir) {
        Path config = gameDir.resolve("config/subterra/servercore.td");
        if (!Files.isRegularFile(config)) {
            return Setting.off();
        }
        try {
            String text = Files.readString(config, StandardCharsets.UTF_8);
            if (text.isBlank()) {
                return Setting.off();
            }
            TdTable root = Td.parse(text);
            TdValue loading = root.get("loading");
            if (loading instanceof TdTable table) {
                TdValue v = table.get("reduce_sync_loads");
                if (v != null) {
                    return new Setting(v.asBool());
                }
            }
        } catch (IllegalArgumentException | IOException e) {
            LOGGER.warn("sync-load guard: {} ignored ({}), using defaults", config, e);
        }
        return Setting.off();
    }
}