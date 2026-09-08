package io.toterra.subterra.worldgen.profiler;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;

import org.slf4j.Logger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import io.toterra.subterra.Subterra;
import io.toterra.subterra.api.worldgen.profiler.ProfileAxis;
import io.toterra.subterra.api.worldgen.profiler.ProfileCategory;
import io.toterra.subterra.api.worldgen.profiler.ProfilePlan;
import io.toterra.subterra.api.worldgen.profiler.ProfileReport;
import io.toterra.subterra.api.worldgen.profiler.ProfileSink;
import io.toterra.subterra.api.worldgen.profiler.ProfileSlice;
import io.toterra.subterra.api.worldgen.profiler.ProfileWindow;
import io.toterra.subterra.api.worldgen.profiler.SliceUnit;
import io.toterra.subterra.profiler.core.ProfileRegion;
import io.toterra.subterra.profiler.core.ResidentCollector;
import io.toterra.subterra.profiler.facade.ApiRegistry;
import io.toterra.subterra.profiler.facade.WorldProfileRunner;

/**
 * p.1.8.30 in-server World Profiler wiring: binds the pure-JDK profiler engine
 * ({@link WorldProfileRunner} / {@link ResidentCollector}) to a booted NeoForge
 * server via {@link McWorldSampler}, and exposes {@code /subterra profile},
 * {@code /subterra slice}, {@code /subterra profile-dump},
 * {@code /subterra profile-resume} and {@code /subterra profile-pause}. Static
 * {@code @SubscribeEvent} handlers on the NeoForge game bus, the same
 * {@code @EventBusSubscriber(modid, Bus.GAME)} style used by
 * {@code SameSeedCompareHook}.
 * <p>
 * Wiring happens once on {@link ServerStartedEvent}: the overworld is captured as the
 * {@link WorldSampler} source, the run context (seed / dimension / app version) is
 * pushed into the pure-JDK {@link ApiRegistry}, and — only when a
 * {@code subterra.profile} system property is present — a one-shot profile (positive
 * int radius) or a {@code residency} resume runs. No property means zero impact on boot.
 * <p>
 * The {@code /subterra} root already exists via {@code SameSeedCompareHook} (no
 * {@code requires}); to merge cleanly with it this class registers the root without a
 * permission guard and instead gates each profiling sub-command with
 * {@code source.hasPermission(2)}. This is a deliberate, documented deviation from a
 * root-level {@code requires}; the effect (op2-only profiling commands) is identical.
 * <p>
 * Everything guarded: no handler ever throws past the event/command dispatch — errors
 * are logged or sent to the sender as {@code BLOCKED}.
 * <p>
 * p.1.8.30 服务端 World Profiler 接线：经 {@link McWorldSampler} 把纯 JDK 分析引擎
 * ({@link WorldProfileRunner} / {@link ResidentCollector}) 绑定到已启动的 NeoForge 服务器，
 * 并暴露 {@code /subterra profile}、{@code /subterra slice}、{@code /subterra profile-dump}、
 * {@code /subterra profile-resume} 与 {@code /subterra profile-pause}。静态
 * {@code @SubscribeEvent} 处理器注册在 NeoForge 游戏总线上，与 {@code SameSeedCompareHook}
 * 相同的 {@code @EventBusSubscriber(modid, Bus.GAME)} 风格。
 * <p>
 * 接线在 {@link ServerStartedEvent} 一次性完成：主世界被捕获为 {@link WorldSampler} 源，运行
 * 上下文（seed / dimension / app version）被推入纯 JDK {@link ApiRegistry}，并且仅当存在
 * {@code subterra.profile} 系统属性时才执行一次分析（正整数半径）或 {@code residency} 常驻
 * 恢复。无属性时对启动零影响。
 * <p>
 * {@code /subterra} 根已由 {@code SameSeedCompareHook} 注册（不带 {@code requires}）；为与其
 * 干净合并，本类注册根节点时不做权限守卫，而把权限门放到每个分析子命令上
 * （{@code source.hasPermission(2)}）。这是记录在案的有意偏离；效果（仅 op2 可用的分析命令）
 * 完全相同。
 * <p>
 * 所有路径均受保护：绝无处理器从事件/命令分发抛出异常——错误以 {@code BLOCKED} 记录或回送
 * 发送者。
 */
