// p.2.25.3: deterministic entity-scale wiring probe — anchors the p.2.25 engine.scale plane
// (p.2.25.1 ScaleType/ScaleData/ScaleModifier/ScaledEntityData/ScaleRuleDocument) and the
// p.2.25.2 runtime.scale shell (ScaleRuntime) to fixed-order / hardcoded / byte-identical
// assertions. Pure JVM — no MC runtime, no timestamps / random / timing; exit 0 = PASS,
// exit 1 = FAIL. NOT shipped in the mod jar.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.scale.ScaleData;
import io.toterra.subterra.engine.scale.ScaleModifier;
import io.toterra.subterra.engine.scale.ScaleRuleDocument;
import io.toterra.subterra.engine.scale.ScaleType;
import io.toterra.subterra.engine.scale.ScaledEntityData;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * p.2.25.3 — 缩放确定性/接线探针：把 p.2.25 的 engine.scale 数据面（p.2.25.1 Pehkui-Rebuilt 核心
 * 移植：固定 9 维 {@link ScaleType}、每实体每维度 {@link ScaleData}、乘法修饰符 {@link ScaleModifier}、
 * 按标签基础缩放 {@link ScaledEntityData}、td 化数据包规则 {@link ScaleRuleDocument}）与 p.2.25.2
 * 的 runtime.scale 壳的确定性契约锚定为纯 JVM 断言。纯 JVM——不碰 MC、无时序、无随机；
 * exit 0 = PASS，exit 1 = FAIL；不进 mod jar。五节：
 * <ol>
 *   <li><b>模型确定性</b>：{@link ScaleType#values()} 固定序（9 个，逐名核对）+ {@code form()} /
 *       {@code fromForm()} 全量往返；{@link ScaleData} with/get 确定性（缺省 1.0）、枚举序
 *       （构造归一化为 ordinal 升序的 {@code values()} 序遍历比对）、非法值（≤0 / NaN）拒绝；
 *       {@link ScaleModifier#applyAll} 注册序逐维乘法同 double 位（连跑两遍 + 从零重建摘要逐字符
 *       一致）。</li>
 *   <li><b>规则文档</b>：{@link ScaleRuleDocument} toTd→写入→parse→fromTd 往返恒等（固定字段序
 *       type/tag/scale）；重复 {@code type+tag} 首现胜出（丢弃后不再可应用）；applyRules 覆盖语义
 *       （后命中规则覆盖先命中、规则胜出文档化中性值）；toTd 连跑两遍同字节。</li>
 *   <li><b>标签缩放</b>：{@link ScaledEntityData} tagScales 字典序（{@code TreeMap} String 自然序）
 *       + merge 确定性（乘法并入，连跑两遍 + 重建摘要一致）。</li>
 *   <li><b>接线契约（静态盘点）</b>：runtime.scale 的 {@code ScaleRuntime} 类存在（只加载不初始化，
 *       纯 JVM 不触发 MC {@code LogUtils} 静态初始化）+ 类字节含门控属性串 {@code subterra.probe.scale}
 *       + marker 前缀 {@code [Subterra scale]}；AsyncE2EProbe 类字节含 {@code [Subterra scale]}
 *       断言字面量——真实 boot 生命周期由 AsyncE2EProbe scaleOk 槽覆盖。</li>
 *   <li><b>许可盘点</b>：classpath 资源
 *       {@code META-INF/third-party/pehkui-rebuilt-3.8.5/LICENSE} 含 {@code MIT License} 与
 *       {@code Virtuoel}（版权声明）；NOTICE 存在。</li>
 * </ol>
 * 确定性纪律：固定序、无时序、无随机；全部线性遍历（禁 O(n²)）；失败计数只在失败路径自增；
 * 全过输出 {@code [ScaleProbe] PASS (n checks)} exit 0，否则 FAIL exit 1。
 *
 * <p>p.2.25.3 — deterministic entity-scale wiring probe: anchors the p.2.25 engine.scale data plane
 * (the p.2.25.1 Pehkui-Rebuilt core port — fixed nine-dimension {@link ScaleType}, per-entity
 * per-dimension {@link ScaleData}, multiplicative {@link ScaleModifier}, per-tag base scales
 * {@link ScaledEntityData}, td-ized data-pack rules {@link ScaleRuleDocument}) and the p.2.25.2
 * runtime.scale shell to pure-JVM assertions. Pure JVM — no MC runtime, no timing, no randomness;
 * exit 0 = PASS, exit 1 = FAIL; never shipped in the mod jar. Five sections:
 * <ol>
 *   <li><b>Model determinism</b>: {@link ScaleType#values()} fixed order (all nine, name-checked) +
 *       full {@code form()}/{@code fromForm()} round-trip; {@link ScaleData} with/get determinism
 *       (default 1.0), enum order (the ctor's ordinal-ascending {@code values()} traversal), and
 *       invalid-value (≤0 / NaN) rejection; {@link ScaleModifier#applyAll} registration-order
 *       per-dimension multiplication is bit-identical across two runs plus a from-scratch rebuild.</li>
 *   <li><b>Rule document</b>: {@link ScaleRuleDocument} toTd→write→parse→fromTd round-trip identity
 *       (fixed field order type/tag/scale); duplicate {@code type+tag} first-occurrence-wins
 *       (then never applied); applyRules override semantics (a later match overrides an earlier one,
 *       and the rule wins over the documented neutral value); toTd twice yields identical bytes.</li>
 *   <li><b>Tag scaling</b>: {@link ScaledEntityData} tagScales lexicographic order ({@code TreeMap}
 *       String natural order) + merge determinism (multiplicative fold-in, identical digest across
 *       two runs plus a rebuild).</li>
 *   <li><b>Wiring contract (static inventory)</b>: the runtime.scale {@code ScaleRuntime} class is
 *       present (load-only, never initialized — the pure JVM must not trigger the MC {@code LogUtils}
 *       static init), its class bytes carry the gate string {@code subterra.probe.scale} and the
 *       marker prefix {@code [Subterra scale]}; the AsyncE2EProbe class bytes carry the
 *       {@code [Subterra scale]} assertion literal — the real boot lifecycle is covered by the
 *       AsyncE2EProbe scaleOk slot.</li>
 *   <li><b>License inventory</b>: the classpath resource
 *       {@code META-INF/third-party/pehkui-rebuilt-3.8.5/LICENSE} carries {@code MIT License} and
 *       {@code Virtuoel} (the copyright notice); the NOTICE is present.</li>
 * </ol>
 * Determinism discipline: fixed order, no timing, no randomness; all traversals linear (no O(n²));
 * failures are counted only on failing paths; PASS only when all checks pass, then exit 0, else FAIL
 * with counts and exit 1.
 */
public final class ScaleProbe {

    private ScaleProbe() {
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
            modelDeterminism();
            ruleDocument();
            tagScaling();
            wiringContract();
            licenseInventory();
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] probe exception: " + e);
            e.printStackTrace(System.out);
        }
        if (failures == 0) {
            System.out.println("[ScaleProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[ScaleProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    /** 动作抛出 IAE 为真。True iff the action raises IllegalArgumentException. */
    private static boolean throwsIAE(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    /** 缩放摘要：固定 {@link ScaleType#values()} 维度序 {@code type=value} 逗号连接，作为确定性
     *  复验载体。 Scale digest: the fixed {@link ScaleType#values()} dimension-order
     *  {@code type=value} join — a determinism-replay carrier. */
    private static String digest(ScaleData data) {
        StringBuilder sb = new StringBuilder();
        for (ScaleType t : ScaleType.values()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(t.form()).append('=').append(data.get(t));
        }
        return sb.toString();
    }

    /** 固定 {@code scale_rules} td 条目（固定字段序 type / tag / scale）。A fixed {@code scale_rules}
     *  td entry (fixed field order type / tag / scale). */
    private static TdTable ruleEntry(String type, String tag, double scale) {
        return TdTable.builder()
                .put("type", TdValue.str(type))
                .put("tag", TdValue.str(tag))
                .put("scale", TdValue.of(scale))
                .build();
    }

    /** 固定 {@code scale_rules} td 文档（version=1 + 按给定序号列出条目）。A fixed {@code scale_rules}
     *  td document (version=1 + entries in the given order). */
    private static TdTable ruleDoc(TdTable... entries) {
        TdTable.Builder eb = TdTable.builder();
        for (TdTable e : entries) {
            eb.element(e);
        }
        return TdTable.builder()
                .put("version", TdValue.of(1L))
                .put("entries", eb.build())
                .build();
    }

    // ---------- (1) model determinism (p.2.25.1) ----------

    private static void modelDeterminism() {
        // ScaleType: fixed values() order (9) + form/fromForm full round-trip.
        List<String> expected = List.of("BASE", "WIDTH", "HEIGHT", "DEPTH", "EYE_HEIGHT",
                "HITBOX_WIDTH", "HITBOX_HEIGHT", "MODEL_WIDTH", "MODEL_HEIGHT");
        ScaleType[] vals = ScaleType.values();
        boolean fixedOrder = vals.length == 9;
        for (int i = 0; i < vals.length && i < expected.size(); i++) {
            fixedOrder = fixedOrder && vals[i].name().equals(expected.get(i));
        }
        check("ScaleType: values() 固定序（9 维，逐名核对 BASE→WIDTH→…→MODEL_HEIGHT）", fixedOrder);
        boolean roundTrip = true;
        for (ScaleType t : ScaleType.values()) {
            roundTrip = roundTrip && ScaleType.fromForm(t.form()) == t;
        }
        check("ScaleType: form()/fromForm() 全量往返（9 维各一次）", roundTrip);

        // ScaleData: with/get determinism, default 1.0, enum (ordinal) iteration order, invalid reject.
        ScaleData d = ScaleData.of("minecraft:zombie")
                .with(ScaleType.WIDTH, 4.0)
                .with(ScaleType.DEPTH, 1.5)
                .with(ScaleType.BASE, 2.0);
        check("ScaleData: with/get 确定性（BASE=2.0, WIDTH=4.0, DEPTH=1.5；缺省 HEIGHT=1.0）",
                d.get(ScaleType.BASE) == 2.0
                        && d.get(ScaleType.WIDTH) == 4.0
                        && d.get(ScaleType.DEPTH) == 1.5
                        && d.get(ScaleType.HEIGHT) == 1.0);
        // The fixed enum order: ScaleData.get iterates values() in ordinal order, restricted to the
        // dimensions the consumer asks about; the backing map is normalized to ordinal-ascending order.
        List<ScaleType> keyed = new ArrayList<>(d.scales().keySet());
        check("ScaleData: 枚举序（构造归一化为 ordinal 升序，键序 = BASE,WIDTH,DEPTH 与插序无关）",
                keyed.equals(List.of(ScaleType.BASE, ScaleType.WIDTH, ScaleType.DEPTH)));
        check("ScaleData: ≤0 值拒绝（0.0）",
                throwsIAE(() -> ScaleData.of("e").with(ScaleType.BASE, 0.0)));
        check("ScaleData: ≤0 值拒绝（负值）",
                throwsIAE(() -> ScaleData.of("e").with(ScaleType.BASE, -1.0)));
        check("ScaleData: NaN 值拒绝",
                throwsIAE(() -> ScaleData.of("e").with(ScaleType.BASE, Double.NaN)));

        // ScaleModifier.applyAll: registration-order fold is double-bit-identical across two runs
        // and a from-scratch rebuild.
        ScaleData base = ScaleData.of("minecraft:zombie")
                .with(ScaleType.BASE, 2.0)
                .with(ScaleType.WIDTH, 4.0)
                .with(ScaleType.HEIGHT, 0.5);
        List<ScaleModifier> mods = List.of(
                new ScaleModifier("subterra:nether", 0.5),
                new ScaleModifier("subterra:potions", 2.0));
        String run1 = digest(ScaleModifier.applyAll(mods, base));
        String run2 = digest(ScaleModifier.applyAll(mods, base));
        String rebuild = digest(ScaleModifier.applyAll(mods,
                ScaleData.of("minecraft:zombie")
                        .with(ScaleType.BASE, 2.0)
                        .with(ScaleType.WIDTH, 4.0)
                        .with(ScaleType.HEIGHT, 0.5)));
        check("ScaleModifier.applyAll: 注册序逐维乘法连跑两遍摘要逐字符一致",
                run1.equals(run2));
        check("ScaleModifier.applyAll: 注册序逐维乘法从零重建摘要一致（同 double 位）",
                run1.equals(rebuild)
                        && ScaleModifier.applyAll(mods, base).get(ScaleType.BASE) == 2.0
                        && ScaleModifier.applyAll(mods, base).get(ScaleType.WIDTH) == 4.0
                        && ScaleModifier.applyAll(mods, base).get(ScaleType.HEIGHT) == 0.5);
    }

    // ---------- (2) rule document (td round-trip + override, p.2.25.1) ----------

    private static void ruleDocument() {
        // toTd -> write -> parse -> fromTd round-trip identity with fixed field order type/tag/scale.
        TdTable doc1 = ruleDoc(
                ruleEntry("width", "minecraft:zombie", 1.5),
                ruleEntry("height", "minecraft:player", 0.5));
        ScaleRuleDocument rules1 = ScaleRuleDocument.fromTd(doc1);
        String text1 = Td.write(rules1.toTd());
        ScaleRuleDocument back1 = ScaleRuleDocument.fromTd(Td.parse(text1));
        check("ScaleRuleDocument: toTd→write→parse→fromTd 往返恒等（固定字段序 type/tag/scale，2 条）",
                back1.entries().equals(rules1.entries())
                        && back1.entries().size() == 2
                        && back1.entries().get(0).type() == ScaleType.WIDTH
                        && back1.entries().get(0).tag().equals("minecraft:zombie")
                        && back1.entries().get(0).scale() == 1.5
                        && back1.entries().get(1).type() == ScaleType.HEIGHT);
        check("ScaleRuleDocument: toTd 连跑两遍同字节",
                Td.write(rules1.toTd()).equals(text1)
                        && Td.write(rules1.toTd()).equals(Td.write(rules1.toTd())));

        // Duplicate type+tag: first occurrence wins (the later 5.0 is dropped, never applied).
        TdTable dupDoc = ruleDoc(
                ruleEntry("width", "minecraft:zombie", 8.0),
                ruleEntry("height", "minecraft:zombie", 0.25),
                ruleEntry("width", "minecraft:zombie", 5.0));
        ScaleRuleDocument dup = ScaleRuleDocument.fromTd(dupDoc);
        check("ScaleRuleDocument: 重复 type+tag 首现胜出（装载后 2 条，width/zombie 取 8.0 非 5.0）",
                dup.entries().size() == 2
                        && dup.entries().get(0).type() == ScaleType.WIDTH
                        && dup.entries().get(0).scale() == 8.0
                        && dup.entries().get(0).tag().equals("minecraft:zombie")
                        && dup.rule(ScaleType.WIDTH, "minecraft:zombie").scale() == 8.0);
        ScaleData droppedCheck = dup.applyRules(
                ScaleData.of("minecraft:zombie"), Set.of("minecraft:zombie"));
        check("ScaleRuleDocument: 首现胜出丢弃的重复规则绝不被应用（WIDTH 恒 8.0 而非 5.0）",
                droppedCheck.get(ScaleType.WIDTH) == 8.0
                        && droppedCheck.get(ScaleType.HEIGHT) == 0.25);

        // applyRules override semantics: later matching rule overrides an earlier one; the rule wins
        // over the documented base/neutral value.
        TdTable overDoc = ruleDoc(
                ruleEntry("width", "minecraft:zombie", 8.0),
                ruleEntry("height", "minecraft:zombie", 0.25),
                ruleEntry("width", "minecraft:hostile", 4.0));
        ScaleRuleDocument over = ScaleRuleDocument.fromTd(overDoc);
        ScaleData applied = over.applyRules(
                ScaleData.of("minecraft:zombie").with(ScaleType.BASE, 2.0),
                Set.of("minecraft:zombie", "minecraft:hostile"));
        check("ScaleRuleDocument.applyRules: 覆盖语义（后命中 hostile 宽 4.0 覆盖先 zombie 宽 8.0，规则胜出文档化 BASE）",
                applied.get(ScaleType.WIDTH) == 4.0
                        && applied.get(ScaleType.HEIGHT) == 0.25
                        && applied.get(ScaleType.BASE) == 2.0);
    }

    // ---------- (3) tag scaling (ScaledEntityData) ----------

    private static void tagScaling() {
        // tagScales lexicographic order regardless of insertion order.
        Map<String, Double> unordered = new LinkedHashMap<>();
        unordered.put("minecraft:zombie", 2.0);
        unordered.put("minecraft:hostile", 3.0);
        unordered.put("minecraft:player", 1.5);
        ScaledEntityData tags = new ScaledEntityData("minecraft:zombie", unordered);
        List<String> keyed = new ArrayList<>(tags.tagScales().keySet());
        List<String> sorted = new ArrayList<>(unordered.keySet());
        sorted.sort(null);
        check("ScaledEntityData: tagScales 字典序（TreeMap String 自然序，与插序无关）",
                keyed.equals(sorted)
                        && keyed.equals(List.of("minecraft:hostile", "minecraft:player", "minecraft:zombie")));

        // merge determinism: multiplicative fold-in, identical across two runs + a rebuild.
        ScaleData base = ScaleData.of("minecraft:zombie")
                .with(ScaleType.BASE, 2.0)
                .with(ScaleType.WIDTH, 4.0);
        String m1 = digest(tags.merge("minecraft:zombie", base));
        String m2 = digest(tags.merge("minecraft:zombie", base));
        String rebuild = digest(new ScaledEntityData("minecraft:zombie", unordered)
                .merge("minecraft:zombie",
                        ScaleData.of("minecraft:zombie").with(ScaleType.BASE, 2.0).with(ScaleType.WIDTH, 4.0)));
        check("ScaledEntityData.merge: 乘法并入连跑两遍摘要一致（BASE=4.0, WIDTH=8.0, 其余 neutral）",
                m1.equals(m2) && m1.equals(rebuild) && m1.startsWith("base=4.0,width=8.0,"));
    }

    // ---------- (4) wiring contract (static inventory, load-only) ----------

    private static void wiringContract() {
        String scaleRuntime = "io.toterra.subterra.runtime.scale.ScaleRuntime";
        check("wiring: runtime.scale.ScaleRuntime 类存在（只加载不初始化，纯 JVM 不触发 MC LogUtils）",
                classExists(scaleRuntime));
        check("wiring: ScaleRuntime 类字节含门控属性串 'subterra.probe.scale'",
                classBytesContain(scaleRuntime, "subterra.probe.scale"));
        check("wiring: ScaleRuntime 类字节含 marker 前缀 '[Subterra scale]'",
                classBytesContain(scaleRuntime, "[Subterra scale]"));
        // 真实生命周期由 boot E2E 覆盖：AsyncE2EProbe scaleOk 槽断言字面量的静态存在性。
        check("wiring: AsyncE2EProbe 类字节含 scaleOk 断言（'[Subterra scale]' marker，真实 boot 生命周期覆盖）",
                classBytesContain("io.toterra.subterra.probes.AsyncE2EProbe", "[Subterra scale]"));
    }

    /** 只加载不初始化地确认类存在。Loads without initializing — presence only. */
    private static boolean classExists(String fqcn) {
        try {
            Class.forName(fqcn, false, ScaleProbe.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** 断言类的 .class 字节包含某字符串字面量（常量池 UTF-8，纯 ASCII，ISO-8859-1 逐字节映射，
     *  线性 contains、无 O(n²)）。Asserts the class bytes contain a string literal (constant-pool
     *  UTF-8; the strings are pure ASCII so ISO-8859-1 is a byte-identity mapping — a linear
     *  contains scan, no O(n²)). */
    private static boolean classBytesContain(String fqcn, String literal) {
        String resource = "/" + fqcn.replace('.', '/') + ".class";
        try (InputStream in = ScaleProbe.class.getResourceAsStream(resource)) {
            if (in == null) {
                return false;
            }
            byte[] bytes = in.readAllBytes();
            return new String(bytes, StandardCharsets.ISO_8859_1).contains(literal);
        } catch (IOException e) {
            return false;
        }
    }

    // ---------- (5) third-party license inventory (classpath resources) ----------

    private static void licenseInventory() {
        String license = readResource("/META-INF/third-party/pehkui-rebuilt-3.8.5/LICENSE");
        check("license: pehkui-rebuilt-3.8.5 LICENSE classpath 资源存在", license != null);
        if (license != null) {
            check("license: pehkui-rebuilt-3.8.5 LICENSE 含 'MIT License' 与 'Virtuoel'（版权声明）",
                    license.contains("MIT License") && license.contains("Virtuoel"));
        }
        check("license: pehkui-rebuilt-3.8.5 NOTICE.md classpath 资源存在",
                readResource("/META-INF/third-party/pehkui-rebuilt-3.8.5/NOTICE.md") != null);
    }

    /** 读取 classpath 资源（缺失/异常 → null）。Reads a classpath resource (null on missing/error). */
    private static String readResource(String path) {
        try (InputStream in = ScaleProbe.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}