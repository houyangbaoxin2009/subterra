package io.toterra.subterra.engine.anim;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic keyframe sampler (p.2.21.1). Given an animation time {@code t}
 * (double, ticks), samples the nearest keyframe interval with fixed semantics:
 * <ul>
 *   <li>an empty track samples as {@link AnimVec3#ZERO};</li>
 *   <li>{@code t} before the first keyframe clamps to the first keyframe value, and
 *       {@code t} after the last keyframe clamps to the last keyframe value (fixed
 *       clamp semantics);</li>
 *   <li>a {@code NaN} {@code t} samples as the first keyframe value (fixed);</li>
 *   <li>between keyframes, POSITION/SCALE use component-wise linear interpolation
 *       {@code a + (b - a) * f} and ROTATION uses shortest-arc interpolation on
 *       Euler degrees (see {@link AnimVec3#lerpRotation(AnimVec3, double)}); a zero
 *       span (equal-time keyframes) samples the earlier value.</li>
 * </ul>
 * All operations are pure arithmetic with no randomness and no timing, so identical
 * inputs yield identical double bit patterns. Interval lookup is a binary search —
 * O(log n), no O(n²).
 *
 * <p>确定性关键帧采样器（p.2.21.1）。给定动画时间 {@code t}（double，tick），按固定语义采样
 * 最近的关键帧区间：
 * <ul>
 *   <li>空轨道采样为 {@link AnimVec3#ZERO}；</li>
 *   <li>{@code t} 早于首帧 clamp 到首帧值，晚于末帧 clamp 到末帧值（固定 clamp 语义）；</li>
 *   <li>{@code t} 为 {@code NaN} 时采样为首帧值（固定）；</li>
 *   <li>关键帧之间，POSITION/SCALE 用逐分量线性插值 {@code a + (b - a) * f}，ROTATION 用
 *       欧拉角（度）最短弧插值（见 {@link AnimVec3#lerpRotation(AnimVec3, double)}）；
 *       零跨度（等时关键帧）采样较早的值。</li>
 * </ul>
 * 全部为纯算术、无随机、无时序，同输入恒得同 double 位模式。区间查找为二分查找——
 * O(log n)，无 O(n²)。
 */
public final class AnimSampler {

    private AnimSampler() {
    }

    /**
     * Samples a track at time {@code t}. 在时间 {@code t} 采样轨道。
     *
     * @param track the track, or {@code null} (samples as {@link AnimVec3#ZERO}).
     *              轨道，可为 {@code null}（采样为 {@link AnimVec3#ZERO}）。
     * @param t     the animation time in ticks. 动画时间（tick）。
     * @return the sampled value. 采样值。
     */
    public static AnimVec3 sample(KeyframeTrack track, double t) {
        if (track == null) {
            return AnimVec3.ZERO;
        }
        List<Keyframe> keyframes = track.keyframes();
        if (keyframes.isEmpty()) {
            return AnimVec3.ZERO;
        }
        if (keyframes.size() == 1) {
            return keyframes.get(0).value();
        }
        Keyframe first = keyframes.get(0);
        Keyframe last = keyframes.get(keyframes.size() - 1);
        if (Double.isNaN(t)) {
            return first.value();
        }
        double clamped = Math.min(Math.max(t, first.time()), last.time());
        int i = lowerBound(keyframes, clamped);
        Keyframe a = keyframes.get(i);
        if (i >= keyframes.size() - 1) {
            return a.value();
        }
        Keyframe b = keyframes.get(i + 1);
        double span = b.time() - a.time();
        double f = span == 0.0 ? 0.0 : (clamped - a.time()) / span;
        if (track.channel() == AnimChannel.ROTATION) {
            return a.value().lerpRotation(b.value(), f);
        }
        return a.value().lerp(b.value(), f);
    }

    /**
     * Samples the given channel of an animation at time {@code t}; a {@code null}
     * animation or channel, or a missing track, samples as {@link AnimVec3#ZERO}.
     *
     * 在时间 {@code t} 采样动画的指定通道；动画或通道为 {@code null}、或缺少轨道时采样为
     * {@link AnimVec3#ZERO}。
     *
     * @param animation the animation, or {@code null}. 动画，可为 {@code null}。
     * @param channel   the channel, or {@code null}. 通道，可为 {@code null}。
     * @param t         the animation time in ticks. 动画时间（tick）。
     * @return the sampled value. 采样值。
     */
    public static AnimVec3 sample(AnimationData animation, AnimChannel channel, double t) {
        if (animation == null || channel == null) {
            return AnimVec3.ZERO;
        }
        return sample(animation.track(channel), t);
    }

    /**
     * Index of the last keyframe with {@code time() <= t}; the caller guarantees
     * {@code t} is within the track's time range. Binary search — deterministic,
     * O(log n). 最后一个满足 {@code time() <= t} 的关键帧下标；调用方保证 {@code t} 在轨道
     * 时间范围内。二分查找——确定性，O(log n)。
     */
    private static int lowerBound(List<Keyframe> keyframes, double t) {
        int lo = 0;
        int hi = keyframes.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (keyframes.get(mid).time() <= t) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo - 1;
    }
}
