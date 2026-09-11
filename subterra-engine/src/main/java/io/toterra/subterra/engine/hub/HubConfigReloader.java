package io.toterra.subterra.engine.hub;

import io.toterra.subterra.engine.config.rules.RuleReloader;
import io.toterra.subterra.engine.config.rules.RuleSpec;
import io.toterra.subterra.engine.config.rules.RuleStore;

import java.util.List;
import java.util.Map;

/**
 * p.2.23.2 热重载门面（final class，纯 JDK）——In-game mod hub 的「热重载」侧：对双层配置做
 * <b>逐字节变更检测</b>（同文本 → unchanged、{@link #revision()} 不自增）+ <b>原子
 * apply-or-keep-old</b>（违约时保持旧态、不写入任何条目）+ <b>重入幂等</b>（同序列重放终态
 * 一致）。本类是 p.2.17 规则系统语义的 Hub 适配（表单编辑面）——<b>内部委托
 * {@link RuleReloader}</b>（目标 {@link RuleStore} 经构造注入），自身只做表单值 ↔ 配置文档的
 * 适配层：{@link #reloadGlobal(Map, Map)}/{@link #reloadOverrides(Map, Map)} 把「旧/新表单值」
 * 经 {@link HubFormValues#formValuesToTd} 转为规则文档 td 文本，再交给
 * {@code RuleReloader.reloadGlobalText/reloadOverridesText} 走其四步确定性契约（变更检测 →
 * 幂等记忆 → 原子装载 → 单调版本），结果直接复用 {@link RuleReloader.ReloadResult}。
 * <p>
 * 与 {@code RuleReloader} 的异同（文档化）：语义完全一致（同文本 no-op、失败保持旧态、成功
 * applied 才 revision +1、unchanged/failed 不自增；失败不更新幂等记忆，重试同非法文档仍报
 * failed），本类不重复实现——仅把输入形态从「td 文档文本」适配为「表单值 Map」；重载写入仍走
 * {@code RuleStore} 装载语义（putIfAbsent 首现胜出、跨重载累积），与
 * {@link HubConfigEditor} 的 key 级覆盖编辑面相对、互不干扰。
 * <p>
 * 确定性：适配转换（{@link HubFormValues#formValuesToTd}）确定性、同输入同字节；委托结果
 * 确定性（同序列同终态、重放一致）；无时序、无随机、禁 O(n²)（每次操作 = 常数次线性扫描 /
 * O(n log n) 校验）。非线程安全（引擎单线程确定性设计，无同步原语）。
 * <p>
 * p.2.23.2 hot-reload facade (final class, pure JDK) — the "hot reload" side of the In-game
 * mod hub: <b>byte-exact change detection</b> over the two-tier config (same text → unchanged,
 * {@link #revision()} not incremented) + <b>atomic apply-or-keep-old</b> (on violations the old
 * state is kept with nothing written) + <b>re-entrant idempotency</b> (the same sequence replayed
 * ends in the same final state). This class is the Hub adaptation of the p.2.17 rules-system
 * semantics (form-edit surface) — it <b>delegates to {@link RuleReloader}</b> (the target
 * {@link RuleStore} is constructor-injected), acting purely as a form-value ↔ config-document
 * adapter: {@link #reloadGlobal(Map, Map)}/{@link #reloadOverrides(Map, Map)} convert the
 * old/new form values to rule-document td text via
 * {@link HubFormValues#formValuesToTd} and hand them to
 * {@code RuleReloader.reloadGlobalText/reloadOverridesText}, which runs its four-step
 * deterministic contract (change detection → idempotency memory → atomic load → monotonic
 * revision); the result reuses {@link RuleReloader.ReloadResult} directly.
 * <p>
 * Difference from {@code RuleReloader} (documented): the semantics are identical (same text →
 * no-op; failure keeps the old state; revision +1 only on an actual applied reload, never on
 * unchanged/failed; failures do not update the idempotency memory, so retrying the same invalid
 * document still reports failed) — this class does not re-implement them; it only adapts the
 * input shape from "td document text" to "form-value Map". Reload writes still go through the
 * {@code RuleStore} load semantics (putIfAbsent first-occurrence-wins, accumulating across
 * reloads), orthogonal to and non-interfering with the key-level-overwrite edit surface of
 * {@link HubConfigEditor}.
 * <p>
 * Determinism: the adapter conversion ({@link HubFormValues#formValuesToTd}) is deterministic,
 * same input → same bytes; the delegated result is deterministic (same sequence → same final
 * state, replay-consistent); no timing, no randomness, no O(n²) (each operation = a constant
 * number of linear scans / O(n log n) validation). Not thread-safe by design (single-threaded
 * deterministic engine; no synchronization primitives).
 */
