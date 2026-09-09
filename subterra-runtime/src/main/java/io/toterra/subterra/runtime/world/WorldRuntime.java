package io.toterra.subterra.runtime.world;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import io.toterra.subterra.Subterra;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.runtime.datapack.DatapackRuntime;
import io.toterra.subterra.engine.world.WorldPackPacker;
import io.toterra.subterra.engine.world.WorldPackResult;

/**
 * p.2.9.6 runtime 壳：把 engine.world「世界即产物」世界包桥到真实游戏生命周期（默认不接管，渐进
 * 增强）。在 {@code ServerStartedEvent} 上于真实游戏 JVM 内做一次「世界包闭环」轻量桥：从 server
 * 取确定性种子与固定键组装最小 meta，把 level storage 目录当 {@code saveRoot}、解析出的数据包目录
 * 当 {@code datapackDir}，经 {@code engine.world WorldPack + WorldPackPacker} 走一次
 * {@code pack → rehydrate → unpack}，证明底座在游戏 JVM 装载且契约保持。安全起见**只做 capture +
 * rehydrate 闭环验证，不落盘写回服务器**：unpack 的 {@code saveTarget}/{@code datapackTarget} 一律放
 * 隔离的 staging（{@code java.io.tmpdir} / 项目目录下固定子目录 {@code build/tmp/world-e2e}），绝不写
 * 服务器原目录。门控 {@code -Dsubterra.probe.world}：不设则完全 no-op，对原版存档/世界生命周期零影响
 * （也不与任何既有 marker 交互——async/network/save/sim marker 及各自 original-path 保持字节原样）。
 * 门控开启时向 stdout 打确定性 marker（探针按前缀 {@code [Subterra world]} 匹配）。经
 * {@code @EventBusSubscriber} 自注册到 NeoForge 游戏总线（与 SimRuntime/SaveRuntime 同风格，无需改
 * Subterra.java）；默认是纯 no-op 壳——开态/关态都不注册任何接管逻辑（同 SimRuntime），任何异常被兜底为
 * {@code closed-loop FAILED: ...} marker 并带异常栈（供探针断言失败路径，失败不吞，绝不断言中断服务
 * 启动）。依赖铁律：runtime 可依赖 engine（{@code engine.world} 经 {@code engine.datapack/save/config}）
 * 与 MC 事件，禁依赖 migrate/devkit。
 * <p>
 * 本壳刻意不含游戏内世界接管、不写服务器存档；确定性与隔离性由纯数据闭环证明。marker 内容固定格式
 * （无时间戳无随机），供 {@code AsyncE2EProbe} 断言。
 * <p>
 * p.2.9.6 runtime shell: bridges the {@code engine.world} "world-as-artifact" pack to the real game
 * lifecycle (off by default — vanilla save/world lifecycle untouched, progressive enhancement). On
 * {@code ServerStartedEvent} it runs one lightweight <em>world-pack closed loop</em> inside the real
 * game JVM: a deterministic seed and fixed keys build a minimal {@code meta}; the level storage dir is
 * used as {@code saveRoot} and the resolved datapack dir as {@code datapackDir}, pushed through
 * {@code engine.world WorldPack + WorldPackPacker} as {@code pack → rehydrate → unpack} to prove the
 * base loads in the game JVM and its contract holds. For safety this is <em>capture+rehydrate only —
 * nothing is written back to the server</em>: the unpack {@code saveTarget}/{@code datapackTarget} land
 * in an isolated staging area (the fixed sub-dir {@code build/tmp/world-e2e} under
 * {@code java.io.tmpdir} / the project dir), never the server's live directories. Gated by
 * {@code -Dsubterra.probe.world}: absent → fully no-op (never touches existing markers — the
 * async/network/save/sim markers plus their original-path lines stay byte-identical). When gated on it
 * prints deterministic markers to stdout (probes match by prefix {@code [Subterra world]}). It
 * self-registers on the NeoForge game bus via {@code @EventBusSubscriber} (same style as
 * SimRuntime/SaveRuntime — no Subterra.java edit); it is a pure no-op shell by default — neither the
 * open nor the closed state registers any take-over logic (same as SimRuntime); any throwable is caught
 * and reported as a {@code closed-loop FAILED: ...} marker with the stack (so the probe can assert the
 * failure path — the failure is not swallowed, but it never breaks the boot gate). Dependency rule: the
 * runtime may depend on engine ({@code engine.world} via {@code engine.datapack/save/config}) and MC
 * events, never on migrate/devkit.
 * <p>
 * This shell deliberately owns no game path and writes no server save; the closure is proven by a pure
 * data loop. The marker body is a fixed format (no timestamps, no randomness), asserted by
 * {@code AsyncE2EProbe}.
 */
