package io.toterra.subterra.probes;

import io.toterra.subterra.optim.entity.spawning.MobcapCalculator;
import io.toterra.subterra.optim.entity.spawning.MobcapCalculator.Quota;

/**
 * Deterministic acceptance probe for the per-player mobcap core (p.1.4.12,
 * ServerCore mob-spawning surface, MIT face clean-room): quota validation,
 * equal per-player division, additional-capacity borrowing, overlap-taking-
 * fullest semantics and boundary behavior. Pure JVM.
 */
public final class MobcapProbe {

    private MobcapProbe() {
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
        // Validation.
        check("validation rejects negative capacity", rejects(-1, 0, 1));
        check("validation rejects negative added", rejects(70, -1, 1));
        check("validation rejects zero players", rejects(70, 0, 0));

        // Equal per-player division.
        check("two players halve", new Quota(70, 0, 2).share() == 35);
        check("single player full cap", new Quota(70, 0, 1).share() == 70);
        check("floor division", new Quota(7, 0, 2).share() == 3);
        check("additional capacity added first", new Quota(70, 30, 2).share() == 50);

        // canSpawn: any participating player with free quota allows the spawn.
        Quota q = new Quota(70, 0, 2); // each player holds 35
        check("spawn while one player under quota",
                MobcapCalculator.canSpawn(q, new int[]{35, 10}));
        check("spawn while both under quota",
                MobcapCalculator.canSpawn(q, new int[]{30, 30}));
        check("spawn blocked when all saturated",
                !MobcapCalculator.canSpawn(q, new int[]{35, 35}));
        check("over-count blocks", !MobcapCalculator.canSpawn(q, new int[]{40, 40}));

        // Saturated share with big per-player counts still blocks.
        Quota small = new Quota(7, 0, 2); // share = 3
        check("small share blocks", !MobcapCalculator.canSpawn(small, new int[]{3, 3}));

        // Enforcement: special sources borrow additional capacity.
        Quota enforced = new Quota(70, 30, 1); // share = 100 (one player)
        check("enforced quota enlarged", enforced.share() == 100);
        check("enforced still capped", !MobcapCalculator.canSpawn(enforced, new int[]{100}));

        // freeQuota sign.
        check("free quota positive", MobcapCalculator.freeQuota(q, 10) == 25);
        check("free quota negative", MobcapCalculator.freeQuota(q, 40) == -5);

        if (failures == 0) {
            System.out.println("[MobcapProbe] PASS (per-player mobcap core, " + 14 + " checks)");
            System.exit(0);
        } else {
            System.out.println("[MobcapProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }

    private static boolean rejects(int capacity, int added, int players) {
        try {
            new Quota(capacity, added, players);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}