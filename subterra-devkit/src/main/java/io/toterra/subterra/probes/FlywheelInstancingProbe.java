// p.2.27.1.2: flywheel instancing deterministic probe — load determinism (InstanceFormat /
// ShaderTemplate byte identity across rebuilds), backend SPI (fixed registration order,
// duplicate-name rejection, lookup hit/miss, deterministic default selection with priority
// and registration-order tie-break), instance format model (scalar/vector/matrix/array
// offset/alignment/byteSize spot checks + cross-field alignment padding), and the
// third-party license inventory (META-INF/third-party/flywheel-1.0.6 LICENSE + NOTICE).
// Pure JVM: no MC runtime, no wall-clock, no timestamps.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.render.instancing.BackendRegistry;
import io.toterra.subterra.engine.render.instancing.InstanceFormat;
import io.toterra.subterra.engine.render.instancing.InstanceFormat.Field;
import io.toterra.subterra.engine.render.instancing.InstanceFormat.ScalarType;
import io.toterra.subterra.engine.render.instancing.RenderBackend;
import io.toterra.subterra.engine.render.instancing.ShaderTemplate;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * p.2.27.1.2 装载与后端确定性探针 —— 把 p.2.27.1.1 的 engine.render.instancing（Flywheel 1.0.6
 * instancing-core 纯 JDK 移植）的确定性契约锚定到四个组：
 * <ol>
 *   <li><b>装载确定性</b>：同一 {@link InstanceFormat} 声明连跑两遍 → {@code canonicalText()}
 *       逐字节一致、{@code canonicalBytes()} 逐字节一致、{@code byteSize()} 相等；同一
 *       {@link ShaderTemplate}（部件拼装 + 占位符替换）连跑两遍 → {@code compile(Map)} 文本与
 *       替换后 {@code compileBytes()} 逐字节一致（同输入同字节）；未替换模板
 *       {@code compile()} 确定性抛 {@link IllegalStateException}（残留占位符禁止编译）。</li>
 *   <li><b>后端 SPI</b>：{@link BackendRegistry} 固定注册序（两遍独立注册 → 顺序逐项相同；追加
 *       注册保持插入序）、同名二次注册与空白名以 {@link IllegalArgumentException} 拒绝、
 *       {@code lookup} 命中/未命中/传 null 三分支、{@code defaultFor} 最高优先级胜出 + 平局按
 *       注册序（先注册者胜）+ 不支持格式被过滤（无一合格 → null；null 格式 = 无约束，全合格）。</li>
 *   <li><b>实例格式模型</b>：固定声明序的多字段样例（标量 U32 / 向量 F32x3 / 矩阵 F32 4x4 /
 *       标量数组 U8x8 / 向量数组 F32x4 x3）的偏移/对齐/byteSize 逐项抽查（期望
 *       byteSize=136、byteAlignment=4）；跨字段对齐补齐样例（U8, U8, F32 → F32 偏移 4 而非 2，
 *       记录 byteSize=8 证明补齐缝隙）；字段未命中返回 null。</li>
 *   <li><b>许可材料盘点</b>：classpath 资源
 *       {@code /META-INF/third-party/flywheel-1.0.6/LICENSE} 存在且含 {@code MIT License} 与
 *       上游版权人 {@code Jozufozu}；{@code NOTICE.md} 存在。</li>
 * </ol>
 * 每项失败计数 +1 并给出明确诊断；全过才输出 {@code [FlywheelInstancingProbe] PASS (n checks)}
 * 并 exit 0，否则 FAIL 计数 exit 1。确定性纪律：固定序、无时序、无随机；全部线性遍历；失败计数
 * 只在失败路径自增。纯 JVM——绝不触碰 Minecraft / OpenGL 类。
 *
 * <p>p.2.27.1.2 load-and-backend determinism probe — anchors the p.2.27.1.1
 * engine.render.instancing (the Flywheel 1.0.6 instancing-core pure-JDK port) determinism
 * contract in four groups:
 * <ol>
 *   <li><b>Load determinism</b>: building the same {@link InstanceFormat} declarations twice →
 *       {@code canonicalText()} byte-identical, {@code canonicalBytes()} byte-identical,
 *       {@code byteSize()} equal; building the same {@link ShaderTemplate} (parts assembly +
 *       placeholder substitution) twice → {@code compile(Map)} text and substituted
 *       {@code compileBytes()} byte-identical (same input, same bytes); an unsubstituted
 *       template's {@code compile()} fails deterministically with {@link IllegalStateException}
 *       (leftover placeholders must not compile).</li>
 *   <li><b>Backend SPI</b>: {@link BackendRegistry} fixed registration order (two independent
 *       passes → order element-wise identical; appending keeps insertion order), duplicate-name
 *       and blank-name registration rejected with {@link IllegalArgumentException},
 *       {@code lookup} hit / miss / null three-way, and {@code defaultFor} highest-priority
 *       wins + tie broken by registration order (earlier registered wins) + unsupported formats
 *       filtered (no eligible backend → null; null format = no constraint, all eligible).</li>
 *   <li><b>Instance format model</b>: fixed-declaration-order multi-field sample
 *       (scalar U32 / vector F32x3 / matrix F32 4x4 / scalar array U8x8 / vector array
 *       F32x4 ×3) offset/alignment/byteSize spot checks (expected byteSize=136,
 *       byteAlignment=4); a cross-field alignment-padding sample (U8, U8, F32 → the F32 starts
 *       at offset 4, not 2, record byteSize=8 proving the padding gap); field miss returns
 *       null.</li>
 *   <li><b>License inventory</b>: the classpath resource
 *       {@code /META-INF/third-party/flywheel-1.0.6/LICENSE} is present and carries
 *       {@code MIT License} and the upstream copyright holder {@code Jozufozu};
 *       {@code NOTICE.md} is present.</li>
 * </ol>
 * Every failure is counted and diagnosed; PASS only when all checks pass, then exit 0, else FAIL
 * with counts and exit 1. Determinism discipline: fixed order, no timing, no randomness; all
 * traversals linear (no O(n²)); failures are counted only on failing paths. Pure JVM — never
 * touches Minecraft / OpenGL classes.
 */
