package io.toterra.subterra.engine.p2p;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Random;

/**
 * 节点可寻址标识（p.2.5.2）：固定 16 字节（128 bit）内容 + 确定性编码。
 * NodeId —— a fixed 16-byte (128-bit) addressable node identifier with a deterministic
 * encoding ({@code of}/{@code fromHex}/{@code random}/{@code bytes}/{@code hex}).
 * <p>
 * 不变式 / Invariants: exactly 16 bytes; canonical hex is 32 lowercase hex chars with no
 * 0x prefix. Value semantics via {@code equals}/{@code hashCode}/{@code toString}.
 * <p>
 * 纯 JDK，无 MC 依赖（engine 层铁律）。Pure JDK — no Minecraft coupling.
 */
public final class NodeId {

    /** 固定字节长。Fixed byte length (128 bit). */
    public static final int BYTES = 16;

    private static final HexFormat HEX = HexFormat.of();
    private final byte[] id;

    private NodeId(byte[] id) {
        this.id = id;
    }

    /** 由恰好 16 字节构建；长度错则抛。Builds from exactly 16 bytes; throws otherwise. */
    public static NodeId of(byte[] id) {
        if (id == null || id.length != BYTES) {
            throw new IllegalArgumentException("NodeId requires exactly " + BYTES + " bytes, got "
                    + (id == null ? "null" : id.length));
        }
        return new NodeId(id.clone());
    }

    /**
     * 由规范 32 位小写 hex 构建；拒绝 0x 前缀、非法字符与非 32 长度。Builds from canonical
     * 32 lowercase hex chars; rejects a 0x prefix, illegal chars, and any length != 32.
     */
    public static NodeId fromHex(String hex) {
        if (hex == null || hex.length() != BYTES * 2) {
            throw new IllegalArgumentException("NodeId hex must be exactly " + (BYTES * 2)
                    + " chars without a 0x prefix, got " + (hex == null ? "null" : "len " + hex.length()));
        }
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            boolean hexDigit = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!hexDigit) {
                throw new IllegalArgumentException("NodeId hex contains illegal char '" + c + "'");
            }
        }
        return new NodeId(HEX.parseHex(hex));
    }

    /** 由固定种子可复现地生成随机节点。Reproducibly generates a random node from a seeded Random. */
    public static NodeId random(Random r) {
        byte[] b = new byte[BYTES];
        r.nextBytes(b);
        return new NodeId(b);
    }

    /** 内容副本（防御性拷贝）。Copy of the underlying bytes. */
    public byte[] bytes() {
        return id.clone();
    }

    /** 规范 32 位小写 hex。Canonical 32-lowercase-hex string. */
    public String hex() {
        return HEX.formatHex(id);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof NodeId other && Arrays.equals(id, other.id));
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(id);
    }

    @Override
    public String toString() {
        return "NodeId(" + hex() + ")";
    }
}