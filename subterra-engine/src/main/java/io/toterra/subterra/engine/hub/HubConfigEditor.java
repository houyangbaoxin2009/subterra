package io.toterra.subterra.engine.hub;

import io.toterra.subterra.api.config.Rule;
import io.toterra.subterra.engine.config.rules.RuleReport;
import io.toterra.subterra.engine.config.rules.RuleSpec;
import io.toterra.subterra.engine.config.rules.RuleValidator;
import io.toterra.subterra.engine.config.rules.RuleViolationException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * p.2.23.2 配置编辑门面（final class，纯 JDK）——In-game mod hub 的「表单保存」侧：把表单值
 * （{@code Map<String,String>}，键=规则键）写入双层配置。本类是 p.2.17 规则系统语义的 Hub
 * 适配（表单编辑面）：内部<b>直接构造 Rules 文档状态</b>（规格 {@code specs} + 全局档
 * {@code global} + 存档覆盖档 {@code overrides}，均有序），配置存储语义完全复用——
 * 双层结构、{@link #resolve()}（global putAll → overrides putAll）、{@link #render()}
 * （TreeMap 字典序 {@code k=v; }）、校验（{@link RuleValidator} 以 {@code RuleSpec} 声明为
 * 准）均与 {@code RuleStore} 对齐（见 {@code engine.config.rules.RuleStore} 类 javadoc）。
 * <p>
 * 与 {@code RuleStore} 装载面（putIfAbsent 首现胜出、跨装载累积）的差异（仅此一处，文档化）：
 * 编辑面是<b>表单回写</b>——{@code editGlobal/editOverrides} 对表单值中的键做 key 级覆盖
 * （{@code putAll}），即用户改完表单提交的新值必定生效；表单值未覆盖的键保持原值不动。
 * 装载面（文档并入、不覆盖）仍由 {@code RuleStore}/{@code RuleReloader} 承载，两条路径共用
 * 同一存储语义（双层、resolve、render、校验），互不干扰。
 * <p>
 * 编辑契约：写入前过类型校验（复用 {@code RuleSpec} 声明）——未知键、类型违约、缺失必填
 * 一律抛确定性异常 {@link RuleViolationException}（携带完整 {@link RuleReport}），全有或全无
 * （违约时不写入任何条目、{@link #revision()} 不自增）；通过后 key 级覆盖并入对应档并
 * {@code revision} 自增 1（每次实际编辑 +1，禁时间戳、单调）。null specs/formValues 或 null
 * 键值由构造/校验层以 {@link IllegalArgumentException} 拒绝。
 * <p>
 * 确定性：注册序固定、编辑序固定、无时序、无随机、同输入同输出；禁 O(n²)（构造为线性扫描，
 * 校验由 {@link RuleValidator} 保证 O(n log n)）。{@code specs()/global()/overrides()} 返回
 * 只读副本；{@link #resolve()} 返回有序副本，外部篡改不影响内部状态。非线程安全（引擎单线程
 * 确定性设计，无同步原语）。
 * <p>
 * p.2.23.2 config-edit facade (final class, pure JDK) — the "form save" side of the In-game
 * mod hub: writes form values ({@code Map<String,String>}, keys = rule keys) into the two-tier
 * config. This class is the Hub adaptation of the p.2.17 rules-system semantics (form-edit
 * surface); internally it <b>builds the Rules-document state directly</b> (specs
 * {@code specs} + global layer {@code global} + save-overrides layer {@code overrides}, all
 * ordered), and the config-storage semantics are fully reused — the two-tier structure,
 * {@link #resolve()} (global putAll then overrides putAll), {@link #render()} (TreeMap
 * lexicographic {@code k=v; }), and validation ({@link RuleValidator} against the
 * {@code RuleSpec} declarations) all align with {@code RuleStore} (see the
 * {@code engine.config.rules.RuleStore} class javadoc).
 * <p>
 * The one divergence from the {@code RuleStore} load surface (putIfAbsent first-occurrence-wins,
 * accumulating across loads), documented here: the edit surface is a <b>form write-back</b> —
 * {@code editGlobal/editOverrides} overwrite per key ({@code putAll}) for the keys present in
 * the form values, i.e. a submitted new value from an edited form always takes effect; keys not
 * covered by the form values keep their previous values. The load surface (document merge,
 * never overwrite) stays with {@code RuleStore}/{@code RuleReloader}; both paths share the same
 * storage semantics (two-tier, resolve, render, validation) without interfering.
 * <p>
 * Edit contract: values are type-checked before the write (reusing the {@code RuleSpec}
 * declarations) — an unknown key, a type violation or a missing required rule raises the
 * deterministic {@link RuleViolationException} (carrying the full {@link RuleReport}),
 * all-or-nothing (nothing written on violations, {@link #revision()} not incremented); a passing
 * set merges into the target layer by key-level overwrite and bumps {@code revision} by 1 (every
 * actual edit, no timestamps, monotonic). Null specs/formValues or null keys/values are rejected
 * with {@link IllegalArgumentException} at construction/validation time.
 * <p>
 * Determinism: fixed registration order, fixed edit order, no timing, no randomness, same input
 * → same output; no O(n²) (construction is a linear scan, validation stays O(n log n) via
 * {@link RuleValidator}). {@code specs()/global()/overrides()} return read-only copies;
 * {@link #resolve()} returns an ordered copy — external mutation never leaks into the editor.
 * Not thread-safe by design (single-threaded deterministic engine; no synchronization
 * primitives).
 */
public final class HubConfigEditor {

    private final List<RuleSpec> specs;
    private final Map<String, String> global = new LinkedHashMap<>();
    private final Map<String, String> overrides = new LinkedHashMap<>();
    private long revision = 0L;

    /**
     * 构造：以给定规格列表建立编辑面（保注册序）；null 列表/元素、同 key 重复一律
     * {@link IllegalArgumentException}（对齐 {@code RuleStore.register} 纪律）。
     * Builds the editor from the given spec list (registration order preserved); a null
     * list/element or a duplicate key raises {@link IllegalArgumentException} (aligned with the
     * {@code RuleStore.register} discipline).
     *
     * @param specs 规则定义（键唯一）/ the rule definitions (unique keys).
     */
    public HubConfigEditor(List<RuleSpec> specs) {
        if (specs == null) {
            throw new IllegalArgumentException("specs must be non-null");
        }
        List<RuleSpec> copy = new ArrayList<>(specs.size());
        Set<String> seen = new HashSet<>();
        for (RuleSpec spec : specs) {
            if (spec == null) {
                throw new IllegalArgumentException("rule spec must be non-null");
            }
            String k = spec.key().form();
            if (!seen.add(k)) {
                throw new IllegalArgumentException("duplicate rule spec: " + k);
            }
            copy.add(spec);
        }
        this.specs = List.copyOf(copy);
    }

    /**
     * 编辑全局档：先过类型校验（{@link RuleValidator} 以 {@code RuleSpec} 声明为准；未知键、类型
     * 违约、缺失必填 → {@link RuleViolationException}，不写入任何条目、revision 不自增），通过后
     * 表单值按 key 级覆盖并入全局档并 revision 自增 1。null 表单值或 null 键值由校验层以
     * {@link IllegalArgumentException} 拒绝。
     * <p>
     * Edits the global layer: type-checked first ({@link RuleValidator} against the
     * {@code RuleSpec} declarations; an unknown key, a type violation or a missing required rule
     * → {@link RuleViolationException}, nothing written, revision not incremented), then the form
     * values merge into the global layer by key-level overwrite and revision bumps by 1. A null
     * form-values map or null keys/values are rejected with {@link IllegalArgumentException} at
     * the validation layer.
     *
     * @param formValues 表单值（键=规则键）/ the form values (keys = rule keys).
     */
    public void editGlobal(Map<String, String> formValues) {
        RuleReport report = validate(formValues);
        if (!report.ok()) {
            throw new RuleViolationException(report);
        }
        global.putAll(formValues);
        revision++;
    }

    /**
     * 编辑存档覆盖档：契约同 {@link #editGlobal(Map)}，key 级覆盖并入覆盖档。
     * Edits the save-overrides layer: same contract as {@link #editGlobal(Map)}, merging by
     * key-level overwrite into the overrides layer.
     *
     * @param formValues 表单值（键=规则键）/ the form values (keys = rule keys).
     */
    public void editOverrides(Map<String, String> formValues) {
        RuleReport report = validate(formValues);
        if (!report.ok()) {
            throw new RuleViolationException(report);
        }
        overrides.putAll(formValues);
        revision++;
    }

    /**
     * 双层解析：先并入 global，再 putAll(overrides)（存档覆盖档对冲突 key 全部胜出）；返回有序
     * 副本（LinkedHashMap，外部修改不影响内部状态）。语义与 {@code RuleStore.resolve} 逐条一致
     * （= p.2.16.2 {@code api.config.RuleRegistry.resolveTiered} = p.2.3.5
     * {@code DatapackRules.resolveTiered}）。
     * <p>
     * Two-tier resolve: global first, then putAll(overrides) (the save-overrides layer wins every
     * conflicting key); returns an ordered copy (LinkedHashMap; external mutation never affects
     * the editor). Semantics item-identical to {@code RuleStore.resolve} (= p.2.16.2
     * {@code api.config.RuleRegistry.resolveTiered} = p.2.3.5
     * {@code DatapackRules.resolveTiered}).
     *
     * @return 有效规则（global → 存档胜出）/ the effective rules (global, then save wins).
     */
    public Map<String, String> resolve() {
        Map<String, String> out = new LinkedHashMap<>(global);
        out.putAll(overrides);
        return out;
    }

    /**
     * 确定性渲染当前有效规则：key 字典序（TreeMap）{@code k=v; } 连接，空 → 空串；与
     * {@code RuleStore.render} / {@code DatapackRules.render} 逐字节一致（同输入同字节，连跑两遍
     * 同字节）。
     * <p>
     * Renders the current effective rules deterministically: keys in lexicographic order
     * (TreeMap), {@code k=v} joined by {@code "; "}, empty → {@code ""}; byte-identical to
     * {@code RuleStore.render} / {@code DatapackRules.render} (same input → same bytes, same
     * bytes on a second run).
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
     * 单调递增版本号：每次实际编辑（通过校验并写入）自增 1；违约编辑不自增；初始 0；禁时间戳。
     * The monotonic revision: +1 per actual edit (validated and written), never incremented by a
     * violating edit; starts at 0; no timestamps.
     *
     * @return 当前版本号 / the current revision.
     */
    public long revision() {
        return revision;
    }

    /**
     * 声明型规格的只读副本（固定注册序）。A read-only copy of the specs (fixed registration order).
     *
     * @return 不可变规格列表 / the immutable spec list.
     */
    public List<RuleSpec> specs() {
        return specs;
    }

    /**
     * 全局档只读副本（编辑序，key 级覆盖已并入）。A read-only copy of the global layer (edit order,
     * key-level overwrites already merged).
     *
     * @return 有序副本 / an ordered copy.
     */
    public Map<String, String> global() {
        return new LinkedHashMap<>(global);
    }

    /**
     * 存档覆盖档只读副本（编辑序，key 级覆盖已并入）。A read-only copy of the save-overrides layer
     * (edit order, key-level overwrites already merged).
     *
     * @return 有序副本 / an ordered copy.
     */
    public Map<String, String> overrides() {
        return new LinkedHashMap<>(overrides);
    }

    private RuleReport validate(Map<String, String> formValues) {
        if (formValues == null) {
            throw new IllegalArgumentException("formValues must be non-null");
        }
        List<Rule> rules = new ArrayList<>(formValues.size());
        for (Map.Entry<String, String> e : formValues.entrySet()) {
            rules.add(new Rule(e.getKey(), e.getValue())); // null key/value → IllegalArgumentException
        }
        return RuleValidator.validate(specs, rules);
    }
}
