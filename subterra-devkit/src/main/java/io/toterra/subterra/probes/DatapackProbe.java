package io.toterra.subterra.probes;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.datapack.DatapackPack;
import io.toterra.subterra.engine.datapack.Datapack;
import io.toterra.subterra.engine.datapack.DatapackEntry;
import io.toterra.subterra.engine.datapack.DatapackExportArchive;
import io.toterra.subterra.engine.datapack.DatapackExporter;
import io.toterra.subterra.engine.datapack.DatapackLoader;
import io.toterra.subterra.engine.datapack.EntryKind;
import io.toterra.subterra.engine.datapack.TieLogicBundle;
import io.toterra.subterra.engine.datapack.TieLogicLoader;
import io.toterra.subterra.engine.tie.TieFunction;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * p.2.2 datapack determinism probe — pure td direct loading, no JSON.
 *
 * <p>Self-contained: extracts the bundled seed pack {@code resources/datapack/
 * mini_dp} plus the precompiled tie library (FFM needs a real file path) into a
 * temp pack dir, loads it with {@link DatapackLoader}, asserts the registry
 * (dir scan + pack.td manifest + 7 kinds), then exercises the tie-logic chain
 * ({@link TieLogicLoader} → {@link TieLogicBundle} → engine.tie downcall:
 * explicit {@code fn}, default {@code fn} from the entry path, private symbol
 * unexported, bool boundary). Also proves malformed td surfaces the offender,
 * repeated loads are deterministic, and the recipe export round-trip
 * ({@link DatapackExporter} → {@link DatapackLoader} re-load → re-export) is
 * byte-identical (p.2.2 block 5). Block 6 extends the round-trip to all kinds
 * (per-entry file reload) and to the whole-pack {@link DatapackExportArchive}
 * archive ({@code export}∘{@code rehydrate} identity + entry-count parity),
 * plus a field-fidelity check that the canonical per-kind export equals the
 * plain payload serialization.
 */
public final class DatapackProbe {

    private DatapackProbe() {
    }

    private static int failures = 0;

