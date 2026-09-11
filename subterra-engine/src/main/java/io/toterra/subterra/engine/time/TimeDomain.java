package io.toterra.subterra.engine.time;

import java.util.Locale;

/**
 * The fixed set of time-scaling domains (p.2.26.1, clean-room self-developed): the
 * four independent scope dimensions that a {@link FlowRate} scale applies to.
 * {@link #GLOBAL} is the world-level baseline every other domain falls back to;
 * {@link #ENTITY}, {@link #ZONE} and {@link #PLAYER} are per-domain overrides that
 * may be set or left unresolved to the global default.
 * <p>
 * Deterministic: {@link #values()} order is fixed ({@code GLOBAL, ENTITY, ZONE, PLAYER})
 * and is the canonical iteration order used across this package; {@link #form()}
 * yields the lowercase registration name and {@link #fromForm(String)} maps back
 * deterministically (unknown forms are rejected). No randomness, no timing.
 *
 * <p>时间缩放域的固定集合（p.2.26.1，clean-room 自研）：{@link FlowRate} 流速比例所施加的四个独立
 * 作用维度。{@link #GLOBAL} 为其它域兜底回退的世界级基线；{@link #ENTITY}、{@link #ZONE} 与
 * {@link #PLAYER} 是可设置或留空的按域覆盖档，未设置时回退全局默认。
 * <p>确定性：{@link #values()} 序固定（{@code GLOBAL, ENTITY, ZONE, PLAYER}），为本包统一规范迭代序；
 * {@link #form()} 给出小写注册名，{@link #fromForm(String)} 确定性回映射（未知形式被拒绝）。无随机、
 * 无时序。
 */
public enum TimeDomain {

    /** World-level baseline. / 世界级基线（兜底档）。 */
    GLOBAL,
    /** Per-entity domain. / 按实体域。 */
    ENTITY,
    /** Per-zone (region) domain. / 按区域域。 */
    ZONE,
    /** Per-player domain. / 按玩家域。 */
    PLAYER;

    /** Lowercase registration name. / 小写注册名。 */
    public String form() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Maps a lowercase (or any-case) registration form back to a {@link TimeDomain}.
     * Deterministic; throws {@link IllegalArgumentException} for unknown forms.
     * / 将小写（或任意大小写）注册名回映射为 {@link TimeDomain}。确定性；未知形式抛
     * {@link IllegalArgumentException}。
     */
    public static TimeDomain fromForm(String form) {
        if (form == null) {
            throw new IllegalArgumentException("time domain form must not be null");
        }
        String f = form.trim().toLowerCase(Locale.ROOT);
        for (TimeDomain d : values()) {
            if (d.form().equals(f)) {
                return d;
            }
        }
        throw new IllegalArgumentException("unknown time domain form: " + form);
    }
}