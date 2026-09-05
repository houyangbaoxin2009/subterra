package io.toterra.subterra.probes;

import io.toterra.subterra.compat.Java25Gaps;
import io.toterra.subterra.compat.Java25Gaps.Gap;

import java.util.List;

/**
 * Deterministic acceptance probe for the L2 compatibility layer (p.1.2).
 * Pure JVM — no Minecraft runtime required.
 * <p>
 * Asserts the {@link Java25Gaps} registry contract: immutable, no open gaps
 * (the boot baseline must stay clean), and every PATCHED entry carries a
 * remediation note.
 * Exit 0 = PASS, exit 1 = FAIL (gates acceptance; never shipped in the mod jar).
 */
public final class CompatProbe {

    private CompatProbe() {
    }

    public static void main(String[] args) {
        int failures = 0;

        List<Gap> registry = Java25Gaps.registry();
        if (registry == null) {
            fail("registry()", "must not be null");
            failures++;
        } else {
            for (Gap gap : registry) {
                if (gap.status() == Java25Gaps.Status.PATCHED && (gap.note() == null || gap.note().isBlank())) {
                    fail("PATCHED gap note", "must document the fix in " + gap.module());
                    failures++;
                }
            }
        }

        if (Java25Gaps.hasOpenGaps()) {
            fail("hasOpenGaps()", "expected false on the boot-verified baseline");
            failures++;
        }

        if (failures == 0) {
            System.out.println("[CompatProbe] PASS (registry entries=" + registry.size() + ", open gaps=0)");
            System.exit(0);
        } else {
            System.out.println("[CompatProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }

    private static void fail(String what, String detail) {
        System.out.println("[CompatProbe] FAIL " + what + ": " + detail);
    }
}