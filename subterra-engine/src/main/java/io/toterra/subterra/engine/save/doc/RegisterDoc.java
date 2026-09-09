package io.toterra.subterra.engine.save.doc;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 台账文档（p.2.3.4，槽 {@code SaveSlot.REGISTER}）：每行 { day, count, total, note } 记录的列表，
 * 通用但具体。字段语义由 Toterra 领域层赋予（{@code day} 日期 i64 / {@code count} 当日记数 / {@code total}
 * 累计 / {@code note} 附注），本框架只保证确定性 td 编码与无损往返。
 * <p>
 * td 形状（toTd 产裸 {@link TdTable}，不含文档级头）：
 * <pre>
 * [
 *   version = 1,
 *   entries = [
 *     [ day = 20240101, count = 12, total = 150, note = "..." ],
 *     ...
 *   ]
 * ]
 * </pre>
 * {@link #toTd()} 前按 {@code (day → count → total → note)} 稳定排序。字段语义为通用命名，业务含义由
 * Toterra 领域层赋予，框架只管确定性编码与往返。条目量大时（洞察探针 ~10k 行）经 TdTable builder 线性构建，
 * 不得做字符串层拼接（O(n²) 内存爆炸）。本类用于盘点大编往返，不做时序断言。
 * <p>
 * Register-document (p.2.3.4, slot {@code SaveSlot.REGISTER}): a list of per-line records
 * { day, count, total, note }. Field semantics are assigned by the Toterra domain layer; the
 * framework only guarantees deterministic td encoding and lossless round-trip. Entries sort
 * stably by {@code (day → count → total → note)} before {@link #toTd()}. For large entry
 * counts build through the TdTable builder linearly — never string-concatenate (O(n²)).
 */
public final class RegisterDoc {

    private static final String VERSION_KEY = "version";
    private static final String ENTRIES_KEY = "entries";

    /** 台账一行 / one register line. */
    public record RegisterRow(long day, long count, long total, String note) {
    }

    private static final Comparator<RegisterRow> ORDER = Comparator
            .comparingLong(RegisterRow::day)
            .thenComparingLong(RegisterRow::count)
            .thenComparingLong(RegisterRow::total)
            .thenComparing(RegisterRow::note);

    private final List<RegisterRow> rows;

    private RegisterDoc(List<RegisterRow> rows) {
        List<RegisterRow> copy = new ArrayList<>(rows);
        copy.sort(ORDER);
        this.rows = List.copyOf(copy);
    }

    /** 归一化构建（拷贝 + 确定性排序）。Normalising factory (copies + deterministic sort). */
    public static RegisterDoc of(List<RegisterRow> rows) {
        return new RegisterDoc(rows);
    }

    /** 行视图（已确定性排序）。Deterministically ordered row view. */
    public List<RegisterRow> rows() {
        return rows;
    }

    /** 序列化为裸 {@link TdTable}（线性 builder，防 O(n²)）。Serialises to a bare {@link TdTable}. */
    public TdTable toTd() {
        TdTable.Builder eb = TdTable.builder();
        for (RegisterRow e : rows) {
            eb.element(TdTable.builder()
                    .put("day", TdValue.of(e.day()))
                    .put("count", TdValue.of(e.count()))
                    .put("total", TdValue.of(e.total()))
                    .put("note", e.note())
                    .build());
        }
        return TdTable.builder()
                .put(VERSION_KEY, TdValue.of(1L))
                .put(ENTRIES_KEY, eb.build())
                .build();
    }

    /** 反解析（缺失 / 非法字段取默认值）。Parses a bare {@link TdTable}. */
    public static RegisterDoc fromTd(TdTable t) {
        List<RegisterRow> out = new ArrayList<>();
        TdValue ev = t.get(ENTRIES_KEY);
        if (ev instanceof TdTable et) {
            for (TdValue v : et.elements()) {
                if (v instanceof TdTable row) {
                    TdValue day = row.get("day");
                    TdValue count = row.get("count");
                    TdValue total = row.get("total");
                    TdValue note = row.get("note");
                    out.add(new RegisterRow(
                            day != null ? day.asInt() : 0L,
                            count != null ? count.asInt() : 0L,
                            total != null ? total.asInt() : 0L,
                            note != null ? note.asString() : ""));
                }
            }
        }
        return new RegisterDoc(out);
    }
}