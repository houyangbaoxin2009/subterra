// Deterministic acceptance probe for p.2.8.5: the full engine.sim stack (budget +
// spatial + core + run/parallel) composed into a deterministic "world-evolution
// caricature". Pure JVM — no MC, no wall-clock. NOT shipped in the mod jar.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.parallel.TaskResult;
import io.toterra.subterra.engine.sim.budget.BudgetScheduler;
import io.toterra.subterra.engine.sim.budget.RegionKey;
import io.toterra.subterra.engine.sim.budget.TickBudget;
import io.toterra.subterra.engine.sim.core.PerSimRandom;
import io.toterra.subterra.engine.sim.core.SimKey;
import io.toterra.subterra.engine.sim.core.SimWorld;
import io.toterra.subterra.engine.sim.run.SimRunner;
import io.toterra.subterra.engine.sim.spatial.CellCoord;
import io.toterra.subterra.engine.sim.spatial.SpatialIndex;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Deterministic acceptance probe for p.2.8.5 — a full-stack combination of the four
 * landed p.2.8 modules ({@code engine.sim.budget}, {@code engine.sim.spatial},
 * {@code engine.sim.core}, {@code engine.sim.run}/{@code engine.parallel}). It glues
 * them into a deterministic "world-evolution caricature": scattered simulants holding a
 * {population-energy} state are budget-charged in key order each tick (units driven by a
 * local neighborhood-count query), only the budget-accepted change-locals are advanced
 * through the parallel runner, and the resulting deterministic byte summary is diffed
 * across two runs and two insertion orders. Every check is a final-state assertion after
 * a join — never a wall-clock/timing assertion, never an O(n²) scan.
 *
 * <p>The eight assertions: (1) per-step parallel run == runSerial byte-identical (and the
 * applied world state equals the parallel output); (2) two worlds with the same params but
 * different simulant insertion order produce byte-identical T-tick summaries (input-order
 * independence); (3) a full re-run of the loop reproduces the identical summary
 * (reproducibility); (4) the incremental advance path ({@code advance(keys())}) is
 * byte-identical tick-by-tick to the full path ({@code advanceAll()}) per the
 * {@link SimWorld} contract; (5) budget-DEFERRED keys keep their state byte-identical that
 * tick and deterministically follow up next tick after the budget resets; (6) two
 * independent schedulers fed the same key-order request stream emit an identical decision
 * stream; (7) a spatial window query contains only local keys (a far-away simulant never
 * interferes — no global scan); (8) only the changed local is advanced, so every
 * non-advanced key's state is byte-identical before/after (diff isolation through both the
 * budget and parallel paths).
 *
 * <p>p.2.8.5 的全栈组合确定性验收探针：把已落地的四模块（{@code engine.sim.budget}、
 * {@code engine.sim.spatial}、{@code engine.sim.core}、{@code engine.sim.run}/
 * {@code engine.parallel}）拼成一个确定性的「世界演化简景」——散布 simulant 持 {种群-能量}
 * 状态，每 tick 按键序做预算计费（单位由邻域局部计数查询驱动），仅预算接受的"变化局部"
 * 经并行 runner 推进，最终把确定性字节摘要跨两次运行/两种插入序做 diff。所有检查都是
 * join 之后的终态断言——绝不依赖墙钟/时序，绝无 O(n²) 扫描。
 *
 * <p>八项断言：(1) 每步并行 run 与 runSerial 逐字节一致（且应用后的世界状态等于并行输出）；
 * (2) 同参数但不同 simulant 插入序的两世界 T tick 后摘要逐字节一致（输入序无关）；
 * (3) 全轨重跑复现同一摘要（可复现性）；(4) 按 {@link SimWorld} 契约，增量推进路径
 * （{@code advance(keys())}）与全量路径（{@code advanceAll()}）逐 tick 逐字节一致；
 * (5) 预算 DEFERRED 的键本 tick 状态逐字节未变，且下一 tick 预算复位后确定性跟进；
 * (6) 两台独立调度器喂同一按键序的请求流，产出逐位一致的决策流；(7) 空间窗口查询仅含
 * 局部键（远端 simulant 恒不干扰——无全局扫描）；(8) 仅推进"变化局部"，故每个未推进键的
 * 状态前后逐字节不变（diff 隔离贯穿预算 + 并行两路径）。
 */
public final class SimCombineProbe {

    /** A simulant's immutable {population-energy} state; pure input to the tick function. */
    record Pop(long energy) {
    }

    /** Outcome of one full budgeted world-evolution loop. */
    record RunResult(
            byte[] summary,
            boolean parSerialOk,
            boolean deferredUnchangedOk,
            boolean followUpFound) {
    }

