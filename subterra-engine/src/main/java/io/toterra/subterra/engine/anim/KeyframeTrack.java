package io.toterra.subterra.engine.anim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A fixed-order keyframe track for one {@link AnimChannel} (p.2.21.1). The keyframes
 * are defensively copied and stably sorted by ascending time (equal times keep input
 * order), so the track order is deterministic and independent of construction order.
 * An empty track is legal and samples as {@link AnimVec3#ZERO}.
 *
 * <p>单个 {@link AnimChannel} 的固定序关键帧轨道（p.2.21.1）。关键帧被防御性拷贝并按时间
 * 升序稳定排序（等时保持输入序），因此轨道顺序确定且与构造顺序无关。空轨道合法，采样结果为
 * {@link AnimVec3#ZERO}。
 *
 * @param channel   the channel this track animates. 本轨道动画化的通道。
 * @param keyframes the keyframes; copied and sorted on construction. 关键帧；构造时拷贝并排序。
 */
public record KeyframeTrack(AnimChannel channel, List<Keyframe> keyframes) {

    public KeyframeTrack {
        Objects.requireNonNull(channel, "channel must be non-null");
        Objects.requireNonNull(keyframes, "keyframes must be non-null");
        List<Keyframe> sorted = new ArrayList<>(keyframes);
        sorted.sort(Comparator.comparingDouble(Keyframe::time));
        keyframes = List.copyOf(sorted);
    }
}
