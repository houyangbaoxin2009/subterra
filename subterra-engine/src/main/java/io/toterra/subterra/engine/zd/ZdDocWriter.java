package io.toterra.subterra.engine.zd;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * zd v2 文档写出器（p.2.3.1）：写 10 字节头 + 一串六字段 wire2 记录。类名取 zd-doc 以区别于
 * profiler 报告里同名的旧 {@code ZdWriter}（后者现在委托本类）。来自树的写出复用 DFS 先序平铺
 * 语义（与 {@code tdzd.flatten} / profiler 一致：kind 0=表/数组、1=字符串、2=整数与 bool、3=浮点，
 * bool 折叠 0/1，child = 具名条目 + 裸元素数，子序 = 先具名后裸元素）。
 * <p>
 * zd v2 document writer (p.2.3.1): writes a 10-byte header + a series of six-field
 * wire2 records. The class is named zd-doc to avoid clashing with the legacy
 * {@code ZdWriter} in the profiler report (which now delegates here). The tree path
 * reuses the DFS preorder flatten semantics (matching {@code tdzd.flatten} / the
 * profiler: kind 0 = table/array, 1 = string, 2 = integer & bool, 3 = float; bool folded
 * to 0/1; child = named entries + bare elements; child order = named then bare).
 */
public final class ZdDocWriter {

    private ZdDocWriter() {
    }

    /**
     * 写完整 zd 字节流（头 + 每行六字段）。Writes the full zd byte stream (header +
     * six-field row for every row).
     */
    public static byte[] write(int flags, List<ZdRow> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(ZdHeader.writeZ(flags), 0, 10);
        for (ZdRow r : rows) {
            writeRow(out, r);
        }
        return out.toByteArray();
    }

    /**
     * 以 DFS 先序把 {@code root} 树平铺为行再写出。Flattens the {@code root} tree in
     * DFS preorder into rows and writes them.
     */
    public static byte[] writeTree(int flags, TdTable root) {
        return write(flags, flatten(root));
    }

    /**
     * 用显式栈按 DFS 先序把一棵 {@link TdTable} 平铺为 {@link ZdRow} 列表。子顺序：先具名
     * 条目（按 {@code keys()}）后裸元素。Flattens a {@link TdTable} into a list of
     * {@link ZdRow} in DFS preorder with an explicit stack; children run named entries
     * (by {@code keys()}) then bare elements.
     */
    private static List<ZdRow> flatten(TdTable root) {
        List<ZdRow> rows = new ArrayList<>();
        Deque<TdValue> nodes = new ArrayDeque<>();
        Deque<String> keys = new ArrayDeque<>();
        nodes.push(root);
        keys.push("");
        while (!nodes.isEmpty()) {
            TdValue node = nodes.pop();
            String key = keys.pop();
            if (node instanceof TdTable t) {
                List<String> named = t.keys();
                List<TdValue> elems = t.elements();
                long child = (long) named.size() + elems.size();
                rows.add(new ZdRow(0, key, 0L, 0.0, "", child));
                // Push children in reverse so the first desired child pops first.
                for (int i = (int) child - 1; i >= 0; i--) {
                    if (i < named.size()) {
                        String k = named.get(i);
                        nodes.push(t.get(k));
                        keys.push(k);
                    } else {
                        nodes.push(elems.get(i - named.size()));
                        keys.push("");
                    }
                }
            } else {
                rows.add(scalarRow(key, node));
            }
        }
        return rows;
    }

    private static ZdRow scalarRow(String key, TdValue v) {
        TdValue.Scalar s = v.scalar();
        return switch (s.kind()) {
            case STRING -> new ZdRow(1, key, 0L, 0.0, s.str(), 0);
            case INT -> new ZdRow(2, key, s.i(), 0.0, "", 0);
            case FLOAT -> new ZdRow(3, key, 0L, s.f(), "", 0);
            case BOOL -> new ZdRow(2, key, s.b() ? 1L : 0L, 0.0, "", 0);
        };
    }

    /**
     * 写一条记录的固定六字段：tag 10 (kind)、18 (key)、26 (value_i64)、34 (value_f64)、
     * 42 (value_str)、50 (child_count)。Writes one node as the fixed six wire2 fields:
     * tags 10 (kind), 18 (key), 26 (value_i64), 34 (value_f64), 42 (value_str),
     * 50 (child_count).
     */
    private static void writeRow(ByteArrayOutputStream out, ZdRow r) {
        writeField(out, 10, ZdPrimitives.encI64(r.kind()));
        writeField(out, 18, ZdPrimitives.utf8(r.key()));
        writeField(out, 26, ZdPrimitives.encI64(r.valueI64()));
        writeField(out, 34, ZdPrimitives.be64(Double.doubleToLongBits(r.valueF64())));
        writeField(out, 42, ZdPrimitives.utf8(r.valueStr()));
        writeField(out, 50, ZdPrimitives.varint(r.childCount()));
    }

    private static void writeField(ByteArrayOutputStream out, int tag, byte[] payload) {
        ZdPrimitives.writeVarint(out, tag);
        ZdPrimitives.writeVarint(out, payload.length);
        out.write(payload, 0, payload.length);
    }
}