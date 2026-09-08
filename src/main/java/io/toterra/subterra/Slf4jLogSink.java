package io.toterra.subterra;

import io.toterra.subterra.engine.log.LogHub;
import io.toterra.subterra.engine.log.LogRecord;
import io.toterra.subterra.engine.log.LogSink;
import org.slf4j.LoggerFactory;

/**
 * Bridges {@code engine.log} records into the Minecraft log
 * ({@code logs/latest.log}) via SLF4J, so Subterra logging and MC logging share
 * one timeline. Registered at boot; optional Subterra file sinks add a
 * separate rolling file when {@code log.td} enables them.
 */
public final class Slf4jLogSink implements LogSink {

    private static final org.slf4j.Logger LOG =
            LoggerFactory.getLogger("subterra");

    @Override
    public void accept(LogRecord record) {
        String msg = '[' + record.logger() + "] " + record.message();
        switch (record.level()) {
            case DEBUG -> LOG.debug(msg);
            case INFO -> LOG.info(msg);
            case WARN -> LOG.warn(msg, stackOf(record));
            case ERROR -> LOG.error(msg, stackOf(record));
            case FATAL -> LOG.error(msg, stackOf(record));
        }
    }

    private static Exception stackOf(LogRecord record) {
        return record.stack() == null ? null : new Exception(record.stack());
    }

    /** Convenience for boot-time wiring. */
    public static Slf4jLogSink register() {
        Slf4jLogSink sink = new Slf4jLogSink();
        LogHub.addSink(sink);
        return sink;
    }
}