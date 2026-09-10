package io.toterra.subterra.engine.config.rules;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;

/**
 * p.2.17.3 确定性规则热重载门（final class，纯 JDK）——目标 {@link RuleStore} 经构造注入；本类是
 * 以「重载」名义变更该 store 的唯一入口（绕过本类直接装载会使幂等记忆失真，本类不监听外部变更）。
 * 每次重载以「旧文档 vs 新文档」成对给出，按下列确定性契约执行：
 * <p>
 * ① 变更检测（逐字节）：旧文档与新文档逐字节相同（文本形态 = {@code String.equals}；表形态 = 经
 * {@link Td#write} 的规范文本逐字节）→ no-op，返回 {@link ReloadResult#unchanged()}——不解析、
 * 不校验、store 不触碰、{@link #revision()} 不自增。
 * <p>
 * ② 确定性幂等（重入安全）：新文档与上一次成功应用的文档逐字节相同 → 同样返回 unchanged（同一
 * 目标重复送达不重复生效）。失败不更新记忆，同一非法文档重试仍报 failed（同输入同输出）。
 * <p>
 * ③ 原子应用（apply-or-keep-old）：变更文档经 {@link RuleStore} 装载语义全量处理（解析 → 校验 →
 * 全有或全无并入，见 {@link RuleStore} 类 javadoc：违约时不装载任何条目）——违约时
 * {@link RuleViolationException} 被捕获转为 {@link ReloadResult#failed}，store 保持旧状态、不写
 * 入任何条目，失败记忆与版本号均不更新；通过后才并入目标档。
 * <p>
 * ④ 单调版本：{@link #revision()} 为 long 单调递增号——每次实际 applied 自增 1，unchanged 与
 * failed 均不自增；禁时间戳。
 * <p>
 * 语义来源：应用即复用 {@link RuleStore} 装载语义（双层配置；跨重载累积、首现胜出——已装载的 key
 * 永不覆盖，重载从不删除先前装载的规则）。确定性：同序列同终态（重放一致）、无时序、无随机、禁
 * O(n²)（每次操作 = 常数次线性扫描 / O(n log n) 校验）。非线程安全（引擎单线程确定性设计，无同步
 * 原语）。
 * <p>
 * p.2.17.3 deterministic rule hot-reload gate (final class, pure JDK) — the target
 * {@link RuleStore} is constructor-injected; this class is the single entry point for changing
 * that store "as a reload" (bypassing it with direct loads would stale the idempotency memory;
 * external mutation is not watched). Every reload takes an old-vs-new document pair and runs the
 * following deterministic contract:
 * <p>
 * (1) Change detection (byte-exact): when the old and new documents are byte-identical (text
 * form: {@code String.equals}; table form: byte-exact on the canonical {@link Td#write} text) →
 * no-op, {@link ReloadResult#unchanged()} — nothing parsed, nothing validated, the store
 * untouched, {@link #revision()} not incremented.
 * <p>
 * (2) Deterministic idempotency (re-entrant): when the new document is byte-identical to the
 * last successfully applied one → unchanged again (the same target delivered twice never takes
 * effect twice). Failures are not remembered, so retrying the same invalid document still reports
 * failed (same input → same output).
 * <p>
 * (3) Atomic apply (apply-or-keep-old): a changed document goes through the {@link RuleStore}
 * load semantics (parse → validate → all-or-nothing merge; see the {@link RuleStore} class
 * javadoc: on violations nothing is loaded) — on violations the raised
 * {@link RuleViolationException} is captured into a {@link ReloadResult#failed}, the store keeps
 * its old state with nothing written, and neither the applied-memory nor the revision is updated;
 * only a passing document merges into the target layer.
 * <p>
 * (4) Monotonic revision: {@link #revision()} is a monotonic {@code long} — +1 per actually
 * applied reload, never incremented by unchanged or failed; no timestamps.
 * <p>
 * Semantics source: applying reuses the {@link RuleStore} load semantics (two-tier config;
 * accumulating across reloads, first occurrence wins — an already-loaded key is never
 * overwritten, and a reload never removes previously loaded rules). Determinism: same sequence →
 * same final state (replay-consistent), no timing, no randomness, no O(n²) (each operation = a
 * constant number of linear scans / O(n log n) validation). Not thread-safe by design
 * (single-threaded deterministic engine; no synchronization primitives).
 */
