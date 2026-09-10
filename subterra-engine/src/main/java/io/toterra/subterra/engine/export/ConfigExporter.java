package io.toterra.subterra.engine.export;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.zd.ZdVolume;

import java.util.Map;
import java.util.TreeMap;

/**
 * p.2.18.3 配置导出器 —— 把双层配置（全局档 {@code global} + 存档覆盖档 {@code overrides}，均为
 * {@code Map<String,String>} 字符串键值；与 {@code engine.config.rules.RuleStore} 的双层形态一致，
 * 此处以两层 Map 解耦规则系统，接入方传 {@code store.global()}/{@code store.overrides()} 即可）确定
 * 性地产出为规范 td 文档（{@link #exportTd}）及其 zd v2 二进制变体（{@link #exportZd}），并提供反向
 * 回水化（{@link #rehydrate} / {@link #rehydrateZd}，返回两层 {@link ConfigLayers}）。文档形态
 * （纯 JDK，无 JSON）：
 *
 * <pre>{@code
 * type tie<data>
 * config = [
 *   version = 1,
 *   global = [
 *     [ k = "a", v = "1" ],
 *     [ k = "b", v = "2" ],
 *   ],
 *   overrides = [
 *     [ k = "b", v = "9" ],
 *   ],
 *   effective = [
 *     [ k = "a", v = "1" ],
 *     [ k = "b", v = "9" ],
 *   ],
 * ]
 * }</pre>
 *
 * <p>三个块 {@code global} / {@code overrides} / {@code effective} 各自按 key 字典序（TreeMap 迭代
 * 序）；字段名与顺序固定（version / global / overrides / effective，条目内 k / v）；禁时间戳、禁随机
 * ——同输入恒同字节。{@code effective} 为解析视图：先并入 global、再 putAll(overrides)（存档覆盖档对
 * 冲突 key 全部胜出，语义 = {@code RuleStore.resolve()}），回水化时忽略该块、按 global+overrides 重
 * 算（denormalized 视图，供人读与工具对照）。回水化把文档解析回两个 {@code TreeMap}（保持 key 字典
 * 序）；块内重复 key 首现胜出；文本转义（引号 / 反斜杠 / 换行 / 制表 / 回车）经 {@link Td#parse} 天然
 * 往返。回水化同时接受命名顶层形态（{@code config = [...]}，表名被 {@link Td#parse} 剥离）与裸根表 +
 * {@code config} 条目形态（p.2.18.1 归档惯例）。恒等契约：
 * {@code exportTd(rehydrate(exportTd(g, o)))} 逐字节等于 {@code exportTd(g, o)}
 * （export∘rehydrate∘export；{@code rehydrate} 返回的 {@link ConfigLayers} 由 {@link #exportTd(ConfigLayers)}
 * 直接再导出），zd 变体同理（{@code Arrays.equals}）。输入防御：null 层 / null key / 空白 key / null
 * value 以 {@link IllegalArgumentException} 拒绝。复杂度 O(n log n)（TreeMap 排序），禁 O(n²)。
 *
 * <p>p.2.18.3 config exporter — deterministically renders the two-tier configuration (a global
 * layer {@code global} + a save-overrides layer {@code overrides}, both {@code Map<String,String>}
 * string key/value pairs; the same two-tier shape as {@code engine.config.rules.RuleStore}, here
 * decoupled from the rule system by taking the two maps — wire it with
 * {@code store.global()}/{@code store.overrides()}) as a canonical td document
 * ({@link #exportTd}) and its zd v2 binary variant ({@link #exportZd}), with the reverse rehydrate
 * ({@link #rehydrate} / {@link #rehydrateZd}, returning the two layers as {@link ConfigLayers}).
 * Document shape (pure JDK, no JSON):
 *
 * <pre>{@code
 * type tie<data>
 * config = [
 *   version = 1,
 *   global = [
 *     [ k = "a", v = "1" ],
 *     [ k = "b", v = "2" ],
 *   ],
 *   overrides = [
 *     [ k = "b", v = "9" ],
 *   ],
 *   effective = [
 *     [ k = "a", v = "1" ],
 *     [ k = "b", v = "9" ],
 *   ],
 * ]
 * }</pre>
 *
 * <p>The three blocks {@code global} / {@code overrides} / {@code effective} are each sorted by
 * key (TreeMap iteration order); field names and order are fixed (version / global / overrides /
 * effective; per-entry k / v); no timestamps, no randomness — same input yields the same bytes.
 * {@code effective} is a derived view: global merged first, then putAll(overrides) (the
 * save-overrides layer wins every conflicting key; semantics = {@code RuleStore.resolve()}); the
 * rehydrate ignores this block and recomputes it from global+overrides (a denormalized view for
 * humans and cross-checking tools). Rehydrate parses back into two {@code TreeMap}s (key order
 * preserved); duplicate keys within a block: first occurrence wins; text escapes (quotes /
 * backslash / newline / tab / CR) round-trip losslessly via {@link Td#parse}. Rehydrate accepts
 * both the named top-level form ({@code config = [...]}, the name stripped by {@link Td#parse})
 * and the bare-root + {@code config} entry form (the p.2.18.1 archive convention). Identity
 * contract: {@code exportTd(rehydrate(exportTd(g, o)))} is byte-identical to
 * {@code exportTd(g, o)} (export∘rehydrate∘export; the {@link ConfigLayers} returned by
 * {@link #rehydrate} is re-exported directly via {@link #exportTd(ConfigLayers)}); the zd variant
 * likewise under {@code Arrays.equals}. Input defence: a null layer / null key / blank key / null
 * value is rejected with {@link IllegalArgumentException}. O(n log n) via TreeMap sorting; no
 * O(n²).
 */
