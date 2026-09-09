package io.toterra.subterra.engine.save.doc;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 领域档案文档（p.2.3.4，槽 {@code SaveSlot.DOMAIN}）：每个领域 { name, kind, depth?, note } 记录的
 * 列表，通用但具体。字段语义由 Toterra 领域层赋予（{@code name} 领域名 / {@code kind} 类别 /
 * {@code depth} 可选深度 / {@code note} 附注），本框架只保证确定性 td 编码与无损往返。
 * <p>
 * td 形状（toTd 产裸 {@link TdTable}，不含文档级头）：
 * <pre>
 * [
 *   version = 1,
 *   entries = [
 *     [ name = "...", kind = "...", depth = 2, note = "..." ],
 *     ...
 *   ]
 * ]
 * </pre>
 * {@link #toTd()} 前按 {@code (name 词法序 → kind → depth → note)} 稳定排序；{@code depth} 为空时
 * 该字段省略。字段语义为通用命名，业务含义由 Toterra 领域层赋予，框架只管确定性编码与往返。
 * <p>
 * Domain-archive document (p.2.3.4, slot {@code SaveSlot.DOMAIN}): a list of per-domain
 * records { name, kind, depth?, note }, generic-but-concrete. Field semantics are assigned
 * by the Toterra domain layer; the framework only guarantees deterministic td encoding and
 * lossless round-trip. Entries sort stably by {@code (name → kind → depth → note)} before
 * {@link #toTd()}; a null {@code depth} is omitted from the emitted row.
 */
public final class DomainDoc {

    private static final String VERSION_KEY = "version";
    private static final String ENTRIES_KEY = "entries";

    /** 单个领域记录 / one domain record. */
    public record DomainEntry(String name, String kind, Integer depth, String note) {
    }

    private static final Comparator<DomainEntry> ORDER = Comparator
            .comparing(DomainEntry::name)
            .thenComparing(DomainEntry::kind)
            .thenComparing(DomainEntry::depth, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(DomainEntry::note);

    private final List<DomainEntry> entries;

    private DomainDoc(List<DomainEntry> entries) {
        List<DomainEntry> copy = new ArrayList<>(entries);
        copy.sort(ORDER);
        this.entries = List.copyOf(copy);
    }

    /** 归一化构建（拷贝 + 确定性排序）。Normalising factory (copies + deterministic sort). */
    public static DomainDoc of(List<DomainEntry> entries) {
        return new DomainDoc(entries);
    }

    /** 条目视图（已确定性排序）。Deterministically ordered entry view. */
    public List<DomainEntry> entries() {
        return entries;
    }

    /** 序列化为裸 {@link TdTable}（线性 builder，防 O(n²)）。Serialises to a bare {@link TdTable}. */
    public TdTable toTd() {
        TdTable.Builder eb = TdTable.builder();
        for (DomainEntry e : entries) {
            TdTable.Builder rb = TdTable.builder()
                    .put("name", e.name())
                    .put("kind", e.kind());
            if (e.depth() != null) {
                rb.put("depth", TdValue.of(e.depth().longValue()));
            }
            rb.put("note", e.note());
            eb.element(rb.build());
        }
        return TdTable.builder()
                .put(VERSION_KEY, TdValue.of(1L))
                .put(ENTRIES_KEY, eb.build())
                .build();
    }

    /** 反解析（缺失 / 非法字段取默认值，{@code depth} 缺省为 null）。Parses a bare {@link TdTable}. */
    public static DomainDoc fromTd(TdTable t) {
        List<DomainEntry> out = new ArrayList<>();
        TdValue ev = t.get(ENTRIES_KEY);
        if (ev instanceof TdTable et) {
            for (TdValue v : et.elements()) {
                if (v instanceof TdTable row) {
                    TdValue name = row.get("name");
                    TdValue kind = row.get("kind");
                    TdValue depth = row.get("depth");
                    TdValue note = row.get("note");
                    out.add(new DomainEntry(
                            name != null ? name.asString() : "",
                            kind != null ? kind.asString() : "",
                            depth != null ? (int) depth.asInt() : null,
                            note != null ? note.asString() : ""));
                }
            }
        }
        return new DomainDoc(out);
    }
}