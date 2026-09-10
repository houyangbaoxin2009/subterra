package io.toterra.subterra.engine.anim;

/**
 * Fixed-order animation channel kinds (p.2.21.1), mirroring the GeckoLib 4 per-bone
 * transform channels fetched from the upstream {@code 1.21.1} branch
 * ({@code rotation} / {@code position} / {@code scale}). The enum order
 * {@link #POSITION}, {@link #ROTATION}, {@link #SCALE} is the fixed order used by
 * {@link AnimationData} track sorting and state-machine pose assembly — deterministic,
 * no timing, no randomness. {@link #name()} returns the GeckoLib JSON channel id
 * ({@code "position"} / {@code "rotation"} / {@code "scale"}).
 *
 * <p>固定序动画通道类型（p.2.21.1），对应上游 {@code 1.21.1} 分支抓取的 GeckoLib 4 逐骨骼
 * 变换通道（{@code rotation} / {@code position} / {@code scale}）。枚举序
 * {@link #POSITION}、{@link #ROTATION}、{@link #SCALE} 为固定序，用于 {@link AnimationData}
 * 的轨道排序与状态机 pose 组装——确定性、无时序、无随机。{@link #name()} 返回 GeckoLib JSON
 * 通道标识（{@code "position"} / {@code "rotation"} / {@code "scale"}）。
 */
public enum AnimChannel {
    /** Translation channel (blocks). 位移通道（格）。 */
    POSITION,
    /** Rotation channel, Euler angles in degrees (shortest-arc interpolation). 旋转通道，欧拉角（度），最短弧插值。 */
    ROTATION,
    /** Scale channel (factor). 缩放通道（倍数）。 */
    SCALE;

    /** GeckoLib JSON channel id: {@code "position"}. GeckoLib JSON 通道标识：{@code "position"}。 */
    @Override
    public String toString() {
        return switch (this) {
            case POSITION -> "position";
            case ROTATION -> "rotation";
            case SCALE -> "scale";
        };
    }
}
