package io.toterra.subterra.probes;

import io.toterra.subterra.runtime.launch.JvmLaunchArgs;

import java.util.List;

/**
 * Deterministic acceptance probe for the L1 JVM argument package (p.1.1).
 * Pure JVM — no Minecraft runtime required.
 * <p>
 * Asserts the startup contract of {@link JvmLaunchArgs}: the static tuning
 * flags are always present in the full package, are individually reported
 * when missing, and the Java 25 compat opens are well-formed.
 * Exit 0 = PASS, exit 1 = FAIL (gates acceptance; never shipped in the mod jar).
 */
public final class LaunchArgsProbe {

    private LaunchArgsProbe() {
    }

    public static void main(String[] args) {
        int failures = 0;
        List<String> all = JvmLaunchArgs.all();

        for (String flag : JvmLaunchArgs.staticTuningFlags()) {
            if (!all.contains(flag)) {
                fail("static flag missing from all()", flag);
                failures++;
            }
        }

        List<String> missing = JvmLaunchArgs.missingStaticFlags(all);
        if (!missing.isEmpty()) {
            fail("missingStaticFlags(all())", "expected empty, got " + missing);
            failures++;
        }

        List<String> missingOnEmpty = JvmLaunchArgs.missingStaticFlags(List.of());
        for (String flag : JvmLaunchArgs.staticTuningFlags()) {
            if (!missingOnEmpty.contains(flag)) {
                fail("missingStaticFlags(empty) lacks", flag);
                failures++;
            }
        }

        List<String> opens = JvmLaunchArgs.java25CompatOpens();
        if (opens.isEmpty() || opens.size() % 2 != 0) {
            fail("java25CompatOpens()", "must be non-empty and come in --add-opens/target pairs");
            failures++;
        }
        for (int i = 0; i < opens.size(); i += 2) {
            if (!"--add-opens".equals(opens.get(i))) {
                fail("java25CompatOpens() odd element at " + i, opens.get(i));
                failures++;
            }
            if (!opens.get(i + 1).contains("=")) {
                fail("java25CompatOpens() target lacks 'module=ALL-UNNAMED' form", opens.get(i + 1));
                failures++;
            }
        }

        if (failures == 0) {
            System.out.println("[LaunchArgsProbe] PASS (static=" + JvmLaunchArgs.staticTuningFlags().size() + ", opens=" + opens.size() + ")");
            System.exit(0);
        } else {
            System.out.println("[LaunchArgsProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }

    private static void fail(String what, String detail) {
        System.out.println("[LaunchArgsProbe] FAIL " + what + ": " + detail);
    }
}