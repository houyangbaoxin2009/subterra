package io.toterra.subterra.engine.render;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Vanilla-fidelity vertex layout model — p.2.27.1.2: the clean-room data model of
 * the Minecraft 1.21.1 vanilla vertex formats (reference only: the semantic field
 * order and per-field storage types of the vanilla block / entity vertex formats as
 * documented by the Yarn 1.21.1 {@code VertexFormat} mapping). A layout is an
 * immutable, ordered list of {@link VertexField}s in declaration order; each field
 * carries its {@link FieldType}, element count, byte offset, byte size and alignment.
 * All layout math is deterministic and fixed-order, following the same cumulative
 * alignment paradigm as {@code engine.render.instancing.InstanceFormat}: field order
 * equals declaration order, each field starts at the next multiple of its alignment
 * (its scalar type size), and identical declarations always produce the same
 * {@link #byteSize()}, the same {@link #layout()}, and byte-identical
 * {@link #canonicalBytes()} (same layout, same bytes). No timing, no randomness, no
 * MC, no OpenGL. The two built-in constants model the vanilla 1.21.1 semantics:
 * {@link #BLOCK} is the block/terrain vertex layout (position, color, uv, light,
 * overlay, normal) and {@link #ENTITY} is the entity vertex layout (position, color,
 * uv, light, normal). Clean-room: only field semantics are modeled — no vanilla,
 * Sodium or Embeddium source code is included.
 *
 * <p>原版保真顶点布局模型——p.2.27.1.2：Minecraft 1.21.1 原版 vertex 格式的 clean-room 数据模型
 * （仅作对照参考：字段语义序与各字段存储类型取自 Yarn 1.21.1 {@code VertexFormat} 映射对原版方块/
 * 实体 vertex 格式的记录）。布局是不可变的、按声明序排列的 {@link VertexField} 列表；每字段携带
 * {@link FieldType}、元素个数、字节偏移、字节大小与对齐。布局计算全部确定性且固定序，沿用与
 * {@code engine.render.instancing.InstanceFormat} 相同的累计对齐范式：字段序等于声明序，每字段
 * 始于其对齐（其标量类型大小）的下一个倍数，相同声明恒产生相同的 {@link #byteSize()}、相同的
 * {@link #layout()} 与逐字节一致的 {@link #canonicalBytes()}（同布局、同字节）。无时序、无随机、
 * 无 MC、无 OpenGL。两个内建常量按 1.21.1 原版语义建模：{@link #BLOCK} 为方块/地形 vertex 布局
 * （position、color、uv、light、overlay、normal），{@link #ENTITY} 为实体 vertex 布局
 * （position、color、uv、light、normal）。clean-room：只建模字段语义——不包含任何原版、Sodium 或
 * Embeddium 源码。
 */
public final class VertexLayout {

    /**
     * Scalar storage type of a layout field (bytes per scalar), mirroring the vanilla
     * vertex-format element types (FLOAT / UBYTE / USHORT / UINT families).
     *
     * 布局字段的标量存储类型（每标量字节数），对应原版 vertex 格式元素类型（FLOAT / UBYTE /
     * USHORT / UINT 族）。
     */
    public enum FieldType {
        FLOAT(4), UNSIGNED_BYTE(1), UNSIGNED_SHORT(2), UNSIGNED_INT(4);

        private final int byteSize;

        FieldType(int byteSize) {
            this.byteSize = byteSize;
        }

        /** Bytes occupied by a single scalar of this type. 单个该类型标量占用的字节数。 */
        public int byteSize() {
            return byteSize;
        }
    }

    /**
     * A single layout field. Immutable; byte offset / size / alignment are computed
     * deterministically at build time and are part of the canonical encoding.
     * {@code count} is the number of scalars of the field's {@link FieldType}
     * (1..4, e.g. 3 for a position, 4 for an RGBA color).
     *
     * 单个布局字段。不可变；字节偏移/大小/对齐在构建时确定性算出，并参与规范编码。{@code count} 为
     * 该字段 {@link FieldType} 的标量个数（1..4，例如 position 为 3、RGBA 颜色为 4）。
     */
    public static final class VertexField {

        private final String name;
        private final FieldType type;
        private final int count;
        private final int byteOffset;
        private final int byteSize;
        private final int alignment;
        private final int paddedByteSize;
        private final int paddingByteSize;

        private VertexField(String name, FieldType type, int count, int byteOffset,
                            int byteSize, int alignment, int paddedByteSize, int paddingByteSize) {
            this.name = name;
            this.type = type;
            this.count = count;
            this.byteOffset = byteOffset;
            this.byteSize = byteSize;
            this.alignment = alignment;
            this.paddedByteSize = paddedByteSize;
            this.paddingByteSize = paddingByteSize;
        }

        /** Field name (unique within a layout). 字段名（同一布局内唯一）。 */
        public String name() {
            return name;
        }

        /** Scalar storage type. 标量存储类型。 */
        public FieldType type() {
            return type;
        }

        /** Number of scalars of this field. 该字段的标量个数。 */
        public int count() {
            return count;
        }

        /** Byte offset of the field within the vertex. 字段在顶点内的字节偏移。 */
        public int byteOffset() {
            return byteOffset;
        }

        /** Raw byte size of the field. 字段原始字节大小。 */
        public int byteSize() {
            return byteSize;
        }

        /** Byte alignment of the field (= its scalar type size). 字段字节对齐（=其标量类型大小）。 */
        public int alignment() {
            return alignment;
        }

        /** Field size padded up to the alignment boundary. 对齐边界补齐后的字段大小。 */
        public int paddedByteSize() {
            return paddedByteSize;
        }

        /** {@code paddedByteSize() - byteSize()}. 补齐字节数。 */
        public int paddingByteSize() {
            return paddingByteSize;
        }

        @Override
        public String toString() {
            return fieldCanonicalLine(this);
        }
    }

    /**
     * Deterministic layout builder. Fields are appended in declaration order; a
     * duplicate field name is rejected with {@link IllegalArgumentException}. Layout
     * math (offsets / sizes / alignment) is computed once at {@link #build()} and is
     * byte-deterministic, following the same cumulative alignment paradigm as
     * {@code InstanceFormat}: each field starts at the next multiple of its alignment,
     * and the vertex's {@code byteSize} is the running size rounded up to the maximum
     * field alignment. No timing, no randomness.
     *
     * 确定性布局构建器。字段按声明序追加；重名字段以 {@link IllegalArgumentException} 拒绝。布局
     * 计算（偏移/大小/对齐）在 {@link #build()} 时一次性完成且逐字节确定，沿用与 {@code InstanceFormat}
     * 相同的累计对齐范式：每字段始于其对齐的下一个倍数，顶点 {@code byteSize} 为累计大小向上取整到
     * 最大字段对齐。无时序、无随机。
     */
    public static final class Builder {

        private final List<Object[]> pending = new ArrayList<>();

        private Builder() {
        }

        /**
         * Appends a field of the given scalar type and scalar count (1..4) in
         * declaration order. 按声明序追加一个指定标量类型与标量个数（1..4）的字段。
         */
        public Builder append(String name, FieldType type, int count) {
            Objects.requireNonNull(name, "field name must be non-null");
            if (name.isEmpty()) {
                throw new IllegalArgumentException("field name must be non-empty");
            }
            Objects.requireNonNull(type, "field type must be non-null");
            if (count < 1 || count > 4) {
                throw new IllegalArgumentException("field count must be in [1, 4] but was " + count);
            }
            for (Object[] d : pending) {
                if (name.equals(d[0])) {
                    throw new IllegalArgumentException("duplicate field name: " + name);
                }
            }
            pending.add(new Object[]{name, type, count});
            return this;
        }

        /**
         * Computes the deterministic layout and returns the immutable format. Fields
         * keep declaration order; each field starts at the next multiple of its
         * alignment (scalar type size) relative to the running size, and the vertex's
         * {@code byteSize} is the running size rounded up to the maximum alignment.
         * Same declarations always yield the same bytes.
         *
         * 计算确定性布局并返回不可变格式。字段保持声明序；每字段在累计大小的下一个对齐（标量类型
         * 大小）倍数处起始，顶点 {@code byteSize} 为累计大小向上取整到最大对齐。相同声明恒产生相同
         * 字节。
         */
        public VertexLayout build() {
            int running = 0;
            int maxAlignment = 1;
            List<VertexField> fields = new ArrayList<>(pending.size());
            for (Object[] d : pending) {
                String name = (String) d[0];
                FieldType type = (FieldType) d[1];
                int count = (Integer) d[2];
                int alignment = type.byteSize();
                int offset = alignUp(running, alignment);
                int size = count * alignment;
                int padded = alignUp(size, alignment);
                fields.add(new VertexField(name, type, count, offset, size, alignment,
                        padded, padded - size));
                running = offset + padded;
                if (alignment > maxAlignment) {
                    maxAlignment = alignment;
                }
            }
            return new VertexLayout(fields, alignUp(running, maxAlignment), maxAlignment);
        }
    }

    /**
     * Built-in vanilla block/terrain vertex layout (1.21.1 semantics, clean-room):
     * fixed order position (FLOAT×3), color (UNSIGNED_BYTE×4), uv (UNSIGNED_SHORT×2),
     * light (UNSIGNED_SHORT×2), overlay (UNSIGNED_SHORT×2), normal (UNSIGNED_BYTE×3).
     *
     * 内建原版方块/地形 vertex 布局（1.21.1 语义，clean-room）：固定序 position（FLOAT×3）、
     * color（UNSIGNED_BYTE×4）、uv（UNSIGNED_SHORT×2）、light（UNSIGNED_SHORT×2）、
     * overlay（UNSIGNED_SHORT×2）、normal（UNSIGNED_BYTE×3）。
     */
    public static final VertexLayout BLOCK = builder()
            .append("position", FieldType.FLOAT, 3)
            .append("color", FieldType.UNSIGNED_BYTE, 4)
            .append("uv", FieldType.UNSIGNED_SHORT, 2)
            .append("light", FieldType.UNSIGNED_SHORT, 2)
            .append("overlay", FieldType.UNSIGNED_SHORT, 2)
            .append("normal", FieldType.UNSIGNED_BYTE, 3)
            .build();

    /**
     * Built-in vanilla entity vertex layout (1.21.1 semantics, clean-room): fixed
     * order position (FLOAT×3), color (UNSIGNED_BYTE×4), uv (UNSIGNED_SHORT×2),
     * light (UNSIGNED_SHORT×2), normal (UNSIGNED_BYTE×3).
     *
     * 内建原版实体 vertex 布局（1.21.1 语义，clean-room）：固定序 position（FLOAT×3）、
     * color（UNSIGNED_BYTE×4）、uv（UNSIGNED_SHORT×2）、light（UNSIGNED_SHORT×2）、
     * normal（UNSIGNED_BYTE×3）。
     */
    public static final VertexLayout ENTITY = builder()
            .append("position", FieldType.FLOAT, 3)
            .append("color", FieldType.UNSIGNED_BYTE, 4)
            .append("uv", FieldType.UNSIGNED_SHORT, 2)
            .append("light", FieldType.UNSIGNED_SHORT, 2)
            .append("normal", FieldType.UNSIGNED_BYTE, 3)
            .build();

    private final List<VertexField> fields;
    private final int byteSize;
    private final int byteAlignment;
    private final String canonicalText;

    private VertexLayout(List<VertexField> fields, int byteSize, int byteAlignment) {
        this.fields = List.copyOf(fields);
        this.byteSize = byteSize;
        this.byteAlignment = byteAlignment;
        StringBuilder sb = new StringBuilder();
        for (VertexField f : fields) {
            sb.append(fieldCanonicalLine(f)).append('\n');
        }
        sb.append("byteSize=").append(byteSize).append(" byteAlignment=").append(byteAlignment);
        this.canonicalText = sb.toString();
    }

    /**
     * Creates a new deterministic builder. 创建新的确定性构建器。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * All fields in declaration order (unmodifiable, fixed order).
     * 全部字段，按声明序（不可变、固定序）。
     */
    public List<VertexField> fields() {
        return fields;
    }

    /**
     * Fixed-order layout: same as {@link #fields()} — the layout order is always the
     * field declaration order (deterministic, byte-identical for identical inputs).
     *
     * 固定序布局：与 {@link #fields()} 相同——布局序恒等于字段声明序（确定性，相同输入逐字节一致）。
     */
    public List<VertexField> layout() {
        return fields;
    }

    /**
     * Returns the field with the given name, or {@code null} when absent.
     * 返回指定名称的字段；不存在时返回 {@code null}。
     */
    public VertexField field(String name) {
        if (name == null) {
            return null;
        }
        for (VertexField f : fields) {
            if (f.name.equals(name)) {
                return f;
            }
        }
        return null;
    }

    /**
     * Total vertex byte size. 顶点总字节大小。
     */
    public int byteSize() {
        return byteSize;
    }

    /**
     * Vertex byte alignment (the maximum field alignment). 顶点字节对齐（各字段对齐的最大值）。
     */
    public int byteAlignment() {
        return byteAlignment;
    }

    /**
     * Canonical layout text: one fixed-order line per field (name, type, count,
     * offset, size, alignment, padded size) plus the totals line. Deterministic —
     * identical declarations always produce identical text.
     *
     * 规范布局文本：每字段一行固定序描述（名称、类型、个数、偏移、大小、对齐、补齐大小），外加总计行。
     * 确定性——相同声明恒产生相同文本。
     */
    public String canonicalText() {
        return canonicalText;
    }

    /**
     * Canonical encoding of the layout as bytes (UTF-8 of {@link #canonicalText()}).
     * Same layout, same bytes.
     *
     * 布局的规范字节编码（{@link #canonicalText()} 的 UTF-8）。同布局、同字节。
     */
    public byte[] canonicalBytes() {
        return canonicalText.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Deterministic equality: two layouts are equal iff their canonical text is
     * identical. 确定性相等：两布局相等当且仅当其规范文本逐字节相同。
     */
    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof VertexLayout that
                && canonicalText.equals(that.canonicalText));
    }

    @Override
    public int hashCode() {
        return canonicalText.hashCode();
    }

    @Override
    public String toString() {
        return canonicalText;
    }

    private static String fieldCanonicalLine(VertexField f) {
        return "name=" + f.name + " type=" + f.type + " count=" + f.count
                + " offset=" + f.byteOffset + " bytes=" + f.byteSize
                + " align=" + f.alignment + " padded=" + f.paddedByteSize;
    }

    private static int alignUp(int value, int alignment) {
        int rem = value % alignment;
        return rem == 0 ? value : value + (alignment - rem);
    }
}
