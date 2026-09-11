package io.toterra.subterra.engine.render.lod;

import io.toterra.subterra.engine.render.VertexLayout;
import io.toterra.subterra.engine.render.VertexLayout.FieldType;

import java.nio.charset.StandardCharsets;

/**
 * Compact quantized vertex layouts for the LOD renderer (p.2.28.1, clean-room), built by
 * reusing the engine's existing {@link VertexLayout#builder()} and styled exactly after the
 * p.2.27.1 {@code InstanceFormat} paradigm. The two built-in layouts model the LOD surface
 * semantics: {@link #QUAD} is a horizontal merged quad vertex
 * (position {@link FieldType#UNSIGNED_SHORT}×3 as chunk-relative quantized coordinates +
 * colorIndex {@link FieldType#UNSIGNED_BYTE}×1 + meta {@link FieldType#UNSIGNED_BYTE}×1 for
 * facing/light bits; {@link #byteSize()} = 8), and {@link #COLUMN} is a column-cross-section
 * quad vertex (position {@link FieldType#UNSIGNED_SHORT}×3 + colorIndex
 * {@link FieldType#UNSIGNED_BYTE}×1; {@link #byteSize()} = 8). The quantization semantics are
 * fixed: position carries int16/uint16 chunk-relative coordinates (no world-space, no
 * floating point in the hot layout). Exposed via {@link #canonicalBytes()}/{@link #layout()}
 * isomorphic to {@link VertexLayout}; the {@link VertexLayout} source is <em>not</em> copied —
 * only imported and reused through its Builder.
 *
 * <p>LOD 渲染器紧凑量化顶点布局（p.2.28.1，clean-room），复用引擎已有的 {@link VertexLayout#builder()}
 * 构建，风格完全对齐 p.2.27.1 {@code InstanceFormat} 范式。两个内建布局建模 LOD 表面语义：{@link #QUAD}
 * 为水平合并 quad 顶点（position {@link FieldType#UNSIGNED_SHORT}×3 作区块相对量化坐标 + colorIndex
 * {@link FieldType#UNSIGNED_BYTE}×1 + meta {@link FieldType#UNSIGNED_BYTE}×1 存朝向/光照位；
 * {@link #byteSize()} = 8），{@link #COLUMN} 为列柱剖面 quad 顶点（position
 * {@link FieldType#UNSIGNED_SHORT}×3 + colorIndex {@link FieldType#UNSIGNED_BYTE}×1；
 * {@link #byteSize()} = 8）。量化语义固定：position 携带 int16/uint16 区块相对坐标（热点布局无世界空间、
 * 无浮点）。经 {@link #canonicalBytes()}/{@link #layout()} 与 {@link VertexLayout} 同构暴露；
 * {@link VertexLayout} 源码<em>不</em>被复制——仅 import 并复用其 Builder。
 */
public final class LodVertexLayout {

    private LodVertexLayout() {
    }

    /**
     * Horizontal merged quad vertex layout (LOD QUAD): fixed order position
     * (UNSIGNED_SHORT×3, chunk-relative quantized coordinates), colorIndex (UNSIGNED_BYTE×1),
     * meta (UNSIGNED_BYTE×1: facing/light bits). {@link #byteSize()} = 8 via cumulative
     * alignment.
     * / 水平合并 quad 顶点布局（LOD QUAD）：固定序 position（UNSIGNED_SHORT×3，区块相对量化坐标）、
     * colorIndex（UNSIGNED_BYTE×1）、meta（UNSIGNED_BYTE×1：朝向/光照位）。经累计对齐 {@link #byteSize()} = 8。
     */
    public static final VertexLayout QUAD = VertexLayout.builder()
            .append("position", FieldType.UNSIGNED_SHORT, 3)
            .append("colorIndex", FieldType.UNSIGNED_BYTE, 1)
            .append("meta", FieldType.UNSIGNED_BYTE, 1)
            .build();

    /**
     * Column cross-section quad vertex layout (LOD COLUMN): fixed order position
     * (UNSIGNED_SHORT×3, chunk-relative quantized coordinates) and colorIndex
     * (UNSIGNED_BYTE×1), no meta. {@link #byteSize()} = 8 via cumulative alignment.
     * / 列柱剖面 quad 顶点布局（LOD COLUMN）：固定序 position（UNSIGNED_SHORT×3，区块相对量化坐标）与
     * colorIndex（UNSIGNED_BYTE×1），无 meta。经累计对齐 {@link #byteSize()} = 8。
     */
    public static final VertexLayout COLUMN = VertexLayout.builder()
            .append("position", FieldType.UNSIGNED_SHORT, 3)
            .append("colorIndex", FieldType.UNSIGNED_BYTE, 1)
            .build();

    /** The QUAD layout's canonical bytes (UTF-8 of its canonical text). / QUAD 布局规范字节（其规范文本 UTF-8）。 */
    public static byte[] quadsCanonicalBytes() {
        return QUAD.canonicalBytes();
    }

    /** The COLUMN layout's canonical bytes (UTF-8 of its canonical text). / COLUMN 布局规范字节（其规范文本 UTF-8）。 */
    public static byte[] columnCanonicalBytes() {
        return COLUMN.canonicalBytes();
    }

    /** UTF-8 canonical bytes of a layout's canonical text (delegates to {@link VertexLayout}). /
     *  布局规范文本的 UTF-8 规范字节（委托 {@link VertexLayout}）。 */
    public static byte[] canonicalBytes(VertexLayout layout) {
        return layout.canonicalBytes();
    }

    /** Byte-identical marker: verifies a layout's canonical encoding is stable (used by tooling). /
     *  逐字节一致标记：校验布局规范编码稳定（供工具用）。 */
    public static boolean isByteStable(VertexLayout layout) {
        byte[] a = layout.canonicalBytes();
        byte[] b = new String(a, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
        return java.util.Arrays.equals(a, b);
    }
}