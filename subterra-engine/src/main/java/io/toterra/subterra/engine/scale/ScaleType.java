package io.toterra.subterra.engine.scale;

import java.util.Locale;

/**
 * A dimensioned entity-scale type, modelling the dimensioned scale types of Pehkui /
 * Pehkui-Rebuilt (the lowercase registration forms keep the upstream {@code pehkui:<type>}
 * ids). {@code BASE} is the root scale that other dimensions derive from; the
 * dimensioned types are per-axis sizes / derived aspects. See
 * {@code META-INF/third-party/pehkui-rebuilt-3.8.5/NOTICE.md} for the clean-room scope
 * (this is a fixed pure-JDK subset; upstream models many more derived types via an
 * MC-injected registry).
 * <p>
 * Deterministic: {@link #values()} order is fixed and is the canonical iteration order
 * used across this package; {@link #form()} yields the lowercase registration name and
 * {@link #fromForm(String)} maps back deterministically (unknown forms are rejected).
 *
 * <p>按维度实体的缩放类型，建模 Pehkui / Pehkui-Rebuilt 的按维度缩放类型（小写注册名沿用上游
 * {@code pehkui:<type>} id）。{@link #BASE} 为根缩放，其它维度由它派生；按维度类型为各轴尺寸 /
 * 派生方面。clean-room 适用范围见 {@code META-INF/third-party/pehkui-rebuilt-3.8.5/NOTICE.md}
 * （本处为固定纯 JDK 子集；上游以 MC 注入注册表建模更多派生类型）。
 * <p>确定性：{@link #values()} 序固定，为本包统一的规范迭代序；{@link #form()} 给出小写注册名，
 * {@link #fromForm(String)} 确定性回映射（未知形式被拒绝）。
 */
public enum ScaleType {
    /** Root scale — most other dimensions derive from it. / 根缩放——多数派生维度由此派生。 */
    BASE,
    /** Entity width / length / depth axis. / 实体宽度（长/深）轴。 */
    WIDTH,
    /** Entity height axis. / 实体高度轴。 */
    HEIGHT,
    /** Explicit depth axis (orthogonal addition; upstream folds depth into width). /
     *  显式深度轴（正交新增；上游把深度并入 width）。 */
    DEPTH,
    /** Eye / camera height. / 视点（相机）高度。 */
    EYE_HEIGHT,
    /** Collision-box width. / 碰撞箱宽度。 */
    HITBOX_WIDTH,
    /** Collision-box height. / 碰撞箱高度。 */
    HITBOX_HEIGHT,
    /** Rendered model width. / 渲染模型宽度。 */
    MODEL_WIDTH,
    /** Rendered model height. / 渲染模型高度。 */
    MODEL_HEIGHT;

    /** Lowercase registration name (matches the upstream {@code pehkui:<type>} id). /
     *  小写注册名（对应上游 {@code pehkui:<type>} id）。 */
    public String form() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Maps a lowercase (or any-case) registration form back to a {@link ScaleType}.
     * Deterministic; throws {@link IllegalArgumentException} for unknown forms.
     * / 将小写（或任意大小写）注册名回映射为 {@link ScaleType}。确定性；未知形式抛
     * {@link IllegalArgumentException}。
     */
    public static ScaleType fromForm(String form) {
        if (form == null) {
            throw new IllegalArgumentException("scale type form must not be null");
        }
        String f = form.trim().toLowerCase(Locale.ROOT);
        for (ScaleType t : values()) {
            if (t.form().equals(f)) {
                return t;
            }
        }
        throw new IllegalArgumentException("unknown scale type form: " + form);
    }
}