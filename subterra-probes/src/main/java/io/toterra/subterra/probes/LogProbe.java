package io.toterra.subterra.probes;

import io.toterra.subterra.config.Td;
import io.toterra.subterra.config.TdTable;
import io.toterra.subterra.log.CrashDumper;
import io.toterra.subterra.log.FileLogSink;
import io.toterra.subterra.log.LogConfig;
import io.toterra.subterra.log.LogHub;
import io.toterra.subterra.log.LogLevel;
import io.toterra.subterra.log.LogRecord;
import io.toterra.subterra.log.LogRing;
import io.toterra.subterra.log.Logger;
import io.toterra.subterra.log.ModuleReg;
import io.toterra.subterra.log.analysis.AiAnalyzer;
import io.toterra.subterra.log.analysis.Analyzers;
import io.toterra.subterra.log.analysis.ErrorAnalyzer;
import io.toterra.subterra.log.analysis.RulesAnalyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Deterministic acceptance probe for the logging module (p.1.6), including the
 * crash-time diagnostics and the pluggable error analyzers. Pure JVM — no
 * Minecraft runtime. Exit 0 = PASS, exit 1 = FAIL (gates acceptance).
 */
public final class LogProbe {

    private LogProbe() {
    }

    private static int failures = 0;

    private static void check(String name, boolean ok) {
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    public static void main(String[] args) throws IOException {
        levelFiltering();
        ring();
        fileSink();
        moduleCycles();
        crashDumper();
        logConfig();
        rulesAnalyzer();
        analyzerResolution();

        if (failures == 0) {
            System.out.println("[LogProbe] PASS (logging, diagnostics, analyzers)");
            System.exit(0);
        } else {
            System.out.println("[LogProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }

    // ---------- sections ----------

    private static void levelFiltering() {
        LogHub.configure(LogConfig.fromTd(
                Td.parse("[\n  level = \"INFO\",\n  ring_size = 32,\n]\n")));
        Logger log = Logger.get("probe");
        log.debug("dropped debug");   // below INFO threshold
        log.warn("kept warn");
        log.error("kept error", new IllegalStateException("boom"));
        List<LogRecord> snap = LogHub.hub().snapshot();
        check("threshold drops debug", snap.stream().noneMatch(r -> r.message().equals("dropped debug")));
        check("threshold keeps warn", snap.stream().anyMatch(r -> r.message().equals("kept warn") && r.level() == LogLevel.WARN));
        check("record carries stack", snap.stream().anyMatch(r -> r.message().equals("kept error")
                && r.stack() != null && r.stack().contains("IllegalStateException")));
        check("record format header", snap.stream().allMatch(r -> r.format().matches(
                "(?s)\\A\\[[0-9:.]+\\] \\[[^/]+/[A-Z]+\\] \\[probe\\]: .*")));
    }

    private static void ring() {
        LogRing ring = new LogRing(3);
        check("ring empty", ring.size() == 0 && ring.snapshot().isEmpty());
        for (int i = 0; i < 5; i++) {
            ring.append(new LogRecord(i, "t", "l", LogLevel.INFO, "m" + i, null));
        }
        List<LogRecord> snap = ring.snapshot();
        check("ring overwrites oldest", snap.size() == 3
                && snap.get(0).millis() == 2 && snap.get(2).millis() == 4);
    }

    private static void fileSink() throws IOException {
        Path dir = Files.createTempDirectory("probe-log");
        try {
            FileLogSink a = new FileLogSink(dir, 2);
            Path first = a.file();
            a.accept(new LogRecord(1, "t", "l", LogLevel.INFO, "line one", null));
            a.accept(new LogRecord(2, "t", "l", LogLevel.ERROR, "line two", null));
            a.close();
            String text = Files.readString(first);
            check("sink writes rows", text.lines().count() >= 2
                    && text.lines().allMatch(l -> l.startsWith("[")));
            // Writing two more launches prunes back to keep=2.
            for (int i = 0; i < 2; i++) {
                FileLogSink s = new FileLogSink(dir, 2);
                s.close();
            }
            long files;
            try (Stream<Path> ds = Files.list(dir)) {
                files = ds.filter(p -> p.getFileName().toString().startsWith("subterra-")).count();
            }
            check("sink prunes to keep", files == 2);
        } finally {
            deleteTree(dir);
        }
    }

    private static boolean hasCycle(List<List<String>> cycles, String... names) {
        for (List<String> c : cycles) {
            if (c.size() == names.length) {
                boolean all = true;
                for (String n : names) {
                    all &= c.contains(n);
                }
                if (all) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void moduleCycles() {
        ModuleReg reg = ModuleReg.registry();
        check("cycles empty", reg.dependencyCycles().isEmpty());
        reg.register("z-mod", "1.0", "z-mod"); // self loop
        check("self-loop detected", hasCycle(reg.dependencyCycles(), "z-mod"));
        reg.register("m-mod", "1.0", "n-mod");
        reg.register("n-mod", "1.0", "m-mod"); // m -> n -> m
        check("pair cycle detected", hasCycle(reg.dependencyCycles(), "m-mod", "n-mod"));
        reg.register("a-mod", "1.0", "b-mod");
        reg.register("b-mod", "1.0"); // acyclics: a -> b, no edge back
        check("acyclic adds no cycle", reg.dependencyCycles().stream()
                .noneMatch(c -> c.contains("a-mod") || c.contains("b-mod")));
        String desc = reg.describe();
        check("describe lists modules", desc.contains("Modules:")
                && desc.contains("m-mod") && desc.contains("n-mod"));
    }

    private static void crashDumper() throws IOException {
        Path dir = Files.createTempDirectory("probe-crash");
        try {
            LogHub.configure(LogConfig.defaults());
            Logger.get("probe").error("tail line for dump");
            ModuleReg reg = ModuleReg.registry();
            reg.register("mod-x", "v9", "mod-y");
            reg.register("mod-y", "v8");
            Path dump = CrashDumper.dump(dir, new RuntimeException("kaboom"), LogHub.hub(), reg);
            String text = Files.readString(dump);
            check("dump written", Files.exists(dump));
            check("dump has exception", text.contains("kaboom") && text.contains("Stack:"));
            check("dump has modules block", text.contains("Modules:") && text.contains("mod-x v9"));
            check("dump has log tail", text.contains("tail line for dump"));
            String block = CrashDumper.diagnosticsBlock(LogHub.hub(), reg);
            check("diagnostics block modules", block.contains("Modules:") && block.contains("mod-x v9"));
            check("diagnostics block tail", block.contains("Recent log (tail):") && block.contains("tail line for dump"));
        } finally {
            deleteTree(dir);
        }
    }

    private static void logConfig() {
        TdTable full = Td.parse("""
                type tie<data>
                log = [
                  level = "WARN",
                  ring_size = 64,
                  file = [
                    enabled = true,
                    dir = "logs/x",
                    keep = 3,
                  ],
                  analysis = [
                    enabled = true,
                    device = "NPU",
                    model = "tiny",
                  ],
                ]
                """);
        LogConfig cfg = LogConfig.fromTd(full);
        check("cfg level", cfg.level() == LogLevel.WARN);
        check("cfg ring", cfg.ringSize() == 64);
        check("cfg file", cfg.fileEnabled() && cfg.fileDir().endsWith("logs/x") && cfg.fileKeep() == 3);
        check("cfg analysis", cfg.analysisEnabled() && "NPU".equals(cfg.analysisDevice())
                && "tiny".equals(cfg.analysisModel()));

        LogConfig bare = LogConfig.fromTd(Td.parse("[\n  x = 1,\n]\n"));
        check("cfg missing -> defaults", bare.level() == LogLevel.INFO && bare.ringSize() == 1024
                && !bare.fileEnabled() && !bare.analysisEnabled());

        LogConfig bad = LogConfig.fromTd(Td.parse("[\n  level = \"bogus\",\n  ring_size = -5,\n]\n"));
        check("cfg invalid -> defaulted", bad.level() == LogLevel.INFO && bad.ringSize() == 1024);
    }

    private static void rulesAnalyzer() {
        RulesAnalyzer ra = new RulesAnalyzer();
        check("rules name", "rules".equals(ra.name()));
        check("rule oom", "out_of_memory".equals(
                ra.analyze("java.lang.OutOfMemoryError: Java heap space").category()));
        check("rule port", "port_in_use".equals(
                ra.analyze("java.net.BindException: Address already in use: bind").category()));
        check("rule mixin", "mixin_conflict".equals(
                ra.analyze("Invalid mixin from mod X: could not resolve target").category()));
        check("rule npe", "null_pointer".equals(
                ra.analyze("Caused by: java.lang.NullPointerException").category()));
        check("rule stack", "stack_overflow".equals(
                ra.analyze("java.lang.StackOverflowError").category()));
        check("rule classpath", "classpath_mismatch".equals(
                ra.analyze("java.lang.NoClassDefFoundError: org/example/Something").category()));
        check("rule jvm", "jvm_native".equals(
                ra.analyze("# A fatal error has been detected by the Java Runtime Environment").category()));
        check("rule modload", "mod_load".equals(
                ra.analyze("Failed to load mod 'abc'").category()));
        ErrorAnalyzer.Result unknown = ra.analyze("just some random noise");
        check("rule unknown fallback", "unknown".equals(unknown.category()) && unknown.confidence() == 0.0
                && !unknown.suggestion().isBlank());
    }

    private static void analyzerResolution() {
        LogConfig off = LogConfig.fromTd(Td.parse("[\n  analysis = [ enabled = false ],\n]\n"));
        check("resolve rules when off", Analyzers.resolve(off) instanceof RulesAnalyzer);
        LogConfig on = LogConfig.fromTd(Td.parse(
                "[\n  analysis = [ enabled = true, device = \"GPU\", model = \"mini\" ],\n]\n"));
        ErrorAnalyzer ai = Analyzers.resolve(on);
        check("resolve ai when on", ai instanceof AiAnalyzer && "ai".equals(ai.name()));
        check("ai fields", ai instanceof AiAnalyzer a2 && "GPU".equals(a2.device()) && "mini".equals(a2.model()));
        check("ai not wired collapses to unknown", "unknown".equals(ai.analyze("x").category()));
    }

    // ---------- helpers ----------

    private static void deleteTree(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}