package io.toterra.subterra.engine.save.migrate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * 最小通用 NBT 二进制解析器（p.2.3.2）。NBT 是通用二进制格式，纯 JDK 即可解析，不碰 MC 类。支持
 * gzip 检测 / zlib / 未压缩三种载荷头；tag 类型 1..12（BYTE/SHORT/INT/LONG/FLOAT/DOUBLE/BYTE_ARRAY/
 * STRING/LIST/COMPOUND/INT_ARRAY/LONG_ARRAY）；输出为 {@link NbtNode} 自定义树。含 128 层深度限制
 * （防恶意深递归）与顶层解析后的剩余字节校验（根部消费后还有剩余字节即判非法）。
 * <p>
 * Minimal generic NBT binary parser (p.2.3.2). NBT is a generic binary format and is parsed in pure
 * JDK without touching any Minecraft class. Supports gzip / zlib / uncompressed payload headers;
 * tag types 1..12 (BYTE/SHORT/INT/LONG/FLOAT/DOUBLE/BYTE_ARRAY/STRING/LIST/COMPOUND/INT_ARRAY/
 * LONG_ARRAY); output is a custom {@link NbtNode} tree. Enforces a 128-level depth limit (against
 * hostile deep recursion) and a trailing-bytes check (leftover bytes after the root are illegal).
 */
public final class NbtTreeReader {

    public static final byte TAG_END = 0;
    public static final byte TAG_BYTE = 1;
    public static final byte TAG_SHORT = 2;
    public static final byte TAG_INT = 3;
    public static final byte TAG_LONG = 4;
    public static final byte TAG_FLOAT = 5;
    public static final byte TAG_DOUBLE = 6;
    public static final byte TAG_BYTE_ARRAY = 7;
    public static final byte TAG_STRING = 8;
    public static final byte TAG_LIST = 9;
    public static final byte TAG_COMPOUND = 10;
    public static final byte TAG_INT_ARRAY = 11;
    public static final byte TAG_LONG_ARRAY = 12;

    /** 最大嵌套深度 / maximum nesting depth (against deep recursion). */
    public static final int MAX_DEPTH = 128;

    private NbtTreeReader() {
    }

    /**
     * 解析压缩或未压缩的 NBT 载荷为根 {@link NbtNode}。头部探测：{@code 0x1f 0x8b}→gzip，
     * {@code 0x78}→zlib，否则当未压缩。根必须是具名 tag；根之后仍有字节则抛
     * {@link IllegalArgumentException}。Parses a compressed or uncompressed NBT payload into the root
     * {@link NbtNode}. Header probing: {@code 0x1f 0x8b}→gzip, {@code 0x78}→zlib, else raw. The root
     * must be a named tag; leftover bytes after the root raise {@link IllegalArgumentException}.
     */
    public static NbtNode readCompressed(byte[] data) {
        if (data == null || data.length < 2) {
            throw new IllegalArgumentException("nbt buffer too short: "
                    + (data == null ? "null" : data.length));
        }
        byte[] raw;
        int b0 = data[0] & 0xFF;
        int b1 = data[1] & 0xFF;
        if (b0 == 0x1f && b1 == 0x8b) {
            raw = inflate(data, true);
        } else if (b0 == 0x78) {
            raw = inflate(data, false);
        } else {
            raw = data;
        }
        Parser p = new Parser(raw);
        NbtNode root = p.readRoot();
        if (p.remaining() != 0) {
            throw new IllegalArgumentException("nbt root does not consume the buffer ("
                    + p.remaining() + " trailing bytes)");
        }
        return root;
    }

