// Ported from ServerCore (github.com/Wesley1808/ServerCore), MIT (c) Wesley1808.
package io.toterra.subterra.optim.server.dynamic;

import io.toterra.subterra.config.Td;
import io.toterra.subterra.config.TdTable;
import io.toterra.subterra.config.TdValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dynamic view/simulation-distance config, loaded from td (see
 * {@code config/subterra/servercore.td}) via subterra-config. Unknown or
 * invalid fields fall back to defaults so a broken config file can never take
 * the port down.
 *
 * <p>Schema (new top-level {@code dynamic} table in the same file):
 * <pre>{@code
 * [
 *   dynamic = [
 *     enabled = false,          // toggle the per-tick performance checks.
 *     target_ms = 35,           // average MSPT to target; the adjuster steps in when
 *                               //   avg < target-5 (increase) or avg > target+5 (decrease).
 *     default_values = [        // optional: initial value for each knob.
 *       chunk_tick_distance = 10,
 *       mobcap_percentage = 100,
 *     ],
 *     settings = [              // knobs decreased in order when overloaded, increased
 *                               //   in reverse when healthy.
 *       [ setting = "chunk_tick_distance", max = 10, min = 6, increment = 1, interval = 15 ],
 *       [ setting = "mobcap_percentage",   max = 100, min = 50, increment = 10, interval = 15 ],
 *       [ setting = "simulation_distance", max = 10, min = 6, increment = 1, interval = 15 ],
 *       [ setting = "chunk_tick_distance", max = 6, min = 2, increment = 1, interval = 15 ],
 *       [ setting = "mobcap_percentage",   max = 50, min = 30, increment = 10, interval = 15 ],
 *       [ setting = "simulation_distance", max = 6, min = 2, increment = 1, interval = 15 ],
 *       [ setting = "view_distance",       max = 10, min = 5, increment = 1, interval = 150 ],
 *     ],
 *   ],
 * ]
 * }</pre>
 * Knob name → {@link DynamicSetting}: {@code view_distance}, {@code
 * simulation_distance}, {@code mobcap_percentage}, {@code chunk_tick_distance}.
 */
public final class DynamicConfig {

    public static final int DEFAULT_TARGET_MSPT = 35;
    public static final boolean DEFAULT_ENABLED = false;

    private static final Map<DynamicSetting, Integer> DEFAULT_DEFAULT_VALUES = new LinkedHashMap<>();
    private static final List<Setting> DEFAULT_SETTINGS = defaultSettings();

    static {
        DEFAULT_DEFAULT_VALUES.put(DynamicSetting.CHUNK_TICK_DISTANCE, DynamicSetting.CHUNK_TICK_DISTANCE.getDefaultValue());
        DEFAULT_DEFAULT_VALUES.put(DynamicSetting.MOBCAP_PERCENTAGE, DynamicSetting.MOBCAP_PERCENTAGE.getDefaultValue());
    }

    private final boolean enabled;
    private final int targetMspt;
    private final Map<DynamicSetting, Integer> defaultValues;
    private final List<Setting> settings;

    private DynamicConfig(boolean enabled, int targetMspt, Map<DynamicSetting, Integer> defaultValues, List<Setting> settings) {
        this.enabled = enabled;
        this.targetMspt = targetMspt;
        this.defaultValues = defaultValues;
        this.settings = settings;
    }

    public static DynamicConfig defaults() {
        return new DynamicConfig(DEFAULT_ENABLED, DEFAULT_TARGET_MSPT, DEFAULT_DEFAULT_VALUES, DEFAULT_SETTINGS);
    }

