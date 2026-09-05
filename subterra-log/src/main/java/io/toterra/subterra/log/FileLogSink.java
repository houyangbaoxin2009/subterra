package io.toterra.subterra.log;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * File-backed {@link LogSink} following the Paper/NeoForge rolling policy:
 * a new timestamped file per launch ({@code OnStartupTriggeringPolicy}),
 * retaining at most {@code keep} files ({@code DefaultRolloverStrategy max}).
 * Writes are UTF-8, ANSI-free, and never throw into the caller — on I/O failure
 * the message degrades to stderr instead of breaking the game loop.
 */
public final class FileLogSink implements LogSink, AutoCloseable {

    private static final String PREFIX = "subterra-";
    private static final String SUFFIX = ".log";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final Path file;
    private final BufferedWriter writer;

    public FileLogSink(Path dir, int keep) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot create log dir: " + dir, e);
        }
        this.file = dir.resolve(PREFIX + STAMP.format(java.time.LocalDateTime.now()) + SUFFIX);
        try {
            this.writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot open log file: " + file, e);
        }
        prune(dir, keep);
    }

    /** The file this sink writes to. */
    public Path file() {
        return file;
    }

    /** Keeps only the {@code keep} most recent {@code subterra-*.log} files. */
    private static void prune(Path dir, int keep) {
        List<Path> logs = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, PREFIX + "*" + SUFFIX)) {
            for (Path p : ds) {
                logs.add(p);
            }
        } catch (IOException ignored) {
            return;
        }
        logs.sort(Path::compareTo); // timestamped names sort chronologically
        int excess = logs.size() - Math.max(1, keep);
        for (int i = 0; i < excess; i++) {
            try {
                Files.deleteIfExists(logs.get(i));
            } catch (IOException ignored) {
                // prune is best-effort; never spill into the log path
            }
        }
    }

    @Override
    public void accept(LogRecord record) {
        try {
            synchronized (writer) {
                writer.write(record.format());
                writer.newLine();
                writer.flush(); // crash-safe: dump always sees the last lines
            }
        } catch (IOException e) {
            System.err.println("[subterra-log] file sink write failed: " + e);
        }
    }

    @Override
    public void close() {
        try {
            synchronized (writer) {
                writer.close();
            }
        } catch (IOException ignored) {
        }
    }
}