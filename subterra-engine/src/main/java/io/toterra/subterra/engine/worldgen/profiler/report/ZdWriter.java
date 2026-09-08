package io.toterra.subterra.engine.worldgen.profiler.report;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import io.toterra.subterra.api.worldgen.profiler.ProfileReport;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

/**
 * Writes a {@link ProfileReport} as z-file (zd) binary (p.1.8.30). The file is
 * produced from the same {@link ProfileTree} as {@link TdWriter}, so a zd
 * payload always corresponds one-to-one with the td text of the same report.
 * <p>
 * Layout: a 10-byte header {@code "TIEDBZD" + 0x00 0x02 + 0x00} (base-48 version
 * digits stored as raw digit bytes: v2 = digits d1=0, d2=2 -> bytes 0x00 0x02, per the
 * pinned zd v2 header spec), followed by the
 * flattened tree as a sequence of wire2 records. Each tree node is flattened in
 * depth-first preorder (matching {@code tdzd.flatten}: kind 0 = table/array,
 * 1 = string, 2 = integer/bool, 3 = float; bool folds into integer 0/1) and
 * emitted as one record that always carries the six fixed wire2 fields. Field
 * numbers are 1 = kind, 2 = key, 3 = value_i64, 4 = value_f64, 5 = value_str,
 * 6 = child_count, giving tags 10 / 18 / 26 / 34 / 42 / 50. Each field is
 * written as {@code varint tag + varint length + payload}. Payloads: kind and
 * value_i64 use the {@code zdw.enc_i64} compact encoding transcribed below,
 * key/value_str are UTF-8, value_f64 is the IEEE-754 bit pattern as 8 big-endian
 * bytes, child_count is a protobuf varint.
 * <p>
 * 把 {@link ProfileReport} 写为 zd 二进制（p.1.8.30）。文件由与 {@link TdWriter} 相同的
 * 一棵 {@link ProfileTree} 生成，因此 zd 载荷总是与同一报告对应的 td 文本一一对应。
 * <p>
 * 布局：10 字节头 {@code "TIEDBZD" + 0x00 0x02 + 0x00}（base-48 版本数字按原始进制字节
 * 存储：v2 = 数字 d1=0、d2=2 → 字节 0x00 0x02，遵循已定稿的 zd v2 头规范），随后是按深度
 * 优先先序平铺的树，
 * 以一串 wire2 记录呈现。每个树节点按深度优先先序平铺（匹配 {@code tdzd.flatten}：kind
 * 0 = 表/数组，1 = 字符串，2 = 整数/bool，3 = 浮点；bool 归整数 0/1）并以一条记录发出，
 * 记录恒携带固定的六个 wire2 字段。字段号为 1 = kind、2 = key、3 = value_i64、4 =
 * value_f64、5 = value_str、6 = child_count，对应 tag 10 / 18 / 26 / 34 / 42 / 50。每个
 * 字段写为 {@code varint tag + varint 长度 + 载荷}。载荷：kind 与 value_i64 使用下面照抄的
 * {@code zdw.enc_i64} 紧凑编码；key/value_str 为 UTF-8；value_f64 为 IEEE-754 位模式按
 * 8 字节大端；child_count 为 protobuf varint。
 * <p>
 * <b>Deviation notice:</b> the reference {@code compiler/tdzd.tie} packs whole
 * columns as six aggregate array fields plus per-value zd type markers. This
 * implementation instead follows design document section 8's "fixed six fields"
 * row-per-node record (no per-scalar type markers, f64 field is a bare 8-byte
 * bit pattern), and it is therefore the vector anchor that the downstream probe
 * checks against.
 * <p>
 * <b>偏离说明：</b>参考 {@code compiler/tdzd.tie} 把整列打包成六个聚合数组字段并带逐值类型
 * 标记。本实现改为遵循设计文档第 8 节的"固定六字段"逐节点记录（无逐标量的类型标记，f64
 * 字段为裸 8 字节位模式），因此本实现是下游探针校验其向量的锚点。
 */
public final class ZdWriter {

    private ZdWriter() {
    }

    /**
     * Produces the full zd byte stream (header + flattened records) for a report.
     * <p>
     * 为一个报告生成完整的 zd 字节流（头部 + 平铺记录）。
     *
     * @param r the completed report.
     * @return the zd serialization.
     */
    public static byte[] write(ProfileReport r) {
        TdTable root = ProfileTree.toTd(r);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeHeader(out);
        flatten(out, root);
        return out.toByteArray();
    }

    /** 10-byte header {@code TIEDBZD} + base-48 version digits 0x00 0x02 + flags 0. */
    private static void writeHeader(ByteArrayOutputStream out) {
        byte[] magic = "TIEDBZD".getBytes(StandardCharsets.US_ASCII);
        out.write(magic, 0, magic.length);
        out.write(0x00);
        out.write(0x02);
        out.write(0x00);
    }

    /**
     * Flattens the tree in DFS preorder with an explicit stack, writing one
     * six-field record per node. Children are visited in td serialization order:
     * all named entries (in {@code keys()} order) then all bare array elements.
     * <p>
     * 用显式栈按 DFS 先序平铺树，每个节点写一条六字段记录。子节点按 td 序列化顺序访问：
     * 先是全部具名条目（按 {@code keys()} 顺序），随后是全部裸数组元素。
     */
    private static void flatten(ByteArrayOutputStream out, TdTable root) {
        Deque<TdValue> nodes = new ArrayDeque<>();
        Deque<String> keys = new ArrayDeque<>();
        nodes.push(root);
        keys.push("");
        while (!nodes.isEmpty()) {
            TdValue node = nodes.pop();
            String key = keys.pop();
            if (node instanceof TdTable t) {
                emitTable(out, key, t, nodes, keys);
            } else {
                emitScalar(out, key, node);
            }
        }
    }

