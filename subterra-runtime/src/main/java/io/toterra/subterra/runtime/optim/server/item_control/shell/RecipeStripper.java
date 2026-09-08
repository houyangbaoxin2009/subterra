// Ported from itemban (github.com/lnsanes/itemban), Apache-2.0 (c) lnsanes;
// adapted to Subterra's NeoForge 1.21.1 internal capability.
package io.toterra.subterra.runtime.optim.server.item_control.shell;

import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Removes recipes whose output is banned. Only the output is judged, never
 * recipes that merely consume a banned item as an ingredient, to avoid wiping
 * the many vanilla/modded recipes.
 *
 * <p>On datapack reload the full recipe table is re-snapshotted; {@code
 * /itemban add/remove/reload} only re-filter from the snapshot, so removing a
 * ban restores its recipes. A single recipe read failure preserves that
 * recipe rather than interrupting the load. The write-back goes through the
 * vanilla {@link RecipeManager#replaceRecipes}, in 1.21.1 via the
 * {@link RecipeHolder} collection.
 *
 * <p>NeoForge 1.21.1 note: recipes are carried as {@link RecipeHolder}s — the
 * id lives on the holder ({@link RecipeHolder#id()}), not on the
 * {@link Recipe} value.
 */
public final class RecipeStripper {

    /** Full recipe table (copy) before filtering, captured after datapack load. */
    private static List<RecipeHolder<?>> snapshot = List.of();
    private static MinecraftServer current;
    private static boolean serverLive;

    private RecipeStripper() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        RecipeManager reloading = event.getServerResources().getRecipeManager();
        event.addListener((preparationBarrier, resourceManager, prepareProfiler, applyProfiler, backgroundExecutor, gameExecutor) ->
            preparationBarrier.wait(net.minecraft.util.Unit.INSTANCE).thenRunAsync(() -> {
                MinecraftServer server = current;
                if (server != null && serverLive) {
                    recaptureAndApply(server, reloading);
                }
            }, gameExecutor));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        current = event.getServer();
        serverLive = true;
        recaptureAndApply(current);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        serverLive = false;
        current = null;
        snapshot = List.of();
    }

    /** After {@code /itemban add/remove/reload}: re-filter from snapshot. */
    public static int applyFromSnapshot(MinecraftServer server) {
        if (server == null) {
            return 0;
        }
        if (snapshot.isEmpty()) {
            return recaptureAndApply(server);
        }
        return replaceFrom(server, server.getRecipeManager(), snapshot, true);
    }

    static int recaptureAndApply(MinecraftServer server) {
        if (server == null) {
            return 0;
        }
        return recaptureAndApply(server, server.getRecipeManager());
    }

    static int recaptureAndApply(MinecraftServer server, RecipeManager manager) {
        if (server == null || manager == null) {
            return 0;
        }
        Collection<RecipeHolder<?>> currentRecipes = manager.getRecipes();
        if (currentRecipes.isEmpty()) {
            ItemControl.LOGGER.error("[item_control] recipe table empty; skipped filtering to avoid wiping recipes");
            return 0;
        }
        snapshot = List.copyOf(new ArrayList<>(currentRecipes));
        return replaceFrom(server, manager, snapshot, true);
    }

    private static int replaceFrom(MinecraftServer server, RecipeManager manager, List<RecipeHolder<?>> source, boolean syncPlayers) {
        List<RecipeHolder<?>> keep = new ArrayList<>(source.size());
        List<ResourceLocation> removedIds = new ArrayList<>();
        var registryAccess = server.registryAccess();

        for (RecipeHolder<?> holder : source) {
            try {
                ItemStack result = holder.value().getResultItem(registryAccess);
                if (!result.isEmpty() && isOutputBanned(result)) {
                    removedIds.add(holder.id());
                    continue;
                }
                keep.add(holder);
            } catch (Throwable t) {
                ItemControl.LOGGER.warn("[item_control] failed to read recipe {}; kept it: {}", safeId(holder), t.toString());
                keep.add(holder);
            }
        }

        try {
            manager.replaceRecipes(keep);
        } catch (Throwable t) {
            ItemControl.LOGGER.error("[item_control] writing back to RecipeManager failed; recipe table unchanged", t);
            return 0;
        }

        int removed = removedIds.size();
        if (removed == 0) {
            ItemControl.LOGGER.info("[item_control] recipe filter done; nothing to remove (snapshot {})", source.size());
        } else {
            String examples = removedIds.stream().limit(8).map(ResourceLocation::toString).reduce((a, b) -> a + ", " + b).orElse("");
            ItemControl.LOGGER.info("[item_control] recipe filter done; removed {} of {}: {}", removed, keep.size(), examples);
        }

        if (!verifyNoBannedOutputsRemain(server, manager)) {
            ItemControl.LOGGER.error("[item_control] banned outputs still present after filtering; check the log");
        }

        if (syncPlayers) {
            syncToPlayers(server, manager);
        }
        return removed;
    }

    private static boolean isOutputBanned(ItemStack result) {
        String id = result.getItem().builtInRegistryHolder().key().location().toString();
        // items match by id in 1.21.1 (DataComponent system, no legacy CompoundTag).
        return ItemControlConfig.isBlacklisted(id, null);
    }

    private static boolean verifyNoBannedOutputsRemain(MinecraftServer server, RecipeManager manager) {
        var registryAccess = server.registryAccess();
        boolean clean = true;
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            try {
                ItemStack result = holder.value().getResultItem(registryAccess);
                if (!result.isEmpty() && isOutputBanned(result)) {
                    ItemControl.LOGGER.error("[item_control] leftover recipe after filter: {} -> {}", holder.id(), result);
                    clean = false;
                }
            } catch (Throwable ignored) {
                // consistent with the main loop: failed-to-read recipes are kept
            }
        }
        return clean;
    }

    private static void syncToPlayers(MinecraftServer server, RecipeManager manager) {
        Collection<RecipeHolder<?>> recipes = manager.getRecipes();
        ClientboundUpdateRecipesPacket packet = new ClientboundUpdateRecipesPacket(recipes);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                player.connection.send(packet);
            } catch (Throwable t) {
                ItemControl.LOGGER.warn("[item_control] failed to sync recipes to {}: {}",
                        player.getGameProfile().getName(), t.toString());
            }
        }
    }

    private static String safeId(RecipeHolder<?> holder) {
        try {
            return String.valueOf(holder.id());
        } catch (Throwable t) {
            return "<unknown>";
        }
    }
}