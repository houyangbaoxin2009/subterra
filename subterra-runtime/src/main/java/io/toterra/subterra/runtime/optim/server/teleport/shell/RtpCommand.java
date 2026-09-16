package io.toterra.subterra.runtime.optim.server.teleport.shell;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import io.toterra.subterra.engine.optim.server.teleport.RtpPlanner;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * p.2.31.1 {@code /subterra rtp} 命令（全玩家可用，权限级别由 td 规则
 * {@code command_permission} 决定，缺省 0；{@code reload} 子命令恒需 op 2）。
 * 执行：确定性规划（{@link RtpPlanner} 经 worldSeed + 玩家位置派生）→ 表面
 * 高度裁决（MOTION_BLOCKING_NO_LEAVES）→ 传送 + 确定性 marker。
 *
 * <p>The p.2.31.1 {@code /subterra rtp} command (available to all players; the
 * permission level comes from the td rule {@code command_permission}, default 0;
 * the {@code reload} subcommand always requires op 2). Execution: deterministic
 * planning ({@link RtpPlanner} over worldSeed + player position) → surface
 * adjudication (MOTION_BLOCKING_NO_LEAVES) → teleport + deterministic markers.
 */
public final class RtpCommand {

    private static final Map<UUID, Long> LAST_TELEPORT_MS = new ConcurrentHashMap<>();

    private RtpCommand() {
    }

    /** 命令注册（与 rule / export 同挂 {@code subterra} 根，Brigadier 自动合并）。 / Registers under the shared {@code subterra} root. */
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("subterra")
                .then(Commands.literal("rtp")
                        .requires(source -> source.hasPermission(RtpConfig.commandPermission()))
                        .executes(ctx -> execute(ctx.getSource()))
                        .then(Commands.literal("reload")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> {
                                    RtpConfig.load(RtpRuntime.gameDir());
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "rtp config reloaded (" + RtpConfig.lastState() + ")"), true);
                                    RtpRuntime.LOGGER.info("{} config reload ok ({})", RtpRuntime.MARKER, RtpConfig.summary());
                                    return 1;
                                }))));
    }

    private static int execute(CommandSourceStack source) {
        if (!RtpConfig.enabled()) {
            source.sendFailure(Component.literal("rtp is disabled"));
            return 0;
        }
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(Component.literal("rtp requires a player"));
            return 0;
        }
        if (!RtpRuntime.seedCaptured()) {
            source.sendFailure(Component.literal("world seed not captured yet"));
            return 0;
        }
        long now = System.currentTimeMillis();
        long cooldownMs = RtpConfig.cooldownSeconds() * 1000L;
        Long last = LAST_TELEPORT_MS.get(player.getUUID());
        if (last != null && cooldownMs > 0 && now - last < cooldownMs) {
            long remain = (cooldownMs - (now - last) + 999) / 1000;
            source.sendFailure(Component.literal("rtp cooldown: " + remain + "s remaining"));
            return 0;
        }
        RtpPlanner.Params params = RtpConfig.params();
        int px = player.blockPosition().getX();
        int pz = player.blockPosition().getZ();
        RtpPlanner.Target target = RtpPlanner.locate(
                RtpRuntime.worldSeed(), px, pz, params,
                (x, z) -> source.getLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
        if (!target.found()) {
            RtpRuntime.LOGGER.info("{} exhausted ({})", RtpRuntime.MARKER, target.render());
            source.sendFailure(Component.literal("rtp failed: no safe spot within the budget"));
            return 0;
        }
        player.teleportTo(source.getLevel(), target.x() + 0.5, target.y(), target.z() + 0.5,
                player.getYRot(), player.getXRot());
        LAST_TELEPORT_MS.put(player.getUUID(), now);
        RtpRuntime.LOGGER.info("{} ok (player={}, x={}, y={}, z={}, attempts={})",
                RtpRuntime.MARKER, player.getName().getString(), target.x(), target.y(), target.z(), target.attempts());
        source.sendSuccess(() -> Component.literal("rtp: " + target.x() + ", " + target.y() + ", " + target.z()
                + " (attempts=" + target.attempts() + ")"), true);
        return 1;
    }
}
