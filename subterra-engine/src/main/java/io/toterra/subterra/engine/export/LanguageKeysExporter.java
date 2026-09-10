package io.toterra.subterra.engine.export;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.zd.ZdVolume;

import java.util.Map;
import java.util.TreeMap;

/**
 * p.2.18.2 语言键导出器 —— 把 {@code Map<String,String>} 语言键表确定性地产出为规范 td 文档
 * （{@link #exportTd}）及其 zd v2 二进制变体（{@link #exportZd}），并提供反向回水化
 * （{@link #rehydrate} / {@link #rehydrateZd}）。文档形态（纯 JDK，无 JSON）：
 *
 * <pre>{@code
 * type tie<data>
 * lang = [
 *   version = 1,
 *   count = N,
 *   entries = [
 *     [ key = "a", text = "b" ],
 *     ...
 *   ],
 * ]
 * }</pre>
 *
 * <p>{@code entries} 按 key 字典序（TreeMap 迭代序）排序；字段名与顺序固定（version / count /
 * entries，条目内 key / text）；禁时间戳、禁随机——同输入恒同字节。回水化把 td 解析回
 * {@code TreeMap}（保持 key 字典序）；重复 key 首现胜出；文本转义（引号 / 反斜杠 / 换行 /
 * 制表 / 回车）经 {@link Td#parse} 天然往返。回水化同时接受命名顶层形态（{@code lang = [...]}，
 * 表名被 {@link Td#parse} 剥离）与裸根表 + {@code lang} 条目形态（p.2.18.1 归档惯例）。恒等
 * 契约：{@code exportTd(rehydrate(exportTd(keys)))} 逐字节等于 {@code exportTd(keys)}
 * （export∘rehydrate∘export），zd 变体同理（{@code Arrays.equals}）。输入防御：null key / null
 * text 与空白 key 以 {@link IllegalArgumentException} 拒绝。本实现为 clean-room 自研，仅借鉴
 * Export-Language-Keys-for-Compasses（MIT）「全量导出语言键」这一功能方向，不抄代码不抄资产。
 *
 * <p>p.2.18.2 language-keys exporter — deterministically renders a
 * {@code Map<String,String>} language-key table as a canonical td document
 * ({@link #exportTd}) and its zd v2 binary variant ({@link #exportZd}), with the
 * reverse rehydrate ({@link #rehydrate} / {@link #rehydrateZd}). Document shape
 * (pure JDK, no JSON):
 *
 * <pre>{@code
 * type tie<data>
 * lang = [
 *   version = 1,
 *   count = N,
 *   entries = [
 *     [ key = "a", text = "b" ],
 *     ...
 *   ],
 * ]
 * }</pre>
 *
 * <p>{@code entries} are sorted by key (TreeMap iteration order); field names and
 * order are fixed (version / count / entries; per-entry key / text); no timestamps,
 * no randomness — same input yields the same bytes. Rehydrate parses back into a
 * {@code TreeMap} (key order preserved); duplicate keys: first occurrence wins; text
 * escapes (quotes / backslash / newline / tab / CR) round-trip losslessly via
 * {@link Td#parse}. Rehydrate accepts both the named top-level form
 * ({@code lang = [...]}, the name stripped by {@link Td#parse}) and the bare-root +
 * {@code lang} entry form (the p.2.18.1 archive convention). Identity contract:
 * {@code exportTd(rehydrate(exportTd(keys)))} is byte-identical to
 * {@code exportTd(keys)} (export∘rehydrate∘export); the zd variant likewise under
 * {@code Arrays.equals}. Input defence: null keys / null texts and blank keys are
 * rejected with {@link IllegalArgumentException}. Clean-room implementation; only
 * the "full language-key export" direction is inspired by
 * Export-Language-Keys-for-Compasses (MIT) — no code, no assets copied.
 */
public final class LanguageKeysExporter {

    /** 当前文档格式版本。Current document format version. */
    public static final long VERSION = 1;

    /** 顶层表名标记（{@code lang = [...]}；{@link Td#parse} 会剥离可选表名）。 */
    private static final String MARKER = "lang";

    private LanguageKeysExporter() {
    }

