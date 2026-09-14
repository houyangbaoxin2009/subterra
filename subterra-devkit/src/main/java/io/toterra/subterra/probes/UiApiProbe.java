package io.toterra.subterra.probes;

import io.toterra.subterra.api.ui.UiApi;
import io.toterra.subterra.engine.ui.UiApiMirror;

import java.util.List;

/**
 * p.2.33.5（探针）— api.ui 契约面 ↔ engine 镜像逐字对照探针：把 {@code api.ui.UiApi}（对外契约）与
 * {@code engine.ui.UiApiMirror}（实现镜像）锚定为纯 JVM 断言。纯 JVM——不碰 MC、无时序、无随机；exit 0 =
 * PASS，exit 1 = FAIL；不进 mod jar。分节：
 * <ol>
 *   <li><b>api 固定序清单</b>：{@code tooltipRowOrder()} 固定 {@code hunger, saturation, ratio}、
 *       {@code pageTypes()} 固定 {@code text, recipe, entity}、{@code modFieldOrder()} 固定 {@code id, name,
 *       description, version, license}。</li>
 *   <li><b>api↔engine 镜像逐字对照</b>：调 {@link UiApiMirror} 与 {@link UiApi} 对同输入求值，饱和度增量/鸡腿
 *       数/tooltip 行序/页类型序/字段序同输入同输出（饱和度 float 逐位对照）。</li>
 *   <li><b>确定性再入 + 端点</b>：饱和度为 0 / 负值语义确定、同输入连跑同字节（确定性再入）。</li>
 * </ol>
 * 确定性纪律：固定序、无时序、无随机；失败计数只在失败路径自增；全过输出 {@code [UiApiProbe] PASS (n checks)}
 * exit 0，否则 FAIL exit 1。
 * <p>
 * p.2.33.5 (probe) — api.ui contract ↔ engine mirror verbatim probe: anchors {@code api.ui.UiApi} (external
 * contract) and {@code engine.ui.UiApiMirror} (implementation mirror) to pure-JVM assertions. Pure JVM — no MC
 * runtime, no timing, no randomness; exit 0 = PASS, exit 1 = FAIL; never shipped in the mod jar. Sections: api
 * fixed-order lists / api↔engine mirror verbatim / deterministic re-entry + endpoints.
 */
public final class UiApiProbe {

    private UiApiProbe() {
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
            apiFixedLists();
            apiMirror();
            reentryAndEndpoints();
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] probe exception: " + e);
            e.printStackTrace(System.out);
        }
        if (failures == 0) {
            System.out.println("[UiApiProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[UiApiProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    // ---------- (1) api fixed-order lists ----------

    private static void apiFixedLists() {
        check("UiApi: tooltipRowOrder() 固定（hunger,saturation,ratio）",
                UiApi.tooltipRowOrder().equals(List.of("hunger", "saturation", "ratio")));
        check("UiApi: pageTypes() 固定（text,recipe,entity）",
                UiApi.pageTypes().equals(List.of("text", "recipe", "entity")));
        check("UiApi: modFieldOrder() 固定（id,name,description,version,license）",
                UiApi.modFieldOrder().equals(List.of("id", "name", "description", "version", "license")));
    }

    // ---------- (2) api↔engine mirror verbatim ----------

    private static void apiMirror() {
        boolean sat = true;
        int[][] vals = {{1, 1}, {4, 2}, {6, 1}, {2, 0}, {8, 1}, {3, 1}, {10, 3}};
        for (int[] v : vals) {
            float a = UiApi.foodSaturationIncrement(v[0], (float) v[1]);
            float m = UiApiMirror.foodSaturationIncrement(v[0], (float) v[1]);
            sat = sat && Float.floatToIntBits(a) == Float.floatToIntBits(m);
        }
        check("mirror: foodSaturationIncrement 同输入同 float 位（7 组）", sat);
        boolean shank = true;
        int[] hungers = {1, 2, 3, 4, 5, 6, 0, -3, -5, 10};
        for (int h : hungers) {
            shank = shank && UiApi.foodShankCount(h) == UiApiMirror.foodShankCount(h);
        }
        check("mirror: foodShankCount 同输入同整数（10 组含 0/负值）", shank);
        check("mirror: tooltipRowOrder()/pageTypes()/modFieldOrder() 商 engine=api",
                UiApiMirror.tooltipRowOrder().equals(UiApi.tooltipRowOrder())
                        && UiApiMirror.pageTypes().equals(UiApi.pageTypes())
                        && UiApiMirror.modFieldOrder().equals(UiApi.modFieldOrder()));
    }

    // ---------- (3) deterministic re-entry + endpoints ----------

    private static void reentryAndEndpoints() {
        check("api: 饥饿 0 → 饱和度 0.0 + 鸡腿 0（端点语义确定）",
                Float.floatToIntBits(UiApi.foodSaturationIncrement(0, 1f)) == Float.floatToIntBits(0.0f)
                        && UiApi.foodShankCount(0) == 0);
        check("api: 负值语义（负饥饿鸡腿取绝对值、负饱和度增量负向）",
                UiApi.foodShankCount(-3) == 2 && UiApi.foodShankCount(-6) == 3
                        && UiApi.foodSaturationIncrement(2, -1f) < 0f);
        check("reflect: 确定性再入（固定清单两次连跑同字节）",
                UiApi.tooltipRowOrder().equals(UiApi.tooltipRowOrder())
                        && UiApi.pageTypes().equals(UiApi.pageTypes())
                        && UiApiMirror.modFieldOrder().equals(UiApiMirror.modFieldOrder())
                        && Float.floatToIntBits(UiApi.foodSaturationIncrement(4, 2f))
                        == Float.floatToIntBits(UiApi.foodSaturationIncrement(4, 2f)));
    }
}