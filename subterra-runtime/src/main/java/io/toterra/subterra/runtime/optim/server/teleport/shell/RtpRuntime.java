package io.toterra.subterra.runtime.optim.server.teleport.shell;

import com.mojang.logging.LogUtils;
import io.toterra.subterra.engine.optim.server.teleport.RtpPlanner;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

/**
 * p.2.31.1 StellarRTP 接线壳：td 配置预载（{@link RtpConfig}）+ {@code /subterra rtp}
 * 命令注册（{@link RtpCommand}）+ 世界种子阶段保证捕获（ServerStarting 捕获、
 * ServerStopped 复位），供确定性规划使用。默认门控 {@code subterra.probe.rtp} 打印
 * 确定性计划渲染（平面 surface=64 纯回调、零区块加载，供 E2E 断言）。
 *
 * <p>The p.2.31.1 StellarRTP wiring shell: td config preload ({@link RtpConfig}) +
 * the {@code /subterra rtp} command registration ({@link RtpCommand}) + phase-guaranteed
 * world-seed capture (captured at ServerStarting, reset at ServerStopped) for the
 * deterministic planner. The default-gated {@code subterra.probe.rtp} prints the
 * canonical plan render over a flat surface callback (pure, zero chunk loads; for
 * E2E assertions).
 */
public final class RtpRuntime {

    /** 确定性日志 marker。 / The deterministic log marker. */
    public static final String MARKER = "[Subterra rtp]";

    /** E2E 门控属性。 / The E2E gate property. */
    public static final String PROBE_GATE = "subterra.probe.rtp";

    public static final Logger LOGGER = LogUtils.getLogger();

    private static volatile long worldSeed;
    private static volatile boolean seedCaptured;
    private static volatile java.nio.file.Path gameDir;

    private RtpRuntime() {
    }

    /** 从 mod 构造调用：载配置、挂命令与生命周期监听。 / Called from the mod constructor. */
    public static void bootstrap(ModContainer container) {
        gameDir = FMLPaths.GAMEDIR.get();
        RtpConfig.load(gameDir);
        NeoForge.EVENT_BUS.addListener(RtpCommand::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(RtpRuntime::onServerStarting);
        NeoForge.EVENT_BUS.addListener(RtpRuntime::onServerStarted);
        NeoForge.EVENT_BUS.addListener(RtpRuntime::onServerStopped);
        LOGGER.info("{} shell active ({})", MARKER, RtpConfig.summary());
    }

    private static void onServerStarting(ServerStartingEvent event) {
        worldSeed = event.getServer().getWorldData().worldGenOptions().seed();
        seedCaptured = true;
        LOGGER.info("{} server starting; seed captured", MARKER);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        if (System.getProperty(PROBE_GATE) == null) {
            return;
        }
        RtpPlanner.Params params = RtpConfig.params();
        // 门控样例：确定性规划一次（中心 0,0、平面 surface=64 纯回调），零副作用 marker。
        // Gated sample: one deterministic plan (center 0,0 over a flat surface=64 callback), side-effect-free.
        RtpPlanner.Target target = RtpPlanner.locate(worldSeed, 0, 0, params, (x, z) -> 64);
        LOGGER.info("{} probe gate=on plan ok ({}) target={}", MARKER, params.render(), target.render());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        seedCaptured = false;
        LOGGER.info("{} server stopped; seed reset", MARKER);
    }

    public static boolean seedCaptured() {
        return seedCaptured;
    }

    public static long worldSeed() {
        return worldSeed;
    }

    /** bootstrap 缓存的游戏目录（reload 用）。 / The game dir cached at bootstrap (for reload). */
    public static java.nio.file.Path gameDir() {
        return gameDir;
    }
}
