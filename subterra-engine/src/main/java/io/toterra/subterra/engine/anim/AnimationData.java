package io.toterra.subterra.engine.anim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Immutable animation data (p.2.21.1): a name, a duration in ticks, and the keyframe
 * tracks. The tracks are defensively copied, sorted into the fixed
 * {@link AnimChannel} order (POSITION, ROTATION, SCALE), and a duplicate channel is
 * rejected with {@link IllegalArgumentException} — the fixed order makes sampling and
 * pose assembly deterministic. The name mirrors GeckoLib 4's {@code Animation(name,
 * length, loopType, boneAnimations)} record shape (upstream {@code 1.21.1} branch,
 * see {@code META-INF/third-party/geckolib-4.8/NOTICE.md}); loop types and bone
 * hierarchies are not ported.
 *
 * <p>不可变动画数据（p.2.21.1）：名称、时长（tick）与关键帧轨道。轨道被防御性拷贝、按固定
 * {@link AnimChannel} 序（POSITION、ROTATION、SCALE）排序，重复通道以
 * {@link IllegalArgumentException} 拒绝——固定序使采样与 pose 组装确定性。名称呼应 GeckoLib 4
 * 的 {@code Animation(name, length, loopType, boneAnimations)} record 形态（上游
 * {@code 1.21.1} 分支，见 {@code META-INF/third-party/geckolib-4.8/NOTICE.md}）；循环类型与
 * 骨骼层级未移植。
 *
 * @param name       the animation name (non-blank). 动画名（非空白）。
 * @param lengthTicks the animation duration in ticks (finite, &gt; 0). 动画时长（tick，有限，&gt; 0）。
 * @param tracks     the keyframe tracks; copied, fixed-order sorted, duplicate channels rejected.
 *                   关键帧轨道；构造时拷贝、固定序排序、重复通道拒绝。
 */
public record AnimationData(String name, double lengthTicks, List<KeyframeTrack> tracks) {

    public AnimationData {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("animation name must be non-null and non-blank");
        }
        if (!Double.isFinite(lengthTicks) || lengthTicks <= 0.0) {
            throw new IllegalArgumentException("lengthTicks must be finite and > 0: " + lengthTicks);
        }
        Objects.requireNonNull(tracks, "tracks must be non-null");
        List<KeyframeTrack> sorted = new ArrayList<>(tracks);
        sorted.sort(Comparator.comparingInt(track -> track.channel().ordinal()));
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).channel() == sorted.get(i - 1).channel()) {
                throw new IllegalArgumentException(
                        "duplicate track channel: " + sorted.get(i).channel());
            }
        }
        tracks = List.copyOf(sorted);
    }

    /**
     * The track for the given channel, or {@code null} when the animation has no
     * track for it. 指定通道的轨道；动画无该通道轨道时返回 {@code null}。
     *
     * @param channel the channel. 通道。
     * @return the matching track, or {@code null}. 匹配的轨道，或 {@code null}。
     */
    public KeyframeTrack track(AnimChannel channel) {
        if (channel == null) {
            return null;
        }
        for (KeyframeTrack track : tracks) {
            if (track.channel() == channel) {
                return track;
            }
        }
        return null;
    }
}
