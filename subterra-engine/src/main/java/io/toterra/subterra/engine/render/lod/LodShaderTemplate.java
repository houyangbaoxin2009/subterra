package io.toterra.subterra.engine.render.lod;

import io.toterra.subterra.engine.render.instancing.ShaderTemplate;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Clean-room fixed-order LOD shader templates (p.2.28.4, self-developed): the vertex and
 * fragment GLSL skeletons for the LOD renderer, assembled entirely through the p.2.27.1
 * {@link ShaderTemplate} mechanism (<em>imported and re-used, never copied or re-invented</em>).
 * {@link ShaderTemplate#fromParts(List, String, List)} does fixed-order parts assembly and
 * {@link ShaderTemplate#substituteAll(Map)} does fixed-order placeholder substitution, so the
 * produced source is byte-identical for the same inputs (same template + same values &rarr; same
 * bytes). The LOD-specific semantics the templates encode: quantized-coordinate decode
 * (uint16/uint16/uint16 position scaled by {@code {{QUANT_SCALE}}}), color
 * index&rarr;palette-index sampling, distance fog (a linear blend over {@code {{FOG_START}}..
 * {@code {{FOG_END}}}), and the seam {@code stitch} bit carried in the vertex {@code meta} low nibble.
 *
 * <p><b>Clean-room stance:</b> the GLSL below is original Subterra code written to the LOD task
 * shape. No third-party shader source is included — in particular zero Distant Horizons
 * (LGPL-3.0, assessed reference-only) and zero vanilla/Mojang/Bedrock shader code. The
 * constructs used (vertex/fragment stages, {@code attribute}/{@code varying}/{@code uniform},
 * {@code texture2D} palette lookup, linear fog blend) are public, common GLSL knowledge.
 *
 * <p>The default substitution values are pinned as {@link #DEFAULTS}; {@link #vertexSource()},
 * {@link #vertexSourceBytes()}, {@link #fragmentSource()}, {@link #fragmentSourceBytes()} return
 * the canonical compiled sources (byte-identical on every call). Deterministic: no timing, no
 * randomness, no environment dependence.
 *
 * <p>Clean-room 固定序 LOD 着色器模板（p.2.28.4，自研）：LOD 渲染器的顶点/片元 GLSL 骨架，完全经 p.2.27.1
 * {@link ShaderTemplate} 机制拼装（<em>import 并复用，绝不复制或重造</em>）。{@link ShaderTemplate#fromParts(List, String, List)}
 * 做固定序部件拼装、{@link ShaderTemplate#substituteAll(Map)} 做固定序占位符替换，故对相同输入产物逐字节一致
 * （同模板+同取值&rarr;同字节）。模板编码的 LOD 特有语义：量化坐标解码（uint16/uint16/uint16 position 乘以
 * {@code {{QUANT_SCALE}}}）、颜色索引&rarr;调色板索引采样、距离雾（在 {@code {{FOG_START}}..{@code {{FOG_END}}}} 上的
 * 线性混合）、以及顶点 {@code meta} 低半字节携带的接缝 {@code stitch} 位。
 *
 * <p><b>clean-room 立场：</b>下方 GLSL 为按 LOD 任务形态原创的 Subterra 代码。不包含任何第三方着色器源码——
 * 尤为重要的是零 Distant Horizons（LGPL-3.0，仅评估为参考）与零原版/Mojang/Bedrock 着色器代码。所用语言构造
 * （vertex/fragment 阶段、{@code attribute}/{@code varying}/{@code uniform}、{@code texture2D} 调色板查找、
 * 线性雾混合）均为公开的通用 GLSL 常识。
 *
 * <p>缺省替换取值被钉死为 {@link #DEFAULTS}；{@link #vertexSource()}、{@link #vertexSourceBytes()}、
 * {@link #fragmentSource()}、{@link #fragmentSourceBytes()} 返回规范编译源码（每次调用逐字节一致）。
 * 确定性：无时序、无随机、无环境依赖。
 */
public final class LodShaderTemplate {

    /** Palette sampler's declared size (texels). / 调色板采样器声明的纹素数。 */
    public static final String PALETTE_SIZE = "{{PALETTE_SIZE}}";
    /** Quantized-position decode scale (world units per quantized step). / 量化坐标解码缩放（每量化步的世界单位）。 */
    public static final String QUANT_SCALE = "{{QUANT_SCALE}}";
    /** Near fog distance (world units). / 近端雾距离（世界单位）。 */
    public static final String FOG_START = "{{FOG_START}}";
    /** Far fog distance (world units). / 远端雾距离（世界单位）。 */
    public static final String FOG_END = "{{FOG_END}}";

    /**
     * Pinned default substitution values (deterministic golden values, aligned with
     * {@link LodConfigDoc#DEFAULT_DISTANCES} fog range 64..512): {@code PALETTE_SIZE=256},
     * {@code QUANT_SCALE=1.0}, {@code FOG_START=64.0}, {@code FOG_END=512.0}. Same values &rarr;
     * same bytes. / 钉死的缺省替换取值（确定性黄金值，对齐 {@link LodConfigDoc#DEFAULT_DISTANCES} 雾程
     * 64..512）：{@code PALETTE_SIZE=256}、{@code QUANT_SCALE=1.0}、{@code FOG_START=64.0}、
     * {@code FOG_END=512.0}。同取值&rarr;同字节。
     */
    public static final Map<String, String> DEFAULTS = immutableDefaults();

    private static final ShaderTemplate VERTEX_TPL = ShaderTemplate.fromParts(
            List.of(
                    "#version 120",
                    "attribute vec3 a_position;",
                    "attribute float a_colorIndex;",
                    "attribute float a_meta;",
                    "uniform mat4 u_modelViewProj;"),
            // body: quantized decode + palette color index pass-through + distance fog + seam stitch bit
            "varying float v_colorIndex;\n"
                    + "varying float v_fog;\n"
                    + "varying float v_seam;\n"
                    + "void main() {\n"
                    + "    vec3 pos = a_position * " + QUANT_SCALE + ";\n"
                    + "    vec4 view = u_modelViewProj * vec4(pos, 1.0);\n"
                    + "    gl_Position = view;\n"
                    + "    float dist = length(view.xyz);\n"
                    + "    v_fog = clamp((dist - " + FOG_START + ") / (" + FOG_END + " - " + FOG_START + "), 0.0, 1.0);\n"
                    + "    v_colorIndex = a_colorIndex;\n"
                    + "    v_seam = mod(a_meta, 16.0);\n"
                    + "}",
            List.of());

    private static final ShaderTemplate FRAGMENT_TPL = ShaderTemplate.fromParts(
            List.of(
                    "#version 120",
                    "uniform sampler2D u_palette;",
                    "uniform vec4 u_fogColor;",
                    "varying float v_colorIndex;",
                    "varying float v_fog;",
                    "varying float v_seam;"),
            // body: color index -> palette texel + monotone fog blend (stitch bit consumed by mesh simplifier at draw time)
            "vec4 paletteTexel(float idx) {\n"
                    + "    return texture2D(u_palette, vec2((idx + 0.5) / " + PALETTE_SIZE + ", 0.5));\n"
                    + "}\n"
                    + "void main() {\n"
                    + "    vec4 c = paletteTexel(v_colorIndex);\n"
                    + "    gl_FragColor = mix(c, u_fogColor, v_fog);\n"
                    + "}",
            List.of());

    private LodShaderTemplate() {
    }

    /** The fixed vertex {@link ShaderTemplate} pre-substitution. / 固定顶点 {@link ShaderTemplate}（预替换）。 */
    public static ShaderTemplate vertexTemplate() {
        return VERTEX_TPL;
    }

    /** The fixed fragment {@link ShaderTemplate} pre-substitution. / 固定片元 {@link ShaderTemplate}（预替换）。 */
    public static ShaderTemplate fragmentTemplate() {
        return FRAGMENT_TPL;
    }

    /**
     * Canonical vertex GLSL with the pinned default values substituted (byte-identical).
     * / 以钉死缺省取值替换后的规范顶点 GLSL（逐字节一致）。
     */
    public static String vertexSource() {
        return VERTEX_TPL.compile(DEFAULTS);
    }

    /**
     * Canonical vertex GLSL with explicit value overrides (fixed-order substitution; missing keys
     * fall back to {@link #DEFAULTS}). Same inputs &rarr; same bytes. / 以显式覆盖取值替换后的规范顶点
     * GLSL（固定序替换；缺键回落到 {@link #DEFAULTS}）。同输入&rarr;同字节。
     */
    public static String vertexSource(Map<String, String> overrides) {
        return VERTEX_TPL.compile(mergeDefaults(overrides));
    }

    /** UTF-8 bytes of {@link #vertexSource()}. / {@link #vertexSource()} 的 UTF-8 字节。 */
    public static byte[] vertexSourceBytes() {
        return vertexSource().getBytes(StandardCharsets.UTF_8);
    }

    /** Same as {@link #vertexSourceBytes()}: the LOD vertex baseline, byte-identical. / 同
     * {@link #vertexSourceBytes()}：LOD 顶点基线，逐字节一致。 */
    public static byte[] vertexCanonicalBytes() {
        return vertexSourceBytes();
    }

    /** Canonical fragment GLSL with the pinned default values substituted (byte-identical). / 以钉死缺省值
     * 替换后的规范片元 GLSL（逐字节一致）。 */
    public static String fragmentSource() {
        return FRAGMENT_TPL.compile(DEFAULTS);
    }

    /** UTF-8 bytes of {@link #fragmentSource()}. / {@link #fragmentSource()} 的 UTF-8 字节。 */
    public static byte[] fragmentSourceBytes() {
        return fragmentSource().getBytes(StandardCharsets.UTF_8);
    }

    /** Same as {@link #fragmentSourceBytes()}: the LOD fragment baseline, byte-identical. / 同
     * {@link #fragmentSourceBytes()}：LOD 片元基线，逐字节一致。 */
    public static byte[] fragmentCanonicalBytes() {
        return fragmentSourceBytes();
    }

    private static Map<String, String> mergeDefaults(Map<String, String> overrides) {
        Map<String, String> merged = new LinkedHashMap<>(DEFAULTS);
        if (overrides != null) {
            merged.putAll(overrides);
        }
        return merged;
    }

    private static Map<String, String> immutableDefaults() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("PALETTE_SIZE", "256");
        m.put("QUANT_SCALE", "1.0");
        m.put("FOG_START", "64.0");
        m.put("FOG_END", "512.0");
        return Map.copyOf(m);
    }
}