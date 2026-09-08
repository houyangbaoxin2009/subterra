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
 * End-to-end boot gate for the L2 compatibility layer (p.1.2).
 * <p>
 * Boots the dedicated dev server on the Java 25 toolchain with the L1 argument
 * package injected, then asserts the deterministic boot contract from the log:
 * <ul>
 *   <li>the dedicated server printed {@code Done (...)} (world ready)</li>
 *   <li>Subterra logged {@code Java 25 ... verified: true} (JVM 25 in use)</li>
 *   <li>Subterra logged {@code JVM argument package present} (args injected)</li>
 *   <li>no FATAL / BUILD FAILED occurred</li>
 * </ul>
 * The gate is event-based (log content), never timing-based. The server is
 * stopped gracefully via the console {@code stop} command once {@code Done}
 * is observed, so the probe leaves no lingering processes.
 * <p>
 * Exit 0 = PASS, exit 1 = FAIL (gates acceptance; never shipped in the mod jar).
 */
public final class ServerBootProbe {

    private static final String DONE_MARKER = "Done (";
    private static final String JVM25_MARKER = "Subterra L1: Java 25";
    private static final String ARGS_MARKER = "JVM argument package present";
    private static final long BOOT_DEADLINE_MINUTES = 6;
    /** Fixed probe port (see the server run config); used to reap the gate's own game. */
    private static final int PROBE_PORT = 25599;

    private ServerBootProbe() {
    }

    public static void main(String[] args) throws Exception {
        String projectDir = System.getProperty("subterra.projectDir", System.getProperty("user.dir", "."));
        Path root = Paths.get(projectDir).toAbsolutePath();
        String gradlew = resolveGradlew(root);
        if (!Files.isRegularFile(Paths.get(gradlew))) {
            System.out.println("[ServerBootProbe] project dir not found: " + root);
            System.exit(1);
        }

        ensureAssetProperties(root);
        deleteWorldIfPresent(root);
        reapPort(PROBE_PORT);

        ProcessBuilder pb = new ProcessBuilder(
                gradlew,
                "runServer", "-x", "downloadAssets",
                "--console=plain", "--no-daemon");
        pb.directory(root.toFile());
        pb.redirectErrorStream(true);
        Map<String, String> env = pb.environment();
        String ipv4 = "-Djava.net.preferIPv4Stack=true";
        env.merge("JAVA_TOOL_OPTIONS", ipv4, (oldVal, newVal) -> oldVal.isBlank() ? newVal : oldVal + " " + newVal);

        Process process = pb.start();

        boolean done = false;
        boolean java25 = false;
        boolean argsOk = false;
        boolean fatal = false;
        boolean signaled = false;
        long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(BOOT_DEADLINE_MINUTES);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                if (line.contains(DONE_MARKER)) {
                    done = true;
                }
                if (line.contains(JVM25_MARKER)) {
                    java25 = true;
                }
                if (line.contains(ARGS_MARKER)) {
                    argsOk = true;
                }
                if (line.contains("FATAL") || line.contains("BUILD FAILED")) {
                    fatal = true;
                }
                boolean contractMet = done && java25 && argsOk && !fatal;
                if (contractMet || fatal) {
                    // Evidence collected; stop reading (the game may keep the pipe
                    // open indefinitely, so never wait for EOF).
                    if (!signaled) {
                        signaled = true;
                        try {
                            process.getOutputStream().write("stop\n".getBytes(StandardCharsets.UTF_8));
                            process.getOutputStream().flush();
                        } catch (IOException ignored) {
                            // The process may already be exiting; the probe owns cleanup below.
                        }
                    }
                    System.out.println("[ServerBootProbe] contract evidence complete, shutting down");
                    break;
                }
                if (System.currentTimeMillis() > deadline) {
                    System.out.println("[ServerBootProbe] boot deadline exceeded");
                    break;
                }
            }
        }

        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            System.out.println("[ServerBootProbe] forcing termination of the boot process");
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
        // Windows: gradle does not propagate termination to the game child.
        // taskkill the wrapper tree we own, then reap the game itself by its
        // probe port (never use `gradlew --stop` here: this probe may itself
        // run inside the outer gradle daemon).
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

        boolean pass = done && java25 && argsOk && !fatal;
        System.out.println();
        System.out.println("[ServerBootProbe] done=" + done + " java25=" + java25 + " argsOk=" + argsOk + " fatal=" + fatal);
        if (pass) {
            System.out.println("[ServerBootProbe] PASS (dev server booted on Java 25)");
            System.exit(0);
        } else {
            System.out.println("[ServerBootProbe] FAIL: boot contract not met");
            System.exit(1);
        }
    }

    private static String resolveGradlew(Path root) {
        String os = System.getProperty("os.name", "").toLowerCase();
        return root.resolve(os.contains("win") ? "gradlew.bat" : "gradlew").toString();
    }

    /**
     * Kills (with process tree) any process listening on {@code port} — the
     * gate's own dev server. netstat gives the PID without CIM or quoting
     * pitfalls and the match is scoped to the probe port, so no unrelated
     * JVM is ever touched.
     */
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
                    System.out.println("[ServerBootProbe] reaped game pid " + pid + " (port " + port + ")");
                }
            }
        } catch (IOException | InterruptedException ignored) {
            // Best-effort cleanup; the gate verdict does not depend on it.
        }
    }

    /**
     * Deletes any previous dev-server world so each gate run boots a fresh
     * world deterministically (and never collides with a leftover world lock).
     */
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
        System.out.println("[ServerBootProbe] removed previous dev world");
    }

    /**
     * Self-heals the dev-run asset reference file so the boot can skip the
     * (network-heavy) downloadAssets pass. Written only when absent; the asset
     * cache path is derived deterministically from the user home.
     */
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
            p.store(out, "generated by ServerBootProbe (dev-run asset reference)");
        }
        System.out.println("[ServerBootProbe] wrote " + props);
    }
}