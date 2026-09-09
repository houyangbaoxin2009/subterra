package io.toterra.subterra.runtime.sim;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import io.toterra.subterra.Subterra;
import io.toterra.subterra.engine.parallel.ParallelRunner;
import io.toterra.subterra.engine.parallel.TaskResult;
import io.toterra.subterra.engine.sim.budget.BudgetScheduler;
import io.toterra.subterra.engine.sim.budget.BudgetScheduler.ChargeResult;
import io.toterra.subterra.engine.sim.budget.RegionKey;
import io.toterra.subterra.engine.sim.budget.TickBudget;
import io.toterra.subterra.engine.sim.core.PerSimRandom;
import io.toterra.subterra.engine.sim.core.SimKey;
import io.toterra.subterra.engine.sim.core.SimWorld;
import io.toterra.subterra.engine.sim.core.Simulator;
import io.toterra.subterra.engine.sim.run.SimRunner;
import io.toterra.subterra.engine.sim.spatial.CellCoord;
import io.toterra.subterra.engine.sim.spatial.SpatialIndex;
import io.toterra.subterra.engine.worldgen.pipeline.noise.XoroRandom;

/**
 * p.2.8.6 runtime 壳：engine.sim 微型确定性校验 MC 壳（默认不接管，渐进增强）。只做接线验证——
 * 在 {@code ServerStartedEvent} 上于真实游戏 JVM 内<em>组合</em> engine.sim 四模块（p.2.8.1
 * budget / p.2.8.2 spatial / p.2.8.3 core / p.2.8 run 门面），用固定 3–4 个 simulant 的 mini
 * 场景推进 2 tick，证明底座在游戏 JVM 装载且契约保持。门控 {@code -Dsubterra.probe.sim}：不设则
 * 完全 no-op，对原版模拟/生命周期零影响（也不与任何既有 marker 交互——async/network/save marker
 * 及各自 original-path 保持字节原样）。门控开启时向 stdout 打确定性 marker（探针按前缀
 * {@code [Subterra sim]} 匹配）。经 {@code @EventBusSubscriber} 自注册到 NeoForge 游戏总线
 * （与 AsyncChunkRuntime/EnhancedChannelRuntime/SaveRuntime 同风格，无需改 Subterra.java）；
 * 任何异常被兜底为 {@code error:...} marker，绝不断言中断服务启动。{@code ServerStoppingEvent}
 * 上打印确定性关停 marker（本壳无持有状态，纯 no-op）。依赖铁律：runtime 可依赖 engine
 * （{@code engine.sim.*}）与 MC 事件，禁依赖 migrate/devkit。
 * <p>
 * 本壳刻意不含游戏内模拟循环、也不接管任何游戏路径；不确定性/性能取舍由纯 JVM 探针
 * （devkit 侧）证明。引擎导入仅限 engine.sim 与支撑的 engine.parallel / noise。
 * <p>
 * p.2.8.6 runtime shell: a tiny deterministic-check shell for {@code engine.sim} inside a real
 * game JVM (off by default — vanilla simulation/lifecycle untouched, progressive enhancement).
 * On {@code ServerStartedEvent} it <em>composes</em> all four engine.sim modules (p.2.8.1
 * budget / p.2.8.2 spatial / p.2.8.3 core / p.2.8 SimRunner facade) against a fixed 3–4 simulant
 * mini-scene advanced for 2 ticks, proving the base loads in the game JVM and its contract holds.
 * Gated by {@code -Dsubterra.probe.sim}: absent → fully no-op (never touches existing markers —
 * the async/network/save markers plus their original-path lines stay byte-identical). When gated
 * on it prints deterministic markers to stdout (probes match by prefix {@code [Subterra sim]}).
 * It self-registers on the NeoForge game bus via {@code @EventBusSubscriber} (same style as
 * AsyncChunkRuntime / EnhancedChannelRuntime / SaveRuntime — no Subterra.java edit); any throwable
 * is caught and reported as an {@code error:...} marker rather than breaking the boot gate. On
 * {@code ServerStoppingEvent} it prints a deterministic shutdown marker (no held state — the shell
 * is a pure no-op). Dependency rule: the runtime may depend on engine ({@code engine.sim.*}) and MC
 * events, never on migrate/devkit.
 * <p>
 * This shell deliberately has no in-game simulation loop and does not own any game path; the
 * determinism/cost trade-offs are proven by pure-JVM probes (devkit side). Engine imports are
 * limited to engine.sim plus the supporting engine.parallel / noise.
 */
