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
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * End-to-end datapack gate (p.2.2 block 2+4): boots the dev server with a seed
 * td datapack staged under {@code run/datapacks/} and asserts the registration
 * chain from the boot log (never timing-based):
 * <ul>
 *   <li>the pack is loaded (directory scan + pack.td manifest);</li>
 *   <li>tags / lang are indexed (deterministic detail markers);</li>
 *   <li>the td recipe {@code toterra:recipe/example} is compiled to a vanilla
 *   ShapedRecipe and merged into the live RecipeManager;</li>
 *   <li>FUNCTION entries resolve through the tie bridge and are called
 *   (dp_logic$seed_42 / dp_logic$farewell → {@code 42} / {@code 108});</li>
 *   <li>the td loot table {@code toterra:loot_table/chest/bonus} is built and
 *   merged into the LOOT_TABLE datapack registry (visible marker, pools=1);</li>
 *   <li>the td worldgen entry is registered into CONFIGURED_FEATURE and the td
 *   structure entry into STRUCTURE / STRUCTURE_SET (visible markers).</li>
 *   <li>the td-built recipes are exported to canonical td, re-imported through
 *   the registrar builder, and re-exported byte-identical (export round-trip
 *   markers {@code toterra:recipe/example} / {@code toterra:recipe/smoke} ok);
 *   block 6 extends per-kind export round-trip markers for every entry (tag,
 *   lang, loot_table, worldgen, structure, function — kind word per entry) and
 *   the whole-pack archive marker ({@code export archive roundtrip ok} with
 *   {@code entries=10}).</li>
 *   <li>the {@code /subterra export} command writes the loaded pack out as an
 *   export-archive td doc and verifies the export∘rehydrate identity (markers
 *   {@code export cmd ok (packs=1,} and per-pack {@code rehydrate=ok}).</li>
 *   <li>the effective datapack rules are logged from pack.td ({@code rules tick
 *   = 40}) and the save override file wins ({@code rules wild = false}, p.2.2.9).</li>
 * </ul>
 * The probe stages the seed by copying devkit resources into
 * {@code run/datapacks/} fresh each run, so the gate stays deterministic and
 * never depends on committed run/ content. Exit 0 = PASS, exit 1 = FAIL.
 */
public final class DatapackE2EProbe {

    private static final String DONE_MARKER = "Done (";
    private static final String FATAL_MARKER = "FATAL";
    private static final String BUILD_FAILED_MARKER = "BUILD FAILED";
    private static final long BOOT_DEADLINE_MINUTES = 6;
    private static final int PROBE_PORT = 25599;
    private static final String DP_MARKER = "[Subterra datapack]";

    private DatapackE2EProbe() {
    }

    public static void main(String[] args) throws Exception {
        String projectDir = System.getProperty("subterra.projectDir", System.getProperty("user.dir", "."));
        Path root = Paths.get(projectDir).toAbsolutePath();
        String gradlew = root.resolve(System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "gradlew.bat" : "gradlew").toString();
        if (!Files.isRegularFile(Paths.get(gradlew))) {
            System.out.println("[DatapackE2EProbe] project dir not found: " + root);
            System.exit(1);
        }

        ensureAssetProperties(root);
        deleteWorldIfPresent(root);
        String stagedRoot = stageSeedPack(root);
        reapPort(PROBE_PORT);

        ProcessBuilder pb = new ProcessBuilder(
                gradlew, "runServer", "-x", "downloadAssets",
                "--console=plain", "--no-daemon",
                "-Psubterra.datapacks=" + stagedRoot,
                "-Psubterra.override=" + Paths.get(stagedRoot).resolve("overrides.td").toString());
        pb.directory(root.toFile());
        pb.redirectErrorStream(true);
        pb.environment().merge("JAVA_TOOL_OPTIONS", "-Djava.net.preferIPv4Stack=true",
                (o, n) -> o.isBlank() ? n : o + " " + n);

        Process process = pb.start();

        boolean[] seen = new boolean[31];
        boolean fatal = false;
        boolean signaled = false;
        boolean exportIssued = false;
        long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(BOOT_DEADLINE_MINUTES);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                if (line.contains(DONE_MARKER)) {
                    seen[0] = true;
                }
                if (line.contains(DP_MARKER + " loaded 1 packs, 10 entries")) {
                    seen[1] = true;
                }
                if (line.contains(DP_MARKER + " tag toterra:tag/item/special values=[minecraft:stick, minecraft:apple]")) {
                    seen[2] = true;
                }
                if (line.contains(DP_MARKER + " lang item.toterra.crystal = Crystal")) {
                    seen[3] = true;
                }
                if (line.contains(DP_MARKER + " lang entries=2")) {
                    seen[4] = true;
                }
                if (line.contains(DP_MARKER + " register recipe toterra:example")) {
                    seen[5] = true;
                }
                if (line.contains(DP_MARKER + " register recipe toterra:smoke")) {
                    seen[6] = true;
                }
                if (line.contains(DP_MARKER + " recipes registered=2")) {
                    seen[7] = true;
                }
                if (line.contains(DP_MARKER + " tie toterra:function/greet -> 42")) {
                    seen[8] = true;
                }
                if (line.contains(DP_MARKER + " tie toterra:function/farewell -> 108")) {
                    seen[9] = true;
                }
                if (line.contains(DP_MARKER + " recipe canonical toterra:example ok")) {
                    seen[10] = true;
                }
                if (line.contains(DP_MARKER + " recipe canonical toterra:smoke ok")) {
                    seen[11] = true;
                }
                if (line.contains(DP_MARKER + " loot built toterra:loot_table/chest/bonus (pools=1")) {
                    seen[12] = true;
                }
                if (line.contains(DP_MARKER + " loot visible toterra:loot_table/chest/bonus (pools=1, registry=ok")) {
                    seen[13] = true;
                }
                if (line.contains(DP_MARKER + " worldgen visible toterra:worldgen/configured_feature/meadow_of_tie (configured_feature, registry=ok")) {
                    seen[14] = true;
                }
                if (line.contains(DP_MARKER + " structure visible toterra:structure/shrine (structure_set,")) {
                    seen[15] = true;
                }
                if (line.contains(DP_MARKER + " export roundtrip recipe toterra:recipe/example ok")) {
                    seen[16] = true;
                }
                if (line.contains(DP_MARKER + " export roundtrip recipe toterra:recipe/smoke ok")) {
                    seen[17] = true;
                }
                if (line.contains(DP_MARKER + " export roundtrip tag toterra:tag/item/special ok")) {
                    seen[18] = true;
                }
                if (line.contains(DP_MARKER + " export roundtrip lang toterra:lang/en_us ok")) {
                    seen[19] = true;
                }
                if (line.contains(DP_MARKER + " export roundtrip loot_table toterra:loot_table/chest/bonus ok")) {
                    seen[20] = true;
                }
                if (line.contains(DP_MARKER + " export roundtrip worldgen toterra:worldgen/configured_feature/meadow_of_tie ok")) {
                    seen[21] = true;
                }
                if (line.contains(DP_MARKER + " export roundtrip structure toterra:structure/shrine ok")) {
                    seen[22] = true;
                }
                if (line.contains(DP_MARKER + " export roundtrip structure devkit:structure/obligatory_tower ok")) {
                    seen[23] = true;
                }
                if (line.contains(DP_MARKER + " export roundtrip function toterra:function/greet ok")) {
                    seen[24] = true;
                }
                if (line.contains(DP_MARKER + " export roundtrip function toterra:function/farewell ok")) {
                    seen[25] = true;
                }
                if (line.contains(DP_MARKER + " export archive roundtrip ok (entries=10")) {
                    seen[26] = true;
                }
                if (line.contains(DP_MARKER + " export cmd ok (packs=1,")) {
                    seen[27] = true;
                }
                if (line.contains(DP_MARKER + " export cmd ") && line.contains("rehydrate=ok")) {
                    seen[28] = true;
                }
                if (line.contains(DP_MARKER + " rules tick = 40")) {
                    seen[29] = true;
                }
                if (line.contains(DP_MARKER + " rules wild = false")) {
                    seen[30] = true;
                }
                if (line.contains(FATAL_MARKER) || line.contains(BUILD_FAILED_MARKER)) {
                    fatal = true;
                }
                if (seen[0] && !exportIssued) {
                    exportIssued = true;
                    try {
                        process.getOutputStream().write(
                                "subterra export build/tmp/subterra-export-e2e\n".getBytes(StandardCharsets.UTF_8));
                        process.getOutputStream().flush();
                        System.out.println("[DatapackE2EProbe] issued subterra export command");
                    } catch (IOException ignored) {
                        // process may be exiting; probe owns cleanup below
                    }
                }
                if (all(seen) || fatal) {
                    if (!signaled) {
                        signaled = true;
                        try {
                            process.getOutputStream().write("stop\n".getBytes(StandardCharsets.UTF_8));
                            process.getOutputStream().flush();
                        } catch (IOException ignored) {
                            // process may be exiting; probe owns cleanup below
                        }
                    }
                    System.out.println("[DatapackE2EProbe] contract evidence complete, shutting down");
                    break;
                }
                if (System.currentTimeMillis() > deadline) {
                    System.out.println("[DatapackE2EProbe] boot deadline exceeded");
                    break;
                }
            }
        }

        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            System.out.println("[DatapackE2EProbe] forcing termination of the boot process");
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
        System.out.println("[DatapackE2EProbe] done=" + seen[0] + " loaded=" + seen[1] + " tag=" + seen[2]
                + " lang=" + seen[3] + " langTotal=" + seen[4] + " recipe=" + seen[5]
                + " smoke=" + seen[6] + " recipeTotal=" + seen[7] + " tieGreet=" + seen[8]
                + " tieFarewell=" + seen[9] + " canonicalShaped=" + seen[10]
                + " canonicalSmoke=" + seen[11] + " lootBuilt=" + seen[12] + " lootVisible=" + seen[13]
                + " worldgenVisible=" + seen[14] + " structureVisible=" + seen[15]
                + " exportExample=" + seen[16] + " exportSmoke=" + seen[17]
                + " exportTag=" + seen[18] + " exportLang=" + seen[19] + " exportLoot=" + seen[20]
                + " exportWorldgen=" + seen[21] + " exportStructure=" + seen[22]
                + " exportTower=" + seen[23] + " exportGreet=" + seen[24] + " exportFarewell=" + seen[25] + " exportArchive=" + seen[26]
                + " exportCmd=" + seen[27] + " exportRehydrate=" + seen[28] + " rulesTick=" + seen[29] + " rulesWild=" + seen[30] + " fatal=" + fatal);
        if (pass) {
            System.out.println("[DatapackE2EProbe] PASS (td datapack loaded + registered on a live server)");
            System.exit(0);
        } else {
            System.out.println("[DatapackE2EProbe] FAIL: datapack registration contract not met");
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

    // ---------- staging ----------

    /**
     * Copies the bundled seed pack into {@code build/tmp/datapacks-e2e/} fresh
     * (deterministic). The staging root is private to this probe — it never
     * touches {@code run/datapacks}, so the bootProbe game (which loads the
     * shared dir by default) can never hold a Windows file handle on our dll.
     */
    private static String stageSeedPack(Path root) throws Exception {
        // clear a previous run's export output so the command starts from a clean dir
        Path exportDir = root.resolve("build/tmp/subterra-export-e2e");
        if (Files.exists(exportDir)) {
            try (var walk = Files.walk(exportDir)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (Exception ignored) {
                                // best-effort; stale leftovers never block staging
                            }
                        });
            }
        }
        Path stagedRoot = root.resolve("build/tmp/datapacks-e2e");
        if (Files.exists(stagedRoot)) {
            try (var walk = Files.walk(stagedRoot)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (Exception ignored) {
                                // best-effort; stale leftovers never block staging
                            }
                        });
            }
        }
        Path target = stagedRoot.resolve("mini_dp");
        copy("/datapack/mini_dp/pack.td", target.resolve("pack.td"));
        Path data = target.resolve("data/toterra");
        copy("/datapack/mini_dp/data/toterra/tag/item/special.td", data.resolve("tag/item/special.td"));
        copy("/datapack/mini_dp/data/toterra/lang/en_us.td", data.resolve("lang/en_us.td"));
        copy("/datapack/mini_dp/data/toterra/recipe/example.td", data.resolve("recipe/example.td"));
        copy("/datapack/mini_dp/data/toterra/recipe/smoke.td", data.resolve("recipe/smoke.td"));
        copy("/datapack/mini_dp/data/toterra/loot_table/chest/bonus.td", data.resolve("loot_table/chest/bonus.td"));
        copy("/datapack/mini_dp/data/toterra/worldgen/configured_feature/meadow_of_tie.td", data.resolve("worldgen/configured_feature/meadow_of_tie.td"));
        copy("/datapack/mini_dp/data/toterra/structure/shrine.td", data.resolve("structure/shrine.td"));
        copy("/datapack/mini_dp/data/toterra/function/greet.td", data.resolve("function/greet.td"));
        copy("/datapack/mini_dp/data/toterra/function/farewell.td", data.resolve("function/farewell.td"));
        copy("/datapack/mini_dp/extra/obligatory_tower.td", target.resolve("extra/obligatory_tower.td"));
        copy("/tie/dp_logic_probe.dll", target.resolve("tie/dp_logic_probe.dll"));
        // save/session rules override for p.2.2.9: flips pack.td's wild=true -> false
        Files.writeString(stagedRoot.resolve("overrides.td"),
                "type tie<data>\nrules = [ [ k = \"wild\", v = false ] ],\n");
        System.out.println("[DatapackE2EProbe] staged seed pack at " + target);
        return stagedRoot.toAbsolutePath().normalize().toString();
    }

    private static void copy(String resource, Path target) throws Exception {
        try (InputStream in = DatapackE2EProbe.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing probe resource: " + resource);
            }
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ---------- boot hygiene (mirrors ServerBootProbe) ----------

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
            p.store(out, "generated by DatapackE2EProbe (dev-run asset reference)");
        }
        System.out.println("[DatapackE2EProbe] wrote " + props);
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
                    // best-effort
                }
            });
        }
        System.out.println("[DatapackE2EProbe] removed previous dev world");
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
                    System.out.println("[DatapackE2EProbe] reaped game pid " + pid + " (port " + port + ")");
                }
            }
        } catch (IOException | InterruptedException ignored) {
            // best-effort cleanup
        }
    }
}