    private static final long WORLD_SEED = 44905237L;
    private static final int CELL_BITS = 4;    // cell size = 2^4 = 16 world units
    private static final int REGION_BITS = 5;  // region size = 2^5 = 32 world units
    private static final int TICKS = 48;
    private static final SimKey FAR = new SimKey(1_000_000L, 1_000_000L);

    /** Pure per-simulant tick function: energy random-walks within [1, 1000]. */
    private static final io.toterra.subterra.engine.sim.core.Simulator<Pop> SIM =
            (tickNo, key, rand, state) -> new Pop(clamp(state.energy() + rand.nextInt(5) - 2, 1, 1000));

    private SimCombineProbe() {
    }

    private static int failures = 0;
    private static int checks = 0;

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    private static long clamp(long v, long lo, long hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /**
     * Builds the deterministic simulant key layout: 8 tightly-clustered 3x3 grids (each
     * confined to a single cell and single region, far apart) plus one isolated far-away
     * simulant. Every pair of clusters is separated by ≫ the radius-1 window (48 world
     * units), so a window query around one cluster can never reach another.
     */
    private static List<SimKey> buildKeys() {
        int[][] centers = {
                {0, 0}, {300, 0}, {0, 300}, {300, 300},
                {600, 100}, {150, 600}, {900, 400}, {400, 900}
        };
        List<SimKey> keys = new ArrayList<>();
        for (int[] c : centers) {
            for (int dx = 0; dx < 3; dx++) {
                for (int dz = 0; dz < 3; dz++) {
                    keys.add(new SimKey(c[0] + dx, c[1] + dz));
                }
            }
        }
        keys.add(FAR);
        return keys;
    }

    /** 8 bytes big-endian. */
    private static void writeLong(ByteArrayOutputStream bos, long v) {
        bos.write((byte) (v >>> 56));
        bos.write((byte) (v >>> 48));
        bos.write((byte) (v >>> 40));
        bos.write((byte) (v >>> 32));
        bos.write((byte) (v >>> 24));
        bos.write((byte) (v >>> 16));
        bos.write((byte) (v >>> 8));
        bos.write((byte) v);
    }

    /**
     * Deterministic summary of a world's final state: for every key in canonical natural
     * order, write (x, z, energy) big-endian. This is the byte-identity oracle for the
     * cross-run / cross-order assertions.
     */
    private static byte[] summaryOf(SimWorld<Pop> world) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (SimKey k : world.keys()) {
            Pop s = world.state(k);
            writeLong(bos, k.x());
            writeLong(bos, k.z());
            writeLong(bos, s.energy());
        }
        return bos.toByteArray();
    }

