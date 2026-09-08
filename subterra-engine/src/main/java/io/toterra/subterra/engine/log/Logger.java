package io.toterra.subterra.engine.log;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Logging facade, mirroring {@code LoggerFactory.getLogger(name)} usage.
 * Emits through the process hub; the caller pays nothing when a record is
 * below the effective threshold (fast-path drop before formatting).
 */
public final class Logger {

    private final String name;

    private Logger(String name) {
        this.name = name;
    }

    /** Acquires a logger; the name (mod id / module id) is shown in every line. */
    public static Logger get(String name) {
        return new Logger(name);
    }

    public boolean isDebugEnabled() {
        return isEnabled(LogLevel.DEBUG);
    }

    public boolean isEnabled(LogLevel lv) {
        return lv.atLeast(LogHub.hub().threshold());
    }

    public void debug(String msg) {
        log(LogLevel.DEBUG, msg, null);
    }

    public void info(String msg) {
        log(LogLevel.INFO, msg, null);
    }

    public void warn(String msg) {
        log(LogLevel.WARN, msg, null);
    }

    public void error(String msg) {
        log(LogLevel.ERROR, msg, null);
    }

    public void fatal(String msg) {
        log(LogLevel.FATAL, msg, null);
    }

    public void error(String msg, Throwable t) {
        log(LogLevel.ERROR, msg, t);
    }

    public void warn(String msg, Throwable t) {
        log(LogLevel.WARN, msg, t);
    }

    private void log(LogLevel lv, String msg, Throwable t) {
        LogHub hub = LogHub.hub();
        if (!lv.atLeast(hub.threshold())) {
            return;
        }
        String stack = t == null ? null : stackOf(t);
        hub.log(new LogRecord(System.currentTimeMillis(),
                Thread.currentThread().getName(), name, lv, msg, stack));
    }

    private static String stackOf(Throwable t) {
        StringWriter sw = new StringWriter(256);
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}