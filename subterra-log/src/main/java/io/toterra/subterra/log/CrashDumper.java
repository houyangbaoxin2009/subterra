package io.toterra.subterra.log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Crash-time diagnostic exporter (the "crash auto-export" contract of the
 * logging module): on an unhandled throwable it writes a self-contained dump —
 * crash header + stack + system info + registered-modules dependency block +
 * the recent log tail from the hub ring — to {@code crash-<timestamp>.txt}.
 * <p>
 * Pure JVM and deterministic; the Minecraft integration later hooks this into
 * the loader crash path. The returned path lets the caller surface it.
 */
public final class CrashDumper {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private CrashDumper() {
    }

    /** Dumps using the process hub and module registry. */
    public static Path dump(Path dir, Throwable t) {
        return dump(dir, t, LogHub.hub(), ModuleReg.registry());
    }

    /** Dumps with explicit sources (tests inject temp dirs / fresh hubs). */
    public static Path dump(Path dir, Throwable t, LogHub hub, ModuleReg reg) {
        String body = render(t, hub, reg);
        Path file = dir.resolve("crash-" + STAMP.format(LocalDateTime.now()) + ".txt");
        try {
            Files.createDirectories(dir);
            Files.writeString(file, body, StandardCharsets.UTF_8);
            return file;
        } catch (Exception e) {
            throw new IllegalArgumentException("cannot write crash dump: " + file, e);
        }
    }

    /**
     * Self-contained diagnostics text (system info + module dependency block +
     * recent log tail) for embedding into a Minecraft crash report via a
     * system-report crash callable. Pure JDK; no exception argument needed.
     */
    public static String diagnosticsBlock() {
        return diagnosticsBlock(LogHub.hub(), ModuleReg.registry());
    }

    /** Diagnostics block with explicit sources (tests inject fresh hubs). */
    public static String diagnosticsBlock(LogHub hub, ModuleReg reg) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("System:\n")
          .append("  java.version=").append(System.getProperty("java.version")).append('\n')
          .append("  java.vm.name=").append(System.getProperty("java.vm.name", "?")).append('\n')
          .append("  os.name=").append(System.getProperty("os.name")).append('\n')
          .append("  os.arch=").append(System.getProperty("os.arch")).append('\n')
          .append("  availableProcessors=").append(Runtime.getRuntime().availableProcessors()).append('\n')
          .append("  maxMemory=").append(Runtime.getRuntime().maxMemory()).append('\n')
          .append('\n')
          .append(reg.describe()).append('\n')
          .append('\n')
          .append("Recent log (tail):");
        for (LogRecord r : hub.snapshot()) {
            sb.append('\n').append(r.format());
        }
        return sb.toString();
    }

    private static String render(Throwable t, LogHub hub, ModuleReg reg) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("---- Subterra Diagnostic Dump ----\n");
        sb.append("Time: ").append(LocalDateTime.now()).append('\n');
        sb.append("Description: ").append(t.getClass().getName());
        if (t.getMessage() != null) {
            sb.append(": ").append(t.getMessage());
        }
        sb.append('\n');
        sb.append("Stack:\n").append(stackOf(t));
        sb.append('\n').append(diagnosticsBlock(hub, reg)).append('\n');
        return sb.toString();
    }

    private static String stackOf(Throwable t) {
        StringWriter sw = new StringWriter(512);
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}