package io.toterra.subterra.engine.zd;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * zd 卷读取器（p.2.3.1，v3 升级 p.2.3.7）：把 zd 字节流解析回 {@link ZdRow} 列表或直接逆推回
 * 一棵 {@link TdTable} 树。<b>v3 读取义务 = v2 + v3</b>：v2 文件（头 "02"，正文从偏移 10 到
 * 文件尾）与无 footer 的 v3 文件同规则读取；带索引 footer 的 v3 文件（尾 25 字节魔数
 * {@code ZD3FT}，见 {@link ZdFooter}）<b>必须</b>经段表定位段类型 0（数据体）读取，头 bit5
 * 与 footer 存在性不一致确定性拒绝。逐字段迭代，对未知 tag 跳过；字段可缺失（缺失给默认
 * 0 / ""）。树逆推按 child_count 建层：第一行为根，每行 child 个后续行是子；具名子 key 非空、
 * 裸子 key 空，具名在前裸元素在后。往返恒等：{@code write(═read(write))} 逐字节；
 * {@code readTree(writeTree(t)) ≍ t} 结构。
 * <p>
 * zd volume reader (p.2.3.1; v3 upgrade p.2.3.7): parses a zd byte stream back into a
 * list of {@link ZdRow} or reconstructs a {@link TdTable} tree directly. <b>The v3
 * reading obligation = v2 + v3:</b> v2 files (header "02", body from offset 10 to EOF)
 * and footerless v3 files read under the same rules; a v3 file with the index footer
 * (tail 25-byte magic {@code ZD3FT}, see {@link ZdFooter}) <b>must</b> be read through
 * the segment table's type-0 (data) segment, and a header-bit5/footer-presence
 * disagreement is deterministically rejected. Iterates fields, skipping unknown tags;
 * missing fields fall back to their defaults (0 / ""). Tree reconstruction layers by
 * child_count: the first row is the root, each row's {@code child_count} following rows
 * are its children; named children have a non-empty key, bare children an empty key,
 * named first then bare elements. Round-trips: {@code write(═read(write))} is
 * byte-for-byte; {@code readTree(writeTree(t))} is structurally equivalent to {@code t}.
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
     * 解析 zd 载荷为 {@link ZdRow} 列表（v2 + v3 读义务）。非 zd v2/v3 头抛
     * {@link IllegalArgumentException}；带 footer 时经段表定位数据体（段类型 0），头 bit5
     * 与 footer 存在性不一致确定性拒绝；字段缺失给默认 0 / ""；未知字段跳过。
     * Parses a zd payload into a list of {@link ZdRow} (v2 + v3 reading obligation).
     * A non-zd-v2/v3 header raises {@link IllegalArgumentException}; a footered payload
     * is read through the segment table's type-0 data segment, and a header-bit5 /
     * footer-presence disagreement is deterministically rejected; missing fields
     * default to 0 / ""; unknown fields are skipped.
     */
    public static List<ZdRow> readRows(byte[] data) {
        if (!ZdHeader.isZd(data)) {
            throw new IllegalArgumentException("not a zd v2/v3 payload");
        }
        int version = ZdHeader.parseVersion(data, 0);
        int headerFlags = ZdHeader.flags(data, 0);
        ZdFooter footer = ZdFooter.probe(data);
        if (footer != null) {
            if (version != ZdHeader.VERSION_V3) {
                throw new IllegalArgumentException("zd footer on a non-v3 payload (version " + version + ")");
            }
            if ((headerFlags & ZdHeader.FLAG_INDEX) == 0) {
                throw new IllegalArgumentException("zd footer present but header bit5 (index) is clear");
            }
            ZdFooter.Segment body = footer.segment(ZdFooter.SEG_DATA);
            if (body == null) {
                throw new IllegalArgumentException("zd footer index has no data segment (type 0)");
            }
            return readRowsRange(data, (int) body.offset(), (int) body.length());
        }
        if (version == ZdHeader.VERSION_V3 && (headerFlags & ZdHeader.FLAG_INDEX) != 0) {
            throw new IllegalArgumentException("zd v3 header declares bit5 (index) but no footer is present");
        }
        return readRowsRange(data, 10, data.length - 10);
    }

    /**
     * 文件尾探测索引 footer：存在返回解析好的 {@link ZdFooter}（crc 与段表已校验），无
     * footer 返回 null。footer 与头的一致性（v3 + bit5 置位）在此确定性拒绝。
     * Tail probe for the index footer: returns the parsed {@link ZdFooter} when present
     * (crc and segment table verified), null when absent. Footer/header consistency
     * (v3 + bit5 set) is deterministically enforced here.
     */
    public static ZdFooter footer(byte[] data) {
        if (!ZdHeader.isZd(data)) {
            throw new IllegalArgumentException("not a zd v2/v3 payload");
        }
        ZdFooter footer = ZdFooter.probe(data);
        if (footer != null) {
            int version = ZdHeader.parseVersion(data, 0);
            int headerFlags = ZdHeader.flags(data, 0);
            if (version != ZdHeader.VERSION_V3) {
                throw new IllegalArgumentException("zd footer on a non-v3 payload (version " + version + ")");
            }
            if ((headerFlags & ZdHeader.FLAG_INDEX) == 0) {
                throw new IllegalArgumentException("zd footer present but header bit5 (index) is clear");
            }
        }
        return footer;
    }

    /** 在 {@code [off, off+len)} 范围内逐字段解析 wire2 行。 / Parses wire2 rows within {@code [off, off+len)}. */
    private static List<ZdRow> readRowsRange(byte[] data, int off, int len) {
        if (off < 0 || len < 0 || off > data.length || len > data.length - off) {
            throw new IllegalArgumentException("zd body range out of bounds (off " + off + ", len " + len + ")");
        }
        int end = off + len;
        int[] pos = {off};
        List<ZdRow> rows = new ArrayList<>();
        int kind = 0;
        String key = "";
        long vi = 0L;
        double vf = 0.0;
        String vs = "";
        long child = 0L;
        boolean started = false;
        while (pos[0] < end) {
            long tag = ZdPrimitives.readVarint(data, pos);
            long flen = ZdPrimitives.readVarint(data, pos);
            if (flen < 0 || pos[0] + flen > end) {
                throw new IllegalArgumentException("zd field overruns buffer (tag " + tag + ", len " + flen + ")");
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
                case TAG_KEY -> key = new String(data, pos[0], (int) flen, StandardCharsets.UTF_8);
                case TAG_I64 -> vi = ZdPrimitives.decI64(data, pos[0]);
                case TAG_F64 -> vf = Double.longBitsToDouble(ZdPrimitives.readBe64(data, pos[0]));
                case TAG_STR -> vs = new String(data, pos[0], (int) flen, StandardCharsets.UTF_8);
                case TAG_CHILD -> child = ZdPrimitives.readVarintBounded(data, pos[0], (int) flen);
                default -> { /* unknown tag: skip payload */ }
            }
            pos[0] += (int) flen;
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