// Ported from Inventory Advancement Accelerator (github.com/vicuna-main/InventoryAdvancementAccelerator), MIT (c) vicuna.
package io.toterra.subterra.optim.server.invadvopt;

import io.toterra.subterra.optim.server.invadvopt.command.InvAdvOptCommand;
import io.toterra.subterra.optim.server.invadvopt.config.InvAdvOptConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Subterra-internal entry point for the Inventory Advancement Accelerator.
 * Not a {@code @Mod}: Subterra's own mod entry registers this via
 * {@link #bootstrap(ModContainer)} during FMLCommonSetup, keeping the
 * accelerator an internal capability of the Subterra mod jar.
 */
public final class InventoryAdvancementAccelerator {
    public static final String MOD_ID = "subterra_invadvopt";
    private static final InventoryAdvancementRuntime RUNTIME = new InventoryAdvancementRuntime();

    private InventoryAdvancementAccelerator() {}

    public static InventoryAdvancementRuntime runtime() {
        return RUNTIME;
    }

    public static void bootstrap(ModContainer container) {
        // Explicit config file name: the default would collide with the main
        // mod's subterra-common.toml (both are registered on the subterra container).
        container.registerConfig(ModConfig.Type.COMMON, InvAdvOptConfig.SPEC, "subterra-invadvopt-common.toml");
        NeoForge.EVENT_BUS.register(new InventoryAdvancementAccelerator());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        RUNTIME.startupSelfCheck();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        RUNTIME.clear();
    }

    @SubscribeEvent
    public void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD && event.shouldUpdateStaticData()) {
            RUNTIME.tagsUpdated();
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        RUNTIME.processIndexWarmups(event.getServer());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        InvAdvOptCommand.register(event.getDispatcher());
    }
}
