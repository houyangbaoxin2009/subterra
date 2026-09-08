package io.toterra.subterra.probes;

import io.toterra.subterra.engine.optim.entity.spawning.SpawnEnforcement;
import io.toterra.subterra.engine.optim.entity.spawning.SpawnEnforcement.Source;

/**
 * Deterministic acceptance probe for the special-spawn-source enforcement core
 * (p.1.4.16, ServerCore mob-spawning GPL face, clean-room): known-source
 * registry, enforce/borrow semantics, boundaries. Pure JVM.
 */
public final class SpawnEnforcementProbe {

    private SpawnEnforcementProbe() {
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
        // Known-source registry.
        check("registry has four sources", SpawnEnforcement.KNOWN_SOURCES.size() == 4);
        check("registry lists zombie reinforcement",
                SpawnEnforcement.KNOWN_SOURCES.contains(SpawnEnforcement.ZOMBIE_REINFORCEMENT));
        check("registry lists portal piglin",
                SpawnEnforcement.KNOWN_SOURCES.contains(SpawnEnforcement.PORTAL_PIGLIN));
        check("registry lists spawner",
                SpawnEnforcement.KNOWN_SOURCES.contains(SpawnEnforcement.SPAWNER));
        check("registry lists infested",
                SpawnEnforcement.KNOWN_SOURCES.contains(SpawnEnforcement.INFESTED));
        check("registry rejects unknown source",
                !SpawnEnforcement.KNOWN_SOURCES.contains("wither_spawn"));

        // Validation.
        check("validation rejects negative additional", rejects(true, -1));

        // Enforcement off: always allowed (vanilla bypass).
        Source off = Source.disabled();
        check("disabled always allowed at cap", SpawnEnforcement.allowed(off, 500, 500));
        check("disabled allowed way over", SpawnEnforcement.allowed(off, 5000, 100));

        // Enforcement on: refused at the category cap.
        Source on = Source.enabled();
        check("enforced allowed below cap", SpawnEnforcement.allowed(on, 499, 500));
        check("enforced refused at cap", !SpawnEnforcement.allowed(on, 500, 500));
        check("enforced refused over cap", !SpawnEnforcement.allowed(on, 501, 500));

        // Additional capacity borrows from the reserve.
        Source borrow = new Source(true, 50);
        check("borrow refuses below expanded cap", SpawnEnforcement.allowed(borrow, 549, 500));
        check("borrow refuses at expanded cap", !SpawnEnforcement.allowed(borrow, 550, 500));
        check("borrow zero reserve equals disabled add", SpawnEnforcement.allowed(on, 499, 500));

        // Zero-count categories still enforce.
        check("zero cap enforced", !SpawnEnforcement.allowed(on, 0, 0));
        check("zero cap with borrow", SpawnEnforcement.allowed(new Source(true, 1), 0, 0));

        if (failures == 0) {
            System.out.println("[SpawnEnforcementProbe] PASS (spawn enforcement core, " + 16 + " checks)");
            System.exit(0);
        } else {
            System.out.println("[SpawnEnforcementProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }

    private static boolean rejects(boolean enforce, int additional) {
        try {
            new Source(enforce, additional);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}