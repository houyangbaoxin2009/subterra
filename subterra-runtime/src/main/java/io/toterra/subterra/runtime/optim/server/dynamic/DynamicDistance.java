// Ported from ServerCore (github.com/Wesley1808/ServerCore), MIT (c) Wesley1808.
package io.toterra.subterra.runtime.optim.server.dynamic;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Subterra-internal entry point for the ServerCore dynamic
 * view/simulation-distance port. Not a {@code @Mod}: Subterra's own mod entry
 * registers this via {@link #bootstrap(ModContainer)} in the mod constructor,
 * keeping the feature an internal capability of the Subterra mod jar.
 *
 * <p>The manager is created once a dedicated/integrated server starts and
 * driven by {@link ServerTickEvent.Post} (every 20 ticks it measures the
 * server's smoothed tick time and, when {@code dynamic.enabled} is set, walks
 * the configured settings chain to pull the MSPT back toward
 * {@code target_ms}). On server stop the {@link DynamicSetting} values are
 * reset so a later start re-initializes them.</p>
 */
public final class DynamicDistance {
    public static final String MOD_ID = "subterra_servercore";
    public static final String NAME = "ServerCore dynamic view/simulation distance";

    public static final Logger LOGGER = LogUtils.getLogger();

    private static volatile DynamicConfig config;
    private static volatile DynamicManager manager;

    private DynamicDistance() {
    }

    /**
     * Preloads the td config and wires the server lifecycle listeners. The
     * ready line is emitted here at mod construction so the boot gate can
     * prove the feature is active.
     */
    public static void bootstrap(ModContainer container) {
        LOGGER.info("[{}] dynamic distance tuning active ({})", MOD_ID, config());
        NeoForge.EVENT_BUS.register(new DynamicDistance());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        manager = new DynamicManager(event.getServer(), config());
        LOGGER.info("[{}] dynamic distance manager initialized (average mspt = {})", MOD_ID, manager.getAverageTickTime());
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        DynamicManager active = manager;
        if (active != null) {
            active.update();
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        DynamicSetting.resetAll();
        manager = null;
    }

    /**
     * Lazily-initialized config (double-checked). Called from the boot path,
     * which may run before the mod constructor.
     */
    public static DynamicConfig config() {
        DynamicConfig result = config;
        if (result == null) {
            synchronized (DynamicDistance.class) {
                result = config;
                if (result == null) {
                    result = DynamicConfig.load(FMLPaths.GAMEDIR.get());
                    config = result;
                }
            }
        }
        return result;
    }
}