package io.toterra.subterra.api.time;

import java.util.Locale;

/**
 * p.2.26.2 时间缩放域契约（对外契约面，api 自带定义、纯 JDK）：fixed set 的四个独立作用维度，语义与
 * {@code engine.time.TimeDomain}（p.2.26.1）完全一致——本处为契约、engine 为实现侧镜像，api 不依赖
 * engine。{@link #GLOBAL} 为其它域兜底回退的世界级基线；{@link #ENTITY}、{@link #ZONE}、{@link #PLAYER}
 * 为可设置或留空的按域覆盖档。
 * <p>确定性：{@link #values()} 序固定（{@code GLOBAL, ENTITY, ZONE, PLAYER}），为本包统一规范迭代序；
 * {@link #form()} 给出小写注册名，{@link #fromForm(String)} 确定性回映射（未知形式被拒绝）。无随机、无时序。
 * <p>
 * p.2.26.2 time-domain contract (the external contract surface, self-contained pure-JDK): a fixed set of
 * four independent scope dimensions, semantically identical to {@code engine.time.TimeDomain} (p.2.26.1) —
 * this is the contract, the engine mirrors as its implementation, and the api does not depend on the engine.
 * {@link #GLOBAL} is the world-level baseline every other domain falls back to; {@link #ENTITY},
 * {@link #ZONE}, {@link #PLAYER} are per-domain overrides that may be set or left unresolved.
 * <p>Deterministic: {@link #values()} order is fixed ({@code GLOBAL, ENTITY, ZONE, PLAYER}) and is the
 * canonical iteration order used across this package; {@link #form()} yields the lowercase registration name
 * and {@link #fromForm(String)} maps back deterministically (unknown forms are rejected). No randomness, no timing.
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
     * Maps a lowercase (or any-case) registration form back to a {@link TimeDomain}. Deterministic;
     * throws {@link IllegalArgumentException} for unknown forms. / 将小写（或任意大小写）注册名回映射为
     * {@link TimeDomain}。确定性；未知形式抛 {@link IllegalArgumentException}。
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