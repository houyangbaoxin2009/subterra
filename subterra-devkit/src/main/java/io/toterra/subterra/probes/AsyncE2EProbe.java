package io.toterra.subterra.probes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import io.toterra.subterra.engine.worldgen.async.AsyncChunkPipeline;
import io.toterra.subterra.engine.worldgen.async.ChunkKey;
import io.toterra.subterra.engine.worldgen.async.ChunkProducer;
import io.toterra.subterra.engine.worldgen.async.DeterministicDispatcher.ChunkResult;
import io.toterra.subterra.engine.worldgen.async.io.AsyncIoQueue;

/**
 * p.2.6.6 async-worldgen end-to-end gate: forks {@code gradlew runServer} with
 * {@code -Psubterra.probe.async=1} and asserts the {@code AsyncChunkRuntime} shell emits the
 * deterministic markers ({@code [Subterra async]}) at ServerStartedEvent / ServerStoppingEvent:
 * <ul>
 *   <li>{@code baseline-io-default-off=true} — in a fresh game JVM the engine async I/O gate is
 *       off by default (progressive enhancement, never default-on);</li>
 *   <li>{@code async-io-gate-enabled=true} — the shell explicitly {@link AsyncIoQueue#enable()}s
 *       the gate, wiring engine.worldgen.async.io onto the live server;</li>
 *   <li>{@code async-chunk-runtime-initialized} — the shell stages one deterministic queued write
 *       with no linkage errors.</li>
 * </ul>
 * Since p.2.8.6 the <em>same</em> game boot also absorbs the {@code engine.sim} shell
 * (p.2.8.6) and, since p.2.9.6, the {@code engine.world} world pack shell: the runServer line also
 * passes {@code -Psubterra.probe.sim=1} and {@code -Psubterra.probe.world=1}; the probe asserts the
 * {@code SimRuntime} shell prints its {@code [Subterra sim]} markers ({@code sim-shell-gate=on} and,
 * once every engine.sim micro-check passes, {@code sim-core-composed-ok}) and the
 * {@code WorldRuntime} shell prints its {@code [Subterra world]} markers ({@code world-shell-gate=on}
 * and {@code world-pack closed-loop OK}). Since p.2.10.5 the boot also absorbs the
 * {@code SaveVerifyRuntime}): the runServer line also passes
 * {@code -Psubterra.probe.saveverify=1}; the probe asserts its {@code [Subterra saveverify]}
 * marker ({@code PASS}). Since p.2.19.2 the same boot also absorbs the launch runtime shell
 * ({@code LaunchRuntime}, {@code -Psubterra.probe.launch=probe}) and the fix runtime shell
 * ({@code FixRuntime} over the {@code Java25Gaps} registry, {@code -Psubterra.probe.fix=probe});
 * the probe asserts their {@code [Subterra launch]} and {@code [Subterra fix]} ok markers.
 * Since p.2.19.3 the same boot also absorbs the cfglog shells (both gated by the shared
 * {@code -Psubterra.probe.cfglog=probe}): the {@code ConfigRuntime} shell (two-tier td sample load
 * + RuleStore/RuleReloader hot-reload gate) and the {@code LogRuntime} shell (engine.log MC-shell
 * load verification + deterministic marker); the probe asserts their {@code [Subterra cfglog]}
 * {@code config ok (rules=} and {@code log ok (ring=} markers.
 * Since p.2.19.4 the same boot also absorbs the tie shell ({@code TieRuntime},
 * {@code -Psubterra.probe.tie=probe}): the probe stages the bundled {@code /tie/tiefib_probe.dll}
 * into build/tmp and forwards it as {@code -Psubterra.tie.lib}, asserting either the
 * {@code [Subterra tie]} {@code ok (lib=} or {@code skip (no tie lib)} marker (ok when the dev
 * environment provides a tiec dll, deterministic skip when it does not).
 * Since p.2.27.2 the same boot also absorbs the render-hooks wiring slot
 * ({@code RenderHooks.serverWiringCheck}, same {@code -Psubterra.probe.render=probe} gate): the
 * probe asserts the {@code [Subterra render]} {@code hooks ok (level=} marker (three-hook
 * deterministic check wiring over engine.render + engine.render.instancing).
 * Since p.2.21.2 the same boot also absorbs the anim shell ({@code AnimRuntime},
 * {@code -Psubterra.probe.anim=probe}): the probe asserts the {@code [Subterra anim]}
 * {@code ok (states=} marker (deterministic sample animation state-machine drive over
 * engine.anim, p.2.21.1).
 * Since p.2.22.4 the same boot also absorbs the ui shell ({@code UiRuntime},
 * {@code -Psubterra.probe.ui=probe}): the probe asserts the {@code [Subterra ui]}
 * {@code ok (hud=} marker (deterministic management-view data-plane consumption over
 * engine.ui — hud rows / tooltip book / doc books / mod-list view, p.2.22).
 * Event-driven, timing-free — it is a union gate that asserts
 * the async, sim, world and saveverify shells in one live-server launch, so the 5-boot total is
 * unchanged. All async assertions are kept verbatim; the sim/world/saveverify slots are purely
 * additional.
 * <p>
 * Before forking, a headless pure-JVM engine-core check runs inside the probe JVM (NOT the game):
 * a 2×2 fixed-coordinate set is pushed through {@link AsyncChunkPipeline.generate} (parallel, P=4)
 * vs {@code generateSerial} on the same producer and asserted byte-identical — this confirms the
 * shipped engine still runs off the devkit classpath in the E2E harness. Event-based (never
 * timing-based), mirrors the {@code NetworkE2EProbe} / {@code SaveE2EProbe} fork mechanism; the
 * markers are fully independent of the network/save/datapack markers (prefix {@code [Subterra async]},
 * {@code [Subterra sim]} and {@code [Subterra world]}). Exit 0 = PASS, exit 1 = FAIL.
 * <p>
 * p.2.8.6 起，<em>同一次</em>开服同时并入 {@code engine.sim} 壳（p.2.8.6），p.2.9.6 并入
 * {@code engine.world} 世界包壳：runServer 参数行另加 {@code -Psubterra.probe.sim=1} 与
 * {@code -Psubterra.probe.world=1}，探针断言 {@code SimRuntime} 壳打印其 {@code [Subterra sim]}
 * marker（{@code sim-shell-gate=on}，以及当所有 engine.sim 微校验均通过时的
 * {@code sim-core-composed-ok}），并断言 {@code WorldRuntime} 壳打印其 {@code [Subterra world]}
 * marker（{@code world-shell-gate=on} 与 {@code world-pack closed-loop OK}）。p.2.19.2 起，
 * <em>同一次</em>开服再并入 launch 壳（{@code LaunchRuntime}，{@code -Psubterra.probe.launch=probe}）
 * 与 fix 壳（{@code FixRuntime} 消费 {@code Java25Gaps} registry，{@code -Psubterra.probe.fix=probe}），
 * 探针断言其 {@code [Subterra launch]} 与 {@code [Subterra fix]} ok marker。p.2.19.3 起，同一次开服再
 * 并入 cfglog 两壳（共用同一门控 {@code -Psubterra.probe.cfglog=probe}）：{@code ConfigRuntime} 壳
 * （td 双层样例装载 + RuleStore/RuleReloader 热重载门）与 {@code LogRuntime} 壳（engine.log MC 壳
 * 装载验证 + 确定性 marker），探针断言其 {@code [Subterra cfglog]} 的 {@code config ok (rules=} 与
 * {@code log ok (ring=} marker。p.2.19.4 起，同一次开服再并入 tie 壳（{@code TieRuntime}，
 * {@code -Psubterra.probe.tie=probe}）：探针把捆绑资源 {@code /tie/tiefib_probe.dll} 暂存到
 * build/tmp 并以 {@code -Psubterra.tie.lib} 转发，断言 {@code [Subterra tie]} 的
 * {@code ok (lib=} 或 {@code skip (no tie lib)} marker 二者其一（dev 环境提供 tiec dll 则 ok，
 * 无则确定性 skip）。p.2.21.2 起，同一次开服再并入 anim 壳（{@code AnimRuntime}，
 * {@code -Psubterra.probe.anim=probe}）：探针断言其 {@code [Subterra anim]} 的 {@code ok (states=}
 * marker（engine.anim 确定性示例动画状态机驱动，p.2.21.1）。p.2.22.4 起，同一次开服再并入
 * ui 壳（{@code UiRuntime}，{@code -Psubterra.probe.ui=probe}）：探针断言其
 * {@code [Subterra ui]} 的 {@code ok (hud=} marker（engine.ui 管理视图数据面确定性消费——
 * HUD 行 / tooltip 书籍 / 文档书籍 / 模组列表视图，p.2.22）。事件驱动、禁时序——
 * 它是一次性并断言 async、sim、world、saveverify、launch、fix 六壳的联合门，故 5 次开服总数不变。
 * 既有 async 断言逐字保持；sim/world/saveverify/launch/fix 槽纯属新增。
 */
