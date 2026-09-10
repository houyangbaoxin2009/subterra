package io.toterra.subterra.engine.export;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.zd.ZdVolume;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * p.2.18.4 注册表导出器 —— 把注册表快照（{@code Map<String,List<String>>}，注册表名 → 条目 id
 * 列表）确定性地产出为规范 td 文档（{@link #exportTd}）及其 zd v2 二进制变体（{@link #exportZd}），
 * 并提供反向回水化（{@link #rehydrate} / {@link #rehydrateZd}，返回同名 {@code Map<String,List<String>>}）。
 * 文档形态（纯 JDK，无 JSON）：
 *
 * <pre>{@code
 * type tie<data>
 * registries = [
 *   version = 1,
 *   count = N,
 *   entries = [
 *     [ name = "mob", ids = ["zombie", "skeleton"] ],
 *     [ name = "item", ids = ["stick"] ],
 *     ...
 *   ],
 * ]
 * }</pre>
 *
 * <p>{@code entries} 按注册表名字典序（TreeMap 迭代序）排序；每个 {@code ids} 保持输入序（不去重、
 * 不再排序，文档化的确定性选择）；字段名与顺序固定（version / count / entries，条目内 name / ids）；
 * 禁时间戳、禁随机——同输入恒同字节。回水化把 td 解析回 {@code TreeMap}（保持注册表名字典序，每个
 * {@code ids} 保持文档序即输入序）；重复注册表名首现胜出；文本转义（引号 / 反斜杠 / 换行 / 制表 /
 * 回车）经 {@link Td#parse} 天然往返。回水化同时接受命名顶层形态（{@code registries = [...]}，表名
 * 被 {@link Td#parse} 剥离）与裸根表 + {@code registries} 条目形态（p.2.18.1 归档惯例）。恒等契约：
 * {@code exportTd(rehydrate(exportTd(regs)))} 逐字节等于 {@code exportTd(regs)}
 * （export∘rehydrate∘export），zd 变体同理（{@code Arrays.equals}）。输入防御：null 注册表 / null
 * 注册表名 / 空白注册表名 / null id 列表 / null id / 空白 id 以 {@link IllegalArgumentException}
 * 拒绝；空 id 列表合法（允许空注册表）。复杂度 O(n log n)（TreeMap 排序），禁 O(n²)。
 *
 * <p>p.2.18.4 registries exporter — deterministically renders a registries snapshot
 * ({@code Map<String,List<String>>}, registry name → entry-id list) as a canonical td document
 * ({@link #exportTd}) and its zd v2 binary variant ({@link #exportZd}), with the reverse
 * rehydrate ({@link #rehydrate} / {@link #rehydrateZd}, returning the same
 * {@code Map<String,List<String>>}). Document shape (pure JDK, no JSON):
 *
 * <pre>{@code
 * type tie<data>
 * registries = [
 *   version = 1,
 *   count = N,
 *   entries = [
 *     [ name = "mob", ids = ["zombie", "skeleton"] ],
 *     [ name = "item", ids = ["stick"] ],
 *     ...
 *   ],
 * ]
 * }</pre>
 *
 * <p>{@code entries} are sorted by registry name (TreeMap iteration order); each {@code ids} keeps
 * its input order (no de-duplication, no re-sort — the documented determinism choice); field names
 * and order are fixed (version / count / entries; per-entry name / ids); no timestamps, no
 * randomness — same input yields the same bytes. Rehydrate parses back into a {@code TreeMap}
 * (registry names sorted; each {@code ids} in document order, i.e. input order); duplicate
 * registry names: first occurrence wins; text escapes (quotes / backslash / newline / tab / CR)
 * round-trip losslessly via {@link Td#parse}. Rehydrate accepts both the named top-level form
 * ({@code registries = [...]}, the name stripped by {@link Td#parse}) and the bare-root +
 * {@code registries} entry form (the p.2.18.1 archive convention). Identity contract:
 * {@code exportTd(rehydrate(exportTd(regs)))} is byte-identical to {@code exportTd(regs)}
 * (export∘rehydrate∘export); the zd variant likewise under {@code Arrays.equals}. Input defence: a
 * null registries map / null registry name / blank registry name / null ids list / null id /
 * blank id is rejected with {@link IllegalArgumentException}; an empty ids list is legal (an empty
 * registry is allowed). O(n log n) via TreeMap sorting; no O(n²).
 */
public final class RegistriesExporter {

    /** 当前文档格式版本。Current document format version. */
    public static final long VERSION = 1;

    /** 顶层表名标记（{@code registries = [...]}；{@link Td#parse} 会剥离可选表名）。 */
    private static final String MARKER = "registries";

    private RegistriesExporter() {
    }

    /**
     * 把注册表快照产出为规范 td 文档文本（entries 按注册表名字典序、ids 保持输入序；字段名与顺序
     * 固定；确定性——同输入同字节）。null 注册表 / null 或空白注册表名 / null id 列表 / null 或空白
     * id 以 {@link IllegalArgumentException} 拒绝。
     *
     * Renders the registries snapshot as canonical td document text (entries sorted by registry
     * name, ids in input order; fixed field names and order; deterministic — same input, same
     * bytes). A null registries map / null or blank registry name / null ids list / null or blank
     * id is rejected with {@link IllegalArgumentException}.
     */
    public static String exportTd(Map<String, List<String>> registries) {
        return "type tie<data>\nregistries = " + Td.write(docOf(registries));
    }

    /**
     * 同一内容的 zd v2 二进制变体（复用 p.2.18.1 的 zd 转换路径：
     * {@code ExportHub.zdOf(exportTd(registries))}），逐字节确定。
     *
     * The zd v2 binary variant of the same content (reusing the p.2.18.1 zd conversion path:
     * {@code ExportHub.zdOf(exportTd(registries))}), byte-deterministic.
     */
    public static byte[] exportZd(Map<String, List<String>> registries) {
        return ExportHub.zdOf(exportTd(registries));
    }

    /**
     * 从 td 文档解析回注册表快照（{@code TreeMap}，注册表名字典序；每个 {@code ids} 保持文档序即
     * 输入序；重复注册表名首现胜出；文本转义经 {@link Td#parse} 天然往返）。非注册表文档 / 版本不符 /
     * 条目缺失 name 或空白 name / ids 缺失或条目非字符串 以 {@link IllegalArgumentException} 拒绝。
     *
     * Parses a td document back into the registries snapshot (a {@code TreeMap}, registry names
     * sorted; each {@code ids} in document order, i.e. input order; duplicate registry names:
     * first occurrence wins; text escapes round-trip via {@link Td#parse}). A non-registries
     * document / version mismatch / entry with a missing or blank name / missing ids table or a
     * non-string id is rejected with {@link IllegalArgumentException}.
     */
    public static Map<String, List<String>> rehydrate(String td) {
        return rehydrateTable(Td.parse(td));
    }

    /**
     * 从 zd v2 载荷解析回注册表快照（zd → td 树 → 回水化），与 {@link #rehydrate} 同语义。
     *
     * Parses a zd v2 payload back into the registries snapshot (zd → td tree → rehydrate), same
     * semantics as {@link #rehydrate}.
     */
    public static Map<String, List<String>> rehydrateZd(byte[] zd) {
        return rehydrateTable(ZdVolume.readTree(zd));
    }

    /**
     * 校验并字典序快照注册表表（null 注册表 / null 或空白注册表名 / null id 列表 / null 或空白 id
     * 一律 {@link IllegalArgumentException}；ids 保持输入序）。包内共享：导出路径与
     * {@code RegistriesProducer} 构造都经此防御拷贝。
     *
     * Validates and snapshots the registries map in lexicographic order (a null registries map /
     * null or blank registry name / null ids list / null or blank id raises
     * {@link IllegalArgumentException}; ids keep their input order). Shared package-wide: both the
     * export path and the {@code RegistriesProducer} constructor defensively copy through here.
     */
    static Map<String, List<String>> snapshot(Map<String, List<String>> registries, String what) {
        if (registries == null) {
            throw new IllegalArgumentException(what + " must be non-null");
        }
        TreeMap<String, List<String>> sorted = new TreeMap<>();
        for (Map.Entry<String, List<String>> e : registries.entrySet()) {
            String name = e.getKey();
            List<String> ids = e.getValue();
            if (name == null) {
                throw new IllegalArgumentException(what + " name must be non-null");
            }
            if (name.isBlank()) {
                throw new IllegalArgumentException(what + " name must be non-blank");
            }
            if (ids == null) {
                throw new IllegalArgumentException(what + " ids must be non-null for registry: " + name);
            }
            List<String> copy = new ArrayList<>(ids.size());
            for (String id : ids) {
                if (id == null) {
                    throw new IllegalArgumentException(what + " id must be non-null for registry: " + name);
                }
                if (id.isBlank()) {
                    throw new IllegalArgumentException(what + " id must be non-blank for registry: " + name);
                }
                copy.add(id);
            }
            sorted.put(name, copy);
        }
        return sorted;
    }

    /** Builds the {@code registries} document table (registry names sorted, ids in input order). */
    private static TdTable docOf(Map<String, List<String>> registries) {
        Map<String, List<String>> sorted = snapshot(registries, "registries");
        TdTable.Builder entriesB = TdTable.builder();
        for (Map.Entry<String, List<String>> e : sorted.entrySet()) {
            TdTable.Builder idsB = TdTable.builder();
            for (String id : e.getValue()) {
                idsB.element(TdValue.str(id));
            }
            entriesB.element(TdTable.builder()
                    .put("name", TdValue.str(e.getKey()))
                    .put("ids", idsB.build())
                    .build());
        }
        return TdTable.builder()
                .put("version", TdValue.of(VERSION))
                .put("count", TdValue.of((long) sorted.size()))
                .put("entries", entriesB.build())
                .build();
    }

    /** Shared rehydrate core over a parsed root table. */
    private static Map<String, List<String>> rehydrateTable(TdTable root) {
        TdValue markerValue = root.get(MARKER);
        TdTable doc = markerValue instanceof TdTable t
                ? t   // bare-root + `registries` entry form
                : root; // named top-level `registries = [...]` form (name stripped by Td.parse)
        long version = doc.get("version") != null ? doc.get("version").asInt() : -1;
        if (version != VERSION) {
            throw new IllegalArgumentException("unsupported registries document version: " + version);
        }
        TdValue entriesValue = doc.get("entries");
        if (!(entriesValue instanceof TdTable entries)) {
            throw new IllegalArgumentException("registries document missing 'entries' table");
        }
        Map<String, List<String>> map = new TreeMap<>();
        for (TdValue item : entries.elements()) {
            if (!(item instanceof TdTable t)) {
                throw new IllegalArgumentException("registries entry is not a table");
            }
            String name = t.get("name") != null ? t.get("name").asString() : "";
            if (name.isBlank()) {
                throw new IllegalArgumentException("registries entry has a blank or missing name");
            }
            TdValue idsValue = t.get("ids");
            if (!(idsValue instanceof TdTable idsTable)) {
                throw new IllegalArgumentException("registries entry '" + name + "' missing 'ids' table");
            }
            List<String> ids = new ArrayList<>(idsTable.elements().size());
            for (TdValue idValue : idsTable.elements()) {
                String id = idValue.asString();
                if (id.isBlank()) {
                    throw new IllegalArgumentException("registries entry '" + name + "' has a blank or non-string id");
                }
                ids.add(id);
            }
            map.putIfAbsent(name, ids); // duplicate registry name: first occurrence wins
        }
        return map;
    }
}
