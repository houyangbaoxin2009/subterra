package io.toterra.subterra.api.time;

import java.time.Duration;
import java.time.Instant;

/**
 * L3 API library (p.1.3): time helpers for domain mods.
 * Pure Java, no Minecraft runtime dependency.
 */
public final class TimeApi {

    private TimeApi() {
    }

    /** Current wall-clock millis (UTC). */
    public static long nowMillis() {
        return System.currentTimeMillis();
    }

    /** Monotonic high-resolution clock (nanos since an arbitrary origin). */
    public static long nowNanos() {
        return System.nanoTime();
    }

    /** Formats wall-clock millis as an ISO-8601 UTC instant (ends with "Z"). */
    public static String isoUtc(long millis) {
        return Instant.ofEpochMilli(millis).toString();
    }

    /** Parses an ISO-8601 UTC instant back to wall-clock millis. */
    public static long parseIsoUtc(String iso) {
        return Instant.parse(iso).toEpochMilli();
    }

    /**
     * Compact human-readable duration, e.g. {@code 2d 3h 4m 5s}; zero-leading
     * units are omitted. Sub-second durations render as {@code <1s}.
     */
    public static String humanDuration(long millis) {
        if (millis < 0) {
            return "-" + humanDuration(-millis);
        }
        if (millis < 1000) {
            return "<1s";
        }
        Duration d = Duration.ofMillis(millis);
        long days = d.toDays();
        long hours = d.toHoursPart();
        long minutes = d.toMinutesPart();
        long seconds = d.toSecondsPart();
        StringBuilder sb = new StringBuilder(16);
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (days > 0 || hours > 0) {
            sb.append(hours).append("h ");
        }
        if (days > 0 || hours > 0 || minutes > 0) {
            sb.append(minutes).append("m ");
        }
        sb.append(seconds).append('s');
        return sb.toString();
    }
}