public final class RuleReloader {

    private final RuleStore store;
    private long revision = 0L;
    private String lastGlobal = null;
    private String lastOverrides = null;

    /**
     * 构造：目标 store 为 null 一律 {@link IllegalArgumentException}。
     * Constructor: a null target store raises {@link IllegalArgumentException}.
     *
     * @param store 待重载的规则存储（不可 null）/ the rule store to reload (never null).
     */
    public RuleReloader(RuleStore store) {
        if (store == null) {
            throw new IllegalArgumentException("store must be non-null");
        }
        this.store = store;
    }

    /**
     * 全局档重载（td 文本形态）。类 javadoc 的四步契约逐条适用；应用经
     * {@link RuleStore#loadGlobalText}（全有或全无并入全局档）。null 文本一律
     * {@link IllegalArgumentException}。
     * Reloads the global layer (td text form). The four-step contract in the class javadoc
     * applies; the application goes through {@link RuleStore#loadGlobalText} (all-or-nothing
     * merge into the global layer). A null text raises {@link IllegalArgumentException}.
     *
     * @param oldText 旧全局档文本（不可 null）/ the old global-document text (never null).
     * @param newText 新全局档文本（不可 null）/ the new global-document text (never null).
     * @return 确定性重载结果 / the deterministic reload result.
     */
    public ReloadResult reloadGlobalText(String oldText, String newText) {
        return reload(oldText, newText, true, () -> store.loadGlobalText(newText));
    }

    /**
     * 全局档重载（td 表形态）：变更检测按 {@link Td#write} 规范文本逐字节比较；应用经
     * {@link RuleStore#loadGlobal}。null 文档一律 {@link IllegalArgumentException}。
     * Reloads the global layer (a td table): change detection compares the canonical
     * {@link Td#write} bytes; the application goes through {@link RuleStore#loadGlobal}. A null
     * document raises {@link IllegalArgumentException}.
     *
     * @param oldDoc 旧全局档根表（不可 null）/ the old global-document root table (never null).
     * @param newDoc 新全局档根表（不可 null）/ the new global-document root table (never null).
     * @return 确定性重载结果 / the deterministic reload result.
     */
    public ReloadResult reloadGlobal(TdTable oldDoc, TdTable newDoc) {
        return reloadTable(oldDoc, newDoc, true, () -> store.loadGlobal(newDoc));
    }

    /**
     * 存档覆盖档重载（td 文本形态）：与 {@link #reloadGlobalText} 同构，应用经
     * {@link RuleStore#loadOverridesText} 并入覆盖档。null 文本一律
     * {@link IllegalArgumentException}。
     * Reloads the save-overrides layer (td text form): isomorphic to
     * {@link #reloadGlobalText}, applied through {@link RuleStore#loadOverridesText} into the
     * overrides layer. A null text raises {@link IllegalArgumentException}.
     *
     * @param oldText 旧覆盖档文本（不可 null）/ the old overrides-document text (never null).
     * @param newText 新覆盖档文本（不可 null）/ the new overrides-document text (never null).
     * @return 确定性重载结果 / the deterministic reload result.
     */
    public ReloadResult reloadOverridesText(String oldText, String newText) {
        return reload(oldText, newText, false, () -> store.loadOverridesText(newText));
    }

    /**
     * 存档覆盖档重载（td 表形态）：与 {@link #reloadGlobal} 同构，应用经
     * {@link RuleStore#loadOverrides}。null 文档一律 {@link IllegalArgumentException}。
     * Reloads the save-overrides layer (a td table): isomorphic to {@link #reloadGlobal},
     * applied through {@link RuleStore#loadOverrides}. A null document raises
     * {@link IllegalArgumentException}.
     *
     * @param oldDoc 旧覆盖档根表（不可 null）/ the old overrides-document root table (never null).
     * @param newDoc 新覆盖档根表（不可 null）/ the new overrides-document root table (never null).
     * @return 确定性重载结果 / the deterministic reload result.
     */
    public ReloadResult reloadOverrides(TdTable oldDoc, TdTable newDoc) {
        return reloadTable(oldDoc, newDoc, false, () -> store.loadOverrides(newDoc));
    }

