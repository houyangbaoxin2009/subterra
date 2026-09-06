package io.toterra.subterra.probes;

import io.toterra.subterra.optim.entity.activation.ActivationPolicy;
import io.toterra.subterra.optim.entity.activation.Activator;
import io.toterra.subterra.optim.entity.activation.TypeofClassifier;
import io.toterra.subterra.optim.entity.activation.TypeofClassifier.Kind;

import java.util.List;

/**
 * Deterministic acceptance probe for the activation-range core (p.1.4.15,
 * ServerCore "activation-range" behaviour, clean-room): policy validation,
 * distance-boundary activation, interval wake-up, immune overrides, new-entity
 * grace, skip-non-immune and typeof classification. Pure JVM.
 */
public final class ActivationProbe {

    private ActivationProbe() {
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

    private static boolean active(ActivationPolicy p, double distSq, long age, int tick,
                                  boolean falling, boolean hurt, boolean panic) {
        return Activator.isActive(p, distSq, age, tick, falling, hurt, panic);
    }

    public static void main(String[] args) {
        ActivationPolicy def = ActivationPolicy.defaults(); // range 16, interval 20, skip 0

        // Validation.
        check("validation rejects negative range", rejects(-1, 20, 0));
        check("validation rejects zero interval", rejects(16, 0, 0));
        check("validation rejects negative skip", rejects(16, 20, -1));

        // In-range activation (d=16 → d²=256 is the boundary, inclusive).
        check("in range active", active(def, 200, 500, 10, false, false, false));
        check("boundary radius active", active(def, 256, 500, 10, false, false, false));
        check("out of range dormant", !active(def, 300, 500, 10, false, false, false));

        // Interval wake-up for out-of-range entities.
        check("out of range woken on interval", active(def, 300, 500, 20, false, false, false));
        check("interval boundary inclusive", active(def, 300, 500, 40, false, false, false));
        check("non-boundary dormant", !active(def, 300, 500, 21, false, false, false));

        // New-entity grace (first 200 ticks always active, even far away).
        check("new entity grace active", active(def, 300, 0, 10, false, false, false));
        check("grace lasts until 199", active(def, 300, 199, 10, false, false, false));
        check("grace expires at 200", !active(def, 300, 200, 10, false, false, false));

        // Immune overrides.
        check("falling immune", active(def, 300, 500, 10, true, false, false));
        check("hurt immune", active(def, 300, 500, 10, false, true, false));
        check("panic immune", active(def, 300, 500, 10, false, false, true));

        // Negative interval disables dormant activation entirely.
        ActivationPolicy noWake = new ActivationPolicy(16, -1, 0);
        check("negative interval kills dormant", !active(noWake, 300, 500, 20, false, false, false));
        check("negative interval keeps in-range", active(noWake, 100, 500, 20, false, false, false));

        // Skip-non-immune: in-range entity skips every 4th tick.
        ActivationPolicy skip = new ActivationPolicy(16, 20, 4);
        check("skip frame inactive", !active(skip, 100, 500, 4, false, false, false));
        check("non-skip frame active", active(skip, 100, 500, 5, false, false, false));

        // typeof classification.
        check("typeof token parsed", Kind.ofTypeofToken("typeof:monster") == Kind.MONSTER);
        check("typeof case insensitive", Kind.ofTypeofToken("typeof:VILLAGER") == Kind.VILLAGER);
        check("typeof unknown kind null", Kind.ofTypeofToken("typeof:dragon") == null);
        check("plain id is not typeof", Kind.ofTypeofToken("minecraft:zombie") == null);
        check("best picks lowest priority", TypeofClassifier.best(List.of(Kind.ANIMAL, Kind.VILLAGER)) == Kind.VILLAGER);
        check("best empty falls back to mob", TypeofClassifier.best(List.of()) == Kind.MOB);
        check("best null falls back to mob", TypeofClassifier.best(null) == Kind.MOB);

        if (failures == 0) {
            System.out.println("[ActivationProbe] PASS (activation-range core, " + 23 + " checks)");
            System.exit(0);
        } else {
            System.out.println("[ActivationProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }

    private static boolean rejects(int range, int interval, int skip) {
        try {
            new ActivationPolicy(range, interval, skip);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}
