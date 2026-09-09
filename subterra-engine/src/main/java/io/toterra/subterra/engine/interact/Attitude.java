package io.toterra.subterra.engine.interact;

/**
 * 确定性态度枚举 —— p.2.14.1 交互规范模型里对实体的态度描述。权威序（也是 {@link #values()} 序与
 * td 文档契约序）固定为 {@code FRIENDLY, NEUTRAL, HOSTILE}；任何依赖枚举顺序的逻辑都以
 * {@link #ordinal()} 此序为锚点，绝不重排。
 * <p>
 * <b>数据模型内部语义，非渲染标注</b>：本枚举是交互规范数据模型内部确定性的态度分类，仅作世界内的态度
 * 描述（友善 / 中立 / 敌意），<b>不</b>是任何 UI 渲染层的颜色/标记/标注；零 HUD 渲染规则（p.2.14.3）
 * 如何把态度映射到世界内表达不属于本枚举职责。也提供该态度在 td 文档中的规范化文本名
 * （{@code friendly/neutral/hostile}），用于 {@link InteractSpecParser} 的编解码。
 * <p>
 * Deterministic attitude enum — the in-world attitude description of an entity in the p.2.14.1
 * interaction-spec model. The authoritative order (also the {@link #values()} order and the td-document
 * contract order) is fixed as {@code FRIENDLY, NEUTRAL, HOSTILE}; any logic depending on enum order
 * anchors on this order (via {@link #ordinal()}) and never reorders it.
 * <p>
 * <b>Data-model-internal semantics, not a rendering tag</b>: this enum is a deterministic attitude
 * classification internal to the interaction-spec data model — it is only an in-world attitude
 * description (friendly / neutral / hostile) and is <b>not</b> any colour / marker / label of a UI
 * rendering layer; how the zero-HUD rendering rules (p.2.14.3) carry an attitude into in-world
 * expression is not this enum's concern. It also supplies the canonical text name of an attitude in a td
 * document ({@code friendly/neutral/hostile}) for {@link InteractSpecParser} codec use.
 */
public enum Attitude {

    /** 友善：对玩家以善意相待的世界内态度。Friendly: an in-world bearing of goodwill toward the player. */
    FRIENDLY("friendly"),
    /** 中立：不偏不倚的世界内态度。Neutral: an even, impartial in-world bearing. */
    NEUTRAL("neutral"),
    /** 敌意：对玩家以戒备/敌视相对的世界内态度。Hostile: an in-world bearing of wariness or antagonism toward the player. */
    HOSTILE("hostile");

    private final String tdName;

    Attitude(String tdName) {
        this.tdName = tdName;
    }

    /** 该态度在 td 文档中的规范化名称。The canonical attitude name in a td document. */
    public String tdName() {
        return tdName;
    }

    /**
     * 由 td 规范化名称解析态度；未知文本返回 {@code null}。Resolves the attitude from its canonical td
     * name; returns {@code null} for an unknown text.
     *
     * @param text td 规范化名称 / the canonical td name.
     * @return 匹配的态度或 null / the matching attitude, or {@code null}.
     */
    public static Attitude fromTd(String text) {
        for (Attitude a : values()) {
            if (a.tdName.equals(text)) {
                return a;
            }
        }
        return null;
    }
}
