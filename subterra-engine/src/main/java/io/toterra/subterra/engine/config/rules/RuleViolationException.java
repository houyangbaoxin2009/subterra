package io.toterra.subterra.engine.config.rules;

/**
 * p.2.17.2 装载违约异常 —— {@link RuleStore} 装载系列（{@code loadGlobal/loadGlobalText/
 * loadOverrides/loadOverridesText}）经 {@link RuleValidator#validate} 发现违约时抛出，携带完整
 * 校验报告 {@link #report()}（含全部违约的固定序列表）。{@code RuntimeException}，纯 JDK；
 * 契约 = 全有或全无：违约时不装载任何条目，异常携带报告供调用方展示/回滚。仅由装载路径抛出，
 * {@link RuleStore#validate()} 以返回值形式报告（不抛）。
 * <p>
 * p.2.17.2 load-violation exception — thrown by the {@link RuleStore} load family
 * ({@code loadGlobal/loadGlobalText/loadOverrides/loadOverridesText}) when
 * {@link RuleValidator#validate} finds violations; it carries the full validation report
 * {@link #report()} (the fixed-order violation list). A {@code RuntimeException}, pure JDK; the
 * contract is all-or-nothing: on violations nothing is loaded, and the exception carries the
 * report for the caller to surface or roll back. Only the load path throws it —
 * {@link RuleStore#validate()} reports by return value instead.
 */
public final class RuleViolationException extends RuntimeException {

    private final RuleReport report;

    /**
     * 构造：{@code report} 为 null 一律 {@link IllegalArgumentException}（程序错误）。
     * Constructor: a null {@code report} raises {@link IllegalArgumentException} (a program
     * error).
     *
     * @param report 完整校验报告（不可 null）/ the full validation report (never null).
     */
    public RuleViolationException(RuleReport report) {
        super("rule document failed validation");
        if (report == null) {
            throw new IllegalArgumentException("report must be non-null");
        }
        this.report = report;
    }

    /**
     * 装载失败的完整校验报告（违约固定序，见 {@link RuleReport}）。
     * The full validation report of the failed load (violations in fixed order, see
     * {@link RuleReport}).
     *
     * @return 不可变校验报告 / the immutable validation report.
     */
    public RuleReport report() {
        return report;
    }
}
