package io.toterra.subterra.api.datapack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * p.2.33.1 对外 td 数据包消费契约门面（final class、静态纯函数、纯 JDK）：确定性接口面，供上层/域模组按
 * 「kind + id + td 文本」消费 td 数据包。语义与 {@code engine.datapack} 的
 * {@code Datapack}/{@code DatapackEntry}/{@code EntryKind}/{@code DatapackRules}/{@code DatapackExporter}
 * （p.2.2 块）一致——本处为契约与数据面注入，engine 为实现镜像（{@code engine.datapack.DatapackApiMirror}），
 * api 不依赖 engine。契约常量与缺省均系从 engine 实际常量/缺省逐字对照落于此，禁止拍脑袋。
 * <p>确定性：{@link #kinds()} 返回固定序 {@code FUNCTION, RECIPE, LOOT_TABLE, WORLDGEN, STRUCTURE, TAG,
 * LANG, NOISE_SETTINGS}（逐字镜像 {@code engine.datapack.EntryKind} 的 {@code dir} 段）；{@link Kind#dir()}
 * 为数据包目录段（{@code entryId} 的构造源）；{@link #resolveRules} 为纯解析、同输入恒得同输出
 * （后包胜出再存档覆盖胜出的确定性 {@code putAll} merge）；{@link #renderRules} 以排序键输出确定性标记文本；
 * {@link #emptyDatapack()} 返回空数据包缺省（无条目、无规则）。td 消费视角下 payload 以规范 td 文本承载
 * 为不可变值，{@link #exportEntryTd} 与 {@link #rehydrateEntry} 保证
 * {@code export∘rehydrate∘export 逐字节恒等}（同输入同字节）。无随机、无墙钟、无迭代序依赖、无副作用。
 * <p>契约常量：{@link #EMPTY_NAME}={@code ""}（空数据包缺省名）；{@link #kindDirs()} 与 {@link Kind#dir()}
 * 逐字镜像 {@code engine.datapack.EntryKind}；{@link #renderRules} 的空映射 → {@code ""}（镜像
 * {@code DatapackRules.render}）。
 * <p>
 * p.2.33.1 the external td-datapack consumption contract facade (final class, static pure functions, pure
 * JDK): a deterministic interface surface for upper layers / domain mods to consume a td datapack by
 * "kind + id + td text". Semantics match {@code engine.datapack}'s
 * {@code Datapack}/{@code DatapackEntry}/{@code EntryKind}/{@code DatapackRules}/{@code DatapackExporter}
 * (p.2.2 blocks) — this is the contract and data-injection surface for the engine to mirror as its
 * implementation ({@code engine.datapack.DatapackApiMirror}), and the api does not depend on the engine.
 * Contract constants and defaults are pinned here verbatim from the engine's actual constants/defaults —
 * nothing is guessed.
 * <p>Deterministic: {@link #kinds()} returns the fixed order {@code FUNCTION, RECIPE, LOOT_TABLE, WORLDGEN,
 * STRUCTURE, TAG, LANG, NOISE_SETTINGS} (verbatim mirror of {@code engine.datapack.EntryKind}'s {@code dir}
 * segments); {@link Kind#dir()} is the datapack directory segment (the source of {@link #entryId}); 
 * {@link #resolveRules} is a pure resolution, same input always yields the same output (a deterministic
 * {@code putAll} merge of later-pack-wins then save-override-wins); {@link #renderRules} yields a deterministic
 * marker text over sorted keys; {@link #emptyDatapack()} returns the empty-pack default (no entries, no rules).
 * Under the td-consumption view a payload is carried as an immutable canonical td text, and
 * {@link #exportEntryTd} / {@link #rehydrateEntry} guarantee {@code export∘rehydrate∘export} byte-identical
 * (same input → same bytes). No randomness, no wall-clock, no iteration-order dependence, no side effects.
 * <p>Contract constants: {@link #EMPTY_NAME}={@code ""} (the empty-pack default name); {@link #kindDirs()} and
 * {@link Kind#dir()} mirror {@code engine.datapack.EntryKind} verbatim; {@link #renderRules} maps an empty map
 * to {@code ""} (mirrors {@code DatapackRules.render}).
 */
public final class DatapackApi {

    /** The default name of an empty datapack ({@code ""}). / 空数据包缺省名（{@code ""}）。 */
    public static final String EMPTY_NAME = "";

    private DatapackApi() {
    }

    /** The seven base entry kinds plus the noise-settings superset kind, fixed order, mirroring
     *  {@code engine.datapack.EntryKind} (each kind carries its data-dir segment {@code dir}). /
     *  七个基础条目 kind 外加噪声设置超集 kind，固定序，逐字镜像 {@code engine.datapack.EntryKind}
     *  （每个 kind 携带自己的数据目录段 {@code dir}）。 */
    public enum Kind {
        /** functions — {@code data/<ns>/function/<path>.data.tie}. */
        FUNCTION("function"),
        /** recipes. */
        RECIPE("recipe"),
        /** loot tables. */
        LOOT_TABLE("loot_table"),
        /** worldgen. */
        WORLDGEN("worldgen"),
        /** structures. */
        STRUCTURE("structure"),
        /** tags. */
        TAG("tag"),
        /** localization. */
        LANG("lang"),
        /** noise settings — {@code data/<ns>/worldgen/noise_settings/<path>.data.tie}. */
        NOISE_SETTINGS("noise_settings");

        private final String dir;

        Kind(String dir) {
            this.dir = dir;
        }

        /** Directory segment name under {@code data/<namespace>/}, mirroring the engine kind. /
         *  {@code data/<namespace>/} 下的目录段名，镜像 engine kind。 */
        public String dir() {
            return dir;
        }

        /** Resolves a data-dir segment (e.g. {@code "loot_table"}) to a {@link Kind}. Deterministic;
         *  throws {@link IllegalArgumentException} for unknown segments. / 将数据目录段（如
         *  {@code "loot_table"}）回映射为 {@link Kind}。确定性；未知段抛 {@link IllegalArgumentException}。 */
        public static Kind fromDir(String dir) {
            for (Kind k : values()) {
                if (k.dir.equals(dir)) {
                    return k;
                }
            }
            throw new IllegalArgumentException("unknown datapack kind directory: " + dir);
        }

        @Override
        public String toString() {
            return dir;
        }
    }

    /**
     * A single datapack entry view (immutable): kind + namespace + path carrying the payload as canonical
     * td text. {@code id()} = {@code <namespace>:<kind-dir>/<path>}, matching
     * {@code engine.datapack.DatapackEntry#id()}. / 单条数据包条目视图（不可变）：kind + namespace + path，
     * payload 以规范 td 文本承载。{@code id()} = {@code <namespace>:<kind-dir>/<path>}，对齐
     * {@code engine.datapack.DatapackEntry#id()}。
     *
     * @param kind      the entry kind (non-null).
     * @param namespace the entry namespace (non-null).
     * @param path      the entry path (non-null).
     * @param payloadTd the canonical td text of the payload, bare table without {@code type tie<data>}
     *                  header (non-null).
     */
    public record Entry(Kind kind, String namespace, String path, String payloadTd) {

        /** Canonical id {@code <namespace>:<kind-dir>/<path>} (deterministic). / 规范 id
         *  {@code <namespace>:<kind-dir>/<path>}（确定性）。 */
        public String id() {
            return entryId(kind, namespace, path);
        }
    }

    /**
     * A loaded datapack view (immutable, deterministic): name + an id→entry registry + a rule map. The
     * registry is sorted by canonical id for determinism; rules follow the engine's pack-level semantics.
     * / 已装载数据包视图（不可变、确定性）：name + id→entry 注册表 + 规则 map。注册表按规范 id 排序以保
     * 确定性；规则遵循 engine 的包级语义。 */
    public static final class Datapack {

        private final String name;
        private final Map<String, Entry> entries;
        private final Map<String, String> rules;

        private Datapack(String name, Map<String, Entry> entries, Map<String, String> rules) {
            this.name = name;
            this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
            this.rules = Collections.unmodifiableMap(new LinkedHashMap<>(rules));
        }

        /** The datapack name. / 数据包名。 */
        public String name() {
            return name;
        }

        /** All entries by canonical id, deterministically sorted. / 全部条目，按规范 id 确定性排序。 */
        public Map<String, Entry> entries() {
            return entries;
        }

        /** Entries of one kind, in id order. / 某 kind 的条目，按 id 序。 */
        public List<Entry> byKind(Kind kind) {
            List<Entry> out = new ArrayList<>();
            for (Entry e : entries.values()) {
                if (e.kind() == kind) {
                    out.add(e);
                }
            }
            return List.copyOf(out);
        }

        /** Entry by kind/namespace/path, or {@code null} when absent. / 按 kind/namespace/path 取条目；
         *  缺失时返回 {@code null}。 */
        public Entry get(Kind kind, String namespace, String path) {
            return entries.get(entryId(kind, namespace, path));
        }

        /** Entry by canonical id, or {@code null} when absent. / 按规范 id 取条目；缺失时返回 {@code null}。 */
        public Entry getById(String id) {
            return entries.get(id);
        }

        /** Pack-level key→value rules (later pack wins then save override wins), mirrored from
         *  {@code DatapackRules}. / 包级 key→value 规则（后包胜出再存档覆盖胜出），镜像自
         *  {@code DatapackRules}。 */
        public Map<String, String> rules() {
            return rules;
        }

        /** True when the datapack has no entries and no rules. / 数据包无条目且无规则时为真。 */
        public boolean isEmpty() {
            return entries.isEmpty() && rules.isEmpty();
        }
    }

    /** The allowed entry kinds in fixed canonical order. / 允许的条目 kind，固定规范序。 */
    public static List<Kind> kinds() {
        return List.of(Kind.values());
    }

    /** The fixed data-dir name order of all kinds, mirroring {@code engine.datapack.EntryKind}. /
     *  全部 kind 的固定数据目录段名序，逐字镜像 {@code engine.datapack.EntryKind}。 */
    public static List<String> kindDirs() {
        List<String> out = new ArrayList<>();
        for (Kind k : Kind.values()) {
            out.add(k.dir());
        }
        return List.copyOf(out);
    }

    /** The canonical entry id {@code <namespace>:<kind-dir>/<path>}. Deterministic. / 规范条目 id
     *  {@code <namespace>:<kind-dir>/<path>}。确定性。 */
    public static String entryId(Kind kind, String namespace, String path) {
        return namespace + ":" + kind.dir() + "/" + path;
    }

    /** Canonical id whose kind is given by its directory segment. / 以目录段给定 kind 的规范 id。 */
    public static String entryId(String kindDir, String namespace, String path) {
        return entryId(Kind.fromDir(kindDir), namespace, path);
    }

    /**
     * Rehydrates single td payload text into an {@link Entry} with the given identity. The payload is carried
     * as canonical td text (bare table, no {@code type tie<data>} header); the contract treats it as an
     * opaque-but-canonical unit, so the export/rehydrate round-trip
     * ({@code export∘rehydrate∘export}) is byte-identical. Deterministic.
     * / 将单条 td 负载文本按给定身份重水化为 {@link Entry}。payload 以规范 td 文本承载（裸表、无
     * {@code type tie<data>} 头）；契约将其视为不透明但规范的单元，故 export/rehydrate 往返
     * （{@code export∘rehydrate∘export}）逐字节恒等。确定性。
     *
     * @param kind      the entry kind (non-null).
     * @param namespace the entry namespace (non-null).
     * @param path      the entry path (non-null).
     * @param payloadTd the canonical td payload text (non-null).
     * @return a new immutable {@link Entry}.
     * @throws NullPointerException if any argument is null.
     */
    public static Entry rehydrateEntry(Kind kind, String namespace, String path, String payloadTd) {
        if (kind == null || namespace == null || path == null || payloadTd == null) {
            throw new NullPointerException("kind/namespace/path/payloadTd must not be null");
        }
        return new Entry(kind, namespace, path, payloadTd);
    }

    /**
     * Exports an entry's payload to its canonical td text (byte-identical to what rehydrate carries). Combined
     * with {@link #rehydrateEntry}, this guarantees {@code export∘rehydrate∘export} byte-identical.
     * / 将条目 payload 导出为规范 td 文本（与 rehydrate 承载的逐字节一致）。与 {@link #rehydrateEntry} 合用
     * 保证 {@code export∘rehydrate∘export} 逐字节恒等。
     *
     * @param entry the entry (non-null).
     * @return the canonical td payload text.
     * @throws NullPointerException if {@code entry} is null.
     */
    public static String exportEntryTd(Entry entry) {
        if (entry == null) {
            throw new NullPointerException("entry must not be null");
        }
        return entry.payloadTd();
    }

    /** The empty datapack default: {@link #EMPTY_NAME}, no entries, no rules. Deterministic. / 空数据包缺省：
     *  {@link #EMPTY_NAME}、无条目、无规则。确定性。 */
    public static Datapack emptyDatapack() {
        return new Datapack(EMPTY_NAME, Map.of(), Map.of());
    }

    /**
     * Builds an immutable datapack view from a name and entry list (id-sorted for determinism) plus
     * pack-level rules. Duplicate entry ids are rejected. / 由名称、条目列表（按 id 排序以保确定性）与
     * 包级规则构建不可变数据包视图。重复条目 id 被拒绝。
     *
     * @param name    the datapack name (non-null).
     * @param entries the entries (non-null, unique ids).
     * @param rules   the pack-level rules (non-null).
     * @return a new immutable {@link Datapack}.
     * @throws IllegalArgumentException if an entry id is duplicated.
     */
    public static Datapack datapack(String name, List<Entry> entries, Map<String, String> rules) {
        Map<String, Entry> sorted = new TreeMap<>();
        for (Entry e : entries) {
            if (sorted.putIfAbsent(e.id(), e) != null) {
                throw new IllegalArgumentException("duplicate datapack entry id: " + e.id());
            }
        }
        Objects.requireNonNull(rules, "rules must not be null");
        return new Datapack(name, sorted, new LinkedHashMap<>(rules));
    }

    /**
     * Resolves the effective pack-level rules, mirroring {@code DatapackRules} precedence: later packs win
     * for the same key, then save/session overrides win over every pack. Deterministic, insertion-ordered.
     * / 解析有效包级规则，镜像 {@code DatapackRules} 优先级：同一 key 后包胜出，随后存档/会话覆盖对全部包胜出。
     * 确定性，保插入序。
     *
     * @param packs         each pack's key→rendered-value rules, applied in order (later wins).
     * @param saveOverrides save/session overrides, applied last and winning over every pack.
     * @return an immutable resolved {@link Map}.
     */
    public static Map<String, String> resolveRules(List<? extends Map<String, String>> packs,
                                                   Map<String, String> saveOverrides) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map<String, String> pack : packs) {
            out.putAll(pack);
        }
        out.putAll(saveOverrides);
        return Collections.unmodifiableMap(out);
    }

    /**
     * Deterministic marker text for an effective rule map: keys sorted alphabetically, {@code k=value}
     * joined by {@code "; "}. Empty map → {@code ""}. Mirrors {@code DatapackRules.render}. /
     * 有效规则 map 的确定性标记文本：键按字母排序，{@code k=value} 以 {@code "; "} 连接。空 map → {@code ""}。
     * 镜像 {@code DatapackRules.render}。
     */
    public static String renderRules(Map<String, String> effective) {
        if (effective.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : new TreeMap<>(effective).entrySet()) {
            if (!first) {
                sb.append("; ");
            }
            first = false;
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }
}