public final class AsyncE2EProbe {

    private static final String DONE_MARKER = "Done (";
    private static final String FATAL_MARKER = "FATAL";
    private static final String BUILD_FAILED_MARKER = "BUILD FAILED";
    private static final long BOOT_DEADLINE_MINUTES = 6;
    private static final int PROBE_PORT = 25599;
    private static final String ASYNC_MARKER = "[Subterra async]";
    /** sim-shell marker prefix emitted by the {@code SimRuntime} shell (p.2.8.6). */
    private static final String SIM_MARKER = "[Subterra sim]";
    /** world-pack shell marker prefix emitted by the {@code WorldRuntime} shell (p.2.9.6). */
    private static final String WORLD_MARKER = "[Subterra world]";
    /** verifiable-save shell marker prefix emitted by the {@code SaveVerifyRuntime} shell (p.2.10.5). */
    private static final String SAVEVERIFY_MARKER = "[Subterra saveverify]";
    /** programmable-session shell marker prefix emitted by the {@code SessionRuntime} shell (p.2.11.5). */
    private static final String SESSION_MARKER = "[Subterra session]";
    /** launch runtime shell marker prefix emitted by the {@code LaunchRuntime} shell (p.2.19.2). */
    private static final String LAUNCH_MARKER = "[Subterra launch]";
    /** fix runtime shell marker prefix emitted by the {@code FixRuntime} shell over Java25Gaps (p.2.19.2). */
    private static final String FIX_MARKER = "[Subterra fix]";
    /** cfglog shells marker prefix emitted by the {@code ConfigRuntime} (two-tier td sample load +
     * RuleStore/RuleReloader hot-reload gate) and {@code LogRuntime} (engine.log MC-shell load
     * verification) shells, both gated by subterra.probe.cfglog (p.2.19.3). */
    private static final String CFGLOG_MARKER = "[Subterra cfglog]";
    /** tie shell marker prefix emitted by the {@code TieRuntime} shell over the engine.tie FFM
     * bridge, gated by subterra.probe.tie (p.2.19.4). */
    private static final String TIE_MARKER = "[Subterra tie]";
    /** render shell marker prefix emitted by the {@code RenderRuntime} shell over
     * engine.render.instancing, gated by subterra.probe.render (p.2.27.1.2). */
    private static final String RENDER_MARKER = "[Subterra render]";
    /** anim shell marker prefix emitted by the {@code AnimRuntime} shell over engine.anim,
     * gated by subterra.probe.anim (p.2.21.2). */
    private static final String ANIM_MARKER = "[Subterra anim]";
    /** ui shell marker prefix emitted by the {@code UiRuntime} shell over engine.ui,
     * gated by subterra.probe.ui (p.2.22.4). */
    private static final String UI_MARKER = "[Subterra ui]";
    /** Fixed seed shared with the engine-core check and the engine probes. */
    private static final long WORLD_SEED = 44905237L;

