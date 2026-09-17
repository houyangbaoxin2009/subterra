package io.toterra.subterra.engine.ui.kit;

/**
 * The five theme-token families of the Subterra UI kit (p.2.37.1): {@code color / font /
 * spacing / corner / animation}. The {@link #NEUTRAL} default is only a neutral starting
 * point — <b>the visual style is decided by the consumer</b> (user decision 2026-09-17):
 * themes are wholesale-replaceable and the core makes zero style assumptions. Pure value
 * type; colors are ARGB ints; the animation curve set is fixed.
 *
 * <p>Subterra UI 库的五族主题 token（p.2.37.1）：{@code color / font / spacing / corner /
 * animation}。{@link #NEUTRAL} 缺省只是中性起点——<b>视觉方式由消费者决定</b>（2026-09-17
 * 用户定案）：主题整包可替换，核心对视觉风格零假设。纯值类型；颜色为 ARGB 整型；动画曲线集固定。
 */
public record ThemeTokens(
        int colorBackground,
        int colorSurface,
        int colorText,
        int colorAccent,
        int colorBorder,
        String fontFamily,
        int fontSize,
        int spacingUnit,
        int spacingGap,
        int cornerRadius,
        int animationDurationMs,
        AnimationCurve animationCurve) {

    /** The fixed animation-curve set. / 固定动画曲线集。 */
    public enum AnimationCurve {
        /** Linear interpolation. / 线性插值。 */
        LINEAR("linear"),
        /** Smoothstep (3t² − 2t³). / 平滑插值。 */
        SMOOTHSTEP("smoothstep"),
        ;

        private final String id;

        AnimationCurve(String id) {
            this.id = id;
        }

        /** The td-declared literal. / td 声明字面量。 */
        public String id() {
            return id;
        }
    }

    /** The neutral starting-point theme (consumer-replaceable). / 中性起点主题（可整包替换）。 */
    public static final ThemeTokens NEUTRAL = new ThemeTokens(
            0xFF2A2A2E, 0xFF35353A, 0xFFE8E8E8, 0xFF4C8BF5, 0xFF555559,
            "default", 9, 4, 8, 4, 150, AnimationCurve.SMOOTHSTEP);

    public ThemeTokens {
        java.util.Objects.requireNonNull(fontFamily, "fontFamily must not be null");
        java.util.Objects.requireNonNull(animationCurve, "animationCurve must not be null");
        if (fontSize <= 0 || spacingUnit <= 0 || spacingGap < 0 || cornerRadius < 0
                || animationDurationMs < 0) {
            throw new IllegalArgumentException("theme token values out of range: " + this);
        }
    }
}
