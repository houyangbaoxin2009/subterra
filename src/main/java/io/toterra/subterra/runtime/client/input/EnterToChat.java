package io.toterra.subterra.runtime.client.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.lwjgl.glfw.GLFW;

/**
 * Enter-to-chat (p-add): pressing bare Enter while no screen is open (same
 * trigger condition as the vanilla T / comment keys — {@code Minecraft.screen
 * == null}) opens the chat input as if the player had pressed it directly.
 *
 * <p>The vanilla chat keys (T / {@code /}) already open {@link ChatScreen}
 * through their own key mapping; this listener only adds Enter as an
 * alternative same-gate trigger, never intercepting keys while any screen
 * (ChatScreen, inventory, hub panel, …) is up — those keep their native
 * handlers.
 *
 * <p>Client-only (dedicated servers never load this class). No config this
 * round; a {@code config/subterra/client.td} gate can be added later if needed.
 */
public final class EnterToChat {

    private EnterToChat() {
    }

    /** Boot hook — {@link SubterraClient} constructor calls this once. */
    public static void bootstrap() {
        // Static @SubscribeEvent handlers are picked up by registering this
        // class on the game event bus (this class is intentionally not
        // @EventBusSubscriber — wiring stays explicit and deterministic).
        NeoForge.EVENT_BUS.register(EnterToChat.class);
    }

    /**
     * Enter key handler: open chat only when no screen is currently open and the
     * world/client are usable (screen null already implies single-player world or
     * server screen absent, mirroring vanilla T-gate).
     */
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        if (event.getKey() != GLFW.GLFW_KEY_ENTER) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            return; // a screen is open (hub, chat, inventory, …) — native handling wins
        }
        if (mc.level == null) {
            return; // not in-game (title/generating); mirror the vanilla T gate
        }
        // Same opening state as the T shortcut: a fresh chat line.
        mc.setScreen(new ChatScreen(""));
    }
}