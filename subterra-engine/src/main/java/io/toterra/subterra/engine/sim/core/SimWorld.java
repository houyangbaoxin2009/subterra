package io.toterra.subterra.engine.sim.core;

import io.toterra.subterra.engine.worldgen.pipeline.noise.XoroRandom;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A final, deterministic simulation world (p.2.8.3) over a set of independent
 * simulants keyed by {@link SimKey}. It is the incremental core of
 * {@code engine.sim}: rather than recomputing every simulant every tick, it
 * advances <em>only the changed locals</em> (a dirty set) and returns the diff,
 * while leaving untouched simulants byte-for-byte unchanged. Under the
 * {@link Simulator}/{@link PerSimRandom} purity contract the whole result is
 * deterministic and order-independent: {@code advanceAll()} is byte-identical to
 * {@code advance(keys() in any order)}, and replaying the recorded per-tick
 * diffs against the same dirty plan reproduces a full recompute byte-for-byte.
 *
 * <p><b>Advance semantics (diff)</b> {@link #advance} internally advances the
 * world tick by exactly one ({@code newTick = tickNo + 1}) and, for each given key,
 * computes {@code next = sim.compute(newTick, key,
 * PerSimRandom.forTick(worldSeed, newTick, key), currentState)}. Because
 * {@code compute} is a pure function and {@code forTick} a pure seed factory, each
 * key's result depends only on (worldSeed, the key's own lived-tick count, its
 * initial state) and is independent of the batch's iteration order. The returned
 * map contains <em>only</em> the newly computed states of the advanced keys; a
 * key not advanced keeps its previous state, and neither it nor absent keys appear
 * in the return map. Keys that are not present in the world are ignored (never
 * added, never advanced). Duplicate keys in the input are advanced once.
 *
 * <p><b>Writable-cell assumption:</b> {@link #add} allows re-adding an existing
 * key (overwrite) as a way to (re)seed a simulant deterministically; {@link
 * #advanceAll} is defined as {@code advance(keys())} and is thus equivalent to the
 * full path.
 *
 * <p><b>一个确定性的模拟世界</b>（p.2.8.3），承载按 {@link SimKey} 索引的一组相互独立
 * 的 simulant。它是 {@code engine.sim} 的增量核心：并不每 tick 重算全部 simulant，
 * 而是只推进<em>发生变化的局部</em>（脏集）并返回 diff，同时让未推进的 simulant
 * 逐字节保持不变。在 {@link Simulator}/{@link PerSimRandom} 纯度契约下，整体结果
 * 确定且顺序无关：{@code advanceAll()} 与任何乱序的 {@code advance(keys())} 逐字节
 * 一致；按同一脏计划重放记录的逐 tick diff 可逐字节复现全量重算。
 *
 * <p><b>推进语义（diff）：</b> {@link #advance} 内部将世界 tick 恰好推进一
 * （{@code newTick = tickNo + 1}），对每个给定键计算 {@code next =
 * sim.compute(newTick, key, PerSimRandom.forTick(worldSeed, newTick, key),
 * currentState)}。因 {@code compute} 为纯函数、{@code forTick} 为纯种子工厂，每个键的
 * 结果仅取决于（世界种子、该键自身已历 tick 数、其初始状态），与同批迭代序无关。
 * 返回的 map 仅含被推进键的新状态；未推进的键保持原状且不出现在返回 map 中；世界里
 * 不存在的键被忽略（既不新增也不推进）；输入中的重复键只推进一次。
 *
 * <p><b>可写单元格假设：</b> {@link #add} 允许对已存在键重复添加（覆盖），用于确定性地
 * （重新）植种某 simulant；{@link #advanceAll} 定义为 {@code advance(keys())}，因此
 * 即全量路径的等价实现。
 */
public final class SimWorld<S> {

    private final Simulator<S> sim;
    private final long worldSeed;
    private long tickNo;
    private final Map<SimKey, S> states;

    private SimWorld(Simulator<S> sim, long worldSeed, long tickNo) {
        this.sim = sim;
        this.worldSeed = worldSeed;
        this.tickNo = tickNo;
        this.states = new TreeMap<>();
    }

    /**
     * Creates an empty {@link SimWorld} with a fixed world seed, starting at the
     * given tick. Simulants are added afterwards via {@link #add}.
     *
     * @param sim       the pure per-simulant tick function.
     * @param worldSeed the master world seed (drives every {@link PerSimRandom}).
     * @param tickNo    the tick the world starts at.
     * @param <S>       the state type.
     * @return a new, empty {@link SimWorld}.
     *
     * @return 以固定世界种子、自给定 tick 起始的空 {@link SimWorld}。之后用
     *         {@link #add} 添加 simulant。
     */
    public static <S> SimWorld<S> create(Simulator<S> sim, long worldSeed, long tickNo) {
        return new SimWorld<>(sim, worldSeed, tickNo);
    }

    /**
     * Adds (or, if the key already exists, overwrites) the simulant's state.
     *
     * @param key   the simulant identity.
     * @param state the simulant's initial/current state.
     * @return {@code this} for chaining.
     *
     * @return {@code this} 以支持链式调用。
     */
    public SimWorld<S> add(SimKey key, S state) {
        states.put(key, state);
        return this;
    }

    /**
     * Returns the current state of the given key, or {@code null} if absent.
     *
     * @param key the simulant identity.
     * @return the simulant's current state or {@code null}.
     *
     * @return 给定键的当前状态，不存在时返回 {@code null}。
     */
    public S state(SimKey key) {
        return states.get(key);
    }

    /**
     * Whether the given key is present in this world.
     *
     * @param key the simulant identity.
     * @return {@code true} if the key is present.
     *
     * @return 该键是否存在于本世界。
     */
    public boolean contains(SimKey key) {
        return states.containsKey(key);
    }

    /**
     * All keys in canonical natural order ({@link SimKey#compareTo}).
     *
     * @return the keys, sorted ascending.
     *
     * @return 按自然序升序排列的全部键。
     */
    public List<SimKey> keys() {
        return new ArrayList<>(states.keySet()); // TreeMap -> ascending
    }

    /**
     * The number of simulants in this world.
     *
     * @return the simulant count.
     *
     * @return 本世界的 simulant 数。
     */
    public int size() {
        return states.size();
    }

    /**
     * The master world seed this world was created with.
     *
     * @return the world seed.
     *
     * @return 创建本世界所用的世界种子。
     */
    public long worldSeed() {
        return worldSeed;
    }

    /**
     * The world's current tick (advances by exactly one per {@link #advance}/
     * {@link #advanceAll} call).
     *
     * @return the current tick number.
     *
     * @return 当前 tick 号（每次 {@link #advance}/{@link #advanceAll} 恰好 +1）。
     */
    public long tickNo() {
        return tickNo;
    }

    /**
     * Advances exactly the given keys by exactly one tick. The world tick becomes
     * {@code tickNo + 1}; each key's new random is {@link PerSimRandom#forTick(
     * worldSeed, newTick, key)}. Only the advanced (and present) keys' new states
     * are returned (the diff); non-advanced keys keep their previous state
     * byte-for-byte and never appear in the result. Result is independent of the
     * input iteration order and of any other key advanced in the same batch.
     * Keys not present in the world are ignored; duplicates advance once.
     *
     * @param keys the dirty set — the keys to advance.
     * @return a map from advanced key to its new state (the diff).
     *
     * @return 恰好推进给定 keys 一个 tick 后，由被推进键指向其新状态的 map（即 diff）。
     */
    public Map<SimKey, S> advance(Collection<SimKey> keys) {
        long newTick = tickNo + 1;
        Map<SimKey, S> result = new LinkedHashMap<>();
        Set<SimKey> seen = new HashSet<>();
        for (SimKey key : keys) {
            if (key == null || !seen.add(key) || !states.containsKey(key)) {
                continue; // absent / duplicate: never advanced, never added
            }
            XoroRandom rand = PerSimRandom.forTick(worldSeed, newTick, key);
            S next = sim.compute(newTick, key, rand, states.get(key));
            states.put(key, next);
            result.put(key, next);
        }
        tickNo = newTick;
        return result;
    }

    /**
     * Full path: advances every present key by exactly one tick, equivalent to the
     * {@code advance(keys())} full path (see {@link #advance}). Returns the diff of
     * all worlds' new states.
     *
     * @return a map from every key to its new state.
     *
     * @return 全量路径：将每个存在的键推进一个 tick，等价于 {@code advance(keys())}。
     *         返回全体键到其新状态的 diff map。
     */
    public Map<SimKey, S> advanceAll() {
        return advance(keys());
    }
}