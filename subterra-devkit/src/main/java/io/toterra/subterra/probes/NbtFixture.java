package io.toterra.subterra.probes;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

/**
 * p.2.3 迁移探针共享的最小 NBT 写入助手（纯 JDK，无 MC 运行时）。探针侧自造夹具用；
 * engine 只读 NBT、从不写。p.2.3.2（SaveMigrateProbe）把 level.dat 夹具写入助手内嵌私有，
 * p.2.3.3 抽取为共同类（read-only 重构，既有断言结果不变）并为玩家夹具增加
 * {@code LIST DOUBLE}/{@code LIST FLOAT}/{@code INT_ARRAY} 支持。支持 tag：
 * BYTE 1 / SHORT 2 / INT 3 / LONG 4 / FLOAT 5 / DOUBLE 6 / STRING 8 / LIST 9 / COMPOUND 10 /
 * INT_ARRAY 11。注意 LIST 元素不含名字。
 * <p>
 * Shared minimal NBT writer helper for the p.2.3 migration probes (pure JDK, no Minecraft runtime).
 * The probes build their own fixtures; the engine only reads NBT and never writes. p.2.3.2's
 * (SaveMigrateProbe) level.dat writer lived as private probe-local code; p.2.3.3 lifts it into this
 * shared class (read-only refactor, existing assertions unchanged) and adds
 * {@code LIST DOUBLE}/{@code LIST FLOAT}/{@code INT_ARRAY} support for the player fixtures.
 * Supported tags: BYTE 1 / SHORT 2 / INT 3 / LONG 4 / FLOAT 5 / DOUBLE 6 / STRING 8 / LIST 9 /
 * COMPOUND 10 / INT_ARRAY 11. LIST elements carry no names.
 */
public final class NbtFixture {

    private NbtFixture() {
    }

    /** gzip 压缩 raw NBT（供 readCompressed 的 gzip 探测路径）。 */
    public static byte[] gzipped(byte[] raw) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(raw);
        }
        return out.toByteArray();
    }

    /** 根：空名具名 COMPOUND（与 level.dat / player .dat 惯例一致）后接 payload + 根 TAG_End. */
    public static byte[] root(byte[] namedCompound) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(10);                 // root type COMPOUND
        o.write(0);
        o.write(0);                  // empty root name
        o.writeBytes(namedCompound); // includes children + trailing TAG_End
        o.write(0);                  // TAG_End for root itself
        return o.toByteArray();
    }

    /** STRING (tag 8). */
    public static byte[] wString(String name, String val) {
        byte[] nb = utf8(name);
        byte[] vb = utf8(val);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(8);
        writeName(o, nb);
        writeU16(o, vb.length);
        o.writeBytes(vb);
        return o.toByteArray();
    }

    /** LONG (tag 4). */
    public static byte[] wLong(String name, long val) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(4);
        writeName(o, utf8(name));
        writeI64(o, val);
        return o.toByteArray();
    }

    /** INT (tag 3). */
    public static byte[] wInt(String name, int val) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(3);
        writeName(o, utf8(name));
        writeI32(o, val);
        return o.toByteArray();
    }

    /** BYTE (tag 1). */
    public static byte[] wByte(String name, int val) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(1);
        writeName(o, utf8(name));
        o.write(val & 0xFF);
        return o.toByteArray();
    }

    /** FLOAT (tag 5). */
    public static byte[] wFloat(String name, float val) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(5);
        writeName(o, utf8(name));
        writeI32(o, Float.floatToIntBits(val));
        return o.toByteArray();
    }

    /** DOUBLE (tag 6). */
    public static byte[] wDouble(String name, double val) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(6);
        writeName(o, utf8(name));
        writeI64(o, Double.doubleToLongBits(val));
        return o.toByteArray();
    }

    /** COMPOUND (tag 10)；{@code kids} 每个为「type+name+payload」，其后统一加一个 TAG_End. */
    public static byte[] wCompound(String name, byte[][] kids) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(10);
        writeName(o, utf8(name));
        for (byte[] k : kids) {
            o.writeBytes(k);
        }
        o.write(0); // TAG_End
        return o.toByteArray();
    }

    /** LIST (tag 9)：单一元素类型 {@code elemTag} + 元素个数 + 各元素 payload（无名字）。 */
    public static byte[] wList(String name, int elemTag, byte[][] payloadElements) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(9);
        writeName(o, utf8(name));
        o.write(elemTag);
        writeI32(o, payloadElements.length);
        for (byte[] e : payloadElements) {
            o.writeBytes(e);
        }
        return o.toByteArray();
    }

    /** {@code Pos} 风格 LIST DOUBLE×3 / a LIST DOUBLE (e.g. {@code Pos}). */
    public static byte[] wListDoubles(String name, double[] vals) {
        byte[][] elems = new byte[vals.length][];
        for (int i = 0; i < vals.length; i++) {
            ByteArrayOutputStream e = new ByteArrayOutputStream();
            writeI64(e, Double.doubleToLongBits(vals[i]));
            elems[i] = e.toByteArray();
        }
        return wList(name, 6, elems);
    }

    /** {@code Rotation} 风格 LIST FLOAT / a LIST FLOAT (e.g. {@code Rotation}). */
    public static byte[] wListFloats(String name, float[] vals) {
        byte[][] elems = new byte[vals.length][];
        for (int i = 0; i < vals.length; i++) {
            ByteArrayOutputStream e = new ByteArrayOutputStream();
            writeI32(e, Float.floatToIntBits(vals[i]));
            elems[i] = e.toByteArray();
        }
        return wList(name, 5, elems);
    }

    /** INT_ARRAY (tag 11)，如玩家 {@code UUID}. */
    public static byte[] wIntArray(String name, int[] vals) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(11);
        writeName(o, utf8(name));
        writeI32(o, vals.length);
        for (int v : vals) {
            writeI32(o, v);
        }
        return o.toByteArray();
    }

    // ---- primitive writers ------------------------------------------------------

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static void writeName(ByteArrayOutputStream o, byte[] nb) {
        writeU16(o, nb.length);
        o.writeBytes(nb);
    }

    private static void writeU16(ByteArrayOutputStream o, int v) {
        o.write((v >> 8) & 0xFF);
        o.write(v & 0xFF);
    }

    private static void writeI32(ByteArrayOutputStream o, int v) {
        o.write((v >> 24) & 0xFF);
        o.write((v >> 16) & 0xFF);
        o.write((v >> 8) & 0xFF);
        o.write(v & 0xFF);
    }

    private static void writeI64(ByteArrayOutputStream o, long v) {
        for (int i = 7; i >= 0; i--) {
            o.write((int) ((v >> (i * 8)) & 0xFF));
        }
    }
}