    /**
     * 单调递增版本号：每次实际 applied 的自增 1；unchanged 与 failed 不自增；初始 0；禁时间戳。
     * The monotonic revision: +1 per actually applied reload, never incremented by unchanged or
     * failed; starts at 0; no timestamps.
     *
     * @return 当前版本号 / the current revision.
     */
    public long revision() {
        return revision;
    }

    private ReloadResult reloadTable(TdTable oldDoc, TdTable newDoc, boolean global, Runnable apply) {
        if (oldDoc == null || newDoc == null) {
            throw new IllegalArgumentException("old and new documents must be non-null");
        }
        return reload(Td.write(oldDoc), Td.write(newDoc), global, apply);
    }

    private ReloadResult reload(String oldText, String newText, boolean global, Runnable apply) {
        if (oldText == null || newText == null) {
            throw new IllegalArgumentException("old and new texts must be non-null");
        }
        if (oldText.equals(newText)) {
            return ReloadResult.unchanged(); // byte-identical → no-op
        }
        String last = global ? lastGlobal : lastOverrides;
        if (newText.equals(last)) {
            return ReloadResult.unchanged(); // deterministic idempotency: same target already applied
        }
        try {
            apply.run();
        } catch (RuleViolationException e) {
            return ReloadResult.failed(e.report(), e.getMessage());
        } catch (IllegalArgumentException e) {
            return ReloadResult.failed(RuleReport.pass(), e.getMessage());
        }
        if (global) {
            lastGlobal = newText;
        } else {
            lastOverrides = newText;
        }
        revision++;
        return ReloadResult.success();
    }

    /**
     * 一次重载的确定性结果（record）。三态互斥：unchanged（{@code changed=false, applied=false}，
     * 无差异 no-op）/ applied（{@code true, true}，已并入目标档）/ failed（{@code true, false}，
     * 检出差异但未应用——store 保持旧状态）。{@code changed()} = 输入层面检出逐字节差异；
     * {@code applied()} = store 实际被更新；{@code failureReason()} 非 null ⟺ failed；
     * {@code report()} = failed 时的完整校验报告（解析失败未产生校验违约时为
     * {@link RuleReport#pass()}），unchanged/applied 时为 pass。紧凑构造拒绝 null 报告。
     * <p>
     * A deterministic reload result (record). The three states are mutually exclusive:
     * unchanged ({@code changed=false, applied=false}, a no-op with no difference) / applied
     * ({@code true, true}, merged into the target layer) / failed ({@code true, false}, a
     * difference was detected but not applied — the store keeps its old state). {@code changed()}
     * = a byte-exact difference was detected at the input level; {@code applied()} = the store
     * was actually updated; {@code failureReason()} non-null ⟺ failed; {@code report()} = the
     * full validation report on failure ({@link RuleReport#pass()} when parsing failed before
     * any validation), pass otherwise. The compact constructor rejects a null report.
     *
     * @param changed       输入层面是否检出差异 / whether a difference was detected at the input level.
     * @param applied       store 是否实际被更新 / whether the store was actually updated.
     * @param report        校验报告 / the validation report.
     * @param failureReason 失败原因（非 null ⟺ failed）/ the failure reason (non-null ⟺ failed).
     */
    public record ReloadResult(boolean changed, boolean applied, RuleReport report, String failureReason) {

        /**
         * 紧凑构造：null 报告一律 {@link IllegalArgumentException}（程序错误）。
         * Compact constructor: a null report raises {@link IllegalArgumentException} (a program
         * error).
         */
        public ReloadResult {
            if (report == null) {
                throw new IllegalArgumentException("report must be non-null");
            }
        }

        /** 无差异 no-op 工厂。The no-difference no-op factory. */
        public static ReloadResult unchanged() {
            return new ReloadResult(false, false, RuleReport.pass(), null);
        }

        /** 成功应用工厂。The successful-apply factory. */
        public static ReloadResult success() {
            return new ReloadResult(true, true, RuleReport.pass(), null);
        }

        /** 失败工厂（store 未写入，保持旧状态）。The failure factory (the store is untouched). */
        public static ReloadResult failed(RuleReport report, String failureReason) {
            return new ReloadResult(true, false, report, failureReason);
        }
    }
}
