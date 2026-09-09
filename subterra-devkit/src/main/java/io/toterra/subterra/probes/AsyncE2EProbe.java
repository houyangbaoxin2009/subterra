package io.toterra.subterra.probes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * (p.2.8.6): the runServer line also passes {@code -Psubterra.probe.sim=1} and the probe asserts
 * the {@code SimRuntime} shell prints its {@code [Subterra sim]} markers
 * ({@code sim-shell-gate=on} and, once every engine.sim micro-check passes,
 * {@code sim-core-composed-ok}). Event-driven, timing-free — it is a union gate that asserts both
 * the async and the sim shell in one live-server launch, so the 5-boot total is unchanged. All
 * async assertions are kept verbatim; the sim slots are purely additional.
 * <p>
 * Before forking, a headless pure-JVM engine-core check runs inside the probe JVM (NOT the game):
 * a 2×2 fixed-coordinate set is pushed through {@link AsyncChunkPipeline.generate} (parallel, P=4)
 * vs {@code generateSerial} on the same producer and asserted byte-identical — this confirms the
 * shipped engine still runs off the devkit classpath in the E2E harness. Event-based (never
 * timing-based), mirrors the {@code NetworkE2EProbe} / {@code SaveE2EProbe} fork mechanism; the
 * markers are fully independent of the network/save/datapack markers (prefix {@code [Subterra async]}
 * and {@code [Subterra sim]}). Exit 0 = PASS, exit 1 = FAIL.
 * <p>
 * p.2.8.6 起，<em>同一次</em>开服同时并入 {@code engine.sim} 壳（p.2.8.6）：runServer 参数行另加
 * {@code -Psubterra.probe.sim=1}，探针断言 {@code SimRuntime} 壳打印其 {@code [Subterra sim]}
 * marker（{@code sim-shell-gate=on}，以及当所有 engine.sim 微校验均通过时的
 * {@code sim-core-composed-ok}）。事件驱动、禁时序——它是一次性并断言 async 与 sim 两个壳的
 * 联合门，故 5 次开服总数不变。既有 async 断言逐字保持；sim 槽纯属新增。
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

        ProcessBuilder pb = new ProcessBuilder(
                gradlew, "runServer", "-x", "downloadAssets",
                "--console=plain", "--no-daemon",
                "-Psubterra.probe.async=1", "-Psubterra.probe.sim=1");
        pb.directory(root.toFile());
        pb.redirectErrorStream(true);
        pb.environment().merge("JAVA_TOOL_OPTIONS", "-Djava.net.preferIPv4Stack=true",
                (o, n) -> o.isBlank() ? n : o + " " + n);

        Process process = pb.start();

        // 0 = booted (Done), 1 = baseline-IO-default-off, 2 = async-I/O-gate-enabled,
        // 3 = async-chunk-runtime-initialized,
        // 4 = sim-shell-gate-on, 5 = sim-core-composed-ok   (sim slots, p.2.8.6)
        boolean[] seen = new boolean[6];
        boolean fatal = false;
        int asyncMarkerLines = 0;
        int simMarkerLines = 0;
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
                + " fatal=" + fatal);
        if (pass) {
            System.out.println("[AsyncE2EProbe] PASS (async + sim shells fired on a live server, "
                    + asyncMarkerLines + " [Subterra async] + " + simMarkerLines
                    + " [Subterra sim] line(s) observed)");
            System.exit(0);
        } else {
            System.out.println("[AsyncE2EProbe] FAIL: async/sim shell contract not met");
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