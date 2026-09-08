package io.toterra.subterra.runtime.fix;

import java.util.List;

/**
 * L2 compatibility layer (p.1.2): RCA-driven registry of known Java 25 gaps.
 * <p>
 * When the boot gate (ServerBootProbe) surfaces a Java 25 incompatibility,
 * root-cause it here: add an entry, ship the fix, and flip the status to
 * PATCHED. The registry must stay boot-time visible and probe-gated — an open
 * registry entry fails acceptance until patched.
 * <p>
 * Baseline (2026-09-05): Minecraft 1.21.1 + NeoForge 21.1.249 boot on Java 25
 * LTS out of the box — registry starts empty and verified by CompatProbe.
 */
public final class Java25Gaps {

    /** A single RCA'd Java 25 gap and its remediation status. */
    public enum Status {
        /** Gap reproduced and awaiting a fix. Fails acceptance. */
        OPEN,
        /** Fix shipped and the boot gate passes with it applied. */
        PATCHED
    }

    /** One registry entry; ids are plain text, never letter+number labels. */
    public record Gap(Status status, String module, String note) {
    }

    private static final List<Gap> REGISTRY = List.of();

    private Java25Gaps() {
    }

    /** Immutable registry of every RCA'd gap (open or patched). */
    public static List<Gap> registry() {
        return REGISTRY;
    }

    /** True while at least one gap is still OPEN — fails the acceptance gate. */
    public static boolean hasOpenGaps() {
        for (Gap gap : REGISTRY) {
            if (gap.status() == Status.OPEN) {
                return true;
            }
        }
        return false;
    }
}