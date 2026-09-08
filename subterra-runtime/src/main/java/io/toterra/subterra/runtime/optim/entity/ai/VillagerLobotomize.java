// Ported from ServerCore (github.com/Wesley1808/ServerCore), MIT (c) Wesley1808.
package io.toterra.subterra.runtime.optim.entity.ai;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * Subterra-internal entry point for the ServerCore villager lobotomization
 * port. Not a {@code @Mod}: Subterra's own mod entry registers this via
 * {@link #bootstrap(ModContainer)} in the mod constructor, keeping the feature
 * an internal capability of the Subterra mod jar.
 *
 * <p>The {@link AbstractVillagerMixin} drives everything (it skips a stuck
 * villager's brain tick so path-finding CPU is saved); no event listeners are
 * registered here. The td config is loaded lazily on first use via
 * {@link #config()} with defaults on any error or absence; {@code bootstrap}
 * only preloads it.</p>
 */
public final class VillagerLobotomize {
    public static final String MOD_ID = "subterra_servercore";
    public static final String NAME = "ServerCore villager lobotomization";

    public static final Logger LOGGER = LogUtils.getLogger();

    private static volatile LobotomizeConfig config;

    private VillagerLobotomize() {
    }

    /**
     * Preloads the td config so a broken file surfaces at mod construction
     * time; the lazy loader remains the source of truth for the mixin.
     */
    public static void bootstrap(ModContainer container) {
        LOGGER.info("[{}] villager lobotomization ready ({})", MOD_ID, config());
    }

    /**
     * Lazily-initialized config (double-checked). Called from the mixin, which
     * may run before the mod constructor.
     */
    public static LobotomizeConfig config() {
        LobotomizeConfig result = config;
        if (result == null) {
            synchronized (VillagerLobotomize.class) {
                result = config;
                if (result == null) {
                    result = LobotomizeConfig.load(FMLPaths.GAMEDIR.get());
                    config = result;
                }
            }
        }
        return result;
    }
}