// Ported from ServerCore (github.com/Wesley1808/ServerCore), MIT (c) Wesley1808.
package io.toterra.subterra.optim.server.dynamic;

import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The live value of each dynamically-tuned knob. ServerCore drives these off
 * the measured average tick time: overloaded servers decrease the linked
 * settings (in the order configured in {@code dynamic.settings}), healthy
 * servers increase them again (in reverse order). See the linked
 * {@code DynamicSetting} rows for the exact semantics.
 */
public enum DynamicSetting {
    MOBCAP_PERCENTAGE(0, 1024, 100, true, (manager, value) -> DynamicManager.modifyMobcaps(value)),
    CHUNK_TICK_DISTANCE(2, 256, 10, true, null),
    SIMULATION_DISTANCE(2, 256, 10, false, DynamicManager::modifySimulationDistance),
    VIEW_DISTANCE(2, 256, 10, false, DynamicManager::modifyViewDistance);

    private final BiConsumer<DynamicManager, Integer> onChanged;
    private final int minimumBound;
    private final int maximumBound;
    private final int defaultValue;
    private final boolean requiresInit;
    private boolean initialized;
    private int maxValue;
    private int value;

    DynamicSetting(int minimumBound, int maximumBound, int defaultValue, boolean requiresInit, BiConsumer<DynamicManager, Integer> onChanged) {
        this.minimumBound = minimumBound;
        this.maximumBound = maximumBound;
        this.defaultValue = defaultValue;
        this.requiresInit = requiresInit;
        this.onChanged = onChanged;
        this.maxValue = defaultValue;
        this.value = defaultValue;
    }

    /** Applies the configured default values, then initializes any unset settings. */
    static void initDefaultValues(DynamicConfig config, DynamicManager manager) {
        Map<DynamicSetting, Integer> defaultValues = config.defaultValues();
        defaultValues.forEach((setting, settingValue) -> {
            int clampedValue = Mth.clamp(settingValue, setting.minimumBound, setting.maximumBound);
            setting.set(clampedValue, manager);
        });

        // Ensure dynamic setting values are always initialized.
        for (DynamicSetting setting : values()) {
            if (setting.requiresInit && !defaultValues.containsKey(setting)) {
                setting.notifyChanged(manager);
            }
        }
    }

    /** Recomputes the upper bound reached by the settings chain and seeds uninitialized values. */
    static void recalculateValues(List<Setting> settings) {
        for (DynamicSetting setting : values()) {
            setting.maxValue = 0;
        }

        for (Setting setting : settings) {
            DynamicSetting dynamicSetting = setting.dynamicSetting();
            int max = setting.max();
            if (max > dynamicSetting.maxValue) {
                dynamicSetting.maxValue = max;
            }
        }

        for (DynamicSetting setting : values()) {
            if (setting.maxValue <= 0) {
                setting.maxValue = setting.defaultValue;
            }

            if (!setting.initialized) {
                setting.set(setting.maxValue, null);
                setting.initialized = true;
            }
        }
    }

    static void resetAll() {
        for (DynamicSetting setting : values()) {
            setting.initialized = false;
        }
    }

    public void set(int value, @Nullable DynamicManager manager) {
        this.value = value;
        this.notifyChanged(manager);
    }

    void notifyChanged(@Nullable DynamicManager manager) {
        if (this.onChanged != null && manager != null) {
            this.onChanged.accept(manager, this.value);
        }
    }

    public int get() {
        return this.value;
    }

    public int getLowerBound() {
        return this.minimumBound;
    }

    public int getUpperBound() {
        return this.maximumBound;
    }

    public int getDefaultValue() {
        return this.defaultValue;
    }
}