package io.toterra.subterra.engine.sim.core;

import io.toterra.subterra.engine.worldgen.pipeline.noise.XoroRandom;

/**
 * The per-simulant <em>pure tick function</em> (p.2.8.3) that {@link SimWorld}
 * applies to advance one simulant by exactly one tick. {@code compute} must be a
 * pure function of its four inputs — {@code tickNo}, {@code key}, the per-tick
 * {@code rand}, and the simulant's previous {@code state} — and must not close
 * over or mutate any shared mutable state. Because {@link PerSimRandom#forTick}
 * makes {@code rand} a pure function of {@code (worldSeed, tick, key)}, the
 * returned state for a given simulant depends only on (world seed, how many ticks
 * that simulant has lived, initial state) — never on which other simulants advance
 * in the same batch or in what order. This is the property the incremental
 * diff/replay contract relies on.
 *
 * <p>Probes should treat {@code state} as a pure input (in particular the probe
 * here uses immutable {@code record} states) so that recomputation is fully
 * reproducible.
 *
 * <p>每 simulant 的<em>纯 tick 函数</em>（p.2.8.3），由{@link SimWorld}用以将一个
 * simulant 恰好推进一个 tick。{@code compute} 必须是对其四个入参——{@code tickNo}、
 * {@code key}、每 tick 的 {@code rand} 与该 simulant 的前一 {@code state}——的纯函数，
 * 且不得捕获或改动任何共享易变状态。因 {@link PerSimRandom#forTick} 使 {@code rand}
 * 成为 {@code (worldSeed, tick, key)} 的纯函数，故对给定 simulant 的返回状态仅取决于
 * （世界种子、该 simulant 已历 tick 数、初始状态）——绝不取决于同批推进的其他
 * simulant 或其顺序。这正是增量 diff/回放契约所依赖的性质。
 *
 * <p>探针应将 {@code state} 视为纯入参（本探针即使用不可变 {@code record} 状态），
 * 使重算完全可复现。
 *
 * @param <S> the simulant state type (must be usable as a pure function input).
 */
@FunctionalInterface
public interface Simulator<S> {

    /**
     * Advances {@code state} by one tick for {@code key} using the strictly
     * per-simulant {@code rand}. Must be a pure function of its inputs and must
     * not touch shared mutable state.
     *
     * @param tickNo the destination tick (the world's current tick + 1).
     * @param key    the simulant being advanced.
     * @param rand   the strictly per-simulant random for this tick (= {@link
     *               PerSimRandom#forTick(worldSeed, tickNo, key)}).
     * @param state  the simulant's previous state.
     * @return the simulant's new state.
     *
     * @return 用严格 per-simulant 的 {@code rand} 将 {@code state} 对 {@code key}
     *         推进一个 tick 后的新状态。必须是对入参的纯函数，且不得改动共享易变状态。
     */
    S compute(long tickNo, SimKey key, XoroRandom rand, S state);
}