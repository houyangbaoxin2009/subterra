package io.toterra.subterra.probes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * p.2.3.2 level.dat → zdt end-to-end gate: forks {@code gradlew runServer} with
 * {@code -Psubterra.probe.save=1} and asserts the {@code SaveRuntime} shell emits the three
 * deterministic markers ({@code [Subterra save]}) at ServerStartedEvent:
 * <ul>
 *   <li>{@code level name="&lt;name&gt;"} — a non-empty world name (reuses the previously booted
 *       {@code run/world/level.dat}; the world is deliberately NOT deleted here, unlike
 *       DatapackE2EProbe, so the level data exists on disk at ServerStartedEvent);</li>
 *   <li>{@code level seed=&lt;seed&gt; time=&lt;time&gt; rules=&lt;rules&gt;} — seed/time present and
 *       a deterministic rules rendering;</li>
 *   <li>{@code zdt td-bytes=&lt;len&gt; zd-header=TIEDBZD:&lt;version&gt;} — the td document byte length
 *       and a valid zd v2 header.</li>
 * </ul>
 * Event-based (never timing-based), mirrors the {@code DatapackE2EProbe} fork mechanism; the markers
 * are fully independent of the existing 31 datapack markers (prefix {@code [Subterra save]}).
 * Exit 0 = PASS, exit 1 = FAIL.
 */
public final class SaveE2EProbe {

    private static final String DONE_MARKER = "Done (";
    private static final String FATAL_MARKER = "FATAL";
    private static final String BUILD_FAILED_MARKER = "BUILD FAILED";
    private static final long BOOT_DEADLINE_MINUTES = 6;
    private static final int PROBE_PORT = 25599;
    private static final String SAVE_MARKER = "[Subterra save]";

    private SaveE2EProbe() {
    }

    public static void main(String[] args) throws Exception {
        String projectDir = System.getProperty("subterra.projectDir", System.getProperty("user.dir", "."));
        Path root = Paths.get(projectDir).toAbsolutePath();
        String gradlew = root.resolve(System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "gradlew.bat" : "gradlew").toString();
        if (!Files.isRegularFile(Paths.get(gradlew))) {
            System.out.println("[SaveE2EProbe] project dir not found: " + root);
            System.exit(1);
        }

        ensureAssetProperties(root);
        reapPort(PROBE_PORT);

        ProcessBuilder pb = new ProcessBuilder(
                gradlew, "runServer", "-x", "downloadAssets",
                "--console=plain", "--no-daemon",
                "-Psubterra.probe.save=1");
        pb.directory(root.toFile());
        pb.redirectErrorStream(true);
        pb.environment().merge("JAVA_TOOL_OPTIONS", "-Djava.net.preferIPv4Stack=true",
                (o, n) -> o.isBlank() ? n : o + " " + n);

        Process process = pb.start();

        boolean[] seen = new boolean[4]; // 0 done, 1 name non-empty, 2 seed/time/rules, 3 zdt line
        boolean fatal = false;
        long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(BOOT_DEADLINE_MINUTES);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                if (line.contains(DONE_MARKER)) {
                    seen[0] = true;
                }
                if (line.contains(SAVE_MARKER) && line.contains("level name=\"") && !line.contains("name=\"\"")) {
                    seen[1] = true;
                }
                if (line.contains(SAVE_MARKER + " level seed=") && line.contains(" time=") && line.contains(" rules=")) {
                    seen[2] = true;
                }
                if (line.contains(SAVE_MARKER + " zdt td-bytes=") && line.contains("zd-header=TIEDBZD:")) {
                    seen[3] = true;
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
                    System.out.println("[SaveE2EProbe] contract evidence complete, shutting down");
                    break;
                }
                if (System.currentTimeMillis() > deadline) {
                    System.out.println("[SaveE2EProbe] boot deadline exceeded");
                    break;
                }
            }
        }

        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            System.out.println("[SaveE2EProbe] forcing termination of the boot process");
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

        boolean pass = all(seen) && !fatal;
        System.out.println();
        System.out.println("[SaveE2EProbe] done=" + seen[0] + " name=" + seen[1] + " seedTimeRules=" + seen[2]
                + " zdt=" + seen[3] + " fatal=" + fatal);
        if (pass) {
            System.out.println("[SaveE2EProbe] PASS (level.dat -> zdt migration shell fired on a live server)");
            System.exit(0);
        } else {
            System.out.println("[SaveE2EProbe] FAIL: save migration contract not met");
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

    // ---------- boot hygiene (mirrors DatapackE2EProbe) ----------

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
            p.store(out, "generated by SaveE2EProbe (dev-run asset reference)");
        }
        System.out.println("[SaveE2EProbe] wrote " + props);
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
                    System.out.println("[SaveE2EProbe] reaped game pid " + pid + " (port " + port + ")");
                }
            }
        } catch (IOException | InterruptedException ignored) {
            // best-effort cleanup
        }
    }
}