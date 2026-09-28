package io.toterra.subterra.api.worldgen;

import java.util.Locale;
import java.util.Objects;

/**
 * p.2.36.2 混合函数族（纯 JDK、确定性）：九维关系图每条边（{@link RelationEdge}）与三场四元组
 * （{@link FieldQuad}）都选一个混合函数。族只给五支（够用，不预设扩展）：
 * <ul>
 *   <li>{@link #MIX}——按权重线性混合（缺省）。</li>
 *   <li>{@link #ADD}——加权相加后归一。</li>
 *   <li>{@link #MULTIPLY}——相乘（遮罩语义，如山地掩码）。</li>
 *   <li>{@link #MAX} / {@link #MIN}——取大 / 取小（脊线、谷线语义）。</li>
 * </ul>
 * 渲染（{@link #id()}）只有规范小写名，解析（{@link #parse}）宽容大小写与空白。
 * <p>
 * p.2.36.2 the blend function family (pure JDK, deterministic): every relation edge
 * ({@link RelationEdge}) and three-field quad ({@link FieldQuad}) picks one. Five members only — enough,
 * with no extension points presupposed: {@link #MIX} (weighted linear mix, the default), {@link #ADD}
 * (weighted sum, then normalized), {@link #MULTIPLY} (mask semantics, e.g. the mountain mask) and
 * {@link #MAX} / {@link #MIN} (ridge / valley semantics). Rendering emits only the canonical lowercase id;
 * parsing tolerates case and whitespace.
 */
public enum Blend {

    /** Weighted linear mix (default). / 按权重线性混合（缺省）。 */
    MIX,

    /** Weighted sum, normalized afterwards. / 加权相加后归一。 */
    ADD,

    /** Product (mask semantics). / 相乘（遮罩语义）。 */
    MULTIPLY,

    /** Maximum (ridge semantics). / 取大（脊线语义）。 */
    MAX,

    /** Minimum (valley semantics). / 取小（谷线语义）。 */
    MIN;

    /** The canonical lowercase id used by td rendering. / td 渲染用的规范小写名。 */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parses a td value into a blend (trim + lowercase); anything else is rejected deterministically. /
     * 解析 td 值为混合函数（trim + 小写），其余确定性拒绝。
     *
     * @param value the td value (non-null).
     * @throws NullPointerException     if {@code value} is null.
     * @throws IllegalArgumentException if the value names no known blend.
     */
    public static Blend parse(String value) {
        Objects.requireNonNull(value, "blend must not be null");
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "mix" -> MIX;
            case "add" -> ADD;
            case "multiply" -> MULTIPLY;
            case "max" -> MAX;
            case "min" -> MIN;
            default -> throw new IllegalArgumentException("unknown blend '" + value + "'");
        };
    }
}
