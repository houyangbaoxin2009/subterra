// Ported from ServerCore (github.com/Wesley1808/ServerCore), MIT (c) Wesley1808.
package io.toterra.subterra.optim.server.dynamic;

/**
 * A {@link Setting} linked to the previous and next tuning steps so the
 * dynamic adjuster can walk the chain. A setting only changes when it has
 * room to move toward its configured {@code min}/{@code max} and has been
 * visited in the right order.
 */
public final class LinkedSetting {
    private final Setting setting;
    private LinkedSetting prev;
    private LinkedSetting next;

    public LinkedSetting(Setting config) {
        this.setting = config;
    }

    public void initialize(LinkedSetting prev, LinkedSetting next) {
        this.prev = prev;
        this.next = next;
    }

    public boolean shouldRun(int count) {
        return this.setting.interval() > 0 && count % this.setting.interval() == 0;
    }

    public boolean modify(boolean increase, DynamicManager manager) {
        int value = this.newValue(increase);
        if (this.shouldModify(value)) {
            this.setting.dynamicSetting().set(value, manager);
            return true;
        }
        return false;
    }

    private boolean shouldModify(int value) {
        int compared = Integer.compare(value, this.setting.dynamicSetting().get());
        return compared != 0
               && (compared < 0 || (!this.isMaximum() && (this.next == null || this.next.isMaximum())))
               && (compared > 0 || (!this.isMinimum() && (this.prev == null || this.prev.isMinimum())));
    }

    private int newValue(boolean increase) {
        int current = this.setting.dynamicSetting().get();
        return increase
                ? Math.min(current + this.setting.increment(), this.setting.max())
                : Math.max(current - this.setting.increment(), this.setting.min());
    }

    private boolean isMinimum() {
        return this.setting.dynamicSetting().get() <= this.setting.min();
    }

    private boolean isMaximum() {
        return this.setting.dynamicSetting().get() >= this.setting.max();
    }
}