    private static void check(String name, boolean ok) {
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++; // only the failure path counts
            System.out.println("[FAIL] " + name);
        }
    }

    public static void main(String[] args) throws Exception {
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"),
                "subterra_dp_" + System.nanoTime());
        try {
            Path packDir = extractSeedPack(tmp);
            Datapack dp = DatapackLoader.load(packDir);

            // 1. registry: metadata + entry census (6 dir-scanned + 1 manifest)
            check("包名 mini_dp", "mini_dp".equals(dp.name()));
            check("标题含 p.2.2", dp.title().contains("p.2.2"));
            check("条目总数 10", dp.entries().size() == 10);
            List<String> ids = new ArrayList<>(dp.entries().keySet());
            check("id 确定性排序", ids.equals(ids.stream().sorted().toList()));
            check("七类齐全", presentKinds(dp).equals(Set.of(EntryKind.values())));

            // 2. kind census via byKind
            check("function ×2", dp.byKind(EntryKind.FUNCTION).size() == 2);
            check("structure ×2（含 manifest 增量）", dp.byKind(EntryKind.STRUCTURE).size() == 2);
            check("manifest 增量条目", dp.get(EntryKind.STRUCTURE, "devkit", "obligatory_tower") != null);
            check("扫目录条目", dp.get(EntryKind.STRUCTURE, "toterra", "shrine") != null);

            // 3. payload spot checks (td in memory, no JSON)
            DatapackEntry tag = dp.get(EntryKind.TAG, "toterra", "item/special");
            check("tag 存在", tag != null);
            check("tag replace=false", tag != null && !tag.payload().get("replace").asBool());
            check("tag values 命中", tag != null
                    && listStrings(tag.payload().get("values")).equals(List.of("minecraft:stick", "minecraft:apple")));

            DatapackEntry lang = dp.get(EntryKind.LANG, "toterra", "en_us");
            TdTable langPayload = lang != null ? (TdTable) lang.payload() : null;
            check("lang 两条", langPayload != null && langPayload.elements().size() == 2);
            check("lang k/v 读取", langPayload != null
                    && pair(langPayload, 0, "k").equals("item.toterra.crystal")
                    && pair(langPayload, 0, "v").equals("Crystal"));

            DatapackEntry recipe = dp.get(EntryKind.RECIPE, "toterra", "example");
            check("recipe type", recipe != null && "minecraft:crafting_shaped".equals(recipe.payload().get("type").asString()));

            DatapackEntry world = dp.get(EntryKind.WORLDGEN, "toterra", "configured_feature/meadow_of_tie");
            check("worldgen configured_feature type", world != null
                    && "minecraft:configured_feature".equals(world.payload().get("type").asString()));
            check("worldgen simple_block + block 命中", world != null
                    && "minecraft:simple_block".equals(world.payload().get("feature").asString())
                    && "minecraft:diamond_block".equals(world.payload().get("block").asString()));

            // 4. tie logic chain: pack.td declaration → TieLibrary → FFM downcall
            check("tie 库声明 1 条", dp.tieLibraries().size() == 1
                    && dp.tieLibraries().get(0).lib().equals("dp_logic"));

            try (TieLogicBundle bundle = TieLogicLoader.load(dp, packDir)) {
                check("tie 库装载", bundle.libraries().containsKey("dp_logic"));

                DatapackEntry greet = dp.get(EntryKind.FUNCTION, "toterra", "greet");
                DatapackEntry farewell = dp.get(EntryKind.FUNCTION, "toterra", "farewell");
                TieFunction greetFn = bundle.resolve(greet);       // fn explicit = seed_42
                TieFunction farewellFn = bundle.resolve(farewell); // fn defaults to path
                check("seed_42()=42（显式 fn）", greetFn.invoke0() == 42L);
                check("farewell()=108（默认 fn=路径）", farewellFn.invoke0() == 108L);
                check("私有 hidden 未导出", !bundle.libraries().get("dp_logic").contains("dp_logic$hidden"));
                check("craftable(7,1)=7（显式 fn i64×2）",
                        bundle.libraries().get("dp_logic").find("dp_logic$craftable")
                                .orElseThrow().invokeI64I64(7, 1) == 7L);
                check("craftable(7,0)=-1",
                        bundle.libraries().get("dp_logic").find("dp_logic$craftable")
                                .orElseThrow().invokeI64I64(7, 0) == -1L);
            }

            // 5. determinism: a second load produces the identical registry
            check("重复装载注册表一致",
                    dp.entries().keySet().equals(DatapackLoader.load(packDir).entries().keySet()));

            // 6. pack round-trip: datapack dir -> single td doc -> unpack -> reload
            String packed = DatapackPack.export(packDir);
            Path unpacked = tmp.resolve("mini_rt");
            DatapackPack.unpack(packed, unpacked);
            Datapack rt = DatapackLoader.load(unpacked);
            check("打包往返 注册表一致", rt.entries().keySet().equals(dp.entries().keySet()));
            check("打包往返 包名与 manifest 名保留", rt.name().equals(dp.name()));
            DatapackEntry rtRecipe = rt.get(EntryKind.RECIPE, "toterra", "example");
            DatapackEntry srcRecipe = recipe;
            check("打包往返 recipe payload 精确", rtRecipe != null && srcRecipe != null
                    && io.toterra.subterra.engine.config.Td.write(rtRecipe.payload())
                    .equals(io.toterra.subterra.engine.config.Td.write(srcRecipe.payload())));
            check("打包往返 pack.td 携带", rt.tieLibraries().equals(dp.tieLibraries()));

            // 7. export round-trip (pure JDK): entry payload -> canonical td -> DatapackLoader
            //    re-load -> re-export -> byte-identical (p.2.2 block 5)
            Path rexDp = tmp.resolve("rex_dp");
            Path rexData = rexDp.resolve("data/toterra/recipe");
            Map<String, String> text1Map = new LinkedHashMap<>();
            String[] recipePaths = { "example", "smoke" };
            for (String rp : recipePaths) {
                DatapackEntry srcEntry = dp.get(EntryKind.RECIPE, "toterra", rp);
                String text1 = DatapackExporter.exportRecipeTd(srcEntry);
                text1Map.put(rp, text1);
                Path tdFile = rexData.resolve(rp + ".td");
                Files.createDirectories(tdFile.getParent());
                Files.writeString(tdFile, "type tie<data>\n" + text1);
            }
            Datapack rex = DatapackLoader.load(rexDp);
            for (String rp : recipePaths) {
                DatapackEntry r1 = rex.get(EntryKind.RECIPE, "toterra", rp);
                String text2 = DatapackExporter.exportRecipeTd(r1);
                check("导出往返 " + rp + " 逐字节一致", text2.equals(text1Map.get(rp)));
                // third pass: feed the re-export back into a second dir -> must be stable
                Path rex2 = tmp.resolve("rex_dp_" + rp);
                Path f2 = rex2.resolve("data/toterra/recipe").resolve(rp + ".td");
                Files.createDirectories(f2.getParent());
                Files.writeString(f2, "type tie<data>\n" + text2);
                Datapack rex2dp = DatapackLoader.load(rex2);
                String text3 = DatapackExporter.exportRecipeTd(rex2dp.get(EntryKind.RECIPE, "toterra", rp));
                check("导出往返 " + rp + " 三连稳定", text3.equals(text2));
                // field-fidelity bonus: the canonical export equals the plain serialization
                // of the source payload (proves the exporter loses no td field)
                check("导出字段无损 " + rp,
                        text1Map.get(rp).equals(
                                io.toterra.subterra.engine.config.Td.write(dp.get(EntryKind.RECIPE, "toterra", rp).payload())));
            }

            // 8. malformed td names the offender
            Path bad = tmp.resolve("bad_pack");
            Files.createDirectories(bad.resolve("data/broken/function"));
            Files.writeString(bad.resolve("data/broken/function/x.td"), "type tie<data>\nfunction = [\n");
            try {
                DatapackLoader.load(bad);
                check("畸形 td 抛错含文件", false);
            } catch (IllegalArgumentException e) {
                check("畸形 td 抛错含文件", e.getMessage().contains("x.td"));
            }

            // 9. export round-trip all kinds + archive (p.2.2 block 6)
            int checked = 0;
            for (DatapackEntry entry : dp.entries().values()) {
                String e1 = DatapackExporter.exportEntryTd(entry);
                String safeId = entry.id().replace('/', '_').replace(':', '_');
                Path pack7 = tmp.resolve("dp7_" + safeId);
                Path tdFile = pack7.resolve("data").resolve(entry.namespace())
                        .resolve(entry.kind().dir()).resolve(entry.path() + ".td");
                Files.createDirectories(tdFile.getParent());
                Files.writeString(tdFile, "type tie<data>\n" + e1);
                Datapack reloaded = DatapackLoader.load(pack7);
                DatapackEntry re = reloaded.get(entry.kind(), entry.namespace(), entry.path());
                check("导出往返 " + entry.id(), e1.equals(re != null ? DatapackExporter.exportEntryTd(re) : null));
                checked++;
            }
            check("导出全类往返覆盖 (" + checked + " 条)", checked == dp.entries().size());

            // archive round-trip: whole pack -> one td doc -> rehydrate -> re-export
            String doc1 = DatapackExportArchive.export(dp);
            Datapack rehydrated = DatapackExportArchive.rehydrate(doc1);
            String doc2 = DatapackExportArchive.export(rehydrated);
            check("导出档案 往返 逐字节一致", doc1.equals(doc2));
            check("导出档案 条目数保留", rehydrated.entries().size() == dp.entries().size());

            // field fidelity: canonical export == plain payload serialization
            check("导出 tag 无损",
                    DatapackExporter.exportEntryTd(dp.get(EntryKind.TAG, "toterra", "item/special"))
                            .equals(io.toterra.subterra.engine.config.Td.write(
                                    dp.get(EntryKind.TAG, "toterra", "item/special").payload())));
            check("导出 lang 无损",
                    DatapackExporter.exportEntryTd(dp.get(EntryKind.LANG, "toterra", "en_us"))
                            .equals(io.toterra.subterra.engine.config.Td.write(
                                    dp.get(EntryKind.LANG, "toterra", "en_us").payload())));
            check("导出 loot 无损(透传)",
                    DatapackExporter.exportEntryTd(dp.get(EntryKind.LOOT_TABLE, "toterra", "chest/bonus"))
                            .equals(io.toterra.subterra.engine.config.Td.write(
                                    dp.get(EntryKind.LOOT_TABLE, "toterra", "chest/bonus").payload())));
        } finally {
            deleteRecursively(tmp);
        }

        finish();
    }

    // ---------- helpers ----------

    /** The set of entry kinds actually present in the registry. */
    private static Set<EntryKind> presentKinds(Datapack dp) {
        Set<EntryKind> present = new java.util.HashSet<>();
        for (DatapackEntry e : dp.entries().values()) {
            present.add(e.kind());
        }
        return present;
    }

    private static List<String> listStrings(TdValue v) {
        if (!(v instanceof TdTable t)) {
            return List.of();
        }
        return t.elements().stream().map(TdValue::asString).toList();
    }

    private static String pair(TdTable table, int index, String key) {
        TdValue item = table.elements().get(index);
        if (!(item instanceof TdTable t)) {
            return "";
        }
        return t.get(key) != null ? t.get(key).asString() : "";
    }

    private static Path extractSeedPack(Path tmp) throws Exception {
        Path packDir = tmp.resolve("mini_dp");
        Path dataRoot = packDir.resolve("data");
        // td files (explicit list — classpath resources have no directory listing)
        copy("/datapack/mini_dp/pack.td", packDir.resolve("pack.td"));
        copy("/datapack/mini_dp/data/toterra/tag/item/special.td", dataRoot.resolve("toterra/tag/item/special.td"));
        copy("/datapack/mini_dp/data/toterra/lang/en_us.td", dataRoot.resolve("toterra/lang/en_us.td"));
        copy("/datapack/mini_dp/data/toterra/recipe/example.td", dataRoot.resolve("toterra/recipe/example.td"));
        copy("/datapack/mini_dp/data/toterra/recipe/smoke.td", dataRoot.resolve("toterra/recipe/smoke.td"));
        copy("/datapack/mini_dp/data/toterra/loot_table/chest/bonus.td", dataRoot.resolve("toterra/loot_table/chest/bonus.td"));
        copy("/datapack/mini_dp/data/toterra/worldgen/configured_feature/meadow_of_tie.td", dataRoot.resolve("toterra/worldgen/configured_feature/meadow_of_tie.td"));
        copy("/datapack/mini_dp/data/toterra/structure/shrine.td", dataRoot.resolve("toterra/structure/shrine.td"));
        copy("/datapack/mini_dp/data/toterra/function/greet.td", dataRoot.resolve("toterra/function/greet.td"));
        copy("/datapack/mini_dp/data/toterra/function/farewell.td", dataRoot.resolve("toterra/function/farewell.td"));
        // manifest-only entry lives outside data/ (never scanned)
        copy("/datapack/mini_dp/extra/obligatory_tower.td", packDir.resolve("extra/obligatory_tower.td"));
        // precompiled tie logic library — resolves pack.td's dll="tie/dp_logic_probe.dll"
        copy("/tie/dp_logic_probe.dll", packDir.resolve("tie/dp_logic_probe.dll"));
        return packDir;
    }

    private static void copy(String resource, Path target) throws Exception {
        try (InputStream in = DatapackProbe.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing probe resource: " + resource);
            }
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception e) {
                    // best-effort temp cleanup
                }
            });
        }
    }

    private static void finish() {
        if (failures == 0) {
            System.out.println("=== DatapackProbe ALL PASS（td 直载 + tie 逻辑链可行）===");
        } else {
            System.out.println("=== DatapackProbe " + failures + " 项失败 ===");
            System.exit(1);
        }
    }
}