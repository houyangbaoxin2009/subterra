package io.toterra.subterra.probes;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.save.SaveContainer;
import io.toterra.subterra.engine.save.SaveSlot;
import io.toterra.subterra.engine.zd.ZdDocWriter;
import io.toterra.subterra.engine.zd.ZdHeader;
import io.toterra.subterra.engine.zd.ZdPrimitives;
import io.toterra.subterra.engine.zd.ZdRow;
import io.toterra.subterra.engine.zd.ZdVolume;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic acceptance probe for the p.2.3.1 generic zd v2 carrier and the
 * SaveContainer skeleton (pure JVM — no Minecraft runtime).
 * <p>
 * Asserts: the 10-byte zd v2 header (magic + version + flags bits, invalid rejects);
 * row-level round-trips byte-for-byte (kind 2/1/3/0, UTF-8 keys, encI64 boundary
 * values, f64 bit patterns, child counts 0..3); tree-level round-trips byte-for-byte
 * and structurally consistent (named + bare interleaved, bool/f64/i64/str); and the
 * SaveContainer contract (6 slots mounted, duplicate-mount override, overlay wins in
 * {@code effectiveRules}, deterministic {@code renderRules} with sorted keys, embedded
 * {@code rules} extraction).
 * Exit 0 = PASS, exit 1 = FAIL (gates acceptance; never shipped in the mod jar).
 * <p>
 * p.2.3.1 通用 zd v2 载体与 SaveContainer 骨架的确定性验收探针（纯 JVM，不依赖 MC）。
 * 断言：10 字节 zd v2 头（魔数 + 版本 + flags 位，非法拒绝）；行级往返逐字节（kind 2/1/3/0、
 * UTF-8 key、encI64 边界值、f64 位模式、child 0..3）；树级往返逐字节且结构一致（具名 + 裸元
 * 素混排、bool/f64/i64/str）；以及 SaveContainer 契约（6 槽挂载、重复挂载覆盖、
 * {@code effectiveRules} 中 overlay 胜出、{@code renderRules} 确定性且 key 排序、内嵌
 * {@code rules} 解析）。退出码 0 = PASS，1 = FAIL。
 */
public final class SaveProbe {

