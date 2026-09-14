package io.toterra.subterra.api.anim;

import java.util.List;

/**
 * p.2.33.3 对外的动画契约门面（final class、静态纯函数、纯 JDK）：确定性接口面，供上层/域模组按
 * 「固定通道序 + 确定性采样原语」驱动动画。语义与 {@code engine.anim} 的
 * {@code AnimChannel}/{@code AnimVec3}/{@code AnimSampler}（p.2.21.1）一致——本处为契约与数据面注入，
 * engine 为实现镜像（{@code engine.anim.AnimationApiMirror}），api 不依赖 engine。所有契约常量均系从
 * engine 实际常量/纯函数逐字对照落于此，禁止拍脑袋数值。
 * <p>确定性：{@link #channelOrder()} 返回固定 3 通道序（{@code position, rotation, scale}，镜像
 * {@code AnimChannel} 枚举序）；{@link #sampleLinear} 为纯线性插值 {@code a + (b - a) * f}，镜像
 * {@code AnimVec3#lerp} 的逐分量表达式；{@link #sampleRotation} 为欧拉角（度）最短弧插值
 * {@code a + normalizeDeg(b - a) * f}，镜像 {@code AnimVec3#lerpRotation}；{@link #normalizeDeg} 镜像
 * {@code AnimVec3#normalizeDeg}。所有浮点表达式与 engine 逐字相同，故同输入恒得同 double 位模式。
 * 无随机、无墙钟；全部 O(1)、禁 O(n²)。
 * <p>
 * p.2.33.3 the external animation contract facade (final class, static pure functions, pure JDK): a
 * deterministic interface surface for upper layers / domain mods to drive animation from a "fixed channel
 * order + deterministic sampling primitives". Semantics match {@code engine.anim}'s
 * {@code AnimChannel}/{@code AnimVec3}/{@code AnimSampler} (p.2.21.1) — this is the contract and data-injection
 * surface for the engine to mirror as its implementation ({@code engine.anim.AnimationApiMirror}), and the api
 * does not depend on the engine. Every contract constant/function is pinned verbatim from the engine's actual
 * constants/pure functions — nothing is guessed.
 * <p>Deterministic: {@link #channelOrder()} returns the fixed 3-channel order ({@code position, rotation,
 * scale}, mirroring the {@code AnimChannel} enum order); {@link #sampleLinear} is the pure linear
 * interpolation {@code a + (b - a) * f}, mirroring the per-component expression of {@code AnimVec3#lerp};
 * {@link #sampleRotation} is the Euler-angle (degrees) shortest-arc interpolation
 * {@code a + normalizeDeg(b - a) * f}, mirroring {@code AnimVec3#lerpRotation}; {@link #normalizeDeg} mirrors
 * {@code AnimVec3#normalizeDeg}. Every float expression is verbatim-identical to the engine's, so identical
 * inputs always yield identical double bit patterns. No randomness, no wall-clock; all O(1), no O(n²).
 */
public final class AnimationApi {

    /** Number of fixed animation channels ({@code 3}, mirroring {@code AnimChannel.values().length}). /
     *  固定动画通道数（{@code 3}，镜像 {@code AnimChannel.values().length}）。 */
    public static final int CHANNEL_COUNT = 3;

    private AnimationApi() {
    }

    /**
     * The fixed 3-channel order ({@code position, rotation, scale}), mirroring {@code AnimChannel.values()}.
     * Deterministic; identical on every call. / 固定 3 通道序（{@code position, rotation, scale}），镜像
     * {@code AnimChannel.values()}。确定性；每次调用均相同。
     */
    public static List<String> channelOrder() {
        return List.of("position", "rotation", "scale");
    }

    /** The number of fixed animation channels ({@code 3}). / 固定动画通道数（{@code 3}）。 */
    public static int channelCount() {
        return CHANNEL_COUNT;
    }

    /**
     * Component-wise linear interpolation {@code a + (b - a) * f}, the exact expression of
     * {@code engine.anim.AnimVec3#lerp} restricted to a single component. Pure and deterministic: identical
     * inputs yield identical double bits, and {@code f=0}/{@code f=1} reproduce the endpoints bit-for-bit.
     * / 逐分量线性插值 {@code a + (b - a) * f}，即 {@code engine.anim.AnimVec3#lerp} 的精确表达式在单分量
     * 上的形式。纯且确定：同输入恒得同 double 位；{@code f=0}/{@code f=1} 逐位还原端点。
     *
     * @param a the first endpoint. 首端点。
     * @param b the second endpoint. 次端点。
     * @param f the interpolation factor in [0, 1]. 插值因子 [0, 1]。
     * @return {@code a + (b - a) * f}. 插值结果。
     */
    public static double sampleLinear(double a, double b, double f) {
        return a + (b - a) * f;
    }

    /**
     * Shortest-arc interpolation for one Euler angle (degrees): {@code a + normalizeDeg(b - a) * f}, the exact
     * per-component expression of {@code engine.anim.AnimVec3#lerpRotation}. Deterministic, no trigonometry.
     * / 单个欧拉角（度）的最短弧插值：{@code a + normalizeDeg(b - a) * f}，即
     * {@code engine.anim.AnimVec3#lerpRotation} 的精确逐分量表达式。确定性，无三角函数。
     *
     * @param a the first endpoint (degrees). 首端点（度）。
     * @param b the second endpoint (degrees). 次端点（度）。
     * @param f the interpolation factor in [0, 1]. 插值因子 [0, 1]。
     * @return the interpolated angle (degrees). 插值角度（度）。
     */
    public static double sampleRotation(double a, double b, double f) {
        return a + normalizeDeg(b - a) * f;
    }

    /**
     * Normalizes an angle in degrees into the shortest-arc range {@code (-180, 180]}, mirroring
     * {@code engine.anim.AnimVec3#normalizeDeg} verbatim: {@code 180} stays {@code 180}, {@code -180} maps to
     * {@code 180}. Pure modular arithmetic, deterministic bit-for-bit. / 将角度（度）归一化到最短弧区间
     * {@code (-180, 180]}，逐字镜像 {@code engine.anim.AnimVec3#normalizeDeg}：{@code 180} 保持
     * {@code 180}、{@code -180} 映射为 {@code 180}。纯模运算，同输入同输出（逐位确定）。
     *
     * @param angle the angle in degrees. 角度（度）。
     * @return the normalized angle in {@code (-180, 180]}. 归一化后的角度 {@code (-180, 180]}。
     */
    public static double normalizeDeg(double angle) {
        double d = angle % 360.0;
        if (d > 180.0) {
            return d - 360.0;
        }
        if (d <= -180.0) {
            return d + 360.0;
        }
        return d;
    }

    /**
     * Clamps a time sample into {@code [first, last]} — the fixed clamp semantics of
     * {@code engine.anim.AnimSampler#sample} for a sample outside the track's time range. Deterministic.
     * / 将时间采样按 {@code [first, last]} 截断——{@code engine.anim.AnimSampler#sample} 对超出轨道时间范围
     * 采样的固定 clamp 语义。确定性。
     *
     * @param t     the animation time in ticks. 动画时间（tick）。
     * @param first the track's first keyframe time. 轨道首帧时间。
     * @param last  the track's last keyframe time. 轨道末帧时间。
     * @return {@code min(max(t, first), last)}. 截断后的时间。
     */
    public static double clampTime(double t, double first, double last) {
        return Math.min(Math.max(t, first), last);
    }
}