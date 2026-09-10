package io.toterra.subterra.engine.render.instancing;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic instance layout / format model (p.2.27.1.1): the pure-JDK counterpart
 * of Flywheel 1.0.x {@code api.layout} (Layout / LayoutBuilder / ElementType /
 * ValueRepr and the scalar / vector / matrix / array element types, as fetched from
 * the {@code mc1.21.1/v1.0.5} tag of the Flywheel line that ships 1.0.6 — see
 * {@code META-INF/third-party/flywheel-1.0.6}). A format is an immutable, ordered list
 * of {@link Field}s in declaration order; each field carries its scalar type, element
 * shape, byte offset, byte size and alignment. All layout math is deterministic and
 * fixed-order: field order equals declaration order, offsets are computed by
 * cumulative alignment (each field starts at the next multiple of its alignment), and
 * the same declarations always produce the same {@link #byteSize()}, the same
 * {@link #layout()}, and byte-identical {@link #canonicalBytes()} (same format, same
 * bytes). No timing, no randomness, no MC, no OpenGL.
 *
 * <p>确定性实例布局/格式模型（p.2.27.1.1）：Flywheel 1.0.x {@code api.layout} 的纯 JDK 对应物
 * （Layout / LayoutBuilder / ElementType / ValueRepr 及 scalar / vector / matrix / array
 * 元素类型，抓取自承载 1.0.6 的 Flywheel 线的 {@code mc1.21.1/v1.0.5} tag——见
 * {@code META-INF/third-party/flywheel-1.0.6}）。格式是字段按声明序构成的不变有序列表；每字段
 * 携带标量类型、元素形态、字节偏移、字节大小与对齐。布局计算全部确定性且固定序：字段序等于声明
 * 序，偏移按累计对齐计算（每字段始于其对齐的下一个倍数），相同声明恒产生相同的 {@link #byteSize()}、
 * 相同的 {@link #layout()} 与逐字节一致的 {@link #canonicalBytes()}（同格式、同字节）。无时序、
 * 无随机、无 MC、无 OpenGL。
 */
public final class InstanceFormat {

    /**
     * Scalar storage type of a field element (bytes per scalar), mirroring Flywheel's
     * IntegerRepr / UnsignedIntegerRepr / FloatRepr families.
     *
     * 字段元素的标量存储类型（每标量字节数），对应 Flywheel 的 IntegerRepr /
     * UnsignedIntegerRepr / FloatRepr 族。
     */
    public enum ScalarType {
        I8(1), I16(2), I32(4), U8(1), U16(2), U32(4), F16(2), F32(4), F64(8);

        private final int byteSize;

        ScalarType(int byteSize) {
            this.byteSize = byteSize;
        }

        /** Bytes occupied by a single scalar of this type. 单个该类型标量占用的字节数。 */
        public int byteSize() {
            return byteSize;
        }
    }

    /**
     * Element shape of a field, mirroring Flywheel's ScalarElementType /
     * VectorElementType / MatrixElementType / ArrayElementType. Declaration order of
     * the enum constants is fixed.
     *
     * 字段的元素形态，对应 Flywheel 的 ScalarElementType / VectorElementType /
     * MatrixElementType / ArrayElementType。枚举常量声明序固定。
     */
    public enum ElementKind {
        SCALAR, VECTOR, MATRIX, ARRAY
    }

    /**
     * A single layout field. Immutable; byte offset / size / alignment are computed
     * deterministically at build time and are part of the canonical encoding.
     * Meaningful dimension slots per {@link ElementKind}: {@code vectorSize} for
     * VECTOR (2..4); {@code rows}/{@code columns} for MATRIX (2..4 each);
     * {@code arrayLength} for ARRAY (1..256, elements shaped by
     * {@code arrayElementKind} plus its own dimension slots).
     *
     * 单个布局字段。不可变；字节偏移/大小/对齐在构建时确定性算出，并参与规范编码。
     * 各 {@link ElementKind} 的有效维度槽位：VECTOR 用 {@code vectorSize}（2..4）；
     * MATRIX 用 {@code rows}/{@code columns}（各 2..4）；ARRAY 用 {@code arrayLength}
     * （1..256，元素形态由 {@code arrayElementKind} 及其维度槽位决定）。
     */
    public static final class Field {

        private final String name;
        private final ScalarType scalarType;
        private final ElementKind kind;
        private final int vectorSize;
        private final int rows;
        private final int columns;
        private final ElementKind arrayElementKind;
        private final int arrayLength;
        private final int byteOffset;
        private final int byteSize;
        private final int alignment;
        private final int paddedByteSize;
        private final int paddingByteSize;

        private Field(String name, ScalarType scalarType, ElementKind kind, int vectorSize,
                      int rows, int columns, ElementKind arrayElementKind, int arrayLength,
                      int byteOffset, int byteSize, int alignment, int paddedByteSize,
                      int paddingByteSize) {
            this.name = name;
            this.scalarType = scalarType;
            this.kind = kind;
            this.vectorSize = vectorSize;
            this.rows = rows;
            this.columns = columns;
            this.arrayElementKind = arrayElementKind;
            this.arrayLength = arrayLength;
            this.byteOffset = byteOffset;
            this.byteSize = byteSize;
            this.alignment = alignment;
            this.paddedByteSize = paddedByteSize;
            this.paddingByteSize = paddingByteSize;
        }

        /** Field name (unique within a format). 字段名（同一格式内唯一）。 */
        public String name() {
            return name;
        }

        /** Scalar storage type. 标量存储类型。 */
        public ScalarType scalarType() {
            return scalarType;
        }

        /** Element shape. 元素形态。 */
        public ElementKind kind() {
            return kind;
        }

        /** Vector size (VECTOR only). 向量大小（仅 VECTOR）。 */
        public int vectorSize() {
            return vectorSize;
        }

        /** Matrix rows (MATRIX only). 矩阵行数（仅 MATRIX）。 */
        public int rows() {
            return rows;
        }

        /** Matrix columns (MATRIX only). 矩阵列数（仅 MATRIX）。 */
        public int columns() {
            return columns;
        }

        /** Element shape of an ARRAY field. ARRAY 字段的元素形态。 */
        public ElementKind arrayElementKind() {
            return arrayElementKind;
        }

        /** Array length (ARRAY only). 数组长度（仅 ARRAY）。 */
        public int arrayLength() {
            return arrayLength;
        }

        /** Byte offset of the field within the instance record. 字段在实例记录内的字节偏移。 */
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
     * Deterministic format builder. Fields are appended in declaration order; a
     * duplicate field name is rejected with {@link IllegalArgumentException}. Layout
     * math (offsets / sizes / alignment) is computed once at {@link #build()} and is
     * byte-deterministic. No timing, no randomness.
     *
     * 确定性格式构建器。字段按声明序追加；重名字段以 {@link IllegalArgumentException} 拒绝。
     * 布局计算（偏移/大小/对齐）在 {@link #build()} 时一次性完成且逐字节确定。无时序、无随机。
     */
    public static final class Builder {

        private final List<String> names = new ArrayList<>();
        private final List<Object[]> pending = new ArrayList<>();

        private Builder() {
        }

        private Builder scalarLike(String name, ScalarType scalarType, ElementKind kind,
                                   int vectorSize, int rows, int columns) {
            Objects.requireNonNull(name, "field name must be non-null");
            if (name.isEmpty()) {
                throw new IllegalArgumentException("field name must be non-empty");
            }
            Objects.requireNonNull(scalarType, "field scalar type must be non-null");
            if (names.contains(name)) {
                throw new IllegalArgumentException("duplicate field name: " + name);
            }
            names.add(name);
            pending.add(new Object[]{name, scalarType, kind, vectorSize, rows, columns, null, 0});
            return this;
        }

        /**
         * Appends a scalar field ({@code elementCount} scalars). 追加标量字段（一个标量）。
         */
        public Builder scalar(String name, ScalarType scalarType) {
            return scalarLike(name, scalarType, ElementKind.SCALAR, 1, 1, 1);
        }

        /**
         * Appends a vector field of the given size (2..4). 追加指定大小（2..4）的向量字段。
         */
        public Builder vector(String name, ScalarType scalarType, int size) {
            requireRange(size, 2, 4, "vector size");
            return scalarLike(name, scalarType, ElementKind.VECTOR, size, 1, 1);
        }

        /**
         * Appends a matrix field with the given rows/columns (2..4 each).
         * 追加指定行/列（各 2..4）的矩阵字段。
         */
        public Builder matrix(String name, ScalarType scalarType, int rows, int columns) {
            requireRange(rows, 2, 4, "matrix rows");
            requireRange(columns, 2, 4, "matrix columns");
            return scalarLike(name, scalarType, ElementKind.MATRIX, 1, rows, columns);
        }

        /**
         * Appends a matrix field of the given square size (2..4). 追加指定方阵大小（2..4）的矩阵字段。
         */
        public Builder matrix(String name, ScalarType scalarType, int size) {
            requireRange(size, 2, 4, "matrix size");
            return scalarLike(name, scalarType, ElementKind.MATRIX, 1, size, size);
        }

        /**
         * Appends an array of scalars (length 1..256). 追加标量数组（长度 1..256）。
         */
        public Builder scalarArray(String name, ScalarType scalarType, int length) {
            requireRange(length, 1, 256, "array length");
            return arrayLike(name, scalarType, ElementKind.SCALAR, 1, 1, 1, length);
        }

        /**
         * Appends an array of vectors (size 2..4, length 1..256).
         * 追加向量数组（大小 2..4，长度 1..256）。
         */
        public Builder vectorArray(String name, ScalarType scalarType, int size, int length) {
            requireRange(size, 2, 4, "vector size");
            requireRange(length, 1, 256, "array length");
            return arrayLike(name, scalarType, ElementKind.VECTOR, size, 1, 1, length);
        }

        /**
         * Appends an array of matrices (rows/columns 2..4 each, length 1..256).
         * 追加矩阵数组（行/列各 2..4，长度 1..256）。
         */
        public Builder matrixArray(String name, ScalarType scalarType, int rows, int columns, int length) {
            requireRange(rows, 2, 4, "matrix rows");
            requireRange(columns, 2, 4, "matrix columns");
            requireRange(length, 1, 256, "array length");
            return arrayLike(name, scalarType, ElementKind.MATRIX, 1, rows, columns, length);
        }

        private Builder arrayLike(String name, ScalarType scalarType, ElementKind arrayElementKind,
                                  int vectorSize, int rows, int columns, int length) {
            Objects.requireNonNull(name, "field name must be non-null");
            if (name.isEmpty()) {
                throw new IllegalArgumentException("field name must be non-empty");
            }
            Objects.requireNonNull(scalarType, "field scalar type must be non-null");
            if (names.contains(name)) {
                throw new IllegalArgumentException("duplicate field name: " + name);
            }
            names.add(name);
            pending.add(new Object[]{name, scalarType, ElementKind.ARRAY, vectorSize, rows,
                    columns, arrayElementKind, length});
            return this;
        }

        /**
         * Computes the deterministic layout and returns the immutable format. Fields
         * keep declaration order; each field starts at the next multiple of its
         * alignment (scalar type size) relative to the running size, and the record's
         * {@code byteSize} is the running size rounded up to the maximum alignment.
         * Same declarations always yield the same bytes.
         *
         * 计算确定性布局并返回不可变格式。字段保持声明序；每字段在累计大小的下一个对齐（标量类型
         * 大小）倍数处起始，记录 {@code byteSize} 为累计大小向上取整到最大对齐。相同声明恒产生
         * 相同字节。
         */
        public InstanceFormat build() {
            int running = 0;
            int maxAlignment = 1;
            List<Field> fields = new ArrayList<>(pending.size());
            for (Object[] d : pending) {
                String name = (String) d[0];
                ScalarType scalarType = (ScalarType) d[1];
                ElementKind kind = (ElementKind) d[2];
                int vectorSize = (Integer) d[3];
                int rows = (Integer) d[4];
                int columns = (Integer) d[5];
                ElementKind arrayElementKind = (ElementKind) d[6];
                int arrayLength = (Integer) d[7];
                int scalarCount = scalarCount(kind, vectorSize, rows, columns, arrayElementKind, arrayLength);
                int alignment = scalarType.byteSize();
                int offset = alignUp(running, alignment);
                int size = scalarCount * alignment;
                int padded = alignUp(size, alignment);
                fields.add(new Field(name, scalarType, kind, vectorSize, rows, columns,
                        arrayElementKind, arrayLength, offset, size, alignment, padded, padded - size));
                running = offset + padded;
                if (alignment > maxAlignment) {
                    maxAlignment = alignment;
                }
            }
            return new InstanceFormat(fields, alignUp(running, maxAlignment), maxAlignment);
        }
    }

    private final List<Field> fields;
    private final int byteSize;
    private final int byteAlignment;
    private final String canonicalText;

    private InstanceFormat(List<Field> fields, int byteSize, int byteAlignment) {
        this.fields = List.copyOf(fields);
        this.byteSize = byteSize;
        this.byteAlignment = byteAlignment;
        StringBuilder sb = new StringBuilder();
        for (Field f : fields) {
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
    public List<Field> fields() {
        return fields;
    }

    /**
     * Fixed-order layout: same as {@link #fields()} — the layout order is always the
     * field declaration order (deterministic, byte-identical for identical inputs).
     *
     * 固定序布局：与 {@link #fields()} 相同——布局序恒等于字段声明序（确定性，相同输入逐字节一致）。
     */
    public List<Field> layout() {
        return fields;
    }

    /**
     * Returns the field with the given name, or {@code null} when absent.
     * 返回指定名称的字段；不存在时返回 {@code null}。
     */
    public Field field(String name) {
        if (name == null) {
            return null;
        }
        for (Field f : fields) {
            if (f.name.equals(name)) {
                return f;
            }
        }
        return null;
    }

    /**
     * Total instance record byte size. 实例记录总字节大小。
     */
    public int byteSize() {
        return byteSize;
    }

    /**
     * Record byte alignment (the maximum field alignment). 记录字节对齐（各字段对齐的最大值）。
     */
    public int byteAlignment() {
        return byteAlignment;
    }

    /**
     * Canonical layout text: one fixed-order line per field (name, kind, scalar type,
     * dimensions, offset, size, alignment, padded size) plus the totals line.
     * Deterministic — identical declarations always produce identical text.
     *
     * 规范布局文本：每字段一行固定序描述（名称、形态、标量类型、维度、偏移、大小、对齐、补齐大小），
     * 外加总计行。确定性——相同声明恒产生相同文本。
     */
    public String canonicalText() {
        return canonicalText;
    }

    /**
     * Canonical encoding of the format as bytes (UTF-8 of {@link #canonicalText()}).
     * Same format, same bytes.
     *
     * 格式的规范字节编码（{@link #canonicalText()} 的 UTF-8）。同格式、同字节。
     */
    public byte[] canonicalBytes() {
        return canonicalText.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Deterministic equality: two formats are equal iff their canonical text is
     * identical. 确定性相等：两格式相等当且仅当其规范文本逐字节相同。
     */
    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof InstanceFormat that
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

    private static String fieldShapeText(Field f) {
        return switch (f.kind) {
            case SCALAR -> "scalar";
            case VECTOR -> "vector" + f.vectorSize;
            case MATRIX -> "matrix" + f.rows + 'x' + f.columns;
            case ARRAY -> "array:" + f.arrayElementKind.name().toLowerCase()
                    + ":" + (switch (f.arrayElementKind) {
                        case SCALAR -> "1";
                        case VECTOR -> String.valueOf(f.vectorSize);
                        case MATRIX -> f.rows + "x" + f.columns;
                        default -> "?";
                    }) + ":" + f.arrayLength;
        };
    }

    private static String fieldCanonicalLine(Field f) {
        return "name=" + f.name + " kind=" + f.kind + " type=" + f.scalarType
                + " shape=" + fieldShapeText(f)
                + " offset=" + f.byteOffset + " bytes=" + f.byteSize
                + " align=" + f.alignment + " padded=" + f.paddedByteSize;
    }

    private static int scalarCount(ElementKind kind, int vectorSize, int rows, int columns,
                                   ElementKind arrayElementKind, int arrayLength) {
        return switch (kind) {
            case SCALAR -> 1;
            case VECTOR -> vectorSize;
            case MATRIX -> rows * columns;
            case ARRAY -> arrayLength * switch (arrayElementKind) {
                case SCALAR -> 1;
                case VECTOR -> vectorSize;
                case MATRIX -> rows * columns;
                default -> throw new IllegalStateException("unreachable array element kind");
            };
        };
    }

    private static int alignUp(int value, int alignment) {
        int rem = value % alignment;
        return rem == 0 ? value : value + (alignment - rem);
    }

    private static void requireRange(int value, int min, int max, String what) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(what + " must be in [" + min + ", " + max + "] but was " + value);
        }
    }
}
