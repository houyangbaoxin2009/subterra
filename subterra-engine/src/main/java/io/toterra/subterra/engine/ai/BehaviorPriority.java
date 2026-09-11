package io.toterra.subterra.engine.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic behavior priority arbitration (p.2.24.2): behaviors are
 * registered with an integer priority in fixed insertion order — a duplicate or
 * null/blank name, or a null behavior, is rejected with
 * {@link IllegalArgumentException}, mirroring the fixed-order duplicate
 * rejection of {@link Brain}. {@link #select(List, BrainMemory)} deterministically
 * picks the single behavior to run this tick: candidates are evaluated in
 * priority-descending order with ties broken by registration (i.e. passed-in)
 * order — a stable sort, never by hash or timing — and the first candidate
 * whose {@code start} passes is returned; when none passes {@code null} is
 * returned. No randomness, no timing: identical registrations and identical
 * memory yield identical selections, so arbitration replays tick-for-tick.
 * Pure JDK, no Minecraft code touched.
 *
 * <p>行为优先级裁决（p.2.24.2）：行为以整数优先级按固定插入序注册——重复名、null/空白名
 * 或 null 行为均以 {@link IllegalArgumentException} 拒绝，与 {@link Brain} 的固定序重复拒
 * 语义一致。{@link #select(List, BrainMemory)} 确定性选出本 tick 运行的那个行为：候选按
 * 优先级降序评估、同优先级按注册（即传入）序打破平局——稳定排序，绝不依哈希或时序——返回
 * 首个 {@code start} 通过者；全部不通过时返回 {@code null}。无随机、无时序：注册相同、记忆
 * 相同则选择相同，裁决可逐 tick 回放。纯 JDK，不触碰任何 MC 代码。
 */
public final class BehaviorPriority {

    /**
     * Immutable registration descriptor: a behavior's unique name and its
     * arbitration priority. 不可变注册描述：行为的唯一名与仲裁优先级。
     *
     * @param name     the behavior's unique registration name (non-null, non-blank).
     * @param priority higher wins arbitration; ties are broken by registration order.
     */
    public record BehaviorEntry(String name, int priority) {
        public BehaviorEntry {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("behavior name must be non-null and non-blank");
            }
        }
    }

    /** Fixed insertion order of registered entries; values re-keyed by name. */
    private final Map<String, BehaviorEntry> entries = new LinkedHashMap<>();
    /** The registered behaviors keyed by name, mirroring {@link Brain}'s registry. */
    private final Map<String, BrainBehavior> behaviors = new LinkedHashMap<>();

    /**
     * Registers a behavior with the given priority in insertion order. A null
     * behavior, a null/blank name, or a duplicate name is rejected with
     * {@link IllegalArgumentException}. Deterministic.
     *
     * 以给定优先级按插入序注册行为。null 行为、null/空白名或重复名均以
     * {@link IllegalArgumentException} 拒绝。确定性。
     *
     * @param behavior the behavior to register (its {@code name()} is the key).
     * @param priority the arbitration priority (higher wins).
     * @return {@code this}, for chaining.
     */
    public BehaviorPriority register(BrainBehavior behavior, int priority) {
        if (behavior == null) {
            throw new IllegalArgumentException("behavior must be non-null");
        }
        String name = behavior.name();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("behavior name must be non-null and non-blank");
        }
        if (entries.containsKey(name)) {
            throw new IllegalArgumentException("behavior already registered: " + name);
        }
        entries.put(name, new BehaviorEntry(name, priority));
        behaviors.put(name, behavior);
        return this;
    }

    /**
     * All registered entries as an unmodifiable copy in registration order
     * (deterministic). 全部已注册项，按注册序返回不可变副本（确定性）。
     */
    public List<BehaviorEntry> entries() {
        return List.copyOf(entries.values());
    }

    /**
     * The behavior registered under {@code name}, or {@code null} when the name
     * is not registered. Read-only lookup, deterministic.
     *
     * 注册在 {@code name} 下的行为；未注册时为 {@code null}。只读查找，确定性。
     */
    public BrainBehavior behavior(String name) {
        return behaviors.get(name);
    }

    /**
     * Stable priority-descending sort of the given candidates (ties keep their
     * passed-in — i.e. registration — order). The returned copy leaves the input
     * untouched; the same input always yields the same order. Shared by
     * {@link #select(List, BrainMemory)} and the scheduler's budgeted walk.
     *
     * 对给定候选做稳定优先级降序排序（同优先级保持传入——即注册——序）。返回副本不触碰输入；
     * 同输入恒得同序。{@link #select(List, BrainMemory)} 与调度器的预算遍历共用此排序语义。
     *
     * @param candidates candidates in registration order (unmodifiable input is fine).
     * @return a stable priority-descending copy.
     * @throws NullPointerException if {@code candidates} is null.
     */
    public List<BehaviorEntry> ordered(List<BehaviorEntry> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        List<BehaviorEntry> ordered = new ArrayList<>(candidates);
        ordered.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        return ordered;
    }

    /**
     * Deterministic arbitration over {@code candidates} in registration order:
     * evaluates them priority-descending (ties by registration order, see
     * {@link #ordered(List)}) and returns the first behavior whose {@code start}
     * passes; {@code null} when none passes. Every candidate name must be a
     * registered behavior (unregistered names are rejected with
     * {@link IllegalArgumentException}), keeping the arbitration deterministic.
     *
     * 对按注册序传入的 {@code candidates} 做确定性裁决：按优先级降序（同优先级按注册序，见
     * {@link #ordered(List)}）逐个评估，返回首个 {@code start} 通过的行为；全部不通过时返回
     * {@code null}。每个候选名必须是已注册行为（未注册名以 {@link IllegalArgumentException}
     * 拒绝），保证裁决确定性。
     *
     * @param candidates the candidate entries in registration order.
     * @param memory     the shared brain memory read by {@code start}.
     * @return the selected behavior, or {@code null} if none passed.
     * @throws NullPointerException     if either argument is null.
     * @throws IllegalArgumentException if a candidate name is not registered.
     */
    public BrainBehavior select(List<BehaviorEntry> candidates, BrainMemory memory) {
        return select(candidates, memory, Integer.MAX_VALUE);
    }

    /**
     * Budgeted arbitration: like {@link #select(List, BrainMemory)} but evaluates
     * at most {@code maxEvaluations} candidates in priority-descending order and
     * deterministically skips the remaining ones. A {@code maxEvaluations <= 0}
     * budget selects nothing. This is the budgeted surface the scheduler drives
     * with its per-tick behavior cap.
     *
     * 带预算的裁决：同 {@link #select(List, BrainMemory)}，但最多按优先级降序评估
     * {@code maxEvaluations} 个候选，并按序确定性跳过剩余候选。{@code maxEvaluations <= 0}
     * 时不选中任何行为。这是调度器以其每 tick 行为上限驱动的带预算表面。
     *
     * @param candidates      the candidate entries in registration order.
     * @param memory          the shared brain memory read by {@code start}.
     * @param maxEvaluations  the maximum number of candidates to evaluate (>= 0);
     *                        remaining candidates are skipped in order.
     * @return the selected behavior, or {@code null} if none passed within budget.
     * @throws NullPointerException     if either object argument is null.
     * @throws IllegalArgumentException if a candidate name is not registered.
     */
    public BrainBehavior select(List<BehaviorEntry> candidates, BrainMemory memory, int maxEvaluations) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(memory, "memory");
        int evaluated = 0;
        for (BehaviorEntry entry : ordered(candidates)) {
            if (evaluated >= maxEvaluations) {
                break; // budget exhausted: deterministically skip the remaining candidates
            }
            evaluated++;
            BrainBehavior behavior = behaviors.get(entry.name());
            if (behavior == null) {
                throw new IllegalArgumentException("no behavior registered for priority entry: " + entry.name());
            }
            if (behavior.start(memory)) {
                return behavior;
            }
        }
        return null;
    }
}
