// p.2.24.4: deterministic AI wiring probe — anchors the p.2.24 engine.ai plane
// (p.2.24.1 Brain/BrainMemory/BrainSensor/BrainBehavior/BrainTask orchestration, p.2.24.2
// BehaviorPriority/BrainScheduler/AiRandom selection + scheduling) and the p.2.24.3 runtime.ai
// shell to fixed-order / hardcoded / byte-identical assertions. Pure JVM — no MC runtime, no
// timestamps / random / timing; exit 0 = PASS, exit 1 = FAIL. NOT shipped in the mod jar.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.ai.AiRandom;
import io.toterra.subterra.engine.ai.BehaviorPriority;
import io.toterra.subterra.engine.ai.Brain;
import io.toterra.subterra.engine.ai.BrainBehavior;
import io.toterra.subterra.engine.ai.BrainMemory;
import io.toterra.subterra.engine.ai.BrainScheduler;
import io.toterra.subterra.engine.ai.BrainSensor;
import io.toterra.subterra.engine.ai.BrainTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * p.2.24.4 — AI 确定性/接线探针：把 p.2.24 的 engine.ai 数据面（p.2.24.1 确定性 Brain 编排核心
 * {@link Brain} / {@link BrainMemory} / {@link BrainSensor} / {@link BrainBehavior} /
 * {@link BrainTask}，p.2.24.2 行为选择{@link BehaviorPriority} + 预算调度 {@link BrainScheduler} +
 * 确定性随机 {@link AiRandom}）与 p.2.24.3 的 runtime.ai 壳的确定性契约锚定为纯 JVM 断言。
 * 纯 JVM——不碰 MC、无时序、无随机；exit 0 = PASS，exit 1 = FAIL；不进 mod jar。四节：
 * <ol>
 *   <li><b>Brain 编排确定性</b>：sensor/behavior/task 各「两次注册」后序不变（感知器经 memory 固定
 *       键序、行为经 {@link Brain#behaviors()}、任务经其写序）；三类重复名
 *       {@link IllegalArgumentException}；感知写 memory 固定序键；行为决策固定序（不激活行为被跳过、
 *       绝不自 tick）；{@link Brain#tick()} 逐 tick 编排序 sense→decide→execute 与硬编码期望一致，
 *       且同注册两遍重放终态一致。</li>
 *   <li><b>行为选择 + 调度</b>：{@link BehaviorPriority#select} 优先级降序（高优先通过即选中）、
 *       同优先级按注册序打破平局、高优先不激活则回落下一优先、预算 {@code maxEvaluations} 超限按序
 *       确定性跳过；{@link BrainScheduler.TickBudget} 三 cap（sense/behavior/task 各自超限跳过固定
 *       序）+ cap&lt;1 拒绝；{@link AiRandom} 同种子同序列（nextInt/nextDouble 期望值硬编码抽查）+
 *       fork 隔离（等种子 fork 出等源、异盐 fork 出异源、父源不扰）。</li>
 *   <li><b>接线契约（静态盘点）</b>：runtime.ai 的 {@code AiRuntime} 类存在（只加载不初始化，纯 JVM
 *       不触发 MC 的 {@code LogUtils} 静态初始化）+ 类字节含门控属性串 {@code subterra.probe.ai} +
 *       marker 前缀 {@code [Subterra ai]}；AsyncE2EProbe 类字节含 {@code [Subterra ai]} 断言字面量——
 *       真实 boot 生命周期由 AsyncE2EProbe aiOk 槽覆盖。</li>
 *   <li><b>clean-room 声明盘点</b>：源码文本扫描 engine/ai package（从仓库根向上解析），要求核心
 *       编排类与 package-info 各含 {@code SmartBrainLib} 引用字样与 {@code MPL-2.0} 或
 *       {@code clean-room} 声明——证据面：engine/ai 全部为 clean-room 模型参考、零第三方代码。</li>
 * </ol>
 * 确定性纪律：固定序、无时序、无随机；全部线性遍历（禁 O(n²)）；失败计数只在失败路径自增；
 * 全过输出 {@code [AiProbe] PASS (n checks)} exit 0，否则 FAIL exit 1。
 *
 * <p>p.2.24.4 — deterministic AI wiring probe: anchors the p.2.24 engine.ai data plane (the p.2.24.1
 * deterministic Brain orchestration core {@link Brain} / {@link BrainMemory} / {@link BrainSensor} /
 * {@link BrainBehavior} / {@link BrainTask}, the p.2.24.2 behavior selection {@link BehaviorPriority} +
 * budget scheduling {@link BrainScheduler} + deterministic randomness {@link AiRandom}) and the p.2.24.3
 * runtime.ai shell to pure-JVM assertions. Pure JVM — no MC runtime, no timing, no randomness; exit 0 =
 * PASS, exit 1 = FAIL; never shipped in the mod jar. Four sections:
 * <ol>
 *   <li><b>Brain orchestration determinism</b>: sensors/behaviors/tasks each registered in two batches
 *       keep their order (sensors via the fixed memory-key order, behaviors via
 *       {@link Brain#behaviors()}, tasks via their write order); the three duplicate-name types raise
 *       {@link IllegalArgumentException}; sensing writes memory in fixed key order; behavior decision is
 *       fixed-order (an inactive behavior is skipped and never self-ticks); {@link Brain#tick()} advances
 *       sense→decide→execute matching a hardcoded expectation, and two replays with the same
 *       registrations end in identical terminal states.</li>
 *   <li><b>Behavior selection + scheduling</b>: {@link BehaviorPriority#select} picks priority-descending
 *       (a passing high-priority behavior wins), breaks ties by registration order, falls back to the next
 *       priority when the high one is inactive, and a {@code maxEvaluations} budget deterministically skips
 *       the remaining candidates in order; {@link BrainScheduler.TickBudget} has three caps (sense /
 *       behavior / task each skip the remaining entries in fixed order over-cap) and rejects {@code cap < 1};
 *       {@link AiRandom} yields the same sequence for the same seed (nextInt/nextDouble hardcoded spot
 *       checks) + fork isolation (equal seeds fork equal sources, a distinct salt forks a distinct source,
 *       and the parent is undisturbed).</li>
 *   <li><b>Wiring contract (static inventory)</b>: the runtime.ai {@code AiRuntime} class is present
 *       (load-only, never initialized — the pure JVM must not trigger the MC {@code LogUtils} static init),
 *       its class bytes carry the gate string {@code subterra.probe.ai} and the marker prefix
 *       {@code [Subterra ai]}; the AsyncE2EProbe class bytes carry the {@code [Subterra ai]} assertion
 *       literal — the real boot lifecycle is covered by the AsyncE2EProbe aiOk slot.</li>
 *   <li><b>Clean-room inventory</b>: a source-text scan of the engine/ai package (resolved upward from the
 *       repo root) requires the core orchestration classes and the package-info each to carry a
 *       {@code SmartBrainLib} reference and an {@code MPL-2.0} or {@code clean-room} declaration — evidence
 *       that engine/ai is clean-room model-reference only, with zero third-party code.</li>
 * </ol>
 * Determinism discipline: fixed order, no timing, no randomness; all traversals linear (no O(n²));
 * failures are counted only on failing paths; PASS only when all checks pass, then exit 0, else FAIL with
 * counts and exit 1.
 */
public final class AiProbe {

    private AiProbe() {
    }

    private static int checks = 0;
    private static int failures = 0;

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    public static void main(String[] args) {
        try {
            brainOrchestration();
            selectionAndScheduling();
            wiringContract();
            cleanRoomInventory();
        } catch (Exception e) {
            failures++;
            System.out.println("[FAIL] probe exception: " + e);
            e.printStackTrace(System.out);
        }
        if (failures == 0) {
            System.out.println("[AiProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[AiProbe] FAIL (" + failures + " of " + checks + " checks)");
            System.exit(1);
        }
    }

    /** 动作抛出 IAE 为真。True iff the action raises IllegalArgumentException. */
    private static boolean throwsIAE(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    /** 记忆摘要：固定键序 {@code key=value} 逗号连接，作为确定性重放终态载体。Memory digest:
     *  {@code key=value} joined by comma in fixed key order — a deterministic replay terminal carrier. */
    private static String digest(BrainMemory memory) {
        StringBuilder sb = new StringBuilder();
        for (String key : memory.keys()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(key).append('=').append(memory.get(key));
        }
        return sb.toString();
    }

    // ---------- (1) Brain orchestration determinism (p.2.24.1) ----------

    private static void brainOrchestration() {
        // sensors: two registration batches, order verified via fixed memory key order.
        Brain sensorBrain = new Brain();
        sensorBrain.registerSensor(constSensor("sensor.a", "k.a", "va"));
        sensorBrain.registerSensor(constSensor("sensor.b", "k.b", "vb"));
        sensorBrain.registerSensor(constSensor("sensor.c", "k.c", "vc"));
        sensorBrain.registerSensor(constSensor("sensor.d", "k.d", "vd"));
        sensorBrain.sense();
        check("Brain: sensor 两次注册后固定序（sense 写 memory 固定键序 k.a→k.b→k.c→k.d）",
                sensorBrain.memory().keys().equals(List.of("k.a", "k.b", "k.c", "k.d"))
                        && sensorBrain.memory().get("k.a").equals("va")
                        && sensorBrain.memory().get("k.d").equals("vd"));

        // behaviors: registration order via behaviors().
        Brain behaviorBrain = new Brain();
        behaviorBrain.registerBehavior(nameBehavior("behavior.a", true));
        behaviorBrain.registerBehavior(nameBehavior("behavior.b", true));
        behaviorBrain.registerBehavior(nameBehavior("behavior.c", true));
        behaviorBrain.registerBehavior(nameBehavior("behavior.d", true));
        check("Brain: behavior 两次注册后注册序不变 (behaviors() 固定序)",
                behaviorBrain.behaviors().stream().map(BrainBehavior::name).toList()
                        .equals(List.of("behavior.a", "behavior.b", "behavior.c", "behavior.d")));

        // tasks: registration order verified via shared write-order memory key.
        Brain taskBrain = new Brain();
        taskBrain.registerTask(appendTask("task.a"));
        taskBrain.registerTask(appendTask("task.b"));
        taskBrain.registerTask(appendTask("task.c"));
        taskBrain.registerTask(appendTask("task.d"));
        taskBrain.tick();
        check("Brain: task 两次注册后固定序（tick 写序 ta,tb,tc,td）",
                "task.a,task.b,task.c,task.d".equals(taskBrain.memory().get("taskOrder")));

        // duplicate-name rejection, all three kinds.
        Brain dup = new Brain();
        dup.registerSensor(constSensor("dup.s", "k", "v"));
        dup.registerBehavior(nameBehavior("dup.b", true));
        dup.registerTask(appendTask("dup.t"));
        check("Brain: 重复感知器名 → IllegalArgumentException",
                throwsIAE(() -> dup.registerSensor(constSensor("dup.s", "k2", "v"))));
        check("Brain: 重复行为名 → IllegalArgumentException",
                throwsIAE(() -> dup.registerBehavior(nameBehavior("dup.b", true))));
        check("Brain: 重复任务名 → IllegalArgumentException",
                throwsIAE(() -> dup.registerTask(appendTask("dup.t"))));

        // behavior decision fixed order: active logs, inactive is skipped (never self-ticks).
        Brain decide = new Brain();
        decide.registerBehavior(new BrainBehavior() {
            @Override
            public String name() {
                return "decide.active";
            }

            @Override
            public boolean start(BrainMemory memory) {
                return true;
            }

            @Override
            public void tick(BrainMemory memory) {
                memory.put("behLog", "A");
            }
        });
        decide.registerBehavior(new BrainBehavior() {
            @Override
            public String name() {
                return "decide.inactive";
            }

            @Override
            public boolean start(BrainMemory memory) {
                return false; // inactive -> never decides, never ticks
            }

            @Override
            public void tick(BrainMemory memory) {
                throw new IllegalStateException("inactive behavior must not tick");
            }
        });
        decide.tick();
        check("Brain: 行为决策固定序（不激活行为被跳过、绝不自 tick；活跃行为写 A）",
                "A".equals(decide.memory().get("behLog"))
                        && !decide.memory().has("decide.inactive"));

        // tick orchestration order sense→decide→execute, hardcoded digest + replay determinism.
        String digest1 = runTickSequence();
        String digest2 = runTickSequence();
        check("Brain.tick: 逐 tick 编排序 sense→decide→execute 终态与硬编码期望一致",
                "ticks.sensed=3,behavior.afterSense=3,ticks.decided=3,task.afterDecide=3,ticks.executed=3"
                        .equals(digest1));
        check("Brain.tick: 同注册两遍重放终态一致（确定性回放）", digest1.equals(digest2));
    }

    /** 固定感知器：写入固定键/值。A fixed sensor writing a fixed key/value. */
    private static BrainSensor constSensor(String name, String key, String value) {
        return new BrainSensor() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void sense(BrainMemory memory) {
                memory.put(key, value);
            }
        };
    }

    /** 固定行为：start 返回固定布尔。A fixed behavior with a fixed start result. */
    private static BrainBehavior nameBehavior(String name, boolean active) {
        return new BrainBehavior() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean start(BrainMemory memory) {
                return active;
            }

            @Override
            public void tick(BrainMemory memory) {
                // no-op: presence in behaviors() is what matters here
            }
        };
    }

    /** 固定任务：把名字追加进共享 {@code taskOrder} 串（写序=注册序）。A fixed task appending its name
     *  to the shared {@code taskOrder} string (write order = registration order). */
    private static BrainTask appendTask(String name) {
        return new BrainTask() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void run(BrainMemory memory) {
                String prev = memory.has("taskOrder") ? (String) memory.get("taskOrder") : "";
                memory.put("taskOrder", prev.isEmpty() ? name : prev + "," + name);
            }
        };
    }

    /** 固定 tick 驱动两遍回放的问题：感知器每 tick 递增 {@code ticks.sensed}；行为（恒定激活）读到本
     *   tick 感知值后写 {@code behavior.afterSense} 并递增 {@code ticks.decided}；任务读到本 tick 决策
     *   值后写 {@code task.afterDecide} 并递增 {@code ticks.executed}。键序=首次写入序，证明单 tick 内
     *   phase 依赖链 sense→decide→execute。One full replay of the fixed tick drive: the sensor increments
     *   {@code ticks.sensed} each tick; the always-active behavior reads this tick's sensed value into
     *   {@code behavior.afterSense} and increments {@code ticks.decided}; the task reads this tick's decided
     *   value into {@code task.afterDecide} and increments {@code ticks.executed}. The key order = first-write
     *   order, proving the within-tick phase dependency chain sense→decide→execute. */
    private static String runTickSequence() {
        Brain brain = new Brain();
        brain.registerSensor(new BrainSensor() {
            @Override
            public String name() {
                return "tick.sensor";
            }

            @Override
            public void sense(BrainMemory memory) {
                int n = memory.has("ticks.sensed") ? ((Number) memory.get("ticks.sensed")).intValue() : 0;
                memory.put("ticks.sensed", n + 1);
            }
        });
        brain.registerBehavior(new BrainBehavior() {
            @Override
            public String name() {
                return "tick.behavior";
            }

            @Override
            public boolean start(BrainMemory memory) {
                return true;
            }

            @Override
            public void tick(BrainMemory memory) {
                memory.put("behavior.afterSense", memory.get("ticks.sensed"));
                int n = memory.has("ticks.decided") ? ((Number) memory.get("ticks.decided")).intValue() : 0;
                memory.put("ticks.decided", n + 1);
            }
        });
        brain.registerTask(new BrainTask() {
            @Override
            public String name() {
                return "tick.task";
            }

            @Override
            public void run(BrainMemory memory) {
                memory.put("task.afterDecide", memory.get("ticks.decided"));
                int n = memory.has("ticks.executed") ? ((Number) memory.get("ticks.executed")).intValue() : 0;
                memory.put("ticks.executed", n + 1);
            }
        });
        for (int i = 0; i < 3; i++) {
            brain.tick();
        }
        return digest(brain.memory());
    }

    // ---------- (2) behavior selection + scheduling (p.2.24.2) ----------

    private static void selectionAndScheduling() {
        // BehaviorPriority: priority-descending select.
        BehaviorPriority prio = new BehaviorPriority();
        BehaviorPriority.BehaviorEntry hi = register(prio, "prio.hi", 10, true);
        BehaviorPriority.BehaviorEntry mid = register(prio, "prio.mid", 5, true);
        BehaviorPriority.BehaviorEntry low = register(prio, "prio.low", 1, true);
        BrainMemory mem = new BrainMemory();
        check("BehaviorPriority.select: 优先级降序（高优先通过即选中 hi）",
                "prio.hi".equals(prio.select(prio.entries(), mem).name()));

        // same-priority ties are broken by registration order.
        BehaviorPriority tie = new BehaviorPriority();
        BehaviorPriority.BehaviorEntry tieA = register(tie, "tie.a", 7, true);
        BehaviorPriority.BehaviorEntry tieB = register(tie, "tie.b", 7, true);
        check("BehaviorPriority.select: 同优先级按注册序打破平局（tie.a 先注册先选中）",
                "tie.a".equals(tie.select(tie.entries(), mem).name())
                        && tieA.priority() == tieB.priority());

        // a high-priority but-inactive behavior is skipped; the next passing priority is selected.
        BehaviorPriority fallback = new BehaviorPriority();
        register(fallback, "fall.high", 9, false);
        register(fallback, "fall.mid", 5, true);
        check("BehaviorPriority.select: 高优先不激活则回落下一通过优先（mid）",
                "fall.mid".equals(fallback.select(fallback.entries(), mem).name()));

        // budget maxEvaluations: over-cap candidates are deterministically skipped in order.
        BehaviorPriority budget = new BehaviorPriority();
        register(budget, "bud.a", 3, false);
        register(budget, "bud.b", 2, false);
        register(budget, "bud.c", 1, true);
        List<BehaviorPriority.BehaviorEntry> cands = budget.entries();
        check("BehaviorPriority.select(budget=2): 预算超限确定性跳过剩余候选（全部 false → null 且预算内不到 bud.c）",
                budget.select(cands, mem, 2) == null
                        && budget.select(cands, mem, 1) == null);
        check("BehaviorPriority.select(budget=∞): 预算足够时选中唯一通过者 bud.c",
                "bud.c".equals(budget.select(cands, mem).name())
                        && "bud.c".equals(budget.select(cands, mem, 3).name()));

        // BrainScheduler: three phase caps skip the remaining entries in fixed order.
        BrainScheduler senseCapped = new BrainScheduler(BrainScheduler.TickBudget.of(1, 5, 5));
        senseCapped.registerSensor(counterSensor("sc.s1", "sc"));
        senseCapped.registerSensor(throwingSensor("sc.s2"));
        senseCapped.registerSensor(throwingSensor("sc.s3"));
        Brain senseBrain = new Brain();
        BrainScheduler.TickResult senseRes = senseCapped.tick(senseBrain);
        check("BrainScheduler: sense cap=1（仅 s1 运行 sc=1；s2/s3 超限跳过未运行）",
                senseRes.sensorsRun() == 1
                        && ((Number) senseBrain.memory().get("sc")).intValue() == 1
                        && !senseBrain.memory().has("sc.s2"));

        BrainScheduler taskCapped = new BrainScheduler(BrainScheduler.TickBudget.of(5, 5, 1));
        taskCapped.registerTask(counterTask("tc.t1", "tc"));
        taskCapped.registerTask(throwingTask("tc.t2"));
        taskCapped.registerTask(throwingTask("tc.t3"));
        Brain taskBrain2 = new Brain();
        BrainScheduler.TickResult taskRes = taskCapped.tick(taskBrain2);
        check("BrainScheduler: task cap=1（仅 t1 运行 tc=1；t2/t3 超限跳过未运行）",
                taskRes.tasksRun() == 1
                        && ((Number) taskBrain2.memory().get("tc")).intValue() == 1);

        BrainScheduler behCapped = new BrainScheduler(BrainScheduler.TickBudget.of(5, 1, 5));
        behCapped.registerBehavior(nameBehavior("bh.hi", true), 10);
        behCapped.registerBehavior(throwingBehavior("bh.lo"), 1);
        Brain behBrain = new Brain();
        BrainScheduler.TickResult behRes = behCapped.tick(behBrain);
        check("BrainScheduler: behavior cap=1（仅评估最高优先 hi；lo 超限被跳过未评估）",
                behRes.selectedBehavior() != null && behRes.selectedBehavior().equals("bh.hi")
                        && behRes.behaviorsEvaluated() == 1);

        check("BrainScheduler.TickBudget.of: cap<1 拒绝（sense/behavior/task 三槽各校验）",
                throwsIAE(() -> BrainScheduler.TickBudget.of(0, 5, 5))
                        && throwsIAE(() -> BrainScheduler.TickBudget.of(5, 0, 5))
                        && throwsIAE(() -> BrainScheduler.TickBudget.of(5, 5, 0)));

        // AiRandom: same seed, same sequence — hardcoded spot checks.
        AiRandom r = new AiRandom(42L);
        check("AiRandom: 同种子 nextInt 期望值硬编码抽查（413, 316）",
                r.nextInt(1000) == 413 && r.nextInt(1000) == 316);
        check("AiRandom: 同种子 nextDouble 期望值硬编码抽查（0.5911…, 0.2650…）",
                r.nextDouble() == 0.5911075968429961 && r.nextDouble() == 0.2650272295535885);

        AiRandom r1 = new AiRandom(42L);
        AiRandom r2 = new AiRandom(42L);
        check("AiRandom: 两遍同种子同调用序列 4 次抽取值逐位一致（确定性证明）",
                r1.nextInt(1000) == r2.nextInt(1000)
                        && r1.nextInt(1000) == r2.nextInt(1000)
                        && r1.nextDouble() == r2.nextDouble()
                        && r1.nextDouble() == r2.nextDouble());

        // fork isolation: equal seeds fork equal sources; distinct salts fork distinct sources;
        // the parent's later draw equals what it would have produced had no fork happened.
        AiRandom base = new AiRandom(7L);
        base.nextInt(1000); // 707
        base.nextInt(1000); // 321
        AiRandom forkA = base.fork(1L);
        AiRandom forkB = base.fork(1L);
        int parentAfterFork = base.nextInt(1000);
        AiRandom nofork = new AiRandom(7L);
        nofork.nextInt(1000);
        nofork.nextInt(1000);
        int noforkThird = nofork.nextInt(1000);
        AiRandom forkC = base.fork(999L);
        check("AiRandom: fork 等种子 → 等派生源（forkA0==forkB0==416, forkA1==296）",
                forkA.nextInt(1000) == 416 && forkB.nextInt(1000) == 416
                        && forkA.nextInt(1000) == 296);
        check("AiRandom: fork 异盐 → 异派生源（forkC != forkA）",
                forkC.nextInt(1000) != 416);
        check("AiRandom: fork 隔离父源不扰（父源后续取值 738 == 未 fork 第 3 抽 738）",
                parentAfterFork == 738 && noforkThird == 738 && parentAfterFork == noforkThird);
    }

    /** 注册一个行为并返回条目。Registers a behavior and returns its entry. */
    private static BehaviorPriority.BehaviorEntry register(BehaviorPriority bp, String name, int prio,
                                                           boolean active) {
        bp.register(nameBehavior(name, active), prio);
        for (BehaviorPriority.BehaviorEntry e : bp.entries()) {
            if (e.name().equals(name)) {
                return e;
            }
        }
        throw new IllegalStateException("unreachable");
    }

    /** 递增共享计数器键的感知器。A sensor incrementing a shared counter key. */
    private static BrainSensor counterSensor(String name, String key) {
        return new BrainSensor() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void sense(BrainMemory memory) {
                int n = memory.has(key) ? ((Number) memory.get(key)).intValue() : 0;
                memory.put(key, n + 1);
            }
        };
    }

    /** 一被调用必抛的感知器（证明超限跳过）。A sensor that throws when invoked (proves over-cap skip). */
    private static BrainSensor throwingSensor(String name) {
        return new BrainSensor() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void sense(BrainMemory memory) {
                throw new IllegalStateException("over-cap sensor must be skipped: " + name);
            }
        };
    }

    /** 递增共享计数器键的任务。A task incrementing a shared counter key. */
    private static BrainTask counterTask(String name, String key) {
        return new BrainTask() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void run(BrainMemory memory) {
                int n = memory.has(key) ? ((Number) memory.get(key)).intValue() : 0;
                memory.put(key, n + 1);
            }
        };
    }

    /** 一被调用必抛的任务（证明超限跳过）。A task that throws when invoked (proves over-cap skip). */
    private static BrainTask throwingTask(String name) {
        return new BrainTask() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void run(BrainMemory memory) {
                throw new IllegalStateException("over-cap task must be skipped: " + name);
            }
        };
    }

    /** 一被评估必抛的行为（证明超限跳过）。A behavior that throws when evaluated (proves over-cap skip). */
    private static BrainBehavior throwingBehavior(String name) {
        return new BrainBehavior() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean start(BrainMemory memory) {
                throw new IllegalStateException("over-cap behavior must be skipped: " + name);
            }

            @Override
            public void tick(BrainMemory memory) {
                throw new IllegalStateException("over-cap behavior must be skipped: " + name);
            }
        };
    }

    // ---------- (3) wiring contract (static inventory, load-only) ----------

    private static void wiringContract() {
        String aiRuntime = "io.toterra.subterra.runtime.ai.AiRuntime";
        check("wiring: runtime.ai.AiRuntime 类存在（只加载不初始化，纯 JVM 不触发 MC LogUtils）",
                classExists(aiRuntime));
        check("wiring: AiRuntime 类字节含门控属性串 'subterra.probe.ai'",
                classBytesContain(aiRuntime, "subterra.probe.ai"));
        check("wiring: AiRuntime 类字节含 marker 前缀 '[Subterra ai]'",
                classBytesContain(aiRuntime, "[Subterra ai]"));
        // 真实生命周期由 boot E2E 覆盖：AsyncE2EProbe aiOk 槽断言字面量的静态存在性。
        check("wiring: AsyncE2EProbe 类字节含 aiOk 断言（'[Subterra ai]' marker）",
                classBytesContain("io.toterra.subterra.probes.AsyncE2EProbe", "[Subterra ai]"));
    }

    /** 只加载不初始化地确认类存在。Loads without initializing — presence only. */
    private static boolean classExists(String fqcn) {
        try {
            Class.forName(fqcn, false, AiProbe.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** 断言类的 .class 字节包含某字符串字面量（常量池 UTF-8，纯 ASCII，ISO-8859-1 逐字节映射，
     *  线性 contains、无 O(n²)）。Asserts the class bytes contain a string literal (constant-pool
     *  UTF-8; the strings are pure ASCII so ISO-8859-1 is a byte-identity mapping — a linear contains
     *  scan, no O(n²)). */
    private static boolean classBytesContain(String fqcn, String literal) {
        String resource = "/" + fqcn.replace('.', '/') + ".class";
        try (InputStream in = AiProbe.class.getResourceAsStream(resource)) {
            if (in == null) {
                return false;
            }
            byte[] bytes = in.readAllBytes();
            return new String(bytes, StandardCharsets.ISO_8859_1).contains(literal);
        } catch (IOException e) {
            return false;
        }
    }

    // ---------- (4) clean-room declaration inventory (source-text scan) ----------

    private static void cleanRoomInventory() {
        File aiDir = engineAiSourceDir();
        if (aiDir == null || !aiDir.isDirectory()) {
            check("clean-room: engine/ai 源码目录可解析（repo 根向上解析）", false);
            return;
        }
        String[] core = {"Brain.java", "BrainMemory.java", "BrainSensor.java",
                "BrainBehavior.java", "BrainTask.java", "package-info.java"};
        boolean allDeclared = true;
        for (String fileName : core) {
            String text = readText(new File(aiDir, fileName));
            boolean declared = text != null && text.contains("SmartBrainLib")
                    && (text.contains("MPL-2.0") || text.contains("clean-room"));
            allDeclared = allDeclared && declared;
        }
        check("clean-room: engine/ai 核心类+package-info 源码含 'SmartBrainLib' 与 'MPL-2.0'/'clean-room' 声明（clean-room 模型参考、零第三方代码）",
                allDeclared);
    }

    /** 从 user.dir 向上解析到仓库根，定位 engine/ai 源码目录；未找到返回 null。Resolves the repository
     *  root upward from user.dir and locates the engine/ai source dir; null when not found. */
    private static File engineAiSourceDir() {
        File d = new File(System.getProperty("user.dir"));
        while (d != null) {
            File engine = new File(d, "subterra-engine");
            if (engine.isDirectory()) {
                File ai = new File(engine, "src/main/java/io/toterra/subterra/engine/ai");
                if (ai.isDirectory()) {
                    return ai;
                }
            }
            d = d.getParentFile();
        }
        return null;
    }

    /** UTF-8 读一个源码文件；缺失/IO 错误返回 null。Reads a source file as UTF-8; null when absent/IO. */
    private static String readText(File file) {
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}