public final class ConfigExporter {

    /** 当前文档格式版本。Current document format version. */
    public static final long VERSION = 1;

    /** 顶层表名标记（{@code config = [...]}；{@link Td#parse} 会剥离可选表名）。 */
    private static final String MARKER = "config";

    private ConfigExporter() {
    }

    /**
     * 把双层配置产出为规范 td 文档文本（三个块各自按 key 字典序；字段名与顺序固定；确定性——同输入
     * 同字节）。null 层 / null key / 空白 key / null value 以 {@link IllegalArgumentException} 拒绝。
     *
     * Renders the two-tier configuration as canonical td document text (each block sorted by key;
     * fixed field names and order; deterministic — same input, same bytes). A null layer / null
     * key / blank key / null value is rejected with {@link IllegalArgumentException}.
     */
    public static String exportTd(Map<String, String> global, Map<String, String> overrides) {
        return "type tie<data>\nconfig = " + Td.write(docOf(global, overrides));
    }

    /**
     * {@link #exportTd(Map, Map)} 的 {@link ConfigLayers} 便捷重载（恒等契约的再导出路径）。
     * A {@link ConfigLayers} convenience overload of {@link #exportTd(Map, Map)} (the re-export
     * path of the identity contract).
     */
    public static String exportTd(ConfigLayers layers) {
        if (layers == null) {
            throw new IllegalArgumentException("config layers must be non-null");
        }
        return exportTd(layers.global(), layers.overrides());
    }

    /**
     * 同一内容的 zd v2 二进制变体（复用 p.2.18.1 的 zd 转换路径：
     * {@code ExportHub.zdOf(exportTd(global, overrides))}），逐字节确定。
     *
     * The zd v2 binary variant of the same content (reusing the p.2.18.1 zd conversion path:
     * {@code ExportHub.zdOf(exportTd(global, overrides))}), byte-deterministic.
     */
    public static byte[] exportZd(Map<String, String> global, Map<String, String> overrides) {
        return ExportHub.zdOf(exportTd(global, overrides));
    }

    /**
     * {@link #exportZd(Map, Map)} 的 {@link ConfigLayers} 便捷重载。
     * A {@link ConfigLayers} convenience overload of {@link #exportZd(Map, Map)}.
     */
    public static byte[] exportZd(ConfigLayers layers) {
        if (layers == null) {
            throw new IllegalArgumentException("config layers must be non-null");
        }
        return exportZd(layers.global(), layers.overrides());
    }

    /**
     * 从 td 文档解析回两层 {@link ConfigLayers}（各层 {@code TreeMap}，保持 key 字典序；块内重复 key
     * 首现胜出；文本转义经 {@link Td#parse} 天然往返）。非配置文档 / 版本不符 / 缺失块 / 条目缺失 key
     * 或空白 key 以 {@link IllegalArgumentException} 拒绝。
     *
     * Parses a td document back into the two layers as {@link ConfigLayers} (each layer a
     * {@code TreeMap}, key order preserved; duplicate keys within a block: first occurrence wins;
     * text escapes round-trip via {@link Td#parse}). A non-config document / version mismatch /
     * missing block / entry with a missing or blank key is rejected with
     * {@link IllegalArgumentException}.
     */
    public static ConfigLayers rehydrate(String td) {
        return rehydrateRoot(Td.parse(td));
    }

