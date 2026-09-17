package io.toterra.subterra.engine.ui.kit;

/**
 * One deterministic layout output: the placed rect of a node, emitted in fixed depth-first
 * order (node itself, then children in order). Pure value type.
 *
 * <p>单条确定性布局输出：一个节点的放置矩形，按固定深度优先序发射（先节点自身、再按序子节点）。
 * 纯值类型。
 */
public record Placement(String id, WidgetKind kind, int x, int y, int width, int height) {
}
