// Ported from mc-smoothboot (github.com/UltimateBoomer/mc-smoothboot), MIT (c) UltimateBoomer.
package io.toterra.subterra.optim.server.smoothboot;

import io.toterra.subterra.config.Td;
import io.toterra.subterra.config.TdTable;
import io.toterra.subterra.config.TdValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Smooth Boot tuning, loaded from td (see {@code config/subterra/smoothboot.td})
 * via subterra-config. Unknown or invalid fields fall back to defaults so a
 * broken config file can never take the worker tuning down.
 *
 * <pre>{@code
 * [
 *   main_threads = 3,
 *   io_threads = 1,
 *   game_priority = 5,
 *   bootstrap_priority = 1,
 *   main_priority = 1,
 *   io_priority = 1,
 *   integrated_server_priority = 5,
 * ]
 * }</pre>
 */
public final class SmoothBootConfig {

    public static final int MAX_THREADS = 255;

    private final int mainThreads;
    private final int ioThreads;
    private final int gamePriority;
    private final int bootstrapPriority;
    private final int mainPriority;
    private final int ioPriority;
    private final int integratedServerPriority;

    private SmoothBootConfig(int mainThreads, int ioThreads, int gamePriority, int bootstrapPriority,
                             int mainPriority, int ioPriority, int integratedServerPriority) {
        this.mainThreads = mainThreads;
        this.ioThreads = ioThreads;
        this.gamePriority = gamePriority;
        this.bootstrapPriority = bootstrapPriority;
        this.mainPriority = mainPriority;
        this.ioPriority = ioPriority;
        this.integratedServerPriority = integratedServerPriority;
    }

    /** Upstream defaults: main = clamp(cpus - 1, 1, 255), io = 1, game/integrated 5, bootstrap/main/io 1. */
    public static SmoothBootConfig defaults() {
        int mainThreads = clampInt(Runtime.getRuntime().availableProcessors() - 1, 1, MAX_THREADS);
        return new SmoothBootConfig(mainThreads, 1, 5, 1, 1, 1, 5);
    }

    /**
     * Reads {@code config/subterra/smoothboot.td} under {@code gameDir};
     * falls back to defaults on absence, malformed input or invalid values.
     */
    public static SmoothBootConfig load(Path gameDir) {
        Path config = gameDir.resolve("config/subterra/smoothboot.td");
        if (!Files.isRegularFile(config)) {
            return defaults();
        }
        try {
            String text = Files.readString(config, StandardCharsets.UTF_8);
            if (text.isBlank()) {
                return defaults();
            }
            TdTable table = Td.parse(text); // malformed input raises; caught below
            return fromTd(table);
        } catch (IllegalArgumentException | IOException e) {
            SmoothBoot.LOGGER.warn("Smooth Boot: {} ignored ({}), using defaults", config, e);
            return defaults();
        }
    }

    static SmoothBootConfig fromTd(TdTable root) {
        SmoothBootConfig dflt = defaults();
        int mainThreads = threadCount(root.get("main_threads"), dflt.mainThreads);
        int ioThreads = threadCount(root.get("io_threads"), dflt.ioThreads);
        int gamePriority = priority(root.get("game_priority"), dflt.gamePriority);
        int bootstrapPriority = priority(root.get("bootstrap_priority"), dflt.bootstrapPriority);
        int mainPriority = priority(root.get("main_priority"), dflt.mainPriority);
        int ioPriority = priority(root.get("io_priority"), dflt.ioPriority);
        int integratedServerPriority = priority(root.get("integrated_server_priority"), dflt.integratedServerPriority);
        return new SmoothBootConfig(mainThreads, ioThreads, gamePriority, bootstrapPriority,
                mainPriority, ioPriority, integratedServerPriority);
    }

    /** Thread counts are validated &gt;= 1 (upstream validate()); bad/absent values fall back to defaults. */
    private static int threadCount(TdValue v, int dflt) {
        if (v != null) {
            long value = v.asInt();
            if (value >= 1) {
                return (int) Math.min(value, Integer.MAX_VALUE);
            }
        }
        return dflt;
    }

    /** Priorities are clamped to 1..10 (upstream validate()). */
    private static int priority(TdValue v, int dflt) {
        return v != null ? clampInt(v.asInt(), 1, 10) : dflt;
    }

    private static int clampInt(long value, int min, int max) {
        return (int) Math.max(min, Math.min(value, max));
    }

    public int mainThreads() {
        return mainThreads;
    }

    public int ioThreads() {
        return ioThreads;
    }

    public int gamePriority() {
        return gamePriority;
    }

    public int bootstrapPriority() {
        return bootstrapPriority;
    }

    public int mainPriority() {
        return mainPriority;
    }

    public int ioPriority() {
        return ioPriority;
    }

    public int integratedServerPriority() {
        return integratedServerPriority;
    }

    @Override
    public String toString() {
        return "SmoothBootConfig{mainThreads=" + mainThreads + ", ioThreads=" + ioThreads
                + ", gamePriority=" + gamePriority + ", bootstrapPriority=" + bootstrapPriority
                + ", mainPriority=" + mainPriority + ", ioPriority=" + ioPriority
                + ", integratedServerPriority=" + integratedServerPriority + '}';
    }
}
