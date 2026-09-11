package io.toterra.subterra.engine.ui;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Documentation-book td codec (p.2.22.2): deterministic {@code fromTd}/{@code
 * toTd} round-trip for a single {@link BookDef}. The concept is inspired by
 * Patchouli's data-driven books/pages idea (VazkiiMods/Patchouli,
 * CC-BY-NC-SA 3.0) — a clean-room reference of the idea only: zero upstream code
 * or assets are included, and the data form is td-ized (tie data) instead of
 * upstream's JSON book structures. Pure JDK; fixed field order, no timestamps,
 * no randomness and no timing — identical inputs yield identical bytes.
 *
 * <p>Document shape (td, fixed field order, no timestamps): the root table
 * carries a named entry {@code book = [...]} (mirroring the rules-document
 * layer) —
 * <pre>{@code
 * [
 *   book = [
 *     id = "subterra:field_guide",
 *     title = "Field Guide",
 *     pages = [
 *       [ type = "text", title = "Intro", content = "..." ],
 *     ],
 *   ],
 * ]
 * }</pre>
 *
 * <p>{@link #fromTd(TdTable)} parses deterministically: a missing or non-table
 * {@code book} raises {@link IllegalArgumentException}; the book {@code id} must
 * be present and non-blank (else {@link IllegalArgumentException}); a missing
 * {@code title} defaults to {@code ""}; a missing or non-table {@code pages}
 * yields an empty page list; a page row that is not a table or lacks a non-blank
 * {@code type} is skipped silently (schema-free, matching the rules-document
 * layer); a missing page {@code title}/{@code content} defaults to {@code ""}.
 * Non-scalar field values are opaque on the string contract and read as missing.
 * Page types follow the fixed enum order documented on {@link BookPage}; unknown
 * type strings are preserved verbatim.
 *
 * <p>{@link #toTd(BookDef)} writes the canonical td text: root {@code book =
 * [...]} with {@code id}, {@code title}, then {@code pages = [...]} (fixed field
 * order); each page is a nested table with {@code type}, {@code title}, then
 * {@code content}, in the given list order. The output parses with
 * {@link Td#parse} and round-trips byte-identically through {@link #fromTd}:
 * same input, same bytes, run any number of times.
 *
 * <p>文档书籍 td 编解码（p.2.22.2）：单个 {@link BookDef} 的确定性
 * {@code fromTd}/{@code toTd} 往返。概念受 Patchouli 的数据驱动书籍/页思想启发
 * （VazkiiMods/Patchouli，CC-BY-NC-SA 3.0）——仅为该思想的 clean-room 参考：不含任何
 * 上游代码或资产，数据形态 td 化（tie data），而非上游的 JSON 书籍结构。纯 JDK；固定字段
 * 序、禁时间戳、无随机无时序——同输入恒得同字节。
 *
 * <p>文档形态（td，固定字段序，禁时间戳）：根表含命名条目 {@code book = [...]}
 * （与规则文档层同构）——
 * <pre>{@code
 * [
 *   book = [
 *     id = "subterra:field_guide",
 *     title = "Field Guide",
 *     pages = [
 *       [ type = "text", title = "Intro", content = "..." ],
 *     ],
 *   ],
 * ]
 * }</pre>
 *
 * <p>{@link #fromTd(TdTable)} 确定性解析：{@code book} 缺失或非表抛
 * {@link IllegalArgumentException}；书籍 {@code id} 必须存在且非空白（否则
 * {@link IllegalArgumentException}）；{@code title} 缺失默认 {@code ""}；
 * {@code pages} 缺失或非表 → 空页列表；非表或缺非空白 {@code type} 的页行静默跳过
 * （schema-free，与规则文档层一致）；页 {@code title}/{@code content} 缺失默认
 * {@code ""}。非标量字段值在字符串契约面上不透明，按缺失处理。页类型遵循
 * {@link BookPage} 上文档化的固定枚举序；未知类型串原样保留。
 *
 * <p>{@link #toTd(BookDef)} 写出规范 td 文本：根表 {@code book = [...]}，依次为
 * {@code id}、{@code title}、{@code pages = [...]}（固定字段序）；每页为嵌套表，字段序固定
 * 为 {@code type}、{@code title}、{@code content}，按给定列表序。输出可用
 * {@link Td#parse} 解析，经 {@link #fromTd} 逐字节往返恒等：同输入、同字节、跑任意次。
 */
public final class DocBookTd {

    private DocBookTd() {
    }

    /**
     * Parses the {@code book} document into a {@link BookDef} (see class javadoc
     * for validation and defaults). 将 {@code book} 文档解析为 {@link BookDef}
     * （校验与默认值见类注释）。
     *
     * @param doc the book document root table. 书籍文档根表。
     * @return the deterministic book definition. 确定性的书籍定义。
     */
    public static BookDef fromTd(TdTable doc) {
        if (doc == null) {
            throw new IllegalArgumentException("doc must be non-null");
        }
        if (!(doc.get("book") instanceof TdTable book)) {
            throw new IllegalArgumentException("missing book table");
        }
        String id = scalarString(book.get("id"));
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("book id must be present and non-blank");
        }
        String title = scalarString(book.get("title"));
        List<BookPage> pages = book.get("pages") instanceof TdTable pagesTable
                ? pagesOf(pagesTable)
                : List.of();
        return new BookDef(id, title != null ? title : "", pages);
    }

    /**
     * Writes the canonical td text of the given book (see class javadoc: fixed
     * field order, no timestamps). 将给定书籍写出规范 td 文本（见类注释：固定字段序、
     * 禁时间戳）。
     *
     * @param book the book definition, non-null. 书籍定义，非空。
     * @return the canonical td text. 规范 td 文本。
     */
    public static String toTd(BookDef book) {
        if (book == null) {
            throw new IllegalArgumentException("book must be non-null");
        }
        TdTable.Builder pages = TdTable.builder();
        for (BookPage page : book.pages()) {
            pages.element(TdTable.builder()
                    .put("type", page.type())
                    .put("title", page.title())
                    .put("content", page.content())
                    .build());
        }
        TdTable root = TdTable.builder()
                .put("book", TdTable.builder()
                        .put("id", book.id())
                        .put("title", book.title())
                        .put("pages", pages.build())
                        .build())
                .build();
        return Td.write(root);
    }

    private static List<BookPage> pagesOf(TdTable pagesTable) {
        List<BookPage> out = new ArrayList<>();
        for (TdValue item : pagesTable.elements()) {
            if (!(item instanceof TdTable row)) {
                continue;
            }
            String type = scalarString(row.get("type"));
            if (type == null || type.isBlank()) {
                continue; // page without a type is skipped silently
            }
            String title = scalarString(row.get("title"));
            String content = scalarString(row.get("content"));
            out.add(new BookPage(type, title != null ? title : "", content != null ? content : ""));
        }
        return List.copyOf(out);
    }

    /**
     * Canonical scalar rendering of a td value (same contract as the rules
     * document layer); {@code null} for missing or table values.
     * td 标量的规范字符串渲染（与规则文档层同契约）；缺失或表值返回 {@code null}。
     */
    private static String scalarString(TdValue v) {
        return v == null || v instanceof TdTable ? null : v.toString();
    }
}
