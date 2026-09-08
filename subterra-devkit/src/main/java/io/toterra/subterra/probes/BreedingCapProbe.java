package io.toterra.subterra.probes;

import io.toterra.subterra.engine.optim.entity.breeding.BreedingCap;

/**
 * Deterministic acceptance probe for the breeding-cap core (p.1.4.2,
 * ServerCore breeding surface): policy validation, cap adherence per type,
 * acquire/release accounting. Pure JVM.
 */
public final class BreedingCapProbe {

    private BreedingCapProbe() {
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
        check("validation rejects negative", rejects(-1));
        check("unbounded factory", BreedingCap.UNBOUNDED.capPerType() == 0);

        BreedingCap cap2 = new BreedingCap(2);
        BreedingCap.Counters counters = new BreedingCap.Counters();
        check("can start below cap", BreedingCap.canBreed(cap2, counters, "cow")
                && BreedingCap.canBreed(cap2, counters, "cow"));
        counters.acquire("cow");
        counters.acquire("cow");
        check("cap blocks third", !BreedingCap.canBreed(cap2, counters, "cow"));
        check("different type unaffected", BreedingCap.canBreed(cap2, counters, "pig"));
        counters.release("cow");
        check("release frees slot", BreedingCap.canBreed(cap2, counters, "cow"));
        counters.clear();
        check("clear resets", BreedingCap.canBreed(cap2, counters, "cow"));

        BreedingCap unbounded = new BreedingCap(0);
        BreedingCap.Counters many = new BreedingCap.Counters();
        for (int i = 0; i < 100; i++) {
            many.acquire("wolf");
        }
        check("zero cap means unlimited", BreedingCap.canBreed(unbounded, many, "wolf"));

        if (failures == 0) {
            System.out.println("[BreedingCapProbe] PASS (breeding cap core)");
            System.exit(0);
        } else {
            System.out.println("[BreedingCapProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }

    private static boolean rejects(int cap) {
        try {
            new BreedingCap(cap);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }
}