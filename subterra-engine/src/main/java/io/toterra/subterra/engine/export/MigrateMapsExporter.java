package io.toterra.subterra.engine.export;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.zd.ZdVolume;

import java.util.Map;
import java.util.TreeMap;

/**
 * p.2.18.4 迁移映射导出器 —— 把迁移映射表（{@code Map<String,String>}，源键 → 目标键；本框架
 * p.2.3.5 迁移管线的通用映射形态，模块解耦——接入方自行把迁移器/数据流的键映射组装成该表传入即可）
 * 确定性地产出为规范 td 文档（{@link #exportTd}）及其 zd v2 二进制变体（{@link #exportZd}），并
 * 提供反向回水化（{@link #rehydrate} / {@link #rehydrateZd}，返回同名 {@code Map<String,String>}）。
 * 文档形态（纯 JDK，无 JSON）：
 *
 * <pre>{@code
 * type tie<data>
 * migrate_maps = [
 *   version = 1,
 *   count = N,
 *   entries = [
 *     [ from = "a", to = "b" ],
 *     [ from = "c", to = "c" ],
 *     ...
 *   ],
 * ]
 * }</pre>
 *
 * <p>{@code entries} 按 {@code from} 键字典序（TreeMap 迭代序）排序；字段名与顺序固定
 * （version / count / entries，条目内 from / to）；禁时间戳、禁随机——同输入恒同字节。回水化把
 * td 解析回 {@code TreeMap}（保持 {@code from} 键字典序）；重复 {@code from} 首现胜出；文本转义
 * （引号 / 反斜杠 / 换行 / 制表 / 回车）经 {@link Td#parse} 天然往返。回水化同时接受命名顶层形态
 * （{@code migrate_maps = [...]}，表名被 {@link Td#parse} 剥离）与裸根表 + {@code migrate_maps}
 * 条目形态（p.2.18.1 归档惯例）。恒等契约：{@code exportTd(rehydrate(exportTd(map)))} 逐字节等于
 * {@code exportTd(map)}（export∘rehydrate∘export），zd 变体同理（{@code Arrays.equals}）。输入
 * 防御：null 映射 / null {@code from} 键 / 空白 {@code from} 键 / null {@code to} 值以
 * {@link IllegalArgumentException} 拒绝；空白 {@code to} 合法（允许「映射到空 = 删除」语义）。
 * 复杂度 O(n log n)（TreeMap 排序），禁 O(n²)。
 *
 * <p>p.2.18.4 migrate-maps exporter — deterministically renders a migration-map table
 * ({@code Map<String,String>}, source key → target key; the generic mapping shape of the
 * framework's p.2.3.5 migration pipeline, module-decoupled — wire any migrator/data-stream key map
 * into this table) as a canonical td document ({@link #exportTd}) and its zd v2 binary variant
 * ({@link #exportZd}), with the reverse rehydrate ({@link #rehydrate} / {@link #rehydrateZd},
 * returning the same {@code Map<String,String>}). Document shape (pure JDK, no JSON):
 *
 * <pre>{@code
 * type tie<data>
 * migrate_maps = [
 *   version = 1,
 *   count = N,
 *   entries = [
 *     [ from = "a", to = "b" ],
 *     [ from = "c", to = "c" ],
 *     ...
 *   ],
 * ]
 * }</pre>
 *
 * <p>{@code entries} are sorted by {@code from} key (TreeMap iteration order); field names and
 * order are fixed (version / count / entries; per-entry from / to); no timestamps, no randomness —
 * same input yields the same bytes. Rehydrate parses back into a {@code TreeMap} (from-key order
 * preserved); duplicate {@code from} keys: first occurrence wins; text escapes (quotes / backslash
 * / newline / tab / CR) round-trip losslessly via {@link Td#parse}. Rehydrate accepts both the
 * named top-level form ({@code migrate_maps = [...]}, the name stripped by {@link Td#parse}) and
 * the bare-root + {@code migrate_maps} entry form (the p.2.18.1 archive convention). Identity
 * contract: {@code exportTd(rehydrate(exportTd(map)))} is byte-identical to {@code exportTd(map)}
 * (export∘rehydrate∘export); the zd variant likewise under {@code Arrays.equals}. Input defence: a
 * null map / null {@code from} key / blank {@code from} key / null {@code to} value is rejected
 * with {@link IllegalArgumentException}; a blank {@code to} is legal (allowing the
 * "map-to-empty = deletion" semantics). O(n log n) via TreeMap sorting; no O(n²).
 */
public final class MigrateMapsExporter {

    /** 当前文档格式版本。Current document format version. */
    public static final long VERSION = 1;

    /** 顶层表名标记（{@code migrate_maps = [...]}；{@link Td#parse} 会剥离可选表名）。 */
    private static final String MARKER = "migrate_maps";

    private MigrateMapsExporter() {
    }

