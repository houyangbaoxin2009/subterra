package io.toterra.subterra.probes;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.datapack.DatapackRules;
import io.toterra.subterra.engine.save.SaveContainer;
import io.toterra.subterra.engine.save.SaveExportArchive;
import io.toterra.subterra.engine.save.SaveSlot;
import io.toterra.subterra.engine.save.doc.DomainDoc;
import io.toterra.subterra.engine.save.doc.DomainDoc.DomainEntry;
import io.toterra.subterra.engine.save.doc.LedgerDoc;
import io.toterra.subterra.engine.save.doc.LedgerDoc.LedgerEntry;
import io.toterra.subterra.engine.save.doc.RegisterDoc;
import io.toterra.subterra.engine.save.doc.RegisterDoc.RegisterRow;
import io.toterra.subterra.engine.save.doc.RelicDoc;
import io.toterra.subterra.engine.save.doc.RelicDoc.RelicEntry;
import io.toterra.subterra.engine.save.migrate.LevelDatum;
import io.toterra.subterra.engine.save.migrate.LevelZdt;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * p.2.3.6 save export archive 探针（纯 JVM，无 MC 运行时）：{@link SaveExportArchive} 的「导出 →
 * 再水化 → 导出」逐字节往返契约 + 字段级往返 + 边界/畸形输入。断言：
 * <ol>
 *   <li>多槽容器（WORLD 带真实 {@link LevelZdt#toTd} 产出、CONFIG 内嵌 rules 表、REGISTER /
 *       LEDGER / RELIC / DOMAIN 各一 + LOG/PROBE 两槽 overlay + 全局层）：{@code export()} 两次
 *       逐字节相等（确定性命中）；{@code rehydrate(export(c))} 逐槽 document 文本相等、每槽
 *       effectiveRules 相等、global 相等、slots() 相同；再 export → 与首次 export 逐字节相等
 *       （最强往返）。</li>
 *   <li>单槽无 overlay 无全局：往返字段级相等 + export→rehydrate→export 逐字节相等（「无 overlay
 *       则省略 rules」往返恒定）。</li>
 *   <li>空容器：export→rehydrate→export 逐字节相等、slots() 为空。</li>
 *   <li>畸形：未知 slot 值 / 版本 999 均抛 {@link IllegalArgumentException}。</li>
 * </ol>
 * 退出码 0 = PASS，1 = FAIL。
 * <p>
 * p.2.3.6 save export archive probe (pure JVM, no MC runtime): the
 * {@link SaveExportArchive} "export → rehydrate → export" byte-identical round-trip
 * contract, field-level round-trip, and boundary/malformed inputs. Exit 0 = PASS.
 */
public final class SaveExportProbe {

    private SaveExportProbe() {
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

    public static void main(String[] args) {
        multiSlotRoundTrip();
        singleSlotNoOverlay();
        emptyContainer();
        malformedInputs();

        if (failures == 0) {
            System.out.println("[SaveExportProbe] PASS (save export archive round-trip, " + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[SaveExportProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }

    private static void multiSlotRoundTrip() {
        // WORLD: real LevelZdt.toTd output parsed back to a TdTable (devkit can import engine).
        LevelDatum level = new LevelDatum("WorldA", 42L, 1000L,
                Map.of("doFireTick", TdValue.of(true), "randomTickSpeed", TdValue.of(3L)));
        TdTable worldDoc = Td.parse(LevelZdt.toTd(level));
        // CONFIG: doc with an embedded rules table.
        TdTable configDoc = TdTable.builder().put("rules", TdTable.builder()
                .element(rulesKv("a", 10L))
                .element(rulesKv("c", 5L))
                .build()).build();
        TdTable registerDoc = RegisterDoc.of(List.of(new RegisterRow(20240101L, 5L, 50L, "seed"))).toTd();
        TdTable ledgerDoc = LedgerDoc.of(List.of(new LedgerEntry("runes.ancient", 1L, 1L, "seed"))).toTd();
        TdTable relicDoc = RelicDoc.of(List.of(new RelicEntry("relics.amber-fang", 1L, "common", 1L, "seed"))).toTd();
        TdTable domainDoc = DomainDoc.of(List.of(new DomainEntry("bazaar", "region", 1, "seed"))).toTd();

        Map<String, TdValue> global = new LinkedHashMap<>();
        global.put("verbose", TdValue.str("on"));
        global.put("time", TdValue.of(1L));
        Map<String, TdValue> worldOverlay = new LinkedHashMap<>();
        worldOverlay.put("world.scale", TdValue.of(3L));
        worldOverlay.put("world.difficulty", TdValue.str("hard"));
        Map<String, TdValue> configOverlay = new LinkedHashMap<>();
        configOverlay.put("a", TdValue.of(99L));
        configOverlay.put("b", TdValue.of(2L));

        SaveContainer c = new SaveContainer()
                .attach(SaveSlot.WORLD, worldDoc)
                .attach(SaveSlot.CONFIG, configDoc)
                .attach(SaveSlot.REGISTER, registerDoc)
                .attach(SaveSlot.LEDGER, ledgerDoc)
                .attach(SaveSlot.RELIC, relicDoc)
                .attach(SaveSlot.DOMAIN, domainDoc)
                .overlay(SaveSlot.WORLD, worldOverlay)
                .overlay(SaveSlot.CONFIG, configOverlay)
                .globalRules(global);

        String e1 = SaveExportArchive.export(c);
        String e2 = SaveExportArchive.export(c);
        check("export: 两次逐字节相等（确定性）", e1.equals(e2));

        SaveContainer back = SaveExportArchive.rehydrate(e1);
        check("rehydrate: slots() 与源相同（枚举序）", back.slots().equals(c.slots()));

        boolean docEq = true;
        for (SaveSlot s : c.slots()) {
            if (!Td.write(back.document(s)).equals(Td.write(c.document(s)))) {
                docEq = false;
            }
        }
        check("rehydrate: 每槽 document Td.write 文本相等", docEq);

        boolean rulesEq = true;
        for (SaveSlot s : c.slots()) {
            if (!DatapackRules.render(back.effectiveRules(s)).equals(DatapackRules.render(c.effectiveRules(s)))) {
                rulesEq = false;
            }
        }
        check("rehydrate: 每槽 effectiveRules（含 overlay/doc rules）相等", rulesEq);

        check("rehydrate: globalRules 相等",
                DatapackRules.render(back.globalRules()).equals(DatapackRules.render(c.globalRules())));

        String e3 = SaveExportArchive.export(back);
        check("round-trip: rehydrate→export 与首次 export 逐字节相等", e3.equals(e1));
    }

    private static void singleSlotNoOverlay() {
        SaveContainer c = new SaveContainer()
                .attach(SaveSlot.REGISTER, RegisterDoc.of(List.of(
                        new RegisterRow(20240102L, 8L, 168L, ""))).toTd());
        SaveContainer back = SaveExportArchive.rehydrate(SaveExportArchive.export(c));
        boolean eq = back.slots().equals(c.slots())
                && Td.write(back.document(SaveSlot.REGISTER)).equals(Td.write(c.document(SaveSlot.REGISTER)))
                && back.globalRules().isEmpty();
        check("单槽无 overlay/global: 字段级往返一致", eq);
        String e1 = SaveExportArchive.export(c);
        String e2 = SaveExportArchive.export(back);
        check("单槽无 overlay/global: export→rehydrate→export 逐字节相等", e1.equals(e2));
    }

    private static void emptyContainer() {
        SaveContainer empty = new SaveContainer();
        String e1 = SaveExportArchive.export(empty);
        SaveContainer back = SaveExportArchive.rehydrate(e1);
        check("空容器: slots() 为空", back.slots().isEmpty());
        check("空容器: export→rehydrate→export 逐字节相等",
                SaveExportArchive.export(back).equals(e1));
    }

    private static void malformedInputs() {
        check("畸形: 未知 slot 值抛 IllegalArgumentException", throwsIAE(() -> SaveExportArchive.rehydrate(archive(
                TdTable.builder()
                        .put("version", TdValue.of(1L))
                        .put("global", TdTable.builder().build())
                        .put("slots", TdTable.builder().element(TdTable.builder()
                                .put("slot", TdValue.str("bogus"))
                                .put("doc", TdTable.builder().build())
                                .build()).build())
                        .build()))));
        check("畸形: 版本 999 抛 IllegalArgumentException", throwsIAE(() -> SaveExportArchive.rehydrate(archive(
                TdTable.builder().put("version", TdValue.of(999L)).build()))));
        check("畸形: 缺 export 顶层抛 IllegalArgumentException",
                throwsIAE(() -> SaveExportArchive.rehydrate("type tie<data>\n[]")));
    }

    /** Wraps a doc back into the named top-level `export = [...]` archive shape. */
    private static String archive(TdTable doc) {
        return "type tie<data>\n" + Td.write(TdTable.builder().put("export", doc).build());
    }

    private static TdTable rulesKv(String k, long v) {
        return TdTable.builder().put("k", TdValue.str(k)).put("v", TdValue.of(v)).build();
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static boolean throwsIAE(ThrowingRunnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}