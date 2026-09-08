package io.toterra.subterra.probes;

import io.toterra.subterra.engine.config.ConfigPack;
import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deterministic acceptance probe for the configuration package import/export
 * (p.1.5.1): exact round-trips, multiple files, header/version handling,
 * malformed-input rejection, deterministic file ordering. Pure JVM — no
 * Minecraft runtime.
 */
public final class ConfigPackProbe {

    private ConfigPackProbe() {
    }

    private static int failures = 0;

    private static void check(String name, boolean ok) {
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    public static void main(String[] args) {
        roundtrip();
        rejection();

        if (failures == 0) {
            System.out.println("[ConfigPackProbe] PASS (configuration package)");
            System.exit(0);
        } else {
            System.out.println("[ConfigPackProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }

    private static void roundtrip() {
        Map<String, TdTable> files = new LinkedHashMap<>();
        files.put("log.td", Td.parse("""
                type tie<data>
                log = [
                  level = "WARN",
                  ring_size = 64,
                  file = [ enabled = true, dir = "logs/x", keep = 3 ],
                ]
                """));
        files.put("worldgen.td", Td.parse("""
                [
                  worldgen = [ mode = "eco" ],
                  dimension = [ id = "overworld" ],
                ]
                """));
        files.put("z-last.td", Td.parse("[\n  enabled = false,\n]\n"));

        String packed = ConfigPack.export(files);
        check("package has header and bare root", packed.startsWith("type tie<data>\n[\n")
                && packed.contains("package = ["));
        check("package lists all files", packed.contains("\"log.td\"") && packed.contains("\"worldgen.td\"")
                && packed.contains("\"z-last.td\""));

        Map<String, TdTable> restored = ConfigPack.parse(packed);
        check("restored file count", restored.size() == 3);
        // The td parser strips a top-level table name (`log = [...]` -> bare
        // content), same as tiec parse_data; content is preserved at root.
        check("log.td content preserved", restored.get("log.td") instanceof TdTable logTable
                && logTable.get("level").asString().equals("WARN")
                && logTable.get("ring_size").asInt() == 64);
        check("worldgen.td preserved", restored.get("worldgen.td").get("worldgen") instanceof TdTable wg
                && wg.get("mode").asString().equals("eco"));
        check("ordering deterministic", files.keySet().equals(restored.keySet()));

        // Export again from the restored map — must be byte-identical.
        check("re-export idempotent", ConfigPack.export(restored).equals(packed));

        // Scalars round-trip too.
        Map<String, TdTable> scalar = new LinkedHashMap<>();
        scalar.put("s.td", TdTable.builder().put("n", TdValue.of(7L)).build());
        TdTable s = ConfigPack.parse(ConfigPack.export(scalar)).get("s.td");
        check("scalar nested round-trip", s.get("n").asInt() == 7);
    }

    private static void rejection() {
        check("rejects missing package", rejects("[\n  x = 1,\n]\n"));
        check("rejects bad version", rejects("package = [ version = 9, files = [] ]"));
        check("rejects non-table file entry", rejects("package = [ version = 1, files = [ \"a\" = 1 ] ]"));
    }

    private static boolean rejects(String source) {
        try {
            ConfigPack.parse(source);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }
}