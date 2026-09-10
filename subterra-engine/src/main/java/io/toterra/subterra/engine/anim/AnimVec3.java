package io.toterra.subterra.engine.anim;

import java.util.Objects;

/**
 * Immutable 3D vector (double components), the value type of {@link Keyframe}s
 * (p.2.21.1). Position and scale are interpolated with {@link #lerp(AnimVec3, double)};
 * rotation (Euler angles in degrees, matching the GeckoLib 4 semantics fetched from
 * the upstream {@code 1.21.1} branch) is interpolated along the shortest arc via
 * {@link #lerpRotation(AnimVec3, double)}, with each component delta normalized into
 * {@code (-180, 180]}. All operations are pure arithmetic (no trigonometry, no
 * randomness), so the same inputs always yield the same double bit patterns.
 *
 * <p>不可变三维向量（double 分量），{@link Keyframe} 的值类型（p.2.21.1）。position 与 scale
 * 用 {@link #lerp(AnimVec3, double)} 插值；rotation（欧拉角，单位为度，与上游 {@code 1.21.1}
 * 分支抓取的 GeckoLib 4 语义一致）经 {@link #lerpRotation(AnimVec3, double)} 沿最短弧插值，
 * 各分量角度差归一化到 {@code (-180, 180]}。全部为纯算术（无三角、无随机），同输入恒得同
 * double 位模式。
 */
public record AnimVec3(double x, double y, double z) {

    /** Zero vector (0, 0, 0). 零向量 (0, 0, 0)。 */
    public static final AnimVec3 ZERO = new AnimVec3(0.0, 0.0, 0.0);

    public AnimVec3 {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("AnimVec3 components must be finite: " + x + ", " + y + ", " + z);
        }
    }

    /**
     * Creates a vector. 创建向量。
     *
     * @param x X component. X 分量。
     * @param y Y component. Y 分量。
     * @param z Z component. Z 分量。
     * @return the new vector. 新向量。
     */
    public static AnimVec3 of(double x, double y, double z) {
        return new AnimVec3(x, y, z);
    }

    /**
     * Component-wise linear interpolation {@code a + (b - a) * f}, computed with the
     * exact expression {@code a + (b - a) * f} so that {@code f = 0} and {@code f = 1}
     * reproduce the endpoints bit-for-bit. Deterministic for identical inputs.
     *
     * 逐分量线性插值 {@code a + (b - a) * f}，按精确表达式 {@code a + (b - a) * f} 计算，
     * 使得 {@code f = 0} 与 {@code f = 1} 时逐位还原端点。同输入同输出（确定性）。
     *
     * @param other the second endpoint. 另一端点。
     * @param f     interpolation factor in [0, 1]. 插值因子 [0, 1]。
     * @return the interpolated vector. 插值结果向量。
     */
    public AnimVec3 lerp(AnimVec3 other, double f) {
        Objects.requireNonNull(other, "other must be non-null");
        return new AnimVec3(
                x + (other.x - x) * f,
                y + (other.y - y) * f,
                z + (other.z - z) * f);
    }

    /**
     * Shortest-arc interpolation for Euler angles (degrees): per component the delta
     * {@code other - this} is normalized into {@code (-180, 180]} with
     * {@link #normalizeDeg(double)}, then {@code this + delta * f} is computed.
     * Deterministic, no trigonometry.
     *
     * 欧拉角（度）最短弧插值：各分量角度差 {@code other - this} 经
     * {@link #normalizeDeg(double)} 归一化到 {@code (-180, 180]}，再计算
     * {@code this + delta * f}。确定性，无三角函数。
     *
     * @param other the second endpoint. 另一端点。
     * @param f     interpolation factor in [0, 1]. 插值因子 [0, 1]。
     * @return the interpolated angles (degrees). 插值结果角度（度）。
     */
    public AnimVec3 lerpRotation(AnimVec3 other, double f) {
        Objects.requireNonNull(other, "other must be non-null");
        return new AnimVec3(
                x + normalizeDeg(other.x - x) * f,
                y + normalizeDeg(other.y - y) * f,
                z + normalizeDeg(other.z - z) * f);
    }

    /**
     * Normalizes an angle in degrees into the shortest-arc range {@code (-180, 180]}:
     * {@code 180} stays {@code 180} and {@code -180} maps to {@code 180}. Pure modular
     * arithmetic, deterministic bit-for-bit for identical inputs.
     *
     * 将角度（度）归一化到最短弧区间 {@code (-180, 180]}：{@code 180} 保持 {@code 180}，
     * {@code -180} 映射为 {@code 180}。纯模运算，同输入同输出（逐位确定）。
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
}