@EventBusSubscriber(modid = Subterra.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class WorldRuntime {

    /** 探针 marker 前缀 / probe marker prefix. */
    public static final String MARKER = "[Subterra world]";

    /** Staging root segments: {@code build/tmp/world-e2e} under the base dir. */
    private static final String STAGING_ROOT = "world-e2e";

    private WorldRuntime() {
    }

    /**
     * 探针门控：{@code -Dsubterra.probe.world} 存在且非空且非 {@code 0}/{@code false} 即视为开启
     * （E2E 走 {@code -Psubterra.probe.world=1}，经根 build.gradle server run 块转发到游戏 JVM）。
     * 缺失 / 空白 → no-op。Probe gate: enabled when {@code -Dsubterra.probe.world} is present,
     * non-blank and not {@code 0}/{@code false} (E2E uses {@code -Psubterra.probe.world=1},
     * forwarded to the game JVM by the root server run block). Absent/blank → no-op.
     */
    private static boolean worldProbeGated() {
        String v = System.getProperty("subterra.probe.world");
        if (v == null || v.isBlank()) {
            return false;
        }
        String t = v.trim();
        return !"0".equals(t) && !"false".equalsIgnoreCase(t);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        if (!worldProbeGated()) {
            return; // no probe gate -> zero impact; vanilla save/world lifecycle untouched
        }
        try {
            MinecraftServer server = event.getServer();
            if (server == null) {
                print("world-pack closed-loop FAILED: null-server");
                return;
            }
            print("world-shell-gate=on");
            runClosedLoop(server);
        } catch (Throwable t) {
            // never propagate past the event dispatch; the boot gate stays green.
            // The failure is NOT swallowed: report it with a stack for probe assertion.
            print("world-pack closed-loop FAILED: " + t);
            t.printStackTrace();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // 无持有状态 -> 纯 no-op；对称保留空钩子（同 SaveRuntime）。
        // No held state -> pure no-op; empty hook kept for symmetry (as SaveRuntime).
        if (!worldProbeGated()) {
            return;
        }
    }

    /**
     * The deterministic close loop: seed + fixed keys → minimal meta; the level storage dir as
     * {@code saveRoot}; pack → rehydrate → unpack into isolated staging only; print the fixed OK marker.
     *
     * 确定性闭环：种子 + 固定键 → 最小 meta；level storage 目录当 {@code saveRoot}；
     * {@code pack → rehydrate → unpack} 且只入隔离 staging；打印固定 OK marker。
     */
    private static void runClosedLoop(MinecraftServer server) throws IOException {
        // Staging base: <projectDir|java.io.tmpdir>/build/tmp/world-e2e. Fixed per boot; cleaned first.
        Path staging = stagingBase().resolve("build").resolve("tmp").resolve(STAGING_ROOT).toAbsolutePath().normalize();
        deleteRecursively(staging);
        Files.createDirectories(staging);

        long seed = server.getWorldData().worldGenOptions().seed();
        Map<String, TdValue> meta = new LinkedHashMap<>();
        meta.put("world.id", TdValue.str("subterra-dev"));
        meta.put("world.seed", TdValue.of(seed));

        // saveRoot = the server's live level-storage dir (never written; read-only source).
        Path saveRoot = server.getWorldPath(LevelResource.ROOT);

        // datapackDir = the resolved datapack dir; if it does not exist, stage an empty one so the
        // closure still runs with a deterministic 0-file datapack (we never create it on the server).
        Path resolvedDatapack = DatapackRuntime.resolveDatapacksDir();
        Path datapackDir;
        if (Files.isDirectory(resolvedDatapack)) {
            datapackDir = resolvedDatapack;
        } else {
            datapackDir = staging.resolve("datapack-empty");
            Files.createDirectories(datapackDir);
        }

        int datapack = countTdFiles(datapackDir);
        String doc = WorldPackPacker.pack(saveRoot, datapackDir, meta);
        WorldPackResult result = WorldPackPacker.unpack(doc, staging.resolve("save"), staging.resolve("datapack"));
        int slots = result.save().slots().size();

        // Fixed format, deterministic, no timestamp / no randomness: seed slots datapack-files.
        print("world-pack closed-loop OK: " + seed + " " + slots + " " + datapack);
    }

    /** Staging base dir: {@code subterra.projectDir} property (root project, as AsyncE2EProbe) or {@code java.io.tmpdir}. */
    private static Path stagingBase() {
        String prop = System.getProperty("subterra.projectDir");
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop);
        }
        return Path.of(System.getProperty("java.io.tmpdir"));
    }

    /** Recursively deletes a directory tree if present (staging hygiene). */
    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("staging cleanup failed: " + dir, e);
        }
    }

    /** Counts {@code .td} files under a directory (deterministic, matches DatapackPack's scan). */
    private static int countTdFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return (int) walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".td"))
                    .count();
        } catch (IOException e) {
            throw new UncheckedIOException("datapack count failed: " + dir, e);
        }
    }

    private static void print(String body) {
        System.out.println(MARKER + " " + body);
    }
}