package io.toterra.subterra.engine.ui.kit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The deterministic layout engine of the Subterra UI kit (p.2.37.1). Pure JDK, no wall-clock,
 * no randomness: same tree + same viewport + same theme → the same placement list, byte for
 * byte. Semantics (pinned for p.2.37.1, extensible by later sub-items without changing these):
 * <ul>
 *   <li>The root rect is the node's {@code width}/{@code height} props, defaulting to the
 *       viewport; the root is placed at (0, 0).</li>
 *   <li>{@code row}: children laid left→right with the theme gap; each child's height is its
 *       {@code height} prop, else the inner height (vertical stretch); {@code grow=x} children
 *       share the leftover inner width EQUALLY (integer floor) after non-grow widths and gaps;
 *       child margins offset the placement.</li>
 *   <li>{@code column}: symmetric vertical (width stretches, {@code grow=y} splits the
 *       leftover height equally).</li>
 *   <li>{@code stack}/{@code panel}: every child stretches to the inner rect (later on top).</li>
 *   <li>{@code grid}: the {@code cols} prop (default 1); cell sizes floored, leftovers unused;
 *       children fill their cells in fixed order (row-major).</li>
 *   <li>Leaf widgets take their prop sizes verbatim.</li>
 * </ul>
 * Duplicate node ids anywhere in the tree are rejected deterministically before layout.
 *
 * <p>Subterra UI 库的确定性布局引擎（p.2.37.1）。纯 JDK、无墙钟、无随机：同树 + 同视口 +
 * 同主题 → 同一放置列表（逐字节）。语义钉死（后续子项可扩展、不改这些）：
 * 根矩形取 width/height props（缺省取视口）、置于 (0,0)；row 左→右按主题 gap 排布，子高取
 * height prop 否则内高（纵向拉伸），grow=x 子均分（整除 floor）剩余内宽，子外边距偏移放置；
 * column 垂直对称（grow=y 均分剩余内高）；stack/panel 子全部拉伸到内矩形（后子上层）；
 * grid 取 cols prop（缺省 1），单元格尺寸 floor、余数弃用，子按固定序（行主序）填格；
 * 叶子控件按 props 尺寸原样。全树重复 id 布局前确定性拒绝。
 */
public final class LayoutEngine {

    private LayoutEngine() {
    }

    /**
     * Lays the tree out over the viewport; returns the placements in fixed DFS order.
     * / 对树按视口布局；按固定深度优先序返回放置列表。
     *
     * @param root      the tree root (non-null).
     * @param viewportW viewport width (>= 0).
     * @param viewportH viewport height (>= 0).
     * @param theme     the active tokens (non-null).
     * @return the fixed-order placements.
     * @throws NullPointerException     if any argument is null.
     * @throws IllegalArgumentException if the viewport is negative or an id duplicates.
     */
    public static List<Placement> layout(UiNode root, int viewportW, int viewportH,
                                         ThemeTokens theme) {
        Objects.requireNonNull(root, "root must not be null");
        Objects.requireNonNull(theme, "theme must not be null");
        if (viewportW < 0 || viewportH < 0) {
            throw new IllegalArgumentException("viewport must be >= 0: " + viewportW + "x" + viewportH);
        }
        checkUniqueIds(root);
        int rw = root.intProp("width", viewportW);
        int rh = root.intProp("height", viewportH);
        List<Placement> out = new ArrayList<>();
        place(root, 0, 0, rw, rh, theme, out);
        return List.copyOf(out);
    }

    private static void checkUniqueIds(UiNode node) {
        Set<String> seen = new HashSet<>();
        collectIds(node, seen);
    }

    private static void collectIds(UiNode node, Set<String> seen) {
        if (!seen.add(node.id())) {
            throw new IllegalArgumentException("duplicate node id: " + node.id());
        }
        for (UiNode child : node.children()) {
            collectIds(child, seen);
        }
    }

    /** Places the node rect, emits it, then lays out the children by kind. /
     *  放置节点矩形并发射，随后按种类布局子节点。 */
    private static void place(UiNode node, int x, int y, int w, int h,
                              ThemeTokens theme, List<Placement> out) {
        out.add(new Placement(node.id(), node.kind(), x, y, w, h));
        if (node.children().isEmpty()) {
            return;
        }
        BoxStyle box = node.box();
        int ix = x + box.leftInset();
        int iy = y + box.topInset();
        int innerW = Math.max(0, w - box.horizontal());
        int innerH = Math.max(0, h - box.vertical());
        switch (node.kind()) {
            case ROW -> layoutFlow(node, ix, iy, innerW, innerH, theme, out, true);
            case COLUMN -> layoutFlow(node, ix, iy, innerW, innerH, theme, out, false);
            case STACK, PANEL -> layoutStack(node, ix, iy, innerW, innerH, theme, out);
            case GRID -> layoutGrid(node, ix, iy, innerW, innerH, theme, out);
            default -> {
                // Leaf kinds with children are treated as stacks (deterministic fallback).
                // 带子节点的叶子种类按叠层处理（确定性回退）。
                layoutStack(node, ix, iy, innerW, innerH, theme, out);
            }
        }
    }

