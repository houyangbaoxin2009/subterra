// Ported from ServerCore (github.com/Wesley1808/ServerCore), MIT (c) Wesley1808.
package io.toterra.subterra.runtime.optim.entity.ai;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Villager lobotomization config, loaded from td (see
 * {@code config/subterra/servercore.td}) via engine.config. Unknown or
 * invalid fields fall back to defaults so a broken config file can never take
 * the port down.
 *
 * <pre>{@code
 * [
 *   lobotomize = [
 *     enabled = true,
 *     tick_interval = 100,
 *   ],
 * ]
 * }</pre>
 */
public final class LobotomizeConfig {

    private static final boolean DEFAULT_ENABLED = true;
    private static final int DEFAULT_TICK_INTERVAL = 100;

    private final boolean enabled;
    private final int tickInterval;

    private LobotomizeConfig(boolean enabled, int tickInterval) {
        this.enabled = enabled;
        this.tickInterval = tickInterval;
    }

    public static LobotomizeConfig defaults() {
        return new LobotomizeConfig(DEFAULT_ENABLED, DEFAULT_TICK_INTERVAL);
    }

    /**
     * Reads {@code config/subterra/servercore.td} under {@code gameDir};
     * falls back to defaults on absence, malformed input or invalid values.
     */
    public static LobotomizeConfig load(Path gameDir) {
        Path config = gameDir.resolve("config/subterra/servercore.td");
        if (!Files.isRegularFile(config)) {
            return defaults();
        }
        try {
            String text = Files.readString(config, StandardCharsets.UTF_8);
            if (text.isBlank()) {
                return defaults();
            }
            TdTable root = Td.parse(text); // malformed input raises; caught below
            return fromTd(root);
        } catch (IllegalArgumentException | IOException e) {
            VillagerLobotomize.LOGGER.warn("villager lobotomization: {} ignored ({}), using defaults", config, e);
            return defaults();
        }
    }

    static LobotomizeConfig fromTd(TdTable root) {
        // The config file wraps the feature in a "lobotomize" table.
        TdValue lobotomize = root.get("lobotomize");
        if (!(lobotomize instanceof TdTable table)) {
            return defaults();
        }
        boolean enabled = enabled(table.get("enabled"));
        int tickInterval = tickInterval(table.get("tick_interval"));
        return new LobotomizeConfig(enabled, tickInterval);
    }

    private static boolean enabled(TdValue v) {
        return v != null ? v.asBool() : DEFAULT_ENABLED;
    }

    /** tick_interval is validated &gt;= 1 like upstream; bad/absent values fall back to defaults. */
    private static int tickInterval(TdValue v) {
        if (v != null) {
            long value = v.asInt();
            if (value >= 1) {
                return (int) Math.min(value, Integer.MAX_VALUE);
            }
        }
        return DEFAULT_TICK_INTERVAL;
    }

    public boolean enabled() {
        return enabled;
    }

    public int tickInterval() {
        return tickInterval;
    }

    @Override
    public String toString() {
        return "LobotomizeConfig{enabled=" + enabled + ", tickInterval=" + tickInterval + '}';
    }
}