    private static byte[] inflate(byte[] data, boolean gzip) {
        try {
            InputStream is = gzip
                    ? new GZIPInputStream(new ByteArrayInputStream(data))
                    : new InflaterInputStream(new ByteArrayInputStream(data));
            ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 4);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            is.close();
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("nbt decompression failed: " + e);
        }
    }

    /** 游标解析器 / cursor-based parser over the raw bytes. */
    private static final class Parser {
        private final byte[] b;
        private int pos;

        Parser(byte[] b) {
            this.b = b;
        }

        int remaining() {
            return b.length - pos;
        }

        NbtNode readRoot() {
            if (pos + 1 > b.length) {
                throw new IllegalArgumentException("nbt truncated root tag");
            }
            byte type = b[pos++];
            if (type == TAG_END || type < 1) {
                return null; // empty root (no named compound)
            }
            readName(); // root name is usually empty for level.dat
            return readPayload(type, 0);
        }

        private String readName() {
            int len = readU16();
            if (pos + len > b.length) {
                throw new IllegalArgumentException("nbt name overruns buffer");
            }
            String s = new String(b, pos, len, StandardCharsets.UTF_8);
            pos += len;
            return s;
        }

        private NbtNode readPayload(byte type, int depth) {
            if (depth > MAX_DEPTH) {
                throw new IllegalArgumentException("nbt exceeds max depth " + MAX_DEPTH);
            }
            switch (type) {
                case TAG_BYTE:
                    return new NbtNode(type, (byte) readI8());
                case TAG_SHORT:
                    return new NbtNode(type, (short) readU16());
                case TAG_INT:
                    return new NbtNode(type, readI32());
                case TAG_LONG:
                    return new NbtNode(type, readI64());
                case TAG_FLOAT:
                    return new NbtNode(type, Float.intBitsToFloat(readI32()));
                case TAG_DOUBLE:
                    return new NbtNode(type, Double.longBitsToDouble(readI64()));
                case TAG_BYTE_ARRAY: {
                    int len = readI32();
                    if (len < 0 || pos + len > b.length) {
                        throw new IllegalArgumentException("nbt byte-array overruns buffer (" + len + ")");
                    }
                    byte[] arr = new byte[len];
                    System.arraycopy(b, pos, arr, 0, len);
                    pos += len;
                    return new NbtNode(type, arr);
                }
                case TAG_STRING: {
                    int len = readU16();
                    if (pos + len > b.length) {
                        throw new IllegalArgumentException("nbt string overruns buffer");
                    }
                    String s = new String(b, pos, len, StandardCharsets.UTF_8);
                    pos += len;
                    return new NbtNode(type, s);
                }
                case TAG_LIST: {
                    byte elem = b[pos++];
                    int len = readI32();
                    if (len < 0) {
                        throw new IllegalArgumentException("nbt list negative length");
                    }
                    List<NbtNode> list = new ArrayList<>(Math.min(len, 1024));
                    for (int i = 0; i < len; i++) {
                        list.add(readPayload(elem, depth + 1));
                    }
                    return new NbtNode(type, list);
                }
                case TAG_COMPOUND: {
                    Map<String, NbtNode> map = new LinkedHashMap<>();
                    while (true) {
                        if (pos + 1 > b.length) {
                            throw new IllegalArgumentException("nbt compound unterminated");
                        }
                        byte inner = b[pos++];
                        if (inner == TAG_END) {
                            break;
                        }
                        String k = readName();
                        NbtNode v = readPayload(inner, depth + 1);
                        map.putIfAbsent(k, v); // first occurrence wins (mirrors TdTable)
                    }
                    return new NbtNode(type, map);
                }
                case TAG_INT_ARRAY: {
                    int len = readI32();
                    if (len < 0 || pos + (long) len * 4 > b.length) {
                        throw new IllegalArgumentException("nbt int-array overruns buffer (" + len + ")");
                    }
                    int[] arr = new int[len];
                    for (int i = 0; i < len; i++) {
                        arr[i] = readI32();
                    }
                    return new NbtNode(type, arr);
                }
                case TAG_LONG_ARRAY: {
                    int len = readI32();
                    if (len < 0 || pos + (long) len * 8 > b.length) {
                        throw new IllegalArgumentException("nbt long-array overruns buffer (" + len + ")");
                    }
                    long[] arr = new long[len];
                    for (int i = 0; i < len; i++) {
                        arr[i] = readI64();
                    }
                    return new NbtNode(type, arr);
                }
                default:
                    throw new IllegalArgumentException("unsupported nbt tag type " + type);
            }
        }

        private int readI8() {
            if (pos + 1 > b.length) {
                throw new IllegalArgumentException("nbt truncated");
            }
            return b[pos++];
        }

        private int readU16() {
            if (pos + 2 > b.length) {
                throw new IllegalArgumentException("nbt truncated");
            }
            int v = ((b[pos] & 0xFF) << 8) | (b[pos + 1] & 0xFF);
            pos += 2;
            return v;
        }

        private int readI32() {
            if (pos + 4 > b.length) {
                throw new IllegalArgumentException("nbt truncated");
            }
            int v = ((b[pos] & 0xFF) << 24) | ((b[pos + 1] & 0xFF) << 16)
                    | ((b[pos + 2] & 0xFF) << 8) | (b[pos + 3] & 0xFF);
            pos += 4;
            return v;
        }

        private long readI64() {
            if (pos + 8 > b.length) {
                throw new IllegalArgumentException("nbt truncated");
            }
            long v = 0;
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | (b[pos + i] & 0xFFL);
            }
            pos += 8;
            return v;
        }
    }
}