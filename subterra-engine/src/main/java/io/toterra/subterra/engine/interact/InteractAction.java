package io.toterra.subterra.engine.interact;

/**
 * 交互动作枚举 —— p.2.14.1 交互规范模型中的世界内动作词。权威序（也是 {@link #values()} 序与
 * td 文档契约序）固定为 {@code CONVERSE, READ, OFFER, EXAMINE}；任何依赖枚举顺序的逻辑都以
 * {@link #ordinal()} 此序为锚点，绝不重排。动作词汇仅限世界内表达（交谈 / 阅读 / 呈奉 / 端详），
 * 无任何系统层名词。也提供该动作在 td 文档中的规范化文本名（{@code converse/read/offer/examine}），
 * 用于 {@link InteractSpecParser} 的编解码。
 * <p>
 * Interaction-action enum — the in-world action words in the p.2.14.1 interaction-spec model. The
 * authoritative order (also the {@link #values()} order and the td-document contract order) is fixed as
 * {@code CONVERSE, READ, OFFER, EXAMINE}; any logic depending on enum order anchors on this order (via
 * {@link #ordinal()}) and never reorders it. The action vocabulary is confined to in-world expression
 * (converse / read / offer / examine) with no system-layer terms. It also supplies the canonical text
 * name of an action in a td document ({@code converse/read/offer/examine}) for {@link InteractSpecParser}
 * codec use.
 */
public enum InteractAction {

    /** 交谈：与活物实体来往言谈。Converse: to exchange words with an animate being. */
    CONVERSE("converse"),
    /** 阅读：读懂文书/刻字上的记载。Read: to make out the writing on a document or carving. */
    READ("read"),
    /** 呈奉：向活物实体呈上某物。Offer: to present something to an animate being. */
    OFFER("offer"),
    /** 端详：细看某物的形貌与微处。Examine: to look closely at the form and detail of something. */
    EXAMINE("examine");

    private final String tdName;

    InteractAction(String tdName) {
        this.tdName = tdName;
    }

    /** 该动作在 td 文档中的规范化名称。The canonical action name in a td document. */
    public String tdName() {
        return tdName;
    }

    /**
     * 由 td 规范化名称解析动作；未知文本返回 {@code null}。Resolves the action from its canonical td
     * name; returns {@code null} for an unknown text.
     *
     * @param text td 规范化名称 / the canonical td name.
     * @return 匹配的动作或 null / the matching action, or {@code null}.
     */
    public static InteractAction fromTd(String text) {
        for (InteractAction a : values()) {
            if (a.tdName.equals(text)) {
                return a;
            }
        }
        return null;
    }
}
