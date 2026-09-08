package io.toterra.subterra.engine.optim.server.loading;

import java.util.Objects;
import java.util.Set;

/**
 * Sync-load guard core (clean-room re-key of the ServerCore "reduce-sync-
 * loads" concept, GPL family surface re-written from contract, no upstream
 * code): several hot paths force-load chunks synchronously (map ticking,
 * pathfinding across chunk borders, entity teleportation, commands, locate /
 * compass, projectile hits, village sieges), causing server-thread stalls.
 * With the guard on, each hot spot instead executes only when the target
 * chunk is already loaded and skips it otherwise — a registry below names the
 * known hot spots so the MC shell can consult one predicate.
 *
 * <p>Pure JDK; immutable; deterministic.</p>
 */
public final class SyncLoadGuard {

    /** Known sync-load hot spots (MC shell hooks consult this registry). */
    public static final Set<String> KNOWN_HOTSPOTS = Set.of(
            "map_tick",
            "pathfinding",
            "entity_teleport",
            "command_level",
            "locate",
            "compass",
            "projectile_hit",
            "village_siege");

    private SyncLoadGuard() {
    }

    /** Master switch for the reduce-sync-loads behaviour. */
    public record Setting(boolean reduceSyncLoads) {

        public static Setting defaultOn() {
            return new Setting(true);
        }

        public static Setting off() {
            return new Setting(false);
        }
    }

    /** True when the hot spot is one of the known sync-load sites. */
    public static boolean isKnown(String hotSpot) {
        return hotSpot != null && KNOWN_HOTSPOTS.contains(hotSpot);
    }

    /**
     * Whether the {@code hotSpot} should consult the loaded-chunk guard this
     * run: requires the master switch on and a known site id.
     */
    public static boolean shouldApply(Setting setting, String hotSpot) {
        Objects.requireNonNull(setting, "setting");
        return setting.reduceSyncLoads() && isKnown(hotSpot);
    }
}