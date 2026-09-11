package io.toterra.subterra.engine.render.lod;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A single vertical column stack within a chunk-section (p.2.28.1, clean-room
 * self-developed): it owns a fixed in-chunk column coordinate {@code (x, z)} plus an ordered
 * list of {@link LodProfileEntry} cross-section segments, each carrying a quantized
 * int16 {@code yLow}/{@code yHigh} block range and a {@code colorIndex} small integer. The
 * field order is fixed and every {@link #toBytes()}/{@link #fromBytes(byte[])} encoding is
 * byte-identical to its own round-trip (fixed-width, length-prefixed, no variable-length
 * strings — colour and all fields are fixed width). The entry list is built with a single
 * {@link ArrayList} append pass, so construction is {@code O(n)}, never {@code O(n²)}.
 *
 * <p>区块内单列垂直线（p.2.28.1，clean-room 自研）：拥有一个区块内固定列坐标 {@code (x, z)} 与一列有序的
 * {@link LodProfileEntry} 剖面段，每段携带量化 int16 {@code yLow}/{@code yHigh} 方块范围与 {@code
 * colorIndex} 小整数。字段序固定，每个 {@link #toBytes()}/{@link #fromBytes(byte[])} 编码都与其自身
 * 往返逐字节一致（定宽、长度前缀、禁可变长字符串——颜色等全部定宽）。条目列表以单次 {@link ArrayList} 追加
 * 构建，构造为 {@code O(n)}，绝不 {@code O(n²)}。
 */
public final class LodColumnStack {

    /** Fixed byte size of a single {@link LodProfileEntry}. / 单个 {@link LodProfileEntry} 的固定字节大小。 */
    static final int PROFILE_ENTRY_BYTES = 5;

    /** Fixed byte size of the structural header (x short + z short + count int). / 结构头固定字节大小（x short + z short + count int）。 */
    static final int HEADER_BYTES = 2 + 2 + 4;

    private final int x;
    private final int z;
    private final List<LodProfileEntry> entries;

    /**
     * Builds a column stack with the given in-chunk column coordinate and ordered profile
     * entries. The coordinate must be in {@code [0, 32767]}; entries are defensively copied
     * to an immutable fixed-order list.
     * / 用给定区块内列坐标与有序剖面段构建列柱。坐标须在 {@code [0, 32767]}；条目被防御性复制为不可变固定序
     * 列表。
     *
     * @throws IllegalArgumentException if {@code x}/{@code z} fall outside {@code [0, 32767]}.
     */
    public LodColumnStack(int x, int z, List<LodProfileEntry> entries) {
        if (x < 0 || x > 0x7FFF) {
            throw new IllegalArgumentException("column x must be in [0, 32767] but was " + x);
        }
        if (z < 0 || z > 0x7FFF) {
            throw new IllegalArgumentException("column z must be in [0, 32767] but was " + z);
        }
        this.x = x;
        this.z = z;
        this.entries = List.copyOf(Objects.requireNonNull(entries, "entries must not be null"));
    }

    /** In-chunk column x coordinate. / 区块内列 x 坐标。 */
    public int x() {
        return x;
    }

    /** In-chunk column z coordinate. / 区块内列 z 坐标。 */
    public int z() {
        return z;
    }

    /** Ordered profile entries, in fixed insertion order (unmodifiable). / 有序剖面段，固定插入序（不可变）。 */
    public List<LodProfileEntry> entries() {
        return entries;
    }

    /**
     * Fixed-order byte encoding: { {@code x} short, {@code z} short, {@code count} int, then
     * {@code count} × 5-byte entries }. Length-prefixed, fully fixed-width, no strings.
     * Deterministic; identical input produces identical bytes.
     * / 固定序字节编码：{ {@code x} short、{@code z} short、{@code count} int、后接 {@code count} × 5 字节
     * 条目 }。长度前缀、全定宽、无字符串。确定性；相同输入恒产生相同字节。
     */
    public byte[] toBytes() {
        byte[] out = new byte[HEADER_BYTES + entries.size() * PROFILE_ENTRY_BYTES];
        int i = 0;
        out[i++] = (byte) (x >> 8);
        out[i++] = (byte) x;
        out[i++] = (byte) (z >> 8);
        out[i++] = (byte) z;
        out[i++] = (byte) (entries.size() >> 24);
        out[i++] = (byte) (entries.size() >> 16);
        out[i++] = (byte) (entries.size() >> 8);
        out[i++] = (byte) entries.size();
        for (LodProfileEntry e : entries) {
            i = e.writeInto(out, i);
        }
        return out;
    }

    /**
     * Strictly mirrors {@link #toBytes()}, re-validating the length prefix and range-checks.
     * @return the reconstructed column stack; identical round-trip.
     * / 严格镜像 {@link #toBytes()}，重新校验长度前缀与范围。
     * @return 重建的列柱；往返一致。
     * @throws IllegalArgumentException on truncated/malformed input.
     */
    public static LodColumnStack fromBytes(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        if (data.length < HEADER_BYTES) {
            throw new IllegalArgumentException("lod column data too short: " + data.length + " bytes");
        }
        int i = 0;
        int rx = readShort(data, i);
        i += 2;
        int rz = readShort(data, i);
        i += 2;
        int count = readInt(data, i);
        i += 4;
        if (count < 0) {
            throw new IllegalArgumentException("negative profile entry count: " + count);
        }
        if (data.length != HEADER_BYTES + count * PROFILE_ENTRY_BYTES) {
            throw new IllegalArgumentException("lod column length mismatch: expected "
                    + (HEADER_BYTES + count * PROFILE_ENTRY_BYTES) + " but was " + data.length);
        }
        List<LodProfileEntry> entries = new ArrayList<>(count);
        for (int n = 0; n < count; n++) {
            entries.add(LodProfileEntry.readFrom(data, i));
            i += PROFILE_ENTRY_BYTES;
        }
        return new LodColumnStack(rx, rz, entries);
    }

    /**
     * Canonical fixed-order text: a header line then one line per entry in insertion order.
     * Byte-identical for the same column. / 规范固定序文本：一个头行再按插入序每条目一行。对同一列柱逐字节一致。
     */
    public String canonicalText() {
        StringBuilder sb = new StringBuilder();
        sb.append("column x=").append(x).append(" z=").append(z).append(" entries=").append(entries.size()).append('\n');
        for (LodProfileEntry e : entries) {
            sb.append(e.canonicalText()).append('\n');
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof LodColumnStack that
                && x == that.x && z == that.z && entries.equals(that.entries));
    }

    @Override
    public int hashCode() {
        return 31 * (31 * x + z) + entries.hashCode();
    }

    @Override
    public String toString() {
        return canonicalText();
    }

    private static int readShort(byte[] d, int i) {
        return (short) (((d[i] & 0xFF) << 8) | (d[i + 1] & 0xFF));
    }

    private static int readInt(byte[] d, int i) {
        return ((d[i] & 0xFF) << 24) | ((d[i + 1] & 0xFF) << 16)
                | ((d[i + 2] & 0xFF) << 8) | (d[i + 3] & 0xFF);
    }

    /**
     * A single cross-section profile segment of a column stack (p.2.28.1): a quantized
     * int16 {@code yLow}/{@code yHigh} block range and a {@code colorIndex} small integer.
     * Fixed field order; the wire form is always 5 bytes, so {@link #toBytes()}/{@link
     * #fromBytes(byte[])} are byte-identical round-trips with no variable-length fields.
     * / 列柱的一段剖面段（p.2.28.1）：量化 int16 {@code yLow}/{@code yHigh} 方块范围与 {@code colorIndex}
     * 小整数。字段序固定；线缆形态恒为 5 字节，故 {@link #toBytes()}/{@link #fromBytes(byte[])} 为逐字节
     * 一致往返且无变长字段。
     */
    public static final class LodProfileEntry {

        private final int yLow;
        private final int yHigh;
        private final int colorIndex;

        /**
         * @throws IllegalArgumentException if the range is inverted ({@code yLow > yHigh}),
         *     out of quantized int16 range, or {@code colorIndex} outside {@code [0, 255]}.
         * / 若范围内反（{@code yLow > yHigh}）、超出量化 int16 范围，或 {@code colorIndex} 超出 {@code [0, 255]}，
         * 抛 {@link IllegalArgumentException}。
         */
        public LodProfileEntry(int yLow, int yHigh, int colorIndex) {
            if (yLow < Short.MIN_VALUE || yLow > Short.MAX_VALUE) {
                throw new IllegalArgumentException("yLow out of int16 range: " + yLow);
            }
            if (yHigh < Short.MIN_VALUE || yHigh > Short.MAX_VALUE) {
                throw new IllegalArgumentException("yHigh out of int16 range: " + yHigh);
            }
            if (yLow > yHigh) {
                throw new IllegalArgumentException("yLow must not exceed yHigh: " + yLow + " > " + yHigh);
            }
            if (colorIndex < 0 || colorIndex > 255) {
                throw new IllegalArgumentException("colorIndex must be in [0, 255] but was " + colorIndex);
            }
            this.yLow = yLow;
            this.yHigh = yHigh;
            this.colorIndex = colorIndex;
        }

        /** Quantized lower block bound (int16). / 量化方块下界（int16）。 */
        public int yLow() {
            return yLow;
        }

        /** Quantized upper block bound (int16). / 量化方块上界（int16）。 */
        public int yHigh() {
            return yHigh;
        }

        /** Palette color index (0..255). / 调色板颜色索引（0..255）。 */
        public int colorIndex() {
            return colorIndex;
        }

        /**
         * 5-byte fixed encoding, in fixed field order: {@code yLow} short, {@code yHigh} short,
         * {@code colorIndex} byte. / 固定 5 字节编码，固定字段序：{@code yLow} short、{@code yHigh} short、
         * {@code colorIndex} byte。
         */
        public byte[] toBytes() {
            byte[] out = new byte[PROFILE_ENTRY_BYTES];
            writeInto(out, 0);
            return out;
        }

        /** Writes this entry into {@code out} at offset {@code i}; returns the next offset. /
         *  把本条目写入 {@code out} 的偏移 {@code i} 处；返回下一个偏移。 */
        private int writeInto(byte[] out, int i) {
            out[i++] = (byte) (yLow >> 8);
            out[i++] = (byte) yLow;
            out[i++] = (byte) (yHigh >> 8);
            out[i++] = (byte) yHigh;
            out[i++] = (byte) colorIndex;
            return i;
        }

        /**
         * Parses the 5-byte fixed encoding and reconstructs the entry.
         * / 解析 5 字节固定编码并重建条目。
         * @throws IllegalArgumentException if the segment is malformed or the byte length is not 5.
         */
        public static LodProfileEntry fromBytes(byte[] data) {
            Objects.requireNonNull(data, "data must not be null");
            if (data.length != PROFILE_ENTRY_BYTES) {
                throw new IllegalArgumentException("profile entry must be " + PROFILE_ENTRY_BYTES
                        + " bytes but was " + data.length);
            }
            return readFrom(data, 0);
        }

        private static LodProfileEntry readFrom(byte[] d, int i) {
            int yl = readShort(d, i);
            int yh = readShort(d, i + 2);
            int c = d[i + 4] & 0xFF;
            return new LodProfileEntry(yl, yh, c);
        }

        /** Canonical fixed-order text. / 规范固定序文本。 */
        public String canonicalText() {
            return "profile yLow=" + yLow + " yHigh=" + yHigh + " colorIndex=" + colorIndex;
        }

        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof LodProfileEntry e
                    && yLow == e.yLow && yHigh == e.yHigh && colorIndex == e.colorIndex);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * yLow + yHigh) + colorIndex;
        }

        @Override
        public String toString() {
            return canonicalText();
        }
    }
}