@EventBusSubscriber(modid = Subterra.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SimRuntime {

    /** 探针 marker 前缀 / probe marker prefix. */
    public static final String MARKER = "[Subterra sim]";

    /** Fixed master world seed shared by every micro-check (deterministic goldens). */
    private static final long SIM_SEED = 44905237L;

    private SimRuntime() {
    }

    /**
     * 探针门控：{@code -Dsubterra.probe.sim} 存在且非空且非 {@code 0}/{@code false} 即视为开启
     * （E2E 走 {@code -Psubterra.probe.sim=1}）。缺失 / 空白 → no-op。Probe gate: enabled when
     * {@code -Dsubterra.probe.sim} is present, non-blank and not {@code 0}/{@code false}
     * (E2E uses {@code -Psubterra.probe.sim=1}). Absent/blank → no-op.
     */
    private static boolean simProbeGated() {
        String v = System.getProperty("subterra.probe.sim");
        if (v == null || v.isBlank()) {
            return false;
        }
        String t = v.trim();
        return !"0".equals(t) && !"false".equalsIgnoreCase(t);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        if (!simProbeGated()) {
            return; // no probe gate -> zero impact; vanilla simulation/lifecycle untouched
        }
        try {
            // 1) 栅门开启 + 组合校验：engine.sim 四模块各做一微型确定性断言（ok/bad，绝不抛出）。
            // Gate-on + composed check: one tiny deterministic assertion per engine.sim module.
            print("sim-shell-gate=on");
            boolean ok = parallelVsSerial()
                    & incrementalVsFull()
                    & budgetDeferral()
                    & spatialWindow();
            // 2) 收尾 marker：全部 ok 才报 composed-ok；Bob 语义绝不断言中断启动。
            // Wrap-up marker: only when every micro-check is ok; never breaks the boot gate.
            print(ok ? "sim-core-composed-ok" : "bad:sim-core-composed");
        } catch (Throwable t) {
            // never propagate past the event dispatch; the boot gate stays green
            print("error:sim " + t);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (!simProbeGated()) {
            return; // no probe gate -> zero impact
        }
        try {
            // 无持有状态 -> 确定性 no-op 关停 marker。
            // No held state -> deterministic no-op shutdown marker.
            print("sim-shell-shutdown");
        } catch (Throwable t) {
            // never propagate past the event dispatch; the boot gate stays green
            print("error:sim-shutdown " + t);
        }
    }

    // ---------- engine.sim micro-checks (all no-throw; print ok/bad and return a boolean) ----------

    /**
     * p.2.8 SimRunner facade: parallel {@code run} vs serial golden {@code runSerial} are
     * element-for-element, byte-for-byte identical for the same key set. Prints
     * {@code sim-core-parallel-vs-serial=ok|bad}.
     */
    private static boolean parallelVsSerial() {
        SimRunner runner = SimRunner.create(4);
        List<SimKey> keys = List.of(new SimKey(0, 0), new SimKey(1, 0), new SimKey(0, 1));
        ParallelRunner.KeyFunction<SimKey, byte[]> fn = key -> {
            XoroRandom r = PerSimRandom.forTick(SIM_SEED, 1L, key);
            byte[] b = new byte[8];
            for (int i = 0; i < b.length; i++) {
                b[i] = (byte) (r.nextInt(256) & 0xFF);
            }
            return b;
        };
        List<TaskResult<SimKey, byte[]>> par = runner.run(keys, fn);
        List<TaskResult<SimKey, byte[]>> ser = runner.runSerial(keys, fn);
        boolean ok = par.size() == ser.size();
        if (ok) {
            for (int i = 0; i < par.size(); i++) {
                if (!par.get(i).key().equals(ser.get(i).key())
                        || !Arrays.equals(par.get(i).result(), ser.get(i).result())) {
                    ok = false;
                    break;
                }
            }
        }
        print("sim-core-parallel-vs-serial=" + (ok ? "ok" : "bad"));
        return ok;
    }

    /**
     * p.2.8.3 incremental core: {@code advanceAll()} ≡ {@code advance(keys())} — advancing a whole
     * world is byte-identical to advancing its key set in canonical order. Prints
     * {@code sim-core-incremental-vs-full=ok|bad}.
     */
    private static boolean incrementalVsFull() {
        Simulator<SimState> sim = (tick, key, rand, state) ->
                new SimState(state.v + 1 + rand.nextInt(3));
        SimWorld<SimState> fullW = SimWorld.create(sim, SIM_SEED, 0L)
                .add(new SimKey(0, 0), new SimState(1))
                .add(new SimKey(1, 1), new SimState(5))
                .add(new SimKey(-2, 3), new SimState(9));
        Map<SimKey, SimState> full = fullW.advanceAll();
        SimWorld<SimState> incrW = SimWorld.create(sim, SIM_SEED, 0L)
                .add(new SimKey(0, 0), new SimState(1))
                .add(new SimKey(1, 1), new SimState(5))
                .add(new SimKey(-2, 3), new SimState(9));
        Map<SimKey, SimState> incr = incrW.advance(incrW.keys());
        boolean ok = full.equals(incr);
        print("sim-core-incremental-vs-full=" + (ok ? "ok" : "bad"));
        return ok;
    }

    /**
     * p.2.8.1 budget: builds a scenario where a second charge on the same simulant <em>must</em> be
     * rejected (DEFERRED) and asserts nothing is recorded for the rejected unit
     * (atomic all-or-nothing). Prints {@code sim-core-budget-deferral=ok|bad}.
     */
    private static boolean budgetDeferral() {
        TickBudget budget = TickBudget.of(10, 100, 1000);
        BudgetScheduler<SimKey> sched = BudgetScheduler.create(budget, 5);
        RegionKey region = sched.regionOf(1000, 1000); // world coords -> region (regionBits = 5)
        SimKey key = new SimKey(0, 0);
        ChargeResult accepted = sched.charge(key, region, 10L); // at the simulant cap exactly
        ChargeResult deferred = sched.charge(key, region, 1L);  // 1 > 10 - 10 = 0 -> DEFERRED
        boolean ok = accepted == ChargeResult.ACCEPTED
                && deferred == ChargeResult.DEFERRED
                && sched.chargedSimulants().contains(key)
                && sched.globalUnitsUsed() == 10L;
        print("sim-core-budget-deferral=" + (ok ? "ok" : "bad"));
        return ok;
    }

    /**
     * p.2.8.2 spatial: a window query returns <em>only</em> the cells within the window — a far-away
     * key stored outside the radius is never visited. Prints
     * {@code sim-core-spatial-local-window=ok|bad}.
     */
    private static boolean spatialWindow() {
        final int cellBits = 8;
        SpatialIndex<SimKey> idx = new SpatialIndex<>();
        CellCoord near = CellCoord.of(0, 0, cellBits);
        idx.insert(near, new SimKey(0, 0));
        idx.insert(near, new SimKey(1, 0));
        idx.insert(CellCoord.of(100000, 100000, cellBits), new SimKey(50, 50)); // far away
        List<SimKey> win = idx.query(0, 0, 1, cellBits);
        boolean ok = win.size() == 2
                && win.contains(new SimKey(0, 0))
                && win.contains(new SimKey(1, 0))
                && !win.contains(new SimKey(50, 50));
        print("sim-core-spatial-local-window=" + (ok ? "ok" : "bad"));
        return ok;
    }

    private static void print(String body) {
        System.out.println(MARKER + " " + body);
    }

    /** Immutable per-simulant state for the incremental micro-check (pure-function input). */
    private record SimState(int v) {
    }
}