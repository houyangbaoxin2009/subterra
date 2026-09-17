package io.toterra.subterra.probes;

import io.toterra.subterra.engine.ui.kit.BoxStyle;
import io.toterra.subterra.engine.ui.kit.LayoutEngine;
import io.toterra.subterra.engine.ui.kit.LocaleTable;
import io.toterra.subterra.engine.ui.kit.Placement;
import io.toterra.subterra.engine.ui.kit.ThemeTokens;
import io.toterra.subterra.engine.ui.kit.UiNode;
import io.toterra.subterra.engine.ui.kit.WidgetKind;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * p.2.37.1（探针）— UI 库引擎核心确定性探针：组件模型 / 盒样式 / 主题 token / 底层多语言表 /
 * 布局引擎的纯 JVM 锚定。纯 JVM——不碰 MC、无时序、无随机；exit 0 = PASS，exit 1 = FAIL；
 * 不进 mod jar。分节：
 * <ol>
 *   <li><b>控件种类</b>：固定序 11 种 + {@code byId} 大小写不敏感往返 + 非法确定性拒绝。</li>
 *   <li><b>盒样式</b>：负值确定性拒绝（IAE）、DEFAULT 全零、horizontal/vertical 语义。</li>
 *   <li><b>主题 token</b>：NEUTRAL 中性起点（消费者可整包替换——视觉由消费者决定）、非法值
 *       确定性拒绝。</li>
 *   <li><b>底层多语言</b>：locale 规范化与确定性拒绝、fallback 链（zh_cn→en_us）、精确命中、
 *       整链缺失为空、排序快照确定性、同 key 同 locale 同字节再入。</li>
 *   <li><b>布局 golden</b>：column（grow=y 均分剩余）、row（grow=x 均分剩余 + 外边距偏移）、
 *       grid（行主序 floor 单元）、stack（子拉伸内矩形）、重复 id 确定性拒绝、两连跑同字节。</li>
 * </ol>
 * <p>
 * p.2.37.1 (probe) — the UI-kit engine-core determinism probe: pure-JVM anchoring of the
 * component model / box style / theme tokens / bottom-layer locale table / layout engine.
 * Pure JVM — no MC, no timing, no randomness; exit 0 = PASS, exit 1 = FAIL; never shipped in
 * the mod jar. Sections: widget kinds (fixed order + byId round-trip + deterministic
 * rejection) / box style (negative rejection, DEFAULT, insets) / theme tokens (NEUTRAL
 * starting point, consumer-decided visuals, range rejection) / bottom-layer multilingual
 * (locale normalization + fallback chain + exact hit + whole-chain miss + sorted snapshots +
 * re-entry) / layout goldens (column grow-y, row grow-x with margins, grid row-major floor,
 * stack stretch, duplicate-id rejection, byte-identical re-entry).
 */
public final class UiKitProbe {

