package io.toterra.subterra.engine.zd;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * zd 文档写出器（p.2.3.1，v3 升级 p.2.3.7）：写 10 字节头 + 一串六字段 wire2 记录。类名取
 * zd-doc 以区别于 profiler 报告里同名的旧 {@code ZdWriter}（后者现在委托本类）。来自树的写出
 * 复用 DFS 先序平铺语义（与 {@code tdzd.flatten} / profiler 一致：kind 0=表/数组、1=字符串、
 * 2=整数与 bool、3=浮点，bool 折叠 0/1，child = 具名条目 + 裸元素数，子序 = 先具名后裸元素）。
 * <p>
 * <b>v3 纪律（p.2.3.7）：</b>写方默认写 v3（头 {@code "03"}）；{@link #write} 产出
 * 无 footer 的 v3 文件（纯单段形态，v2 读取规则可读的正文布局）；{@link #writeIndexed}
 * 产出带索引 footer 的 v3 文件（头 bit5 + 段表 + 尾 footer，见 {@link ZdFooter}）；
 * {@link #writeV2} 保留 v2 字节兼容路径。正文行格式在 v2/v3 间逐字节一致，仅头版本与
 * 可选 footer 不同。
 * <p>
 * zd document writer (p.2.3.1; v3 upgrade p.2.3.7): writes a 10-byte header + a series
 * of six-field wire2 records. The class is named zd-doc to avoid clashing with the
 * legacy {@code ZdWriter} in the profiler report (which now delegates here). The tree
 * path reuses the DFS preorder flatten semantics (matching {@code tdzd.flatten} / the
 * profiler: kind 0 = table/array, 1 = string, 2 = integer & bool, 3 = float; bool folded
 * to 0/1; child = named entries + bare elements; child order = named then bare).
 * <p>
 * <b>v3 discipline (p.2.3.7):</b> writers default to v3 (header {@code "03"});
 * {@link #write} produces a footerless v3 file (single-segment shape, a body layout
 * readable under the v2 reading rules); {@link #writeIndexed} produces a v3 file with
 * the index footer (header bit5 + segment table + tail footer, see {@link ZdFooter});
 * {@link #writeV2} keeps the v2 byte-compatibility path. The row body is byte-identical
 * across v2/v3 — only the header version and the optional footer differ.
 */
public final class ZdDocWriter {

    private ZdDocWriter() {
    }

    /**
     * 写完整 zd <b>v3</b> 字节流（头 "03" + 每行六字段，无 footer；bit5 不置位）。
     * p.2.3.7 起的默认写路径。Writes the full zd <b>v3</b> byte stream (header "03" +
     * six-field row per row, no footer; bit5 clear). The default write path from
     * p.2.3.7 on.
     */
    public static byte[] write(int flags, List<ZdRow> rows) {
        return writeVersioned(ZdHeader.VERSION_V3, flags & ZdHeader.FLAGS_MASK_V3, rows);
    }

    /**
     * 写完整 zd <b>v2</b> 字节流（头 "02"，低 5 位 flags）——v2 字节兼容写路径。
     * Writes the full zd <b>v2</b> byte stream (header "02", low-5-bit flags) — the
     * v2 byte-compatibility write path.
     */
    public static byte[] writeV2(int flags, List<ZdRow> rows) {
        return writeVersioned(ZdHeader.VERSION_V2, flags & ZdHeader.FLAGS_MASK_V2, rows);
    }

    private static byte[] writeVersioned(int version, int flags, List<ZdRow> rows) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(ZdHeader.write(version, flags), 0, 10);
        for (ZdRow r : rows) {
            writeRow(out, r);
        }
        return out.toByteArray();
    }

    /**
     * 写带索引 footer 的 zd v3 字节流（p.2.3.7）：头（bit5 置位）+ 数据体行 + 索引段（单条
     * 段类型 0 条目，指向数据体）+ 25 字节尾 footer；crc32 覆盖索引段。数据体从偏移 10 起、
     * 索引段紧随其后——布局确定性，同输入同字节。
     * Writes a v3 zd byte stream with the index footer (p.2.3.7): header (bit5 set) +
     * body rows + the index segment (a single type-0 entry pointing at the body) + the
     * 25-byte tail footer; crc32 covers the index segment. The body starts at offset 10
     * and the index segment follows directly — deterministic layout, same input → same
     * bytes.
     */
    public static byte[] writeIndexed(int flags, List<ZdRow> rows) {
        int v3Flags = (flags & ZdHeader.FLAGS_MASK_V3) | ZdHeader.FLAG_INDEX;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(ZdHeader.write(ZdHeader.VERSION_V3, v3Flags), 0, 10);
        for (ZdRow r : rows) {
            writeRow(out, r);
        }
        long bodyOff = 10;
        long bodyLen = out.size() - 10;
        byte[] index = ZdFooter.encodeIndex(List.of(new ZdFooter.Segment(
                ZdFooter.SEG_DATA, bodyOff, bodyLen, "")));
        // The index segment's in-file offset is the stream size BEFORE appending it
        // (header + body); the footer then follows the index directly.
        long indexOff = out.size();
        out.write(index, 0, index.length);
        byte[] footer = ZdFooter.write(indexOff, index);
        out.write(footer, 0, footer.length);
        return out.toByteArray();
    }

    /**
     * 以 DFS 先序把 {@code root} 树平铺为行再按 v3 写出。Flattens the {@code root} tree
     * in DFS preorder into rows and writes them as v3.
     */
    public static byte[] writeTree(int flags, TdTable root) {
        return write(flags, flatten(root));
    }

    /**
     * 以 DFS 先序把 {@code root} 树平铺为行再按 v2 写出（v2 兼容路径）。
     * Flattens the {@code root} tree in DFS preorder into rows and writes them as v2
     * (compatibility path).
     */
    public static byte[] writeTreeV2(int flags, TdTable root) {
        return writeV2(flags, flatten(root));
    }

    /**
     * 以 DFS 先序把 {@code root} 树平铺为行再按带索引 v3 写出（p.2.3.7）。
     * Flattens the {@code root} tree in DFS preorder into rows and writes them as an
     * index-footered v3 document (p.2.3.7).
     */
    public static byte[] writeTreeIndexed(int flags, TdTable root) {
        return writeIndexed(flags, flatten(root));
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