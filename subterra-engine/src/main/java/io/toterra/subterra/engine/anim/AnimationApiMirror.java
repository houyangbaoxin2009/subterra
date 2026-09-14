package io.toterra.subterra.engine.anim;

import java.util.ArrayList;
import java.util.List;

/**
 * p.2.33.3 引擎侧镜像实现（api 定义形状 / engine 镜像实现范式，对应 {@code api.anim.AnimationApi}）：以
 * {@link AnimChannel} / {@link AnimVec3} / {@link AnimSampler} 的<b>真实常量与纯函数</b>为唯一来源，暴露
 * 与 {@code api.anim.AnimationApi} 同语义的只读契约面——固定通道序、线性/最短弧插值、角度归一化与时间 clamp
 * 均同输入同输出（供 p.2.33.3 探针对照断言）。本镜像<em>不 import</em> api 包，仅消费 engine 自身类型并把
 * api 契约值逐字导出，从而在两侧分别实例化后完全一致。插值经 {@link AnimVec3#lerp}/{@link AnimVec3#lerpRotation}
 * 在零向量分量上的单轴表达求值（与 api 的逐字表达式 {@code a + (b - a) * f} 位一致）。
 * <p>确定性：全部方法为纯函数；角度归一化经 {@link AnimVec3#normalizeDeg} 逐字导出；时间 clamp 与
 * {@link AnimSampler#sample} 的固定 clamp 语义一致。常量来源（勿重猜）：{@link AnimChannel}、{@link AnimVec3}。
 * <p>
 * p.2.33.3 engine-side mirror implementation (api-shapes / engine-mirrors pattern, counterpart of
 * {@code api.anim.AnimationApi}): using the <b>actual constants and pure functions</b> of {@link AnimChannel} /
 * {@link AnimVec3} / {@link AnimSampler} as the single source of truth, it exposes a read-only surface with the
 * same semantics as {@code api.anim.AnimationApi} — the fixed channel order, linear / shortest-arc
 * interpolation, angle normalization and time clamp are all same-input-same-output (the p.2.33.3 probe asserts
 * both sides). This mirror does <em>not</em> import the api package; it consumes only engine types and exports
 * the api contract values verbatim, so instantiating both sides yields identical results. Interpolation is
 * evaluated through the single-axis expression of {@link AnimVec3#lerp}/{@link AnimVec3#lerpRotation} on a
 * zero-vector component (bit-identical to the api's verbatim expression {@code a + (b - a) * f}).
 * <p>Deterministic: every method is a pure function; angle normalization is exported verbatim via
 * {@link AnimVec3#normalizeDeg}; the time clamp matches {@link AnimSampler#sample}'s fixed clamp semantics.
 * Constant sources (do not re-guess): {@link AnimChannel}, {@link AnimVec3}.
 */
public final class AnimationApiMirror {

    private AnimationApiMirror() {
    }

    /** The fixed 3-channel order, sourced verbatim from {@link AnimChannel#values()} order. /
     *  固定 3 通道序，逐字源自 {@link AnimChannel#values()} 序。 */
    public static List<String> channelOrder() {
        List<String> out = new ArrayList<>(AnimChannel.values().length);
        for (AnimChannel c : AnimChannel.values()) {
            out.add(c.toString());
        }
        return List.copyOf(out);
    }

    /** The number of fixed channels, sourced from {@link AnimChannel#values().length} ({@code 3}). /
     *  固定通道数，源自 {@link AnimChannel#values().length}（{@code 3}）。 */
    public static int channelCount() {
        return AnimChannel.values().length;
    }

    /** Linear interpolation on a single axis, sourced from {@link AnimVec3#lerp} ({@code a + (b - a) * f}). /
     *  单轴线性插值，源自 {@link AnimVec3#lerp}（{@code a + (b - a) * f}）。 */
    public static double sampleLinear(double a, double b, double f) {
        return AnimVec3.of(a, 0.0, 0.0).lerp(AnimVec3.of(b, 0.0, 0.0), f).x();
    }

    /** Shortest-arc angle interpolation (degrees) on a single axis, sourced from {@link AnimVec3#lerpRotation}. /
     *  单轴欧拉角（度）最短弧插值，源自 {@link AnimVec3#lerpRotation}。 */
    public static double sampleRotation(double a, double b, double f) {
        return AnimVec3.of(a, 0.0, 0.0).lerpRotation(AnimVec3.of(b, 0.0, 0.0), f).x();
    }

    /** Angle normalization into {@code (-180, 180]}, sourced verbatim from {@link AnimVec3#normalizeDeg}. /
     *  角度归一化到 {@code (-180, 180]}，逐字源自 {@link AnimVec3#normalizeDeg}。 */
    public static double normalizeDeg(double angle) {
        return AnimVec3.normalizeDeg(angle);
    }

    /** Time clamp into {@code [first, last]}, matching {@link AnimSampler#sample}'s fixed clamp semantics. /
     *  时间按 {@code [first, last]} 截断，与 {@link AnimSampler#sample} 的固定 clamp 语义一致。 */
    public static double clampTime(double t, double first, double last) {
        return Math.min(Math.max(t, first), last);
    }
}