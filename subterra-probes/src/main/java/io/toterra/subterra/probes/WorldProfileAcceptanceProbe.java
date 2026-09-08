package io.toterra.subterra.probes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * p.1.8.30 world-generator acceptance driver built on the World Profiler module.
 * <p>
 * Boots the dev server with a deterministic world (seed + level-type preset written
 * into {@code run/server.properties}, previous world removed), then drives the
 * profiler headlessly through system properties: {@code subterra.profile=<radius>}
 * with {@code subterra.profileBuild=true} force-generates the window and writes
 * {@code run/profile/profile.zd|.td}; {@code subterra.profileSlice=axis:start:length:
 * unit[:planes[:positions]]} additionally collects an axial slice. The probe watches
 * the server log for the profiler completion markers and reaps the game by its
 * probe port, exactly like {@link ServerBootProbe}.
 * <p>
 * Entry / exit: system property {@code subterra.seed} (default 44905237),
 * {@code subterra.levelType} (default {@code subterra:subterra}; pass
 * {@code minecraft:normal} for a vanilla baseline), {@code subterra.profileRadius}
 * (default 6), {@code subterra.profileBuild} (default true),
 * {@code subterra.profileSlice} (default {@code x:-16:16:block:1:0}).
 * Exit 0 = PASS iff the stats and slice markers were logged, the report files were
 * written non-empty, and no FATAL / BUILD FAILED occurred.
 * <p>
 * p.1.8.30 世界生成器验收驱动，基于 World Profiler 模块。以确定性世界（种子+预设写入
 * {@code run/server.properties}、删除旧世界）启动开发服务器，并经系统属性驱动分析器无头运行：
 * {@code subterra.profile=<半径>} + {@code subterra.profileBuild=true} 强制生成窗口并写出
 * {@code run/profile/profile.zd|.td}；{@code subterra.profileSlice=axis:start:length:unit[:planes[:positions]]}
 * 额外采集轴向切片。观察服务端日志中的分析完成标记，按探针端口回收游戏进程
 * （与 {@link ServerBootProbe} 相同）。
 */
public final class WorldProfileAcceptanceProbe {

    private static final String DONE_MARKER = "Done (";
    private static final String PROFILE_MARKER = "[subterra_profiler] auto profile radius=";
    private static final String SLICE_MARKER = "[subterra_profiler] auto slice ";
    private static final long DEADLINE_MINUTES = 10;
    private static final int PROBE_PORT = 25599;

    private WorldProfileAcceptanceProbe() {
    }

