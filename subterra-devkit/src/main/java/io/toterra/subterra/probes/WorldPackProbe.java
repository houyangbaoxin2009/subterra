// Deterministic acceptance probe for p.2.9.4: the "world-as-artifact" envelope
// (engine.world.WorldPack + WorldPackResult + WorldPackPacker) and its p.2.9.5
// ExportHub wiring. Pure JVM — no MC, no wall-clock, no timestamps, no random
// seeds. NOT shipped in the mod jar.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.datapack.DatapackPack;
import io.toterra.subterra.engine.export.ExportHub;
import io.toterra.subterra.engine.export.ExportKind;
import io.toterra.subterra.engine.export.ExportPort;
import io.toterra.subterra.engine.save.SaveContainer;
import io.toterra.subterra.engine.save.SaveExportArchive;
import io.toterra.subterra.engine.save.SaveSlot;
import io.toterra.subterra.engine.world.WorldPack;
import io.toterra.subterra.engine.world.WorldPackPacker;
import io.toterra.subterra.engine.world.WorldPackResult;
import io.toterra.subterra.engine.save.doc.DomainDoc;
import io.toterra.subterra.engine.save.doc.DomainDoc.DomainEntry;
import io.toterra.subterra.engine.save.doc.LedgerDoc;
import io.toterra.subterra.engine.save.doc.LedgerDoc.LedgerEntry;
import io.toterra.subterra.engine.save.doc.RegisterDoc;
import io.toterra.subterra.engine.save.doc.RegisterDoc.RegisterRow;
import io.toterra.subterra.engine.save.doc.RelicDoc;
import io.toterra.subterra.engine.save.doc.RelicDoc.RelicEntry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * p.2.9.4 确定性世界包探针 —— 世界「作为可搬移产物」的单一 td 信封（{@link WorldPack} 导出/
 * 再水化、{@link WorldPackResult} 藏录/领域表面、{@link WorldPackPacker} 工作树打包/还原）、
 * 段序与 meta 排序、版本/畸形拒绝，以及 p.2.9.5 导出中枢（{@link ExportHub}）接线。全部断言
 * 确定性：无时序、无随机种子、无默认时间戳，逐字节相等为最强往返判据。退出码 0 = PASS。
 *
 * <p>p.2.9.4 deterministic world-pack probe — the world-pack single-td envelope
 * ({@link WorldPack} export/rehydrate, {@link WorldPackResult} ledger/domain surface,
 * {@link WorldPackPacker} work-tree pack/unpack), section order + meta sorting,
 * version/malformed rejection, and the p.2.9.5 export hub ({@link ExportHub}) wiring.
 * All assertions are deterministic; byte-identity is the strongest round-trip oracle.
 * Exit 0 = PASS.
 */
public final class WorldPackProbe {

    /** Deterministic staging root (relative to the project dir the probe runs in). */
    private static final Path STAGING = Path.of("build", "tmp", "worldpack-probe");

    private WorldPackProbe() {
    }

    private static int failures = 0;
    private static int checks = 0;

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    /** The five slots actually written/expected on disk (relic intentionally missing). */
    private static final List<SaveSlot> FIVE_SLOTS = List.of(
            SaveSlot.WORLD, SaveSlot.CONFIG, SaveSlot.LEDGER,
            SaveSlot.DOMAIN, SaveSlot.REGISTER);

    /** Deterministic metadata handed into the world pack (deliberately unordered). */
    private static final Map<String, TdValue> META = meta();

    private static Map<String, TdValue> meta() {
        Map<String, TdValue> m = new LinkedHashMap<>();
        m.put("world.z", TdValue.of(99L));
        m.put("world.a", TdValue.of(10L));
        m.put("world.seed", TdValue.of(42L));
        return m;
    }

