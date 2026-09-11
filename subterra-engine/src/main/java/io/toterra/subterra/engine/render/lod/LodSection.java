package io.toterra.subterra.engine.render.lod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The fixed-order LOD section container (p.2.28.1, clean-room self-developed): a versioned
 * payload linking a {@link LodLevel}, the block-space section origin
 * {@code originBlockX}/{@code originBlockZ}, an ordered list of {@link LodColumnStack}s
 * (sorted by {@code (x, z)} lexicographic order) and an ordered list of {@link LodQuad}s
 * (in a fixed canonical order).
 *
 * <p><b>zd ABI contract:</b> the {@link #toBytes()} form — version header + fixed-order
 * payload (column count + each column's byte sequence, then quad count + each quad's byte
 * sequence) — is the pinned payload ("bytes in &rarr; bytes out") consumed by the p.2.28.2
 * tie hot path over the p.2.1 engine.tie FFM bridge. The field order is deliberate and must
 * never change; any future revision bumps {@link #VERSION}, never permutes fields. Columns
 * and quads are normalized to their canonical fixed order at build time so the same logical
 * section always serializes to the identical byte sequence. Deterministic: no timing, no
 * randomness, no strings.
 *
 * <p>固定序 LOD 区块容器（p.2.28.1，clean-room 自研）：一个带版本号、链接 {@link LodLevel}、方块空间区块原点
 * {@code originBlockX}/{@code originBlockZ}、有序 {@link LodColumnStack} 列表（按 {@code (x, z)}
 * 字典序排序）与有序 {@link LodQuad} 列表（固定规范序）的载荷。
 *
 * <p><b>zd ABI 契约：</b>{@link #toBytes()} 形态——版本头 + 固定序载荷（列计数 + 每列字节序列，再 quad 计数 +
 * 每 quad 字节序列）——是 p.2.28.2 tie 经 p.2.1 engine.tie FFM 桥热点的钉死载荷（「字节进→字节出」）。字段序
 * 为刻意钉死、绝不可改；任何未来修订只 bump {@link #VERSION}，绝不重排字段。列与 quad 在构建时归一化为其规范
 * 固定序，使同一逻辑区块恒序列化为相同的字节序列。确定性：无时序、无随机、无字符串。
 */
public final class LodSection {

    /** Current wire version of the field-order payload. Bump on incompatible change; never permute fields.
     *  / 当前字段序载荷线缆版本。不兼容变更时递增；绝不重排字段。 */
    public static final int VERSION = 1;

    /** Version header (int) + level ordinal (byte) + origin (int ×2). / 版本头（int）+ 层级序（byte）+ 原点（int ×2）。 */
    static final int HEADER_BYTES = 4 + 1 + 4 + 4;

    private final LodLevel level;
    private final int originBlockX;
    private final int originBlockZ;
    private final List<LodColumnStack> columnStacks;
    private final List<LodQuad> quads;

    private LodSection(LodLevel level, int originBlockX, int originBlockZ,
                       List<LodColumnStack> columnStacks, List<LodQuad> quads) {
        this.level = level;
        this.originBlockX = originBlockX;
        this.originBlockZ = originBlockZ;
        this.columnStacks = List.copyOf(columnStacks);
        this.quads = List.copyOf(quads);
    }

    /** The section's detail level. / 区块细节层级。 */
    public LodLevel level() {
        return level;
    }

    /** Section block-space origin x. / 区块方块空间原点 x。 */
    public int originBlockX() {
        return originBlockX;
    }

    /** Section block-space origin z. / 区块方块空间原点 z。 */
    public int originBlockZ() {
        return originBlockZ;
    }

    /** Columns in canonical fixed order (sorted by {@code (x, z)}), unmodifiable. / 规范固定序列（按 {@code (x, z)} 排序），不可变。 */
    public List<LodColumnStack> columnStacks() {
        return columnStacks;
    }

    /** Quads in canonical fixed order, unmodifiable. / 规范固定序 quad，不可变。 */
    public List<LodQuad> quads() {
        return quads;
    }

    /**
     * Fixed-order byte encoding: {@code version} int, {@code levelOrdB} byte,
     * {@code originBlockX} int, {@code originBlockZ} int, {@code columnCount} int, then each
     * column's fixed-order bytes, then {@code quadCount} int, then each quad's 9 bytes.
     * Byte-identical for the same normalized section; this is the pinned zd ABI payload for
     * the p.2.28.2 tie hot path.
     * / 固定序字节编码：{@code version} int、{@code levelOrdB} byte、{@code originBlockX} int、
     * {@code originBlockZ} int、{@code columnCount} int、再每列的固定序字节、{@code quadCount} int、
     * 再每 quad 的 9 字节。对同一规范化区块逐字节一致；此为 p.2.28.2 tie 热点的钉死 zd ABI 载荷。
     */
    public byte[] toBytes() {
        int size = HEADER_BYTES + 4; // 13 header + 4 column-count int
        for (LodColumnStack c : columnStacks) {
            size += LodColumnStack.HEADER_BYTES + c.entries().size() * LodColumnStack.PROFILE_ENTRY_BYTES;
        }
        size += 4 + quads.size() * LodQuad.BYTE_SIZE;
        byte[] out = new byte[size];
        int i = 0;
        out[i++] = (byte) (VERSION >> 24);
        out[i++] = (byte) (VERSION >> 16);
        out[i++] = (byte) (VERSION >> 8);
        out[i++] = (byte) VERSION;
        out[i++] = (byte) level.ordinal();
        out[i++] = (byte) (originBlockX >> 24);
        out[i++] = (byte) (originBlockX >> 16);
        out[i++] = (byte) (originBlockX >> 8);
        out[i++] = (byte) originBlockX;
        out[i++] = (byte) (originBlockZ >> 24);
        out[i++] = (byte) (originBlockZ >> 16);
        out[i++] = (byte) (originBlockZ >> 8);
        out[i++] = (byte) originBlockZ;
        out[i++] = (byte) (columnStacks.size() >> 24);
        out[i++] = (byte) (columnStacks.size() >> 16);
        out[i++] = (byte) (columnStacks.size() >> 8);
        out[i++] = (byte) columnStacks.size();
        for (LodColumnStack c : columnStacks) {
            byte[] cb = c.toBytes();
            System.arraycopy(cb, 0, out, i, cb.length);
            i += cb.length;
        }
        out[i++] = (byte) (quads.size() >> 24);
        out[i++] = (byte) (quads.size() >> 16);
        out[i++] = (byte) (quads.size() >> 8);
        out[i++] = (byte) quads.size();
        for (LodQuad q : quads) {
            byte[] qb = q.toBytes();
            System.arraycopy(qb, 0, out, i, qb.length);
            i += qb.length;
        }
        return out;
    }

    /**
     * Strictly mirrors {@link #toBytes()}: re-validates the version header, level ordinal,
     * counts and lengths, and reconstructs the section. {@link #VERSION} mismatch is rejected.
     * / 严格镜像 {@link #toBytes()}：重新校验版本头、层级序、计数与长度并重建区块。{@link #VERSION} 不一致被拒绝。
     * @throws IllegalArgumentException on malformed input.
     */
    public static LodSection fromBytes(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        if (data.length < HEADER_BYTES) {
            throw new IllegalArgumentException("lod section data too short: " + data.length + " bytes");
        }
        int i = 0;
        int ver = readInt(data, i);
        i += 4;
        if (ver != VERSION) {
            throw new IllegalArgumentException("unsupported lod section version: " + ver);
        }
        int ord = data[i++] & 0xFF;
        if (ord < 0 || ord >= LodLevel.values().length) {
            throw new IllegalArgumentException("invalid lod level ordinal: " + ord);
        }
        LodLevel level = LodLevel.values()[ord];
        int ox = readInt(data, i);
        i += 4;
        int oz = readInt(data, i);
        i += 4;
        int colCount = readInt(data, i);
        i += 4;
        if (colCount < 0) {
            throw new IllegalArgumentException("negative column count: " + colCount);
        }
        List<LodColumnStack> columns = new ArrayList<>(Math.min(colCount, 64));
        for (int n = 0; n < colCount; n++) {
            if (data.length - i < LodColumnStack.HEADER_BYTES) {
                throw new IllegalArgumentException("truncated lod column in section");
            }
            int cLen = LodColumnStack.HEADER_BYTES
                    + readInt(data, i + 4) * LodColumnStack.PROFILE_ENTRY_BYTES;
            if (data.length - i < cLen) {
                throw new IllegalArgumentException("truncated lod column tail in section");
            }
            byte[] cb = new byte[cLen];
            System.arraycopy(data, i, cb, 0, cLen);
            columns.add(LodColumnStack.fromBytes(cb));
            i += cLen;
        }
        int quadCount = readInt(data, i);
        i += 4;
        if (quadCount < 0) {
            throw new IllegalArgumentException("negative quad count: " + quadCount);
        }
        if (data.length - i != quadCount * LodQuad.BYTE_SIZE) {
            throw new IllegalArgumentException("lod section quad tail length mismatch");
        }
        List<LodQuad> quads = new ArrayList<>(Math.min(quadCount, 64));
        for (int n = 0; n < quadCount; n++) {
            byte[] qb = new byte[LodQuad.BYTE_SIZE];
            System.arraycopy(data, i, qb, 0, LodQuad.BYTE_SIZE);
            quads.add(LodQuad.fromBytes(qb));
            i += LodQuad.BYTE_SIZE;
        }
        return new LodSection(level, ox, oz, columns, quads);
    }

    /**
     * Canonical fixed-order text: a header line, one line per column, one line per quad.
     * Byte-identical for the same normalized section.
     * / 规范固定序文本：一个头行、每列一行、每 quad 一行。对同一规范化区块逐字节一致。
     */
    public String canonicalText() {
        StringBuilder sb = new StringBuilder();
        sb.append("section version=").append(VERSION).append(" level=").append(level.form())
                .append(" originBlockX=").append(originBlockX).append(" originBlockZ=").append(originBlockZ)
                .append(" columns=").append(columnStacks.size()).append(" quads=").append(quads.size()).append('\n');
        for (LodColumnStack c : columnStacks) {
            sb.append(c.canonicalText());
        }
        for (LodQuad q : quads) {
            sb.append(q.canonicalText()).append('\n');
        }
        return sb.toString();
    }

    /**
     * Creates a deterministic builder that normalizes columns to {@code (x, z)} order and
     * quads to a fixed canonical order at {@link #build()}.
     * / 创建一个确定性构建器，在 {@link #build()} 时将列归一化到 {@code (x, z)} 序、将 quad 归一化到固定规范序。
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Deterministic builder for {@link LodSection}. / {@link LodSection} 的确定性构建器。 */
    public static final class Builder {

        private LodLevel level = LodLevel.L0;
        private int originBlockX;
        private int originBlockZ;
        private final List<LodColumnStack> columns = new ArrayList<>();
        private final List<LodQuad> quads = new ArrayList<>();

        private Builder() {
        }

        /** Sets the section detail level (default L0). / 设置区块细节层级（缺省 L0）。 */
        public Builder level(LodLevel level) {
            this.level = Objects.requireNonNull(level, "level must not be null");
            return this;
        }

        public Builder originBlockX(int originBlockX) {
            this.originBlockX = originBlockX;
            return this;
        }

        public Builder originBlockZ(int originBlockZ) {
            this.originBlockZ = originBlockZ;
            return this;
        }

        public Builder column(LodColumnStack column) {
            columns.add(Objects.requireNonNull(column, "column must not be null"));
            return this;
        }

        public Builder quad(LodQuad quad) {
            quads.add(Objects.requireNonNull(quad, "quad must not be null"));
            return this;
        }

        /**
         * Builds the immutable section, normalizing columns to {@code (x, z)} lexicographic
         * order and quads to fixed canonical order; duplicated {@code (x, z)} column coordinates
         * are rejected deterministically.
         * / 构建不可变区块，将列归一化到 {@code (x, z)} 字典序、将 quad 归一化到固定规范序；重复的 {@code (x, z)}
         * 列坐标被确定性拒绝。
         * @throws IllegalArgumentException on duplicate column coordinates.
         */
        public LodSection build() {
            ArrayList<LodColumnStack> cols = new ArrayList<>(columns);
            cols.sort(Comparator.comparingInt(LodColumnStack::x).thenComparingInt(LodColumnStack::z));
            for (int n = 1; n < cols.size(); n++) {
                LodColumnStack a = cols.get(n - 1);
                LodColumnStack b = cols.get(n);
                if (a.x() == b.x() && a.z() == b.z()) {
                    throw new IllegalArgumentException("duplicate column coordinate (" + a.x() + ", " + a.z() + ")");
                }
            }
            ArrayList<LodQuad> qs = new ArrayList<>(quads);
            qs.sort(Comparator
                    .comparingInt(LodQuad::facingBits)
                    .thenComparingInt(LodQuad::originX)
                    .thenComparingInt(LodQuad::originZ)
                    .thenComparingInt(LodQuad::size)
                    .thenComparingInt(LodQuad::yTop)
                    .thenComparingInt(LodQuad::colorIndex));
            return new LodSection(level, originBlockX, originBlockZ, cols, qs);
        }
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof LodSection s
                && level == s.level && originBlockX == s.originBlockX && originBlockZ == s.originBlockZ
                && columnStacks.equals(s.columnStacks) && quads.equals(s.quads));
    }

    @Override
    public int hashCode() {
        int h = 31 * level.hashCode() + originBlockX;
        h = 31 * h + originBlockZ;
        h = 31 * h + columnStacks.hashCode();
        return 31 * h + quads.hashCode();
    }

    @Override
    public String toString() {
        return canonicalText();
    }

    private static int readInt(byte[] d, int i) {
        return ((d[i] & 0xFF) << 24) | ((d[i + 1] & 0xFF) << 16)
                | ((d[i + 2] & 0xFF) << 8) | (d[i + 3] & 0xFF);
    }
}