    private UiKitProbe() {
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

    private static boolean throwsIAE(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean throwsNPE(Runnable r) {
        try {
            r.run();
            return false;
        } catch (NullPointerException e) {
            return true;
        }
    }

    public static void main(String[] args) {
        try {
            widgetKinds();
            boxStyle();
            themeTokens();
            localeTable();
            layoutGoldens();
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] probe exception: " + e);
            e.printStackTrace(System.out);
        }
        if (failures == 0) {
            System.out.println("[UiKitProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[UiKitProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    // ---------- (1) widget kinds ----------

    private static void widgetKinds() {
        List<String> expected = List.of("panel", "stack", "row", "column", "grid", "label",
                "button", "text_field", "toggle", "slider", "list", "nine_slice");
        check("kind: 固定序 12 种逐名核对",
                java.util.stream.Stream.of(WidgetKind.values()).map(WidgetKind::id).toList()
                        .equals(expected));
        check("kind: byId 大小写/空白容忍（' ROW '→row，'Nine_Slice'→nine_slice）",
                WidgetKind.byId(" ROW ") == WidgetKind.ROW
                        && WidgetKind.byId("Nine_Slice") == WidgetKind.NINE_SLICE
                        && WidgetKind.byId(WidgetKind.PANEL.id()) == WidgetKind.PANEL);
        check("kind: 非法/空/null 确定性拒绝（IAE/NPE）",
                throwsIAE(() -> WidgetKind.byId("gridx"))
                        && throwsIAE(() -> WidgetKind.byId(""))
                        && throwsNPE(() -> WidgetKind.byId(null)));
    }

    // ---------- (2) box style ----------

    private static void boxStyle() {
        check("box: DEFAULT 全零",
                BoxStyle.DEFAULT.horizontal() == 0 && BoxStyle.DEFAULT.vertical() == 0
                        && BoxStyle.DEFAULT.leftInset() == 0 && BoxStyle.DEFAULT.topInset() == 0);
        check("box: 负值确定性拒绝（IAE）",
                throwsIAE(() -> new BoxStyle(-1, 0, 0, 0, 0, 0, 0, 0, 0))
                        && throwsIAE(() -> new BoxStyle(0, 0, 0, 0, -1, 0, 0, 0, 0))
                        && throwsIAE(() -> new BoxStyle(0, 0, 0, 0, 0, 0, 0, 0, -2)));
        BoxStyle b = new BoxStyle(2, 1, 3, 4, 5, 6, 7, 8, 9);
        check("box: horizontal/vertical/inset 语义（margin+2*border+padding / leftInset）",
                b.horizontal() == 2 + 3 + 2 * 5 + 6 + 8
                        && b.vertical() == 1 + 4 + 2 * 5 + 7 + 9
                        && b.leftInset() == 2 + 5 + 6 && b.topInset() == 1 + 5 + 7);
    }

    // ---------- (3) theme tokens ----------

    private static void themeTokens() {
        check("theme: NEUTRAL 中性起点可重入（同值同字节）",
                ThemeTokens.NEUTRAL.equals(new ThemeTokens(
                        ThemeTokens.NEUTRAL.colorBackground(), ThemeTokens.NEUTRAL.colorSurface(),
                        ThemeTokens.NEUTRAL.colorText(), ThemeTokens.NEUTRAL.colorAccent(),
                        ThemeTokens.NEUTRAL.colorBorder(), ThemeTokens.NEUTRAL.fontFamily(),
                        ThemeTokens.NEUTRAL.fontSize(), ThemeTokens.NEUTRAL.spacingUnit(),
                        ThemeTokens.NEUTRAL.spacingGap(), ThemeTokens.NEUTRAL.cornerRadius(),
                        ThemeTokens.NEUTRAL.animationDurationMs(),
                        ThemeTokens.NEUTRAL.animationCurve())));
        check("theme: 越界确定性拒绝（IAE）",
                throwsIAE(() -> new ThemeTokens(0, 0, 0, 0, 0, "f", 0, 4, 8, 4, 150,
                        ThemeTokens.AnimationCurve.LINEAR))
                        && throwsIAE(() -> new ThemeTokens(0, 0, 0, 0, 0, "f", 9, 4, -1, 4, 150,
                        ThemeTokens.AnimationCurve.LINEAR)));
        check("theme: 视觉由消费者决定——整包替换为另一风格后语义有效",
                new ThemeTokens(0xFFFFFFFF, 0xFF101014, 0xFF111111, 0xFFFF7043, 0xFF222226,
                        "consumer_font", 12, 6, 10, 0, 0, ThemeTokens.AnimationCurve.LINEAR)
                        .fontSize() == 12);
    }

    // ---------- (4) bottom-layer multilingual ----------

    private static void localeTable() {
        LocaleTable t = LocaleTable.empty();
        check("locale: 规范化（'ZH_CN '→zh_cn）与非法/空/null 确定性拒绝",
                LocaleTable.normalizeLocale(" ZH_CN ").equals("zh_cn")
                        && throwsIAE(() -> LocaleTable.normalizeLocale("zh!"))
                        && throwsIAE(() -> LocaleTable.normalizeLocale(""))
                        && throwsNPE(() -> LocaleTable.normalizeLocale(null)));
        t.put("subterra.ui.ok", "zh_cn", "确定");
        t.put("subterra.ui.ok", "en_us", "OK");
        t.put("subterra.ui.cancel", "en_us", "Cancel");
        check("locale: 精确命中优先于回退（zh_cn 取中文）",
                t.resolve("subterra.ui.ok", "zh_cn").equals(Optional.of("确定")));
        check("locale: fallback 链（zh_TW→en_us；en_us 无二次回退）",
                t.resolve("subterra.ui.ok", "zh_tw").equals(Optional.of("OK"))
                        && LocaleTable.fallbackChain("zh_tw").equals(List.of("zh_tw", "en_us"))
                        && LocaleTable.fallbackChain("en_us").equals(List.of("en_us")));
        check("locale: 整链缺失为空、未知 key 为空",
                t.resolve("subterra.ui.cancel", "zh_cn").equals(Optional.of("Cancel"))
                        && t.resolve("subterra.ui.nope", "zh_cn").isEmpty());
        check("locale: 排序快照确定性与再入（同 key 同 locale 同字节）",
                t.keys().equals(List.of("subterra.ui.cancel", "subterra.ui.ok"))
                        && t.localesOf("subterra.ui.ok").equals(List.of("en_us", "zh_cn"))
                        && t.resolve("subterra.ui.ok", "zh_cn").equals(t.resolve("subterra.ui.ok", "zh_cn")));
        check("locale: 空 key 写入确定性拒绝（IAE）+ null key（NPE）",
                throwsIAE(() -> t.put(" ", "en_us", "x"))
                        && throwsNPE(() -> t.put(null, "en_us", "x")));
        t.clear();
        check("locale: clear 全回落", t.keys().isEmpty());
    }

    // ---------- (5) layout goldens ----------

    private static UiNode node(String id, WidgetKind kind, int w, int h) {
        Map<String, String> props = new java.util.LinkedHashMap<>();
        if (w >= 0) {
            props.put("width", Integer.toString(w));
        }
        if (h >= 0) {
            props.put("height", Integer.toString(h));
        }
        return UiNode.leaf(id, kind, props);
    }

    private static void layoutGoldens() {
        ThemeTokens theme = ThemeTokens.NEUTRAL; // spacingGap = 8
        // column golden: root 100x60 (no padding) -> inner 100x60; title(40x10 fixed w),
        // ok(grow_y), list(h=16); gaps 2*8; ok height = 60 - 10 - 16 - 16 = 18.
        UiNode column = UiNode.container("root", WidgetKind.COLUMN,
                Map.of("width", "100", "height", "60"),
                List.of(node("title", WidgetKind.LABEL, 40, 10),
                        new UiNode("ok", WidgetKind.BUTTON, Map.of("grow_y", "true"), List.of(),
                                BoxStyle.DEFAULT),
                        node("list", WidgetKind.LIST, -1, 16)));
        List<Placement> col = LayoutEngine.layout(column, 0, 0, theme);
        check("layout column golden: root/title/ok/list 放置（含 grow=y 均分与横向拉伸）",
                col.get(0).equals(new Placement("root", WidgetKind.COLUMN, 0, 0, 100, 60))
                        && col.get(1).equals(new Placement("title", WidgetKind.LABEL, 0, 0, 40, 10))
                        && col.get(2).equals(new Placement("ok", WidgetKind.BUTTON, 0, 18, 100, 18))
                        && col.get(3).equals(new Placement("list", WidgetKind.LIST, 0, 44, 100, 16)));
        // row golden: root 100x20 (no padding) -> inner 100x20; toggle(w=12), button(grow_x),
        // slider(w=30); leftover = 100 - 12 - 30 - 2*8 = 42.
        UiNode row = UiNode.container("bar", WidgetKind.ROW,
                Map.of("width", "100", "height", "20"),
                List.of(node("t", WidgetKind.TOGGLE, 12, 12),
                        new UiNode("ok", WidgetKind.BUTTON, Map.of("grow_x", "true"), List.of(),
                                BoxStyle.DEFAULT),
                        node("s", WidgetKind.SLIDER, 30, 12)));
        List<Placement> rowPl = LayoutEngine.layout(row, 0, 0, theme);
        check("layout row golden: toggle/ok/slider 放置（grow=x 剩余 42；ok 无高 prop 走纵向拉伸 20）",
                rowPl.get(1).equals(new Placement("t", WidgetKind.TOGGLE, 0, 0, 12, 12))
                        && rowPl.get(2).equals(new Placement("ok", WidgetKind.BUTTON, 20, 0, 42, 20))
                        && rowPl.get(3).equals(new Placement("s", WidgetKind.SLIDER, 70, 0, 30, 12)));
        // grid golden: root 60x40 cols=2 gap 8 -> cell 26x16, rows=2; a b c 行主序.
        UiNode grid = UiNode.container("g", WidgetKind.GRID, Map.of("width", "60", "height", "40", "cols", "2"),
                List.of(node("a", WidgetKind.PANEL, -1, -1),
                        node("b", WidgetKind.PANEL, -1, -1),
                        node("c", WidgetKind.PANEL, -1, -1)));
        List<Placement> gridPl = LayoutEngine.layout(grid, 0, 0, theme);
        check("layout grid golden: 行主序 floor 单元（26x16）",
                gridPl.get(1).equals(new Placement("a", WidgetKind.PANEL, 0, 0, 26, 16))
                        && gridPl.get(2).equals(new Placement("b", WidgetKind.PANEL, 34, 0, 26, 16))
                        && gridPl.get(3).equals(new Placement("c", WidgetKind.PANEL, 0, 24, 26, 16)));
        // stack golden: children stretch to the inner rect in order.
        UiNode stack = UiNode.container("st", WidgetKind.STACK, Map.of("width", "50", "height", "30"),
                List.of(node("under", WidgetKind.NINE_SLICE, -1, -1),
                        node("over", WidgetKind.LABEL, 10, 10)));
        List<Placement> stackPl = LayoutEngine.layout(stack, 0, 0, theme);
        check("layout stack golden: 子拉伸内矩形（后子上层保持序）",
                stackPl.get(1).equals(new Placement("under", WidgetKind.NINE_SLICE, 0, 0, 50, 30))
                        && stackPl.get(2).equals(new Placement("over", WidgetKind.LABEL, 0, 0, 50, 30)));
        // margins offset the placement: row child with marginLeft 5, width 10 -> x = 0 + 5.
        UiNode margined = UiNode.container("m", WidgetKind.ROW, Map.of("width", "40", "height", "20"),
                List.of(new UiNode("c", WidgetKind.PANEL, Map.of("width", "10", "height", "10"),
                        List.of(), new BoxStyle(5, 2, 0, 0, 0, 0, 0, 0, 0))));
        List<Placement> mPl = LayoutEngine.layout(margined, 0, 0, theme);
        check("layout margin golden: 子外边距偏移放置（x=5, y=2）",
                mPl.get(1).equals(new Placement("c", WidgetKind.PANEL, 5, 2, 10, 10)));
        check("layout: 重复 id 确定性拒绝（IAE）",
                throwsIAE(() -> LayoutEngine.layout(UiNode.container("dup", WidgetKind.ROW,
                        Map.of(), List.of(node("dup", WidgetKind.LABEL, 1, 1))), 0, 0, theme)));
        check("layout: 确定性再入（两连跑同字节）",
                col.equals(LayoutEngine.layout(column, 0, 0, theme))
                        && rowPl.equals(LayoutEngine.layout(row, 0, 0, theme)));
    }
}