public final class FlywheelInstancingProbe {

    /** 固定声明序的多字段样例格式（偏移由 engine 布局算法确定性算出）。
     *  The fixed multi-field sample format (offsets computed deterministically by the engine
     *  layout algorithm). */
    private static final InstanceFormat SAMPLE_FORMAT = InstanceFormat.builder()
            .scalar("model_index", ScalarType.U32)          // offset 0, bytes 4,  align 4
            .vector("position", ScalarType.F32, 3)          // offset 4, bytes 12, align 4
            .matrix("transform", ScalarType.F32, 4)         // offset 16, bytes 64, align 4
            .scalarArray("tint", ScalarType.U8, 8)          // offset 80, bytes 8,  align 1
            .vectorArray("bones", ScalarType.F32, 4, 3)     // offset 88, bytes 48, align 4
            .build();                                       // byteSize 136, byteAlignment 4

    /** 跨字段对齐补齐样例：U8, U8, F32 → F32 从偏移 4 起（对齐到 4），记录 byteSize=8。
     *  Cross-field alignment-padding sample: U8, U8, F32 → the F32 starts at offset 4 (aligned
     *  up from 2), record byteSize=8. */
    private static final InstanceFormat PADDED_FORMAT = InstanceFormat.builder()
            .scalar("a", ScalarType.U8)                     // offset 0, bytes 1, align 1
            .scalar("b", ScalarType.U8)                     // offset 1, bytes 1, align 1
            .scalar("c", ScalarType.F32)                    // offset 4 (aligned), bytes 4, align 4
            .build();                                       // byteSize 8, byteAlignment 4

