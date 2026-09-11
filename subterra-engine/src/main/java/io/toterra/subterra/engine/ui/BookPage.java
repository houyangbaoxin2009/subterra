package io.toterra.subterra.engine.ui;

import java.util.List;
import java.util.Objects;

/**
 * A single documentation-book page (p.2.22.2): a typed page carrying a title and
 * a content string. The shape is inspired by Patchouli's data-driven book/page
 * idea (VazkiiMods/Patchouli, CC-BY-NC-SA 3.0) — a clean-room reference of the
 * idea only: zero upstream code or assets are included, and the data form is
 * td-ized (tie data) instead of upstream's JSON page definitions. As a canonical
 * record, {@code equals}/{@code hashCode} compare all three components; no
 * randomness and no timing — identical inputs yield identical bits.
 *
 * <p>Page types use the following fixed, documented enum order:
 * <ul>
 *   <li>{@code "text"} — prose / explanation page;</li>
 *   <li>{@code "recipe"} — crafting-recipe page;</li>
 *   <li>{@code "entity"} — entity showcase page.</li>
 * </ul>
 * The set is open: {@link #KNOWN_TYPES} documents the fixed core order, and any
 * other non-blank type string is preserved verbatim (schema-free forward
 * compatibility — see {@link io.toterra.subterra.engine.ui.DocBookTd}).
 *
 * <p>单个文档书籍页（p.2.22.2）：带类型的页，承载标题与内容字符串。形态受 Patchouli 的
 * 数据驱动书籍/页思想启发（VazkiiMods/Patchouli，CC-BY-NC-SA 3.0）——仅为该思想的
 * clean-room 参考：不含任何上游代码或资产，数据形态 td 化（tie data），而非上游的 JSON
 * 页定义。作为规范 record，{@code equals}/{@code hashCode} 比较全部三组件；无随机无时序——
 * 同输入恒得同字节。
 *
 * <p>页类型按以下固定、文档化的枚举序：
 * <ul>
 *   <li>{@code "text"}——散文/说明页；</li>
 *   <li>{@code "recipe"}——合成配方页；</li>
 *   <li>{@code "entity"}——实体展示页。</li>
 * </ul>
 * 集合是开放的：{@link #KNOWN_TYPES} 文档化固定核心序，任何其他非空白类型串原样保留
 * （schema-free 前向兼容——见 {@link io.toterra.subterra.engine.ui.DocBookTd}）。
 *
 * @param type    the page type; non-blank (fixed enum order above). 页类型；非空白（固定枚举序见上）。
 * @param title   the page title; non-null. 页标题；非空。
 * @param content the page content; non-null. 页内容；非空。
 */
public record BookPage(String type, String title, String content) {

    /**
     * Fixed, documented core page types in enum order (open set — unknown types
     * are preserved verbatim). 固定文档化核心页类型（枚举序，开放集合——未知类型原样保留）。
     */
    public static final List<String> KNOWN_TYPES = List.of("text", "recipe", "entity");

    public BookPage {
        Objects.requireNonNull(type, "type must be non-null");
        Objects.requireNonNull(title, "title must be non-null");
        Objects.requireNonNull(content, "content must be non-null");
        if (type.isBlank()) {
            throw new IllegalArgumentException("type must be non-blank: " + type);
        }
    }
}
