package io.toterra.subterra.engine.ui.kit;

/**
 * The fixed widget-kind set of the Subterra UI kit (p.2.37.1), in canonical order.
 * Pure JDK, zero MC/GL imports; {@link #byId} resolves the td-declared literal
 * case-insensitively with deterministic rejection.
 *
 * <p>Subterra UI 库的固定控件种类集（p.2.37.1），规范序。纯 JDK、零 MC/GL import；
 * {@link #byId} 大小写不敏感解析 td 声明字面量，非法确定性拒绝。
 */
public enum WidgetKind {
    /** A plain container (content box for a single child or children stack). / 普通容器。 */
    PANEL("panel"),
    /** Z-stacked children sharing the inner rect (later on top). / 叠层容器（后子在上）。 */
    STACK("stack"),
    /** Horizontal flow layout. / 水平流式布局。 */
    ROW("row"),
    /** Vertical flow layout. / 垂直流式布局。 */
    COLUMN("column"),
    /** Grid layout (row-major, {@code cols} prop). / 网格布局（行主序，{@code cols} prop）。 */
    GRID("grid"),
    /** Static text (holds a translation key; resolved via the locale table). / 静态文本。 */
    LABEL("label"),
    /** Pressable button. / 按钮。 */
    BUTTON("button"),
    /** Single-line text input. / 单行文本输入。 */
    TEXT_FIELD("text_field"),
    /** Boolean toggle. / 布尔开关。 */
    TOGGLE("toggle"),
    /** Range slider. / 滑杆。 */
    SLIDER("slider"),
    /** Virtualized scrolling list. / 虚拟滚动列表。 */
    LIST("list"),
    /** Nine-slice textured panel. / 九宫格纹理面板。 */
    NINE_SLICE("nine_slice"),
    ;

    private final String id;

    WidgetKind(String id) {
        this.id = id;
    }

    /** The td-declared literal ({@code "panel"}, {@code "nine_slice"}, …). / td 声明字面量。 */
    public String id() {
        return id;
    }

    /**
     * Resolves a (case-insensitive) kind literal; illegal/blank forms are rejected
     * deterministically. / 解析（大小写不敏感的）种类字面量；非法确定性拒绝。
     *
     * @param id the literal (non-null).
     * @return the kind.
     * @throws NullPointerException     if {@code id} is null.
     * @throws IllegalArgumentException if the literal names no known kind.
     */
    public static WidgetKind byId(String id) {
        java.util.Objects.requireNonNull(id, "kind id must not be null");
        String lowered = id.trim().toLowerCase(java.util.Locale.ROOT);
        for (WidgetKind k : values()) {
            if (k.id.equals(lowered)) {
                return k;
            }
        }
        throw new IllegalArgumentException("unknown ui widget kind '" + id + "'");
    }
}