    /** Flow layout: horizontal when {@code horizontal}, vertical otherwise. Margins offset
     *  the placement and consume cursor space; they never shrink an explicit prop size.
     *  Stretched children (no size prop) shrink by their own margins to stay inside. /
     *  流式布局：{@code horizontal} 为水平，否则垂直。外边距偏移放置并占用游标空间，
     *  但绝不压缩显式 prop 尺寸；拉伸子节点（无尺寸 prop）按自身外边距收缩以留在内部。 */
    private static void layoutFlow(UiNode node, int ix, int iy, int innerW, int innerH,
                                   ThemeTokens theme, List<Placement> out, boolean horizontal) {
        List<UiNode> children = node.children();
        int gap = theme.spacingGap();
        int main = horizontal ? innerW : innerH;
        int growCount = 0;
        int fixedMain = 0;
        for (UiNode child : children) {
            boolean grow = horizontal ? child.boolProp("grow_x") : child.boolProp("grow_y");
            if (grow) {
                growCount++;
            } else if (horizontal) {
                fixedMain += child.box().marginLeft() + prefWidth(child, innerW)
                        + child.box().marginRight();
            } else {
                fixedMain += child.box().marginTop() + prefHeight(child, innerH)
                        + child.box().marginBottom();
            }
        }
        int gaps = Math.max(0, children.size() - 1) * gap;
        int leftover = Math.max(0, main - fixedMain - gaps);
        int growSize = growCount == 0 ? 0 : leftover / growCount;
        int cursor = horizontal ? ix : iy;
        for (UiNode child : children) {
            BoxStyle cb = child.box();
            boolean grow = horizontal ? child.boolProp("grow_x") : child.boolProp("grow_y");
            int outer = grow ? growSize
                    : (horizontal ? cb.marginLeft() + prefWidth(child, innerW) + cb.marginRight()
                    : cb.marginTop() + prefHeight(child, innerH) + cb.marginBottom());
            int cw;
            int ch;
            if (horizontal) {
                cw = Math.max(0, outer - cb.horizontal());
                ch = child.hasProp("height") ? child.intProp("height", 0)
                        : Math.max(0, innerH - cb.vertical());
            } else {
                cw = child.hasProp("width") ? child.intProp("width", 0)
                        : Math.max(0, innerW - cb.horizontal());
                ch = Math.max(0, outer - cb.vertical());
            }
            int cx = horizontal ? cursor + cb.marginLeft() : ix + cb.marginLeft();
            int cy = horizontal ? iy + cb.marginTop() : cursor + cb.marginTop();
            place(child, cx, cy, cw, ch, theme, out);
            cursor += outer + gap;
        }
    }

    /** Stack layout: every child stretches to the inner rect, in order (later on top). /
     *  叠层布局：子全部拉伸到内矩形，按序（后子上层）。 */
    private static void layoutStack(UiNode node, int ix, int iy, int innerW, int innerH,
                                    ThemeTokens theme, List<Placement> out) {
        for (UiNode child : node.children()) {
            BoxStyle cb = child.box();
            int cw = Math.max(0, innerW - cb.horizontal());
            int ch = Math.max(0, innerH - cb.vertical());
            place(child, ix + cb.marginLeft(), iy + cb.marginTop(), cw, ch, theme, out);
        }
    }

    /** Grid layout: row-major fixed order, floored cell sizes, leftovers unused. /
     *  网格布局：行主序固定，单元格尺寸 floor、余数弃用。 */
    private static void layoutGrid(UiNode node, int ix, int iy, int innerW, int innerH,
                                   ThemeTokens theme, List<Placement> out) {
        int cols = Math.max(1, node.intProp("cols", 1));
        int gap = theme.spacingGap();
        List<UiNode> children = node.children();
        int rows = (children.size() + cols - 1) / cols;
        int cellW = Math.max(0, (innerW - (cols - 1) * gap) / cols);
        int cellH = Math.max(0, rows == 0 ? 0 : (innerH - (rows - 1) * gap) / rows);
        for (int i = 0; i < children.size(); i++) {
            UiNode child = children.get(i);
            BoxStyle cb = child.box();
            int cx = ix + (i % cols) * (cellW + gap);
            int cy = iy + (i / cols) * (cellH + gap);
            place(child, cx + cb.marginLeft(), cy + cb.marginTop(),
                    Math.max(0, cellW - cb.horizontal()), Math.max(0, cellH - cb.vertical()),
                    theme, out);
        }
    }

    /** Preferred width: the {@code width} prop, else the inner width (horizontal stretch). /
     *  期望宽度：{@code width} prop，否则内宽（水平拉伸）。 */
    private static int prefWidth(UiNode node, int innerW) {
        return node.intProp("width", innerW);
    }

    /** Preferred height: the {@code height} prop, else the inner height (vertical stretch). /
     *  期望高度：{@code height} prop，否则内高（垂直拉伸）。 */
    private static int prefHeight(UiNode node, int innerH) {
        return node.intProp("height", innerH);
    }
}