    public static void main(String[] args) throws Exception {
        String projectDir = System.getProperty("subterra.projectDir", System.getProperty("user.dir", "."));
        Path root = Paths.get(projectDir).toAbsolutePath();
        String gradlew = resolveGradlew(root);
        if (!Files.isRegularFile(Paths.get(gradlew))) {
            System.out.println("[WorldProfileAcceptance] project dir not found: " + root);
            System.exit(1);
        }

        long seed = Long.parseLong(System.getProperty("subterra.seed", "44905237").trim());
        String levelType = System.getProperty("subterra.levelType", "subterra:subterra").trim();
        String radius = System.getProperty("subterra.profileRadius", "2").trim();
        boolean build = Boolean.parseBoolean(System.getProperty("subterra.profileBuild", "true").trim());
        String slice = System.getProperty("subterra.profileSlice", "").trim();
        String center = System.getProperty("subterra.profileCenter", "4000,4000").trim();

        ensureAssetProperties(root);
        writeServerProperties(root, seed, levelType);
        deleteWorldIfPresent(root);
        reapPort(PROBE_PORT);

        // Gradle only honours -D system properties placed BEFORE the task name.
        ProcessBuilder pb = new ProcessBuilder(
                gradlew,
                "-Dsubterra.profile=" + radius,
                "-Dsubterra.profileBuild=" + build,
                "-Dsubterra.profileSlice=" + slice,
                "runServer", "-x", "downloadAssets", "--console=plain", "--no-daemon",
                // Never reuse a stale configuration-cache entry: the -D props must be
                // re-read from THIS invocation (a cached config would forward empties).
                "--no-configuration-cache");
        pb.directory(root.toFile());
        pb.redirectErrorStream(true);
        Map<String, String> env = pb.environment();
        String ipv4 = "-Djava.net.preferIPv4Stack=true";
        env.merge("JAVA_TOOL_OPTIONS", ipv4, (oldVal, newVal) -> oldVal.isBlank() ? newVal : oldVal + " " + newVal);
        // Env transport: survives the gradle -> game process hops even if -D parsing drifts.
        env.put("SUBTERRA_PROFILE", radius);
        env.put("SUBTERRA_PROFILE_BUILD", Boolean.toString(build));
        env.put("SUBTERRA_PROFILE_SLICE", slice);
        env.put("SUBTERRA_PERF_COUNT", System.getProperty("subterra.perfCount", "true").trim());
        env.put("SUBTERRA_PROFILE_CENTER", center);

        Process process = pb.start();
        System.out.println("[WorldProfileAcceptance] booting server (seed=" + seed + ", level-type=" + levelType
                + ", radius=" + radius + ", build=" + build + ", slice=" + slice + ")");

        boolean done = false;
        boolean profileDone = false;
        boolean sliceDone = false;
        boolean fatal = false;
        boolean signaled = false;
        long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(DEADLINE_MINUTES);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                if (line.contains(DONE_MARKER)) {
                    done = true;
                }
                if (line.contains(PROFILE_MARKER)) {
                    profileDone = true;
                }
                if (line.contains(SLICE_MARKER)) {
                    sliceDone = true;
                }
                if (line.contains("FATAL") || line.contains("BUILD FAILED")) {
                    fatal = true;
                }
                boolean sliceOk = slice.isBlank() || sliceDone;
                boolean contractMet = profileDone && sliceOk && !fatal;
                boolean filesOk = reportFilesNonEmpty(root);
                if ((contractMet && filesOk) || fatal) {
                    if (!signaled) {
                        signaled = true;
                        try {
                            process.getOutputStream().write("stop\n".getBytes(StandardCharsets.UTF_8));
                            process.getOutputStream().flush();
                        } catch (IOException ignored) {
                            // The process may already be exiting; the probe owns cleanup below.
                        }
                    }
                    System.out.println("[WorldProfileAcceptance] evidence complete (filesOk=" + filesOk
                            + "), shutting down");
                    break;
                }
                if (System.currentTimeMillis() > deadline) {
                    System.out.println("[WorldProfileAcceptance] deadline exceeded");
                    break;
                }
            }
        }

        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            System.out.println("[WorldProfileAcceptance] forcing termination of the boot process");
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            try {
                new ProcessBuilder("taskkill", "/PID", Long.toString(process.pid()), "/T", "/F")
                        .inheritIO().start().waitFor(15, TimeUnit.SECONDS);
            } catch (IOException ignored) {
                // Process already gone.
            }
            reapPort(PROBE_PORT);
        }

        boolean filesOk = reportFilesNonEmpty(root);
        boolean sliceOk = slice.isBlank() || sliceDone;
        boolean pass = done && profileDone && sliceOk && filesOk && !fatal;
        System.out.println();
        System.out.println("[WorldProfileAcceptance] done=" + done + " profile=" + profileDone
                + " slice=" + sliceDone + " filesOk=" + filesOk + " fatal=" + fatal);
        if (pass) {
            System.out.println("[WorldProfileAcceptance] PASS (seed " + seed + ", " + levelType + ")");
            System.exit(0);
        } else {
            System.out.println("[WorldProfileAcceptance] FAIL: acceptance contract not met");
            System.exit(1);
        }
    }

    /** The primary report file present and non-empty under {@code run/profile/}. */
    private static boolean reportFilesNonEmpty(Path root) {
        Path dir = root.resolve("run/profile");
        for (String f : new String[]{"profile.zd"}) {
            Path p = dir.resolve(f);
            try {
                if (!Files.isRegularFile(p) || Files.size(p) <= 0) {
                    return false;
                }
            } catch (IOException e) {
                return false;
            }
        }
        return true;
    }

    private static String resolveGradlew(Path root) {
        String os = System.getProperty("os.name", "").toLowerCase();
        return root.resolve(os.contains("win") ? "gradlew.bat" : "gradlew").toString();
    }

    /** Writes a deterministic dev-server world seed + level-type preset. */
    private static void writeServerProperties(Path root, long seed, String levelType) throws Exception {
        Path props = root.resolve("run/server.properties");
        Files.createDirectories(props.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("level-seed=").append(seed).append('\n');
        sb.append("level-type=").append(levelType).append('\n');
        sb.append("level-name=world\n");
        // The acceptance drives the profiler synchronously on the server thread
        // (window + slice chunk generation); disable the 60s tick watchdog so it
        // cannot kill our own verification run.
        sb.append("max-tick-time=-1\n");
        Files.writeString(props, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("[WorldProfileAcceptance] wrote " + props + " (seed=" + seed + ", level-type=" + levelType + ")");
    }

    private static void deleteWorldIfPresent(Path root) throws Exception {
        Path world = root.resolve("run/world");
        if (!Files.exists(world)) {
            return;
        }
        try (var stream = Files.walk(world)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // Best-effort; a busy file may remain, boot still proceeds.
                }
            });
        }
        System.out.println("[WorldProfileAcceptance] removed previous dev world");
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
                            // Not a real PID.
                        }
                    }
                }
            }
            np.waitFor(15, TimeUnit.SECONDS);
            for (Long pid : pids) {
                if (pid != 0 && pid != 4) {
                    new ProcessBuilder("taskkill", "/PID", pid.toString(), "/T", "/F")
                            .inheritIO().start().waitFor(15, TimeUnit.SECONDS);
                    System.out.println("[WorldProfileAcceptance] reaped game pid " + pid + " (port " + port + ")");
                }
            }
        } catch (IOException | InterruptedException ignored) {
            // Best-effort cleanup; the verdict does not depend on it.
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
            p.store(out, "generated by WorldProfileAcceptanceProbe (dev-run asset reference)");
        }
        System.out.println("[WorldProfileAcceptance] wrote " + props);
    }
}