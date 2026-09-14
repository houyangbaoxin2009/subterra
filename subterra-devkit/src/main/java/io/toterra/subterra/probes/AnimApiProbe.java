package io.toterra.subterra.probes;

import io.toterra.subterra.api.anim.AnimationApi;
import io.toterra.subterra.engine.anim.AnimationApiMirror;
import io.toterra.subterra.engine.anim.AnimChannel;

import java.util.List;
import java.util.stream.Stream;

/**
 * p.2.33.3（探针）— api.anim 契约面 ↔ engine 镜像逐字对照探针：把 {@code api.anim.AnimationApi}（对外契约）
 * 与 {@code engine.anim.AnimationApiMirror}（实现镜像）锚定为纯 JVM 断言。纯 JVM——不碰 MC、无时序、无随机；
 * exit 0 = PASS，exit 1 = FAIL；不进 mod jar。分节：
 * <ol>
 *   <li><b>api 通道固定 3 序</b>：{@code channelOrder()} 固定 {@code position, rotation, scale} +
 *       {@code channelCount()}=3 + 与 engine {@code AnimChannel.values()} 序逐字对照。</li>
 *   <li><b>api↔engine 镜像逐字对照</b>：调 {@link AnimationApiMirror} 与 {@link AnimationApi} 对同输入求值，
 *       线性插值/最短弧插值/角度归一化/时间 clamp 同输入同 double 位（逐位对照）。</li>
 *   <li><b>仿射端点与确定性再入</b>：{@code f=0/f=1} 逐位还原端点、{@code normalizeDeg} 边界（180/-180/190/-190
 *       等）确定、同输入连跑同字节（确定性再入）。</li>
 * </ol>
 * 确定性纪律：固定序、无时序、无随机；失败计数只在失败路径自增；全过输出 {@code [AnimApiProbe] PASS (n
 * checks)} exit 0，否则 FAIL exit 1。
 * <p>
 * p.2.33.3 (probe) — api.anim contract ↔ engine mirror verbatim probe: anchors {@code api.anim.AnimationApi}
 * (external contract) and {@code engine.anim.AnimationApiMirror} (implementation mirror) to pure-JVM assertions.
 * Pure JVM — no MC runtime, no timing, no randomness; exit 0 = PASS, exit 1 = FAIL; never shipped in the mod jar.
 * Sections: api fixed 3-channel order / api↔engine mirror verbatim / affine endpoints + deterministic re-entry.
 */
public final class AnimApiProbe {