    private static void emitTable(ByteArrayOutputStream out, String key, TdTable t,
                                  Deque<TdValue> nodes, Deque<String> keys) {
        List<String> named = t.keys();
        List<TdValue> elems = t.elements();
        long child = (long) named.size() + elems.size();
        writeRow(out, 0, key, 0L, 0.0, "", child);
        // Push children in reverse so the first desired child pops first.
        // Desired forward order: named entries then bare elements.
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
    }

    private static void emitScalar(ByteArrayOutputStream out, String key, TdValue v) {
        TdValue.Scalar s = v.scalar();
        switch (s.kind()) {
            case STRING -> writeRow(out, 1, key, 0L, 0.0, s.str(), 0);
            case INT -> writeRow(out, 2, key, s.i(), 0.0, "", 0);
            case FLOAT -> writeRow(out, 3, key, 0L, s.f(), "", 0);
            case BOOL -> writeRow(out, 2, key, s.b() ? 1L : 0L, 0.0, "", 0);
            default -> writeRow(out, 2, key, 0L, 0.0, "", 0);
        }
    }

    /**
     * Writes one node as the fixed six wire2 fields: tags 10 (kind), 18 (key),
     * 26 (value_i64), 34 (value_f64), 42 (value_str), 50 (child_count).
     * <p>
     * 把一个节点写为固定的六个 wire2 字段：tag 10 (kind)、18 (key)、26 (value_i64)、
     * 34 (value_f64)、42 (value_str)、50 (child_count)。
     */
    private static void writeRow(ByteArrayOutputStream out, int kind, String key,
                                 long vi, double vf, String vs, long child) {
        writeField(out, 10, encI64(kind));
        writeField(out, 18, utf8(key));
        writeField(out, 26, encI64(vi));
        writeField(out, 34, be64(Double.doubleToLongBits(vf)));
        writeField(out, 42, utf8(vs));
        writeField(out, 50, varint(child));
    }

    /** Writes one field: varint tag + varint payload length + payload. */
    private static void writeField(ByteArrayOutputStream out, int tag, byte[] payload) {
        writeVarint(out, tag);
        writeVarint(out, payload.length);
        out.write(payload, 0, payload.length);
    }

    /**
     * Protobuf-style base-128 varint. Negative values produce no bytes (matching
     * {@code zdw.varint}); this writer only passes non-negative tag/length/child.
     * <p>
     * Protobuf 风格 base-128 varint。负值不产生字节（与 {@code zdw.varint} 一致）；本写出
     * 只传入非负的 tag/长度/子计数。
     */
    private static byte[] varint(long n) {
        if (n < 0) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(8);
        writeVarint(out, n);
        return out.toByteArray();
    }

    /** Appends a non-negative protobuf varint to the stream. */
    private static void writeVarint(ByteArrayOutputStream out, long n) {
        long v = n;
        while (v >= 0x80) {
            out.write((int) ((v & 0x7F) | 0x80));
            v >>= 7;
        }
        out.write((int) v);
    }

    /** The {@code zdw.enc_i64} compact encoding, transcribed branch for branch. */
    private static byte[] encI64(long n) {
        if (n >= 0 && n <= 127) {
            return new byte[]{(byte) n};
        }
        if (n >= -32 && n < 0) {
            return new byte[]{(byte) (n & 0xFF)};
        }
        if (n >= -128 && n < -32) {
            return new byte[]{(byte) 0xD0, (byte) (n & 0xFF)};
        }
        if (n >= 128 && n <= 255) {
            return new byte[]{(byte) 0xCC, (byte) n};
        }
        if (n >= -32768 && n < -128) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(3);
            out.write(0xD1);
            writeBe16(out, n & 0xFFFF);
            return out.toByteArray();
        }
        if (n >= 256 && n <= 65535) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(3);
            out.write(0xCD);
            writeBe16(out, n);
            return out.toByteArray();
        }
        if (n >= -2147483648L && n < -32768) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(5);
            out.write(0xD2);
            writeBe32(out, n & 0xFFFFFFFFL);
            return out.toByteArray();
        }
        if (n >= 65536 && n <= 4294967295L) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(5);
            out.write(0xCE);
            writeBe32(out, n);
            return out.toByteArray();
        }
        if (n >= 0) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(9);
            out.write(0xCF);
            writeBe64(out, n);
            return out.toByteArray();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(9);
        out.write(0xD3);
        writeBe64(out, n);
        return out.toByteArray();
    }

    /** {@code zdw.enc_f64}: IEEE-754 bit pattern as 8 big-endian bytes. */
    private static byte[] be64(long bits) {
        return new byte[]{
            (byte) (bits >> 56), (byte) (bits >> 48), (byte) (bits >> 40), (byte) (bits >> 32),
            (byte) (bits >> 24), (byte) (bits >> 16), (byte) (bits >> 8), (byte) bits
        };
    }

    private static void writeBe16(ByteArrayOutputStream out, long n) {
        out.write((int) ((n >> 8) & 0xFF));
        out.write((int) (n & 0xFF));
    }

    private static void writeBe32(ByteArrayOutputStream out, long n) {
        out.write((int) ((n >> 24) & 0xFF));
        out.write((int) ((n >> 16) & 0xFF));
        out.write((int) ((n >> 8) & 0xFF));
        out.write((int) (n & 0xFF));
    }

    private static void writeBe64(ByteArrayOutputStream out, long n) {
        for (int i = 7; i >= 0; i--) {
            out.write((int) ((n >> (i * 8)) & 0xFF));
        }
    }

    private static byte[] utf8(String s) {
        if (s == null) {
            return new byte[0];
        }
        return s.getBytes(StandardCharsets.UTF_8);
    }
}