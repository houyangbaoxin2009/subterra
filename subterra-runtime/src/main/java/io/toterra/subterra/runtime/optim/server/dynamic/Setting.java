// Ported from ServerCore (github.com/Wesley1808/ServerCore), MIT (c) Wesley1808.
package io.toterra.subterra.runtime.optim.server.dynamic;

/**
 * One entry of the {@code dynamic.settings} chain: a {@link DynamicSetting}
 * knob plus its tuning window. {@link DynamicManager} decreases the linked
 * settings (in list order) when the server is overloaded and increases them
 * (in reverse order) when it is not. Bounds are clamped to the knob's own
 * {@link DynamicSetting#getLowerBound()}/{@link DynamicSetting#getUpperBound()}.
 */
public final class Setting {
    private final DynamicSetting dynamicSetting;
    private final int max;
    private final int min;
    private final int increment;
    private final int interval;

    public Setting(DynamicSetting dynamicSetting, int max, int min, int increment, int interval) {
        this.dynamicSetting = dynamicSetting;
        this.max = max;
        this.min = min;
        this.increment = increment;
        this.interval = interval;
    }

    public DynamicSetting dynamicSetting() {
        return this.dynamicSetting;
    }

    public int max() {
        return Math.min(this.max, this.dynamicSetting.getUpperBound());
    }

    public int min() {
        return Math.max(this.min, this.dynamicSetting.getLowerBound());
    }

    public int increment() {
        return this.increment;
    }

    public int interval() {
        return this.interval;
    }
}