package io.toterra.subterra.engine.anim;

import java.util.Objects;

/**
 * A single keyframe: an absolute animation time (in ticks) and the sampled value at
 * that time (p.2.21.1). Keyframes are held in a {@link KeyframeTrack} which sorts
 * them by ascending time (stable sort — equal times keep their input order), giving
 * the track a fixed deterministic order. Time must be finite; the value must be
 * finite (enforced by {@link AnimVec3}).
 *
 * <p>Note on the GeckoLib 4 mapping: upstream keyframes fetched from the
 * {@code 1.21.1} branch are {@code (length, startValue, endValue, easingType)}
 * transitions consumed tick-by-tick; {@code engine.anim} is a clean-room
 * simplification sampling by absolute time instead — see
 * {@code META-INF/third-party/geckolib-4.8/NOTICE.md}.
 *
 * <p>单个关键帧：绝对动画时间（tick）与此刻的采样值（p.2.21.1）。关键帧存放于
 * {@link KeyframeTrack}，按时间升序排序（稳定排序——等时保持输入序），从而轨道具有固定
 * 确定性顺序。时间必须有限；值必须有限（由 {@link AnimVec3} 强制）。
 *
 * <p>关于 GeckoLib 4 映射的说明：上游 {@code 1.21.1} 分支的关键帧为
 * {@code (length, startValue, endValue, easingType)} 过渡形式，由控制器逐 tick 消费；
 * {@code engine.anim} 为 clean-room 简化，改为按绝对时间采样——见
 * {@code META-INF/third-party/geckolib-4.8/NOTICE.md}。
 *
 * @param time  absolute animation time in ticks (finite). 绝对动画时间（tick，有限）。
 * @param value sampled value at that time. 该时刻的采样值。
 */
public record Keyframe(double time, AnimVec3 value) {

    public Keyframe {
        if (!Double.isFinite(time)) {
            throw new IllegalArgumentException("Keyframe time must be finite: " + time);
        }
        Objects.requireNonNull(value, "value must be non-null");
    }
}
