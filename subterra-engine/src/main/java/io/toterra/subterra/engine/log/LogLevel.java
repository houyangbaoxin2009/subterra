package io.toterra.subterra.engine.log;

/**
 * Log severity levels, following the standard
 * {@code TRACE < DEBUG < INFO < WARN < ERROR < FATAL} ordering (subset without
 * TRACE, which Subterra does not emit). Comparison is by numeric order, so
 * {@code WARN.atLeast(INFO)} is true and {@code DEBUG.atLeast(INFO)} is false.
 */
public enum LogLevel {

    DEBUG(10),
    INFO(20),
    WARN(30),
    ERROR(40),
    FATAL(50);

    private final int order;

    LogLevel(int order) {
        this.order = order;
    }

    /** True when this level is at least as severe as {@code other}. */
    public boolean atLeast(LogLevel other) {
        return this.order >= other.order;
    }

    /**
     * Case-insensitive parse; null returned for unknown names so callers can
     * fall back to a default instead of propagating exceptions.
     */
    public static LogLevel of(String name) {
        if (name == null) {
            return null;
        }
        for (LogLevel lv : values()) {
            if (lv.name().equalsIgnoreCase(name.trim())) {
                return lv;
            }
        }
        return null;
    }
}