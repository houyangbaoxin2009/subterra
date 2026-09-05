package io.toterra.subterra.probes;

import io.toterra.subterra.api.worldgen.RegionAnnouncement;

/**
 * Deterministic acceptance probe for the RegionAnnouncement API (p.1.3.1):
 * unknown/clued/named display strings and input validation. Pure JVM.
 */
public final class RegionProbe {

    private RegionProbe() {
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
        check("unknown shows ???", RegionAnnouncement.UNKNOWN_LABEL
                .equals(RegionAnnouncement.displayName("r1", RegionAnnouncement.Visibility.UNKNOWN, "H", "N")));
        check("clued shows hint",
                "H".equals(RegionAnnouncement.displayName("r1", RegionAnnouncement.Visibility.CLUED, "H", "N")));
        check("named shows custom",
                "N".equals(RegionAnnouncement.displayName("r1", RegionAnnouncement.Visibility.NAMED, "H", "N")));
        check("clued blank hint falls back",
                RegionAnnouncement.UNKNOWN_LABEL.equals(
                        RegionAnnouncement.displayName("r1", RegionAnnouncement.Visibility.CLUED, "  ", "N")));
        check("named blank custom falls back",
                RegionAnnouncement.UNKNOWN_LABEL.equals(
                        RegionAnnouncement.displayName("r1", RegionAnnouncement.Visibility.NAMED, "H", " ")));
        check("convenience hidden",
                RegionAnnouncement.UNKNOWN_LABEL.equals(RegionAnnouncement.name("r1", false, "H")));
        check("convenience clued", "H".equals(RegionAnnouncement.name("r1", true, "H")));
        try {
            RegionAnnouncement.displayName(" ", RegionAnnouncement.Visibility.UNKNOWN, "", null);
            check("rejects blank region id", false);
        } catch (IllegalArgumentException expected) {
            check("rejects blank region id", true);
        }

        if (failures == 0) {
            System.out.println("[RegionProbe] PASS (region announcement API)");
            System.exit(0);
        } else {
            System.out.println("[RegionProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }
}