// p.2.27.3: render-fidelity deterministic probe — vanilla-fidelity render model determinism
// (RenderStage fixed values() order / form() round-trip / transparent+sortOnCpu semantics,
// MaterialSurface canonical form bytes, VertexLayout.BLOCK 32B + ENTITY 28B byte size / field
// order / offsets / alignment + canonicalBytes re-run identity), instancing-base complementarity
// (InstanceFormat rebuild of VertexLayout.BLOCK / ENTITY byte-identical sizes and per-field
// offsets, ShaderTemplate assembly + placeholder substitution byte identity, BackendRegistry
// registration order / duplicate rejection / deterministic default), and the three-hook wiring
// contract as a pure-JVM static inventory (RenderHooks.serverWiringCheck / RenderHooksClient
// class presence, RenderRuntime gate property + hooks-ok marker literals via load-only class
// loading and constant-pool byte scans). Pure JVM: no MC runtime, no timing, no randomness.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.render.RenderStage;
import io.toterra.subterra.engine.render.MaterialSurface;
import io.toterra.subterra.engine.render.VertexLayout;
import io.toterra.subterra.engine.render.VertexLayout.FieldType;
import io.toterra.subterra.engine.render.VertexLayout.VertexField;
import io.toterra.subterra.engine.render.instancing.BackendRegistry;
import io.toterra.subterra.engine.render.instancing.InstanceFormat;
import io.toterra.subterra.engine.render.instancing.InstanceFormat.ScalarType;
import io.toterra.subterra.engine.render.instancing.RenderBackend;
import io.toterra.subterra.engine.render.instancing.ShaderTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * p.2.27.3 渲染确定性/接线探针 —— 把 p.2.27.1（engine.render 原版保真模型）与 p.2.27.1.1
 * （engine.render.instancing 实例化基座）的确定性契约锚定到三组断言：
 * <ol>
 *   <li><b>原版保真模型确定性</b>：{@link RenderStage} 固定 {@code values()} 序（10 阶段，p.2.27.1
 *       权威 form 序）+ {@code form()} 经 {@code fromForm()} 全量往返 + 未知文本返回 null +
 *       {@code transparent()} / {@code sortOnCpu()} 语义抽查（SOLID 不透明不排序、TRANSLUCENT 混合
 *       且 CPU 排序、BEACON_BEAM / LIGHTNING / CLOUDS 混合不排序、WATER_MASK 不透明不排序、
 *       TRANSLUCENT_MOVING_BLOCK 混合且排序）；{@link MaterialSurface#form()} 规范串
 *       {@code stage/buffer/texture} 逐字节断言（构造样例 + 期望串比较 + 相同组件逐字节一致 +
 *       确定性拒绝：null 阶段 NPE、空白纹理层/缓冲种类 IAE）；{@link VertexLayout#BLOCK}（32B）与
 *       {@link VertexLayout#ENTITY}（28B）字节大小/字段序/偏移/对齐逐项断言（BLOCK 固定序
 *       position/color/uv/light/overlay/normal，偏移 0/12/16/20/24/28，normal 尾对齐补齐到 32 的
 *       证明）+ {@code canonicalBytes()} 同实例连跑两遍一致 + 同声明重建后 {@code equals()} 与
 *       字节一致 + 重名字段确定性拒绝。</li>
 *   <li><b>实例化基座互补</b>：用 {@link InstanceFormat} 重建 {@link VertexLayout#BLOCK} 同语义布局
 *       （F32x3 / U8x4 / U16x2 / U16x2 / U16x2 / U8x3）→ 32B、对齐 4、逐字段偏移/大小与 BLOCK 一致
 *       （互补验证）；ENTITY 重建 → 28B；{@link ShaderTemplate} 部件拼装 + 占位符替换逐字节一致
 *       （期望源文本精确比较 + 两遍构建一致 + 未替换编译 ISE + 缺失取值 IAE）；{@link BackendRegistry}
 *       注册序/同名重复拒/确定性缺省（最高优先级胜出 + 平局按注册序先注册者胜，样例两后端）。</li>
 *   <li><b>三钩子接线契约（静态盘点）</b>：runtime.render 的 {@code RenderHooks}（
 *       {@code serverWiringCheck} 方法名经 class 常量池字节扫描 + 类存在性）与 {@code RenderHooksClient}
 *       （类存在性）；{@code RenderRuntime} 字节含门控属性串 {@code subterra.probe.render} 与对
 *       {@code RenderHooks} / {@code serverWiringCheck} 的常量池引用，{@code hooks ok} marker 字面量
 *       锚定在真正编译它的 {@code RenderHooks} 字节（由 RenderRuntime 接线调用）。真实 hook 注入由
 *       E2E（AsyncE2EProbe hooksOk）覆盖，本探针只做纯 JVM 静态契约——runtime 类一律只加载不初始化
 *       （{@code Class.forName(fqcn, false)}，避免解析 MC classpath）。</li>
 * </ol>
 * 每项失败计数 +1 并给出明确诊断；全过才输出 {@code [RenderFidelityProbe] PASS (n checks)} 并
 * exit 0，否则 FAIL 计数 exit 1。确定性纪律：固定序、无时序、无随机；全部线性遍历、禁 O(n²)；
 * 失败计数只在失败路径自增。纯 JVM——绝不初始化任何 runtime 壳、绝不触碰 Minecraft / OpenGL 类。
 *
 * <p>p.2.27.3 render-fidelity / wiring probe — anchors the p.2.27.1 (engine.render
 * vanilla-fidelity model) and p.2.27.1.1 (engine.render.instancing base) determinism contracts
 * in three groups:
 * <ol>
 *   <li><b>Vanilla-fidelity model determinism</b>: {@link RenderStage} fixed {@code values()}
 *       order (10 stages, the p.2.27.1 authoritative form order) + full {@code form()} round-trip
 *       via {@code fromForm()} + unknown text returns null + {@code transparent()} /
 *       {@code sortOnCpu()} semantic spot checks (SOLID opaque/unsorted, TRANSLUCENT blends and
 *       sorts on CPU, BEACON_BEAM / LIGHTNING / CLOUDS blend but stay unsorted, WATER_MASK
 *       opaque/unsorted, TRANSLUCENT_MOVING_BLOCK blends and sorts); {@link MaterialSurface#form()}
 *       canonical {@code stage/buffer/texture} string asserted byte-for-byte (constructed samples
 *       vs expected strings, same components byte-identical, deterministic rejection: null stage
 *       NPE, blank texture layer / buffer kind IAE); {@link VertexLayout#BLOCK} (32B) and
 *       {@link VertexLayout#ENTITY} (28B) byte size / field order / offsets / alignment asserted
 *       field-by-field (BLOCK fixed order position/color/uv/light/overlay/normal at offsets
 *       0/12/16/20/24/28, with the trailing normal-alignment pad to 32 proven) +
 *       {@code canonicalBytes()} re-run twice identical on the same instance + identical
 *       declarations rebuild to {@code equals()} and byte-identical canonical bytes + duplicate
 *       field name rejected deterministically.</li>
 *   <li><b>Instancing-base complementarity</b>: {@link InstanceFormat} rebuild of
 *       {@link VertexLayout#BLOCK} with the same semantics (F32x3 / U8x4 / U16x2 / U16x2 / U16x2 /
 *       U8x3) → 32B, alignment 4, per-field offsets/sizes identical to BLOCK (complementary
 *       validation); the ENTITY rebuild → 28B; {@link ShaderTemplate} parts assembly + placeholder
 *       substitution byte-identical (exact expected-source comparison + two-build identity +
 *       unsubstituted compile ISE + missing value IAE); {@link BackendRegistry} registration
 *       order / duplicate-name rejection / deterministic default (highest priority wins + tie
 *       broken by registration order, a two-backend sample).</li>
 *   <li><b>Three-hook wiring contract (static inventory)</b>: runtime.render's {@code RenderHooks}
 *       (the {@code serverWiringCheck} method name via a class constant-pool byte scan + class
 *       presence) and {@code RenderHooksClient} (class presence); {@code RenderRuntime} bytes carry
 *       the {@code subterra.probe.render} gate string and constant-pool references to
 *       {@code RenderHooks} / {@code serverWiringCheck}, while the {@code hooks ok} marker literal
 *       is anchored in {@code RenderHooks} bytes (where it is actually compiled, wired by
 *       RenderRuntime). The real hook injection is covered by the E2E (AsyncE2EProbe hooksOk); this
 *       probe is the pure-JVM static contract only — every runtime class is loaded without
 *       initialization ({@code Class.forName(fqcn, false)}, never resolving the MC classpath).</li>
 * </ol>
 * Every failure is counted and diagnosed; PASS only when all checks pass, then exit 0, else FAIL
 * with counts and exit 1. Determinism discipline: fixed order, no timing, no randomness; all
 * traversals linear (no O(n²)); failures are counted only on failing paths. Pure JVM — no runtime
 * shell is ever initialized, no Minecraft / OpenGL class is touched.
 */
public final class RenderFidelityProbe {

    /** 固定部件拼装的示例着色器主体（含 {@code {{color}}} 占位符，尾部换行）。
     *  The fixed sample shader body (with the {@code {{color}}} placeholder, trailing newline). */
    private static final String SHADER_BODY =
            "layout(location = 0) in vec3 position;\n"
                    + "layout(location = 1) in vec4 {{color}};\n"
                    + "void main() { gl_Position = vec4(position, 1.0); fragColor = {{color}}; }\n";

    /** 确定性替换取值（最终 GLSL 片段）。Deterministic substitution values (final GLSL fragments). */
    private static final Map<String, String> SHADER_VALUES =
            Map.of("color", "vec4(1.0, 1.0, 1.0, 1.0)");

    /** p.2.27.1 权威 10 阶段 form 序。The p.2.27.1 authoritative 10-stage form order. */
    private static final String[] EXPECTED_STAGE_FORMS = {
            "solid", "cutout", "cutout_mipped", "translucent", "tripwire",
            "beacon_beam", "clouds", "lightning", "water_mask", "translucent_moving_block"};

    private static int checks = 0;
    private static int failures = 0;

    private RenderFidelityProbe() {
    }

    public static void main(String[] args) {
        try {
            vanillaFidelityModel();
            instancingComplementarity();
            hookWiringContract();
        } catch (Throwable t) {
            failures++;
            System.out.println("[FAIL] probe exception: " + t);
            t.printStackTrace(System.out);
        }
        if (failures == 0) {
            System.out.println("[RenderFidelityProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[RenderFidelityProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    // ---------- (1) vanilla-fidelity model determinism: RenderStage / MaterialSurface / VertexLayout ----------

    private static void vanillaFidelityModel() {
        // RenderStage: fixed values() order (10 stages, p.2.27.1 authoritative form order).
        RenderStage[] stages = RenderStage.values();
        check("RenderStage values() count = 10 (actual " + stages.length + ")",
                stages.length == EXPECTED_STAGE_FORMS.length);
        boolean orderOk = stages.length == EXPECTED_STAGE_FORMS.length;
        if (orderOk) {
            for (int i = 0; i < stages.length; i++) {
                if (!stages[i].form().equals(EXPECTED_STAGE_FORMS[i])) {
                    orderOk = false;
                    break;
                }
            }
        }
        check("RenderStage fixed values() form order [solid..translucent_moving_block]", orderOk);

        // form() round-trip via fromForm() for every stage + unknown rejection.
        boolean roundTrip = true;
        for (RenderStage s : stages) {
            if (RenderStage.fromForm(s.form()) != s) {
                roundTrip = false;
                break;
            }
        }
        check("RenderStage form() round-trip via fromForm() for all 10 stages", roundTrip);
        check("RenderStage fromForm() unknown text returns null",
                RenderStage.fromForm("nope") == null);

        // transparent / sortOnCpu semantic spot checks (vanilla 1.21.1 semantics).
        check("RenderStage semantic spot: SOLID opaque and unsorted",
                !RenderStage.SOLID.transparent() && !RenderStage.SOLID.sortOnCpu());
        check("RenderStage semantic spot: TRANSLUCENT blends and sorts on CPU",
                RenderStage.TRANSLUCENT.transparent() && RenderStage.TRANSLUCENT.sortOnCpu());
        check("RenderStage semantic spot: BEACON_BEAM blends but is unsorted",
                RenderStage.BEACON_BEAM.transparent() && !RenderStage.BEACON_BEAM.sortOnCpu());
        check("RenderStage semantic spot: LIGHTNING blends but is unsorted",
                RenderStage.LIGHTNING.transparent() && !RenderStage.LIGHTNING.sortOnCpu());
        check("RenderStage semantic spot: CLOUDS blends but is unsorted",
                RenderStage.CLOUDS.transparent() && !RenderStage.CLOUDS.sortOnCpu());
        check("RenderStage semantic spot: WATER_MASK opaque and unsorted",
                !RenderStage.WATER_MASK.transparent() && !RenderStage.WATER_MASK.sortOnCpu());
        check("RenderStage semantic spot: TRANSLUCENT_MOVING_BLOCK blends and sorts on CPU",
                RenderStage.TRANSLUCENT_MOVING_BLOCK.transparent()
                        && RenderStage.TRANSLUCENT_MOVING_BLOCK.sortOnCpu());

        // MaterialSurface: canonical fixed-order form stage/buffer/texture, byte-identical.
        MaterialSurface terrain = new MaterialSurface(RenderStage.TRANSLUCENT, "terrain", "geometry");
        String expectedTerrain = "translucent/geometry/terrain";
        check("MaterialSurface form() canonical 'translucent/geometry/terrain' (stage/buffer/texture)",
                terrain.form().equals(expectedTerrain));
        check("MaterialSurface formBytes() byte-identical to expected UTF-8",
                Arrays.equals(terrain.formBytes(), expectedTerrain.getBytes(StandardCharsets.UTF_8)));
        MaterialSurface solid = new MaterialSurface(RenderStage.SOLID, "atlas/terrain", "entity");
        String expectedSolid = "solid/entity/atlas/terrain";
        check("MaterialSurface form() canonical 'solid/entity/atlas/terrain' (stage/buffer/texture)",
                solid.form().equals(expectedSolid));
        check("MaterialSurface same components -> byte-identical formBytes()",
                Arrays.equals(terrain.formBytes(),
                        new MaterialSurface(RenderStage.TRANSLUCENT, "terrain", "geometry").formBytes()));
        boolean nullStageRejected = false;
        try {
            new MaterialSurface(null, "terrain", "geometry");
        } catch (NullPointerException e) {
            nullStageRejected = true;
        }
        check("MaterialSurface null stage rejected with NullPointerException", nullStageRejected);
        boolean blankTextureRejected = false;
        try {
            new MaterialSurface(RenderStage.SOLID, "  ", "geometry");
        } catch (IllegalArgumentException e) {
            blankTextureRejected = true;
        }
        check("MaterialSurface blank texture layer rejected with IllegalArgumentException",
                blankTextureRejected);
        boolean blankBufferRejected = false;
        try {
            new MaterialSurface(RenderStage.SOLID, "terrain", "");
        } catch (IllegalArgumentException e) {
            blankBufferRejected = true;
        }
        check("MaterialSurface blank buffer kind rejected with IllegalArgumentException",
                blankBufferRejected);

        // VertexLayout: BLOCK 32B / ENTITY 28B + canonical determinism.
        blockLayoutChecks();
        entityLayoutChecks();
        layoutDeterminismChecks();
    }

    /** BLOCK 布局：32B、字段序/偏移/类型/对齐逐项断言 + 尾对齐补齐证明。
     *  BLOCK layout: 32B, per-field order/offset/type/alignment + the trailing pad proof. */
    private static void blockLayoutChecks() {
        VertexLayout block = VertexLayout.BLOCK;
        check("VertexLayout.BLOCK byteSize = 32 (actual " + block.byteSize() + ")",
                block.byteSize() == 32);
        check("VertexLayout.BLOCK byteAlignment = 4 (actual " + block.byteAlignment() + ")",
                block.byteAlignment() == 4);
        String[] names = {"position", "color", "uv", "light", "overlay", "normal"};
        int[] offsets = {0, 12, 16, 20, 24, 28};
        boolean orderOk = block.fields().size() == names.length;
        if (orderOk) {
            for (int i = 0; i < names.length; i++) {
                VertexField f = block.fields().get(i);
                if (!f.name().equals(names[i]) || f.byteOffset() != offsets[i]) {
                    orderOk = false;
                    break;
                }
            }
        }
        check("VertexLayout.BLOCK fixed field order + offsets "
                + "position@0/color@12/uv@16/light@20/overlay@24/normal@28", orderOk);
        check("VertexLayout.BLOCK position FLOATx3 align 4 at offset 0",
                fieldIs(block, "position", FieldType.FLOAT, 3, 0, 4));
        check("VertexLayout.BLOCK color UNSIGNED_BYTEx4 align 1 at offset 12",
                fieldIs(block, "color", FieldType.UNSIGNED_BYTE, 4, 12, 1));
        check("VertexLayout.BLOCK uv UNSIGNED_SHORTx2 align 2 at offset 16",
                fieldIs(block, "uv", FieldType.UNSIGNED_SHORT, 2, 16, 2));
        check("VertexLayout.BLOCK light UNSIGNED_SHORTx2 align 2 at offset 20",
                fieldIs(block, "light", FieldType.UNSIGNED_SHORT, 2, 20, 2));
        check("VertexLayout.BLOCK overlay UNSIGNED_SHORTx2 align 2 at offset 24",
                fieldIs(block, "overlay", FieldType.UNSIGNED_SHORT, 2, 24, 2));
        check("VertexLayout.BLOCK normal UNSIGNED_BYTEx3 align 1 at offset 28",
                fieldIs(block, "normal", FieldType.UNSIGNED_BYTE, 3, 28, 1));
        VertexField normal = block.field("normal");
        check("VertexLayout.BLOCK trailing pad proof: normal ends at 31, byteSize rounds up to 32",
                normal != null && normal.byteOffset() + normal.byteSize() == 31
                        && block.byteSize() == 32);
        check("VertexLayout.BLOCK field miss returns null", block.field("nope") == null);
    }

    /** ENTITY 布局：28B、字段序/偏移 + 关键字段抽查。
     *  ENTITY layout: 28B, field order/offsets + key-field spot checks. */
    private static void entityLayoutChecks() {
        VertexLayout entity = VertexLayout.ENTITY;
        check("VertexLayout.ENTITY byteSize = 28 (actual " + entity.byteSize() + ")",
                entity.byteSize() == 28);
        check("VertexLayout.ENTITY byteAlignment = 4 (actual " + entity.byteAlignment() + ")",
                entity.byteAlignment() == 4);
        String[] names = {"position", "color", "uv", "light", "normal"};
        int[] offsets = {0, 12, 16, 20, 24};
        boolean orderOk = entity.fields().size() == names.length;
        if (orderOk) {
            for (int i = 0; i < names.length; i++) {
                VertexField f = entity.fields().get(i);
                if (!f.name().equals(names[i]) || f.byteOffset() != offsets[i]) {
                    orderOk = false;
                    break;
                }
            }
        }
        check("VertexLayout.ENTITY fixed field order + offsets "
                + "position@0/color@12/uv@16/light@20/normal@24", orderOk);
        check("VertexLayout.ENTITY position FLOATx3 align 4 at offset 0",
                fieldIs(entity, "position", FieldType.FLOAT, 3, 0, 4));
        check("VertexLayout.ENTITY normal UNSIGNED_BYTEx3 align 1 at offset 24",
                fieldIs(entity, "normal", FieldType.UNSIGNED_BYTE, 3, 24, 1));
    }

    /** canonical 确定性：同实例连跑两遍 + 同声明重建一致 + 重名字段拒绝。
     *  Canonical determinism: same-instance re-run, identical-declaration rebuild, dup rejection. */
    private static void layoutDeterminismChecks() {
        check("VertexLayout.BLOCK canonicalBytes() re-run byte-identical",
                Arrays.equals(VertexLayout.BLOCK.canonicalBytes(), VertexLayout.BLOCK.canonicalBytes()));
        VertexLayout blockRebuild = VertexLayout.builder()
                .append("position", FieldType.FLOAT, 3)
                .append("color", FieldType.UNSIGNED_BYTE, 4)
                .append("uv", FieldType.UNSIGNED_SHORT, 2)
                .append("light", FieldType.UNSIGNED_SHORT, 2)
                .append("overlay", FieldType.UNSIGNED_SHORT, 2)
                .append("normal", FieldType.UNSIGNED_BYTE, 3)
                .build();
        check("VertexLayout.BLOCK rebuilt from identical declarations -> equals(BLOCK)",
                blockRebuild.equals(VertexLayout.BLOCK));
        check("VertexLayout.BLOCK rebuilt canonicalBytes() byte-identical",
                Arrays.equals(blockRebuild.canonicalBytes(), VertexLayout.BLOCK.canonicalBytes()));
        check("VertexLayout.ENTITY canonicalBytes() re-run byte-identical",
                Arrays.equals(VertexLayout.ENTITY.canonicalBytes(), VertexLayout.ENTITY.canonicalBytes()));
        boolean dupRejected = false;
        try {
            VertexLayout.builder().append("position", FieldType.FLOAT, 3)
                    .append("position", FieldType.FLOAT, 3).build();
        } catch (IllegalArgumentException e) {
            dupRejected = true;
        }
        check("VertexLayout builder duplicate field name rejected with IllegalArgumentException",
                dupRejected);
    }

    /** 字段抽查助手：name/type/count/offset/alignment 全等。
     *  Field spot-check helper: name/type/count/offset/alignment all match. */
    private static boolean fieldIs(VertexLayout layout, String name, FieldType type, int count,
                                   int offset, int alignment) {
        VertexField f = layout.field(name);
        return f != null && f.type() == type && f.count() == count
                && f.byteOffset() == offset && f.alignment() == alignment;
    }

    // ---------- (2) instancing-base complementarity: InstanceFormat / ShaderTemplate / BackendRegistry ----------

    private static void instancingComplementarity() {
        // (a) InstanceFormat rebuild of VertexLayout.BLOCK — same semantics, same bytes.
        InstanceFormat blockFormat = InstanceFormat.builder()
                .vector("position", ScalarType.F32, 3)
                .vector("color", ScalarType.U8, 4)
                .vector("uv", ScalarType.U16, 2)
                .vector("light", ScalarType.U16, 2)
                .vector("overlay", ScalarType.U16, 2)
                .vector("normal", ScalarType.U8, 3)
                .build();
        check("InstanceFormat rebuild of BLOCK byteSize = 32 (complements VertexLayout.BLOCK)",
                blockFormat.byteSize() == 32 && blockFormat.byteSize() == VertexLayout.BLOCK.byteSize());
        check("InstanceFormat rebuild of BLOCK byteAlignment = 4 (complements VertexLayout.BLOCK)",
                blockFormat.byteAlignment() == 4
                        && blockFormat.byteAlignment() == VertexLayout.BLOCK.byteAlignment());
        String[] names = {"position", "color", "uv", "light", "overlay", "normal"};
        boolean offsetsMatch = blockFormat.fields().size() == names.length;
        if (offsetsMatch) {
            for (int i = 0; i < names.length; i++) {
                VertexField vf = VertexLayout.BLOCK.field(names[i]);
                InstanceFormat.Field ff = blockFormat.field(names[i]);
                if (vf == null || ff == null || ff.byteOffset() != vf.byteOffset()
                        || ff.byteSize() != vf.byteSize()) {
                    offsetsMatch = false;
                    break;
                }
            }
        }
        check("InstanceFormat rebuild of BLOCK per-field offsets/sizes match VertexLayout.BLOCK "
                + "(0/12/16/20/24/28)", offsetsMatch);
        check("InstanceFormat rebuild of BLOCK field order == declaration order (6 fields)",
                blockFormat.layout().equals(blockFormat.fields()) && blockFormat.fields().size() == 6);

        // ENTITY rebuild -> 28B complement.
        InstanceFormat entityFormat = InstanceFormat.builder()
                .vector("position", ScalarType.F32, 3)
                .vector("color", ScalarType.U8, 4)
                .vector("uv", ScalarType.U16, 2)
                .vector("light", ScalarType.U16, 2)
                .vector("normal", ScalarType.U8, 3)
                .build();
        check("InstanceFormat rebuild of ENTITY byteSize = 28 (complements VertexLayout.ENTITY)",
                entityFormat.byteSize() == 28 && entityFormat.byteSize() == VertexLayout.ENTITY.byteSize());

        // (b) ShaderTemplate assembly + placeholder substitution, byte-identical.
        ShaderTemplate s1 = buildSampleShader();
        ShaderTemplate s2 = buildSampleShader();
        check("ShaderTemplate placeholderNames fixed first-occurrence order [color]",
                s1.placeholderNames().equals(List.of("color")));
        String expectedFinal = "#version 450\n"
                + "#extension GL_ARB_bindless_texture : require\n"
                + "layout(location = 0) in vec3 position;\n"
                + "layout(location = 1) in vec4 vec4(1.0, 1.0, 1.0, 1.0);\n"
                + "void main() { gl_Position = vec4(position, 1.0); fragColor = vec4(1.0, 1.0, 1.0, 1.0); }\n"
                + "// end of sample vertex stage\n";
        check("ShaderTemplate compile(Map) byte-identical to expected assembled source",
                s1.compile(SHADER_VALUES).equals(expectedFinal));
        check("ShaderTemplate substituted compileBytes() byte-identical to expected UTF-8",
                Arrays.equals(s1.substituteAll(SHADER_VALUES).compileBytes(),
                        expectedFinal.getBytes(StandardCharsets.UTF_8)));
        check("ShaderTemplate two independent builds -> compile(Map) byte-identical",
                s1.compile(SHADER_VALUES).equals(s2.compile(SHADER_VALUES)));
        boolean unresolvedThrows = false;
        try {
            s1.compile();
        } catch (IllegalStateException e) {
            unresolvedThrows = true;
        }
        check("ShaderTemplate compile() with unresolved placeholders throws IllegalStateException",
                unresolvedThrows);
        boolean missingValueThrows = false;
        try {
            s1.substituteAll(Map.of());
        } catch (IllegalArgumentException e) {
            missingValueThrows = true;
        }
        check("ShaderTemplate substituteAll missing placeholder value throws IllegalArgumentException",
                missingValueThrows);

        // (c) BackendRegistry: fixed registration order / dup-reject / deterministic default.
        RenderBackend simple = backend("fidelity.simple", 10,
                f -> f != null && f.field("position") != null);
        RenderBackend universal = backend("fidelity.universal", 20,
                f -> f != null && f.field("position") != null);
        BackendRegistry.clear();
        BackendRegistry.register(simple);
        BackendRegistry.register(universal);
        check("BackendRegistry fixed registration order [fidelity.simple, fidelity.universal]",
                BackendRegistry.names().equals(List.of("fidelity.simple", "fidelity.universal")));
        boolean dupRejected = false;
        try {
            BackendRegistry.register(simple);
        } catch (IllegalArgumentException e) {
            dupRejected = true;
        }
        check("BackendRegistry duplicate name rejected with IllegalArgumentException", dupRejected);
        check("BackendRegistry defaultFor picks the highest-priority supported backend "
                + "(fidelity.universal 20)", BackendRegistry.defaultFor(blockFormat) == universal);
        // Tie (both priority 10) broken by registration order — earlier registered wins.
        BackendRegistry.clear();
        BackendRegistry.register(simple);
        BackendRegistry.register(backend("fidelity.tie", 10,
                f -> f != null && f.field("position") != null));
        check("BackendRegistry defaultFor tie broken by registration order (earlier wins)",
                BackendRegistry.defaultFor(blockFormat) == simple);
        BackendRegistry.clear(); // probe isolation
    }

    // ---------- (3) three-hook wiring contract: pure-JVM static inventory ----------

    private static void hookWiringContract() {
        String renderRuntime = "io.toterra.subterra.runtime.render.RenderRuntime";
        String renderHooks = "io.toterra.subterra.runtime.render.RenderHooks";
        String renderHooksClient = "io.toterra.subterra.runtime.render.RenderHooksClient";

        // RenderRuntime: class presence + the gate property literal + the wiring references.
        check("hook wiring: RenderRuntime class present (load-only)", classExists(renderRuntime));
        check("hook wiring: gate property 'subterra.probe.render' literal present in RenderRuntime bytes",
                classBytesContain(renderRuntime, "subterra.probe.render"));
        check("hook wiring: RenderRuntime constant-pool reference to RenderHooks",
                classBytesContain(renderRuntime, "RenderHooks"));
        check("hook wiring: RenderRuntime invokes RenderHooks.serverWiringCheck "
                + "(name-and-type in constant pool)", classBytesContain(renderRuntime, "serverWiringCheck"));

        // RenderHooks: class presence + the server-side check method + the hooks-ok marker literal.
        check("hook wiring: RenderHooks class present (load-only)", classExists(renderHooks));
        check("hook wiring: RenderHooks.serverWiringCheck method name present in class bytes",
                classBytesContain(renderHooks, "serverWiringCheck"));
        check("hook wiring: 'hooks ok' marker literal present in RenderHooks bytes "
                + "(serverWiringCheck marker, wired by RenderRuntime)",
                classBytesContain(renderHooks, "hooks ok"));

        // RenderHooksClient: client-side real-object check surface, class presence only.
        check("hook wiring: RenderHooksClient class present (load-only)", classExists(renderHooksClient));
    }

    // ---------- fixtures ----------

    /** 重新构建固定示例着色器模板（同部件）。Rebuilds the fixed sample shader template (same parts). */
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

    // ---------- helpers (load-only, never initialize runtime shells) ----------

    /** 只加载不初始化地确认类存在。Loads without initializing — presence only. */
    private static boolean classExists(String fqcn) {
        try {
            Class.forName(fqcn, false, RenderFidelityProbe.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** 读类的 .class 资源字节（classpath 编译输出）；缺失返回 null。Reads the class-file resource
     * bytes from the classpath compiled outputs; null when absent. */
    private static byte[] classBytes(String fqcn) {
        String resource = "/" + fqcn.replace('.', '/') + ".class";
        try (InputStream in = RenderFidelityProbe.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    /** 断言类的 .class 字节包含某字符串字面量（常量池 UTF-8）。Asserts the class bytes contain a
     * string literal (constant-pool UTF-8). The gate strings are pure ASCII, so modified UTF-8 in
     * the constant pool equals the raw bytes; ISO-8859-1 is a byte-identity mapping, making
     * contains() a linear substring scan (no O(n²)). */
    private static boolean classBytesContain(String fqcn, String literal) {
        byte[] bytes = classBytes(fqcn);
        if (bytes == null) {
            return false;
        }
        return new String(bytes, StandardCharsets.ISO_8859_1).contains(literal);
    }

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
