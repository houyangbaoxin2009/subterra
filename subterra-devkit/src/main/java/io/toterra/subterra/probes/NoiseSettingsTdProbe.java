package io.toterra.subterra.probes;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.datapack.NoiseSettingsDatum;
import io.toterra.subterra.engine.datapack.NoiseSettingsJson;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * p.2.2 "td = JSON datapack plus" — worldgen/noise_settings first closed loop.
 * Pure JDK, no Minecraft runtime. Asserts:
 *
 * <ol>
 * <li>canonical td round-trip: for several inline td payload literals,
 * {@code write ∘ read ∘ write ≡ write} (byte-identical, deterministic field order);</li>
 * <li>baseline JSON equivalence: read {@code subterra_overworld.json} (the vanilla
 * {@code NoiseGeneratorSettings} run source), express it as a noise-settings td,
 * export back to JSON, and assert the two parsed trees are key-by-key equal
 * (order/whitespace independent);</li>
 * <li>plus stripping: a td carrying reserved {@code tie_} keys at nested depths
 * exports to the same JSON as its plus-free counterpart and never leaks a
 * {@code tie_} key into JSON, while the raw payload keeps the plus keys on the
 * td→td path.</li>
 * </ol>
 */
public final class NoiseSettingsTdProbe {

    private NoiseSettingsTdProbe() {
    }

    private static int failures = 0;
    private static int passes = 0;

    private static void check(String name, boolean ok) {
        if (ok) {
            passes++;
            System.out.println("[PASS] " + name);
        } else {
            failures++; // only the failure path counts
            System.out.println("[FAIL] " + name);
        }
    }

