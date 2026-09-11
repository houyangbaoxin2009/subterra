// p.2.23.4: deterministic in-game-mod-hub probe — anchors the p.2.23 engine.hub data
// plane (p.2.23.1 HubFormGen/FormStructure/FormRenderData, p.2.23.2 HubFormValues/
// HubConfigEditor/HubConfigReloader) and the p.2.23.3 runtime.hub shell to fixed-order /
// hardcoded / byte-identical assertions. Pure JVM — no MC runtime, no timestamps / random /
// timing; exit 0 = PASS, exit 1 = FAIL. NOT shipped in the mod jar.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.rules.RuleKey;
import io.toterra.subterra.engine.config.rules.RuleReloader;
import io.toterra.subterra.engine.config.rules.RuleSpec;
import io.toterra.subterra.engine.config.rules.RuleStore;
import io.toterra.subterra.engine.config.rules.RuleType;
import io.toterra.subterra.engine.config.rules.RuleViolationException;
import io.toterra.subterra.engine.hub.FormField;
import io.toterra.subterra.engine.hub.FormRenderData;
import io.toterra.subterra.engine.hub.FormStructure;
import io.toterra.subterra.engine.hub.HubConfigEditor;
import io.toterra.subterra.engine.hub.HubConfigReloader;
import io.toterra.subterra.engine.hub.HubFormGen;
import io.toterra.subterra.engine.hub.HubFormValues;
import io.toterra.subterra.engine.scaffold.FormDataGen;
import io.toterra.subterra.engine.schema.Schema;
import io.toterra.subterra.engine.schema.SchemaCodec;
import io.toterra.subterra.engine.schema.SchemaViolationException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * p.2.23.4 — Hub 确定性/接线探针：把 p.2.23 的 engine.hub 数据面（p.2.23.1 表单自动生成
 * {@link HubFormGen} / {@link FormStructure} / {@link FormRenderData}，p.2.23.2 表单值转换
 * {@link HubFormValues} / 配置编辑 {@link HubConfigEditor} / 热重载 {@link HubConfigReloader}）
 * 与 p.2.23.3 的 runtime.hub 壳的确定性契约锚定为纯 JVM 断言。纯 JVM——不碰 MC、无时序、
 * 无随机；exit 0 = PASS，exit 1 = FAIL；不进 mod jar。四节：
 * <ol>
 *   <li><b>表单自动生成确定性</b>：固定定型 td Schema（TABLE 根四字段 int/float/string/bool +
 *       LIST 根 + 标量根）→ {@link HubFormGen#fromSchema} → {@link FormStructure} 字段固定文档序
 *       （kind 用 {@code SchemaKind.tdName()} 规范名、widget 用固定映射，与硬编码期望逐字段相等）；
 *       {@code toTd→parse→fromTd} 往返恒等（TABLE/LIST/标量根四形态）；{@link FormRenderData#render}
 *       连跑两遍同字节；kind/widget 与 p.2.12.4 {@link FormDataGen} 输出去头部注释后逐字节对齐
 *       （TABLE/LIST/标量样例核对）。</li>
 *   <li><b>配置编辑 + 热重载</b>：{@link HubConfigEditor} 写 global/overrides → {@code resolve}
 *       双层胜出（overrides 对冲突键全胜、未覆盖键保持）；类型违约与未知键抛
 *       {@link RuleViolationException} 且 revision 不自增、全局档不变（全有或全无）；{@code render}
 *       与硬编码期望串逐字符（key 字典序）；{@link HubConfigReloader} 同文本 → unchanged（revision
 *       不变）、变更 → applied（revision+1）、同一目标重投 → 幂等 unchanged、违约 → failed 保旧态
 *       （store 未写、revision 不变）、同序列在 fresh store 上重放终态一致（revision + 双层终态）。</li>
 *   <li><b>往返恒等</b>：{@code HubFormValues.formValuesToTd→tdToFormValues} 往返恒等（逐项值一致
 *       + 迭代序 = spec 注册序）；{@code formValuesToTd} 连跑两遍同字节。</li>
 *   <li><b>接线契约（静态盘点）</b>：runtime.hub 的 {@code HubRuntime}/{@code HubCommand} 类存在
 *       （只加载不初始化，纯 JVM 不触发 MC 的 {@code LogUtils} 初始化）+ 类字节含门控属性串
 *       {@code subterra.probe.hub} + marker 前缀 {@code [Subterra hub]}；AsyncE2EProbe 类字节
 *       含 hubOk/hubVerb 断言字面量（{@code ok (fields=} 与 {@code override ok}）——真实 boot
 *       生命周期由 AsyncE2EProbe hubOk/hubVerb 槽覆盖。</li>
 * </ol>
 * 确定性纪律：固定序、无时序、无随机；全部线性遍历（禁 O(n²)）；失败计数只在失败路径自增；
 * 全过输出 {@code [HubProbe] PASS (n checks)} exit 0，否则 FAIL exit 1。
 *
 * <p>p.2.23.4 — deterministic in-game-mod-hub probe: anchors the p.2.23 engine.hub data plane
 * (the p.2.23.1 form auto-generation {@link HubFormGen} / {@link FormStructure} /
 * {@link FormRenderData}, and the p.2.23.2 form-value conversion {@link HubFormValues} /
 * config-edit {@link HubConfigEditor} / hot-reload {@link HubConfigReloader}) and the p.2.23.3
 * runtime.hub shell to pure-JVM assertions. Pure JVM — no MC runtime, no timing, no randomness;
 * exit 0 = PASS, exit 1 = FAIL; never shipped in the mod jar. Four sections:
 * <ol>
 *   <li><b>Form auto-generation determinism</b>: fixed settled td Schemas (a TABLE root with four
 *       fields int/float/string/bool + a LIST root + scalar roots) → {@link HubFormGen#fromSchema}
 *       → {@link FormStructure} with the fields in fixed document order (kinds = the
 *       {@code SchemaKind.tdName()} canonical names, widgets = the fixed map, element-for-element
 *       equal to hardcoded expectations); {@code toTd→parse→fromTd} round-trip identity (all four
 *       TABLE/LIST/scalar shapes); {@link FormRenderData#render} twice-run byte identity;
 *       kind/widget aligned byte-for-byte with the p.2.12.4 {@link FormDataGen} output after its
 *       header comment lines are stripped (TABLE/LIST/scalar samples checked).</li>
 *   <li><b>Config edit + hot reload</b>: {@link HubConfigEditor} writes global/overrides →
 *       {@code resolve} two-tier win (overrides wins every conflicting key, untouched keys keep
 *       the global values); a type violation and an unknown key raise
 *       {@link RuleViolationException} without bumping revision or writing the global layer
 *       (all-or-nothing); {@code render} is character-for-character the hardcoded lexicographic
 *       expectation; {@link HubConfigReloader} same text → unchanged (revision untouched), a
 *       change → applied (revision+1), re-delivering the same target → idempotent unchanged, a
 *       violating document → failed keeping the old state (store unwritten, revision untouched),
 *       and the same sequence replayed on a fresh store ends in the same final state (revision +
 *       both tier snapshots).</li>
 *   <li><b>Round-trip identity</b>: {@code HubFormValues.formValuesToTd→tdToFormValues}
 *       round-trips identically (item-for-item values + iteration order = spec registration
 *       order); {@code formValuesToTd} twice-run byte identity.</li>
 *   <li><b>Wiring contract (static inventory)</b>: the runtime.hub {@code HubRuntime}/
 *       {@code HubCommand} classes are present (load-only, never initialized — the pure JVM must
 *       not trigger the MC {@code LogUtils} init), their class bytes carry the gate string
 *       {@code subterra.probe.hub} and the marker prefix {@code [Subterra hub]}; the AsyncE2EProbe
 *       class bytes carry the hubOk/hubVerb assertion literals ({@code ok (fields=} and
 *       {@code override ok}) — the real boot lifecycle is covered by the AsyncE2EProbe
 *       hubOk/hubVerb slots.</li>
 * </ol>
 * Determinism discipline: fixed order, no timing, no randomness; all traversals linear (no O(n²));
 * failures are counted only on failing paths; PASS only when all checks pass, then exit 0, else
 * FAIL with counts and exit 1.
 */
public final class HubProbe {

    private HubProbe() {
    }

    private static int checks = 0;
    private static int failures = 0;

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    public static void main(String[] args) {
        try {
            formAutoGen();
            configEditAndReload();
            roundTrip();
            wiringContract();
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] probe exception: " + e);
            e.printStackTrace(System.out);
        }
        if (failures == 0) {
            System.out.println("[HubProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[HubProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    // ---------- fixtures (fixed, the deterministic single source of truth) ----------

    /** 固定定型 td schema：TABLE 根，四字段（int/float/string/bool，固定文档序）。
     *  The fixed settled td schema: TABLE root, four fields (int/float/string/bool, fixed
     *  document order). */
    private static final String TABLE_SCHEMA_TD =
            "[\n"
            + "  root = \"table\",\n"
            + "  fields = [\n"
            + "    seed_offset = \"int\",\n"
            + "    activation_range = \"float\",\n"
            + "    weather = \"string\",\n"
            + "    debug_enabled = \"bool\",\n"
            + "  ],\n"
            + "]";

    /** LIST 根（repeat 语义）。The LIST root (repeat semantics). */
    private static final String LIST_SCHEMA_TD = "[\n  root = \"list\",\n  listOf = \"string\",\n]";

    /** 标量根（int / string）。The scalar roots (int / string). */
    private static final String INT_SCHEMA_TD = "[\n  root = \"int\",\n]";
    private static final String STRING_SCHEMA_TD = "[\n  root = \"string\",\n]";

    /** 固定示例规格集（固定注册序，全带 default）——编辑面与热重载面共用，与 p.2.23.3 样例同源风格。
     *  The fixed sample spec set (fixed registration order, all with defaults) — shared by the
     *  edit surface and the hot-reload surface, same source style as the p.2.23.3 samples. */
    private static final List<RuleSpec> FIXED_SPECS = List.of(
            new RuleSpec(new RuleKey("subterra.worldgen.seed_offset"), RuleType.INT, "0",
                    "world-seed offset applied by the subterra worldgen preset"),
            new RuleSpec(new RuleKey("subterra.entity.activation.range"), RuleType.FLOAT, "32.0",
                    "entity activation range in blocks"),
            new RuleSpec(new RuleKey("subterra.render.weather"), RuleType.STRING, "clear",
                    "weather override rendered into the world"),
            new RuleSpec(new RuleKey("subterra.debug.show_hud"), RuleType.BOOLEAN, "false",
                    "whether the management HUD is shown"));

    /** 固定双层写样例：set 全局档值、override 存档覆盖档值（胜出）。The fixed two-tier write
     *  samples: the set global-layer value and the override save-layer value (the winner). */
    private static final String KEY_OFFSET = "subterra.worldgen.seed_offset";
    private static final String KEY_RANGE = "subterra.entity.activation.range";
    private static final String KEY_WEATHER = "subterra.render.weather";
    private static final String KEY_HUD = "subterra.debug.show_hud";

    /** 固定热重载旧/新表单值对（global 档：seed_offset 7 → 42；overrides 档：range 32.0 → 64.0）。
     *  The fixed hot-reload old/new form-value pairs (global layer: seed_offset 7 → 42;
     *  overrides layer: range 32.0 → 64.0). */
    private static final Map<String, String> OLD_GLOBAL = Map.of(KEY_OFFSET, "7");
    private static final Map<String, String> NEW_GLOBAL = Map.of(KEY_OFFSET, "42");
    private static final Map<String, String> OLD_OVERRIDES = Map.of(KEY_RANGE, "32.0");
    private static final Map<String, String> BAD_OVERRIDES = Map.of(KEY_RANGE, "abc");
    private static final Map<String, String> NEW_OVERRIDES = Map.of(KEY_RANGE, "64.0");

    /** 解析固定 schema td（样例合法——文档违约仅可能来自程序错误，包为 IllegalStateException）。
     *  Parses a fixed schema td (the sample is legal — a document violation can only be a program
     *  error, wrapped as IllegalStateException). */
    private static Schema parseSchema(String td) {
        try {
            return SchemaCodec.parse(td);
        } catch (SchemaViolationException e) {
            throw new IllegalStateException("sample schema invalid: " + e.reason(), e);
        }
    }

    /** 去掉 {@link FormDataGen} 输出头两行注释，得到「体」文本（与 {@link FormRenderData#render}
     *  逐字节对齐）。Strips {@link FormDataGen}'s two header comment lines, yielding the body text
     *  (aligned byte-for-byte with {@link FormRenderData#render}). */
    private static String stripFormDataHeader(String gen) {
        int first = gen.indexOf('\n');
        int second = first < 0 ? -1 : gen.indexOf('\n', first + 1);
        return second < 0 ? "" : gen.substring(second + 1);
    }

    /** 动作抛出 RuleViolationException 为真。True iff the action throws RVE. */
    private static boolean throwsRVE(Runnable r) {
        try {
            r.run();
            return false;
        } catch (RuleViolationException e) {
            return true;
        }
    }

    // ---------- (1) form auto-generation determinism (p.2.23.1) ----------

    private static void formAutoGen() {
        Schema tableSchema = parseSchema(TABLE_SCHEMA_TD);
        FormStructure form = HubFormGen.fromSchema(tableSchema);
        List<FormField> expected = List.of(
                new FormField("seed_offset", "int", "number"),
                new FormField("activation_range", "float", "number"),
                new FormField("weather", "string", "text"),
                new FormField("debug_enabled", "bool", "check"));
        check("HubFormGen.fromSchema(TABLE): 字段固定文档序 + kind/widget 固定映射 + 根 table/group",
                form.fields().equals(expected)
                        && form.rootKind().equals("table")
                        && form.rootWidget().equals("group"));

        FormStructure list = HubFormGen.fromSchema(parseSchema(LIST_SCHEMA_TD));
        check("HubFormGen.fromSchema(LIST): 空字段列表 + 元素 kind 规范名 + widget repeat",
                list.fields().isEmpty()
                        && list.rootKind().equals("string")
                        && list.rootWidget().equals("repeat"));

        FormStructure intRoot = HubFormGen.fromSchema(parseSchema(INT_SCHEMA_TD));
        FormStructure strRoot = HubFormGen.fromSchema(parseSchema(STRING_SCHEMA_TD));
        check("HubFormGen.fromSchema(标量根): 空字段列表 + 根 kind 规范名 + 固定映射控件",
                intRoot.fields().isEmpty() && intRoot.rootKind().equals("int")
                        && intRoot.rootWidget().equals("number")
                        && strRoot.fields().isEmpty() && strRoot.rootKind().equals("string")
                        && strRoot.rootWidget().equals("text"));

        check("HubFormGen: toTd→parse→fromTd 往返恒等（TABLE/LIST/标量根四形态）",
                HubFormGen.fromTd(Td.parse(HubFormGen.toTd(form))).equals(form)
                        && HubFormGen.fromTd(Td.parse(HubFormGen.toTd(list))).equals(list)
                        && HubFormGen.fromTd(Td.parse(HubFormGen.toTd(intRoot))).equals(intRoot)
                        && HubFormGen.fromTd(Td.parse(HubFormGen.toTd(strRoot))).equals(strRoot));

        String render1 = FormRenderData.render(form);
        check("FormRenderData.render: 连跑两遍同字节",
                render1.equals(FormRenderData.render(form))
                        && Arrays.equals(render1.getBytes(StandardCharsets.UTF_8),
                        FormRenderData.render(form).getBytes(StandardCharsets.UTF_8)));

        check("kind/widget 与 FormDataGen 输出逐字节对齐（TABLE 根，去头部注释）",
                stripFormDataHeader(FormDataGen.generateFormData(tableSchema)).equals(render1));

        check("kind/widget 与 FormDataGen 输出逐字节对齐（LIST/标量根，去头部注释）",
                stripFormDataHeader(FormDataGen.generateFormData(parseSchema(LIST_SCHEMA_TD)))
                        .equals(FormRenderData.render(list))
                        && stripFormDataHeader(FormDataGen.generateFormData(parseSchema(INT_SCHEMA_TD)))
                        .equals(FormRenderData.render(intRoot))
                        && stripFormDataHeader(FormDataGen.generateFormData(parseSchema(STRING_SCHEMA_TD)))
                        .equals(FormRenderData.render(strRoot)));
    }

    // ---------- (2) config edit + hot reload (p.2.23.2) ----------

    private static void configEditAndReload() {
        HubConfigEditor editor = new HubConfigEditor(FIXED_SPECS);
        editor.editGlobal(new LinkedHashMap<>(Map.of(
                KEY_OFFSET, "7",
                KEY_RANGE, "32.0",
                KEY_WEATHER, "clear",
                KEY_HUD, "true")));
        check("HubConfigEditor.editGlobal: 写入全局档 + revision=1",
                editor.revision() == 1L
                        && editor.global().get(KEY_OFFSET).equals("7"));

        editor.editOverrides(Map.of(KEY_OFFSET, "42"));
        check("HubConfigEditor.editOverrides: 写入存档覆盖档 + revision=2",
                editor.revision() == 2L
                        && editor.overrides().get(KEY_OFFSET).equals("42"));

        Map<String, String> resolved = editor.resolve();
        check("HubConfigEditor.resolve: 双层胜出（overrides 对冲突键全胜、未覆盖键保持）",
                resolved.size() == 4
                        && resolved.get(KEY_OFFSET).equals("42")
                        && resolved.get(KEY_RANGE).equals("32.0")
                        && resolved.get(KEY_WEATHER).equals("clear")
                        && resolved.get(KEY_HUD).equals("true"));

        long revBefore = editor.revision();
        check("HubConfigEditor: 类型违约 → RuleViolationException 且 revision 不自增、全局档不变",
                throwsRVE(() -> editor.editGlobal(Map.of(KEY_OFFSET, "abc")))
                        && editor.revision() == revBefore
                        && editor.global().get(KEY_OFFSET).equals("7"));

        check("HubConfigEditor: 未知键 → RuleViolationException 且 revision 不自增",
                throwsRVE(() -> editor.editGlobal(Map.of("subterra.unknown.key", "x")))
                        && editor.revision() == revBefore);

        String expectedRender = "subterra.debug.show_hud=true; subterra.entity.activation.range=32.0; "
                + "subterra.render.weather=clear; subterra.worldgen.seed_offset=42";
        check("HubConfigEditor.render: 与期望串逐字符（key 字典序，连跑两遍同字节）",
                editor.render().equals(expectedRender)
                        && editor.render().equals(editor.render()));

        RuleStore store = new RuleStore(FIXED_SPECS);
        HubConfigReloader reloader = new HubConfigReloader(store);
        RuleReloader.ReloadResult same = reloader.reloadGlobal(OLD_GLOBAL, OLD_GLOBAL);
        check("HubConfigReloader: 同文本 → unchanged、revision 不自增、store 未触碰",
                !same.changed() && !same.applied()
                        && reloader.revision() == 0L
                        && store.global().isEmpty());

        RuleReloader.ReloadResult applied = reloader.reloadGlobal(OLD_GLOBAL, NEW_GLOBAL);
        check("HubConfigReloader: 变更 → applied、revision+1、store 并入",
                applied.changed() && applied.applied()
                        && reloader.revision() == 1L
                        && store.global().get(KEY_OFFSET).equals("42"));

        RuleReloader.ReloadResult idempotent = reloader.reloadGlobal(OLD_GLOBAL, NEW_GLOBAL);
        check("HubConfigReloader: 同一目标重投 → 幂等 unchanged、revision 不变",
                !idempotent.changed() && !idempotent.applied()
                        && reloader.revision() == 1L);

        RuleReloader.ReloadResult failed = reloader.reloadOverrides(OLD_OVERRIDES, BAD_OVERRIDES);
        check("HubConfigReloader: 违约 → failed 保旧态（store 未写、revision 不变）",
                failed.changed() && !failed.applied() && failed.failureReason() != null
                        && reloader.revision() == 1L
                        && store.overrides().isEmpty());

        ReloadSnapshot first = replaySequence();
        check("HubConfigReloader: 序列终态锚定（rev=2、global/overrides/resolve 双层值）",
                first.revision == 2L
                        && first.global.equals(Map.of(KEY_OFFSET, "42"))
                        && first.overrides.equals(Map.of(KEY_RANGE, "64.0"))
                        && first.resolve.size() == 2
                        && first.resolve.get(KEY_OFFSET).equals("42")
                        && first.resolve.get(KEY_RANGE).equals("64.0"));

        ReloadSnapshot second = replaySequence();
        check("HubConfigReloader: 同序列在 fresh store 上重放终态一致",
                first.revision == second.revision
                        && first.global.equals(second.global)
                        && first.overrides.equals(second.overrides)
                        && first.resolve.equals(second.resolve));
    }

    /** 固定重载序列（global applied + 幂等重投 + overrides failed + overrides applied）的一次完整回放；
     *  失败不更新幂等记忆，故重放逐行为一致。One full replay of the fixed reload sequence (global
     *  applied + idempotent re-delivery + overrides failed + overrides applied); failures do not
     *  update the idempotency memory, so a replay behaves identically line by line. */
    private static ReloadSnapshot replaySequence() {
        RuleStore store = new RuleStore(FIXED_SPECS);
        HubConfigReloader r = new HubConfigReloader(store);
        r.reloadGlobal(OLD_GLOBAL, NEW_GLOBAL);       // applied
        r.reloadGlobal(OLD_GLOBAL, NEW_GLOBAL);       // unchanged (idempotent)
        r.reloadOverrides(OLD_OVERRIDES, BAD_OVERRIDES); // failed (keeps old state)
        r.reloadOverrides(OLD_OVERRIDES, NEW_OVERRIDES); // applied
        return new ReloadSnapshot(r.revision(), store.global(), store.overrides(), store.resolve());
    }

    /** 一次重载序列后的终态快照（确定性对比载体）。The final-state snapshot after one reload
     *  sequence (a deterministic comparison carrier). */
    private record ReloadSnapshot(long revision, Map<String, String> global,
                                  Map<String, String> overrides, Map<String, String> resolve) {
    }

    // ---------- (3) form-value round-trip (p.2.23.2) ----------

    private static void roundTrip() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(KEY_OFFSET, "7");
        values.put(KEY_RANGE, "32.0");
        values.put(KEY_WEATHER, "clear");
        values.put(KEY_HUD, "true");
        String td1 = HubFormValues.formValuesToTd(FIXED_SPECS, values);
        Map<String, String> back = HubFormValues.tdToFormValues(td1);
        check("HubFormValues: formValuesToTd→tdToFormValues 往返恒等（逐项值一致 + 迭代序=注册序）",
                back.equals(values)
                        && List.copyOf(back.keySet()).equals(
                        List.of(KEY_OFFSET, KEY_RANGE, KEY_WEATHER, KEY_HUD)));

        String td2 = HubFormValues.formValuesToTd(FIXED_SPECS, values);
        check("HubFormValues: formValuesToTd 连跑两遍同字节",
                td1.equals(td2)
                        && Arrays.equals(td1.getBytes(StandardCharsets.UTF_8),
                        td2.getBytes(StandardCharsets.UTF_8)));
    }

    // ---------- (4) wiring contract (static inventory, load-only) ----------

    private static void wiringContract() {
        String hubRuntime = "io.toterra.subterra.runtime.hub.HubRuntime";
        String hubCommand = "io.toterra.subterra.runtime.hub.HubCommand";
        check("wiring: runtime.hub.HubRuntime 类存在（只加载不初始化，纯 JVM 不触发 MC LogUtils）",
                classExists(hubRuntime));
        check("wiring: runtime.hub.HubCommand 类存在（只加载不初始化）",
                classExists(hubCommand));
        check("wiring: HubRuntime 类字节含门控属性串 'subterra.probe.hub'",
                classBytesContain(hubRuntime, "subterra.probe.hub"));
        check("wiring: HubRuntime 类字节含 marker 前缀 '[Subterra hub]'",
                classBytesContain(hubRuntime, "[Subterra hub]"));
        check("wiring: HubCommand 类字节含 marker 前缀 '[Subterra hub]'",
                classBytesContain(hubCommand, "[Subterra hub]"));
        // 真实生命周期由 boot E2E 覆盖：AsyncE2EProbe hubOk/hubVerb 断言字面量的静态存在性。
        check("wiring: AsyncE2EProbe 类字节含 hubOk/hubVerb 断言（'ok (fields=' + 'override ok'）",
                classBytesContain("io.toterra.subterra.probes.AsyncE2EProbe", "[Subterra hub]")
                        && classBytesContain("io.toterra.subterra.probes.AsyncE2EProbe", "ok (fields=")
                        && classBytesContain("io.toterra.subterra.probes.AsyncE2EProbe", "override ok"));
    }

    // ---------- helpers ----------

    /** 只加载不初始化地确认类存在。Loads without initializing — presence only. */
    private static boolean classExists(String fqcn) {
        try {
            Class.forName(fqcn, false, HubProbe.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** 读类的 .class 资源字节（classpath 编译输出）；缺失返回 null。Reads the class-file resource
     *  bytes from the classpath compiled outputs; null when absent. */
    private static byte[] classBytes(String fqcn) {
        String resource = "/" + fqcn.replace('.', '/') + ".class";
        try (InputStream in = HubProbe.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    /** 断言类的 .class 字节包含某字符串字面量（常量池 UTF-8，纯 ASCII，ISO-8859-1 逐字节映射，
     *  线性 contains、无 O(n²)）。Asserts the class bytes contain a string literal (constant-pool
     *  UTF-8; the strings are pure ASCII so ISO-8859-1 is a byte-identity mapping — a linear
     *  contains scan, no O(n²)). */
    private static boolean classBytesContain(String fqcn, String literal) {
        byte[] bytes = classBytes(fqcn);
        if (bytes == null) {
            return false;
        }
        return new String(bytes, StandardCharsets.ISO_8859_1).contains(literal);
    }
}