    /** 固定序示例着色器模板（含 {@code {{color}}} 占位符）。
     *  Fixed-order sample shader template (with the {@code {{color}}} placeholder). */
    private static final String SHADER_BODY =
            "layout(location = 0) in vec3 position;\n"
                    + "layout(location = 1) in vec4 {{color}};\n"
                    + "void main() { gl_Position = vec4(position, 1.0); fragColor = {{color}}; }\n";

    /** 确定性替换取值（最终 GLSL 片段）。Deterministic substitution values (final GLSL fragments). */
    private static final Map<String, String> SHADER_VALUES =
            Map.of("color", "vec4(1.0, 1.0, 1.0, 1.0)");

    private static int checks = 0;
    private static int failures = 0;

    private FlywheelInstancingProbe() {
    }

    public static void main(String[] args) {
        try {
            loadDeterminism();
            backendSpi();
            formatModel();
            licenseInventory();
        } catch (Throwable t) {
            failures++;
            System.out.println("[FAIL] probe exception: " + t);
            t.printStackTrace(System.out);
        }
        if (failures == 0) {
            System.out.println("[FlywheelInstancingProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[FlywheelInstancingProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    // ---------- (1) load determinism: format + shader byte identity across rebuilds ----------

    private static void loadDeterminism() {
        InstanceFormat f1 = buildSampleFormat();
        InstanceFormat f2 = buildSampleFormat();
        check("format canonicalText byte-identical across two builds",
                f1.canonicalText().equals(f2.canonicalText()));
        check("format canonicalBytes byte-identical across two builds",
                Arrays.equals(f1.canonicalBytes(), f2.canonicalBytes()));
        check("format byteSize deterministic across two builds (" + f1.byteSize() + ")",
                f1.byteSize() == f2.byteSize());

        ShaderTemplate s1 = buildSampleShader();
        ShaderTemplate s2 = buildSampleShader();
        check("shader placeholderNames fixed first-occurrence order [color]",
                s1.placeholderNames().equals(List.of("color")));
        check("shader single-step compile(Map) text byte-identical across two builds",
                s1.compile(SHADER_VALUES).equals(s2.compile(SHADER_VALUES)));
        check("shader substituted compileBytes byte-identical across two builds",
                Arrays.equals(s1.substituteAll(SHADER_VALUES).compileBytes(),
                        s2.substituteAll(SHADER_VALUES).compileBytes()));
        check("shader same-template repeated compile byte-identical",
                Arrays.equals(s1.substituteAll(SHADER_VALUES).compileBytes(),
                        s1.substituteAll(SHADER_VALUES).compileBytes()));
        boolean unresolvedThrows = false;
        try {
            s1.compile();
        } catch (IllegalStateException e) {
            unresolvedThrows = true;
        }
        check("shader compile() with unresolved placeholders throws IllegalStateException",
                unresolvedThrows);
    }

    // ---------- (2) backend SPI: registration order / dup-reject / lookup / default ----------

    private static void backendSpi() {
        RenderBackend instanced = backend("instanced", 10,
                f -> f != null && f.field("model_index") != null);
        RenderBackend indexed = backend("indexed", 10,
                f -> f != null && f.field("tint") != null);
        RenderBackend universal = backend("universal", 20, f -> f != null);

        BackendRegistry.clear();
        BackendRegistry.register(instanced);
        BackendRegistry.register(indexed);
        BackendRegistry.register(universal);
        List<String> firstPass = BackendRegistry.names();

        BackendRegistry.clear();
        BackendRegistry.register(instanced);
        BackendRegistry.register(indexed);
        BackendRegistry.register(universal);
        List<String> secondPass = BackendRegistry.names();
        check("backend registry fixed registration order — two independent passes identical",
                firstPass.equals(secondPass));

        BackendRegistry.register(backend("extra", 5, f -> f != null));
        check("backend registry appending keeps insertion order",
                BackendRegistry.names().equals(List.of("instanced", "indexed", "universal", "extra")));

        boolean dupRejected = false;
        try {
            BackendRegistry.register(instanced);
        } catch (IllegalArgumentException e) {
            dupRejected = true;
        }
        check("backend registry duplicate name rejected with IllegalArgumentException", dupRejected);

        boolean blankRejected = false;
        try {
            BackendRegistry.register(backend("", 0, f -> f != null));
        } catch (IllegalArgumentException e) {
            blankRejected = true;
        }
        check("backend registry blank name rejected with IllegalArgumentException", blankRejected);

        check("backend registry lookup hit returns the registered backend",
                BackendRegistry.lookup("indexed") == indexed);
        check("backend registry lookup miss returns null",
                BackendRegistry.lookup("nope") == null);
        check("backend registry lookup(null) returns null",
                BackendRegistry.lookup(null) == null);

        // Highest priority wins among supported backends (universal=20 > instanced/indexed=10).
        BackendRegistry.clear();
        BackendRegistry.register(instanced);
        BackendRegistry.register(indexed);
        BackendRegistry.register(universal);
        check("backend registry defaultFor picks the highest-priority supported backend",
                BackendRegistry.defaultFor(SAMPLE_FORMAT) == universal);

        // Tie (both priority 10, both support the sample) broken by registration order.
        BackendRegistry.clear();
        BackendRegistry.register(indexed);
        BackendRegistry.register(instanced);
        check("backend registry defaultFor tie broken by registration order (earlier wins)",
                BackendRegistry.defaultFor(SAMPLE_FORMAT) == indexed);

        // No eligible backend for the padded format (no model_index / tint fields) -> null.
        check("backend registry defaultFor returns null when nothing is supported",
                BackendRegistry.defaultFor(PADDED_FORMAT) == null);
        // A null format is "no constraint" — every backend eligible, first registered wins.
        check("backend registry defaultFor(null) treats null format as no constraint",
                BackendRegistry.defaultFor(null) == indexed);

        BackendRegistry.clear(); // probe isolation
    }

    // ---------- (3) instance format model: offset / alignment / byteSize spot checks ----------

    private static void formatModel() {
        Field modelIndex = SAMPLE_FORMAT.field("model_index");
        Field position = SAMPLE_FORMAT.field("position");
        Field transform = SAMPLE_FORMAT.field("transform");
        Field tint = SAMPLE_FORMAT.field("tint");
        Field bones = SAMPLE_FORMAT.field("bones");

        check("sample format byteSize = 136 (actual " + SAMPLE_FORMAT.byteSize() + ")",
                SAMPLE_FORMAT.byteSize() == 136);
        check("sample format byteAlignment = 4 (actual " + SAMPLE_FORMAT.byteAlignment() + ")",
                SAMPLE_FORMAT.byteAlignment() == 4);
        check("sample format layout order == declaration order (5 fields)",
                SAMPLE_FORMAT.layout().equals(SAMPLE_FORMAT.fields())
                        && SAMPLE_FORMAT.fields().size() == 5);

        check("scalar U32 'model_index' at offset 0 / bytes 4 / align 4",
                modelIndex != null && modelIndex.kind() == InstanceFormat.ElementKind.SCALAR
                        && modelIndex.byteOffset() == 0 && modelIndex.byteSize() == 4
                        && modelIndex.alignment() == 4);
        check("vector F32x3 'position' at offset 4 / bytes 12 / align 4",
                position != null && position.kind() == InstanceFormat.ElementKind.VECTOR
                        && position.vectorSize() == 3 && position.byteOffset() == 4
                        && position.byteSize() == 12 && position.alignment() == 4);
        check("matrix F32 4x4 'transform' at offset 16 / bytes 64 / align 4",
                transform != null && transform.kind() == InstanceFormat.ElementKind.MATRIX
                        && transform.rows() == 4 && transform.columns() == 4
                        && transform.byteOffset() == 16 && transform.byteSize() == 64
                        && transform.alignment() == 4);
        check("scalar array U8x8 'tint' at offset 80 / bytes 8 / align 1",
                tint != null && tint.kind() == InstanceFormat.ElementKind.ARRAY
                        && tint.arrayElementKind() == InstanceFormat.ElementKind.SCALAR
                        && tint.arrayLength() == 8 && tint.byteOffset() == 80
                        && tint.byteSize() == 8 && tint.alignment() == 1);
        check("vector array F32x4 x3 'bones' at offset 88 / bytes 48 / align 4",
                bones != null && bones.kind() == InstanceFormat.ElementKind.ARRAY
                        && bones.arrayElementKind() == InstanceFormat.ElementKind.VECTOR
                        && bones.vectorSize() == 4 && bones.arrayLength() == 3
                        && bones.byteOffset() == 88 && bones.byteSize() == 48
                        && bones.alignment() == 4);

        Field a = PADDED_FORMAT.field("a");
        Field b = PADDED_FORMAT.field("b");
        Field c = PADDED_FORMAT.field("c");
        check("padding sample: a at 0, b at 1, c (F32) aligned up to offset 4",
                a != null && a.byteOffset() == 0 && b != null && b.byteOffset() == 1
                        && c != null && c.byteOffset() == 4);
        check("padding sample: record byteSize 8 / byteAlignment 4 proves the padding gap",
                PADDED_FORMAT.byteSize() == 8 && PADDED_FORMAT.byteAlignment() == 4);

        check("field miss returns null", SAMPLE_FORMAT.field("nope") == null);
    }

    // ---------- (4) third-party license inventory (classpath resources) ----------

    private static void licenseInventory() {
        String license = readResource("/META-INF/third-party/flywheel-1.0.6/LICENSE");
        check("flywheel-1.0.6 LICENSE classpath resource present", license != null);
        if (license != null) {
            check("flywheel-1.0.6 LICENSE carries the 'MIT License' text",
                    license.contains("MIT License"));
            check("flywheel-1.0.6 LICENSE preserves upstream copyright holder 'Jozufozu'",
                    license.contains("Jozufozu"));
        }
        String notice = readResource("/META-INF/third-party/flywheel-1.0.6/NOTICE.md");
        check("flywheel-1.0.6 NOTICE.md classpath resource present",
                notice != null && !notice.isBlank());
    }

    // ---------- fixtures ----------

    /** 重新构建固定样例格式（同声明）。Rebuilds the fixed sample format (same declarations). */
    private static InstanceFormat buildSampleFormat() {
        return InstanceFormat.builder()
                .scalar("model_index", ScalarType.U32)
                .vector("position", ScalarType.F32, 3)
                .matrix("transform", ScalarType.F32, 4)
                .scalarArray("tint", ScalarType.U8, 8)
                .vectorArray("bones", ScalarType.F32, 4, 3)
                .build();
    }

    /** 重新构建固定样例着色器模板（同部件）。Rebuilds the fixed sample shader template (same parts). */
    private static ShaderTemplate buildSampleShader() {
        return ShaderTemplate.fromParts(
                List.of("#version 450",
                        "#extension GL_ARB_bindless_texture : require"),
                SHADER_BODY,
                List.of("// end of sample vertex stage"));
    }

    /** 确定性测试后端：固定 name/priority 与支持谓词。Deterministic test backend: fixed
     * name/priority and a support predicate. */
    private static RenderBackend backend(String name, int priority,
                                         Predicate<InstanceFormat> supported) {
        return new RenderBackend() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean supports(InstanceFormat format) {
                return supported.test(format);
            }

            @Override
            public String description() {
                return "deterministic probe backend " + name;
            }

            @Override
            public int priority() {
                return priority;
            }
        };
    }

    /** 读取 classpath 资源（缺失/异常 → null）。Reads a classpath resource (null on missing/error). */
    private static String readResource(String path) {
        try (InputStream in = FlywheelInstancingProbe.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- helpers ----------

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }
}
