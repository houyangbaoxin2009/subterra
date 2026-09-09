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
 * p.2.4.6 enhanced-network-channel end-to-end gate: forks {@code gradlew runServer} with
 * {@code -Psubterra.probe.network=1} and asserts the {@code EnhancedChannelRuntime} shell emits the
 * deterministic markers ({@code [Subterra network]}) at ServerStartedEvent:
 * <ul>
 *   <li>{@code enhanced-channel-initialized} — engine network modules load and the core components
 *       (frame v2 codec, bandwidth optimizer, strategy selector, encryption toggle) instantiate with
 *       no linkage errors;</li>
 *   <li>{@code original-path-preserved} — the vanilla handshake handlers stay registered and are
 *       neither unregistered nor overwritten (progressive enhancement, vanilla clients still connect);</li>
 *   <li>{@code encryption-enabled-by-default=true} — the default L3 encrypted path is on.</li>
 * </ul>
 * Event-based (never timing-based), mirrors the {@code SaveE2EProbe} / {@code DatapackE2EProbe} fork
 * mechanism; the markers are fully independent of the save/datapack markers (prefix {@code [Subterra network]}).
 * Exit 0 = PASS, exit 1 = FAIL.
 */
public final class NetworkE2EProbe {

    private static final String DONE_MARKER = "Done (";
    private static final String FATAL_MARKER = "FATAL";
    private static final String BUILD_FAILED_MARKER = "BUILD FAILED";
    private static final long BOOT_DEADLINE_MINUTES = 6;
    private static final int PROBE_PORT = 25599;
    private static final String NET_MARKER = "[Subterra network]";

    private NetworkE2EProbe() {
    }

    public static void main(String[] args) throws Exception {
        String projectDir = System.getProperty("subterra.projectDir", System.getProperty("user.dir", "."));
        Path root = Paths.get(projectDir).toAbsolutePath();
        String gradlew = root.resolve(System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "gradlew.bat" : "gradlew").toString();
        if (!Files.isRegularFile(Paths.get(gradlew))) {
            System.out.println("[NetworkE2EProbe] project dir not found: " + root);
            System.exit(1);
        }

        ensureAssetProperties(root);
        reapPort(PROBE_PORT);

        ProcessBuilder pb = new ProcessBuilder(
                gradlew, "runServer", "-x", "downloadAssets",
                "--console=plain", "--no-daemon",
                "-Psubterra.probe.network=1");
        pb.directory(root.toFile());
        pb.redirectErrorStream(true);
        pb.environment().merge("JAVA_TOOL_OPTIONS", "-Djava.net.preferIPv4Stack=true",
                (o, n) -> o.isBlank() ? n : o + " " + n);

        Process process = pb.start();

        // 0 = booted (Done), 1 = enhanced-channel-initialized, 2 = original-path-preserved,
        // 3 = encryption-enabled-by-default
        boolean[] seen = new boolean[4];
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
                if (line.contains(NET_MARKER) && line.contains("enhanced-channel-initialized")) {
                    seen[1] = true;
                }
                if (line.contains(NET_MARKER) && line.contains("original-path-preserved")) {
                    seen[2] = true;
                }
                if (line.contains(NET_MARKER) && line.contains("encryption-enabled-by-default=true")) {
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
                    System.out.println("[NetworkE2EProbe] contract evidence complete, shutting down");
                    break;
                }
                if (System.currentTimeMillis() > deadline) {
                    System.out.println("[NetworkE2EProbe] boot deadline exceeded");
                    break;
                }
            }
        }

        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            System.out.println("[NetworkE2EProbe] forcing termination of the boot process");
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
        System.out.println("[NetworkE2EProbe] done=" + seen[0] + " enhancedChannel=" + seen[1]
                + " originalPath=" + seen[2] + " encapsulationDefault=" + seen[3] + " fatal=" + fatal);
        if (pass) {
            System.out.println("[NetworkE2EProbe] PASS (enhanced channel shell fired on a live server)");
            System.exit(0);
        } else {
            System.out.println("[NetworkE2EProbe] FAIL: enhanced-network channel contract not met");
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

    // ---------- boot hygiene (mirrors DatapackE2EProbe / SaveE2EProbe) ----------

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
            p.store(out, "generated by NetworkE2EProbe (dev-run asset reference)");
        }
        System.out.println("[NetworkE2EProbe] wrote " + props);
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
                    System.out.println("[NetworkE2EProbe] reaped game pid " + pid + " (port " + port + ")");
                }
            }
        } catch (IOException | InterruptedException ignored) {
            // best-effort cleanup
        }
    }
}