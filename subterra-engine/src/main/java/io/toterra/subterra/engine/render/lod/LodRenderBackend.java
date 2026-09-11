package io.toterra.subterra.engine.render.lod;

import io.toterra.subterra.engine.render.instancing.BackendRegistry;
import io.toterra.subterra.engine.render.instancing.InstanceFormat;
import io.toterra.subterra.engine.render.instancing.RenderBackend;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * LOD mesh render backend (p.2.28.4, clean-room self-developed): renders a {@link LodSection}'s
 * column-profile / horizontal-quad data as a fixed-order instanced data model, reusing the
 * p.2.27.1 instancing base (<em>imported and re-used, never copied</em>). It consumes
 * {@link LodVertexLayout#COLUMN} / {@link LodVertexLayout#QUAD} layout specs and the
 * {@link InstanceFormat} instance-layout paradigm, and it registers into the existing
 * {@link BackendRegistry} so the engine's deterministic default-selection / duplicate-rejection
 * / fixed-registration-order semantics apply uniformly (see {@link #registerInto()}).
 *
 * <p><b>Registration.</b> {@link BackendRegistry} is a fixed static registry exposed only
 * through its public {@link BackendRegistry#register(RenderBackend)} (fixed insertion order,
 * duplicate-name rejection, deterministic default). No instancing class is modified; the LOD
 * backend merely provides {@link #registerInto()} as its registration entry point, which calls
 * {@code BackendRegistry#register}. Because the registry has no instance constructor this
 * adaptor is documented (and usable) as the single registration path for the LOD backend.
 *
 * <p><b>Output data model.</b> {@link #instantiate(LodSection)} converts the section into an
 * immutable {@link InstantiatedMesh}: a fixed-order instance record per horizontal quad
 * ({@link #QUAD_INSTANCE_FORMAT}) plus its 4 {@link LodVertexLayout#QUAD} vertices, and a fixed-order
 * instance record per column profile entry ({@link #COLUMN_INSTANCE_FORMAT}) plus its 4
 * {@link LodVertexLayout#COLUMN} vertices. Order is fully deterministic: the section already
 * normalizes columns to {@code (x,z)} order and quads to a fixed canonical order; columns are
 * iterated in that order and each column's profile entries in insertion order. The output is
 * pure data (byte arrays) — <em>no</em> real GL/GPU calls are made. Same section &rarr; same bytes
 * ({@link InstantiatedMesh#toBytes()}/{@link #canonicalText()}). The per-quad {@code meta} byte
 * carries the seam {@code stitch} bit at {@code (1&lt;&lt;4)} (from {@link LodSeamRules#stitchBit}).
 *
 * <p><b>Determinism / memory.</b> All arithmetic is integer, all streams linear (single
 * {@code Arrays.copyOf}-style sized allocation, no {@code O(n²)} string building), no timing,
 * no randomness. Pure JDK; zero MC/OpenGL imports.
 *
 * <p>LOD 网格渲染后端（p.2.28.4，clean-room 自研）：把 {@link LodSection} 的列柱剖面/水平 quad 数据渲染为
 * 固定序实例化数据模型，复用 p.2.27.1 实例化基座（<em>import 并复用，绝不复制</em>）。它消费
 * {@link LodVertexLayout#COLUMN} / {@link LodVertexLayout#QUAD} 布局规格与 {@link InstanceFormat}
 * 实例布局范式，并注册进现有 {@link BackendRegistry}，使引擎确定性的缺省选择/同名拒绝/固定注册序语义对 LOD
 * 后端统一生效（见 {@link #registerInto()}）。
 *
 * <p><b>注册。</b>{@link BackendRegistry} 是仅经其公有 {@link BackendRegistry#register(RenderBackend)} 暴露的固定
 * 静态注册表（固定插入序、同名拒绝、确定性缺省）。不改任何 instancing 类；LOD 后端仅提供 {@link #registerInto()}
 * 作为其注册入口，内部调用 {@code BackendRegistry#register}。由于注册表没有实例构造器，该适配器被文档化（且可
 * 用）为 LOD 后端的唯一注册路径。
 *
 * <p><b>输出数据模型。</b>{@link #instantiate(LodSection)} 把区块转换为不可变 {@link InstantiatedMesh}：
 * 每个水平 quad 一个固定序实例记录（{@link #QUAD_INSTANCE_FORMAT}）加其 4 个 {@link LodVertexLayout#QUAD}
 * 顶点；每个列剖面段一个固定序实例记录（{@link #COLUMN_INSTANCE_FORMAT}）加其 4 个
 * {@link LodVertexLayout#COLUMN} 顶点。顺序完全确定：区块已把列归一化到 {@code (x,z)} 序、quad 归一化到
 * 固定规范序；列按该序遍历、每列的剖面段按插入序遍历。输出为纯数据（字节数组）——<em>不做</em>真实
 * GL/GPU 调用。同区块&rarr;同字节（{@link InstantiatedMesh#toBytes()} / {@link #canonicalText()}）。每个 quad 的
 * {@code meta} 字节在 {@code (1&lt;&lt;4)} 处携带接缝 {@code stitch} 位（来自 {@link LodSeamRules#stitchBit}）。
 *
 * <p><b>确定性与内存。</b>全部整数算术、全部线性流（单次定尺寸分配、无 {@code O(n²)} 字符串拼装）、无时序、
 * 无随机。纯 JDK；零 MC/OpenGL import。
 */
public final class LodRenderBackend implements RenderBackend {

    /** Stable backend registry name. / 稳定的后端注册名。 */
    public static final String NAME = "lod";

    /** Vertex count of a full horizontal-quad instance (one per corner). / 一个完整水平 quad 实例的顶点数（一角一个）。 */
    public static final int QUAD_VERTS_PER_INSTANCE = 4;

    /** Vertex count of a full column-wall instance (one per corner). / 一个完整列墙实例的顶点数（一角一个）。 */
    public static final int COLUMN_VERTS_PER_INSTANCE = 4;

    /**
     * Per-quad instance record layout (fixed order): {@code originX} U16, {@code originZ} U16,
     * {@code size} U8, {@code yTop} I16, {@code colorIndex} U8, {@code meta} U8. Byte size 10
     * (each U16 scalar aligns to 2 bytes).
     * / 每 quad 实例记录布局（固定序）：{@code originX} U16、{@code originZ} U16、{@code size} U8、
     * {@code yTop} I16、{@code colorIndex} U8、{@code meta} U8。字节大小 10（每个 U16 标量按 2 字节对齐）。
     */
    public static final InstanceFormat QUAD_INSTANCE_FORMAT = InstanceFormat.builder()
            .scalar("originX", InstanceFormat.ScalarType.U16)
            .scalar("originZ", InstanceFormat.ScalarType.U16)
            .scalar("size", InstanceFormat.ScalarType.U8)
            .scalar("yTop", InstanceFormat.ScalarType.I16)
            .scalar("colorIndex", InstanceFormat.ScalarType.U8)
            .scalar("meta", InstanceFormat.ScalarType.U8)
            .build();

    /**
     * Per-column-profile-entry instance record layout (fixed order): {@code originX} U16,
     * {@code originZ} U16, {@code yLow} I16, {@code yHigh} I16, {@code colorIndex} U8.
     * Byte size 10. / 每列剖面段实例记录布局（固定序）：{@code originX} U16、{@code originZ} U16、
     * {@code yLow} I16、{@code yHigh} I16、{@code colorIndex} U8。字节大小 10。
     */
    public static final InstanceFormat COLUMN_INSTANCE_FORMAT = InstanceFormat.builder()
            .scalar("originX", InstanceFormat.ScalarType.U16)
            .scalar("originZ", InstanceFormat.ScalarType.U16)
            .scalar("yLow", InstanceFormat.ScalarType.I16)
            .scalar("yHigh", InstanceFormat.ScalarType.I16)
            .scalar("colorIndex", InstanceFormat.ScalarType.U8)
            .build();

    /** Intended registration order marker: the second-highest priority keeps the default LOD selection. /
     *  注册序标记：LOD 后端优先级。 */
    private static final int LOD_PRIORITY = 0;

    /** Than the shared instance, registered backend (idempotent-safe new instance per register). /
     *  供一次性注册的共享实例。 */
    public static final LodRenderBackend INSTANCE = new LodRenderBackend();

    private LodRenderBackend() {
    }

    /**
     * Registration entry point into the engine's {@link BackendRegistry}: delegates to
     * {@code BackendRegistry#register(INSTANCE)}, i.e. adds the LOD backend in fixed insertion
     * order with this name; a second registration of the same name is rejected with
     * {@link IllegalArgumentException} per the registry's duplicate-rejection contract. The
     * registry's {@link BackendRegistry#defaultFor(InstanceFormat)} then selects this backend
     * deterministically for the two LOD instance formats (its {@link #priority()} and
     * registration order resolve ties). No instancing class is modified.
     * / 进入引擎 {@link BackendRegistry} 的注册入口：委托给 {@code BackendRegistry#register(INSTANCE)}，
     * 即按固定插入序以本名注册 LOD 后端；按注册表同名拒绝契约，同名二次注册以 {@link IllegalArgumentException}
     * 拒绝。此后 {@link BackendRegistry#defaultFor(InstanceFormat)} 会针对两个 LOD 实例格式确定性选中本后端
     * （其 {@link #priority()} 与注册序解决平局）。不改任何 instancing 类。
     */
    public static void registerInto() {
        BackendRegistry.register(INSTANCE);
    }

    /**
     * Additional registration entry for callers that hold a registry reference (the registry is
     * static; the argument documents intent and is validated non-null but unused, mirroring the
     * no-arg {@link #registerInto()}). / 为持有注册表引用的调用方提供的附加注册入口（注册表为静态；参数仅作
     * 意图文档并校验非空但未使用，镜像无参 {@link #registerInto()}）。
     */
    public static void registerInto(BackendRegistry registry) {
        Objects.requireNonNull(registry, "registry must not be null (registry API is static)");
        registerInto();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean supports(InstanceFormat format) {
        return format != null && (QUAD_INSTANCE_FORMAT.equals(format) || COLUMN_INSTANCE_FORMAT.equals(format));
    }

    @Override
    public String description() {
        return "LOD fixed-order mesh instancing backend (p.2.28.4): quantized column-profile and "
                + "horizontal-quad mesh to deterministic instance records + vertex bytes, byte-identical, "
                + "reusing the p.2.27.1 InstanceFormat/LodVertexLayout paradigm.";
    }

    @Override
    public int priority() {
        return LOD_PRIORITY;
    }

    /**
     * Converts a section into the fixed-order instanced data model. Pure data; no GL/GPU calls.
     * / 把区块转换为固定序实例化数据模型。纯数据；不做 GL/GPU 调用。
     *
     * @param section the section to instantiate (non-null).
     * @return an immutable {@link InstantiatedMesh}; same input, same bytes.
     */
    public InstantiatedMesh instantiate(LodSection section) {
        Objects.requireNonNull(section, "section must not be null");
        List<LodColumnStack> columns = section.columnStacks();
        List<LodQuad> quads = section.quads();

        int columnEntryCount = 0;
        for (LodColumnStack c : columns) {
            columnEntryCount += c.entries().size();
        }
        int maxQuadSize = 0;
        for (LodQuad q : quads) {
            if (q.size() > maxQuadSize) {
                maxQuadSize = q.size();
            }
        }

        byte[] colInstance = new byte[columnEntryCount * COLUMN_INSTANCE_FORMAT.byteSize()];
        byte[] colVertex = new byte[columnEntryCount * COLUMN_VERTS_PER_INSTANCE * LodVertexLayout.COLUMN.byteSize()];
        int ci = 0;
        int cv = 0;
        for (LodColumnStack col : columns) {
            for (LodColumnStack.LodProfileEntry e : col.entries()) {
                writeColumnInstance(colInstance, ci, col, e);
                ci += COLUMN_INSTANCE_FORMAT.byteSize();
                writeColumnVertices(colVertex, cv, col, e);
                cv += COLUMN_VERTS_PER_INSTANCE * LodVertexLayout.COLUMN.byteSize();
            }
        }

        byte[] quadInstance = new byte[quads.size() * QUAD_INSTANCE_FORMAT.byteSize()];
        byte[] quadVertex = new byte[quads.size() * QUAD_VERTS_PER_INSTANCE * LodVertexLayout.QUAD.byteSize()];
        int qi = 0;
        int qv = 0;
        for (LodQuad q : quads) {
            int stitch = LodSeamRules.stitchBit(q.size(), maxQuadSize);
            writeQuadInstance(quadInstance, qi, q, stitch);
            qi += QUAD_INSTANCE_FORMAT.byteSize();
            writeQuadVertices(quadVertex, qv, q, stitch);
            qv += QUAD_VERTS_PER_INSTANCE * LodVertexLayout.QUAD.byteSize();
        }

        return new InstantiatedMesh(section.level(), columnEntryCount, quads.size(),
                colInstance, colVertex, quadInstance, quadVertex);
    }

    private void writeColumnInstance(byte[] out, int base, LodColumnStack col, LodColumnStack.LodProfileEntry e) {
        int off = base;
        writeU16(out, off + COLUMN_INSTANCE_FORMAT.field("originX").byteOffset(), col.x());
        writeU16(out, off + COLUMN_INSTANCE_FORMAT.field("originZ").byteOffset(), col.z());
        writeI16(out, off + COLUMN_INSTANCE_FORMAT.field("yLow").byteOffset(), e.yLow());
        writeI16(out, off + COLUMN_INSTANCE_FORMAT.field("yHigh").byteOffset(), e.yHigh());
        writeU8(out, off + COLUMN_INSTANCE_FORMAT.field("colorIndex").byteOffset(), e.colorIndex());
    }

    private void writeQuadInstance(byte[] out, int base, LodQuad q, int stitchBit) {
        int off = base;
        writeU16(out, off + QUAD_INSTANCE_FORMAT.field("originX").byteOffset(), q.originX());
        writeU16(out, off + QUAD_INSTANCE_FORMAT.field("originZ").byteOffset(), q.originZ());
        writeU8(out, off + QUAD_INSTANCE_FORMAT.field("size").byteOffset(), q.size());
        writeI16(out, off + QUAD_INSTANCE_FORMAT.field("yTop").byteOffset(), q.yTop());
        writeU8(out, off + QUAD_INSTANCE_FORMAT.field("colorIndex").byteOffset(), q.colorIndex());
        // meta: facing bits low nibble + seam stitch bit at bit 4
        writeU8(out, off + QUAD_INSTANCE_FORMAT.field("meta").byteOffset(), q.facingBits() | (stitchBit << 4));
    }

    /**
     * Writes the 4 corners of a horizontal quad in fixed order (x1=originX, x2=originX+size,
     * z1=originZ, z2=originZ+size), all at y=yTop, with colorIndex then meta.
     * / 按固定序写出水平 quad 的 4 个角（x1=originX、x2=originX+size、z1=originZ、z2=originZ+size），
     * 全部位于 y=yTop，后接 colorIndex 与 meta。
     */
    private void writeQuadVertices(byte[] out, int base, LodQuad q, int stitchBit) {
        int x1 = q.originX();
        int z1 = q.originZ();
        int x2 = q.originX() + q.size();
        int z2 = q.originZ() + q.size();
        int y = q.yTop();
        int stride = LodVertexLayout.QUAD.byteSize();
        int b = base;
        writeQuadVertex(out, b, x1, y, z1, q.colorIndex(), q.facingBits(), stitchBit);
        b += stride;
        writeQuadVertex(out, b, x2, y, z1, q.colorIndex(), q.facingBits(), stitchBit);
        b += stride;
        writeQuadVertex(out, b, x2, y, z2, q.colorIndex(), q.facingBits(), stitchBit);
        b += stride;
        writeQuadVertex(out, b, x1, y, z2, q.colorIndex(), q.facingBits(), stitchBit);
    }

    private void writeQuadVertex(byte[] out, int base, int x, int y, int z,
                                 int colorIndex, int facingBits, int stitchBit) {
        writeU16(out, base, x & 0xFFFF);
        writeU16(out, base + 2, y & 0xFFFF);
        writeU16(out, base + 4, z & 0xFFFF);
        writeU8(out, base + 6, colorIndex);
        writeU8(out, base + 7, facingBits | (stitchBit << 4));
    }

    /**
     * Writes the 4 corners of a vertical column wall in fixed order, one block wide facing +Z,
     * spanned yLow..yHigh at the column's (x, z): (x,yLow,z), (x,yHigh,z), (x+1,yHigh,z),
     * (x+1,yLow,z); colorIndex after each position.
     * / 按固定序写出 1 方块宽、朝向 +Z、跨 yLow..yHigh 于列 (x, z) 的垂直列墙 4 个角：
     * (x,yLow,z)、(x,yHigh,z)、(x+1,yHigh,z)、(x+1,yLow,z)；每个 position 后接 colorIndex。
     */
    private void writeColumnVertices(byte[] out, int base, LodColumnStack col, LodColumnStack.LodProfileEntry e) {
        int x = col.x();
        int z = col.z();
        int x2 = (x + 1);
        int stride = LodVertexLayout.COLUMN.byteSize();
        int b = base;
        writeColumnVertex(out, b, x, e.yLow(), z, e.colorIndex());
        b += stride;
        writeColumnVertex(out, b, x, e.yHigh(), z, e.colorIndex());
        b += stride;
        writeColumnVertex(out, b, x2, e.yHigh(), z, e.colorIndex());
        b += stride;
        writeColumnVertex(out, b, x2, e.yLow(), z, e.colorIndex());
    }

    private void writeColumnVertex(byte[] out, int base, int x, int y, int z, int colorIndex) {
        writeU16(out, base, x & 0xFFFF);
        writeU16(out, base + 2, y & 0xFFFF);
        writeU16(out, base + 4, z & 0xFFFF);
        writeU8(out, base + 6, colorIndex);
    }

    private static void writeU16(byte[] out, int i, int v) {
        out[i] = (byte) (v >> 8);
        out[i + 1] = (byte) v;
    }

    private static void writeI16(byte[] out, int i, int v) {
        out[i] = (byte) (v >> 8);
        out[i + 1] = (byte) v;
    }

    private static void writeU8(byte[] out, int i, int v) {
        out[i] = (byte) v;
    }

    /**
     * Immutable fixed-order instanced mesh data model produced by {@link LodRenderBackend#instantiate}.
     * Carries the two per-entry instance-record streams (column profiles / horizontal quads) and
     * the two vertex streams (in {@link LodVertexLayout#COLUMN} / {@link LodVertexLayout#QUAD} order),
     * all in deterministic fixed order. {@link #toBytes()} is a fixed-order length-prefixed encoding and
     * {@link #canonicalText()} a fixed-order text; both are byte-identical for the same instantiated mesh.
     * Pure data; no GL/GPU calls.
     * / {@link LodRenderBackend#instantiate} 产出的不可变固定序实例化网格数据模型。携带两类逐条实例记录流（列剖面/
     * 水平 quad）与两类顶点流（按 {@link LodVertexLayout#COLUMN} / {@link LodVertexLayout#QUAD} 序），全部固定序。
     * {@link #toBytes()} 为固定序长度前缀编码、{@link #canonicalText()} 为固定序文本；对同一实例化网格均逐字节一致。
     * 纯数据；不做 GL/GPU 调用。
     */
    public static final class InstantiatedMesh {

        /** Wire version of the encoding. Bump on incompatible change; never permute fields. / 编码线版本。不兼容时递增；绝不重排。 */
        public static final int VERSION = 1;

        /** Fixed header: version int + level ordinal byte + columnEntryCount int + quadCount int. /
         *  固定头：version int + 层级序 byte + columnEntryCount int + quadCount int。 */
        static final int HEADER_BYTES = 4 + 1 + 4 + 4;

        private final LodLevel level;
        private final int columnEntryCount;
        private final int quadCount;
        private final byte[] columnInstanceBytes;
        private final byte[] columnVertexBytes;
        private final byte[] quadInstanceBytes;
        private final byte[] quadVertexBytes;

        private InstantiatedMesh(LodLevel level, int columnEntryCount, int quadCount,
                                 byte[] columnInstanceBytes, byte[] columnVertexBytes,
                                 byte[] quadInstanceBytes, byte[] quadVertexBytes) {
            this.level = level;
            this.columnEntryCount = columnEntryCount;
            this.quadCount = quadCount;
            this.columnInstanceBytes = columnInstanceBytes;
            this.columnVertexBytes = columnVertexBytes;
            this.quadInstanceBytes = quadInstanceBytes;
            this.quadVertexBytes = quadVertexBytes;
        }

        /** The section's detail level. / 区块细节层级。 */
        public LodLevel level() {
            return level;
        }

        /** Number of column profile entries instantiated (fixed order). / 被实例化的列剖面段数（固定序）。 */
        public int columnEntryCount() {
            return columnEntryCount;
        }

        /** Number of horizontal quads instantiated (fixed order). / 被实例化的水平 quad 数（固定序）。 */
        public int quadCount() {
            return quadCount;
        }

        /** Packed column-instance records in fixed order. / 固定序的列实例记录打包字节。 */
        public byte[] columnInstanceBytes() {
            return columnInstanceBytes;
        }

        /** Packed column-wall vertices in fixed order. / 固定序的列墙顶点字节。 */
        public byte[] columnVertexBytes() {
            return columnVertexBytes;
        }

        /** Packed quad-instance records in fixed order. / 固定序的 quad 实例记录打包字节。 */
        public byte[] quadInstanceBytes() {
            return quadInstanceBytes;
        }

        /** Packed horizontal-quad vertices in fixed order. / 固定序的水平 quad 顶点字节。 */
        public byte[] quadVertexBytes() {
            return quadVertexBytes;
        }

        /** Total encoded record bytes: 13-byte fixed header + both instance/vertex streams.
         * / 编码总字节数：13 字节固定头 + 两条实例/顶点流。 */
        public int byteSize() {
            return HEADER_BYTES + columnInstanceBytes.length + columnVertexBytes.length
                    + quadInstanceBytes.length + quadVertexBytes.length;
        }

        /**
         * Fixed-order byte encoding: {@code version} int, {@code levelOrdB} byte, then
         * {@code columnEntryCount} int + column instance bytes + column vertex bytes, then
         * {@code quadCount} int + quad instance bytes + quad vertex bytes. Byte-identical for the
         * same instantiated mesh. / 固定序字节编码：{@code version} int、{@code levelOrdB} byte、
         * 然后 {@code columnEntryCount} int + 列实例字节 + 列顶点字节、再 {@code quadCount} int +
         * quad 实例字节 + quad 顶点字节。对同一实例化网格逐字节一致。
         */
        public byte[] toBytes() {
            byte[] out = new byte[byteSize()];
            int i = 0;
            out[i++] = (byte) (VERSION >> 24);
            out[i++] = (byte) (VERSION >> 16);
            out[i++] = (byte) (VERSION >> 8);
            out[i++] = (byte) VERSION;
            out[i++] = (byte) level.ordinal();
            out[i++] = (byte) (columnEntryCount >> 24);
            out[i++] = (byte) (columnEntryCount >> 16);
            out[i++] = (byte) (columnEntryCount >> 8);
            out[i++] = (byte) columnEntryCount;
            System.arraycopy(columnInstanceBytes, 0, out, i, columnInstanceBytes.length);
            i += columnInstanceBytes.length;
            System.arraycopy(columnVertexBytes, 0, out, i, columnVertexBytes.length);
            i += columnVertexBytes.length;
            out[i++] = (byte) (quadCount >> 24);
            out[i++] = (byte) (quadCount >> 16);
            out[i++] = (byte) (quadCount >> 8);
            out[i++] = (byte) quadCount;
            System.arraycopy(quadInstanceBytes, 0, out, i, quadInstanceBytes.length);
            i += quadInstanceBytes.length;
            System.arraycopy(quadVertexBytes, 0, out, i, quadVertexBytes.length);
            return out;
        }

        /** Fixed-order canonical text; byte-identical for the same mesh. / 固定序规范文本；对同一网格逐字节一致。 */
        public String canonicalText() {
            StringBuilder sb = new StringBuilder(128);
            sb.append("mesh version=").append(VERSION).append(" level=").append(level.form())
                    .append(" columnEntries=").append(columnEntryCount).append(" quads=").append(quadCount)
                    .append(" colInstanceBytes=").append(columnInstanceBytes.length)
                    .append(" colVertexBytes=").append(columnVertexBytes.length)
                    .append(" quadInstanceBytes=").append(quadInstanceBytes.length)
                    .append(" quadVertexBytes=").append(quadVertexBytes.length);
            return sb.toString();
        }

        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof InstantiatedMesh m && level == m.level
                    && columnEntryCount == m.columnEntryCount && quadCount == m.quadCount
                    && Arrays.equals(columnInstanceBytes, m.columnInstanceBytes)
                    && Arrays.equals(columnVertexBytes, m.columnVertexBytes)
                    && Arrays.equals(quadInstanceBytes, m.quadInstanceBytes)
                    && Arrays.equals(quadVertexBytes, m.quadVertexBytes));
        }

        @Override
        public int hashCode() {
            int h = 31 * level.hashCode() + columnEntryCount;
            h = 31 * h + quadCount;
            h = 31 * h + Arrays.hashCode(columnInstanceBytes);
            h = 31 * h + Arrays.hashCode(columnVertexBytes);
            h = 31 * h + Arrays.hashCode(quadInstanceBytes);
            return 31 * h + Arrays.hashCode(quadVertexBytes);
        }

        @Override
        public String toString() {
            return canonicalText();
        }
    }
}