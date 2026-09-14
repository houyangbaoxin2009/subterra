package io.toterra.subterra.probes;

import io.toterra.subterra.api.scale.ScaleApi;
import io.toterra.subterra.engine.scale.ScaleApiMirror;
import io.toterra.subterra.engine.scale.ScaleType;

import java.util.List;

/**
 * p.2.33.2（探针）— api.scale 契约面 ↔ engine 镜像逐字对照探针：把 {@code api.scale.ScaleApi}（对外契约）
 * 与 {@code engine.scale.ScaleApiMirror}（实现镜像）锚定为纯 JVM 断言。纯 JVM——不碰 MC、无时序、无随机；
 * exit 0 = PASS，exit 1 = FAIL；不进 mod jar。分节：
 * <ol>
 *   <li><b>api 维度固定 9 序</b>：{@code dimensions()} 固定 {@code base,width,height,depth,eye_height,
 *       hitbox_width,hitbox_height,model_width,model_height} + {@code dimensionCount()}=9 + 与 engine
 *       {@code ScaleType.values()} 序逐字对照。</li>
 *   <li><b>api↔engine 镜像逐字对照</b>：调 {@link ScaleApiMirror} 与 {@link ScaleApi} 对同输入求值，
 *       维度序/维度数/中性缩放/规范化/rule 字段序同输入同输出。</li>
 *   <li><b>规范化 + 确定性拒绝 + 再入</b>：大小写/空白容忍 ({@code " WIDTH "}→{@code "width"})、非法/空/
 *       null 维度名确定性拒绝、同输入连跑同结果（确定性再入）。</li>
 * </ol>
 * 确定性纪律：固定序、无时序、无随机；失败计数只在失败路径自增；全过输出 {@code [ScaleApiProbe] PASS (n
 * checks)} exit 0，否则 FAIL exit 1。
 * <p>
 * p.2.33.2 (probe) — api.scale contract ↔ engine mirror verbatim probe: anchors {@code api.scale.ScaleApi}
 * (external contract) and {@code engine.scale.ScaleApiMirror} (implementation mirror) to pure-JVM assertions.
 * Pure JVM — no MC runtime, no timing, no randomness; exit 0 = PASS, exit 1 = FAIL; never shipped in the mod jar.
 * Sections: api fixed 9-dimension order / api↔engine mirror verbatim / normalization + deterministic rejection
 * + re-entry.
 */
public final class ScaleApiProbe {

    private ScaleApiProbe() {
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

    /** True iff the action raises IllegalArgumentException. 动作抛出 IAE 为真。 */
    private static boolean throwsIAE(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    public static void main(String[] args) {
        try {
            apiFixedOrder();
            apiMirror();
            normalizationAndReject();
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] probe exception: " + e);
            e.printStackTrace(System.out);
        }
        if (failures == 0) {
            System.out.println("[ScaleApiProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[ScaleApiProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    // ---------- (1) api fixed 9-dimension order ----------

    private static void apiFixedOrder() {
        List<String> expected = List.of("base", "width", "height", "depth", "eye_height",
                "hitbox_width", "hitbox_height", "model_width", "model_height");
        check("ScaleApi: dimensions() 固定 9 维序逐名核对", ScaleApi.dimensions().equals(expected));
        check("ScaleApi: dimensionCount() == 9（= engine ScaleType.values().length）",
                ScaleApi.dimensionCount() == 9 && ScaleApi.dimensionCount() == ScaleType.values().length);
        check("ScaleApi: 与 engine ScaleType.values() 序逐字对照（form() 全量）",
                ScaleApi.dimensions().equals(
                        java.util.stream.Stream.of(ScaleType.values())
                                .map(ScaleType::form).toList()));
        check("ScaleApi: neutralScale() == 1.0 + RULE_FIELD_ORDER == type,tag,scale",
                Double.doubleToLongBits(ScaleApi.neutralScale()) == Double.doubleToLongBits(1.0D)
                        && ScaleApi.RULE_FIELD_ORDER.equals("type,tag,scale"));
    }

    // ---------- (2) api↔engine mirror verbatim ----------

    private static void apiMirror() {
        check("mirror: dimensions() 商 engine=api（9 维同序）",
                ScaleApiMirror.dimensions().equals(ScaleApi.dimensions())
                        && ScaleApiMirror.dimensionCount() == ScaleApi.dimensionCount()
                        && ScaleApiMirror.dimensionCount() == 9);
        check("mirror: neutralScale() 商 engine=api（1.0 逐位）",
                Double.doubleToLongBits(ScaleApiMirror.neutralScale())
                        == Double.doubleToLongBits(ScaleApi.neutralScale()));
        boolean normalOk = true;
        String[] forms = {"base", " WIDTH ", "Height", "depth", "EYE_HEIGHT",
                "hitbox_width", "hitbox_height", "model_width", "model_height"};
        for (String f : forms) {
            normalOk = normalOk && ScaleApiMirror.normalizeDimension(f)
                    .equals(ScaleApi.normalizeDimension(f));
        }
        check("mirror: normalizeDimension 同输入同输出（9 组，api=mirror，均归一化）", normalOk);
        boolean validOk = true;
        String[] probes = {"x", "", " ", "eyeheight", "HITBOX_W", null};
        for (String p : probes) {
            validOk = validOk && ScaleApiMirror.isValidDimension(p) == ScaleApi.isValidDimension(p);
        }
        check("mirror: isValidDimension 同输入同输出（含非法/空/null）", validOk);
        check("mirror: ruleFieldOrder() 商 engine=api",
                ScaleApiMirror.ruleFieldOrder().equals(ScaleApi.ruleFieldOrder()));
    }

    // ---------- (3) normalization + deterministic rejection + re-entry ----------

    private static void normalizationAndReject() {
        check("api: 大小写/空白容忍（' WIDTH '→'width'，'Height'→'height'）",
                ScaleApi.normalizeDimension(" WIDTH ").equals("width")
                        && ScaleApi.normalizeDimension("Height").equals("height"));
        check("api: 非法/空/空白/null 维度名确定性拒绝（IAE）",
                throwsIAE(() -> ScaleApi.normalizeDimension("ultra"))
                        && throwsIAE(() -> ScaleApi.normalizeDimension(""))
                        && throwsIAE(() -> ScaleApi.normalizeDimension(" "))
                        && throwsIAE(() -> ScaleApi.normalizeDimension(null))
                        && !ScaleApi.isValidDimension("ultra"));
        check("api: 所有合法维度可往返规范化（自反 retrieved 自身）",
                ScaleApi.isValidDimension("width")
                        && ScaleApi.normalizeDimension(ScaleApi.normalizeDimension("width")).equals("width")
                        && ScaleApi.dimensions().stream().allMatch(d ->
                                ScaleApi.normalizeDimension(d).equals(d)));
        check("reflect: 确定性再入（dimensions() 两次连跑同字节）",
                ScaleApi.dimensions().equals(ScaleApi.dimensions())
                        && ScaleApiMirror.dimensions().equals(ScaleApiMirror.dimensions()));
    }
}