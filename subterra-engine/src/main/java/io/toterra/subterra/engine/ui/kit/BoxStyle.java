package io.toterra.subterra.engine.ui.kit;

import java.util.Objects;

/**
 * The box-model style of a UI node (p.2.37.1): outer margins, border width and inner
 * paddings, all in layout pixels. Pure value type; negative values are rejected
 * deterministically; {@link #DEFAULT} is the all-zero neutral style.
 *
 * <p>UI 节点的盒模型样式（p.2.37.1）：外边距、边框宽与内边距，均为布局像素。纯值类型；
 * 负值确定性拒绝；{@link #DEFAULT} 为全零中性样式。
 */
public record BoxStyle(
        int marginLeft, int marginTop, int marginRight, int marginBottom,
        int border,
        int paddingLeft, int paddingTop, int paddingRight, int paddingBottom) {

    /** The neutral all-zero style. / 中性全零样式。 */
    public static final BoxStyle DEFAULT = new BoxStyle(0, 0, 0, 0, 0, 0, 0, 0, 0);

    public BoxStyle {
        Objects.requireNonNull(this);
        if (marginLeft < 0 || marginTop < 0 || marginRight < 0 || marginBottom < 0
                || border < 0
                || paddingLeft < 0 || paddingTop < 0 || paddingRight < 0 || paddingBottom < 0) {
            throw new IllegalArgumentException("box style values must be >= 0: " + this);
        }
    }

    /** Total horizontal space consumed around the content: margins + 2*border + paddings. /
     *  内容两侧占用的水平总量：外边距 + 2*边框 + 内边距。 */
    public int horizontal() {
        return marginLeft + marginRight + 2 * border + paddingLeft + paddingRight;
    }

    /** Total vertical space consumed around the content: margins + 2*border + paddings. /
     *  内容两侧占用的垂直总量：外边距 + 2*边框 + 内边距。 */
    public int vertical() {
        return marginTop + marginBottom + 2 * border + paddingTop + paddingBottom;
    }

    /** Left offset of the content box: margin + border + padding. / 内容盒左偏移。 */
    public int leftInset() {
        return marginLeft + border + paddingLeft;
    }

    /** Top offset of the content box: margin + border + padding. / 内容盒上偏移。 */
    public int topInset() {
        return marginTop + border + paddingTop;
    }
}
