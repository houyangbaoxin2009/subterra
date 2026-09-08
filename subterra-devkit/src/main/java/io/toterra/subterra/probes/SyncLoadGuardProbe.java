package io.toterra.subterra.probes;

import io.toterra.subterra.engine.optim.server.loading.SyncLoadGuard;
import io.toterra.subterra.engine.optim.server.loading.SyncLoadGuard.Setting;

/**
 * Deterministic acceptance probe for the sync-load guard core (p.1.4.17,
 * ServerCore "reduce-sync-loads" concept, clean-room): hot-spot registry
 * completeness/rejection, master-switch gating, boundary behaviour. Pure JVM.
 */
public final class SyncLoadGuardProbe {

    private SyncLoadGuardProbe() {
    }

    private static int failures = 0;

    private static void check(String name, boolean ok) {
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    public static void main(String[] args) {
        // Registry completeness.
        check("registry has eight hot spots", SyncLoadGuard.KNOWN_HOTSPOTS.size() == 8);
        check("map_tick known", SyncLoadGuard.isKnown("map_tick"));
        check("pathfinding known", SyncLoadGuard.isKnown("pathfinding"));
        check("entity_teleport known", SyncLoadGuard.isKnown("entity_teleport"));
        check("command_level known", SyncLoadGuard.isKnown("command_level"));
        check("locate known", SyncLoadGuard.isKnown("locate"));
        check("compass known", SyncLoadGuard.isKnown("compass"));
        check("projectile_hit known", SyncLoadGuard.isKnown("projectile_hit"));
        check("village_siege known", SyncLoadGuard.isKnown("village_siege"));
        check("unknown site rejected", !SyncLoadGuard.isKnown("ender_pearl"));
        check("null site rejected", !SyncLoadGuard.isKnown(null));

        // Master switch gating.
        Setting on = Setting.defaultOn();
        Setting off = Setting.off();
        check("default on", on.reduceSyncLoads());
        check("explicit off", !off.reduceSyncLoads());

        check("on applies known site", SyncLoadGuard.shouldApply(on, "map_tick"));
        check("on ignores unknown site", !SyncLoadGuard.shouldApply(on, "ender_pearl"));
        check("on ignores null site", !SyncLoadGuard.shouldApply(on, null));
        check("off blocks known site", !SyncLoadGuard.shouldApply(off, "map_tick"));
        check("off blocks all", !SyncLoadGuard.shouldApply(off, "village_siege"));

        if (failures == 0) {
            System.out.println("[SyncLoadGuardProbe] PASS (sync-load guard core, " + 16 + " checks)");
            System.exit(0);
        } else {
            System.out.println("[SyncLoadGuardProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }
}