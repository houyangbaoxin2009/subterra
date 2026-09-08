package io.toterra.subterra.engine.log;

/**
 * Output destination for {@link LogRecord}s (file, future remote, sink tests).
 * Implementations must never throw on {@link #accept}; failures degrade
 * internally (see {@link FileLogSink}) so logging can never break the game.
 */
public interface LogSink {

    void accept(LogRecord record);
}