    /**
     * 把迁移映射表产出为规范 td 文档文本（entries 按 {@code from} 键字典序；字段名与顺序固定；
     * 确定性——同输入同字节）。null 映射 / null {@code from} 键 / 空白 {@code from} 键 / null
     * {@code to} 值以 {@link IllegalArgumentException} 拒绝。
     *
     * Renders the migration-map table as canonical td document text (entries sorted by
     * {@code from} key; fixed field names and order; deterministic — same input, same bytes).
     * A null map / null {@code from} key / blank {@code from} key / null {@code to} value is
     * rejected with {@link IllegalArgumentException}.
     */
    public static String exportTd(Map<String, String> migrationMap) {
        return "type tie<data>\nmigrate_maps = " + Td.write(docOf(migrationMap));
    }

    /**
     * 同一内容的 zd v2 二进制变体（复用 p.2.18.1 的 zd 转换路径：
     * {@code ExportHub.zdOf(exportTd(migrationMap))}），逐字节确定。
     *
     * The zd v2 binary variant of the same content (reusing the p.2.18.1 zd conversion path:
     * {@code ExportHub.zdOf(exportTd(migrationMap))}), byte-deterministic.
     */
    public static byte[] exportZd(Map<String, String> migrationMap) {
        return ExportHub.zdOf(exportTd(migrationMap));
    }

    /**
     * 从 td 文档解析回迁移映射表（{@code TreeMap}，保持 {@code from} 键字典序；重复 {@code from}
     * 首现胜出；文本转义经 {@link Td#parse} 天然往返）。非迁移映射文档 / 版本不符 / 条目缺失
     * {@code from} 或空白 {@code from} 以 {@link IllegalArgumentException} 拒绝。
     *
     * Parses a td document back into the migration-map table (a {@code TreeMap}, from-key order
     * preserved; duplicate {@code from} keys: first occurrence wins; text escapes round-trip via
     * {@link Td#parse}). A non-migrate-maps document / version mismatch / entry with a missing or
     * blank {@code from} is rejected with {@link IllegalArgumentException}.
     */
    public static Map<String, String> rehydrate(String td) {
        return rehydrateTable(Td.parse(td));
    }

    /**
     * 从 zd v2 载荷解析回迁移映射表（zd → td 树 → 回水化），与 {@link #rehydrate} 同语义。
     *
     * Parses a zd v2 payload back into the migration-map table (zd → td tree → rehydrate), same
     * semantics as {@link #rehydrate}.
     */
    public static Map<String, String> rehydrateZd(byte[] zd) {
        return rehydrateTable(ZdVolume.readTree(zd));
    }

    /**
     * 校验并字典序快照迁移映射表（null 映射 / null {@code from} 键 / 空白 {@code from} 键 / null
     * {@code to} 值一律 {@link IllegalArgumentException}）。包内共享：导出路径与
     * {@code MigrateMapsProducer} 构造都经此防御拷贝。
     *
     * Validates and snapshots the migration map in lexicographic order (a null map / null
     * {@code from} key / blank {@code from} key / null {@code to} value raises
     * {@link IllegalArgumentException}). Shared package-wide: both the export path and the
     * {@code MigrateMapsProducer} constructor defensively copy through here.
     */
    static TreeMap<String, String> snapshot(Map<String, String> migrationMap, String what) {
        if (migrationMap == null) {
            throw new IllegalArgumentException(what + " must be non-null");
        }
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> e : migrationMap.entrySet()) {
            String from = e.getKey();
            String to = e.getValue();
            if (from == null) {
                throw new IllegalArgumentException(what + " from key must be non-null");
            }
            if (from.isBlank()) {
                throw new IllegalArgumentException(what + " from key must be non-blank");
            }
            if (to == null) {
                throw new IllegalArgumentException(what + " to value must be non-null for key: " + from);
            }
            sorted.put(from, to);
        }
        return sorted;
    }

    /** Builds the {@code migrate_maps} document table (from-key sorted). */
    private static TdTable docOf(Map<String, String> migrationMap) {
        Map<String, String> sorted = snapshot(migrationMap, "migrate_maps");
        TdTable.Builder entriesB = TdTable.builder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            entriesB.element(TdTable.builder()
                    .put("from", TdValue.str(e.getKey()))
                    .put("to", TdValue.str(e.getValue()))
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
                ? t   // bare-root + `migrate_maps` entry form
                : root; // named top-level `migrate_maps = [...]` form (name stripped by Td.parse)
        long version = doc.get("version") != null ? doc.get("version").asInt() : -1;
        if (version != VERSION) {
            throw new IllegalArgumentException("unsupported migrate-maps document version: " + version);
        }
        TdValue entriesValue = doc.get("entries");
        if (!(entriesValue instanceof TdTable entries)) {
            throw new IllegalArgumentException("migrate-maps document missing 'entries' table");
        }
        Map<String, String> map = new TreeMap<>();
        for (TdValue item : entries.elements()) {
            if (!(item instanceof TdTable t)) {
                throw new IllegalArgumentException("migrate-maps entry is not a table");
            }
            String from = t.get("from") != null ? t.get("from").asString() : "";
            if (from.isBlank()) {
                throw new IllegalArgumentException("migrate-maps entry has a blank or missing from key");
            }
            String to = t.get("to") != null ? t.get("to").asString() : "";
            map.putIfAbsent(from, to); // duplicate from key: first occurrence wins
        }
        return map;
    }
}
