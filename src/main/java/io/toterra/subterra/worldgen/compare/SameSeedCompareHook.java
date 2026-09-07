package io.toterra.subterra.worldgen.compare;

import org.slf4j.Logger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.logging.LogUtils;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import io.toterra.subterra.Subterra;

/**
 * p.1.8.18 in-server authoritative compare trigger: wires the {@link SameSeedCompare} bridge
 * (p.1.8.17) into a booted NeoForge server so the vanilla-vs-mirror comparison executes on real
 * coordinates in-process. Registers static {@code @SubscribeEvent} handlers on the
 * {@link net.neoforged.bus.api.IEventBus#gameEvent()} (NeoForge) game bus for both
 * client (integrated) and dedicated server dists, via @EventBusSubscriber — the same style used
 * by {@code SubterraClient}.
 * <p>
 * Two entry points:
 * <ul>
 *   <li>auto-trigger: on {@link ServerStartedEvent}, if system property {@code subterra.compareSeed}
 *       is present (parsed as a long), runs the compare once against the overworld and logs the
 *       summary at INFO. Absent property → zero impact on normal boot (the boot gate stays green).</li>
 *   <li>command: {@code /subterra compare <seed>} runs the same compare on the sender's level and
 *       prints a compact per-field summary to the sender.</li>
 * </ul>
 * Everything is guarded: this class never throws past the event/command dispatch — errors are
 * logged and/or reported to the sender as {@code BLOCKED}, then control returns.
 * <p>
 * 服务端权威对拍触发器（p.1.8.18）：将 p.1.8.17 的 {@link SameSeedCompare} 对拍桥接入已启动的
 * NeoForge 服务器，使原生 vs 镜像对比在真实坐标、进程内执行。通过 @EventBusSubscriber 以静态
 * {@code @SubscribeEvent} 处理器注册到 NeoForge 游戏总线（客户端集成服务器与专用服务器两种发行
 * 端均会触发），与 {@code SubterraClient} 的既有风格一致。
 * <p>
 * 两个入口：
 * <ul>
 *   <li>自动触发：{@link ServerStartedEvent} 时，若系统属性 {@code subterra.compareSeed} 存在
 *       （解析为 long），则对主世界执行一次对比并以 INFO 记录摘要。属性缺失 → 对正常启动零影响
 *       （引导门保持绿色）。</li>
 *   <li>命令：{@code /subterra compare <seed>} 在发送者所在维度执行相同对比，向发送者打印紧凑的
 *       逐场摘要。</li>
 * </ul>
 * 所有路径均受保护：本类绝不从事件/命令分发抛出异常——错误以 {@code BLOCKED} 记录和/或回传给
 * 发送者，随后返回。
 */
@EventBusSubscriber(modid = Subterra.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SameSeedCompareHook {

    private static final Logger LOGGER = LogUtils.getLogger();

    private SameSeedCompareHook() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        String prop = System.getProperty("subterra.compareSeed");
        if (prop == null || prop.isBlank()) {
            return; // no property -> zero impact on normal boot
        }
        final long seed;
        try {
            seed = Long.parseLong(prop.trim());
        } catch (NumberFormatException e) {
            LOGGER.error("[SameSeedCompare] BLOCKED: bad subterra.compareSeed={}", prop);
            return;
        }
        try {
            MinecraftServer server = event.getServer();
            ServerLevel overworld = server.overworld();
            if (overworld == null) {
                LOGGER.error("[SameSeedCompare] BLOCKED: server has no overworld ServerLevel yet.");
                return;
            }
            SameSeedCompare.CompareReport rep = SameSeedCompare.runAgainstReport(overworld, seed);
            LOGGER.info("[SameSeedCompare] auto compare for seed={}: {} / {} field-samples bit-equal; "
                            + "per-field stdout summary above, csv=run/compare/{}.csv",
                    rep.seed(), rep.bitEqualTotal(), rep.totalSamples(), rep.seed());
        } catch (Throwable t) {
            String reason = t.getMessage() != null && !t.getMessage().isBlank() ? t.getMessage() : t.toString();
            LOGGER.error("[SameSeedCompare] BLOCKED: {}", reason);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        // /subterra compare <seed> — run the authoritative same-seed compare on the sender's level.
        dispatcher.register(Commands.literal("subterra")
                .then(Commands.literal("compare")
                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                .executes(ctx -> {
                                    long seed = LongArgumentType.getLong(ctx, "seed");
                                    SameSeedCompare.reportToSender(ctx.getSource(), seed);
                                    return 1;
                                }))));
    }
}