@EventBusSubscriber(modid = Subterra.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class WorldProfilerHook {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The booted overworld captured on server start. */
    private static volatile ServerLevel currentLevel;

    /** Whether the incremental resident collector is accumulating chunks. */
    private static volatile boolean residencyEnabled;

    /** The live resident collector, or null when not yet resumed. */
    private static volatile ResidentCollector resident;

    /** The residency plan the resident collector was created with (for snapshots). */
    private static volatile ProfilePlan residentPlan;

    private static volatile long seed;

    private static volatile String dimension = "";

    private static volatile String appVersion = "";

    /** The pure-JDK registry used for third-party and internal profile runs. */
    static final ApiRegistry api = new ApiRegistry();

    private WorldProfilerHook() {
    }

    /** The pure-JDK {@link ApiRegistry} this hook feeds. */
    public static ApiRegistry api() {
        return api;
    }

    // ---------------- lifecycle ----------------

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        try {
            MinecraftServer server = event.getServer();
            ServerLevel overworld = server.overworld();
            if (overworld == null) {
                LOGGER.error("[subterra_profiler] BLOCKED: server has no overworld ServerLevel yet.");
                return;
            }
            currentLevel = overworld;
            seed = server.getWorldData().worldGenOptions().seed();
            dimension = overworld.dimension().location().toString();
            appVersion = ModList.get()
                    .getModContainerById(Subterra.MODID)
                    .map(c -> c.getModInfo().getVersion().toString())
                    .orElse("dev");
            api.setRunContext(seed, dimension, appVersion);
            api.setSamplerSource(() -> new McWorldSampler(levelOrThrow()));
            api.setSinkRoot(WorldProfilerHook::gamedirProfile);
            String prop = System.getProperty("subterra.profile");
            if (prop != null && !prop.isBlank()) {
                handleProfileProperty(prop);
            }
        } catch (Throwable t) {
            String reason = t.getMessage() != null && !t.getMessage().isBlank() ? t.getMessage() : t.toString();
            LOGGER.error("[subterra_profiler] BLOCKED: {}", reason);
        }
    }

    /** Runs the one-shot/residency behaviour requested by {@code subterra.profile}. */
    private static void handleProfileProperty(String prop) {
        if ("residency".equals(prop.trim())) {
            try {
                resumeResidency(WorldProfilerConfig.load());
                LOGGER.info("[subterra_profiler] residency profile resumed via subterra.profile=residency; "
                        + "collecting generated chunks now.");
            } catch (Throwable t) {
                LOGGER.error("[subterra_profiler] BLOCKED: residency resume failed: {}", t.getMessage());
            }
            return;
        }
        final int radius;
        try {
            radius = Integer.parseInt(prop.trim());
        } catch (NumberFormatException e) {
            LOGGER.error("[subterra_profiler] BLOCKED: bad subterra.profile={}", prop);
            return;
        }
        if (radius <= 0) {
            LOGGER.error("[subterra_profiler] BLOCKED: subterra.profile must be a positive int, got {}", prop);
            return;
        }
        try {
            ProfilePlan plan = withRadius(WorldProfilerConfig.load(), radius);
            ServerLevel level = levelOrThrow();
            Path sinkDir = sinkDirFor(plan, level);
            ProfileReport report = WorldProfileRunner.run(
                    new McWorldSampler(level), plan, seed, dimension, appVersion, sinkDir);
            LOGGER.info("[subterra_profiler] auto profile radius={} done; summary:\n{}files: {}/profile.zd, {}/profile.td",
                    radius, WorldProfileRunner.summary(report), sinkDir, sinkDir);
        } catch (Throwable t) {
            String reason = t.getMessage() != null && !t.getMessage().isBlank() ? t.getMessage() : t.toString();
            LOGGER.error("[subterra_profiler] BLOCKED: auto profile failed: {}", reason);
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!residencyEnabled || resident == null) {
            return;
        }
        try {
            if (event.getLevel() instanceof ServerLevel sl && sl == currentLevel) {
                ChunkAccess chunk = event.getChunk();
                if (chunk == null) {
                    return;
                }
                ChunkPos pos = chunk.getPos();
                resident.accept(pos.x, pos.z, new McWorldSampler(sl));
            }
        } catch (Throwable t) {
            LOGGER.error("[subterra_profiler] BLOCKED: chunk sample failed: {}", t.getMessage());
        }
    }

    // ---------------- commands ----------------

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        // The /subterra root is registered by SameSeedCompareHook without a permission
        // guard; we merge our profiling sub-commands onto it and gate each with op2.
        dispatcher.register(Commands.literal("subterra")
                .then(profileCommand())
                .then(sliceCommand())
                .then(guarded("profile-dump").executes(c -> dump(c.getSource())))
                .then(guarded("profile-resume").executes(c -> resume(c.getSource())))
                .then(guarded("profile-pause").executes(c -> pause(c.getSource()))));
    }

    /** A literal gated to op level 2, used so profiling sub-commands require permission. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> guarded(String name) {
        return Commands.literal(name).requires(s -> s.hasPermission(2));
    }

    /** The {@code /subterra profile [radius] [--build] [--sink &lt;run|world&gt;]} sub-command. */
    private static ArgumentBuilder<CommandSourceStack, ?> profileCommand() {
        return guarded("profile")
                .executes(c -> runProfile(c, null, false, null))
                .then(Commands.literal("--build").executes(c -> runProfile(c, null, true, null)))
                .then(Commands.literal("--sink").then(Commands.argument("value", StringArgumentType.word())
                        .executes(c -> runProfile(c, null, false, StringArgumentType.getString(c, "value")))))
                .then(Commands.literal("--build").then(Commands.literal("--sink")
                        .then(Commands.argument("value", StringArgumentType.word())
                                .executes(c -> runProfile(c, null, true, StringArgumentType.getString(c, "value"))))))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                        .executes(c -> runProfile(c, IntegerArgumentType.getInteger(c, "radius"), false, null))
                        .then(Commands.literal("--build")
                                .executes(c -> runProfile(c, IntegerArgumentType.getInteger(c, "radius"), true, null)))
                        .then(Commands.literal("--sink").then(Commands.argument("value", StringArgumentType.word())
                                .executes(c -> runProfile(c, IntegerArgumentType.getInteger(c, "radius"), false,
                                        StringArgumentType.getString(c, "value")))))
                        .then(Commands.literal("--build").then(Commands.literal("--sink")
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(c -> runProfile(c, IntegerArgumentType.getInteger(c, "radius"), true,
                                                StringArgumentType.getString(c, "value")))))));
    }

    /** The {@code /subterra slice <axis> <start> <length> [...]} sub-command. */
    private static ArgumentBuilder<CommandSourceStack, ?> sliceCommand() {
        return guarded("slice")
                .then(Commands.argument("axis", StringArgumentType.word())
                        .then(Commands.argument("start", IntegerArgumentType.integer(-100000, 100000))
                                .then(sliceLength())));
    }

    /** The slice length node plus its optional fully-combined flag branches. */
    private static ArgumentBuilder<CommandSourceStack, ?> sliceLength() {
        return Commands.argument("length", IntegerArgumentType.integer(1, 100000))
                .executes(c -> runSlice(c, new SliceFlags(null, false, false, null)))
                .then(Commands.literal("--unit")
                        .then(Commands.argument("unitVal", StringArgumentType.word())
                                .executes(c -> runSlice(c, new SliceFlags(
                                        StringArgumentType.getString(c, "unitVal"), false, false, null)))))
                .then(Commands.literal("--planes")
                        .executes(c -> runSlice(c, new SliceFlags(null, true, false, null))))
                .then(Commands.literal("--positions")
                        .executes(c -> runSlice(c, new SliceFlags(null, false, true, null))))
                .then(Commands.literal("--sink")
                        .then(Commands.argument("sinkVal", StringArgumentType.word())
                                .executes(c -> runSlice(c, new SliceFlags(null, false, false,
                                        StringArgumentType.getString(c, "sinkVal"))))))
                .then(Commands.literal("--unit")
                        .then(Commands.argument("unitVal", StringArgumentType.word())
                                .then(Commands.literal("--planes")
                                        .then(Commands.literal("--positions")
                                                .then(Commands.literal("--sink")
                                                        .then(Commands.argument("sinkVal", StringArgumentType.word())
                                                                .executes(c -> runSlice(c, new SliceFlags(
                                                                        StringArgumentType.getString(c, "unitVal"),
                                                                        true, true,
                                                                        StringArgumentType.getString(c, "sinkVal"))))))))));
    }

    /** Runs a full window profile (once) and reports the summary to the sender. */
    private static int runProfile(CommandContext<CommandSourceStack> ctx, Integer radius,
                                  boolean build, String sink) {
        try {
            ServerLevel level = levelOrThrow();
            ProfilePlan base = WorldProfilerConfig.load();
            ProfilePlan plan = radius != null ? withRadius(base, radius) : base;
            if (build) {
                forceLoadWindow(level, plan);
            }
            Path sinkDir = sinkDirFor(plan, level);
            if (sink != null && !sink.isEmpty()) {
                sinkDir = "world".equals(sink) ? worldProfileDir(level) : gamedirProfile();
            }
            ProfileReport report = WorldProfileRunner.run(
                    new McWorldSampler(level), plan, seed, dimension, appVersion, sinkDir);
            sendSummary(ctx.getSource(), "[subterra_profiler] " + WorldProfileRunner.summary(report)
                    + " files: " + sinkDir.resolve("profile.zd") + ", " + sinkDir.resolve("profile.td"));
        } catch (Throwable t) {
            blockedToSender(ctx.getSource(), t);
        }
        return 1;
    }

    /** Runs a single axial-slice scan and reports the summary to the sender. */
    private static int runSlice(CommandContext<CommandSourceStack> ctx, SliceFlags flags) {
        try {
            ServerLevel level = levelOrThrow();
            String axisS = StringArgumentType.getString(ctx, "axis");
            int start = IntegerArgumentType.getInteger(ctx, "start");
            int length = IntegerArgumentType.getInteger(ctx, "length");
            ProfileAxis axis;
            switch (axisS) {
                case "x" -> axis = ProfileAxis.X;
                case "y" -> axis = ProfileAxis.Y;
                case "z" -> axis = ProfileAxis.Z;
                default -> {
                    ctx.getSource().sendFailure(Component.literal(
                            "[subterra_profiler] BLOCKED: axis must be x/y/z, got " + axisS));
                    return 1;
                }
            }
            if (length <= 0) {
                ctx.getSource().sendFailure(Component.literal(
                        "[subterra_profiler] BLOCKED: length must be positive."));
                return 1;
            }
            int end = Math.addExact(start, length);
            SliceUnit unit;
            if (flags.unit() == null) {
                unit = SliceUnit.BLOCK;
            } else if ("chunk".equals(flags.unit())) {
                unit = SliceUnit.CHUNK;
            } else if ("block".equals(flags.unit())) {
                unit = SliceUnit.BLOCK;
            } else {
                ctx.getSource().sendFailure(Component.literal(
                        "[subterra_profiler] BLOCKED: --unit must be block/chunk, got " + flags.unit()));
                return 1;
            }
            ProfileSlice slice = new ProfileSlice(axis, start, end, unit,
                    flags.planes(), flags.positions());
            ProfilePlan base = WorldProfilerConfig.load();
            ProfileWindow win = sliceUnderWindow(level, base, slice);
            ProfilePlan plan = new ProfilePlan(base.residency(), win,
                    EnumSet.allOf(ProfileCategory.class), base.step(), slice,
                    base.formats(), sinkFor(flags.sink(), base));
            Path sinkDir = plan.sink() == ProfileSink.WORLD ? worldProfileDir(level) : gamedirProfile();
            ProfileReport report = WorldProfileRunner.run(
                    new McWorldSampler(level), plan, seed, dimension, appVersion, sinkDir);
            sendSummary(ctx.getSource(), "[subterra_profiler] " + WorldProfileRunner.summary(report)
                    + " files: " + sinkDir.resolve("profile.zd") + ", " + sinkDir.resolve("profile.td"));
        } catch (Throwable t) {
            blockedToSender(ctx.getSource(), t);
        }
        return 1;
    }

    /** Snapshot and persist the resident collector's current accumulation. */
    private static int dump(CommandSourceStack stack) {
        try {
            ResidentCollector rc = resident;
            if (!residencyEnabled || rc == null) {
                stack.sendFailure(Component.literal(
                        "[subterra_profiler] BLOCKED: residency profile is not resumed; run /subterra profile-resume first."));
                return 1;
            }
            ProfileReport report = rc.snapshot(seed, dimension, appVersion);
            Path sinkDir = gamedirProfile();
            List<Path> files = WorldProfileRunner.writeFiles(report, residentPlan, sinkDir);
            sendSummary(stack, "[subterra_profiler] " + WorldProfileRunner.summary(report)
                    + " files: " + files);
        } catch (Throwable t) {
            blockedToSender(stack, t);
        }
        return 1;
    }

    /** Start accumulating freshly generated chunks as a resident profile. */
    private static int resume(CommandSourceStack stack) {
        try {
            resumeResidency(WorldProfilerConfig.load());
            stack.sendSuccess(() -> Component.literal(
                    "[subterra_profiler] residency profile resumed; sampling generated chunks."), false);
        } catch (Throwable t) {
            blockedToSender(stack, t);
        }
        return 1;
    }

    /** Stop accumulating (the collector instance stays; dump still works). */
    private static int pause(CommandSourceStack stack) {
        residencyEnabled = false;
        stack.sendSuccess(() -> Component.literal(
                "[subterra_profiler] residency profile paused; existing data is still dumpable via /subterra profile-dump."), false);
        return 1;
    }

    // ---------------- residency ----------------

    /**
     * Builds a residency plan (forces {@code residency=true} because a non-residency
     * plan makes every {@link ResidentCollector#accept} return false) and starts a
     * fresh collector over the current overworld's vertical extent.
     * <p>
     * 构建常驻计划（强制 {@code residency=true}，因为非常驻计划会使 {@link ResidentCollector#accept}
     * 恒为 false）并基于当前主世界的竖直范围启动一个新采集器。
     */
    private static void resumeResidency(ProfilePlan plan) {
        ProfilePlan residencyPlan = new ProfilePlan(true, plan.window(), plan.categories(),
                plan.step(), null, plan.formats(), plan.sink());
        ServerLevel level = levelOrThrow();
        int minY = level.dimensionType().minY();
        int maxY = Math.addExact(minY, level.dimensionType().height());
        resident = new ResidentCollector(residencyPlan, minY, maxY);
        residentPlan = residencyPlan;
        residencyEnabled = true;
    }

    // ---------------- helpers ----------------

    /** The overworld captured at server start, or throws if not yet available. */
    private static ServerLevel levelOrThrow() {
        ServerLevel lv = currentLevel;
        if (lv == null) {
            throw new IllegalStateException("no overworld ServerLevel captured by WorldProfilerHook");
        }
        return lv;
    }

    /** Rewrites a plan's window radius in chunks, keeping every other field. */
    private static ProfilePlan withRadius(ProfilePlan base, int radius) {
        return new ProfilePlan(base.residency(),
                new ProfileWindow(base.window().centerX(), base.window().centerZ(), radius),
                base.categories(), base.step(), base.slice(), base.formats(), base.sink());
    }

    /** The sink directory for a plan: world-store dir for WORLD sinks, else the run dir. */
    private static Path sinkDirFor(ProfilePlan plan, ServerLevel level) {
        return plan.sink() == ProfileSink.WORLD ? worldProfileDir(level) : gamedirProfile();
    }

    /** Resolves a slice --sink value ({@code run}/{@code world}) to a {@link ProfileSink}. */
    private static ProfileSink sinkFor(String sink, ProfilePlan base) {
        if (sink == null) {
            return base.sink();
        }
        return switch (sink) {
            case "run" -> ProfileSink.RUN;
            case "world" -> ProfileSink.WORLD;
            case "none" -> ProfileSink.NONE;
            default -> base.sink();
        };
    }

    /**
     * Derives a {@link ProfileWindow} whose chunk radius deterministically covers the
     * whole slice box. The box is computed via {@link ProfileRegion#sliceBox} using the
     * config window's horizontal range as the perpendicular full range and the
     * dimension's vertical extent; the center and radius are then widened to bracket it.
     * <p>
     * 推导一个 {@link ProfileWindow}，使其区块半径确定性覆盖整个切片盒。盒经
     * {@link ProfileRegion#sliceBox} 计算：以配置窗口的水平范围作为另两轴的全幅、以维度竖直
     * 范围作为竖直全幅；随后把中心与半径加宽到足以括住它。
     */
    private static ProfileWindow sliceUnderWindow(ServerLevel level, ProfilePlan base, ProfileSlice slice) {
        int minY = level.dimensionType().minY();
        int maxY = Math.addExact(minY, level.dimensionType().height());
        ProfileRegion.Box cw = ProfileRegion.windowBox(base.window());
        ProfileRegion.Box box = ProfileRegion.sliceBox(slice, minY, maxY, cw.zFrom(), cw.zTo());
        int cxMid = Math.floorDiv(box.xFrom() + box.xTo(), 2);
        int czMid = Math.floorDiv(box.zFrom() + box.zTo(), 2);
        int span = Math.max(box.xTo() - box.xFrom(), box.zTo() - box.zFrom());
        // A window of radius r covers 32*r+16 horizontal blocks; pick the smallest r
        // that brackets span (>=0), +1 margin for center rounding.
        int radius = span > 0 ? (span / 32) + 1 : 1;
        return new ProfileWindow(cxMid, czMid, radius);
    }

    /** Forces every chunk of the plan window to generate at full status. */
    private static void forceLoadWindow(ServerLevel level, ProfilePlan plan) {
        ProfileRegion.Box box = ProfileRegion.windowBox(plan.window());
        int cxFirst = Math.floorDiv(box.xFrom(), 16);
        int cxLast = Math.floorDiv(box.xTo() - 1, 16);
        int czFirst = Math.floorDiv(box.zFrom(), 16);
        int czLast = Math.floorDiv(box.zTo() - 1, 16);
        for (int cx = cxFirst; cx <= cxLast; cx++) {
            for (int cz = czFirst; cz <= czLast; cz++) {
                level.getChunkSource().getChunk(cx, cz, true);
            }
        }
    }

    /** The per-world profile output dir: {@code <world>/subterra-profile}. */
    private static Path worldProfileDir(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("subterra-profile");
    }

    /** The run-root profile output dir: {@code <gamedir>/profile}. */
    private static Path gamedirProfile() {
        return FMLPaths.GAMEDIR.get().resolve("profile");
    }

    /** Sends each non-blank line of a summary to the command sender. */
    private static void sendSummary(CommandSourceStack stack, String summary) {
        for (String line : summary.split("\\r?\\n")) {
            if (!line.isBlank()) {
                stack.sendSuccess(() -> Component.literal(line), false);
            }
        }
    }

    /** Sends a BLOCKED failure to the command sender and logs it. */
    private static void blockedToSender(CommandSourceStack stack, Throwable t) {
        String reason = t.getMessage() != null && !t.getMessage().isBlank() ? t.getMessage() : t.toString();
        LOGGER.error("[subterra_profiler] BLOCKED: {}", reason);
        stack.sendFailure(Component.literal("[subterra_profiler] BLOCKED: " + reason));
    }

    /** Per-branch optional flags for the slice command, fixed at registration time. */
    private record SliceFlags(String unit, boolean planes, boolean positions, String sink) {
    }
}