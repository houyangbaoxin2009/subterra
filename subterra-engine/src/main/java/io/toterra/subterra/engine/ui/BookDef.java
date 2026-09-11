package io.toterra.subterra.engine.ui;

import java.util.List;
import java.util.Objects;

/**
 * A documentation book definition (p.2.22.2): handbook / bestiary / tutorial
 * data — an id, a title and a fixed-order page list. The shape is inspired by
 * Patchouli's data-driven book definition idea (VazkiiMods/Patchouli,
 * CC-BY-NC-SA 3.0) — a clean-room reference of the idea only: zero upstream code
 * or assets are included, and the data form is td-ized (tie data) instead of
 * upstream's JSON book structures. As a canonical record, {@code equals} /
 * {@code hashCode} compare the id, the title and the page list element-wise;
 * {@code pages} is defensively copied, so the iteration order is fixed and
 * immutable — identical inputs yield identical bits, with no randomness and no
 * timing.
 *
 * <p>文档书籍定义（p.2.22.2）：手册/图鉴/教程数据——id、标题与固定序页列表。形态受
 * Patchouli 的数据驱动书籍定义思想启发（VazkiiMods/Patchouli，CC-BY-NC-SA 3.0）——
 * 仅为该思想的 clean-room 参考：不含任何上游代码或资产，数据形态 td 化（tie data），
 * 而非上游的 JSON 书籍结构。作为规范 record，{@code equals}/{@code hashCode} 逐项比较
 * id、标题与页列表；{@code pages} 防御性拷贝，迭代序固定且不可变——同输入恒得同字节，
 * 无随机无时序。
 *
 * @param id    the unique book id (e.g. {@code "subterra:field_guide"}); non-blank.
 *              唯一书籍 id（如 {@code "subterra:field_guide"}）；非空白。
 * @param title the book title; non-null. 书籍标题；非空。
 * @param pages the fixed-order page list; non-null elements, immutable copy.
 *              固定序页列表；元素非空，不可变拷贝。
 */
public record BookDef(String id, String title, List<BookPage> pages) {

    public BookDef {
        Objects.requireNonNull(id, "id must be non-null");
        Objects.requireNonNull(title, "title must be non-null");
        Objects.requireNonNull(pages, "pages must be non-null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must be non-blank: " + id);
        }
        for (BookPage page : pages) {
            Objects.requireNonNull(page, "page must be non-null: " + id);
        }
        pages = List.copyOf(pages);
    }
}
