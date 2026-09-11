package io.toterra.subterra.engine.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic Brain tick scheduler (p.2.24.2): a fixed-order orchestration
 * shell over {@link Brain} that adds priority arbitration
 * ({@link BehaviorPriority}) and per-tick phase budgets. Sensors, behaviors and
 * tasks are registered in fixed insertion order with the same duplicate /
 * null / blank-name rejection as {@link Brain}; {@link #tick(Brain)} then
 * advances one tick in fixed order — sense (registered sensors write the
 * brain's memory) → priority arbitration {@code select} → the selected
 * behavior's {@code tick} → task execution — with each phase bounded by the
 * configured budget, which deterministically skips the remaining entries in
 * registration order once a phase cap is exhausted.
 *
 * <p>The scheduler is stateless across ticks (budget counters are per-tick
 * locals), so the same registration, the same budget and the same input memory
 * yield byte-identical outputs — a brain can be replayed tick-for-tick, and two
 * identical runs land in identical terminal states. Pure JDK, no Minecraft code
 * touched.
 *
 * <p>Budget semantics are <em>aligned</em> with — not dependent on —
 * {@code engine.sim.budget} (p.2.8): like
 * {@link io.toterra.subterra.engine.sim.budget.TickBudget} the caps form an
 * immutable three-slot snapshot validated {@code >= 1} at construction, all
 * decisions are pure functions of current state, and over-cap tests never wrap.
 * The dimension differs deliberately: p.2.8 tiers are per-simulant / per-region
 * / global units, whereas the scheduler's caps count phase executions
 * (sensors run / behaviors evaluated / tasks run) per tick — a phase execution
 * is the unit, and once a cap is reached the remaining phase entries are
 * skipped in registration order.
 *
 * <p>确定性 Brain tick 调度器（p.2.24.2）：在 {@link Brain} 之上的固定序编排壳，加入优先级
 * 裁决（{@link BehaviorPriority}）与每 tick 分阶段预算。感知器、行为与任务按固定插入序注册，
 * 采用与 {@link Brain} 相同的重复/null/空白名拒绝；{@link #tick(Brain)} 按固定序推进一个
 * tick——感知（已注册感知器写入大脑记忆）→ 优先级裁决 {@code select} → 选中行为的
 * {@code tick} → 任务执行——各阶段受配置预算约束，一旦阶段上限耗尽，剩余条目按注册序
 * 确定性跳过。
 *
 * <p>调度器跨 tick 无状态（预算计数为每 tick 局部量），因此注册相同、预算相同、输入记忆相同
 * 则输出逐字节一致——Brain 可逐 tick 回放，两遍相同运行落在相同终态。纯 JDK，不触碰任何 MC
 * 代码。
 *
 * <p>预算语义与 {@code engine.sim.budget}（p.2.8）<em>对齐</em>而非依赖：与
 * {@link io.toterra.subterra.engine.sim.budget.TickBudget} 相同，各上限构成构造时校验
 * {@code >= 1} 的不可变三槽快照，所有决策都是当前状态的纯函数，超限判断不回绕。维度刻意不同：
 * p.2.8 的 tier 是 per-simulant / per-region / global 单位，而本调度器的上限以阶段执行次数
 * （运行的感知器 / 评估的行为 / 运行的任务）为每 tick 单位——阶段执行即单位，上限达到后该阶段
 * 剩余条目按注册序跳过。
 */
public final class BrainScheduler {

    /**
     * Immutable three-slot per-tick phase budget, semantics-aligned with
     * {@link io.toterra.subterra.engine.sim.budget.TickBudget} (p.2.8): every cap
     * must be {@code >= 1} (rejected at {@link #of(int, int, int)}), each cap is
     * an execution count per tick, and over-cap tests never wrap.
     *
     * 不可变三槽每 tick 分阶段预算，语义对齐
     * {@link io.toterra.subterra.engine.sim.budget.TickBudget}（p.2.8）：每项上限必须
     * {@code >= 1}（在 {@link #of(int, int, int)} 处拒绝），每项上限是每 tick 的执行次数，
     * 超限判断不回绕。
     *
     * @param senseUnits     max sensors run per tick ({@code >= 1}).
     * @param behaviorUnits  max behavior candidates evaluated per tick ({@code >= 1}).
     * @param taskUnits      max tasks run per tick ({@code >= 1}).
     */
    public record TickBudget(int senseUnits, int behaviorUnits, int taskUnits) {

        /**
         * Creates a budget with the given caps.
         *
         * @param senseUnits    the per-tick sensor-execution cap; must be {@code >= 1}.
         * @param behaviorUnits the per-tick behavior-evaluation cap; must be {@code >= 1}.
         * @param taskUnits     the per-tick task-execution cap; must be {@code >= 1}.
         * @return a new immutable {@link TickBudget}.
         * @throws IllegalArgumentException if any cap is {@code < 1}.
         */
        public static TickBudget of(int senseUnits, int behaviorUnits, int taskUnits) {
            if (senseUnits < 1) {
                throw new IllegalArgumentException("senseUnits must be >= 1: " + senseUnits);
            }
            if (behaviorUnits < 1) {
                throw new IllegalArgumentException("behaviorUnits must be >= 1: " + behaviorUnits);
            }
            if (taskUnits < 1) {
                throw new IllegalArgumentException("taskUnits must be >= 1: " + taskUnits);
            }
            return new TickBudget(senseUnits, behaviorUnits, taskUnits);
        }
    }

    /**
     * Immutable per-tick outcome of {@link #tick(Brain)}: the selected behavior
     * name ({@code null} when none passed) and the phase execution counts.
     *
     * {@link #tick(Brain)} 的不可变每 tick 结果：选中的行为名（无选中时为 {@code null}）与
     * 各阶段执行计数。
     *
     * @param selectedBehavior    the selected behavior's name, or {@code null} if none was selected.
     * @param sensorsRun          number of sensors executed this tick.
     * @param behaviorsEvaluated  number of behavior candidates evaluated (start calls) this tick.
     * @param tasksRun            number of tasks executed this tick.
     */
    public record TickResult(String selectedBehavior, int sensorsRun, int behaviorsEvaluated, int tasksRun) {
    }

    /** Default budget: effectively unbounded per-phase caps. 默认预算：各阶段近乎无上限。 */
    private static final TickBudget DEFAULT_BUDGET = TickBudget.of(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);

    private final Map<String, BrainSensor> sensors = new LinkedHashMap<>();
    private final Map<String, BrainTask> tasks = new LinkedHashMap<>();
    private final BehaviorPriority priorities = new BehaviorPriority();
    private final TickBudget budget;

    /**
     * Creates a scheduler with effectively unbounded per-phase caps.
     * 创建各阶段近乎无上限的调度器。
     */
    public BrainScheduler() {
        this(DEFAULT_BUDGET);
    }

    /**
     * Creates a scheduler with the given per-tick phase budget.
     *
     * @param budget the per-tick phase caps; must be non-null.
     * @throws NullPointerException if {@code budget} is null.
     */
    public BrainScheduler(TickBudget budget) {
        this.budget = Objects.requireNonNull(budget, "budget");
    }

    /**
     * The configured per-tick phase budget. 配置的每 tick 分阶段预算。
     */
    public TickBudget tickBudget() {
        return budget;
    }

    /**
     * Registers a sensor in insertion order. A null/blank name, a null sensor or
     * a duplicate name is rejected with {@link IllegalArgumentException}
     * (registration semantics aligned with {@link Brain#registerSensor}).
     *
     * 按插入序注册感知器。null/空白名、null 实例或重复名均以
     * {@link IllegalArgumentException} 拒绝（注册语义与 {@link Brain#registerSensor} 对齐）。
     *
     * @param sensor the sensor to register.
     * @return {@code this}, for chaining.
     */
    public BrainScheduler registerSensor(BrainSensor sensor) {
        if (sensor == null) {
            throw new IllegalArgumentException("sensor must be non-null");
        }
        requireNewName(sensors, sensor.name(), "sensor");
        sensors.put(sensor.name(), sensor);
        return this;
    }

    /**
     * Registers a behavior with the given priority in insertion order. A
     * null/blank name, a null behavior or a duplicate name is rejected with
     * {@link IllegalArgumentException}; the priority is arbitrated by the
     * scheduler's {@link BehaviorPriority}.
     *
     * 以给定优先级按插入序注册行为。null/空白名、null 行为或重复名均以
     * {@link IllegalArgumentException} 拒绝；优先级由调度器内部的
     * {@link BehaviorPriority} 仲裁。
     *
     * @param behavior the behavior to register.
     * @param priority the arbitration priority (higher wins).
     * @return {@code this}, for chaining.
     */
    public BrainScheduler registerBehavior(BrainBehavior behavior, int priority) {
        if (behavior == null) {
            throw new IllegalArgumentException("behavior must be non-null");
        }
        priorities.register(behavior, priority);
        return this;
    }

    /**
     * Registers a task in insertion order. A null/blank name, a null task or a
     * duplicate name is rejected with {@link IllegalArgumentException}
     * (registration semantics aligned with {@link Brain#registerTask}).
     *
     * 按插入序注册任务。null/空白名、null 实例或重复名均以
     * {@link IllegalArgumentException} 拒绝（注册语义与 {@link Brain#registerTask} 对齐）。
     *
     * @param task the task to register.
     * @return {@code this}, for chaining.
     */
    public BrainScheduler registerTask(BrainTask task) {
        if (task == null) {
            throw new IllegalArgumentException("task must be non-null");
        }
        requireNewName(tasks, task.name(), "task");
        tasks.put(task.name(), task);
        return this;
    }

    /**
     * Advances one deterministic tick over the given brain's shared memory, in
     * fixed order: sense (registered sensors write the memory in registration
     * order, up to the sense cap) → priority arbitration (candidates evaluated
     * priority-descending with ties by registration order, up to the behavior
     * cap; remaining candidates skipped in order) → the selected behavior's
     * {@code tick} → task execution (in registration order, up to the task cap).
     * The scheduler keeps no cross-tick state, so identical registration, budget
     * and inputs yield identical outputs and the run replays tick-for-tick.
     *
     * 在给定大脑的共享记忆上按固定序推进一个确定性 tick：感知（已注册感知器按注册序写记忆，
     * 至感知上限）→ 优先级裁决（候选按优先级降序评估、同优先级按注册序，至行为上限；剩余候选
     * 按序跳过）→ 选中行为的 {@code tick} → 任务执行（按注册序，至任务上限）。调度器不保留跨
     * tick 状态，因此注册、预算与输入相同则输出相同，运行可逐 tick 回放。
     *
     * @param brain the brain whose memory is sensed, read and executed against
     *              (non-null).
     * @return the immutable per-tick outcome ({@link TickResult}).
     * @throws NullPointerException if {@code brain} is null.
     */
    public TickResult tick(Brain brain) {
        Objects.requireNonNull(brain, "brain");
        var memory = brain.memory();

        int sensorsRun = 0;
        for (BrainSensor sensor : sensors.values()) {
            if (sensorsRun >= budget.senseUnits()) {
                break; // sense cap reached: deterministically skip the remaining sensors
            }
            sensor.sense(memory);
            sensorsRun++;
        }

        int behaviorsEvaluated = 0;
        BrainBehavior selected = null;
        for (var entry : priorities.ordered(priorities.entries())) {
            if (behaviorsEvaluated >= budget.behaviorUnits()) {
                break; // behavior cap reached: skip the remaining candidates in order
            }
            behaviorsEvaluated++;
            BrainBehavior behavior = priorities.behavior(entry.name());
            if (behavior == null) {
                throw new IllegalArgumentException("no behavior registered for priority entry: " + entry.name());
            }
            if (behavior.start(memory)) {
                selected = behavior;
                break;
            }
        }
        if (selected != null) {
            selected.tick(memory);
        }

        int tasksRun = 0;
        for (BrainTask task : tasks.values()) {
            if (tasksRun >= budget.taskUnits()) {
                break; // task cap reached: deterministically skip the remaining tasks
            }
            task.run(memory);
            tasksRun++;
        }

        return new TickResult(selected == null ? null : selected.name(), sensorsRun, behaviorsEvaluated, tasksRun);
    }

    /** Rejects a null/blank or already-present registration name — fixed order,
     *  deterministic duplicate rejection (aligned with {@link Brain}).
     *  拒绝 null/空白或已存在的注册名——固定序、确定性重复拒（与 {@link Brain} 对齐）。 */
    private static void requireNewName(Map<?, ?> registry, String name, String kind) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(kind + " name must be non-null and non-blank");
        }
        if (registry.containsKey(name)) {
            throw new IllegalArgumentException(kind + " already registered: " + name);
        }
    }
}