    public static void main(String[] args) throws Exception {
        // ---- (a) canonical td closed loop on 4 inline literals ----
        String[] literals = {
                // minimal
                "[\n"
                        + "  aquifers_enabled = true,\n"
                        + "  sea_level = 128,\n"
                        + "  default_block = [ Name = \"minecraft:stone\" ],\n"
                        + "  noise = [ height = 320, min_y = -64, size_horizontal = 1, size_vertical = 2 ],\n"
                        + "  ore_veins_enabled = false,\n"
                        + "  legacy_random_source = false,\n"
                        + "  disable_mob_generation = false\n"
                        + "]",
                // realistic sampler: nested density fn + spawn_target array
                "[\n"
                        + "  sea_level = 63,\n"
                        + "  noise_router = [\n"
                        + "    barrier = [ type = \"minecraft:noise\", noise = \"minecraft:aquifer_barrier\", xz_scale = 1.0, y_scale = 0.5 ],\n"
                        + "    final_density = [ type = \"minecraft:add\", argument1 = 0.1171875, argument2 = [ type = \"minecraft:mul\", argument1 = -0.078125, argument2 = [ type = \"minecraft:interpolated\", argument = [ type = \"minecraft:range_choice\", input = \"minecraft:y\", min_inclusive = -60.0, max_exclusive = 51.0, when_in_range = [ type = \"minecraft:noise\", noise = \"minecraft:ore_vein_a\" ], when_out_of_range = 0.0 ] ] ] ]\n"
                        + "  ],\n"
                        + "  spawn_target = [\n"
                        + "    [ continentalness = [ -0.11, 1.0 ], depth = 0.0, weirdness = [ -1.0, -0.16 ] ],\n"
                        + "    [ continentalness = [ -0.11, 1.0 ], depth = 0.0, weirdness = [ 0.16, 1.0 ] ]\n"
                        + "  ],\n"
                        + "  surface_rule = [ type = \"minecraft:sequence\", "
                        + "sequence = [ [ type = \"minecraft:block\", result_state = [ Name = \"minecraft:bedrock\" ] ] ] ],\n"
                        + "  aquifers_enabled = true,\n"
                        + "  ore_veins_enabled = true,\n"
                        + "  legacy_random_source = false,\n"
                        + "  disable_mob_generation = true\n"
                        + "]",
                // nested block states + anchors
                "[\n"
                        + "  default_fluid = [ Name = \"minecraft:water\", Properties = [ level = \"0\" ] ],\n"
                        + "  sea_level = 32,\n"
                        + "  surface_rule = [ type = \"minecraft:condition\", "
                        + "if_true = [ type = \"minecraft:vertical_gradient\", random_name = \"minecraft:bedrock_floor\", "
                        + "true_at_and_below = [ above_bottom = 0 ], false_at_and_above = [ above_bottom = 5 ] ], "
                        + "then_run = [ type = \"minecraft:block\", result_state = [ Name = \"minecraft:deepslate\", Properties = [ axis = \"y\" ] ] ] ]\n"
                        + "]",
                // floats incl. integral-valued and a large exponent
                "[\n"
                        + "  sea_level = 0,\n"
                        + "  noise_router = [ temperature = [ type = \"minecraft:shifted_noise\", noise = \"minecraft:temperature\", "
                        + "xz_scale = 0.25, y_scale = 0.0, shift_y = 0.0 ], "
                        + "vein_ridged = [ argument1 = -0.07999999821186066 ] ],\n"
                        + "  aquifers_enabled = false\n"
                        + "]"
        };
        check("用例数 4", literals.length == 4);
        for (int i = 0; i < literals.length; i++) {
            canonicalClosedLoop("用例" + (i + 1), literals[i]);
        }

        // ---- (b) baseline JSON -> td -> JSON -> tree equality ----
        String baseline = readResource("/data/subterra/worldgen/noise_settings/subterra_overworld.json");
        check("基线 JSON 可读", baseline != null && !baseline.isBlank());
        if (baseline != null) {
            TdTable baselineTree = NoiseSettingsJson.parseJson(baseline);
            TdTable canonical = NoiseSettingsDatum.read(baselineTree).write();
            String tdText = Td.write(canonical);
            // td text itself round-trips byte-identically (bridge back to the canonical closed loop)
            String tdText2 = Td.write(NoiseSettingsDatum.read(Td.parse(tdText)).write());
            check("基线 td 文本往返恒等", tdText.equals(tdText2));
            // td -> JSON -> parse -> tree equality with the baseline tree
            String jsonOut = NoiseSettingsJson.toJson(canonical);
            TdValue roundTrip = NoiseSettingsJson.parseJson(jsonOut);
            check("基线 JSON→td→JSON 解析树逐键相等", NoiseSettingsJson.treesEqual(baselineTree, roundTrip));
            check("导出 JSON 非空且含 final_density",
                    jsonOut.contains("final_density") && jsonOut.contains("\"subterra:density\""));
            check("导出 JSON 无 plus 键", !jsonOut.contains("tie_"));
        }

        // ---- (c) plus keys: stripped on JSON export, kept on raw td round-trip ----
        check("plus 前缀约定", NoiseSettingsDatum.isPlusKey("tie_final_density")
                && NoiseSettingsDatum.isPlusKey("tie_gen") && !NoiseSettingsDatum.isPlusKey("final_density"));
        String plusTdText = "[\n"
                + "  aquifers_enabled = true,\n"
                + "  sea_level = 128,\n" // vanilla field, kept
                + "  surface_rule = [ tie_surface_base = \"@gen(seed=44905237)\", "
                + "type = \"minecraft:block\", result_state = [ Name = \"minecraft:stone\" ] ],\n"
                + "  noise_router = [ final_density = [ type = \"minecraft:interpolated\", "
                + "argument = [ type = \"minecraft:noise\", noise = \"minecraft:overworld/base\" ] ], "
                + "tie_final = \"tie(subterra:density)\" ]\n"
                + "]";
        TdTable plusTd = Td.parse(plusTdText);
        String rawPayload = Td.write(plusTd);
        check("原 payload 保留 tie_ 键（td→td 全量）",
                rawPayload.contains("tie_surface_base") && rawPayload.contains("tie_final"));
        TdTable stripped = (TdTable) NoiseSettingsDatum.stripPlus(plusTd);
        String jsonPlus = NoiseSettingsJson.toJson(plusTd);
        String jsonNoPlus = NoiseSettingsJson.toJson(stripped);
        check("plus td 导出 = 无 plus 版导出", jsonPlus.equals(jsonNoPlus));
        check("plus 键被剥离（JSON 无 tie_）", !jsonPlus.contains("tie_")
                && !jsonPlus.contains("@gen") && !jsonPlus.contains("tie("));
        check("vanilla 字段照常导出", jsonPlus.contains("\"sea_level\":128")
                && jsonPlus.contains("\"aquifers_enabled\":true")
                && jsonPlus.contains("\"minecraft:stone\""));
        // canonical write() also strips plus at any depth
        String writeStripped = Td.write(NoiseSettingsDatum.read(plusTd).write());
        check("canonical write 剥离 plus", !writeStripped.contains("tie_"));

        finish();
    }

    /** (a) write∘read∘write byte identity for an inline payload literal. */
    private static void canonicalClosedLoop(String name, String tdText) {
        String c1 = Td.write(NoiseSettingsDatum.read(Td.parse(tdText)).write());
        String c2 = Td.write(NoiseSettingsDatum.read(Td.parse(c1)).write());
        check("canonical 往返 " + name, c1.equals(c2) && !c1.isBlank());
    }

    private static String readResource(String path) throws Exception {
        try (InputStream in = NoiseSettingsTdProbe.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void finish() {
        if (failures == 0) {
            System.out.println("=== NoiseSettingsTdProbe ALL PASS（" + passes + " 断言；noise_settings td=JSON plus 首闭环可行）===");
        } else {
            System.out.println("=== NoiseSettingsTdProbe " + failures + " 项失败（余 " + passes + " 通过）===");
            System.exit(1);
        }
    }
}