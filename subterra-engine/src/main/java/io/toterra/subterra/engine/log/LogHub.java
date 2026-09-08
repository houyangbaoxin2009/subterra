package io.toterra.subterra.engine.log;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Process-wide logging hub: holds the effective threshold, the crash tail ring
 * and the registered sinks. {@link Logger} delegates here. Thread-safe; a hub
 * can be re-configured for tests (old sinks are closed when they are closable).
 */
public final class LogHub {

    private static volatile LogHub active;

    private final LogLevel threshold;
    private final LogRing ring;
    private final List<LogSink> sinks = new CopyOnWriteArrayList<>();

    private LogHub(LogLevel threshold, int ringSize) {
        this.threshold = threshold;
        this.ring = new LogRing(ringSize);
    }

    /** The process-wide hub; lazily defaulted (INFO, 1024) until configured. */
    public static LogHub hub() {
        LogHub h = active;
        if (h == null) {
            synchronized (LogHub.class) {
                h = active;
                if (h == null) {
                    h = new LogHub(LogConfig.DEFAULT_LEVEL, LogConfig.DEFAULT_RING_SIZE);
                    active = h;
                }
            }
        }
        return h;
    }

    /**
     * Replaces the active hub. Previously registered sinks of the old hub are
     * closed (no-op for non-closable sinks). Idempotent; safe to call more than
     * once (tests).
     */
    public static void configure(LogConfig cfg) {
        LogHub old;
        LogHub fresh = new LogHub(cfg.level(), cfg.ringSize());
        synchronized (LogHub.class) {
            old = active;
            active = fresh;
        }
        if (old != null) {
            old.closeSinks();
        }
    }

    public static void addSink(LogSink sink) {
        hub().sinks.add(sink);
    }

    public static void removeSink(LogSink sink) {
        hub().sinks.remove(sink);
    }

    /** True when {@link #configure} has been called at least once. */
    public static boolean isConfigured() {
        return active != null;
    }

    public LogLevel threshold() {
        return threshold;
    }

    public LogRing ring() {
        return ring;
    }

    /** Routes a record into the ring and every sink. */
    public void log(LogRecord record) {
        ring.append(record);
        if (!sinks.isEmpty()) {
            for (LogSink s : sinks) {
                try {
                    s.accept(record);
                } catch (RuntimeException e) {
                    // a defective sink must never break logging
                    System.err.println("[subterra-log] sink error: " + e);
                }
            }
        }
    }

    public List<LogRecord> snapshot() {
        return ring.snapshot();
    }

    /** Closes all registered sinks (used at shutdown and before dump). */
    public void closeSinks() {
        for (LogSink s : sinks) {
            if (s instanceof AutoCloseable c) {
                try {
                    c.close();
                } catch (Exception ignored) {
                }
            }
        }
        sinks.clear();
    }
}