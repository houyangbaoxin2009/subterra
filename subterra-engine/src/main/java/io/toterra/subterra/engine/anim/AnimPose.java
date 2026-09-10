package io.toterra.subterra.engine.anim;

/**
 * A deterministic pose (p.2.21.1): the sampled transform channels of a model at one
 * instant, in fixed field order (position, rotation, scale). Channels without
 * keyframes sample as {@link AnimVec3#ZERO}. Immutable.
 *
 * <p>确定性 pose（p.2.21.1）：某模型某一瞬时的采样变换通道，固定字段序（position、
 * rotation、scale）。无关键帧的通道采样为 {@link AnimVec3#ZERO}。不可变。
 *
 * @param position translation. 位移。
 * @param rotation Euler angles in degrees. 欧拉角（度）。
 * @param scale    scale factors. 缩放倍数。
 */
public record AnimPose(AnimVec3 position, AnimVec3 rotation, AnimVec3 scale) {

    /** The zero pose (identity transform). 零 pose（恒等变换）。 */
    public static final AnimPose ZERO = new AnimPose(AnimVec3.ZERO, AnimVec3.ZERO, AnimVec3.ZERO);

    public AnimPose {
        if (position == null || rotation == null || scale == null) {
            throw new IllegalArgumentException("pose components must be non-null");
        }
    }
}