    /**
     * 从 zd v2 载荷解析回两层 {@link ConfigLayers}（zd → td 树 → 回水化），与 {@link #rehydrate}
     * 同语义。
     *
     * Parses a zd v2 payload back into the two layers as {@link ConfigLayers} (zd → td tree →
     * rehydrate), same semantics as {@link #rehydrate}.
     */
    public static ConfigLayers rehydrateZd(byte[] zd) {
        return rehydrateRoot(ZdVolume.readTree(zd));
    }

    /**
     * 双层配置载体：两层只读视图（各层按 key 字典序），由 {@link #rehydrate} / {@link #rehydrateZd}
     * 产出、也可由调用方直接构造；null 层由 {@link #exportTd(ConfigLayers)} 入口拒绝。
     *
     * Two-tier configuration carrier: read-only views of the two layers (each sorted by key),
     * produced by {@link #rehydrate} / {@link #rehydrateZd} or built directly by callers; a null
     * layer is rejected at the {@link #exportTd(ConfigLayers)} entry.
     */
    public record ConfigLayers(Map<String, String> global, Map<String, String> overrides) {
    }

    /**
     * 校验并字典序快照一层（null 层 / null key / 空白 key / null value 一律
     * {@link IllegalArgumentException}）。包内共享：导出路径与 {@code ConfigProducer} 构造都经此
     * 防御拷贝。
     *
     * Validates and snapshots one layer in lexicographic order (a null layer / null key / blank
     * key / null value raises {@link IllegalArgumentException}). Shared package-wide: both the
     * export path and the {@code ConfigProducer} constructor defensively copy through here.
     */
    static TreeMap<String, String> snapshot(Map<String, String> layer, String what) {
        if (layer == null) {
            throw new IllegalArgumentException(what + " must be non-null");
        }
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> e : layer.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if (k == null) {
                throw new IllegalArgumentException(what + " key must be non-null");
            }
            if (k.isBlank()) {
                throw new IllegalArgumentException(what + " key must be non-blank");
            }
            if (v == null) {
                throw new IllegalArgumentException(what + " value must be non-null for key: " + k);
            }
            sorted.put(k, v);
        }
        return sorted;
    }

    /** Builds the {@code config} document table: global / overrides / effective, key-sorted. */
    private static TdTable docOf(Map<String, String> global, Map<String, String> overrides) {
        TreeMap<String, String> g = snapshot(global, "config global");
        TreeMap<String, String> o = snapshot(overrides, "config overrides");
        TreeMap<String, String> effective = new TreeMap<>(g);
        effective.putAll(o); // overrides win every conflicting key, aligned with RuleStore.resolve
        return TdTable.builder()
                .put("version", TdValue.of(VERSION))
                .put("global", rows(g))
                .put("overrides", rows(o))
                .put("effective", rows(effective))
                .build();
    }

    /** Writes a key-sorted layer as the {@code [ [ k = .., v = .. ], ... ]} row table. */
    private static TdTable rows(Map<String, String> sorted) {
        TdTable.Builder b = TdTable.builder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            b.element(TdTable.builder()
                    .put("k", e.getKey())
                    .put("v", e.getValue())
                    .build());
        }
        return b.build();
    }

    /** Shared rehydrate core over a parsed root table. */
    private static ConfigLayers rehydrateRoot(TdTable root) {
        TdValue markerValue = root.get(MARKER);
        TdTable doc = markerValue instanceof TdTable t
                ? t   // bare-root + `config` entry form
                : root; // named top-level `config = [...]` form (name stripped by Td.parse)
        long version = doc.get("version") != null ? doc.get("version").asInt() : -1;
        if (version != VERSION) {
            throw new IllegalArgumentException("unsupported config document version: " + version);
        }
        Map<String, String> global = readRows(doc.get("global"), "config global");
        Map<String, String> overrides = readRows(doc.get("overrides"), "config overrides");
        return new ConfigLayers(global, overrides);
    }

    /** Reads one {@code [ [ k = .., v = .. ], ... ]} block into a key-sorted map. */
    private static Map<String, String> readRows(TdValue block, String what) {
        if (!(block instanceof TdTable table)) {
            throw new IllegalArgumentException("config document missing '" + what + "' table");
        }
        Map<String, String> map = new TreeMap<>();
        for (TdValue item : table.elements()) {
            if (!(item instanceof TdTable t)) {
                throw new IllegalArgumentException("config " + what + " entry is not a table");
            }
            String k = t.get("k") != null ? t.get("k").asString() : "";
            if (k.isBlank()) {
                throw new IllegalArgumentException("config " + what + " entry has a blank or missing key");
            }
            String v = t.get("v") != null ? t.get("v").asString() : "";
            map.putIfAbsent(k, v); // duplicate key within a block: first occurrence wins
        }
        return map;
    }
}
