package io.toterra.subterra.engine.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic brain orchestration core (p.2.24.1): sensors, behaviors and
 * tasks are registered in fixed insertion order (a duplicate or null/blank
 * name, or a null instance, is rejected with
 * {@link IllegalArgumentException}); {@link #sense()} runs sensors in
 * registration order writing into the shared {@link BrainMemory};
 * {@link #tick()} orchestrates in fixed order — sense (sensors write memory)
 * → decide (each behavior's {@code start} reads memory and, when it returns
 * {@code true}, its {@code tick} acts) → execute (each task's {@code run} acts
 * from memory) — with no randomness and no timing: identical registration and
 * identical inputs yield identical outputs, so a brain can be replayed
 * tick-for-tick. Pure JDK, no Minecraft code touched.
 *
 * <p>确定性 Brain 编排核心（p.2.24.1）：感知器、行为与任务按固定插入序注册（重复名、
 * null/空白名或 null 实例均以 {@link IllegalArgumentException} 拒绝）；
 * {@link #sense()} 按注册序运行感知器，写入共享 {@link BrainMemory}；
 * {@link #tick()} 按固定序编排——感知（感知器写记忆）→ 决策（各行为 {@code start}
 * 读记忆，返回 {@code true} 时其 {@code tick} 行动）→ 执行（各任务 {@code run}
 * 依记忆行动）——无随机、无时序：注册相同、输入相同则输出逐字节相同，可逐 tick 回放。
 * 纯 JDK，不触碰任何 MC 代码。
 *
 * <p>Model reference only (clean-room, zero code included): the layered
 * memory/sensor/behavior/task orchestration model of the vanilla
 * {@code net.minecraft.world.entity.ai.Brain} and SmartBrainLib (MPL-2.0);
 * this implementation is original deterministic Subterra code — no third-party
 * code is included, copied or bundled.
 * <p>模型参考（clean-room，零代码包含）：原版 {@code net.minecraft.world.entity.ai.Brain}
 * 与 SmartBrainLib（MPL-2.0）的「memory/sensor/behavior/task 分层编排」模型；本实现为
 * Subterra 原创确定性代码——不包含、不复制、不捆绑任何第三方代码。
 */
public final class Brain {

    private final BrainMemory memory = new BrainMemory();
    private final Map<String, BrainSensor> sensors = new LinkedHashMap<>();
    private final Map<String, BrainBehavior> behaviors = new LinkedHashMap<>();
    private final Map<String, BrainTask> tasks = new LinkedHashMap<>();

    /**
     * Registers a sensor in insertion order. A null/blank name, a null sensor
     * or a duplicate name is rejected with {@link IllegalArgumentException}.
     *
     * 按插入序注册感知器。null/空白名、null 实例或重复名均以
     * {@link IllegalArgumentException} 拒绝。
     *
     * @param sensor the sensor to register. 待注册的感知器。
     * @return {@code this}, for chaining. {@code this}，便于链式调用。
     */
    public Brain registerSensor(BrainSensor sensor) {
        if (sensor == null) {
            throw new IllegalArgumentException("sensor must be non-null");
        }
        requireNewName(sensors, sensor.name(), "sensor");
        sensors.put(sensor.name(), sensor);
        return this;
    }

    /**
     * Registers a behavior in insertion order. A null/blank name, a null
     * behavior or a duplicate name is rejected with
     * {@link IllegalArgumentException}.
     *
     * 按插入序注册行为。null/空白名、null 实例或重复名均以
     * {@link IllegalArgumentException} 拒绝。
     *
     * @param behavior the behavior to register. 待注册的行为。
     * @return {@code this}, for chaining. {@code this}，便于链式调用。
     */
    public Brain registerBehavior(BrainBehavior behavior) {
        if (behavior == null) {
            throw new IllegalArgumentException("behavior must be non-null");
        }
        requireNewName(behaviors, behavior.name(), "behavior");
        behaviors.put(behavior.name(), behavior);
        return this;
    }

    /**
     * Registers a task in insertion order. A null/blank name, a null task or a
     * duplicate name is rejected with {@link IllegalArgumentException}.
     *
     * 按插入序注册任务。null/空白名、null 实例或重复名均以
     * {@link IllegalArgumentException} 拒绝。
     *
     * @param task the task to register. 待注册的任务。
     * @return {@code this}, for chaining. {@code this}，便于链式调用。
     */
    public Brain registerTask(BrainTask task) {
        if (task == null) {
            throw new IllegalArgumentException("task must be non-null");
        }
        requireNewName(tasks, task.name(), "task");
        tasks.put(task.name(), task);
        return this;
    }

    /**
     * Runs all sensors in registration order, each writing into the shared
     * {@link BrainMemory}. Deterministic; a no-op when no sensor is registered.
     *
     * 按注册序运行全部感知器，各自写入共享 {@link BrainMemory}。确定性；未注册感知器时为
     * no-op。
     */
    public void sense() {
        for (BrainSensor sensor : sensors.values()) {
            sensor.sense(memory);
        }
    }

    /**
     * All registered behaviors as an unmodifiable copy in registration order
     * (deterministic). 全部已注册行为，按注册序返回不可变副本（确定性）。
     */
    public List<BrainBehavior> behaviors() {
        return List.copyOf(behaviors.values());
    }

    /**
     * The shared brain memory (live reference, fixed key order).
     * 共享大脑记忆（活引用，固定键序）。
     */
    public BrainMemory memory() {
        return memory;
    }

    /**
     * Advances one deterministic tick: sense → decide → execute, each phase in
     * fixed registration order — first all sensors write the memory, then each
     * behavior decides via {@code start} and, when it returns {@code true}, acts
     * via {@code tick}, then each task runs via {@code run}. No randomness, no
     * timing: identical registration and identical inputs yield identical
     * outputs. 推进一个确定性 tick：感知 → 决策 → 执行，各阶段均按固定注册序——先全部
     * 感知器写记忆，再各行为经 {@code start} 决策、返回 {@code true} 时经 {@code tick}
     * 行动，最后各任务经 {@code run} 执行。无随机、无时序：注册相同、输入相同则输出相同。
     */
    public void tick() {
        sense();
        for (BrainBehavior behavior : behaviors.values()) {
            if (behavior.start(memory)) {
                behavior.tick(memory);
            }
        }
        for (BrainTask task : tasks.values()) {
            task.run(memory);
        }
    }

    /** Rejects a null/blank or already-present registration name — fixed order,
     *  deterministic duplicate rejection. 拒绝 null/空白或已存在的注册名——固定序、确定性
     *  重复拒。 */
    private static void requireNewName(Map<?, ?> registry, String name, String kind) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(kind + " name must be non-null and non-blank");
        }
        if (registry.containsKey(name)) {
            throw new IllegalArgumentException(kind + " already registered: " + name);
        }
    }
}
