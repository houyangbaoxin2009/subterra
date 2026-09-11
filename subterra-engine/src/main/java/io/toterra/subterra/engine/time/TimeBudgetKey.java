// Self-contained engine.time tick-budget key (p.2.26.3). Pure JDK; no
// net.minecraft / net.neoforged dependency. Reuses the p.2.8 sim.budget
// accounting semantics (fixed deterministic order) by delegation.
package io.toterra.subterra.engine.time;

import java.util.Objects;

/**
 * Deterministic identity of a time-budget key (p.2.26.3): the pair of a fixed
 * {@link TimeDomain} and a simulant id ({@code domain, simulantId}). Natural order
 * is by {@link TimeDomain} enumeration order ({@code GLOBAL, ENTITY, ZONE, PLAYER})
 * first, then by ascending {@code simulantId}. Because {@code TimeDomain} is an
 * {@link Enum} (comparable by {@code ordinal()}), a {@link java.util.TreeSet} or
 * {@link java.util.TreeMap} keyed by these records iterates in that fixed domain
 * order — deterministically, regardless of build, registration, thread or
 * wall-clock order. This is the fixed key order that
 * {@link TimeBudgetScheduler#schedule(long)} emits in.
 *
 * <p>Eligibility for execution is decided by {@link TimeBudgetScheduler}, which
 * reuses the {@code engine.sim.budget} (p.2.8) accounting kernel: a key is charged
 * against its {@link TimeDomain}'s per-domain budget pool and included only if the
 * charge is accepted within the cap. Over-budget keys are deterministically
 * deferred (skipped), aligning with p.2.8's atomic {@code ACCEPTED}/{@code DEFERRED}
 * semantics.
 *
 * <p>时间预算键的确定性身份（p.2.26.3）：固定 {@link TimeDomain} 与 simulant 编号的二元组
 * （{@code domain, simulantId}）。自然序先按 {@link TimeDomain} 枚举序
 * （{@code GLOBAL, ENTITY, ZONE, PLAYER}），再按升序 {@code simulantId}。由于
 * {@code TimeDomain} 是 {@link Enum}（按 {@code ordinal()} 可比较），以本 record 为键的
 * {@link java.util.TreeSet}/{@link java.util.TreeMap} 恒按该固定域序迭代——不论构建、注册、线程或
 * 墙钟顺序如何，完全确定。这正是 {@link TimeBudgetScheduler#schedule(long)} 输出所用的固定键序。
 *
 * <p>是否可执行由 {@link TimeBudgetScheduler} 判定，其复用 {@code engine.sim.budget}（p.2.8）
 * 记账内核：某键会被按其 {@link TimeDomain} 的按域预算池计费，仅当该次计费落在上限之内才被纳入；
 * 超预算键被确定性地推迟（跳过），与 p.2.8 的原子 {@code ACCEPTED}/{@code DEFERRED} 语义对齐。
 *
 * @param domain     the fixed time domain of the budget key.
 * @param simulantId the simulant id within that domain.
 */
public record TimeBudgetKey(TimeDomain domain, long simulantId) implements Comparable<TimeBudgetKey> {

    /**
     * Compares by {@link TimeDomain} enumeration order, then by ascending
     * {@code simulantId} — the single canonical, fixed domain-first ordering used by
     * {@link TimeBudgetScheduler}. Deterministic.
     * / 先按 {@link TimeDomain} 枚举序、再按升序 {@code simulantId} 比较——即
     * {@link TimeBudgetScheduler} 采用的唯一规范「域优先」固定序。确定性。
     */
    @Override
    public int compareTo(TimeBudgetKey other) {
        Objects.requireNonNull(other, "other");
        int c = Integer.compare(domain.ordinal(), other.domain().ordinal());
        return c != 0 ? c : Long.compare(simulantId, other.simulantId());
    }
}