    private AsyncE2EProbe() {
    }

    public static void main(String[] args) throws Exception {
        String projectDir = System.getProperty("subterra.projectDir", System.getProperty("user.dir", "."));
        Path root = Paths.get(projectDir).toAbsolutePath();
        String gradlew = root.resolve(System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "gradlew.bat" : "gradlew").toString();
        if (!Files.isRegularFile(Paths.get(gradlew))) {
            System.out.println("[AsyncE2EProbe] project dir not found: " + root);
            System.exit(1);
        }

        // ---- headless pure-JVM engine-core check (in the probe JVM, NOT the game) ----
        boolean engineCoreOk = engineCoreCheck();

        ensureAssetProperties(root);
        reapPort(PROBE_PORT);

        // p.2.19.4: stage the bundled tiec dll for the tie shell (null -> the game JVM sees no
        // tie lib and TieRuntime prints the deterministic skip marker).
        String tieLib = stageTieLib(root);

        List<String> cmd = new ArrayList<>(List.of(
                gradlew, "runServer", "-x", "downloadAssets",
                "--console=plain", "--no-daemon",
                "-Psubterra.probe.async=1", "-Psubterra.probe.sim=1", "-Psubterra.probe.world=1",
                "-Psubterra.probe.saveverify=1", "-Psubterra.probe.session=1",
                "-Psubterra.probe.launch=probe", "-Psubterra.probe.fix=probe",
                "-Psubterra.probe.cfglog=probe",
                "-Psubterra.probe.tie=probe",
                "-Psubterra.probe.render=probe",
                "-Psubterra.probe.anim=probe",
                "-Psubterra.probe.ui=probe"));
        if (tieLib != null) {
            cmd.add("-Psubterra.tie.lib=" + tieLib);
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(root.toFile());
        pb.redirectErrorStream(true);
        pb.environment().merge("JAVA_TOOL_OPTIONS", "-Djava.net.preferIPv4Stack=true",
                (o, n) -> o.isBlank() ? n : o + " " + n);

        Process process = pb.start();

        // 0 = booted (Done), 1 = baseline-IO-default-off, 2 = async-I/O-gate-enabled,
        // 3 = async-chunk-runtime-initialized,
        // 4 = sim-shell-gate-on, 5 = sim-core-composed-ok   (sim slots, p.2.8.6)
        // 6 = world-shell-gate-on, 7 = world-pack-closed-loop-ok   (world slots, p.2.9.6)
        // 8 = saveverify-shell-PASS   (verifiable-save closed-loop slot, p.2.10.5)
        // 9 = session-shell-PASS   (programmable-session closed-loop slot, p.2.11.5)
        // 10 = launch-shell-ok   (launch runtime health slot, p.2.19.2)
        // 11 = fix-shell-ok   (Java25Gaps registry slot, p.2.19.2)
        // 12 = cfglog-config-ok   (ConfigRuntime two-tier sample + hot-reload gate slot, p.2.19.3)
        // 13 = cfglog-log-ok   (LogRuntime engine.log MC-shell marker slot, p.2.19.3)
        // 14 = tie-ok-or-skip   (TieRuntime FFM bridge slot: ok (lib= or skip (no tie lib), p.2.19.4)
        // 15 = render-shell-ok   (RenderRuntime engine.render.instancing load slot, p.2.27.1.2)
        // 16 = render-hooks-ok   (RenderHooks deterministic three-hook wiring slot, p.2.27.2)
        // 17 = anim-shell-ok   (AnimRuntime deterministic anim state-machine drive slot, p.2.21.2)
        // 18 = ui-shell-ok   (UiRuntime engine.ui management-view data-plane consumption slot, p.2.22.4)
        boolean[] seen = new boolean[19];
        boolean fatal = false;
        int asyncMarkerLines = 0;
        int simMarkerLines = 0;
        int worldMarkerLines = 0;
        int saveverifyMarkerLines = 0;
        int sessionMarkerLines = 0;
        int launchMarkerLines = 0;
        int fixMarkerLines = 0;
        int cfglogMarkerLines = 0;
        int tieMarkerLines = 0;
        int renderMarkerLines = 0;
        int animMarkerLines = 0;
        int uiMarkerLines = 0;
        long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(BOOT_DEADLINE_MINUTES);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                if (line.contains(DONE_MARKER)) {
                    seen[0] = true;
                }
                if (line.contains(ASYNC_MARKER)) {
                    asyncMarkerLines++;
                }
                if (line.contains(ASYNC_MARKER) && line.contains("baseline-io-default-off=true")) {
                    seen[1] = true;
                }
                if (line.contains(ASYNC_MARKER) && line.contains("async-io-gate-enabled=true")) {
                    seen[2] = true;
                }
                if (line.contains(ASYNC_MARKER) && line.contains("async-chunk-runtime-initialized")) {
                    seen[3] = true;
                }
                if (line.contains(SIM_MARKER)) {
                    simMarkerLines++;
                }
                if (line.contains(SIM_MARKER) && line.contains("sim-shell-gate=on")) {
                    seen[4] = true;
                }
                if (line.contains(SIM_MARKER) && line.contains("sim-core-composed-ok")) {
                    seen[5] = true;
                }
                if (line.contains(WORLD_MARKER)) {
                    worldMarkerLines++;
                }
                if (line.contains(WORLD_MARKER) && line.contains("world-shell-gate=on")) {
                    seen[6] = true;
                }
                if (line.contains(WORLD_MARKER) && line.contains("world-pack closed-loop OK")) {
                    seen[7] = true;
                }
                if (line.contains(SAVEVERIFY_MARKER)) {
                    saveverifyMarkerLines++;
                }
                if (line.contains(SAVEVERIFY_MARKER) && line.contains("PASS")) {
                    seen[8] = true;
                }
                if (line.contains(SESSION_MARKER)) {
                    sessionMarkerLines++;
                }
                if (line.contains(SESSION_MARKER) && line.contains("PASS")) {
                    seen[9] = true;
                }
                if (line.contains(LAUNCH_MARKER)) {
                    launchMarkerLines++;
                }
                if (line.contains(LAUNCH_MARKER) && line.contains("ok (checks=")) {
                    seen[10] = true;
                }
                if (line.contains(FIX_MARKER)) {
                    fixMarkerLines++;
                }
                if (line.contains(FIX_MARKER) && line.contains("ok (open=")) {
                    seen[11] = true;
                }
                if (line.contains(CFGLOG_MARKER)) {
                    cfglogMarkerLines++;
                }
                if (line.contains(CFGLOG_MARKER) && line.contains("config ok (rules=")) {
                    seen[12] = true;
                }
                if (line.contains(CFGLOG_MARKER) && line.contains("log ok (ring=")) {
                    seen[13] = true;
                }
                if (line.contains(TIE_MARKER)) {
                    tieMarkerLines++;
                }
                if (line.contains(TIE_MARKER)
                        && (line.contains("ok (lib=") || line.contains("skip (no tie lib)"))) {
                    seen[14] = true;
                }
                if (line.contains(RENDER_MARKER)) {
                    renderMarkerLines++;
                }
                if (line.contains(RENDER_MARKER) && line.contains("ok (backends=")) {
                    seen[15] = true;
                }
                if (line.contains(RENDER_MARKER) && line.contains("hooks ok (level=")) {
                    seen[16] = true;
                }
                if (line.contains(ANIM_MARKER)) {
                    animMarkerLines++;
                }
                if (line.contains(ANIM_MARKER) && line.contains("ok (states=")) {
                    seen[17] = true;
                }
                if (line.contains(UI_MARKER)) {
                    uiMarkerLines++;
                }
                if (line.contains(UI_MARKER) && line.contains("ok (hud=")) {
                    seen[18] = true;
                }
                if (line.contains(FATAL_MARKER) || line.contains(BUILD_FAILED_MARKER)) {
                    fatal = true;
                }
                if (all(seen) || fatal) {
                    try {
                        process.getOutputStream().write("stop\n".getBytes(StandardCharsets.UTF_8));
                        process.getOutputStream().flush();
                    } catch (IOException ignored) {
                        // process may be exiting; probe owns cleanup below
                    }
                    System.out.println("[AsyncE2EProbe] contract evidence complete, shutting down");
                    break;
                }
                if (System.currentTimeMillis() > deadline) {
                    System.out.println("[AsyncE2EProbe] boot deadline exceeded");
                    break;
                }
            }
        }

        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            System.out.println("[AsyncE2EProbe] forcing termination of the boot process");
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            try {
                new ProcessBuilder("taskkill", "/PID", Long.toString(process.pid()), "/T", "/F")
                        .inheritIO().start().waitFor(15, TimeUnit.SECONDS);
            } catch (IOException ignored) {
                // already gone
            }
            reapPort(PROBE_PORT);
        }

        boolean pass = all(seen) && !fatal && engineCoreOk;
        System.out.println();
        System.out.println("[AsyncE2EProbe] done=" + seen[0] + " baselineDefaultOff=" + seen[1]
                + " gateEnabled=" + seen[2] + " runtimeInitialized=" + seen[3]
                + " engineCore=" + engineCoreOk + " asyncMarkerLines=" + asyncMarkerLines
                + " simGate=" + seen[4] + " simComposed=" + seen[5] + " simMarkerLines=" + simMarkerLines
                + " worldGate=" + seen[6] + " worldClosedLoop=" + seen[7] + " worldMarkerLines=" + worldMarkerLines
                + " saveverifyPass=" + seen[8] + " saveverifyMarkerLines=" + saveverifyMarkerLines
                + " sessionPass=" + seen[9] + " sessionMarkerLines=" + sessionMarkerLines
                + " launchOk=" + seen[10] + " launchMarkerLines=" + launchMarkerLines
                + " fixOk=" + seen[11] + " fixMarkerLines=" + fixMarkerLines
                + " cfglogConfigOk=" + seen[12] + " cfglogLogOk=" + seen[13]
                + " cfglogMarkerLines=" + cfglogMarkerLines
                + " tieOkOrSkip=" + seen[14] + " tieMarkerLines=" + tieMarkerLines
                + " renderOk=" + seen[15] + " renderMarkerLines=" + renderMarkerLines
                + " hooksOk=" + seen[16]
                + " animOk=" + seen[17] + " animMarkerLines=" + animMarkerLines
                + " uiOk=" + seen[18] + " uiMarkerLines=" + uiMarkerLines
                + " fatal=" + fatal);
        if (pass) {
            System.out.println("[AsyncE2EProbe] PASS (async + sim + world + saveverify + launch + fix + cfglog + tie + render+hooks + anim + ui shells fired on a live server, "
                    + asyncMarkerLines + " [Subterra async] + " + simMarkerLines
                    + " [Subterra sim] + " + worldMarkerLines
                    + " [Subterra world] + " + saveverifyMarkerLines
                    + " [Subterra saveverify] + " + sessionMarkerLines
                    + " [Subterra session] + " + launchMarkerLines
                    + " [Subterra launch] + " + fixMarkerLines
                    + " [Subterra fix] + " + cfglogMarkerLines
                    + " [Subterra cfglog] + " + tieMarkerLines
                    + " [Subterra tie] + " + renderMarkerLines
                    + " [Subterra render] + " + animMarkerLines
                    + " [Subterra anim] + " + uiMarkerLines
                    + " [Subterra ui] line(s) observed)");
            System.exit(0);
        } else {
            System.out.println("[AsyncE2EProbe] FAIL: async/sim/world/saveverify/launch/fix/cfglog/tie/render+hooks/anim/ui shell contract not met");
            System.exit(1);
        }
    }

    private static boolean all(boolean[] a) {
        for (boolean b : a) {
            if (!b) {
                return false;
            }
        }
        return true;
    }

    // ---------- headless pure-JVM engine-core check (in the probe JVM, NOT the game) ----------

    /**
     * Drives a fixed 2×2 coordinate set through {@link AsyncChunkPipeline#generate} (parallel, P=4)
     * vs {@code generateSerial} on the same producer and asserts the merged results are
     * byte-identical per chunk and order. Also asserts the engine async I/O gate is default-off in a
     * fresh JVM. This confirms the shipped engine still runs off the devkit classpath in the E2E
     * harness without depending on game timings.
     */
    private static boolean engineCoreCheck() {
        try {
            boolean ioDefaultOff = !AsyncIoQueue.isEnabled();
            System.out.println("[AsyncE2EProbe] engine-core: async I/O gate default off in probe JVM = " + ioDefaultOff);

            ChunkProducer producer = makeProducer(WORLD_SEED);
            List<ChunkKey> coords = new ArrayList<>(4);
            coords.add(new ChunkKey(0, 0));
            coords.add(new ChunkKey(1, 0));
            coords.add(new ChunkKey(0, 1));
            coords.add(new ChunkKey(1, 1));

            AsyncChunkPipeline par = AsyncChunkPipeline.of(producer, r -> {
            }, WORLD_SEED, 4);
            List<ChunkResult> parallel;
            try {
                parallel = par.generate(coords);
            } finally {
                par.close();
            }

            AsyncChunkPipeline ser = AsyncChunkPipeline.of(producer, r -> {
            }, WORLD_SEED, 1);
            List<ChunkResult> serial;
            try {
                serial = ser.generateSerial(coords);
            } finally {
                ser.close();
            }

            boolean identical = listsEqual(parallel, serial) && parallel.size() == coords.size();
            System.out.println("[AsyncE2EProbe] engine-core: 2x2 parallel vs serial byte-identical = " + identical);
            return ioDefaultOff && identical;
        } catch (Throwable t) {
            System.out.println("[AsyncE2EProbe] engine-core check threw: " + t);
            t.printStackTrace();
            return false;
        }
    }

    /**
     * A tiny deterministic producer: folds the chunk coords + 8 bytes drawn from the isolated
     * per-chunk deterministic random, so parallel-vs-serial byte identity is a meaningful check.
     */
    private static ChunkProducer makeProducer(final long worldSeed) {
        return (cx, cz, ctx) -> {
            byte[] out = new byte[10];
            out[0] = (byte) cx;
            out[1] = (byte) cz;
            for (int i = 2; i < 10; i++) {
                out[i] = (byte) (ctx.random().nextInt(256) & 0xFF);
            }
            return out;
        };
    }

    /** True when two merged result lists are element-wise byte-identical (same keys, same order). */
    private static boolean listsEqual(List<ChunkResult> a, List<ChunkResult> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            ChunkResult ra = a.get(i);
            ChunkResult rb = b.get(i);
            if (ra.x() != rb.x() || ra.z() != rb.z()
                    || !Arrays.equals(ra.payload(), rb.payload())) {
                return false;
            }
        }
        return true;
    }

    // ---------- boot hygiene (mirrors NetworkE2EProbe / SaveE2EProbe) ----------

    /**
     * Stages the bundled tiec dll ({@code /tie/tiefib_probe.dll}, the same TieBridgeProbe ABI) into
     * {@code build/tmp/tie-e2e} and returns its absolute path; null when the resource is missing
     * (the game JVM then sees no tie lib and TieRuntime prints the deterministic skip marker).
     */
    private static String stageTieLib(Path root) throws Exception {
        Path target = root.resolve("build/tmp/tie-e2e/tiefib_probe.dll");
        try (InputStream in = AsyncE2EProbe.class.getResourceAsStream("/tie/tiefib_probe.dll")) {
            if (in == null) {
                System.out.println("[AsyncE2EProbe] tie lib resource missing — expecting the tie shell skip marker");
                return null;
            }
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[AsyncE2EProbe] staged tie lib: " + target);
            return target.toString();
        }
    }

    private static void ensureAssetProperties(Path root) throws Exception {
        Path props = root.resolve("build/moddev/minecraft_assets.properties");
        if (Files.exists(props)) {
            return;
        }
        Path assets = Paths.get(System.getProperty("user.home"), ".gradle", "caches", "neoformruntime", "assets");
        Properties p = new Properties();
        p.setProperty("assets_root", assets.toString());
        p.setProperty("asset_index", "17");
        Files.createDirectories(props.getParent());
        try (var out = Files.newOutputStream(props)) {
            p.store(out, "generated by AsyncE2EProbe (dev-run asset reference)");
        }
        System.out.println("[AsyncE2EProbe] wrote " + props);
    }

    private static void reapPort(int port) {
        try {
            Process np = new ProcessBuilder("netstat", "-ano").redirectErrorStream(true).start();
            java.util.Set<Long> pids = new java.util.HashSet<>();
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "\\s+TCP\\s+.*:" + port + "\\s+.*LISTENING\\s+(\\d+)\\s*");
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(np.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    var m = pattern.matcher(line);
                    if (m.find()) {
                        try {
                            pids.add(Long.parseLong(m.group(1)));
                        } catch (NumberFormatException ignored) {
                            // not a real PID
                        }
                    }
                }
            }
            np.waitFor(15, TimeUnit.SECONDS);
            for (Long pid : pids) {
                if (pid != 0 && pid != 4) {
                    new ProcessBuilder("taskkill", "/PID", pid.toString(), "/T", "/F")
                            .inheritIO().start().waitFor(15, TimeUnit.SECONDS);
                    System.out.println("[AsyncE2EProbe] reaped game pid " + pid + " (port " + port + ")");
                }
            }
        } catch (IOException | InterruptedException ignored) {
            // best-effort cleanup
        }
    }
}