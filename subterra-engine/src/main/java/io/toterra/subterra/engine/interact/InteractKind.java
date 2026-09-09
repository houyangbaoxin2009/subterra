package io.toterra.subterra.engine.interact;

/**
 * 世界内可交互实体枚举 —— p.2.14.1 交互规范模型的基础实体集。权威序（也是 {@link #values()} 序与
 * td 文档契约序）固定为 {@code NPC, INSCRIPTION, SCROLL, LETTER, RELIC}；任何依赖枚举顺序的逻辑都以
 * 此序为锚点，绝不重排。词汇面仅限世界内实体，无任何系统层名词。也提供该实体在 td 文档中的规范化文本名
 * （{@code npc/inscription/scroll/letter/relic}），用于 {@link InteractSpecParser} 的编解码。
 * <p>
 * In-world interactable-entity enum — the base entity set of the p.2.14.1 interaction-spec model.
 * The authoritative order (also the {@link #values()} order and the td-document contract order) is fixed
 * as {@code NPC, INSCRIPTION, SCROLL, LETTER, RELIC}; any logic depending on enum order anchors on this
 * order and never reorders it. The vocabulary is confined to in-world entities with no system-layer
 * terms. It also supplies the canonical text name of each entity in a td document
 * ({@code npc/inscription/scroll/letter/relic}) for {@link InteractSpecParser} codec use.
 */
public enum InteractKind {

    /** 行商/老者的活物实体，可交谈、可呈奉。An animate being (merchant, elder) — ownable to converse and offer. */
    NPC("npc"),
    /** 碑上的刻字，可阅读、可端详。Carved words on a stele — ownable to read and examine. */
    INSCRIPTION("inscription"),
    /** 卷起的文书记载，可阅读。A rolled writing — ownable to read. */
    SCROLL("scroll"),
    /** 措辞的信件，可阅读、可端详。A worded letter — ownable to read and examine. */
    LETTER("letter"),
    /** 承载记忆的遗物，可端详、可呈奉。An heirloom carrying memory — ownable to examine and offer. */
    RELIC("relic");

    private final String tdName;

    InteractKind(String tdName) {
        this.tdName = tdName;
    }

    /** 该实体在 td 文档中的规范化名称。The canonical entity name in a td document. */
    public String tdName() {
        return tdName;
    }

    /**
     * 由 td 规范化名称解析实体；未知文本返回 {@code null}。Resolves the entity from its canonical td
     * name; returns {@code null} for an unknown text.
     *
     * @param text td 规范化名称 / the canonical td name.
     * @return 匹配的实体或 null / the matching entity, or {@code null}.
     */
    public static InteractKind fromTd(String text) {
        for (InteractKind k : values()) {
            if (k.tdName.equals(text)) {
                return k;
            }
        }
        return null;
    }
}
