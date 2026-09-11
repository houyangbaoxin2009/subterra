package io.toterra.subterra.engine.render.lod;

import java.util.Objects;

/**
 * A horizontal merged quad of the LOD system (p.2.28.1, clean-room self-developed): a
 * power-of-two {@code size} ({@code 1..chunkSpan}) merged cell anchored at the quantized
 * in-chunk origin {@code originX}/{@code originZ}, with a quantized int16 {@code yTop}
 * block bound, a {@code colorIndex} and a compact {@code facingBits} bitmask. The field
 * order is fixed and {@link #toBytes()}/{@link #fromBytes(byte[])} are byte-identical
 * round-trips (fixed-width, no strings). {@link #canonicalText()} is byte-stable for the
 * same quad.
 *
 * <p>LOD 系统水平合并 quad（p.2.28.1，clean-room 自研）：一个锚定在量化区块内原点 {@code originX}/
 * {@code originZ}、尺寸为 2 的幂（{@code 1..chunkSpan}）的合并单元，携带量化 int16 {@code yTop} 方块
 * 上界、{@code colorIndex} 与紧凑 {@code facingBits} 位掩码。字段序固定，{@link #toBytes()}/
 * {@link #fromBytes(byte[])} 为逐字节一致往返（定宽、无字符串）。{@link #canonicalText()} 对同一 quad
 * 逐字节稳定。
 */
public final class LodQuad {

    /** Fixed byte size of the wire form. / 线缆形态固定字节大小。 */
    static final int BYTE_SIZE = 2 + 2 + 1 + 2 + 1 + 1; // originX, originZ, size, yTop, colorIndex, facingBits

    private final int originX;
    private final int originZ;
    private final int size;
    private final int yTop;
    private final int colorIndex;
    private final int facingBits;

    /**
     * @throws IllegalArgumentException if {@code originX}/{@code originZ} fall outside
     *     {@code [0, 32767]}, {@code size} is not in {@code [1, 32768]}, {@code yTop} is out
     *     of int16 range, {@code colorIndex} is outside {@code [0, 255]}, or
     *     {@code facingBits} outside {@code [0, 15]}.
     */
    public LodQuad(int originX, int originZ, int size, int yTop, int colorIndex, int facingBits) {
        if (originX < 0 || originX > 0x7FFF) {
            throw new IllegalArgumentException("quad originX must be in [0, 32767] but was " + originX);
        }
        if (originZ < 0 || originZ > 0x7FFF) {
            throw new IllegalArgumentException("quad originZ must be in [0, 32767] but was " + originZ);
        }
        if (size < 1 || size > 0x8000) {
            throw new IllegalArgumentException("quad size must be in [1, 32768] but was " + size);
        }
        if (yTop < Short.MIN_VALUE || yTop > Short.MAX_VALUE) {
            throw new IllegalArgumentException("quad yTop out of int16 range: " + yTop);
        }
        if (colorIndex < 0 || colorIndex > 255) {
            throw new IllegalArgumentException("quad colorIndex must be in [0, 255] but was " + colorIndex);
        }
        if (facingBits < 0 || facingBits > 15) {
            throw new IllegalArgumentException("quad facingBits must be in [0, 15] but was " + facingBits);
        }
        this.originX = originX;
        this.originZ = originZ;
        this.size = size;
        this.yTop = yTop;
        this.colorIndex = colorIndex;
        this.facingBits = facingBits;
    }

    /** Quantized in-chunk origin x. / 量化区块内原点 x。 */
    public int originX() {
        return originX;
    }

    /** Quantized in-chunk origin z. / 量化区块内原点 z。 */
    public int originZ() {
        return originZ;
    }

    /** Power-of-two merged cell size ({@code 1..chunkSpan}). / 2 的幂合并单元尺寸（{@code 1..chunkSpan}）。 */
    public int size() {
        return size;
    }

    /** Quantized top block bound (int16). / 量化顶部方块上界（int16）。 */
    public int yTop() {
        return yTop;
    }

    /** Palette color index. / 调色板颜色索引。 */
    public int colorIndex() {
        return colorIndex;
    }

    /** Compact facing/occlusion bitmask (4 low bits). / 紧凑朝向/遮挡位掩码（低 4 位）。 */
    public int facingBits() {
        return facingBits;
    }

    /**
     * Fixed 9-byte encoding, fixed field order: {@code originX} short, {@code originZ} short,
     * {@code size} byte, {@code yTop} short, {@code colorIndex} byte, {@code facingBits} byte.
     * <p>9 字节固定编码，固定字段序：{@code originX} short、{@code originZ} short、{@code size} byte、
     * {@code yTop} short、{@code colorIndex} byte、{@code facingBits} byte。
     */
    public byte[] toBytes() {
        byte[] out = new byte[BYTE_SIZE];
        int i = 0;
        out[i++] = (byte) (originX >> 8);
        out[i++] = (byte) originX;
        out[i++] = (byte) (originZ >> 8);
        out[i++] = (byte) originZ;
        out[i++] = (byte) size;
        out[i++] = (byte) (yTop >> 8);
        out[i++] = (byte) yTop;
        out[i++] = (byte) colorIndex;
        out[i] = (byte) facingBits;
        return out;
    }

    /**
     * Parses the fixed 9-byte encoding; strict mirror of {@link #toBytes()}.
     * / 解析固定 9 字节编码；严格镜像 {@link #toBytes()}。
     * @throws IllegalArgumentException if the input is not exactly {@value #BYTE_SIZE} bytes.
     */
    public static LodQuad fromBytes(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        if (data.length != BYTE_SIZE) {
            throw new IllegalArgumentException("quad must be " + BYTE_SIZE
                    + " bytes but was " + data.length);
        }
        int i = 0;
        int ox = readShort(data, i);
        i += 2;
        int oz = readShort(data, i);
        i += 2;
        int sz = data[i] & 0xFF;
        i += 1;
        int yt = readShort(data, i);
        i += 2;
        int c = data[i] & 0xFF;
        i += 1;
        int fb = data[i] & 0xFF;
        return new LodQuad(ox, oz, sz, yt, c, fb);
    }

    /** Canonical fixed-order text. Byte-identical for the same quad. / 规范固定序文本。对同一 quad 逐字节一致。 */
    public String canonicalText() {
        return "quad originX=" + originX + " originZ=" + originZ + " size=" + size
                + " yTop=" + yTop + " colorIndex=" + colorIndex + " facingBits=" + facingBits;
    }

    private static int readShort(byte[] d, int i) {
        return (short) (((d[i] & 0xFF) << 8) | (d[i + 1] & 0xFF));
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof LodQuad q && originX == q.originX && originZ == q.originZ
                && size == q.size && yTop == q.yTop && colorIndex == q.colorIndex
                && facingBits == q.facingBits);
    }

    @Override
    public int hashCode() {
        int h = 31 * (31 * originX + originZ) + size;
        h = 31 * h + yTop;
        h = 31 * h + colorIndex;
        return 31 * h + facingBits;
    }

    @Override
    public String toString() {
        return canonicalText();
    }
}