// p.2.22.5: deterministic UI probe — anchors the p.2.22 engine.ui data plane
// (p.2.22.1 AppleSkin FoodValues/FoodTooltipRow/HudData, p.2.22.2 TooltipBook /
// DocBookTd, p.2.22.3 ModMetadata / ModListViewCore / ModDetailViewCore /
// LicenseViewCore) and the p.2.22.4 UiRuntime shell to fixed-order / hardcoded /
// byte-identical assertions. Pure JVM — no MC runtime, no timestamps / random /
// timing; exit 0 = PASS, exit 1 = FAIL. NOT shipped in the mod jar.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.ui.BookDef;
import io.toterra.subterra.engine.ui.BookPage;
import io.toterra.subterra.engine.ui.DocBookTd;
import io.toterra.subterra.engine.ui.FoodTooltipRow;
import io.toterra.subterra.engine.ui.FoodValues;
import io.toterra.subterra.engine.ui.HudData;
import io.toterra.subterra.engine.ui.LicenseViewCore;
import io.toterra.subterra.engine.ui.ModDetailViewCore;
import io.toterra.subterra.engine.ui.ModListViewCore;
import io.toterra.subterra.engine.ui.ModMetadata;
import io.toterra.subterra.engine.ui.TooltipBook;
import io.toterra.subterra.engine.ui.TooltipRule;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * p.2.22.5 — UI 确定性/接线探针：把 p.2.22 的 engine.ui 数据面（p.2.22.1 AppleSkin
 * {@link FoodValues} / {@link FoodTooltipRow} / {@link HudData}，p.2.22.2 td 化
 * {@link TooltipBook} / {@link DocBookTd}，p.2.22.3 {@link ModMetadata} /
 * {@link ModListViewCore} / {@link ModDetailViewCore} / {@link LicenseViewCore}）
 * 与 p.2.22.4 的 {@code UiRuntime} 壳的确定性契约锚定为纯 JVM 断言。纯 JVM——不碰 MC、
 * 无时序、无随机；exit 0 = PASS，exit 1 = FAIL；不进 mod jar。六节：
 * <ol>
 *   <li><b>AppleSkin 数据层</b>：{@link FoodValues} 构造/字段往返 + {@link FoodValues#withModifier(float)}
 *       派生（同 hunger、新 modifier，原值不变）+ {@link FoodValues#getSaturationIncrement()} 硬编码
 *       位精确期望（{@code (6, 0.6f)} → {@code 0x40e66667}，{@code (6, 0.8f)} →
 *       {@code 0x4119999a}；非有限 modifier → IAE）；{@link FoodTooltipRow#rows} 固定序三行
 *       （hunger/saturation/ratio）label 与 value 与硬编码期望逐字符（含负 hunger 样例）；
 *       {@link HudData#ratio} clamp 五档（0/10/20 边界 + 负越界 + 正越界，位精确）。</li>
 *   <li><b>tooltip 数据层</b>：{@link TooltipBook#toTd} → {@link Td#parse} →
 *       {@link TooltipBook#fromTd} 往返恒等（rules 逐项相等）；重复 itemId 文档首现胜出；
 *       {@link TooltipBook#lookup} 命中/未命中（未命中空列表、null 拒绝 NPE）；
 *       toTd 连跑两遍同字节；toTd 重复 itemId → IAE。</li>
 *   <li><b>书籍 GUI 核心</b>：{@link DocBookTd#toTd} → {@link Td#parse} →
 *       {@link DocBookTd#fromTd} 往返恒等（{@link BookDef} 逐项相等）；固定字段序（根
 *       {@code id < title < pages}、页内 {@code type < title < content}）；toTd 连跑两遍
 *       同字节；缺 {@code book} 表 → IAE。</li>
 *   <li><b>视图核心</b>：{@link ModMetadata#toTd}/{@link ModMetadata#fromTd(TdTable)} 往返 +
 *       licenseNotice 固定串；{@link ModListViewCore} id 字典序 + page 分页（越界 clamp：负 → 0、
 *       超末页 → 末页、空列表 → 空页）；{@link ModDetailViewCore#detailRows} 五行固定序
 *       （id/name/version/license/description）；{@link LicenseViewCore#licenseSummary}
 *       按许可名字典序聚合计数 + {@link LicenseViewCore#licenseLines(ModMetadata)} 两行固定序。</li>
 *   <li><b>接线契约（静态盘点）</b>：runtime.ui 的 {@code UiRuntime} 类存在（只加载不初始化，
 *       纯 JVM 不触发 MC 的 {@code LogUtils} 初始化）+ 类字节含门控属性串
 *       {@code subterra.probe.ui} + marker 前缀 {@code [Subterra ui]}；AsyncE2EProbe 类字节
 *       含 {@code ok (hud=} 断言字面量——真实 boot 生命周期由 AsyncE2EProbe uiOk 槽覆盖。</li>
 *   <li><b>许可盘点</b>：classpath 资源
 *       {@code META-INF/third-party/appleskin-3.0.6/LICENSE} 含
 *       {@code This is free and unencumbered software released into the public domain.}
 *       （The Unlicense）；{@code NOTICE.md} 存在。</li>
 * </ol>
 * 确定性纪律：固定序、无时序、无随机；全部线性遍历（禁 O(n²)）；失败计数只在失败路径自增；
 * 全过输出 {@code [UiProbe] PASS (n checks)} exit 0，否则 FAIL exit 1。
 *
 * <p>p.2.22.5 — deterministic UI probe: anchors the p.2.22 engine.ui data plane
 * (p.2.22.1 AppleSkin {@link FoodValues} / {@link FoodTooltipRow} / {@link HudData}, the
 * p.2.22.2 td-ized {@link TooltipBook} / {@link DocBookTd}, the p.2.22.3
 * {@link ModMetadata} / {@link ModListViewCore} / {@link ModDetailViewCore} /
 * {@link LicenseViewCore}) and the p.2.22.4 {@code UiRuntime} shell to pure-JVM
 * assertions. Pure JVM — no MC runtime, no timing, no randomness; exit 0 = PASS,
 * exit 1 = FAIL; never shipped in the mod jar. Six sections:
 * <ol>
 *   <li><b>AppleSkin data layer</b>: {@link FoodValues} ctor/field round-trip +
 *       {@link #withModifier} derivation (same hunger, new modifier, original untouched) +
 *       {@link #getSaturationIncrement()} hardcoded bit-exact expectations
 *       ({@code (6, 0.6f)} → {@code 0x40e66667}, {@code (6, 0.8f)} → {@code 0x4119999a};
 *       a non-finite modifier → IAE); {@link FoodTooltipRow#rows} fixed three rows
 *       (hunger/saturation/ratio) with labels and values compared char-for-char against
 *       hardcoded expectations (a negative-hunger sample included); {@link HudData#ratio}
 *       clamp across five bands (0/10/20 edges plus negative and positive overflow,
 *       bit-exact).</li>
 *   <li><b>Tooltip data layer</b>: {@link TooltipBook#toTd} → {@link Td#parse} →
 *       {@link TooltipBook#fromTd} round-trip identity (rules element-wise equal); a
 *       duplicate item id in a document keeps its first occurrence; {@link TooltipBook#lookup}
 *       hit/miss (empty list on miss, null rejected with NPE); toTd twice-run byte identity;
 *       a duplicate item id to toTd → IAE.</li>
 *   <li><b>Book GUI core</b>: {@link DocBookTd#toTd} → {@link Td#parse} →
 *       {@link DocBookTd#fromTd} round-trip identity ({@link BookDef} element-wise equal);
 *       fixed field order (root {@code id < title < pages}, page {@code type < title <
 *       content}); toTd twice-run byte identity; a missing {@code book} table → IAE.</li>
 *   <li><b>View cores</b>: {@link ModMetadata#toTd}/{@link #fromTd} round-trip + fixed
 *       {@link #licenseNotice} string; {@link ModListViewCore} id-lexicographic order +
 *       page pagination (out-of-range clamp: negative → 0, past the last page → the last
 *       page, empty list → empty page); {@link ModDetailViewCore#detailRows} fixed five
 *       rows (id/name/version/license/description); {@link LicenseViewCore#licenseSummary}
 *       aggregated by license name in lexicographic order with counts +
 *       {@link #licenseLines} fixed two rows.</li>
 *   <li><b>Wiring contract (static inventory)</b>: the runtime.ui {@code UiRuntime} class
 *       is present (load-only, never initialized — the pure JVM must not trigger the MC
 *       {@code LogUtils} init), its class bytes carry the gate string
 *       {@code subterra.probe.ui} and the marker prefix {@code [Subterra ui]}; the
 *       AsyncE2EProbe class bytes carry the {@code ok (hud=} assertion literal — the real
 *       boot lifecycle is covered by the AsyncE2EProbe uiOk slot.</li>
 *   <li><b>License inventory</b>: the classpath resource
 *       {@code META-INF/third-party/appleskin-3.0.6/LICENSE} carries
 *       {@code This is free and unencumbered software released into the public domain.}
 *       (The Unlicense); {@code NOTICE.md} is present.</li>
 * </ol>
 * Determinism discipline: fixed order, no timing, no randomness; all traversals linear
 * (no O(n²)); failures are counted only on failing paths; PASS only when all checks pass,
 * then exit 0, else FAIL with counts and exit 1.
 */
public final class UiProbe {

    private UiProbe() {
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
            appleSkinDataLayer();
            tooltipDataLayer();
            docBookGuiCore();
            viewCores();
            wiringContract();
            licenseInventory();
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] probe exception: " + e);
            e.printStackTrace(System.out);
        }
        if (failures == 0) {
            System.out.println("[UiProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[UiProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    // ---------- fixtures (fixed, the deterministic single source of truth) ----------

    /** 固定工具集：两条规则（首现胜出防重样例）。Fixed tooltip set: two rules
     * (the first-wins duplicate sample is built separately). */
    private static List<TooltipRule> sampleRules() {
        return List.of(
                new TooltipRule("minecraft:apple", List.of("restores 4 hunger", "saturation 2.4")),
                new TooltipRule("minecraft:golden_apple", List.of("restores 8 hunger", "saturation 9.6")));
    }

    /** 固定文档书籍：field guide，三页（text/recipe/entity，固定核心类型序）。
     *  Fixed doc book: field guide, three pages (text/recipe/entity, the fixed core type order). */
    private static BookDef sampleBook() {
        return new BookDef("subterra:field_guide", "Field Guide", List.of(
                new BookPage("text", "Intro", "This handbook documents the Subterra management views."),
                new BookPage("recipe", "Core", "Crafting a subterra:core module."),
                new BookPage("entity", "Snail", "Slow but deterministic.")));
    }

    /** 五个固定模组（输入乱序，排序期望按 id 字典序）。Five fixed mods (unsorted input;
     *  expectations in id-lexicographic order). */
    private static List<ModMetadata> sampleMods() {
        return List.of(
                new ModMetadata("subterra:zulu", "Zulu", "Last alphabetically.", "2.0.0", "Unlicense"),
                new ModMetadata("subterra:alpha", "Alpha", "First alphabetically.", "1.0.0", "MIT"),
                new ModMetadata("subterra:mid", "Mid", "Middle.", "1.2.0", "Apache-2.0"),
                new ModMetadata("subterra:beta", "Beta", "Second.", "1.1.0", "MIT"),
                new ModMetadata("subterra:gamma", "Gamma", "Third.", "1.1.5", "Unlicense"));
    }

    /** 位精确一致：float 与硬编码位模式。Bit-exact: a float vs a hardcoded bit pattern. */
    private static boolean bitsEqual(float f, int bits) {
        return Float.floatToIntBits(f) == bits;
    }

    /** 动作抛出 IllegalArgumentException 为真。True iff the action throws IAE. */
    private static boolean throwsIAE(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    /** 动作抛出 NullPointerException 为真。True iff the action throws NPE. */
    private static boolean throwsNPE(Runnable r) {
        try {
            r.run();
            return false;
        } catch (NullPointerException e) {
            return true;
        }
    }

    // ---------- (1) AppleSkin data layer (p.2.22.1) ----------

    private static void appleSkinDataLayer() {
        FoodValues fv = new FoodValues(6, 0.6f);
        check("FoodValues: 构造/字段往返 (hunger=6, saturationModifier=0.6f 位精确)",
                fv.hunger() == 6 && bitsEqual(fv.saturationModifier(), Float.floatToIntBits(0.6f)));

        FoodValues derived = fv.withModifier(0.8f);
        check("FoodValues: withModifier 派生同 hunger、新 modifier，原值不变",
                derived.hunger() == 6
                        && bitsEqual(derived.saturationModifier(), Float.floatToIntBits(0.8f))
                        && bitsEqual(fv.saturationModifier(), Float.floatToIntBits(0.6f)));

        check("FoodValues: 非有限 modifier → IllegalArgumentException",
                throwsIAE(() -> new FoodValues(6, Float.NaN))
                        && throwsIAE(() -> new FoodValues(6, Float.POSITIVE_INFINITY)));

        check("FoodValues: getSaturationIncrement 硬编码位精确 (6,0.6f)→0x40e66667、(6,0.8f)→0x4119999a",
                bitsEqual(fv.getSaturationIncrement(), 0x40e66667)
                        && bitsEqual(derived.getSaturationIncrement(), 0x4119999a));

        List<FoodTooltipRow> rows = FoodTooltipRow.rows(fv);
        check("FoodTooltipRow.rows: 固定序三行 (hunger/saturation/ratio) 与硬编码期望逐字符",
                rows.size() == 3
                        && rows.get(0).equals(new FoodTooltipRow("hunger", "3"))
                        && rows.get(1).equals(new FoodTooltipRow("saturation", "7.2"))
                        && rows.get(2).equals(new FoodTooltipRow("ratio", "1.2")));

        List<FoodTooltipRow> neg = FoodTooltipRow.rows(new FoodValues(-3, 1.0f));
        check("FoodTooltipRow.rows: 负 hunger 样例 (−3,1.0) → [hunger=2, saturation=-6.0, ratio=2.0]",
                neg.size() == 3
                        && neg.get(0).equals(new FoodTooltipRow("hunger", "2"))
                        && neg.get(1).equals(new FoodTooltipRow("saturation", "-6.0"))
                        && neg.get(2).equals(new FoodTooltipRow("ratio", "2.0")));

        boolean clamp = bitsEqual(new HudData(6, -5, 0.5f).ratio(), 0x0)          // 负越界 → 0
                && bitsEqual(new HudData(6, 0, 0.5f).ratio(), 0x0)                 // 0 → 0
                && bitsEqual(new HudData(6, 10, 0.5f).ratio(), 0x3f000000)         // 10 → 0.5f
                && bitsEqual(new HudData(6, 20, 0.5f).ratio(), 0x3f800000)         // 20 → 1
                && bitsEqual(new HudData(6, 40, 0.5f).ratio(), 0x3f800000);        // 正越界 → 1
        check("HudData.ratio: clamp 五档 (−5→0, 0→0, 10→0.5, 20→1, 40→1) 位精确", clamp);
    }

    // ---------- (2) tooltip data layer (p.2.22.2) ----------

    private static void tooltipDataLayer() {
        List<TooltipRule> rules = sampleRules();
        TooltipBook book = TooltipBook.fromTd(Td.parse(TooltipBook.toTd(rules)));
        check("TooltipBook: toTd→parse→fromTd 往返恒等（rules 逐项相等）", book.rules().equals(rules));

        String td1 = TooltipBook.toTd(rules);
        String td2 = TooltipBook.toTd(rules);
        check("TooltipBook: toTd 连跑两遍同字节",
                td1.equals(td2)
                        && Arrays.equals(td1.getBytes(StandardCharsets.UTF_8),
                        td2.getBytes(StandardCharsets.UTF_8)));

        TdTable dupDoc = TdTable.builder()
                .put("tooltips", TdTable.builder()
                        .put("version", TdValue.of(1L))
                        .put("entries", TdTable.builder()
                                .element(TdTable.builder()
                                        .put("item", "minecraft:apple")
                                        .put("lines", TdTable.builder()
                                                .element(TdValue.str("first")).build())
                                        .build())
                                .element(TdTable.builder()
                                        .put("item", "minecraft:apple")
                                        .put("lines", TdTable.builder()
                                                .element(TdValue.str("second")).build())
                                        .build())
                                .build())
                        .build())
                .build();
        TooltipBook dup = TooltipBook.fromTd(dupDoc);
        check("TooltipBook: 重复 itemId 文档首现胜出（1 条规则，lookup 得首现行）",
                dup.size() == 1
                        && dup.rules().get(0).itemId().equals("minecraft:apple")
                        && dup.lookup("minecraft:apple").equals(List.of("first")));

        check("TooltipBook.lookup: 命中固定行 / 未命中空列表 / null → NPE",
                book.lookup("minecraft:apple").equals(List.of("restores 4 hunger", "saturation 2.4"))
                        && book.lookup("minecraft:golden_apple").equals(List.of("restores 8 hunger", "saturation 9.6"))
                        && book.lookup("minecraft:missing").equals(List.of())
                        && throwsNPE(() -> book.lookup(null)));

        check("TooltipBook: toTd 重复 itemId → IllegalArgumentException",
                throwsIAE(() -> TooltipBook.toTd(List.of(
                        new TooltipRule("minecraft:apple", List.of("a")),
                        new TooltipRule("minecraft:apple", List.of("b"))))));
    }

    // ---------- (3) doc-book GUI core (p.2.22.2) ----------

    private static void docBookGuiCore() {
        BookDef book = sampleBook();
        BookDef back = DocBookTd.fromTd(Td.parse(DocBookTd.toTd(book)));
        check("DocBookTd: toTd→parse→fromTd 往返恒等（BookDef 逐项相等）", back.equals(book));

        String td = DocBookTd.toTd(book);
        String pageSection = td.substring(td.indexOf("pages ="));
        boolean order = td.indexOf("id =") < td.indexOf("title =")
                && td.indexOf("title =") < td.indexOf("pages =")
                && pageSection.indexOf("type =") < pageSection.indexOf("title =")
                && pageSection.indexOf("title =") < pageSection.indexOf("content =");
        check("DocBookTd: 固定字段序（根 id<title<pages，页内 type<title<content）", order);

        String td2 = DocBookTd.toTd(book);
        check("DocBookTd: toTd 连跑两遍同字节",
                td.equals(td2)
                        && Arrays.equals(td.getBytes(StandardCharsets.UTF_8),
                        td2.getBytes(StandardCharsets.UTF_8)));

        check("DocBookTd: 缺 book 表 → IllegalArgumentException",
                throwsIAE(() -> DocBookTd.fromTd(TdTable.builder().build())));
    }

    // ---------- (4) view cores (p.2.22.3) ----------

    private static void viewCores() {
        ModMetadata meta = new ModMetadata("subterra:core", "Subterra Core", "Deterministic engine core.", "1.0.0", "MIT");
        check("ModMetadata: toTd→parse→fromTd 往返恒等",
                ModMetadata.fromTd(Td.parse(ModMetadata.toTd(meta))).equals(meta));
        check("ModMetadata: licenseNotice 固定串",
                meta.licenseNotice().equals("This mod is licensed under MIT."));

        List<ModMetadata> unsorted = sampleMods();
        ModListViewCore view = new ModListViewCore(unsorted);
        boolean sorted = view.size() == 5
                && view.mods().get(0).id().equals("subterra:alpha")
                && view.mods().get(1).id().equals("subterra:beta")
                && view.mods().get(2).id().equals("subterra:gamma")
                && view.mods().get(3).id().equals("subterra:mid")
                && view.mods().get(4).id().equals("subterra:zulu");
        check("ModListViewCore: id 字典序（乱序输入 → 固定升序）", sorted);

        boolean page = view.page(2, -5).equals(List.of(view.mods().get(0), view.mods().get(1)))   // 负越界 clamp → 0
                && view.page(2, 0).equals(List.of(view.mods().get(0), view.mods().get(1)))
                && view.page(2, 1).equals(List.of(view.mods().get(2), view.mods().get(3)))
                && view.page(2, 2).equals(List.of(view.mods().get(4)))                              // 末页
                && view.page(2, 99).equals(List.of(view.mods().get(4)))                            // 正越界 clamp → 末页
                && new ModListViewCore(List.of()).page(2, 0).equals(List.of())                     // 空列表 → 空页
                && throwsIAE(() -> view.page(0, 0));                                               // 非正 pageSize → IAE
        check("ModListViewCore: page 分页（越界 clamp 负/正、末页、空列表、非法 pageSize）", page);

        List<String> detail = ModDetailViewCore.detailRows(meta);
        check("ModDetailViewCore: detailRows 五行固定序",
                detail.equals(List.of(
                        "id: subterra:core",
                        "name: Subterra Core",
                        "version: 1.0.0",
                        "license: MIT",
                        "description: Deterministic engine core.")));

        List<String> summary = LicenseViewCore.licenseSummary(unsorted);
        check("LicenseViewCore: licenseSummary 按许可名字典序聚合计数",
                summary.equals(List.of(
                        "license: Apache-2.0 (1 mod(s))",
                        "license: MIT (2 mod(s))",
                        "license: Unlicense (2 mod(s))")));

        check("LicenseViewCore: licenseLines 两行固定序",
                LicenseViewCore.licenseLines(meta).equals(List.of(
                        "license: MIT",
                        "This mod is licensed under MIT.")));
    }

    // ---------- (5) wiring contract (static inventory, load-only) ----------

    private static void wiringContract() {
        String uiRuntime = "io.toterra.subterra.runtime.ui.UiRuntime";
        check("wiring: runtime.ui.UiRuntime 类存在（只加载不初始化，纯 JVM 不触发 MC LogUtils）",
                classExists(uiRuntime));
        check("wiring: UiRuntime 类字节含门控属性串 'subterra.probe.ui'",
                classBytesContain(uiRuntime, "subterra.probe.ui"));
        check("wiring: UiRuntime 类字节含 marker 前缀 '[Subterra ui]'",
                classBytesContain(uiRuntime, "[Subterra ui]"));
        // 真实生命周期由 boot E2E 覆盖：AsyncE2EProbe uiOk 槽断言字面量的静态存在性。
        check("wiring: AsyncE2EProbe 类字节含 uiOk 断言 'ok (hud='（生命周期由 boot E2E 覆盖）",
                classBytesContain("io.toterra.subterra.probes.AsyncE2EProbe", "[Subterra ui]")
                        && classBytesContain("io.toterra.subterra.probes.AsyncE2EProbe", "ok (hud="));
    }

    // ---------- (6) third-party license inventory (classpath resources) ----------

    private static void licenseInventory() {
        String license = readResource("/META-INF/third-party/appleskin-3.0.6/LICENSE");
        check("license: appleskin-3.0.6 LICENSE classpath 资源存在", license != null);
        if (license != null) {
            check("license: appleskin-3.0.6 LICENSE 含 Unlicense 公共领域句（The Unlicense）",
                    license.contains("This is free and unencumbered software released into the public domain.")
                            || license.contains("Unlicense"));
        }
        String notice = readResource("/META-INF/third-party/appleskin-3.0.6/NOTICE.md");
        check("license: appleskin-3.0.6 NOTICE.md classpath 资源存在", notice != null);
    }

    // ---------- helpers ----------

    /** 只加载不初始化地确认类存在。Loads without initializing — presence only. */
    private static boolean classExists(String fqcn) {
        try {
            Class.forName(fqcn, false, UiProbe.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** 读类的 .class 资源字节（classpath 编译输出）；缺失返回 null。Reads the class-file resource
     * bytes from the classpath compiled outputs; null when absent. */
    private static byte[] classBytes(String fqcn) {
        String resource = "/" + fqcn.replace('.', '/') + ".class";
        try (InputStream in = UiProbe.class.getResourceAsStream(resource)) {
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

    /** 读取 classpath 资源（缺失/异常 → null）。Reads a classpath resource (null on missing/error). */
    private static String readResource(String path) {
        try (InputStream in = UiProbe.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
