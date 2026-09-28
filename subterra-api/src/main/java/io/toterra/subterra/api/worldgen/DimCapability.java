package io.toterra.subterra.api.worldgen;

import java.util.Locale;
import java.util.Objects;

/**
 * p.2.36.2 单个九维能力位（纯 JDK 不可变值）：{@code dim}（必须是 {@link EcoDim#ALL} 中的内建维
 * id）+ {@link DimMode} + 重定向目标 {@code target}。
 * <ul>
 *   <li>{@code dim} 规范化为 trim + 小写；不在九维表内确定性拒绝（扩展维暂不进配置面）。</li>
 *   <li>{@code target} 仅 {@link DimMode#REDIRECT} 可非空，且须为 {@code namespace:path}
 *       （命名空间 {@code [a-z0-9_.-]+}、路径 {@code [a-z0-9_./-]+}）；其余模式必须为空串。</li>
 * </ul>
 * <p>
 * p.2.36.2 one nine-dimension capability bit (pure-JDK immutable value): the {@code dim} (must be a
 * built-in id from {@link EcoDim#ALL}), a {@link DimMode} and the redirect {@code target}. The dim is
 * normalized (trim + lowercase) and unknown ids are rejected deterministically (extension dimensions do
 * not enter the config surface yet); {@code target} is non-blank only for {@link DimMode#REDIRECT} and must
 * be a {@code namespace:path} (namespace {@code [a-z0-9_.-]+}, path {@code [a-z0-9_./-]+}); every other
 * mode requires the empty string.
 *
 * @param dim    the built-in dimension id (normalized: lowercase).
 * @param mode   the capability mode (non-null).
 * @param target the redirect target ({@code namespace:path}), or {@code ""} when the mode is not
 *               {@link DimMode#REDIRECT}.
 */
public record DimCapability(String dim, DimMode mode, String target) {

    /** The empty target used by every non-redirect mode. / 非重定向模式使用的空目标。 */
    public static final String NO_TARGET = "";

    /** The prefix separating a redirect mode from its target in td ({@code "redirect:"}). /
     *  td 中分隔重定向模式与其目标的前缀（{@code "redirect:"}）。 */
    public static final String REDIRECT_PREFIX = "redirect:";

    public DimCapability {
        Objects.requireNonNull(dim, "dim must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(target, "target must not be null");
        dim = dim.trim().toLowerCase(Locale.ROOT);
        if (EcoDim.of(dim) == null) {
            throw new IllegalArgumentException("unknown dimension '" + dim + "'");
        }
        target = target.trim();
        if (mode == DimMode.REDIRECT) {
            if (target.isEmpty()) {
                throw new IllegalArgumentException("redirect target must not be empty for dim '" + dim + "'");
            }
            int colon = target.indexOf(':');
            if (colon <= 0 || target.indexOf(':', colon + 1) >= 0) {
                throw new IllegalArgumentException("redirect target must be exactly 'namespace:path': " + target);
            }
            if (!target.substring(0, colon).matches("[a-z0-9_.-]+")
                    || !target.substring(colon + 1).matches("[a-z0-9_./-]+")) {
                throw new IllegalArgumentException("illegal redirect target '" + target + "'");
            }
        } else if (!target.isEmpty()) {
            throw new IllegalArgumentException("target is only allowed for REDIRECT (dim '" + dim + "')");
        }
    }

    /** The capability with the dimension enabled and no target. / 该维启用且无目标的能力位。 */
    public static DimCapability enabled(String dim) {
        return new DimCapability(dim, DimMode.ENABLE, NO_TARGET);
    }

    /** Whether this bit redirects to another implementation. / 该位是否重定向到别的实现。 */
    public boolean redirected() {
        return mode == DimMode.REDIRECT;
    }

    /** The td value rendering ({@code enable} / {@code disable} / {@code redirect:ns:path}). /
     *  td 值形式（{@code enable} / {@code disable} / {@code redirect:ns:path}）。 */
    public String tdValue() {
        return mode == DimMode.REDIRECT ? REDIRECT_PREFIX + target : mode.id();
    }
}