    /**
     * Reads {@code config/subterra/servercore.td} under {@code gameDir};
     * falls back to defaults on absence, malformed input or invalid values.
     */
    public static DynamicConfig load(Path gameDir) {
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
            DynamicDistance.LOGGER.warn("dynamic distance: {} ignored ({}), using defaults", config, e);
            return defaults();
        }
    }

    static DynamicConfig fromTd(TdTable root) {
        TdValue dynamic = root.get("dynamic");
        if (!(dynamic instanceof TdTable table)) {
            return defaults();
        }
        boolean enabled = enabled(table.get("enabled"));
        int targetMspt = intField(table.get("target_ms"), DEFAULT_TARGET_MSPT, 2, Integer.MAX_VALUE);
        Map<DynamicSetting, Integer> defaultValues = defaultValues(table.get("default_values"));
        List<Setting> settings = settings(table.get("settings"));
        return new DynamicConfig(enabled, targetMspt, defaultValues, settings);
    }

    private static boolean enabled(TdValue v) {
        return v != null ? v.asBool() : DEFAULT_ENABLED;
    }

    private static int intField(TdValue v, int fallback, int min, int max) {
        if (v != null) {
            long value = v.asInt();
            if (value >= min && value <= max) {
                return (int) value;
            }
        }
        return fallback;
    }

    private static Map<DynamicSetting, Integer> defaultValues(TdValue v) {
        if (!(v instanceof TdTable table) || table.isEmpty()) {
            return DEFAULT_DEFAULT_VALUES;
        }
        Map<DynamicSetting, Integer> result = new LinkedHashMap<>();
        for (String key : table.keys()) {
            DynamicSetting setting = DynamicSettingName.byKey(key);
            if (setting == null) {
                continue;
            }
            TdValue raw = table.get(key);
            if (raw != null) {
                int clamped = clampToSetting(raw.asInt(), setting);
                result.put(setting, clamped);
            }
        }
        return result.isEmpty() ? DEFAULT_DEFAULT_VALUES : result;
    }

    private static List<Setting> settings(TdValue v) {
        if (!(v instanceof TdTable table)) {
            return DEFAULT_SETTINGS;
        }
        List<Setting> result = new ArrayList<>();
        for (TdValue element : table.elements()) {
            if (!(element instanceof TdTable s)) {
                continue;
            }
            DynamicSetting setting = DynamicSettingName.byKey(str(s.get("setting")));
            if (setting == null) {
                continue;
            }
            int max = intField(s.get("max"), setting.getDefaultValue(), 1, setting.getUpperBound());
            int min = intField(s.get("min"), setting.getLowerBound(), 0, max);
            int increment = intField(s.get("increment"), 1, 1, max);
            int interval = intField(s.get("interval"), 15, 1, 60000);
            result.add(new Setting(setting, max, min, increment, interval));
        }
        return result.isEmpty() ? DEFAULT_SETTINGS : result;
    }

    private static String str(TdValue v) {
        return v != null ? v.asString() : "";
    }

    private static int clampToSetting(long value, DynamicSetting setting) {
        return (int) Math.min(Math.max(value, setting.getLowerBound()), setting.getUpperBound());
    }

    private static List<Setting> defaultSettings() {
        List<Setting> list = new ArrayList<>();
        list.add(new Setting(DynamicSetting.CHUNK_TICK_DISTANCE, 10, 6, 1, 15));
        list.add(new Setting(DynamicSetting.MOBCAP_PERCENTAGE, 100, 50, 10, 15));
        list.add(new Setting(DynamicSetting.SIMULATION_DISTANCE, 10, 6, 1, 15));
        list.add(new Setting(DynamicSetting.CHUNK_TICK_DISTANCE, 6, 2, 1, 15));
        list.add(new Setting(DynamicSetting.MOBCAP_PERCENTAGE, 50, 30, 10, 15));
        list.add(new Setting(DynamicSetting.SIMULATION_DISTANCE, 6, 2, 1, 15));
        list.add(new Setting(DynamicSetting.VIEW_DISTANCE, 10, 5, 1, 150));
        return list;
    }

    public boolean enabled() {
        return enabled;
    }

    public int targetMspt() {
        return targetMspt;
    }

    public Map<DynamicSetting, Integer> defaultValues() {
        return defaultValues;
    }

    public List<Setting> settings() {
        return settings;
    }

    @Override
    public String toString() {
        return "DynamicConfig{enabled=" + enabled
                + ", targetMspt=" + targetMspt
                + ", defaultValues=" + defaultValues.keySet()
                + ", settings=" + settings.size() + '}';
    }

    /** Maps the td key names of dynamic settings to the enum. */
    private static final class DynamicSettingName {
        private static final Map<String, DynamicSetting> BY_KEY = new LinkedHashMap<>();

        static {
            BY_KEY.put("chunk_tick_distance", DynamicSetting.CHUNK_TICK_DISTANCE);
            BY_KEY.put("mobcap_percentage", DynamicSetting.MOBCAP_PERCENTAGE);
            BY_KEY.put("simulation_distance", DynamicSetting.SIMULATION_DISTANCE);
            BY_KEY.put("view_distance", DynamicSetting.VIEW_DISTANCE);
        }

        static DynamicSetting byKey(String key) {
            return BY_KEY.get(key);
        }

        private DynamicSettingName() {
        }
    }
}