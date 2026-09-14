package io.toterra.subterra.engine.datapack;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * p.2.33.1 引擎侧镜像实现（api 定义形状 / engine 镜像实现范式，对应 {@code api.datapack.DatapackApi}）：以
 * {@link EntryKind} / {@link DatapackEntry} / {@link DatapackRules} / {@link DatapackExporter} / {@link Td}
 * 的<b>真实常量与语义</b>为唯一来源，暴露与 {@code api.datapack.DatapackApi} 同语义的只读消费面——kind 固定
 * 目录段序、规范条目 id、规则双层 resolve 与确定性 render、td payload 规范往返、空数据包缺省均同输入同输出
 * （供 p.2.33.1 探针对照断言）。本镜像<em>不 import</em> api 包，仅消费 engine 自身类型并把 api 契约值/语义
 * 逐字导出，从而两侧分别构建后完全一致。
 * <p>确定性：全部方法为纯函数；{@link #resolveRules} 用包级规则后包胜出 + 存档覆盖胜出的确定性
 * {@code putAll} merge（即 {@link DatapackRules#resolve} 的拓扑）；{@link #renderRules} 用排序键输出与
 * {@link DatapackRules#render} 相同标记文本；{@link #exportEntryTd}/{@link #rehydrateEntry} 保证
 * {@code export∘rehydrate∘export} 逐字节恒等（payload 以规范 td 文本承载，经 {@link Td#parse}/{@link Td#write}
 * 往返稳定）。常量来源（勿重猜）：{@link EntryKind#values()}、{@link DatapackRules}、
 * {@link DatapackExporter}。
 * <p>
 * p.2.33.1 engine-side mirror implementation (api-shapes / engine-mirrors pattern, counterpart of
 * {@code api.datapack.DatapackApi}): using the <b>actual constants and semantics</b> of {@link EntryKind} /
 * {@link DatapackEntry} / {@link DatapackRules} / {@link DatapackExporter} / {@link Td} as the single source
 * of truth, it exposes a read-only consumption surface with the same semantics as
 * {@code api.datapack.DatapackApi} — fixed kind directory-segment order, canonical entry id, the rules
 * two-layer resolve and deterministic render, canonical td payload round-trip, and empty-pack defaults are all
 * same-input-same-output (the p.2.33.1 probe asserts both sides). This mirror does <em>not</em> import the api
 * package; it consumes only engine types and exports the api contract values/semantics verbatim, so both sides
 * agree exactly when instantiated separately.
 * <p>Deterministic: every method is a pure function; {@link #resolveRules} runs the deterministic
 * {@code putAll} merge of later-pack-wins + save-override-wins (the topology of {@link DatapackRules#resolve});
 * {@link #renderRules} yields the same sorted-key marker text as {@link DatapackRules#render};
 * {@link #exportEntryTd}/ {@link #rehydrateEntry} guarantee {@code export∘rehydrate∘export} byte-identical
 * (the payload is carried as canonical td text, stable through {@link Td#parse}/{@link Td#write}). Constant
 * sources (do not re-guess): {@link EntryKind#values()}, {@link DatapackRules}, {@link DatapackExporter}.
 */
public final class DatapackApiMirror {

    /** The default name of an empty datapack, mirroring {@code api.datapack.DatapackApi#EMPTY_NAME}
     *  ({@code ""}). / 空数据包缺省名，镜像 {@code api.datapack.DatapackApi#EMPTY_NAME}（{@code ""}）。 */
    public static final String EMPTY_NAME = "";

    private DatapackApiMirror() {
    }

    /** The fixed data-dir name order of all entry kinds, sourced verbatim from {@link EntryKind#values()}. /
     *  全部条目 kind 的固定数据目录段名序，逐字源自 {@link EntryKind#values()}。 */
    public static List<String> kindDirs() {
        List<String> out = new ArrayList<>();
        for (EntryKind k : EntryKind.values()) {
            out.add(k.dir());
        }
        return List.copyOf(out);
    }

    /** The canonical entry id {@code <namespace>:<kind-dir>/<path>}, sourced from
     *  {@link DatapackEntry#id()} semantics. / 规范条目 id {@code <namespace>:<kind-dir>/<path>}，源自
     *  {@link DatapackEntry#id()} 语义。 */
    public static String entryId(EntryKind kind, String namespace, String path) {
        return namespace + ":" + kind.dir() + "/" + path;
    }

    /** Canonical id whose kind is given by its directory segment. / 以目录段给定 kind 的规范 id。 */
    public static String entryId(String kindDir, String namespace, String path) {
        return entryId(EntryKind.fromDirectory(kindDir), namespace, path);
    }

    /** Resolves a data-dir segment to an {@link EntryKind}, sourcing {@link EntryKind#fromDirectory}. /
     *  将数据目录段解析为 {@link EntryKind}，源自 {@link EntryKind#fromDirectory}。 */
    public static EntryKind kindFromDir(String dir) {
        return EntryKind.fromDirectory(dir);
    }

    /**
     * Rehydrates single td payload text into an engine {@link DatapackEntry} (parsed to a {@link TdTable});
     * the payload text is the canonical td form (parsed then serialized deterministically), so the
     * export/rehydrate round-trip is byte-identical. / 将单条 td 负载文本重水化为引擎 {@link DatapackEntry}
     * （解析为 {@link TdTable}）；载荷文本须为规范 td 形（经确定性地解析→写出），故 export/rehydrate 往返
     * 逐字节恒等。
     *
     * @param kind      the engine entry kind.
     * @param namespace the entry namespace.
     * @param path      the entry path.
     * @param payloadTd the canonical td payload text (bare table, no header).
     */
    public static DatapackEntry rehydrateEntry(EntryKind kind, String namespace, String path, String payloadTd) {
        return new DatapackEntry(kind, namespace, path, Td.parse(payloadTd));
    }

    /**
     * Exports an entry payload to its canonical td text, mirroring the api's
     * {@code export∘rehydrate∘export} byte-identity guarantee ({@link DatapackExporter#exportEntryTd}). /
     * 将条目 payload 导出为规范 td 文本，镜像 api 的 {@code export∘rehydrate∘export} 逐字节恒等保证
     * （{@link DatapackExporter#exportEntryTd}）。
     */
    public static String exportEntryTd(DatapackEntry entry) {
        return DatapackExporter.exportEntryTd(entry);
    }

    /**
     * Resolves effective pack-level rules, mirroring {@link DatapackRules} precedence: later packs win,
     * then save/session overrides win over every pack. Deterministic, insertion-ordered. /
     * 解析有效包级规则，镜像 {@link DatapackRules} 优先级：后包胜出，随后存档/会话覆盖对全部包胜出。确定性，
     * 保插入序。
     *
     * @param packs         each pack's rules, applied in order (later wins).
     * @param saveOverrides save/session overrides, applied last and winning.
     */
    public static Map<String, TdValue> resolveRules(List<? extends Map<String, TdValue>> packs,
                                                    Map<String, TdValue> saveOverrides) {
        Map<String, TdValue> merged = new LinkedHashMap<>();
        for (Map<String, TdValue> pack : packs) {
            merged.putAll(pack);
        }
        merged.putAll(saveOverrides);
        return Collections.unmodifiableMap(new LinkedHashMap<>(merged));
    }

    /** Deterministic marker text over an effective rule map, sourcing {@link DatapackRules#render} (sorted
     *  keys, {@code k=value} joined by {@code "; "}, empty → {@code ""}). / 有效规则 map 的确定性标记文本，
     *  源自 {@link DatapackRules#render}（键排序，{@code k=value} 以 {@code "; "} 连接，空 → {@code ""}）。 */
    public static String renderRules(Map<String, TdValue> effective) {
        return DatapackRules.render(effective);
    }

    /** The default (empty) datapack rule set, as an unmodifiable empty map. / 缺省（空）数据包规则集，不可变
     *  空 map。 */
    public static Map<String, TdValue> defaultRules() {
        return Collections.unmodifiableMap(new LinkedHashMap<>());
    }

    /** The default empty datapack name, mirroring the api contract. / 缺省空数据包名，镜像 api 契约。 */
    public static String emptyName() {
        return EMPTY_NAME;
    }

    /** Canonical td serialize of a table, sourced from {@link Td#write} (deterministic). / 表的规范 td 序列化，
     *  源自 {@link Td#write}（确定性）。 */
    public static String writeTd(TdTable table) {
        return Td.write(table);
    }

    /** Canonical td serialize of a plain map viewed as named entries, deterministic (mirrors
     *  {@link Td#write}). / 将普通 map 视作命名条目做规范 td 序列化，确定性（镜像 {@link Td#write}）。 */
    public static String writeNameValueMap(Map<String, String> named) {
        TdTable.Builder b = TdTable.builder();
        for (Map.Entry<String, String> e : new TreeMap<>(named).entrySet()) {
            b.put(e.getKey(), e.getValue());
        }
        return Td.write(b.build());
    }
}