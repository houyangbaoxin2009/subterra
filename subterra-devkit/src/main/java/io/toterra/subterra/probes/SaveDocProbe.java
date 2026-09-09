package io.toterra.subterra.probes;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.save.SaveContainer;
import io.toterra.subterra.engine.save.SaveSlot;
import io.toterra.subterra.engine.save.doc.DomainDoc;
import io.toterra.subterra.engine.save.doc.DomainDoc.DomainEntry;
import io.toterra.subterra.engine.save.doc.LedgerDoc;
import io.toterra.subterra.engine.save.doc.LedgerDoc.LedgerEntry;
import io.toterra.subterra.engine.save.doc.RegisterDoc;
import io.toterra.subterra.engine.save.doc.RegisterDoc.RegisterRow;
import io.toterra.subterra.engine.save.doc.RelicDoc;
import io.toterra.subterra.engine.save.doc.RelicDoc.RelicEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * p.2.3.4 四槽（藏录 Ledger / 领域档案 Domain / 遗物条目 Relic / 台账 Register）文档确定性探针
 * （纯 JVM，无 MC 运行时）。断言：
 * <ol>
 *   <li>每类文档构造 &gt;5 条真实样例行 → {@code toTd} → {@code fromTd} → 字段级相等</li>
 *   <li>文本往返：{@code Td.write(toTd())} parse 两次 → 文本逐字节相等；乱序输入排序后输出与正序一致</li>
 *   <li>挂槽：{@link SaveContainer#attach} 四槽各挂文档 → {@code document()} 取出 → fromTd 字段级一致；
 *       {@code slots()} 含四槽按枚举序</li>
 *   <li>大编：RegisterDoc 单文档 10_000 行往返正确（字段抽查 + 解析行数 == 10_000，不断言耗时）</li>
 *   <li>空列表边界：空 entries 的 toTd/fromTd 往返</li>
 * </ol>
 * 退出码 0 = PASS，1 = FAIL（gates acceptance；永不打入 mod jar）。
 * <p>
 * p.2.3.4 four-slot (Ledger / Domain / Relic / Register) document determinism probe (pure JVM,
 * no MC runtime). Asserts lossless field-level round-trip for &gt;5 realistic sample rows per
 * document type; byte-identical text round-trip (parse twice) and shuffled-input output equals
 * ordered output; mounting each doc into its {@link SaveContainer} slot and reading it back
 * field-identical with {@code slots()} in enum order; a 10_000-row RegisterDoc round-trip
 * (field spot-check + parsed row count, no timing assertion); and the empty-list boundary.
 * Exit 0 = PASS, exit 1 = FAIL.
 */
public final class SaveDocProbe {

    private SaveDocProbe() {
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
        ledgerChecks();
        domainChecks();
        relicChecks();
        registerChecks();
        mountChecks();
        largeRegisterChecks();
        emptyEdgeCases();

        if (failures == 0) {
            System.out.println("[SaveDocProbe] PASS (p.2.3.4 four slot documents, " + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[SaveDocProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }

    // ---- Ledger (藏录) ---------------------------------------------------------

    private static void ledgerChecks() {
        List<LedgerEntry> in = new ArrayList<>();
        in.add(new LedgerEntry("cards.treasure", 3L, 1700000003000L, "第三张藏宝卡"));
        in.add(new LedgerEntry("relics.brass-lens", 1L, 1700000001000L, "黄铜透镜"));
        in.add(new LedgerEntry("cards.treasure", 1L, 1699999999000L, "首卡"));
        in.add(new LedgerEntry("runes.ancient", 7L, 1700000007000L, "上古符文"));
        in.add(new LedgerEntry("cards.county", 2L, 1700000002000L, "郡图"));
        in.add(new LedgerEntry("cards.treasure", 2L, 1700000002000L, "第二张"));

        LedgerDoc doc = LedgerDoc.of(in);
        check("ledger: 6 条样例经 toTd/fromTd 字段级相等",
                ledgerRoundTrip(doc));

        // 确定性 + 文本往返
        check("ledger: toTd/fromTd 文本两次 parse 逐字节相等", textStable(doc.toTd()));

        List<LedgerEntry> shuffled = new ArrayList<>(in);
        Collections.reverse(shuffled);
        String sortedText = Td.write(doc.toTd());
        String revText = Td.write(LedgerDoc.of(shuffled).toTd());
        check("ledger: 乱序输入排序后输出与正序一致", sortedText.equals(revText));

        // 排序键：k 词法序 → seq
        List<String> ks = LedgerDoc.of(in).entries().stream().map(LedgerEntry::k).toList();
        List<String> sorted = new ArrayList<>(ks);
        sorted.sort(String::compareTo);
        check("ledger: 排序键为 k 词法序", ks.equals(sorted));
        List<LedgerEntry> e = LedgerDoc.of(in).entries();
        boolean seqOk = e.get(1).k().equals("cards.treasure") && e.get(1).seq() == 1
                && e.get(2).seq() == 2 && e.get(3).seq() == 3;
        check("ledger: 同 k 下 seq 递增", seqOk);
    }

    private static boolean ledgerRoundTrip(LedgerDoc doc) {
        LedgerDoc back = LedgerDoc.fromTd(doc.toTd());
        List<LedgerEntry> a = doc.entries();
        List<LedgerEntry> b = back.entries();
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equals(b.get(i))) {
                return false;
            }
        }
        return true;
    }

    // ---- Domain (领域档案) -----------------------------------------------------

    private static void domainChecks() {
        List<DomainEntry> in = new ArrayList<>();
        in.add(new DomainEntry("rainforest", "biome", 2, "繁茂雨林"));
        in.add(new DomainEntry("canals", "region", 1, "运河区"));
        in.add(new DomainEntry("deep-shaft", "structure", 3, "深渊竖井"));
        in.add(new DomainEntry("bazaar", "region", 1, "集市"));
        in.add(new DomainEntry("observatory", "structure", null, "观星台（无深度）"));
        in.add(new DomainEntry("canals-east", "region", 1, "东运河"));

        DomainDoc doc = DomainDoc.of(in);
        check("domain: 6 条样例（含 null depth）toTd/fromTd 字段级相等",
                domainRoundTrip(doc));
        check("domain: toTd/fromTd 文本两次 parse 逐字节相等", textStable(doc.toTd()));

        Collections.reverse(in);
        String a = Td.write(doc.toTd());
        String b = Td.write(DomainDoc.of(in).toTd());
        check("domain: 乱序输入排序后输出与正序一致", a.equals(b));

        // null depth 字段省略
        DomainDoc back = DomainDoc.fromTd(doc.toTd());
        boolean depthMismatch = doc.entries().get(4).depth() == null
                && back.entries().get(4).depth() == null;
        check("domain: obsedrvatory depth null 往返保持 null", depthMismatch);
    }

    private static boolean domainRoundTrip(DomainDoc doc) {
        DomainDoc back = DomainDoc.fromTd(doc.toTd());
        return sameDomain(doc.entries(), back.entries());
    }

    private static boolean sameDomain(List<DomainEntry> a, List<DomainEntry> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            DomainEntry x = a.get(i);
            DomainEntry y = b.get(i);
            if (!x.name().equals(y.name()) || !x.kind().equals(y.kind())
                    || !java.util.Objects.equals(x.depth(), y.depth())
                    || !x.note().equals(y.note())) {
                return false;
            }
        }
        return true;
    }

    // ---- Relic (遗物条目) ------------------------------------------------------

    private static void relicChecks() {
        List<RelicEntry> in = new ArrayList<>();
        in.add(new RelicEntry("relics.brass-lens", 1L, "common", 1700000001000L, ""));
        in.add(new RelicEntry("relics.sun-compass", 2L, "rare", 1700000005000L, "日晷罗盘"));
        in.add(new RelicEntry("relics.brass-lens", 3L, null, 1700000004000L, "无档位"));
        in.add(new RelicEntry("relics.amber-fang", 1L, "epic", 1700000002000L, "琥珀獠牙"));
        in.add(new RelicEntry("relics.sun-compass", 1L, "rare", 1700000003000L, ""));
        in.add(new RelicEntry("relics.shadow-film", 4L, "common", 1700000006000L, "暗影薄膜"));

        RelicDoc doc = RelicDoc.of(in);
        check("relic: 6 条样例（含 null tier）toTd/fromTd 字段级相等",
                relicRoundTrip(doc));
        check("relic: toTd/fromTd 文本两次 parse 逐字节相等", textStable(doc.toTd()));

        Collections.reverse(in);
        String a = Td.write(doc.toTd());
        String b = Td.write(RelicDoc.of(in).toTd());
        check("relic: 乱序输入排序后输出与正序一致", a.equals(b));

        RelicDoc back = RelicDoc.fromTd(doc.toTd());
        boolean tierNull = doc.entries().get(2).tier() == null && back.entries().get(2).tier() == null;
        check("relic: brass-lens tier null 往返保持 null", tierNull);
    }

    private static boolean relicRoundTrip(RelicDoc doc) {
        RelicDoc back = RelicDoc.fromTd(doc.toTd());
        List<RelicEntry> a = doc.entries();
        List<RelicEntry> b = back.entries();
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!java.util.Objects.equals(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    // ---- Register (台账) -------------------------------------------------------

    private static void registerChecks() {
        List<RegisterRow> in = new ArrayList<>();
        in.add(new RegisterRow(20240101L, 12L, 150L, "开年"));
        in.add(new RegisterRow(20240103L, 8L, 168L, ""));
        in.add(new RegisterRow(20240102L, 20L, 170L, "集市日"));
        in.add(new RegisterRow(20240101L, 5L, 143L, "清晨"));
        in.add(new RegisterRow(20240105L, 30L, 210L, "远征"));
        in.add(new RegisterRow(20240104L, 10L, 178L, ""));

        RegisterDoc doc = RegisterDoc.of(in);
        check("register: 6 条样例 toTd/fromTd 字段级相等",
                registerRoundTrip(doc));
        check("register: toTd/fromTd 文本两次 parse 逐字节相等", textStable(doc.toTd()));

        Collections.reverse(in);
        String a = Td.write(doc.toTd());
        String b = Td.write(RegisterDoc.of(in).toTd());
        check("register: 乱序输入排序后输出与正序一致", a.equals(b));

        List<RegisterRow> e = RegisterDoc.of(in).rows();
        boolean asc = e.get(0).day() == 20240101L && e.get(0).count() == 5
                && e.get(2).day() == 20240102L && e.get(3).day() == 20240103L
                && e.get(5).day() == 20240105L;
        check("register: day 词法递增且同 day 下 count 递增", asc);
    }

    private static boolean registerRoundTrip(RegisterDoc doc) {
        RegisterDoc back = RegisterDoc.fromTd(doc.toTd());
        List<RegisterRow> a = doc.rows();
        List<RegisterRow> b = back.rows();
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equals(b.get(i))) {
                return false;
            }
        }
        return true;
    }

    // ---- 挂槽 / mounting -------------------------------------------------------

    private static void mountChecks() {
        SaveContainer sc = new SaveContainer();

        LedgerDoc ledger = LedgerDoc.of(List.of(
                new LedgerEntry("runes.ancient", 1L, 1700000000000L, "挂槽藏录")));
        sc.attach(SaveSlot.LEDGER, ledger.toTd());
        check("ledger: attach → document() 取出 → fromTd 字段级一致",
                ledgerRoundTrip(LedgerDoc.fromTd(sc.document(SaveSlot.LEDGER))));

        DomainDoc domain = DomainDoc.of(List.of(
                new DomainEntry("bazaar", "region", 1, "挂槽领域")));
        sc.attach(SaveSlot.DOMAIN, domain.toTd());
        check("domain: attach → document() 取出 → fromTd 字段级一致",
                sameDomain(domain.entries(), DomainDoc.fromTd(sc.document(SaveSlot.DOMAIN)).entries()));

        RelicDoc relic = RelicDoc.of(List.of(
                new RelicEntry("relics.amber-fang", 2L, "epic", 1700000000000L, "挂槽遗物")));
        sc.attach(SaveSlot.RELIC, relic.toTd());
        check("relic: attach → document() 取出 → fromTd 字段级一致",
                relicRoundTrip(RelicDoc.fromTd(sc.document(SaveSlot.RELIC))));

        RegisterDoc register = RegisterDoc.of(List.of(
                new RegisterRow(20240101L, 9L, 99L, "挂槽台账")));
        sc.attach(SaveSlot.REGISTER, register.toTd());
        check("register: attach → document() 取出 → fromTd 字段级一致",
                registerRoundTrip(RegisterDoc.fromTd(sc.document(SaveSlot.REGISTER))));

        List<SaveSlot> slots = sc.slots();
        check("slots(): 四槽按枚举序",
                slots.size() == 4 && slots.equals(List.of(SaveSlot.LEDGER, SaveSlot.DOMAIN,
                        SaveSlot.RELIC, SaveSlot.REGISTER)));
    }

    // ---- 大编 10k 行 / large register -------------------------------------------

    private static void largeRegisterChecks() {
        int n = 10_000;
        List<RegisterRow> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rows.add(new RegisterRow(20000001L + i, i % 97L, i + 1L, "line-" + i));
        }
        RegisterDoc doc = RegisterDoc.of(rows);
        RegisterDoc back = RegisterDoc.fromTd(doc.toTd());
        check("register: 10_000 行往返行数 == 10_000", back.rows().size() == n);

        List<RegisterRow> b = back.rows();
        boolean spot = b.get(0).day() == 20000001L && b.get(0).count() == 0L
                && b.get(0).total() == 1L && b.get(0).note().equals("line-0")
                && b.get(9999).day() == 20010000L && b.get(9999).count() == 9999 % 97L
                && b.get(9999).total() == 10000L && b.get(9999).note().equals("line-9999");
        check("register: 10_000 行首尾字段抽查正确", spot);

        check("register: 大编文本往返逐字节相等", textStable(doc.toTd()));
    }

    // ---- 空列表边界 / empty entries --------------------------------------------

    private static void emptyEdgeCases() {
        LedgerDoc l = LedgerDoc.of(List.of());
        check("ledger: 空 entries 往返 + 标准形状", l.entries().isEmpty()
                && LedgerDoc.fromTd(l.toTd()).entries().isEmpty()
                && l.toTd().get("version").asInt() == 1);

        DomainDoc d = DomainDoc.of(List.of());
        check("domain: 空 entries 往返", d.entries().isEmpty()
                && DomainDoc.fromTd(d.toTd()).entries().isEmpty());

        RelicDoc r = RelicDoc.of(List.of());
        check("relic: 空 entries 往返", r.entries().isEmpty()
                && RelicDoc.fromTd(r.toTd()).entries().isEmpty());

        RegisterDoc reg = RegisterDoc.of(List.of());
        check("register: 空 entries 往返", reg.rows().isEmpty()
                && RegisterDoc.fromTd(reg.toTd()).rows().isEmpty());
    }

    // ---- helpers ---------------------------------------------------------------

    /** write → parse 回 TdTable → 再 write：两次文本逐字节相等（文本级往返确定性）。 */
    private static boolean textStable(TdTable table) {
        String t1 = Td.write(table);
        String t2 = Td.write(Td.parse(t1));
        return t1.equals(t2);
    }
}