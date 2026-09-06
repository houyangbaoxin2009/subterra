// Ported from ServerCore (github.com/Wesley1808/ServerCore), MIT (c) Wesley1808.
package io.toterra.subterra.optim.server.dynamic;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.MobCategory;

import java.util.ArrayList;
import java.util.List;

/**
 * Measures the server's average tick time and, when enabled, dynamically
 * adjusts the configured knobs (view/simulation distance, mobcap percentage,
 * chunk-tick distance) to bring it back toward {@code target_ms}. Lifted
 * view/simulation distance changes are applied through the player list, which
 * the {@link PlayerListMixin} mirrors back onto the live {@link DynamicSetting}
 * values.
 */
public class DynamicManager {
    private static final List<LinkedSetting> SETTINGS = new ArrayList<>();

    private final MinecraftServer server;
    private final DynamicConfig config;
    private double averageTickTime;
    private int count;

    public DynamicManager(MinecraftServer server, DynamicConfig config) {
        this.server = server;
        this.config = config;
        reload(config);
        DynamicSetting.initDefaultValues(config, this);
    }

    /** Hooks the per-tick measurement + performance checks (driven by the bootstrap env). */
    public void update() {
        if (this.server.getTickCount() % 20 == 0) {
            this.updateValues();
            if (this.config.enabled()) {
                this.runPerformanceChecks();
            }
        }
    }

    private void updateValues() {
        this.averageTickTime = this.calculateAverageTickTime();
        this.count++;
    }

    protected double calculateAverageTickTime() {
        return this.server.getCurrentSmoothedTickTime();
    }

    private void runPerformanceChecks() {
        final double targetMspt = this.config.targetMspt();
        final boolean decrease = this.averageTickTime > targetMspt + 5;
        final boolean increase = this.averageTickTime < Math.max(targetMspt - 5, 2);

        if (decrease || increase) {
            Iterable<LinkedSetting> ordered = increase ? SETTINGS.reversed() : SETTINGS;
            for (LinkedSetting setting : ordered) {
                if (setting.shouldRun(this.count) && setting.modify(increase, this)) {
                    break;
                }
            }
        }
    }

    public void modifyViewDistance(int distance) {
        this.server.getPlayerList().setViewDistance(distance);
    }

    public void modifySimulationDistance(int distance) {
        this.server.getPlayerList().setSimulationDistance(distance);
    }

    public static void modifyMobcaps(int percentage) {
        final double modifier = percentage / 100F;
        for (MobCategory category : MobCategory.values()) {
            IMobCategory.modifyCapacity(category, modifier);
        }
    }

    public double getAverageTickTime() {
        return this.averageTickTime;
    }

    private static void reload(DynamicConfig config) {
        SETTINGS.clear();

        List<Setting> settings = config.settings();
        DynamicSetting.recalculateValues(settings);

        for (Setting setting : settings) {
            SETTINGS.add(new LinkedSetting(setting));
        }

        for (int i = 0; i < SETTINGS.size(); i++) {
            LinkedSetting linked = SETTINGS.get(i);
            linked.initialize(
                    i == 0 ? null : SETTINGS.get(i - 1), // prev
                    i == SETTINGS.size() - 1 ? null : SETTINGS.get(i + 1) // next
            );
        }
    }
}