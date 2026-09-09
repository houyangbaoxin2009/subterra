// Self-contained clean-room implementation of the p.2.8.1 tick-budget tiering
// contract (engine.sim.budget). No net.minecraft / net.neoforged dependency.
package io.toterra.subterra.engine.sim.budget;

/**
 * The three budget tiers of {@link BudgetScheduler}. Each charge is accounted
 * against all three tiers atomically: the per-simulant cap, the per-region cap,
 * and the per-tick global cap.
 *
 * <p>p.2.8.1 三级预算等级。每次 charge 都会三 tier 同时记账地核对：
 * per-simulant 上限、per-region 上限、以及本 tick 的全局上限。
 */
public enum BudgetTier {

    /** Cap on the cumulative units charged this tick to a single simulant key. */
    SIMULANT,

    /** Cap on the cumulative units charged this tick to a single {@link RegionKey}. */
    REGION,

    /** Cap on the cumulative units charged this tick across every simulant/region. */
    GLOBAL
}