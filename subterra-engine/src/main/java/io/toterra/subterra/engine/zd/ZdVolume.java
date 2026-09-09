package io.toterra.subterra.engine.zd;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * zd v2 卷读取器（p.2.3.1）：把 zd 字节流解析回 {@link ZdRow} 列表或直接逆推回一棵
 * {@link TdTable} 树。校验 10 字节头；逐字段迭代，对未知 tag 跳过；字段可缺失（缺失给默认
 * 0 / ""）。树逆推按 child_count 建层：第一行为根，每行 child 个后续行是子；具名子 key 非空、
 * 裸子 key 空，具名在前裸元素在后。往返恒等：{@code write(═read(write))} 逐字节；
 * {@code readTree(writeTree(t)) ≍ t} 结构。
 * <p>
 * zd v2 volume reader (p.2.3.1): parses a zd byte stream back into a list of
 * {@link ZdRow} or reconstructs a {@link TdTable} tree directly. Validates the 10-byte
 * header; iterates fields, skipping unknown tags; missing fields fall back to their
 * defaults (0 / ""). Tree reconstruction layers by child_count: the first row is the
 * root, each row's {@code child_count} following rows are its children; named children
 * have a non-empty key, bare children an empty key, named first then bare elements.
 * Round-trips: {@code write(═read(write))} is byte-for-byte; {@code readTree(writeTree(t))}
 * is structurally equivalent to {@code t}.
 */
public final class ZdVolume {

    private static final int TAG_KIND = 10;
    private static final int TAG_KEY = 18;
    private static final int TAG_I64 = 26;
    private static final int TAG_F64 = 34;
    private static final int TAG_STR = 42;
    private static final int TAG_CHILD = 50;

    private ZdVolume() {
    }

    /**
     * 解析 zd 载荷为 {@link ZdRow} 列表。非 zd v2 头抛 {@link IllegalArgumentException}；
     * 字段缺失给默认 0 / ""；未知字段跳过。Parses a zd payload into a list of
     * {@link ZdRow}. A non-zd-v2 header raises {@link IllegalArgumentException}; missing
     * fields default to 0 / ""; unknown fields are skipped.
     */
    public static List<ZdRow> readRows(byte[] data) {
        if (!ZdHeader.isZd(data)) {
            throw new IllegalArgumentException("not a zd v2 payload");
        }
        int[] pos = {10};
        List<ZdRow> rows = new ArrayList<>();
        int kind = 0;
        String key = "";
        long vi = 0L;
        double vf = 0.0;
        String vs = "";
        long child = 0L;
        boolean started = false;
        while (pos[0] < data.length) {
            long tag = ZdPrimitives.readVarint(data, pos);
            long len = ZdPrimitives.readVarint(data, pos);
            if (len < 0 || pos[0] + len > data.length) {
                throw new IllegalArgumentException("zd field overruns buffer (tag " + tag + ", len " + len + ")");
            }
            switch ((int) tag) {
                case TAG_KIND -> {
                    if (started) {
                        rows.add(new ZdRow(kind, key, vi, vf, vs, child));
                    }
                    started = true;
                    kind = (int) ZdPrimitives.decI64(data, pos[0]);
                    key = "";
                    vi = 0L;
                    vf = 0.0;
                    vs = "";
                    child = 0L;
                }
                case TAG_KEY -> key = new String(data, pos[0], (int) len, StandardCharsets.UTF_8);
                case TAG_I64 -> vi = ZdPrimitives.decI64(data, pos[0]);
                case TAG_F64 -> vf = Double.longBitsToDouble(ZdPrimitives.readBe64(data, pos[0]));
                case TAG_STR -> vs = new String(data, pos[0], (int) len, StandardCharsets.UTF_8);
                case TAG_CHILD -> child = ZdPrimitives.readVarintBounded(data, pos[0], (int) len);
                default -> { /* unknown tag: skip payload */ }
            }
            pos[0] += (int) len;
        }
        if (started) {
            rows.add(new ZdRow(kind, key, vi, vf, vs, child));
        }
        return rows;
    }

    /**
     * 逆推 zd 载荷为一棵 {@link TdTable}。根必须是表（kind 0）；否则抛
     * {@link IllegalArgumentException}。Reconstructs a {@link TdTable} from a zd payload.
     * The root must be a table (kind 0), otherwise {@link IllegalArgumentException}.
     */
    public static TdTable readTree(byte[] data) {
        List<ZdRow> rows = readRows(data);
        int[] idx = {0};
        TdValue root = build(rows, idx);
        if (root instanceof TdTable t) {
            return t;
        }
        throw new IllegalArgumentException("zd root is not a table (scalar root)");
    }

    private static TdValue build(List<ZdRow> rows, int[] idx) {
        ZdRow r = rows.get(idx[0]++);
        switch (r.kind()) {
            case 1:
                return TdValue.str(r.valueStr() == null ? "" : r.valueStr());
            case 2:
                return TdValue.of(r.valueI64());
            case 3:
                return TdValue.of(r.valueF64());
            default: {
                int n = (int) r.childCount();
                TdTable.Builder b = TdTable.builder();
                for (int i = 0; i < n; i++) {
                    ZdRow childRow = rows.get(idx[0]); // peek for the child's key
                    TdValue childVal = build(rows, idx);
                    String ck = childRow.key();
                    if (ck != null && !ck.isEmpty()) {
                        b.put(ck, childVal);
                    } else {
                        b.element(childVal);
                    }
                }
                return b.build();
            }
        }
    }
}