    private SaveProbe() {
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

    private static ZdRow rowEqMatch(List<ZdRow> a, List<ZdRow> b) {
        if (a == null || b == null || a.size() != b.size()) {
            return null;
        }
        for (int i = 0; i < a.size(); i++) {
            ZdRow x = a.get(i);
            ZdRow y = b.get(i);
            if (x.kind() != y.kind() || !x.key().equals(y.key())
                    || x.valueI64() != y.valueI64()
                    || Double.doubleToLongBits(x.valueF64()) != Double.doubleToLongBits(y.valueF64())
                    || !x.valueStr().equals(y.valueStr()) || x.childCount() != y.childCount()) {
                return x;
            }
        }
        return null;
    }

    public static void main(String[] args) {
        headerChecks();
        rowRoundTrip();
        treeRoundTrip();
        saveContainerChecks();

        if (failures == 0) {
            System.out.println("[SaveProbe] PASS (zd carrier + SaveContainer, " + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[SaveProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }

    // ---- 1 ZdHeader (p.2.3.1) ------------------------------------------------

    private static void headerChecks() {
        byte[] empty = ZdHeader.writeZ(false, false, false, false, false);
        byte[] expectEmpty = {'T', 'I', 'E', 'D', 'B', 'Z', 'D', 0x00, 0x02, 0x00};
        check("ZdHeader: all-false writes 10-byte TIEDBZD v2 flags=0",
                empty.length == 10 && Arrays.equals(empty, expectEmpty));

        byte[] combo = ZdHeader.writeZ(true, false, true, true, false);
        boolean comboOk = combo.length == 10
                && combo[0] == 'T' && combo[7] == 0x00 && combo[8] == 0x02
                && (combo[9] & 0xFF) == (ZdHeader.FLAG_DICTIONARY | ZdHeader.FLAG_EXT | ZdHeader.FLAG_STREAMING);
        check("ZdHeader: dict|ext|streaming sets flags bits exactly", comboOk);

        byte[] full = ZdHeader.writeZ(true, true, true, true, true);
        boolean fullOk = (full[9] & 0xFF) == 0x1F;
        check("ZdHeader: all five flags => 0x1F", fullOk);

        check("ZdHeader.isZd true for a written header", ZdHeader.isZd(empty));
        check("ZdHeader.isZd false for garbage / short", !ZdHeader.isZd(new byte[]{1, 2, 3, 4})
                && !ZdHeader.isZd(new byte[]{}));
        byte[] badVersion = {'T', 'I', 'E', 'D', 'B', 'Z', 'D', 0x00, 0x03, 0x00};
        check("ZdHeader.isZd false for wrong version", !ZdHeader.isZd(badVersion));

        boolean invalidThrows = false;
        try {
            ZdHeader.parseVersion(new byte[]{9, 9, 9, 9, 9, 9, 9, 9, 9, 9}, 0);
        } catch (IllegalArgumentException e) {
            invalidThrows = true;
        }
        check("ZdHeader.parseVersion rejects bad magic", invalidThrows);
        check("ZdHeader.parseVersion == 2 for v2 header",
                ZdHeader.parseVersion(empty, 0) == 2 && ZdHeader.flags(empty, 0) == 0);
        check("ZdPrimitives.encI64 symmetric edge", Arrays.equals(ZdPrimitives.encI64(127L), new byte[]{127})
                && Arrays.equals(ZdPrimitives.encI64(-1L), new byte[]{(byte) 0xFF}));
    }

    // ---- 2 Row-level round-trip (p.2.3.1) ------------------------------------

    private static void rowRoundTrip() {
        long[] edge = {0L, 127L, 128L, 255L, 256L, 65535L, 65536L, 4294967295L,
                4294967296L, 10000000000L, -1L, -32L, -33L, -128L, -129L, -32768L,
                -32769L, -2147483648L, -2147483649L};
        List<ZdRow> rows = new ArrayList<>();
        for (int i = 0; i < edge.length; i++) {
            rows.add(new ZdRow(2, "k" + i, edge[i], 0.0, "", 0));
        }
        rows.add(new ZdRow(1, "雨林", 0L, 0.0, "山脉文本", 0));
        rows.add(new ZdRow(3, "坐标", 0L, 3.14159, "", 0));
        rows.add(new ZdRow(3, "负零", 0L, -0.0, "", 0));
        rows.add(new ZdRow(3, "nan", 0L, Double.longBitsToDouble(0x7FF0000000000001L), "", 0));
        rows.add(new ZdRow(0, "root", 0L, 0.0, "", 2));
        rows.add(new ZdRow(0, "", 0L, 0.0, "", 0));
        rows.add(new ZdRow(2, "", 42L, 0.0, "", 0));

        byte[] b1 = ZdDocWriter.write(0, rows);
        List<ZdRow> back = ZdVolume.readRows(b1);
        ZdRow mismatch = rowEqMatch(rows, back);
        check("rows: readRows reproduces every field (enkI64 boundaries incl. NaN f64)",
                mismatch == null);
        byte[] b2 = ZdDocWriter.write(0, back);
        check("rows: write(read(write)) byte-for-byte equal", Arrays.equals(b1, b2));
    }

    // ---- 3 Tree-level round-trip (p.2.3.1) -----------------------------------

    private static void treeRoundTrip() {
        TdTable tree = TdTable.builder()
                .put("name", TdValue.str("雨林"))
                .put("depth", TdValue.of(128L))
                .put("ratio", TdValue.of(1.5))
                .put("active", TdValue.of(true))
                .put("nested", TdTable.builder()
                        .put("x", TdValue.of(7L))
                        .element(TdValue.of(true))
                        .build())
                .element(TdValue.str("裸元素"))
                .element(TdTable.builder()
                        .element(TdValue.of(9L))
                        .element(TdValue.of(false))
                        .build())
                .build();

        byte[] t1 = ZdDocWriter.writeTree(0, tree);
        TdTable back = ZdVolume.readTree(t1);
        byte[] t2 = ZdDocWriter.writeTree(0, back);
        check("tree: writeTree(readTree(writeTree)) byte-for-byte equal", Arrays.equals(t1, t2));

        boolean struct = back.keys().equals(tree.keys())
                && back.elements().size() == tree.elements().size()
                && "雨林".equals(back.get("name").asString())
                && back.get("depth").asInt() == 128
                && back.get("ratio").asFloat() == 1.5
                && back.get("active").asInt() == 1;
        check("tree: readTree structure matches (keys order, elements count, scalar values)", struct);

        TdValue nested = back.get("nested");
        boolean nestedOk = nested instanceof TdTable nt
                && nt.keys().equals(List.of("x"))
                && nt.get("x").asInt() == 7
                && nt.elements().size() == 1
                && nt.elements().get(0).asInt() == 1;
        check("tree: nested named+x and bare bool-element reconstructed", nestedOk);

        TdValue elems = back.elements().size() == 2 ? back.elements().get(1) : null;
        boolean elemOk = elems instanceof TdTable et
                && et.elements().size() == 2
                && et.elements().get(0).asInt() == 9
                && et.elements().get(1).asInt() == 0;
        check("tree: bare array-of-values reconstructed (bool folded 0)", elemOk);
    }

    // ---- 4 SaveContainer (p.2.3.1) -------------------------------------------

    private static void saveContainerChecks() {
        SaveContainer sc = new SaveContainer();

        TdTable world1 = TdTable.builder().put("seed", TdValue.of(42L)).build();
        sc.attach(SaveSlot.WORLD, world1);
        check("sc.document(WORLD) returns mounted doc", sc.document(SaveSlot.WORLD) == world1);

        TdTable world2 = TdTable.builder().put("seed", TdValue.of(99L)).build();
        sc.attach(SaveSlot.WORLD, world2);
        check("sc.attach duplicate slot overrides (WORLD now world2)",
                sc.document(SaveSlot.WORLD) == world2 && sc.document(SaveSlot.WORLD) != world1);

        Map<String, TdValue> ov = new LinkedHashMap<>();
        ov.put("seed", TdValue.of(777L));
        sc.overlay(SaveSlot.WORLD, ov);
        Map<String, TdValue> eff = sc.effectiveRules(SaveSlot.WORLD);
        check("sc.effectiveRules: overlay hits (seed==777), doc absent-rules -> only overlay",
                eff.size() == 1 && eff.get("seed").asInt() == 777);

        String rr1 = sc.renderRules(SaveSlot.WORLD);
        String rr2 = sc.renderRules(SaveSlot.WORLD);
        check("sc.renderRules: deterministic twice + sorted 'seed=777'",
                rr1.equals(rr2) && "seed=777".equals(rr1));

        // rules table extraction from a document (shape like DatapackRules.fromManifest)
        TdTable rulesTable = TdTable.builder()
                .element(TdTable.builder().put("k", "wild").put("v", TdValue.of(true)).build())
                .element(TdTable.builder().put("k", "rate").put("v", TdValue.of(2.5)).build())
                .build();
        TdTable cfg = TdTable.builder().put("rules", rulesTable).build();
        sc.attach(SaveSlot.CONFIG, cfg);
        Map<String, TdValue> fromSlot = sc.rules(SaveSlot.CONFIG);
        check("sc.rules: embedded rules extracted (wild, rate)",
                fromSlot.size() == 2 && fromSlot.get("wild").asBool()
                        && fromSlot.get("rate").asFloat() == 2.5);

        sc.attach(SaveSlot.LEDGER, TdTable.builder().build());
        check("sc.rules: doc without rules -> empty map", sc.rules(SaveSlot.LEDGER).isEmpty());
        check("sc.rules: unmounted slot -> empty map", sc.rules(SaveSlot.DOMAIN).isEmpty());

        Map<String, TdValue> cfgOv = new LinkedHashMap<>();
        cfgOv.put("rate", TdValue.of(9.9));
        sc.overlay(SaveSlot.CONFIG, cfgOv);
        Map<String, TdValue> effCfg = sc.effectiveRules(SaveSlot.CONFIG);
        String rrCfg = sc.renderRules(SaveSlot.CONFIG);
        boolean cfgEff = effCfg.get("rate").asFloat() == 9.9 && effCfg.get("wild").asBool()
                && rrCfg.equals(sc.renderRules(SaveSlot.CONFIG))
                && rrCfg.indexOf("rate=") < rrCfg.indexOf("wild=");
        check("sc: overlay wins over embedded rules + render sorted deterministic", cfgEff);

        // mount the remaining slots; slots() deterministic enum order
        sc.attach(SaveSlot.DOMAIN, TdTable.builder().build());
        sc.attach(SaveSlot.RELIC, TdTable.builder().build());
        sc.attach(SaveSlot.REGISTER, TdTable.builder().build());
        List<SaveSlot> slots = sc.slots();
        check("sc.slots(): 6 slots in enum order",
                slots.size() == 6 && slots.equals(Arrays.asList(SaveSlot.values()))
                        && slots.equals(List.of(SaveSlot.WORLD, SaveSlot.CONFIG, SaveSlot.LEDGER,
                                SaveSlot.DOMAIN, SaveSlot.RELIC, SaveSlot.REGISTER)));
        check("sc.dir(): each slot lowercase dir", slots.stream()
                .allMatch(s -> s.dir().equals(s.name().toLowerCase())));

        // p.2.3.5 global placeholder
        sc.readGlobalRules(TdTable.builder()
                .put("rules", TdTable.builder()
                        .element(TdTable.builder().put("k", "g").put("v", TdValue.of(1L)).build())
                        .build())
                .build());
        check("sc.globalRules stub reads one key (p.2.3.5 placeholder)",
                sc.globalRules().get("g").asInt() == 1);
    }
}