    private AnimApiProbe() {
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

    /** True iff all three doubles are bit-identical to each other. 三者 double 逐位一致为真。 */
    private static boolean bits(double api, double mir, double ref) {
        return Double.doubleToLongBits(api) == Double.doubleToLongBits(mir)
                && Double.doubleToLongBits(mir) == Double.doubleToLongBits(ref);
    }

    public static void main(String[] args) {
        try {
            apiChannelOrder();
            apiMirror();
            endpointsAndReentry();
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] probe exception: " + e);
            e.printStackTrace(System.out);
        }
        if (failures == 0) {
            System.out.println("[AnimApiProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[AnimApiProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    // ---------- (1) api fixed 3-channel order ----------

    private static void apiChannelOrder() {
        check("AnimationApi: channelOrder() 固定 3 通道序（position,rotation,scale）",
                AnimationApi.channelOrder().equals(List.of("position", "rotation", "scale")));
        check("AnimationApi: channelCount()==3（= engine AnimChannel.values().length）",
                AnimationApi.channelCount() == 3 && AnimationApi.channelCount() == AnimChannel.values().length);
        check("AnimationApi: 与 engine AnimChannel.values() 序逐字对照（toString() 全量）",
                AnimationApi.channelOrder().equals(
                        Stream.of(AnimChannel.values()).map(AnimChannel::toString).toList()));
    }

    // ---------- (2) api↔engine mirror verbatim ----------

    private static void apiMirror() {
        check("mirror: channelOrder()/channelCount() 商 engine=api",
                AnimationApiMirror.channelOrder().equals(AnimationApi.channelOrder())
                        && AnimationApiMirror.channelCount() == AnimationApi.channelCount());
        boolean linear = true;
        for (double f = 0.0; f <= 1.0; f += 0.125) {
            linear = linear && bits(AnimationApi.sampleLinear(1.0, 5.0, f),
                    AnimationApiMirror.sampleLinear(1.0, 5.0, f), 1.0 + (5.0 - 1.0) * f);
        }
        check("mirror: sampleLinear 同输入同 double 位（f=0..1 步进 9 组）", linear);
        boolean rot = true;
        double[][] pairs = {{10.0, 170.0}, {350.0, 10.0}, {180.0, -180.0}, {-170.0, 170.0}, {45.0, 315.0}};
        for (double[] p : pairs) {
            for (double f = 0.0; f <= 1.0; f += 0.25) {
                double a = AnimationApi.sampleRotation(p[0], p[1], f);
                double m = AnimationApiMirror.sampleRotation(p[0], p[1], f);
                rot = rot && Double.doubleToLongBits(a) == Double.doubleToLongBits(m);
            }
        }
        check("mirror: sampleRotation 同输入同 double 位（5 组跨最短弧 × f 步进）", rot);
        boolean norm = true;
        double[] angles = {0, 90, 180, -180, 190, -190, 359, -359, 360, 540, 270};
        for (double a : angles) {
            norm = norm && bits(AnimationApi.normalizeDeg(a),
                    AnimationApiMirror.normalizeDeg(a), AnimationApi.normalizeDeg(a));
        }
        check("mirror: normalizeDeg 同输入同 double 位（11 组边界角）", norm);
        boolean clamp = true;
        double[][] cps = {{-5, 0, 10}, {0, 0, 10}, {7, 0, 10}, {20, 0, 10}};
        for (double[] c : cps) {
            clamp = clamp && bits(AnimationApi.clampTime(c[0], c[1], c[2]),
                    AnimationApiMirror.clampTime(c[0], c[1], c[2]),
                    Math.min(Math.max(c[0], c[1]), c[2]));
        }
        check("mirror: clampTime 同输入同 double 位（4 组含越界）", clamp);
    }

    // ---------- (3) affine endpoints + deterministic re-entry ----------

    private static void endpointsAndReentry() {
        check("api: f=0/f=1 逐位还原端点（线性）",
                Double.doubleToLongBits(AnimationApi.sampleLinear(1.0, 5.0, 0.0))
                        == Double.doubleToLongBits(1.0)
                        && Double.doubleToLongBits(AnimationApi.sampleLinear(1.0, 5.0, 1.0))
                        == Double.doubleToLongBits(5.0));
        check("api: normalizeDeg 边界确定（180→180，-180→180，190→-170，-190→170，359→-1）",
                AnimationApi.normalizeDeg(180) == 180.0
                        && AnimationApi.normalizeDeg(-180) == 180.0
                        && AnimationApi.normalizeDeg(190) == -170.0
                        && AnimationApi.normalizeDeg(-190) == 170.0
                        && AnimationApi.normalizeDeg(359) == -1.0);
        check("api: 最短弧越 0° 步进单调（rotation 采样跨 -1°→1° 于 f=0.5 处靠近 0）",
                bits(AnimationApi.sampleRotation(-1.0, 1.0, 0.5),
                        AnimationApiMirror.sampleRotation(-1.0, 1.0, 0.5), 0.0));
        check("reflect: 确定性再入（同函数两次连跑同字节）",
                AnimationApi.channelOrder().equals(AnimationApi.channelOrder())
                        && AnimationApiMirror.channelOrder().equals(AnimationApiMirror.channelOrder())
                        && AnimationApi.normalizeDeg(190) == AnimationApi.normalizeDeg(190));
    }
}