package io.toterra.subterra.runtime.optim.server.teleport.shell;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.optim.server.teleport.RtpPlanner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * p.2.31.1 随机传送 td 配置（零 json）：{@code config/subterra/rtp.td} 根键
 * {@code rtp}，缺省即缺省值（缺省恒等：关闭或全缺省均不改行为）；非法值确定性
 * 拒绝（告警并保持当前态/缺省态），与 itemban 配置同构。
 *
 * <p>The p.2.31.1 random-teleport td config (zero json): {@code config/subterra/rtp.td}
 * with the root key {@code rtp}; defaults apply when absent (identity by default);
 * illegal values are rejected deterministically (warn and keep the current/default
 * state), mirroring the itemban config.
 */
public final class RtpConfig {

    /** 缺省值。 / Defaults. */
    public static final boolean DEFAULT_ENABLED = true;
    public static final int DEFAULT_MIN_RADIUS = 500;
    public static final int DEFAULT_MAX_RADIUS = 5000;
    public static final int DEFAULT_ATTEMPTS = 64;
    public static final int DEFAULT_MIN_Y = -60;
    public static final int DEFAULT_MAX_Y = 320;
    public static final int DEFAULT_Y_OFFSET = 1;
    public static final int DEFAULT_COMMAND_PERMISSION = 0;
    public static final int DEFAULT_COOLDOWN_SECONDS = 30;

    private static volatile boolean enabled = DEFAULT_ENABLED;
    private static volatile int minRadius = DEFAULT_MIN_RADIUS;
    private static volatile int maxRadius = DEFAULT_MAX_RADIUS;
    private static volatile int attempts = DEFAULT_ATTEMPTS;
    private static volatile int minY = DEFAULT_MIN_Y;
    private static volatile int maxY = DEFAULT_MAX_Y;
    private static volatile int yOffset = DEFAULT_Y_OFFSET;
    private static volatile int commandPermission = DEFAULT_COMMAND_PERMISSION;
    private static volatile int cooldownSeconds = DEFAULT_COOLDOWN_SECONDS;
    private static volatile String lastState = "default";

    private RtpConfig() {
    }

    /** 载入 td 配置；缺文件用缺省；解析/取值非法确定性拒绝（告警 + 保持缺省）。 / Loads the td config; deterministic rejection on illegal values. */
    public static void load(Path gameDir) {
        Path configFile = gameDir.resolve("config/subterra/rtp.td");
        try {
            if (Files.isRegularFile(configFile)) {
                String text = Files.readString(configFile, StandardCharsets.UTF_8);
                if (!text.isBlank()) {
                    TdTable root = Td.parse(text);
                    TdValue v = root.get("rtp");
                    if (v instanceof TdTable table) {
                        apply(table);
                        lastState = "file";
                        return;
                    }
                }
            }
            defaults();
            lastState = "default";
        } catch (IllegalArgumentException | IOException e) {
            RtpRuntime.LOGGER.warn("{} config rejected deterministically ({}); using defaults", RtpRuntime.MARKER, e.getMessage());
            defaults();
            lastState = "default";
        }
    }

    private static void apply(TdTable table) {
        boolean en = bool(table.get("enabled"), DEFAULT_ENABLED);
        int min = intOf(table.get("min_radius"), DEFAULT_MIN_RADIUS);
        int max = intOf(table.get("max_radius"), DEFAULT_MAX_RADIUS);
        int att = intOf(table.get("attempts"), DEFAULT_ATTEMPTS);
        int lowY = intOf(table.get("min_y"), DEFAULT_MIN_Y);
        int highY = intOf(table.get("max_y"), DEFAULT_MAX_Y);
        int off = intOf(table.get("y_offset"), DEFAULT_Y_OFFSET);
        int perm = intOf(table.get("command_permission"), DEFAULT_COMMAND_PERMISSION);
        int cd = intOf(table.get("cooldown_seconds"), DEFAULT_COOLDOWN_SECONDS);
        // 规范校验经 Params 构造器：任一非法 → 整体确定性拒绝，回到缺省。
        // Canonical validation via the Params constructor: any illegal value rejects the whole file.
        new RtpPlanner.Params(min, max, att, lowY, highY, off);
        if (perm < 0 || perm > 4) {
            throw new IllegalArgumentException("command_permission must be within [0, 4] (got " + perm + ")");
        }
        if (cd < 0 || cd > 3600) {
            throw new IllegalArgumentException("cooldown_seconds must be within [0, 3600] (got " + cd + ")");
        }
        enabled = en;
        minRadius = min;
        maxRadius = max;
        attempts = att;
        minY = lowY;
        maxY = highY;
        yOffset = off;
        commandPermission = perm;
        cooldownSeconds = cd;
    }

    private static void defaults() {
        enabled = DEFAULT_ENABLED;
        minRadius = DEFAULT_MIN_RADIUS;
        maxRadius = DEFAULT_MAX_RADIUS;
        attempts = DEFAULT_ATTEMPTS;
        minY = DEFAULT_MIN_Y;
        maxY = DEFAULT_MAX_Y;
        yOffset = DEFAULT_Y_OFFSET;
        commandPermission = DEFAULT_COMMAND_PERMISSION;
        cooldownSeconds = DEFAULT_COOLDOWN_SECONDS;
    }

    /** 当前规划参数。 / The current planning params. */
    public static RtpPlanner.Params params() {
        return new RtpPlanner.Params(minRadius, maxRadius, attempts, minY, maxY, yOffset);
    }

    public static boolean enabled() {
        return enabled;
    }

    public static int commandPermission() {
        return commandPermission;
    }

    public static int cooldownSeconds() {
        return cooldownSeconds;
    }

    /** 配置来源（{@code default} / {@code file}）。 / The config source. */
    public static String lastState() {
        return lastState;
    }

    /** 确定性摘要（marker 用）。 / The deterministic summary (for markers). */
    public static String summary() {
        return "enabled=" + enabled + " " + params().render() + " perm=" + commandPermission
                + " cooldown=" + cooldownSeconds + "s state=" + lastState;
    }

    private static boolean bool(TdValue v, boolean fallback) {
        return v != null ? v.asBool() : fallback;
    }

    private static int intOf(TdValue v, int fallback) {
        return v != null ? (int) v.asInt() : fallback;
    }
}
