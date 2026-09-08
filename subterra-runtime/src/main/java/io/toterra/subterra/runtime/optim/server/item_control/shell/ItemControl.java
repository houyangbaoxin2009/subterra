// Ported from itemban (github.com/lnsanes/itemban), Apache-2.0 (c) lnsanes;
// adapted to Subterra's NeoForge 1.21.1 internal capability.
package io.toterra.subterra.runtime.optim.server.item_control.shell;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Subterra-internal entry point for the itemban port: item/block blacklist,
 * auto-cleanup, audit, optional ban and recipe stripping. Not a {@code @Mod}:
 * Subterra's own mod entry calls {@link #bootstrap(ModContainer)} from its
 * constructor, keeping the feature an internal capability of the Subterra jar.
 *
 * <p>Upstream's widget had a {@code @Mod} + {@code commonSetup} that only
 * logged; here the td config is preloaded (with default fallback) and the
 * game-event handlers, command wiring and recipe stripper are registered
 * directly on {@link NeoForge#EVENT_BUS}. No separate mod event bus work is
 * needed, so the injected {@link ModContainer}'s event bus is unused.
 */
public final class ItemControl {
    public static final String NAME = "itemban item/block blacklist control";
    public static final Logger LOGGER = LogUtils.getLogger();

    private ItemControl() {
    }

    /**
     * Preloads the td config ({@code config/subterra/item_control.td}) and
     * wires the handlers, commands and recipe stripper.
     */
    public static void bootstrap(ModContainer container) {
        ItemControlConfig.load(FMLPaths.GAMEDIR.get());
        NeoForge.EVENT_BUS.register(ItemControlHandler.class);
        NeoForge.EVENT_BUS.register(RecipeStripper.class);
        NeoForge.EVENT_BUS.addListener(ItemControlCommands::onRegisterCommands);
        LOGGER.info("[item_control] item/block blacklist control active ({})", ItemControlConfig.summary());
    }
}