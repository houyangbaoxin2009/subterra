package io.toterra.subterra.probes;

import io.toterra.subterra.runtime.launch.JvmEnv;

/**
 * Deterministic acceptance probe for the L1 launch layer (subterra-launch).
 * Pure JVM — no Minecraft runtime required.
 * <p>
 * Verifies the L1 runtime contract: baseline constant and the JVM report
 * are consistent on any supported JVM (feature &gt;= 21).
 * Exit 0 = PASS, exit 1 = FAIL (gates acceptance; never shipped in the mod jar).
 */
public final class LaunchLayerProbe {

    private LaunchLayerProbe() {
    }

    public static void main(String[] args) {
        int failures = 0;

        if (JvmEnv.MIN_FEATURE != 21) {
            fail("MIN_FEATURE", "expected 21, got " + JvmEnv.MIN_FEATURE);
            failures++;
        }

        JvmEnv.Report report = JvmEnv.verify();
        boolean expectVerified = report.javaVersion() >= JvmEnv.MIN_FEATURE;
        if (report.verified() != expectVerified) {
            fail("Report.verified", "expected " + expectVerified + " for feature " + report.javaVersion());
            failures++;
        }

        if (failures == 0) {
            System.out.println("[LaunchLayerProbe] PASS (feature=" + report.javaVersion() + ", verified=" + report.verified() + ", min=" + JvmEnv.MIN_FEATURE + ")");
            System.exit(0);
        } else {
            System.out.println("[LaunchLayerProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }

    private static void fail(String what, String detail) {
        System.out.println("[LaunchLayerProbe] FAIL " + what + ": " + detail);
    }
}