public final class HubConfigReloader {

    private final RuleReloader delegate;
    private final List<RuleSpec> specs;

    /**
     * 构造：目标 store 为 null 一律 {@link IllegalArgumentException}；规格经
     * {@code store.specs()} 获取（RuleStore 已保证键唯一）。
     * Constructor: a null target store raises {@link IllegalArgumentException}; the specs come
     * from {@code store.specs()} (the RuleStore already guarantees unique keys).
     *
     * @param store 待重载的规则存储（不可 null）/ the rule store to reload (never null).
     */
    public HubConfigReloader(RuleStore store) {
        if (store == null) {
            throw new IllegalArgumentException("store must be non-null");
        }
        this.delegate = new RuleReloader(store);
        this.specs = store.specs();
    }

    /**
     * 全局档热重载（表单值形态）：旧/新表单值经 {@link HubFormValues#formValuesToTd} 转为 td
     * 文本后委托 {@link RuleReloader#reloadGlobalText}——同文本 → unchanged；违约 → failed
     * （store 保持旧态）；通过 → applied。null 表单值由适配层以
     * {@link IllegalArgumentException} 拒绝。
     * <p>
     * Hot-reloads the global layer (form-value shape): the old/new form values are converted to
     * td text via {@link HubFormValues#formValuesToTd}, then delegated to
     * {@link RuleReloader#reloadGlobalText} — same text → unchanged; a violation → failed (the
     * store keeps its old state); a passing document → applied. A null form-values map is
     * rejected with {@link IllegalArgumentException} by the adapter layer.
     *
     * @param oldFormValues 旧全局表单值（不可 null）/ the old global form values (never null).
     * @param newFormValues 新全局表单值（不可 null）/ the new global form values (never null).
     * @return 确定性重载结果（复用 {@link RuleReloader.ReloadResult}）/ the deterministic reload
     *         result (reusing {@link RuleReloader.ReloadResult}).
     */
    public RuleReloader.ReloadResult reloadGlobal(Map<String, String> oldFormValues,
                                                  Map<String, String> newFormValues) {
        return delegate.reloadGlobalText(
                HubFormValues.formValuesToTd(specs, oldFormValues),
                HubFormValues.formValuesToTd(specs, newFormValues));
    }

    /**
     * 存档覆盖档热重载（表单值形态）：同 {@link #reloadGlobal(Map, Map)}，委托
     * {@link RuleReloader#reloadOverridesText} 并入覆盖档。
     * Hot-reloads the save-overrides layer (form-value shape): as
     * {@link #reloadGlobal(Map, Map)}, delegated to
     * {@link RuleReloader#reloadOverridesText} into the overrides layer.
     *
     * @param oldFormValues 旧覆盖档表单值（不可 null）/ the old overrides form values (never null).
     * @param newFormValues 新覆盖档表单值（不可 null）/ the new overrides form values (never null).
     * @return 确定性重载结果（复用 {@link RuleReloader.ReloadResult}）/ the deterministic reload
     *         result (reusing {@link RuleReloader.ReloadResult}).
     */
    public RuleReloader.ReloadResult reloadOverrides(Map<String, String> oldFormValues,
                                                     Map<String, String> newFormValues) {
        return delegate.reloadOverridesText(
                HubFormValues.formValuesToTd(specs, oldFormValues),
                HubFormValues.formValuesToTd(specs, newFormValues));
    }

    /**
     * 单调递增版本号（委托 {@link RuleReloader#revision()}）：每次实际 applied 自增 1；
     * unchanged 与 failed 不自增；初始 0；禁时间戳。
     * The monotonic revision (delegating to {@link RuleReloader#revision()}): +1 per actually
     * applied reload, never incremented by unchanged or failed; starts at 0; no timestamps.
     *
     * @return 当前版本号 / the current revision.
     */
    public long revision() {
        return delegate.revision();
    }
}
