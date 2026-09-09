package io.toterra.subterra.engine.save.doc;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 遗物条目文档（p.2.3.4，槽 {@code SaveSlot.RELIC}）：每个遗物 { id, count, tier?, foundAt, note }
 * 记录的列表，通用但具体。字段语义由 Toterra 领域层赋予（{@code id} 遗物标识 / {@code count} 数量 i64 /
 * {@code tier} 可选档位 / {@code foundAt} 发现时间戳 / {@code note} 附注），本框架只保证确定性 td 编码与
 * 无损往返。
 * <p>
 * td 形状（toTd 产裸 {@link TdTable}，不含文档级头）：
 * <pre>
 * [
 *   version = 1,
 *   entries = [
 *     [ id = "...", count = 3, tier = "rare", foundAt = 1700000000000, note = "..." ],
 *     ...
 *   ]
 * ]
 * </pre>
 * {@link #toTd()} 前按 {@code (id 词法序 → foundAt → count → tier → note)} 稳定排序；{@code tier} 为空时
 * 该字段省略。字段语义为通用命名，业务含义由 Toterra 领域层赋予，框架只管确定性编码与往返。
 * <p>
 * Relic-entry document (p.2.3.4, slot {@code SaveSlot.RELIC}): a list of per-relic records
 * { id, count, tier?, foundAt, note }. Field semantics are assigned by the Toterra domain
 * layer; the framework only guarantees deterministic td encoding and lossless round-trip.
 * Entries sort stably by {@code (id → foundAt → count → tier → note)} before
 * {@link #toTd()}; a null {@code tier} is omitted from the emitted row.
 */
public final class RelicDoc {

    private static final String VERSION_KEY = "version";
    private static final String ENTRIES_KEY = "entries";

    /** 单个遗物记录 / one relic record. */
    public record RelicEntry(String id, long count, String tier, long foundAt, String note) {
    }

    private static final Comparator<RelicEntry> ORDER = Comparator
            .comparing(RelicEntry::id)
            .thenComparingLong(RelicEntry::foundAt)
            .thenComparingLong(RelicEntry::count)
            .thenComparing(RelicEntry::tier, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(RelicEntry::note);

    private final List<RelicEntry> entries;

    private RelicDoc(List<RelicEntry> entries) {
        List<RelicEntry> copy = new ArrayList<>(entries);
        copy.sort(ORDER);
        this.entries = List.copyOf(copy);
    }

    /** 归一化构建（拷贝 + 确定性排序）。Normalising factory (copies + deterministic sort). */
    public static RelicDoc of(List<RelicEntry> entries) {
        return new RelicDoc(entries);
    }

    /** 条目视图（已确定性排序）。Deterministically ordered entry view. */
    public List<RelicEntry> entries() {
        return entries;
    }

    /** 序列化为裸 {@link TdTable}（线性 builder，防 O(n²)）。Serialises to a bare {@link TdTable}. */
    public TdTable toTd() {
        TdTable.Builder eb = TdTable.builder();
        for (RelicEntry e : entries) {
            TdTable.Builder rb = TdTable.builder()
                    .put("id", e.id())
                    .put("count", TdValue.of(e.count()));
            if (e.tier() != null) {
                rb.put("tier", e.tier());
            }
            rb.put("foundAt", TdValue.of(e.foundAt()))
                    .put("note", e.note());
            eb.element(rb.build());
        }
        return TdTable.builder()
                .put(VERSION_KEY, TdValue.of(1L))
                .put(ENTRIES_KEY, eb.build())
                .build();
    }

    /** 反解析（缺失 / 非法字段取默认值，{@code tier} 缺省为 null）。Parses a bare {@link TdTable}. */
    public static RelicDoc fromTd(TdTable t) {
        List<RelicEntry> out = new ArrayList<>();
        TdValue ev = t.get(ENTRIES_KEY);
        if (ev instanceof TdTable et) {
            for (TdValue v : et.elements()) {
                if (v instanceof TdTable row) {
                    TdValue id = row.get("id");
                    TdValue count = row.get("count");
                    TdValue tier = row.get("tier");
                    TdValue foundAt = row.get("foundAt");
                    TdValue note = row.get("note");
                    out.add(new RelicEntry(
                            id != null ? id.asString() : "",
                            count != null ? count.asInt() : 0L,
                            tier != null ? tier.asString() : null,
                            foundAt != null ? foundAt.asInt() : 0L,
                            note != null ? note.asString() : ""));
                }
            }
        }
        return new RelicDoc(out);
    }
}