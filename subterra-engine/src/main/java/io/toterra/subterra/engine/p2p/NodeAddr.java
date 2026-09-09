package io.toterra.subterra.engine.p2p;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import io.toterra.subterra.engine.zd.ZdPrimitives;

/**
 * 节点可寻址地址判别联合（p.2.5.2）：直连地址 {@link Direct}（公网 / 打洞后的内网端点）
 * 或 relay 地址 {@link ViaRelay}。可序列化为字节串并解析回来，全部手工编码（无外部库）。
 * <p>
 * Discriminated union of node addresses: a direct endpoint ({@link Direct}, public or
 * post-hole-punch private LAN semantics) or a relayed route ({@link ViaRelay}). Serialises
 * to bytes and parses back with hand-rolled encoding only (no external dependency).
 * <p>
 * Wire format (big-endian + protobuf varint from {@link ZdPrimitives}):
 * <pre>
 *   direct   : tag=0x00 | varint(hostLen) | host UTF-8 | varint(port)
 *   viaRelay : tag=0x01 | NodeId bytes (16)            | varint(tokenLen) | token UTF-8
 * </pre>
 */
public sealed interface NodeAddr permits NodeAddr.Direct, NodeAddr.ViaRelay {

    /** 编码变体标签。Variant tags. */
    byte TAG_DIRECT = 0x00;
    byte TAG_RELAY = 0x01;

    /** 是否为直连地址。Whether this is a direct address. */
    boolean isDirect();

    /** 是否为 relay 地址。Whether this is a relayed address. */
    boolean isRelay();

    /** 若为直连地址则当作 {@link Direct}。Casts to {@link Direct} if direct. */
    default Direct asDirect() {
        if (this instanceof Direct d) {
            return d;
        }
        throw new IllegalStateException("not a Direct address: " + this);
    }

    /** 若为 relay 地址则当作 {@link ViaRelay}。Casts to {@link ViaRelay} if relayed. */
    default ViaRelay asRelay() {
        if (this instanceof ViaRelay r) {
            return r;
        }
        throw new IllegalStateException("not a ViaRelay address: " + this);
    }

    /** 序列化为字节串（往返确定性）。Serialises to bytes (lossless round-trip). */
    byte[] toByteArray();

    /** 解析字节串；非法 / 截断抛 {@link IllegalArgumentException}。Parses bytes; throws on bad input. */
    static NodeAddr fromByteArray(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("NodeAddr bytes must not be null");
        }
        return parse(data, 0);
    }

    static NodeAddr parse(byte[] data, int off) {
        if (off >= data.length) {
            throw new IllegalArgumentException("NodeAddr too short: no tag byte");
        }
        int tag = data[off] & 0xFF;
        int p = off + 1;
        if (tag == TAG_DIRECT) {
            String host = readVarintString(data, p);
            p += varintStringLength(data, p);
            if (p >= data.length) {
                throw new IllegalArgumentException("NodeAddr truncDirect: missing port");
            }
            int[] pos = {p};
            long port = ZdPrimitives.readVarint(data, pos);
            if (port < 0 || port > 0xFFFF) {
                throw new IllegalArgumentException("NodeAddr direct port out of range: " + port);
            }
            return new NodeAddr.Direct(host, (int) port);
        }
        if (tag == TAG_RELAY) {
            if (data.length - p < NodeId.BYTES) {
                throw new IllegalArgumentException("NodeAddr truncRelay: missing NodeId bytes");
            }
            byte[] idBytes = new byte[NodeId.BYTES];
            System.arraycopy(data, p, idBytes, 0, NodeId.BYTES);
            p += NodeId.BYTES;
            String token = readVarintString(data, p);
            return new NodeAddr.ViaRelay(NodeId.of(idBytes), token);
        }
        throw new IllegalArgumentException("NodeAddr unknown tag: " + tag);
    }

    /** 解析一个 varint 前缀的 UTF-8 字符串。Reads a varint-prefixed UTF-8 string. */
    private static String readVarintString(byte[] data, int off) {
        int[] pos = {off};
        long len = ZdPrimitives.readVarint(data, pos);
        if (len < 0) {
            throw new IllegalArgumentException("NodeAddr negative string length");
        }
        int end = pos[0] + (int) len;
        if (end > data.length) {
            throw new IllegalArgumentException("NodeAddr string overruns buffer");
        }
        return new String(data, pos[0], (int) len, StandardCharsets.UTF_8);
    }

    /** 返回 &#34;off 起 varint 前缀 + 字符串&#34; 的占用总字节。Computes total bytes of a varint-prefixed string at off. */
    private static int varintStringLength(byte[] data, int off) {
        int[] pos = {off};
        long len = ZdPrimitives.readVarint(data, pos);
        return pos[0] - off + (int) len;
    }

    /** 附加一个 varint 前缀的 UTF-8 字符串。Appends a varint-prefixed UTF-8 string. */
    static void writeVarintString(ByteArrayOutputStream out, String s) {
        byte[] b = ZdPrimitives.utf8(s);
        ZdPrimitives.writeVarint(out, b.length);
        out.writeBytes(b);
    }

    /**
     * 直连地址：host（规范化小写字符串）+ port。
     * Direct endpoint: a canonicalised-lowercase host and a port.
     */
    record Direct(String host, int port) implements NodeAddr {

        public Direct {
            if (host == null) {
                throw new NullPointerException("host");
            }
            if (host.indexOf(':') >= 0) {
                throw new IllegalArgumentException("Direct host must not carry a colon: " + host);
            }
            if (port < 0 || port > 0xFFFF) {
                throw new IllegalArgumentException("Direct port out of range: " + port);
            }
            host = host.toLowerCase(Locale.ROOT); // 规范化小写 canonicalise
        }

        @Override
        public boolean isDirect() {
            return true;
        }

        @Override
        public boolean isRelay() {
            return false;
        }

        @Override
        public byte[] toByteArray() {
            ByteArrayOutputStream out = new ByteArrayOutputStream(32);
            out.write(TAG_DIRECT);
            writeVarintString(out, host);
            ZdPrimitives.writeVarint(out, port);
            return out.toByteArray();
        }

        @Override
        public String toString() {
            return "Direct(" + host + ":" + port + ")";
        }
    }

    /**
     * relay 地址：经某个 relay 节点（按 NodeId 识别）转发，携带一次性 token。
     * Relayed address: routed through a relay node (identified by {@link NodeId}) with a token.
     */
    record ViaRelay(NodeId relayId, String token) implements NodeAddr {

        public ViaRelay {
            if (relayId == null || token == null) {
                throw new NullPointerException("relayId and token must not be null");
            }
        }

        @Override
        public boolean isDirect() {
            return false;
        }

        @Override
        public boolean isRelay() {
            return true;
        }

        @Override
        public byte[] toByteArray() {
            ByteArrayOutputStream out = new ByteArrayOutputStream(16 + token.length());
            out.write(TAG_RELAY);
            out.writeBytes(relayId.bytes());
            writeVarintString(out, token);
            return out.toByteArray();
        }

        @Override
        public String toString() {
            return "ViaRelay(" + relayId.hex() + "/" + token + ")";
        }
    }
}