package io.toterra.subterra.probes;

import io.toterra.subterra.config.Td;
import io.toterra.subterra.config.TdTable;
import io.toterra.subterra.config.TdValue;

/**
 * Deterministic acceptance probe for the td configuration module (p.1.5).
 * Pure JVM — no Minecraft runtime.
 * <p>
 * Asserts the td parser/writer contract: header and table-name stripping,
 * scalars, nested tables, arrays, escape round-trips, writer round-trips,
 * and malformed-input rejection.
 * Exit 0 = PASS, exit 1 = FAIL (gates acceptance; never shipped in the mod jar).
 */
public final class ConfigProbe {

    private ConfigProbe() {
    }

    public static void main(String[] args) {
        int failures = 0;

        // Bare table, scalars, nesting.
        String sample = """
                type tie<data>
                [
                  name = "雨林" ,
                  count = 42,
                  ratio = 1.5,
                  active = true,
                  tags = ["a", "b"],
                  nested = [
                    x = 1,
                  ],
                ]
                """;
        TdTable table = Td.parse(sample);
        if (!check("scalar str", "雨林".equals(table.get("name").asString()))) failures++;
        if (!check("scalar int", table.get("count").asInt() == 42)) failures++;
        if (!check("scalar float", table.get("ratio").asFloat() == 1.5)) failures++;
        if (!check("scalar bool", table.get("active").asBool())) failures++;
        TdValue tags = table.get("tags");
        if (!check("array elements", tags.asList().size() == 2 && "a".equals(tags.asList().get(0).asString()))) failures++;
        TdValue nested = table.get("nested");
        if (!check("nested get", nested instanceof TdTable nt && nt.get("x").asInt() == 1)) failures++;

        // Optional table name + header stripping.
        TdTable named = Td.parse("type tie<data>\nworldgen = [\n  terrain = \"山地\",\n]\n");
        if (!check("named table", "山地".equals(named.get("terrain").asString()))) failures++;

        // Escape round-trip through writer.
        TdTable built = TdTable.builder()
                .put("note", "line1\nline2\t\"q\"\\path")
                .put("v", TdValue.of(7L))
                .element(TdValue.of(1L))
                .build();
        String written = Td.write(built);
        TdTable reparsed = Td.parse(written);
        if (!check("writer roundtrip str", "line1\nline2\t\"q\"\\path".equals(reparsed.get("note").asString()))) failures++;
        if (!check("writer roundtrip int", reparsed.get("v").asInt() == 7)) failures++;
        if (!check("writer roundtrip element", reparsed.elements().size() == 1 && reparsed.elements().get(0).asInt() == 1)) failures++;

        // Malformed input must throw.
        if (!check("reject unterminated", rejects("[\n  a = 1\n"))) failures++;
        if (!check("reject bad string", rejects("[\n  a = \"oops\n]\n"))) failures++;
        if (!check("reject trailing", rejects("[\n  a = 1,\n] extra\n"))) failures++;

        if (failures == 0) {
            System.out.println("[ConfigProbe] PASS (td parse/write round-trips)");
            System.exit(0);
        } else {
            System.out.println("[ConfigProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }

    private static boolean rejects(String source) {
        try {
            Td.parse(source);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static boolean check(String what, boolean ok) {
        if (!ok) {
            System.out.println("[ConfigProbe] FAIL " + what);
        }
        return ok;
    }
}