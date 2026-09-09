// Self-contained clean-room implementation of the p.2.8.1 tick-budget tiering
// contract (engine.sim.budget). No net.minecraft / net.neoforged dependency.
package io.toterra.subterra.engine.sim.budget;

/**
 * Immutable snapshot of the three per-tick budget caps consumed by
 * {@link BudgetScheduler}. Each cap is expressed in arbitrary integer "units":
 * a positive, {@code long} valued count of cost that a simulant charges per tick.
 *
 * <p>All three caps must be {@code >= 1} (a {@code 0} cap is rejected at
 * {@link #of(long, long, long)} construction time), so every cap is a meaningful
 * non-empty tier.
 *
 * <p>p.2.8.1 {@link BudgetScheduler} 消费的三项每 tick 预算上限不可变快照。每项上限以任意
 * 整数"单位"表达：一个正的 {@code long} 值，表示 simulant 每 tick 计费的成本数量。
 *
 * <p>三项上限都必须 {@code >= 1}（{@code 0} 上限在 {@link #of(long, long, long)}
 * 构造时被拒绝），从而每层预算都是非空的、有意义的 tier。
 *
 * @param simulantUnits the per-simulant cap ({@code >= 1}).
 * @param regionUnits   the per-region cap ({@code >= 1}).
 * @param globalUnits   the per-tick global cap ({@code >= 1}).
 */
public final class TickBudget {

    private final long simulantUnits;
    private final long regionUnits;
    private final long globalUnits;

    private TickBudget(long simulantUnits, long regionUnits, long globalUnits) {
        this.simulantUnits = simulantUnits;
        this.regionUnits = regionUnits;
        this.globalUnits = globalUnits;
    }

    /**
     * Creates a budget with the given caps.
     *
     * @param simulantUnits the per-simulant cap; must be {@code >= 1}.
     * @param regionUnits   the per-region cap; must be {@code >= 1}.
     * @param globalUnits   the per-tick global cap; must be {@code >= 1}.
     * @return a new immutable {@link TickBudget}.
     * @throws IllegalArgumentException if any cap is {@code < 1}.
     */
    public static TickBudget of(long simulantUnits, long regionUnits, long globalUnits) {
        if (simulantUnits < 1) {
            throw new IllegalArgumentException("simulantUnits must be >= 1: " + simulantUnits);
        }
        if (regionUnits < 1) {
            throw new IllegalArgumentException("regionUnits must be >= 1: " + regionUnits);
        }
        if (globalUnits < 1) {
            throw new IllegalArgumentException("globalUnits must be >= 1: " + globalUnits);
        }
        return new TickBudget(simulantUnits, regionUnits, globalUnits);
    }

    /**
     * @return the per-simulant cap ({@code >= 1}).
     */
    public long simulantUnits() {
        return simulantUnits;
    }

    /**
     * @return the per-region cap ({@code >= 1}).
     */
    public long regionUnits() {
        return regionUnits;
    }

    /**
     * @return the per-tick global cap ({@code >= 1}).
     */
    public long globalUnits() {
        return globalUnits;
    }
}