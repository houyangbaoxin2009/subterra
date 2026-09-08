package io.toterra.subterra.probes;

import io.toterra.subterra.optim.entity.merging.MergePolicy;
import io.toterra.subterra.optim.entity.merging.Merger;

import java.util.Random;

/**
 * Deterministic acceptance probe for the entity merging core (p.1.4.1, ServerCore
 * merging surface): policy validation, radius bound, probability extremes and
 * injected-RNG determinism. Pure JVM — no Minecraft runtime.
 */
public final class MergeProbe {

    private MergeProbe() {
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
        policyValidation();
        radiusBound();
        probability();
        determinism();

        if (failures == 0) {
            System.out.println("[MergeProbe] PASS (entity merging core)");
            System.exit(0);
        } else {
            System.out.println("[MergeProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }

    private static void policyValidation() {
        check("policy validates fraction", rejectFraction(-0.1) && rejectFraction(1.1)
                && rejectFraction(Double.NaN));
        try {
            new MergePolicy(true, -1, 1);
            check("policy rejects negative radius", false);
        } catch (IllegalArgumentException expected) {
            check("policy rejects negative radius", true);
        }
        check("disabled policy factory", !MergePolicy.disabled().enabled()
                && MergePolicy.disabled().fraction() == 1.0);
    }

    private static boolean rejectFraction(double f) {
        try {
            new MergePolicy(true, 2, f);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static void radiusBound() {
        MergePolicy p = new MergePolicy(true, 3.0, 1.0);
        check("within radius merges", Merger.canMerge("i", "i", 8.9, p, () -> 0.0)); // 3^2=9 bounds
        check("outside radius rejected", !Merger.canMerge("i", "i", 9.1, p, () -> 0.0));
        check("exact radius merges", Merger.canMerge("i", "i", 9.0, p, () -> 0.0));
        check("disabled policy never merges",
                !Merger.canMerge("i", "i", 0.0, MergePolicy.disabled(), () -> 0.0));
    }

    private static void probability() {
        MergePolicy never = new MergePolicy(true, 5, 0.0);
        check("fraction zero never merges",
                !Merger.canMerge("i", "i", 1, never, () -> 0.0));
        MergePolicy always = new MergePolicy(true, 5, 1.0);
        check("fraction one always merges",
                Merger.canMerge("i", "i", 1, always, () -> 0.999));
        MergePolicy half = new MergePolicy(true, 5, 0.5);
        int merges = 0;
        for (int i = 0; i < 100; i++) {
            final int idx = i;
            if (Merger.canMerge("i", "i", 1, half, () -> (idx % 2) == 0 ? 0.1 : 0.9)) {
                merges++;
            }
        }
        check("fraction half rolls alternately", merges == 50);
        check("identity mismatch never merges",
                !Merger.canMerge("a", "b", 0, always, () -> 0.0));
    }

    private static void determinism() {
        MergePolicy p = new MergePolicy(true, 5, 0.5);
        Random rng = new Random(42L);
        int[] first = run(p, rng);
        rng = new Random(42L);
        int[] second = run(p, rng);
        check("same seed same stream", java.util.Arrays.equals(first, second));
        check("mixed outcomes not all-or-nothing",
                first[0] > 0 && first[0] < first[1]);
    }

    private static int[] run(MergePolicy p, Random rng) {
        int merges = 0;
        int tries = 200;
        for (int i = 0; i < tries; i++) {
            if (Merger.canMerge("i", "i", 1, p, rng::nextDouble)) {
                merges++;
            }
        }
        return new int[]{merges, tries};
    }
}