    /**
     * 把语言键表产出为规范 td 文档文本（entries 按 key 字典序；字段名与顺序固定；确定性——
     * 同输入同字节）。null key / null text / 空白 key 以 {@link IllegalArgumentException} 拒绝。
     *
     * Renders the language-key table as canonical td document text (entries sorted by
     * key; fixed field names and order; deterministic — same input, same bytes).
     * Null keys / null texts / blank keys are rejected with
     * {@link IllegalArgumentException}.
     */
    public static String exportTd(Map<String, String> keys) {
        return "type tie<data>\nlang = " + Td.write(docOf(keys));
    }

    /**
     * 从 td 文档解析回 {@code TreeMap}（保持 key 字典序；重复 key 首现胜出；文本转义经
     * {@link Td#parse} 天然往返）。非语言键文档 / 版本不符 / 条目缺失 key 或空白 key 以
     * {@link IllegalArgumentException} 拒绝。
     *
     * Parses a td document back into a {@code TreeMap} (key order preserved; duplicate
     * keys: first occurrence wins; text escapes round-trip via {@link Td#parse}).
     * A non-language-keys document / version mismatch / entry with a missing or blank
     * key is rejected with {@link IllegalArgumentException}.
     */
    public static Map<String, String> rehydrate(String td) {
        return rehydrateTable(Td.parse(td));
    }

    /**
     * 同一内容的 zd v2 二进制变体（复用 p.2.18.1 的 zd 转换路径：
     * {@code ZdDocWriter.writeTree(0, Td.parse(exportTd(keys)))}），逐字节确定。
     *
     * The zd v2 binary variant of the same content (reusing the p.2.18.1 zd
     * conversion path: {@code ZdDocWriter.writeTree(0, Td.parse(exportTd(keys)))}),
     * byte-deterministic.
     */
    public static byte[] exportZd(Map<String, String> keys) {
        return ExportHub.zdOf(exportTd(keys));
    }

    /**
     * 从 zd v2 载荷解析回 {@code TreeMap}（zd → td 树 → 回水化），与 {@link #rehydrate} 同语义。
     *
     * Parses a zd v2 payload back into a {@code TreeMap} (zd → td tree → rehydrate),
     * same semantics as {@link #rehydrate}.
     */
    public static Map<String, String> rehydrateZd(byte[] zd) {
        return rehydrateTable(ZdVolume.readTree(zd));
    }

    /** Builds the {@code lang} document table from the key map (key-sorted). */
    private static TdTable docOf(Map<String, String> keys) {
        if (keys == null) {
            throw new IllegalArgumentException("language keys must be non-null");
        }
        TdTable.Builder entriesB = TdTable.builder();
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> e : keys.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if (k == null) {
                throw new IllegalArgumentException("language key must be non-null");
            }
            if (v == null) {
                throw new IllegalArgumentException("language text must be non-null for key: " + k);
            }
            if (k.isBlank()) {
                throw new IllegalArgumentException("language key must be non-blank");
            }
            sorted.put(k, v);
        }
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            entriesB.element(TdTable.builder()
                    .put("key", TdValue.str(e.getKey()))
                    .put("text", TdValue.str(e.getValue()))
                    .build());
        }
        return TdTable.builder()
                .put("version", TdValue.of(VERSION))
                .put("count", TdValue.of((long) sorted.size()))
                .put("entries", entriesB.build())
                .build();
    }

    /** Shared rehydrate core over a parsed root table. */
    private static Map<String, String> rehydrateTable(TdTable root) {
        TdValue markerValue = root.get(MARKER);
        TdTable doc = markerValue instanceof TdTable t
                ? t   // bare-root + `lang` entry form
                : root; // named top-level `lang = [...]` form (name stripped by Td.parse)
        long version = doc.get("version") != null ? doc.get("version").asInt() : -1;
        if (version != VERSION) {
            throw new IllegalArgumentException("unsupported language-keys document version: " + version);
        }
        TdValue entriesValue = doc.get("entries");
        if (!(entriesValue instanceof TdTable entries)) {
            throw new IllegalArgumentException("language-keys document missing 'entries' table");
        }
        Map<String, String> map = new TreeMap<>();
        for (TdValue item : entries.elements()) {
            if (!(item instanceof TdTable t)) {
                throw new IllegalArgumentException("language-keys entry is not a table");
            }
            String k = t.get("key") != null ? t.get("key").asString() : "";
            if (k.isBlank()) {
                throw new IllegalArgumentException("language-keys entry has a blank or missing key");
            }
            String v = t.get("text") != null ? t.get("text").asString() : "";
            map.putIfAbsent(k, v); // duplicate key: first occurrence wins
        }
        return map;
    }
}
