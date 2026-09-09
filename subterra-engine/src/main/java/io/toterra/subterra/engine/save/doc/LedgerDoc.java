package io.toterra.subterra.engine.save.doc;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 藏录文档（p.2.3.4，槽 {@code SaveSlot.LEDGER}）：一条按主题索引、带递增序号与时间戳的
 * 元数据记录列表中，通用但具体。字段语义由 Toterra 领域层赋予（{@code art} 主题 /
 * {@code seq} 递增序号 / {@code when} 时间戳 / {@code note} 附注），本框架只保证确定性的
 * td 编码与无损往返。
 * <p>
 * td 形状（toTd 产裸 {@link TdTable}，不含 {@code type tie<data>} 文档级头；槽挂载直接 attach）：
 * <pre>
 * [
 *   version = 1,
 *   entries = [
 *     [ k = "...", seq = 1, when = 1700000000000, note = "..." ],
 *     ...
 *   ]
 * ]
 * </pre>
 * {@link #toTd()} 前按 {@code (k 词法序 → seq → when → note)} 稳定排序，同输入产出逐字节一致的
 * 文本、乱序输入产出相同文本。{@code entry{}} 独立落盘时用 {@code io.toterra.subterra.engine.config.Td#write}，
 * 本子项不落盘。
 * <p>
 * Ledger document (p.2.3.4, slot {@code SaveSlot.LEDGER}): a generic-but-concrete list of
 * topic-keyed metadata records with a monotonic sequence number and a timestamp. Field
 * semantics are assigned by the Toterra domain layer ({@code art} topic / {@code seq}
 * monotonic seq / {@code when} timestamp / {@code note} remark); the framework only
 * guarantees deterministic td encoding and lossless round-trip.
 * <p>
 * td shape ({@link #toTd()} emits a bare {@link TdTable} with no {@code type tie<data>}
 * document-level header; mount directly via attach): see above. Entries are sorted
 * stably by {@code (k lexicographic → seq → when → note)} before {@link #toTd()}, so the
 * same input yields byte-identical text and shuffled input yields the same output.
 */
public final class LedgerDoc {

    private static final String VERSION_KEY = "version";
    private static final String ENTRIES_KEY = "entries";

    /** 藏录一条记录 / one ledger record. */
    public record LedgerEntry(String k, long seq, long when, String note) {
    }

    private static final Comparator<LedgerEntry> ORDER = Comparator
            .comparing(LedgerEntry::k)
            .thenComparingLong(LedgerEntry::seq)
            .thenComparingLong(LedgerEntry::when)
            .thenComparing(LedgerEntry::note);

    private final List<LedgerEntry> entries;

    private LedgerDoc(List<LedgerEntry> entries) {
        List<LedgerEntry> copy = new ArrayList<>(entries);
        copy.sort(ORDER);
        this.entries = List.copyOf(copy);
    }

    /**
     * 归一化构建：内部条目始终按确定性序排列（拷贝 + 排序，不改入参）。Normalising factory: the
     * internal entry list is always kept in deterministic order (copies + sorts, never mutates the input).
     */
    public static LedgerDoc of(List<LedgerEntry> entries) {
        return new LedgerDoc(entries);
    }

    /** 条目视图（已确定性排序）。Deterministically ordered entry view. */
    public List<LedgerEntry> entries() {
        return entries;
    }

    /** 序列化为裸 {@link TdTable}（线性 builder 构建，防 O(n²)）。Serialises to a bare {@link TdTable}. */
    public TdTable toTd() {
        TdTable.Builder eb = TdTable.builder();
        for (LedgerEntry e : entries) {
            eb.element(TdTable.builder()
                    .put("k", e.k())
                    .put("seq", TdValue.of(e.seq()))
                    .put("when", TdValue.of(e.when()))
                    .put("note", TdValue.str(e.note()))
                    .build());
        }
        return TdTable.builder()
                .put(VERSION_KEY, TdValue.of(1L))
                .put(ENTRIES_KEY, eb.build())
                .build();
    }

    /** 反解析（缺失 / 非法字段取默认值，不影响确定性）。Parses a bare {@link TdTable}. */
    public static LedgerDoc fromTd(TdTable t) {
        List<LedgerEntry> out = new ArrayList<>();
        TdValue ev = t.get(ENTRIES_KEY);
        if (ev instanceof TdTable et) {
            for (TdValue v : et.elements()) {
                if (v instanceof TdTable row) {
                    TdValue k = row.get("k");
                    TdValue seq = row.get("seq");
                    TdValue when = row.get("when");
                    TdValue note = row.get("note");
                    out.add(new LedgerEntry(
                            k != null ? k.asString() : "",
                            seq != null ? seq.asInt() : 0L,
                            when != null ? when.asInt() : 0L,
                            note != null ? note.asString() : ""));
                }
            }
        }
        return new LedgerDoc(out);
    }
}