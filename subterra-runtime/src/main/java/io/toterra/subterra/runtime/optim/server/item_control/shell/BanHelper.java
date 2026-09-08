// Ported from itemban (github.com/lnsanes/itemban), Apache-2.0 (c) lnsanes;
// adapted to Subterra's NeoForge 1.21.1 internal capability.
package io.toterra.subterra.runtime.optim.server.item_control.shell;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.UUID;

/**
 * Ban helpers compatible with LnsanesBan (which can override vanilla {@code /ban}).
 *
 * <p>Order: LnsanesBan API (reflection) → vanilla {@link UserBanList} →
 * server-side {@code /ban} as last resort. Always disconnects afterwards if
 * still connected. The LnsanesBan step is optional (only active when the
 * third-party mod is present) and guarded entirely by failing-fast reflection,
 * so its absence can never hurt the Subterra path.
 */
public final class BanHelper {
    private static final String LNSANESBAN_MODID = "lnsanesban";
    private static final String LNSANESBAN_API = "com.lnsanes.lnsanesban.api.LnsanesBanApi";
    private static final String LNSANESBAN_SERVICE = "com.lnsanes.lnsanesban.server.BanService";

    private static Boolean lnsanesbanLoaded;

    private BanHelper() {
    }

    public static void banAndKick(ServerPlayer player, String reason) {
        if (player == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        String banReason = reason == null || reason.isBlank()
                ? "Banned for a violation."
                : reason;

        // Save current inventory to disk first (caller should already have
        // cleared the violation), then ban/kick.
        try {
            if (!player.hasDisconnected()) {
                player.getInventory().setChanged();
                server.getPlayerList().saveAll();
            }
        } catch (Exception e) {
            ItemControl.LOGGER.warn("[item_control] failed to save player data before ban: {}", e.toString());
        }

        boolean persisted = false;
        if (isLnsanesBanLoaded()) {
            persisted = banViaLnsanesBan(player, banReason);
            if (persisted) {
                ItemControl.LOGGER.info("[item_control] banned {} via LnsanesBan", player.getGameProfile().getName());
            } else {
                ItemControl.LOGGER.warn("[item_control] LnsanesBan call failed; falling back to vanilla ban list");
            }
        }

        if (!persisted) {
            persisted = banViaVanillaList(player, banReason);
            if (persisted) {
                ItemControl.LOGGER.info("[item_control] wrote {} to the vanilla ban list", player.getGameProfile().getName());
            }
        }

        if (!persisted) {
            // Last resort: run /ban as the *server* (not the victim) so the source survives.
            try {
                String name = player.getGameProfile().getName();
                String cmd = "ban " + name + " " + banReason;
                // performPrefixedCommand is void in 1.21.1; treat completion as success.
                server.getCommands().performPrefixedCommand(
                        server.createCommandSourceStack().withPermission(4), cmd);
                ItemControl.LOGGER.info("[item_control] /ban fallback executed player={}", name);
                persisted = true;
            } catch (Exception e) {
                ItemControl.LOGGER.warn("[item_control] /ban fallback command failed", e);
            }
        }

        if (!persisted) {
            ItemControl.LOGGER.error("[item_control] could not persist a ban record; kicking {} only",
                    player.getGameProfile().getName());
        }

        // BanService already kicks; disconnect again if the session is still open.
        try {
            if (player.connection != null && !player.hasDisconnected()) {
                player.connection.disconnect(Component.literal(banReason));
            }
        } catch (Exception e) {
            ItemControl.LOGGER.debug("[item_control] disconnect after ban ignored: {}", e.toString());
        }
    }

    private static boolean isLnsanesBanLoaded() {
        if (lnsanesbanLoaded == null) {
            try {
                lnsanesbanLoaded = ModList.get().isLoaded(LNSANESBAN_MODID);
            } catch (Throwable t) {
                lnsanesbanLoaded = false;
            }
        }
        return lnsanesbanLoaded;
    }

    private static boolean banViaLnsanesBan(ServerPlayer player, String reason) {
        // Prefer the public API (3.1.2+).
        try {
            Class<?> api = Class.forName(LNSANESBAN_API);
            Method banPlayer = api.getMethod("banPlayer", ServerPlayer.class, String.class, String.class);
            banPlayer.invoke(null, player, reason, "Subterra");
            return true;
        } catch (ClassNotFoundException ignored) {
            // Older LnsanesBan without the API — fall through to BanService.
        } catch (Throwable e) {
            ItemControl.LOGGER.warn("[item_control] LnsanesBanApi.banPlayer failed", e);
        }

        try {
            Class<?> service = Class.forName(LNSANESBAN_SERVICE);
            Method banCascade = service.getMethod(
                    "banCascade",
                    String.class, UUID.class, String.class, String.class, String.class, MinecraftServer.class);
            banCascade.invoke(null,
                    player.getGameProfile().getName(),
                    player.getUUID(),
                    null,
                    reason,
                    "Subterra",
                    player.getServer());
            return true;
        } catch (Throwable e) {
            ItemControl.LOGGER.warn("[item_control] BanService.banCascade failed", e);
            return false;
        }
    }

    private static boolean banViaVanillaList(ServerPlayer player, String reason) {
        try {
            MinecraftServer server = player.getServer();
            if (server == null) {
                return false;
            }
            UserBanList banList = server.getPlayerList().getBans();
            GameProfile profile = player.getGameProfile();
            if (!banList.isBanned(profile)) {
                banList.add(new UserBanListEntry(profile, new Date(), "Subterra", null, reason));
            }
            return true;
        } catch (Throwable e) {
            ItemControl.LOGGER.warn("[item_control] wrote to vanilla ban list failed", e);
            return false;
        }
    }
}