    public static void main(String[] args) {
        cleanStaging();
        try {
            DeterministicFixture fx = buildFixture();
            byteRoundTrip(fx);
            sectionAndMetaOrder(fx.w1());
            packerRoundTrip(fx);
            ledgerDomainSurface(fx);
            malformedRejection(fx.w1());
            exportHub();
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] 探针异常: " + e);
            e.printStackTrace(System.out);
        }

        if (failures == 0) {
            System.out.println("WorldPackProbe: " + checks + " checks PASS");
            System.exit(0);
        } else {
            System.out.println("WorldPackProbe: " + failures + " assertion(s) of " + checks + " FAIL");
            System.exit(1);
        }
    }

    // ---------- fixture ----------

    /** Immutable fixture shared across the assertion groups. */
    record DeterministicFixture(
            SaveContainer container, TdTable worldDoc, TdTable ledgerDoc,
            TdTable domainDoc, Path srcDatapack, Path saveRoot, String w1) {
    }

    /** Builds the fixed six-slot container, the src datapack dir and packs w1. */
    private static DeterministicFixture buildFixture() throws IOException {
        TdTable worldDoc = TdTable.builder()
                .put("gametime", TdValue.of(1200L))
                .put("raining", TdValue.of(false))
                .build();
        TdTable configDoc = TdTable.builder()
                .put("keepInventory", TdValue.of(false))
                .put("difficulty", TdValue.str("hard"))
                .build();
        TdTable ledgerDoc = LedgerDoc.of(List.of(
                new LedgerEntry("runes.ancient", 1L, 1L, "seed"),
                new LedgerEntry("totems.dusk", 3L, 2L, "seed"))).toTd();
        TdTable domainDoc = DomainDoc.of(List.of(
                new DomainEntry("bazaar", "region", 1, "seed"),
                new DomainEntry("crypt", "dungeon", null, "seed"))).toTd();
        TdTable relicDoc = RelicDoc.of(List.of(
                new RelicEntry("relics.amber-fang", 1L, "common", 1L, "seed"))).toTd();
        TdTable registerDoc = RegisterDoc.of(List.of(
                new RegisterRow(20240101L, 5L, 50L, "seed"))).toTd();

        // Overlays on 2-3 slots (deliberately shuffled keys); global layer non-empty.
        Map<String, TdValue> worldOverlay = new LinkedHashMap<>();
        worldOverlay.put("world.difficulty", TdValue.str("hard"));
        worldOverlay.put("world.z", TdValue.of(7L));
        Map<String, TdValue> configOverlay = new LinkedHashMap<>();
        configOverlay.put("a", TdValue.of(99L));
        configOverlay.put("b", TdValue.of(2L));
        Map<String, TdValue> global = new LinkedHashMap<>();
        global.put("verbose", TdValue.str("on"));
        global.put("time", TdValue.of(1L));

        SaveContainer container = new SaveContainer()
                .attach(SaveSlot.WORLD, worldDoc)
                .attach(SaveSlot.CONFIG, configDoc)
                .attach(SaveSlot.LEDGER, ledgerDoc)
                .attach(SaveSlot.DOMAIN, domainDoc)
                .attach(SaveSlot.RELIC, relicDoc)
                .attach(SaveSlot.REGISTER, registerDoc)
                .overlay(SaveSlot.WORLD, worldOverlay)
                .overlay(SaveSlot.CONFIG, configOverlay)
                .globalRules(global);

        // Datapack dir with 3 td files (one in a nested data/x/... subdir).
        Path srcDatapack = STAGING.resolve("src-datapack");
        Path dpFile1 = srcDatapack.resolve("pack.td");
        writeText(dpFile1, "type tie<data>\n" + Td.write(TdTable.builder()
                .put("name", TdValue.str("mini_world")).build()));
        Path dpFile2 = srcDatapack.resolve("data/toterra/lang/en_us.td");
        writeText(dpFile2, "type tie<data>\n" + Td.write(TdTable.builder()
                .put("hello", TdValue.str("world")).build()));
        Path dpFile3 = srcDatapack.resolve("data/x/tag/root.td");
        writeText(dpFile3, "type tie<data>\n" + Td.write(TdTable.builder()
                .put("value", TdValue.of(5L)).build()));

        String w1 = WorldPack.export(container, srcDatapack, META);

        return new DeterministicFixture(container, worldDoc, ledgerDoc, domainDoc,
                srcDatapack, STAGING.resolve("save-root"), w1);
    }

    // ---------- 2. byte round-trip ----------

    private static void byteRoundTrip(DeterministicFixture fx) {
        WorldPackResult re = WorldPack.rehydrate(fx.w1());
        check("往返: export(rehydrate(w1)) 与 w1 逐字节相等",
                WorldPack.export(re.save(), fx.srcDatapack(), META).equals(fx.w1()));
        check("往返: rehydrate 后 save 容器与源容器 SaveExportArchive.export 字节等值",
                SaveExportArchive.export(re.save()).equals(SaveExportArchive.export(fx.container())));
        check("往返: datapackDocument 与 DatapackPack.export(srcDatapack) 等值",
                re.datapackDocument().equals(DatapackPack.export(fx.srcDatapack())));
        check("往返: meta 读回键集 = {world.a, world.seed, world.z}",
                re.meta().keySet().equals(Set.of("world.a", "world.seed", "world.z"))
                        && re.meta().equals(WorldPack.rehydrate(fx.w1()).meta()));
    }

    // ---------- 3. section order + meta sort (naive string assertions) ----------

    private static void sectionAndMetaOrder(String w1) {
        int v = w1.indexOf("version = ");
        int m = w1.indexOf("meta = ");
        int s = w1.indexOf("save = ");
        int d = w1.indexOf("datapack = ");
        check("段序: version 在 meta 前、meta 在 save 前、save 在 datapack 前",
                v >= 0 && v < m && m < s && s < d);
        int a = w1.indexOf("world.a");
        int seed = w1.indexOf("world.seed");
        int z = w1.indexOf("world.z");
        check("meta 排序: world.a 在 world.seed 前、world.seed 在 world.z 前（排序恒等）",
                a >= 0 && a < seed && seed < z);
    }

    // ---------- 4. packer directory round-trip ----------

    private static void packerRoundTrip(DeterministicFixture fx) throws IOException {
        // Write the five slot docs (enum order); relic directory intentionally absent.
        for (SaveSlot slot : FIVE_SLOTS) {
            TdTable doc = fx.container().document(slot);
            writeText(fx.saveRoot().resolve(slot.dir()).resolve("doc.td"), Td.write(doc));
        }
        String p1 = WorldPackPacker.pack(fx.saveRoot(), fx.srcDatapack(), META);

        Path saveOut = STAGING.resolve("save-out");
        Path datOut = STAGING.resolve("dat-out");
        WorldPackResult up = WorldPackPacker.unpack(p1, saveOut, datOut);

        boolean fiveWritten = true;
        for (SaveSlot slot : FIVE_SLOTS) {
            if (!Files.isRegularFile(saveOut.resolve(slot.dir()).resolve("doc.td"))) {
                fiveWritten = false;
            }
        }
        boolean relicAbsent = !Files.exists(saveOut.resolve("relic"));
        check("Packer: saveOut 写回 5 槽 doc.td，且 relic 目录不产生", fiveWritten && relicAbsent);

        boolean contentOk = true;
        for (SaveSlot slot : FIVE_SLOTS) {
            String src = Td.write(fx.container().document(slot));
            String dst = Files.readString(saveOut.resolve(slot.dir()).resolve("doc.td"));
            if (!Td.write(Td.parse(dst)).equals(src)) {
                contentOk = false;
            }
        }
        check("Packer: saveOut 每槽 Td.parse 文本与源一致", contentOk);

        check("Packer: datOut 文件集与内容 == srcDatapack（DatapackPack 往返保证）",
                DatapackPack.export(datOut).equals(DatapackPack.export(fx.srcDatapack())));

        check("Packer: 再打包 pack(saveOut, datOut, meta) 与 p1 逐字节相等",
                WorldPackPacker.pack(saveOut, datOut, META).equals(p1));
    }

    // ---------- 5. ledger/domain surface ----------

    private static void ledgerDomainSurface(DeterministicFixture fx) {
        WorldPackResult re = WorldPack.rehydrate(fx.w1());
        check("表面: ledger() Td.write 与源挂载文档一致",
                Td.write(re.ledger()).equals(Td.write(fx.ledgerDoc())));
        check("表面: domain() Td.write 与源挂载文档一致",
                Td.write(re.domain()).equals(Td.write(fx.domainDoc())));

        // A container without the LEDGER slot still exports; ledger() returns an empty table.
        SaveContainer noLedger = new SaveContainer().attach(SaveSlot.WORLD, fx.worldDoc());
        WorldPackResult er = WorldPack.rehydrate(WorldPack.export(noLedger, fx.srcDatapack(), META));
        check("表面: 缺槽容器导出后 ledger() 返回空表不抛",
                er.ledger() != null && er.ledger().isEmpty());
    }

    // ---------- 6. version / malformed rejection ----------

    private static void malformedRejection(String w1) {
        check("畸形: 篡改 version 数值抛 IllegalArgumentException",
                throwsIAE(() -> WorldPack.rehydrate(w1.replace("version = 1", "version = 999"))));
        check("畸形: 去掉 world 顶层表抛 IllegalArgumentException",
                throwsIAE(() -> WorldPack.rehydrate("type tie<data>\n[]")));
        // world doc whose `save` field is an integer (not a string) — must be rejected.
        TdTable worldSaveInt = TdTable.builder()
                .put("version", TdValue.of(1L))
                .put("meta", TdTable.builder().build())
                .put("save", TdValue.of(3L))
                .put("datapack", TdValue.str(""))
                .build();
        String doc = "type tie<data>\n" + Td.write(TdTable.builder().put("world", worldSaveInt).build());
        check("畸形: 篡改 save 为字符串外类型抛 IllegalArgumentException",
                throwsIAE(() -> WorldPack.rehydrate(doc)));
    }

    // ---------- 7. ExportHub wiring ----------

    private static void exportHub() {
        ExportHub.preload();
        ExportHub.preload(); // idempotent: second call is a no-op
        check("ExportHub: preload 幂等（二次调用不抛、不重复）",
                ExportHub.kinds().equals(List.of(ExportKind.WORLD_PACK, ExportKind.SAVE)));
        check("ExportHub: kinds() == [WORLD_PACK, SAVE]（枚举序）",
                ExportHub.kinds().equals(List.of(ExportKind.WORLD_PACK, ExportKind.SAVE)));
        check("ExportHub: port(WORLD_PACK).description() 非空",
                ExportHub.port(ExportKind.WORLD_PACK) != null
                        && !ExportHub.port(ExportKind.WORLD_PACK).description().isBlank());
        check("ExportHub: 重复 register(WORLD_PACK, ...) 抛 IllegalArgumentException",
                throwsIAE(() -> ExportHub.register(ExportKind.WORLD_PACK, new ExportPort() {
                    @Override
                    public ExportKind kind() {
                        return ExportKind.WORLD_PACK;
                    }

                    @Override
                    public String description() {
                        return "dup";
                    }
                })));
    }

    // ---------- helpers ----------

    private static void cleanStaging() {
        deleteRecursively(STAGING);
        try {
            Files.createDirectories(STAGING);
        } catch (IOException e) {
            throw new UncheckedIOException("staging create failed: " + STAGING, e);
        }
    }

    private static void writeText(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text);
        } catch (IOException e) {
            throw new UncheckedIOException("write failed: " + file, e);
        }
    }

    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    // best-effort cleanup
                }
            });
        } catch (IOException e) {
            // best-effort cleanup
        }
    }

    private interface ThrowingRunnable {
        void run();
    }

    private static boolean throwsIAE(ThrowingRunnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}