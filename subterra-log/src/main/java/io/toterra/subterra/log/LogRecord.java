package io.toterra.subterra.log;

import java.time.format.DateTimeFormatter;

/**
 * One immutable log event: millisecond timestamp, originating thread, logger
 * name, level, message and an optional formatted stack trace.
 * <p>
 * {@link #format()} renders the NeoForge-style layout
 * {@code [HH:mm:ss.SSS] [thread/LEVEL] [logger]: message} (file layout with
 * ANSI stripped). Messages are written verbatim — no placeholder expansion is
 * ever performed ({@code nolookups} discipline).
 */
public record LogRecord(long millis, String thread, String logger, LogLevel level,
                        String message, String stack) {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /** Formats the record as a single textual log line (stack appended after it). */
    public String format() {
        StringBuilder sb = new StringBuilder(64 + message.length());
        sb.append('[').append(makeTime(millis)).append("] [")
          .append(thread).append('/').append(level.name()).append("] [")
          .append(logger).append("]: ").append(message);
        if (stack != null && !stack.isEmpty()) {
            sb.append('\n').append(stack);
        }
        return sb.toString();
    }

    /** Time-of-day rendering, thread-safe (formatter is immutable and safe). */
    private static String makeTime(long millis) {
        return TIME.format(java.time.Instant.ofEpochMilli(millis)
                .atZone(java.time.ZoneId.systemDefault()));
    }
}