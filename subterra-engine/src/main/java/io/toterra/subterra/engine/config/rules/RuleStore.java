package io.toterra.subterra.engine.config.rules;

import io.toterra.subterra.api.config.Rule;
import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * p.2.17.2 双层配置 RuleStore（实例类，可变，纯 JDK）——引擎侧配置存储：声明型规格
 * {@code specs}（固定注册序，注册期同 key 重复一律 {@link IllegalArgumentException}）+ 全局档
 * {@code global}（有序）+ 存档覆盖档 {@code overrides}（有序）。值载体直接复用
 * {@code api.config.Rule}（key/value 字符串），td 文档形态与装载语义经
 * {@link RuleDocument#fromTd}（对齐 {@code DatapackRules.fromManifest}：首现胜出、畸形条目静默
 * 跳过）。
 * <p>
 * 双层解析语义对齐 p.2.16.2 {@code api.config.RuleRegistry.resolveTiered} 与 p.2.3.5
 * {@code DatapackRules.resolveTiered}（值型不同：本类为字符串、彼处为 TdValue）：先并入 global，
 * 再 putAll(overrides)——存档覆盖档对冲突 key 全部胜出（{@link #resolve()}）。渲染
 * {@link #render()} 与 {@code DatapackRules.render} / api render 对齐：key 字典序（TreeMap）
 * {@code k=v; } 连接，空 → 空串。
 * <p>
 * 装载契约（{@code loadGlobal/loadGlobalText/loadOverrides/loadOverridesText}）：文档先经
 * {@link RuleDocument#fromTd} 解析，再经 {@link RuleValidator#validate} 对 specs 校验；违约即以
 * {@link RuleViolationException} 抛出（携带完整 {@link RuleReport}），全有或全无——违约时不装载
 * 任何条目；通过后按首现胜出（putIfAbsent）并入对应档（重复装载同 key 不覆盖已装载值）。装载期
 * 违约校验覆盖类型违约、未知键、缺失必填；文档内重复 key 由首现胜出消解（另见
 * {@link #validate()}）。
 * <p>
 * 确定性：规格注册序固定、装载序固定、无时序、无随机、同输入同输出；禁 O(n²)（装载为线性扫描，
 * 校验由 {@link RuleValidator} 保证排序 O(n log n)）。{@code specs()/global()/overrides()} 返回
 * 只读副本；{@link #resolve()} 返回有序副本，外部篡改不影响内部状态。
 * <p>
 * p.2.17.2 two-tier config RuleStore (an instance class, mutable, pure JDK) — the engine-side
 * config store: declarative specs {@code specs} (fixed registration order; a duplicate key at
 * registration time raises {@link IllegalArgumentException}) + a global layer {@code global}
 * (ordered) + a save-overrides layer {@code overrides} (ordered). The value carrier reuses
 * {@code api.config.Rule} (key/value strings) directly; the td document shape and load semantics
 * go through {@link RuleDocument#fromTd} (aligned with {@code DatapackRules.fromManifest}: first
 * occurrence wins, malformed entries skipped silently).
 * <p>
 * Two-tier resolution aligns with p.2.16.2 {@code api.config.RuleRegistry.resolveTiered} and
 * p.2.3.5 {@code DatapackRules.resolveTiered} (value types differ: strings here, TdValue there):
 * global is merged in first, then putAll(overrides) — the save-overrides layer wins every
 * conflicting key ({@link #resolve()}). Rendering {@link #render()} aligns with
 * {@code DatapackRules.render} / the api render: keys in lexicographic order (TreeMap) joined as
 * {@code k=v; }, empty → {@code ""}.
 * <p>
 * Load contract ({@code loadGlobal/loadGlobalText/loadOverrides/loadOverridesText}): the document
 * is first parsed by {@link RuleDocument#fromTd}, then checked against the specs by
 * {@link RuleValidator#validate}; on violations a {@link RuleViolationException} is thrown
 * (carrying the full {@link RuleReport}) — all-or-nothing: nothing is loaded on violations;
 * otherwise the values merge into the layer first-occurrence-wins (putIfAbsent; reloading an
 * already-loaded key never overwrites it). Load-time validation covers type violations, unknown
 * keys and missing required rules; duplicate keys within a document are dissolved by
 * first-occurrence-wins (see also {@link #validate()}).
 * <p>
 * Determinism: fixed spec registration order, fixed load order, no timing, no randomness, same
 * input → same output; no O(n²) (loads are linear scans, validation stays O(n log n) via
 * {@link RuleValidator}). {@code specs()/global()/overrides()} return read-only copies;
 * {@link #resolve()} returns an ordered copy — external mutation never leaks into the store.
 */
public final class RuleStore {

    private final List<RuleSpec> specs = new ArrayList<>();
    private final Map<String, String> global = new LinkedHashMap<>();
    private final Map<String, String> overrides = new LinkedHashMap<>();

    /**
     * 构造：以给定规格列表建立存储（保注册序）；null 列表/元素、同 key 重复一律
     * {@link IllegalArgumentException}。
     * Builds the store from the given spec list (registration order preserved); a null
     * list/element or a duplicate key raises {@link IllegalArgumentException}.
     *
     * @param specs 规则定义（键唯一）/ the rule definitions (unique keys).
     */
    public RuleStore(List<RuleSpec> specs) {
        if (specs == null) {
            throw new IllegalArgumentException("specs must be non-null");
        }
        for (RuleSpec spec : specs) {
            register(spec);
        }
    }

    /**
     * 增量注册一条规格（追加到注册序末尾）；null 或同 key 重复一律 {@link IllegalArgumentException}。
     * Registers one spec incrementally (appended to the registration order); a null spec or a
     * duplicate key raises {@link IllegalArgumentException}.
     *
     * @param spec 待注册规格 / the spec to register.
     */
    public void register(RuleSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("rule spec must be non-null");
        }
        String k = spec.key().form();
        for (RuleSpec s : specs) {
            if (s.key().form().equals(k)) {
                throw new IllegalArgumentException("duplicate rule spec: " + k);
            }
        }
        specs.add(spec);
    }

    /**
     * 装载全局档（td 文档）：解析 → 校验 → 违约抛 {@link RuleViolationException}（携带完整报告，
     * 不装载任何条目）；通过后按首现胜出并入 {@code global}。
     * Loads the global layer (a td document): parse → validate → on violations throw
     * {@link RuleViolationException} (full report; nothing loaded); otherwise merge into
     * {@code global}, first occurrence wins.
     *
     * @param doc 全局规则文档根表（不可 null）/ the global rule document root table (never null).
     */
    public void loadGlobal(TdTable doc) {
        load(doc, global);
    }

    /**
     * 装载全局档（td 文本）：{@link Td#parse} 解析后同 {@link #loadGlobal(TdTable)}。
     * Loads the global layer (td text): parsed by {@link Td#parse}, then as
     * {@link #loadGlobal(TdTable)}.
     *
     * @param tdText 全局规则文档 td 文本（不可 null）/ the global rule document td text (never null).
     */
    public void loadGlobalText(String tdText) {
        if (tdText == null) {
            throw new IllegalArgumentException("tdText must be non-null");
        }
        loadGlobal(Td.parse(tdText));
    }

    /**
     * 装载存档覆盖档（td 文档）：同 {@link #loadGlobal(TdTable)}，并入 {@code overrides}。
     * Loads the save-overrides layer (a td document): as {@link #loadGlobal(TdTable)}, merging
     * into {@code overrides}.
     *
     * @param doc 覆盖档规则文档根表（不可 null）/ the overrides rule document root table (never null).
     */
    public void loadOverrides(TdTable doc) {
        load(doc, overrides);
    }

    /**
     * 装载存档覆盖档（td 文本）：{@link Td#parse} 解析后同 {@link #loadOverrides(TdTable)}。
     * Loads the save-overrides layer (td text): parsed by {@link Td#parse}, then as
     * {@link #loadOverrides(TdTable)}.
     *
     * @param tdText 覆盖档规则文档 td 文本（不可 null）/ the overrides rule document td text (never null).
     */
    public void loadOverridesText(String tdText) {
        if (tdText == null) {
            throw new IllegalArgumentException("tdText must be non-null");
        }
        loadOverrides(Td.parse(tdText));
    }

    /**
     * 双层解析：先并入 global，再 putAll(overrides)（存档覆盖档对冲突 key 全部胜出）；返回有序副本
     * （LinkedHashMap，外部修改不影响内部状态）。语义 = p.2.16.2
     * {@code api.config.RuleRegistry.resolveTiered} = p.2.3.5 {@code DatapackRules.resolveTiered}。
     * Two-tier resolve: global first, then putAll(overrides) (the save-overrides layer wins every
     * conflicting key); returns an ordered copy (LinkedHashMap; external mutation never affects
     * the store). Semantics = p.2.16.2 {@code api.config.RuleRegistry.resolveTiered} = p.2.3.5
     * {@code DatapackRules.resolveTiered}.
     *
     * @return 有效规则（global → 存档胜出）/ the effective rules (global, then save wins).
     */
    public Map<String, String> resolve() {
        Map<String, String> out = new LinkedHashMap<>(global);
        out.putAll(overrides);
        return out;
    }

    /**
     * 确定性渲染当前有效规则：key 字典序（TreeMap）{@code k=v; } 连接，空 → 空串，与
     * {@code DatapackRules.render} / {@code api.config.RuleRegistry.render} 对齐（同输入逐字符一致）。
     * Renders the current effective rules deterministically: keys in lexicographic order
     * (TreeMap), {@code k=v} joined by {@code "; "}, empty → {@code ""}, aligned with
     * {@code DatapackRules.render} / {@code api.config.RuleRegistry.render} (character-for-character
     * identical for the same input).
     *
     * @return 规范渲染文本 / the canonical rendered text.
     */
    public String render() {
        Map<String, String> effective = resolve();
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

    /**
     * 校验当前状态：对 global+overrides 合并有效值做 {@link RuleValidator#validate}（类型违约、未知
     * 键、缺失必填）。合并视图键唯一（两层各自首现胜出），故 CONFLICT 在此路径不可达——文档内重复
     * 已在装载期消解；报告以返回值给出（不抛异常，与装载路径的 {@link RuleViolationException} 相对）。
     * Validates the current state: {@link RuleValidator#validate} over the merged effective values
     * of global+overrides (type violations, unknown keys, missing required). The merged view has
     * unique keys (each layer is first-occurrence-wins), so CONFLICT is unreachable on this path —
     * in-document duplicates were dissolved at load time; the report is returned (never raised,
     * opposite to the load path's {@link RuleViolationException}).
     *
     * @return 确定性校验报告 / the deterministic validation report.
     */
    public RuleReport validate() {
        List<Rule> effective = new ArrayList<>(global.size() + overrides.size());
        for (Map.Entry<String, String> e : resolve().entrySet()) {
            effective.add(new Rule(e.getKey(), e.getValue()));
        }
        return RuleValidator.validate(specs, effective);
    }

    /**
     * 声明型规格的只读副本（固定注册序）。A read-only copy of the specs (fixed registration order).
     *
     * @return 不可变规格列表 / the immutable spec list.
     */
    public List<RuleSpec> specs() {
        return List.copyOf(specs);
    }

    /**
     * 全局档只读副本（装载序，首现胜出）。A read-only copy of the global layer (load order, first
     * occurrence wins).
     *
     * @return 有序副本 / an ordered copy.
     */
    public Map<String, String> global() {
        return new LinkedHashMap<>(global);
    }

    /**
     * 存档覆盖档只读副本（装载序，首现胜出）。A read-only copy of the save-overrides layer (load
     * order, first occurrence wins).
     *
     * @return 有序副本 / an ordered copy.
     */
    public Map<String, String> overrides() {
        return new LinkedHashMap<>(overrides);
    }

    private void load(TdTable doc, Map<String, String> layer) {
        if (doc == null) {
            throw new IllegalArgumentException("doc must be non-null");
        }
        List<Rule> parsed = RuleDocument.fromTd(doc);
        RuleReport report = RuleValidator.validate(specs, parsed);
        if (!report.ok()) {
            throw new RuleViolationException(report);
        }
        for (Rule rule : parsed) {
            layer.putIfAbsent(rule.key(), rule.value()); // first occurrence wins
        }
    }
}