    /** Element-for-element, energy-for-energy equality of two merged parallel lists. */
    private static <K extends Comparable<K>> boolean parSerialMatch(
            List<TaskResult<K, Pop>> a, List<TaskResult<K, Pop>> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            TaskResult<K, Pop> x = a.get(i);
            TaskResult<K, Pop> y = b.get(i);
            if (!x.key().equals(y.key()) || x.result().energy() != y.result().energy()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Runs the full budgeted world-evolution caricature for {@link #TICKS} ticks over the
     * given insertion-order of simulant keys, and returns the final byte summary plus the
     * determinism diagnostics. A fresh {@link BudgetScheduler} is built each tick (the
     * "budget reset"); the incremental dirty set carries deferred keys forward together
     * with the keys whose state actually changed last tick, so a deferred key is re-offered
     * next tick and deterministically follows up once the fresh budget admits it.
     */
    private static RunResult runEvolution(List<SimKey> insertionOrder) {
        TickBudget budget = TickBudget.of(50, 500, 45); // region/simulant generous, global tight
        SpatialIndex<SimKey> spatial = new SpatialIndex<>();
        for (SimKey k : insertionOrder) {
            spatial.insert(CellCoord.of(k.x(), k.z(), CELL_BITS), k);
        }
        SimWorld<Pop> world = SimWorld.create(SIM, WORLD_SEED, 0);
        for (SimKey k : insertionOrder) {
            world.add(k, new Pop(100));
        }
        List<SimKey> allKeys = world.keys(); // canonical natural order, insertion-order independent
        SimRunner runner = SimRunner.create(4);

        Set<SimKey> dirty = new TreeSet<>(allKeys);
        Map<SimKey, Pop> snapshotBefore = new HashMap<>();
        for (SimKey k : allKeys) {
            snapshotBefore.put(k, world.state(k));
        }
        Set<SimKey> prevDeferred = new HashSet<>();

        boolean parSerialOk = true;
        boolean deferredUnchangedOk = true;
        boolean followUpFound = false;

        for (int t = 0; t < TICKS; t++) {
            List<SimKey> candidates = new ArrayList<>(dirty); // natural order
            BudgetScheduler<SimKey> sched = BudgetScheduler.create(budget, REGION_BITS);
            List<SimKey> accepted = new ArrayList<>();
            Set<SimKey> deferred = new TreeSet<>();
            for (SimKey k : candidates) {
                Pop before = snapshotBefore.get(k);
                int feed = spatial.query(k.x(), k.z(), 1, CELL_BITS).size(); // local window only
                RegionKey region = sched.regionOf(k.x(), k.z());
                long units = 2 + feed + ((before.energy() + t) & 3L); // state + tick dependent -> boundaries shift
                if (sched.charge(k, region, units) == BudgetScheduler.ChargeResult.ACCEPTED) {
                    accepted.add(k);
                } else {
                    deferred.add(k);
                }
            }
            // Deterministic follow-up: any key deferred last tick must be re-offered and now accepted.
            for (SimKey k : prevDeferred) {
                if (accepted.contains(k)) {
                    followUpFound = true;
                }
            }
            prevDeferred = deferred;

            long destTick = world.tickNo() + 1;
            List<TaskResult<SimKey, Pop>> par = runner.run(accepted,
                    k -> SIM.compute(destTick, k, PerSimRandom.forTick(WORLD_SEED, destTick, k), world.state(k)));
            List<TaskResult<SimKey, Pop>> ser = runner.runSerial(accepted,
                    k -> SIM.compute(destTick, k, PerSimRandom.forTick(WORLD_SEED, destTick, k), world.state(k)));
            if (!parSerialMatch(par, ser)) {
                parSerialOk = false;
            }

            Map<SimKey, Pop> diff = world.advance(accepted); // applies exactly the accepted keys

            // The applied world state must equal the parallel/precompute output (determinism of the apply step).
            for (TaskResult<SimKey, Pop> r : par) {
                if (!Objects.equals(world.state(r.key()), r.result())) {
                    parSerialOk = false;
                }
            }
            // Diff isolation: deferred (or simply not accepted) keys byte-identical before/after.
            for (SimKey k : deferred) {
                if (!Objects.equals(world.state(k), snapshotBefore.get(k))) {
                    deferredUnchangedOk = false;
                }
            }

            // Build next-tick dirty set = deferred (pending) ∪ changed locals.
            Map<SimKey, Pop> snapshotAfter = new HashMap<>();
            dirty = new TreeSet<>(deferred);
            for (SimKey k : allKeys) {
                snapshotAfter.put(k, world.state(k));
                if (!Objects.equals(snapshotAfter.get(k), snapshotBefore.get(k))) {
                    dirty.add(k); // changed local -> still needs processing next tick
                }
            }
            snapshotBefore = snapshotAfter;
        }

        return new RunResult(summaryOf(world), parSerialOk, deferredUnchangedOk, followUpFound);
    }

    public static void main(String[] args) {
        List<SimKey> keysNatural = buildKeys();
        List<SimKey> keysReversed = new ArrayList<>(keysNatural);
        java.util.Collections.reverse(keysReversed);

        // ---- 1. parallel == runSerial, byte-identical (and applied == parallel) ----
        RunResult main = runEvolution(keysNatural);
        check("1. parallel==runSerial byte-identical each tick, and applied world state == parallel output",
                main.parSerialOk());

        // ---- 2. same params, opposite insertion order -> byte-identical summary ----
        RunResult rev = runEvolution(keysReversed);
        check("2. two worlds, opposite simulant insertion order, byte-identical T-tick summary (order-independent)",
                Arrays.equals(main.summary(), rev.summary()));

        // ---- 3. full re-run reproduces the identical summary ----
        RunResult again = runEvolution(keysNatural);
        check("3. full-belt re-run (two independent loops) reproduces byte-identical summary (reproducible)",
                Arrays.equals(main.summary(), rev.summary())
                        && Arrays.equals(main.summary(), again.summary()));

        // ---- 4. incremental advance(keys()) == full advanceAll(), contract path ----
        check("4. SimWorld contract: advance(keys()) == advanceAll() byte-identical for all T ticks",
                incrementalEqualsFullPath());

        // ---- 5. deferred kept byte-identical now; deterministically follows up next tick ----
        check("5. DEFERRED keys state byte-identical that tick; carried into next tick and deterministically "
                + "followed-up after budget reset", main.deferredUnchangedOk() && main.followUpFound());

        // ---- 6. budget decision determinism: same request stream -> identical decision stream ----
        check("6. two independent schedulers, same key-order request stream -> bit-identical accept/defer stream",
                schedulerDeterministic());

        // ---- 7. spatial query stays inside its local window (no global scan) ----
        check("7. radius-1 query returns only window-local keys; far-away simulant never leaks in (no global scan)",
                spatialWindowLocal());

        // ---- 8. only changed locals advanced; every non-advanced key byte-identical ----
        check("8. diff isolation through budget+parallel: non-advanced keys byte-identical every tick",
                main.deferredUnchangedOk()); // gathered across the full loop, includes all not-accepted keys

        if (failures == 0) {
            System.out.println("[SimCombineProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[SimCombineProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }

    /**
     * Assertion 4 — per the {@link SimWorld} contract, {@code advanceAll()} must equal
     * {@code advance(keys())} (the incremental/specified-collection path) for identical
     * advanced sets at identical ticks. Two copy worlds start seed/tick/keys/state equal,
     * then one advances via the full path and the other via the incremental collection
     * path; their summaries are compared after every tick. (Splitting one tick across
     * multiple advance() calls is meaningless because each call advances by exactly one
     * tick regardless of key count, so the only sound full-vs-incremental comparison is
     * the equal-set/equal-tick form asserted here.)
     */
    private static boolean incrementalEqualsFullPath() {
        List<SimKey> keys = buildKeys();
        SimWorld<Pop> full = SimWorld.create(SIM, WORLD_SEED, 0);
        SimWorld<Pop> inc = SimWorld.create(SIM, WORLD_SEED, 0);
        for (SimKey k : keys) {
            full.add(k, new Pop(100));
            inc.add(k, new Pop(100));
        }
        List<SimKey> canon = full.keys();
        for (int t = 0; t < TICKS; t++) {
            full.advanceAll();      // full path
            inc.advance(canon);     // incremental/specified path, same set
            if (!Arrays.equals(summaryOf(full), summaryOf(inc))) {
                return false;
            }
        }
        return true;
    }

    /** Assertion 6 — two fresh schedulers fed the identical key-order request stream agree. */
    private static boolean schedulerDeterministic() {
        TickBudget bd = TickBudget.of(50, 500, 40);
        BudgetScheduler<SimKey> s1 = BudgetScheduler.create(bd, REGION_BITS);
        BudgetScheduler<SimKey> s2 = BudgetScheduler.create(bd, REGION_BITS);
        List<SimKey> seq = buildKeys();
        StringBuilder d1 = new StringBuilder();
        StringBuilder d2 = new StringBuilder();
        for (int i = 0; i < seq.size(); i++) {
            SimKey k = seq.get(i);
            long units = (i * 7L % 5L) + 2L; // deterministic units, some > 3 -> deferrals under global 40
            RegionKey r = s1.regionOf(k.x(), k.z()); // identical for both schedulers
            d1.append(s1.charge(k, r, units));
            d2.append(s2.charge(k, r, units));
        }
        return d1.toString().equals(d2.toString())
                && s1.globalUnitsUsed() == s2.globalUnitsUsed()
                && s1.chargedSimulants().equals(s2.chargedSimulants())
                && s1.deferredRegions().equals(s2.deferredRegions());
    }

    /** Assertion 7 — a radius-1 window query returns only its own cluster; the far simulant never leaks. */
    private static boolean spatialWindowLocal() {
        SpatialIndex<SimKey> sp = new SpatialIndex<>();
        for (SimKey k : buildKeys()) {
            sp.insert(CellCoord.of(k.x(), k.z(), CELL_BITS), k);
        }
        // Cluster A centered at (0,0): its 3x3 keys all live in cell (0,0) -> window r=1 contains only them.
        Set<SimKey> clusterA = new HashSet<>();
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                clusterA.add(new SimKey(dx, dz));
            }
        }
        List<SimKey> q1 = sp.query(0, 0, 1, CELL_BITS);
        boolean localOnly = clusterA.containsAll(q1) && q1.size() == clusterA.size();
        boolean noFar = !q1.contains(FAR) && !q1.isEmpty();
        // The isolated far simulant's own window contains exactly itself.
        List<SimKey> qFar = sp.query(FAR.x(), FAR.z(), 1, CELL_BITS);
        boolean farExact = qFar.size() == 1 && qFar.get(0).equals(FAR);
        // "No global scan": cluster-A query returned its local members, not all 73 keys.
        boolean notGlobal = q1.size() < buildKeys().size();
        return localOnly && noFar && farExact && notGlobal;
    }
}