// Deterministic acceptance probe for the p.1.4.23 FlowSched pure-JDK scheduler
// port (io.toterra.subterra.engine.optim.sched). NOT shipped in the mod jar.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.optim.sched.executor.ExecutorManager;
import io.toterra.subterra.engine.optim.sched.scheduler.Cancellable;
import io.toterra.subterra.engine.optim.sched.scheduler.ItemHolder;
import io.toterra.subterra.engine.optim.sched.scheduler.ItemStatus;
import io.toterra.subterra.engine.optim.sched.scheduler.KeyStatusPair;
import io.toterra.subterra.engine.optim.sched.scheduler.ObjectFactory;
import io.toterra.subterra.engine.optim.sched.scheduler.StatusAdvancingScheduler;
import io.toterra.subterra.engine.optim.sched.structs.DynamicPriorityQueue;
import io.toterra.subterra.engine.optim.sched.structs.SimpleObjectPool;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Deterministic acceptance probe for the p.1.4.23 FlowSched pure-JDK scheduler
 * port (io.toterra.subterra.engine.optim.sched). Drives a minimal deterministic
 * StatusAdvancingScheduler on a probe-controlled gated executor and asserts the
 * advance / dependency / removal / cancellation / busy-gating contract, an
 * object-pool reuse invariant, and the ExecutorManager / WorkerThread +
 * DynamicPriorityQueue smoke behaviour (concurrency-safe, sequence-level).
 * Exit 0 = PASS, 1 = FAIL (never shipped in the mod jar).
 *
 * <p>Driver model: FlowSched advances items when {@code markDirty()} funnels a
 * tick through {@code getBackgroundExecutor()}; {@code wakeUp()} is a protected
 * no-op in this port. The probe therefore supplies a {@link GatedExecutor} and
 * drives advancement deterministically by flushing it on the test thread
 * (single-thread, fully deterministic), rather than racing a background thread.
 *
 * <p>p.1.4.23 FlowSched 纯 JDK 调度器移植（io.toterra.subterra.engine.optim.sched）的确定性
 * 验收探针。在探针自控的门控执行器上驱动一个最小确定性 StatusAdvancingScheduler，
 * 断言推进/依赖/移除/取消/忙碌门控契约、对象池复用不变式，以及 ExecutorManager /
 * WorkerThread + DynamicPriorityQueue 冒烟行为（并发安全、序列层面）。退出码 0 = PASS，
 * 1 = FAIL（永不随 mod jar 发布）。
 *
 * <p>驱动模型：FlowSched 通过 markDirty 把 tick 漏斗到 getBackgroundExecutor 来推进条目；
 * 本移植中 wakeUp 是受保护的空实现。因此探针提供 GatedExecutor，并在测试线程上
 * 同步 flush 驱动推进（单线程、完全确定），而非与后台线程竞态。
 */
public final class FlowSchedProbe {

    private FlowSchedProbe() {
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

    /** Shared completed stage for statuses that perform no async work. */
    static final CompletableFuture<Void> DONE = CompletableFuture.completedFuture(null);

    /** Minimal record carrying what the scheduler handed to makeContext. */
    record MiniCtx(String key, String statusName, int depCount, boolean isUpgrade) {
        @Override
        public String toString() {
            return key + "@" + statusName + (isUpgrade ? "+" : "-") + depCount;
        }
    }

    /**
     * Three-state machine UNLOADED -&gt; LOADED -&gt; FULL. Key "A" and target FULL
     * declare a dependency on key "B" having reached LOADED, so the dependency
     * ordering trace uses the very same statuses as the benign scenarios.
     */
    enum MiniStatus implements ItemStatus<String, Void, MiniCtx> {
        UNLOADED, LOADED, FULL;

        static final KeyStatusPair<String, Void, MiniCtx>[] NO_DEPS = new KeyStatusPair[0];

        @Override
        public ItemStatus<String, Void, MiniCtx>[] getAllStatuses() {
            return MiniStatus.values();
        }

        @Override
        public CompletableFuture<Void> upgradeToThis(MiniCtx ctx, Cancellable cancellable) {
            return DONE;
        }

        @Override
        public CompletableFuture<Void> postUpgradeToThis(MiniCtx ctx) {
            return DONE;
        }

        @Override
        public CompletableFuture<Void> preDowngradeFromThis(MiniCtx ctx, Cancellable cancellable) {
            return DONE;
        }

        @Override
        public CompletableFuture<Void> downgradeFromThis(MiniCtx ctx, Cancellable cancellable) {
            return DONE;
        }

        @Override
        public KeyStatusPair<String, Void, MiniCtx>[] getDependencies(ItemHolder<String, Void, MiniCtx, ?> holder) {
            if (this == FULL && "A".equals(holder.getKey())) {
                return new KeyStatusPair[]{new KeyStatusPair<>("B", LOADED)};
            }
            return NO_DEPS;
        }
    }

    /**
     * Minimal status machine for the cancellation/exception-handling path:
     * UNLOADED -&gt; GOOD -&gt; BROKEN. The BROKEN upgrade fails with a
     * CancellationException while the cancellable is NOT cancelled, which routes
     * the transaction into the default MARK_BROKEN handler (FLAG_BROKEN). This
     * matches the port: a cancelled cancellable + CancellationException is treated
     * as a clean cancellation that does NOT mark the holder broken.
     */
    enum CancelStatus implements ItemStatus<String, Void, MiniCtx> {
        UNLOADED, GOOD, BROKEN;

        static final KeyStatusPair<String, Void, MiniCtx>[] NO_DEPS = new KeyStatusPair[0];

        @Override
        public ItemStatus<String, Void, MiniCtx>[] getAllStatuses() {
            return CancelStatus.values();
        }

        @Override
        public CompletableFuture<Void> upgradeToThis(MiniCtx ctx, Cancellable cancellable) {
            if (this == BROKEN) {
                return CompletableFuture.failedFuture(new CancellationException("flow-sched-probe-broken"));
            }
            return DONE;
        }

        @Override
        public CompletableFuture<Void> postUpgradeToThis(MiniCtx ctx) {
            return DONE;
        }

        @Override
        public CompletableFuture<Void> preDowngradeFromThis(MiniCtx ctx, Cancellable cancellable) {
            return DONE;
        }

        @Override
        public CompletableFuture<Void> downgradeFromThis(MiniCtx ctx, Cancellable cancellable) {
            return DONE;
        }

        @Override
        public KeyStatusPair<String, Void, MiniCtx>[] getDependencies(ItemHolder<String, Void, MiniCtx, ?> holder) {
            return NO_DEPS;
        }
    }

    /** Always-able executor the probe drains explicitly on the test thread. */
    static final class GatedExecutor implements Executor {
        private final ArrayDeque<Runnable> pending = new ArrayDeque<>();

        int flush() {
            int ran = 0;
            Runnable r;
            while ((r = pending.poll()) != null) {
                r.run();
                ran++;
            }
            return ran;
        }

        boolean isEmpty() {
            return pending.isEmpty();
        }

        @Override
        public void execute(Runnable command) {
            pending.add(command);
        }
    }

    /** Tracks how often the scheduler consults ObjectFactory for set/ticket storage. */
    static final class CountingFactory extends ObjectFactory.DefaultObjectFactory {
        private final AtomicInteger sets = new AtomicInteger();

        @Override
        public <E> Set<E> createConcurrentSet() {
            sets.incrementAndGet();
            return super.createConcurrentSet();
        }

        int setCount() {
            return sets.get();
        }
    }

    /** Minimal deterministic scheduler wrapping a GatedExecutor and shared event log. */
    static final class MiniScheduler extends StatusAdvancingScheduler<String, Void, MiniCtx, Void> {
        final GatedExecutor executor;
        final List<String> events = Collections.synchronizedList(new ArrayList<>());
        final ItemStatus<String, Void, MiniCtx>[] allStatus;
        final ObjectFactory factory;

        MiniScheduler(ObjectFactory factory, ItemStatus<String, Void, MiniCtx>[] allStatus) {
            super(factory);
            this.factory = factory;
            this.allStatus = allStatus;
            this.executor = new GatedExecutor();
        }

        MiniScheduler(ItemStatus<String, Void, MiniCtx>[] allStatus) {
            this(new ObjectFactory.DefaultObjectFactory(), allStatus);
        }

        @Override
        protected Executor getBackgroundExecutor() {
            return executor;
        }

        @Override
        protected ItemStatus<String, Void, MiniCtx> getUnloadedStatus() {
            return allStatus[0];
        }

        @Override
        protected MiniCtx makeContext(ItemHolder<String, Void, MiniCtx, Void> holder,
                                      ItemStatus<String, Void, MiniCtx> nextStatus,
                                      KeyStatusPair<String, Void, MiniCtx>[] dependencies,
                                      boolean isUpgrade) {
            return new MiniCtx(holder.getKey(), nextStatus.toString(), dependencies.length, isUpgrade);
        }

        @Override
        protected void onItemUpgrade(ItemHolder<String, Void, MiniCtx, Void> holder,
                                     ItemStatus<String, Void, MiniCtx> statusReached) {
            ItemStatus<String, Void, MiniCtx> prev = statusReached.getPrev();
            events.add(holder.getKey() + ":" + (prev == null ? "nil" : prev.toString()) + "->" + statusReached.toString());
        }

        @Override
        protected void onItemDowngrade(ItemHolder<String, Void, MiniCtx, Void> holder,
                                       ItemStatus<String, Void, MiniCtx> statusReached) {
            ItemStatus<String, Void, MiniCtx> nxt = statusReached.getNext();
            events.add(holder.getKey() + ":" + statusReached.toString() + "->" + (nxt == null ? "nil" : nxt.toString()));
        }

        ItemStatus<String, Void, MiniCtx> byName(String n) {
            for (ItemStatus<String, Void, MiniCtx> s : allStatus) {
                if (s.toString().equals(n)) {
                    return s;
                }
            }
            throw new IllegalStateException("no such status: " + n);
        }
    }

    /** Flush the gated executor until a predicate holds; timeout guards against hangs. */
    static boolean driveUntil(MiniScheduler s, BooleanSupplier done, long timeoutMs) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (true) {
            if (done.getAsBoolean()) {
                return true;
            }
            if (System.nanoTime() > deadline) {
                return false;
            }
            s.executor.flush();
        }
    }

    static boolean allFull(MiniScheduler s, String[] keys) {
        for (String k : keys) {
            ItemHolder<String, Void, MiniCtx, Void> h = s.getHolder(k);
            if (h == null || h.getStatus() != s.byName("FULL")) {
                return false;
            }
        }
        return true;
    }

    static int indexOf(List<String> events, String needle) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).equals(needle)) {
                return i;
            }
        }
        return -1;
    }

    static String snapshot(MiniScheduler s, String[] keys) {
        TreeMap<String, String> m = new TreeMap<>();
        for (String k : keys) {
            ItemHolder<String, Void, MiniCtx, Void> h = s.getHolder(k);
            m.put(k, h == null ? "ABSENT" : h.getStatus().toString());
        }
        return m.toString();
    }

    public static void main(String[] args) {
        String[] k1 = {"1", "2", "3"};

        // ---- 1 advance determinism + stable re-run ---------------------------------
        CountingFactory fac = new CountingFactory();
        MiniScheduler s1 = new MiniScheduler(fac, MiniStatus.values());
        @SuppressWarnings("unchecked")
        ItemHolder<String, Void, MiniCtx, Void>[] h1 = new ItemHolder[3];
        for (int i = 0; i < 3; i++) {
            h1[i] = s1.addTicket(k1[i], s1.byName("FULL"), StatusAdvancingScheduler.NO_OP);
        }
        boolean d1 = driveUntil(s1, () -> allFull(s1, k1), 5000);
        check("determinism: run1 all three keys reach FULL", d1);
        check("determinism: future.getFutureForStatus(FULL) completed (run1)",
                h1[0].getFutureForStatus(s1.byName("FULL")).isDone());
        check("object-factory: scheduler consults ObjectFactory.createConcurrentSet", fac.setCount() > 0);
        List<String> ev1 = new ArrayList<>(s1.events);

        MiniScheduler s2 = new MiniScheduler(MiniStatus.values());
        for (int i = 0; i < 3; i++) {
            s2.addTicket(k1[i], s2.byName("FULL"), StatusAdvancingScheduler.NO_OP);
        }
        boolean d2 = driveUntil(s2, () -> allFull(s2, k1), 5000);
        List<String> ev2 = new ArrayList<>(s2.events);
        check("determinism: run2 (fresh instance) all keys reach FULL", d2);
        check("determinism: two runs produce byte-identical event sequences", ev1.equals(ev2));

        // ---- 2 dependency ordering -----------------------------------------------
        MiniScheduler s3 = new MiniScheduler(MiniStatus.values());
        s3.addTicket("B", s3.byName("FULL"), StatusAdvancingScheduler.NO_OP);
        s3.addTicket("A", s3.byName("FULL"), StatusAdvancingScheduler.NO_OP);
        boolean depDone = driveUntil(s3, () -> allFull(s3, new String[]{"A", "B"}), 5000);
        int aFull = indexOf(s3.events, "A:LOADED->FULL");
        int bFull = indexOf(s3.events, "B:LOADED->FULL");
        check("dependency: A and B both reach FULL", depDone);
        check("dependency: A FULL event strictly after B FULL",
                aFull >= 0 && bFull >= 0 && aFull > bFull);

        // ---- 3 removeTicket ------------------------------------------------------
        MiniScheduler s4 = new MiniScheduler(MiniStatus.values());
        s4.addTicket("C", s4.byName("FULL"), StatusAdvancingScheduler.NO_OP);
        s4.removeTicket("C", s4.byName("FULL"));
        s4.executor.flush();
        ItemHolder<String, Void, MiniCtx, Void> c = s4.getHolder("C");
        check("removeTicket: C not advanced (absent or still UNLOADED)",
                c == null || c.getStatus() == s4.byName("UNLOADED"));
        check("removeTicket: no C events recorded",
                s4.events.stream().noneMatch(e -> e.contains("C")));

        // ---- 4 cancellation -> FLAG_BROKEN (MARK_BROKEN handler) ------------------
        MiniScheduler s5 = new MiniScheduler(CancelStatus.values());
        ItemHolder<String, Void, MiniCtx, Void> hz =
                s5.addTicket("Z", s5.byName("BROKEN"), StatusAdvancingScheduler.NO_OP);
        MiniScheduler broken = s5;
        boolean broke = driveUntil(s5, () -> (hz.getFlags() & ItemHolder.FLAG_BROKEN) != 0
                && !hz.isBusy() && hz.getStatus() == broken.byName("GOOD"), 5000);
        check("cancel/broken: FLAG_BROKEN set, stalled at GOOD, not busy (no hang)", broke);
        broken.executor.flush();
        check("cancel/broken: stays GOOD after further driving (FLAG_BROKEN gating holds)",
                hz.getStatus() == broken.byName("GOOD") && (hz.getFlags() & ItemHolder.FLAG_BROKEN) != 0);

        // ---- 5 busy gating -------------------------------------------------------
        MiniScheduler s6 = new MiniScheduler(MiniStatus.values());
        ItemHolder<String, Void, MiniCtx, Void> hx =
                s6.addTicket("x", s6.byName("LOADED"), StatusAdvancingScheduler.NO_OP);
        MiniScheduler busy = s6;
        check("busy: initial upgrade to LOADED converges",
                driveUntil(s6, () -> hx.getStatus() == busy.byName("LOADED"), 5000));
        CompletableFuture<Void> op = new CompletableFuture<>();
        hx.submitOp(op);
        check("busy: submitOp => holder.isBusy() == true", hx.isBusy());
        s6.removeTicket("x", s6.byName("LOADED"));
        s6.executor.flush();
        check("busy: while op pending, remove-ticket does NOT downgrade (still LOADED)",
                hx.getStatus() == busy.byName("LOADED"));
        op.complete(null);
        boolean downgraded = driveUntil(s6, () -> s6.getHolder("x") == null, 5000);
        check("busy: after op completes, holder downgrades away (removed from map)", downgraded);

        // ---- 6 object pool reuse ------------------------------------------------
        SimpleObjectPool<StringBuilder> pool = new SimpleObjectPool<>(
                poolRef -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append('x');
                    return sb;
                },
                sb -> sb.setLength(0),
                sb -> sb.setLength(0),
                4);
        StringBuilder a = pool.alloc();
        a.append("hello");
        pool.release(a);
        StringBuilder b = pool.alloc();
        check("pool: released instance identity-reused on next alloc", a == b);
        check("pool: initializer applied on alloc (length reset to 0)", b.length() == 0);

        // ---- 7 ExecutorManager / WorkerThread + DynamicPriorityQueue smoke --------
        ExecutorManager em = new ExecutorManager(4, thread -> thread.setDaemon(true));
        AtomicInteger count = new AtomicInteger();
        AtomicInteger mainHit = new AtomicInteger();
        Set<String> workerNames = Collections.synchronizedSet(new TreeSet<>());
        String mainName = Thread.currentThread().getName();
        final int N = 2000;
        for (int i = 0; i < N; i++) {
            em.schedule(() -> {
                count.incrementAndGet();
                String n = Thread.currentThread().getName();
                workerNames.add(n);
                if (n.equals(mainName)) {
                    mainHit.incrementAndGet();
                }
            }, 4);
        }
        long emDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(10000);
        while (count.get() < N && System.nanoTime() < emDeadline) {
            Thread.yield();
        }
        em.shutdown();
        check("executor: all 2000 scheduled runnables executed", count.get() == N);
        check("executor: tasks ran on worker threads (none on main)", mainHit.get() == 0);
        int distinctWorkers = workerNames.size();
        check("executor: 1..4 distinct worker threads observed", distinctWorkers >= 1 && distinctWorkers <= 4);

        DynamicPriorityQueue<Integer> dpq = new DynamicPriorityQueue<>(8);
        Random rnd = new Random(42L);
        Map<Integer, Integer> prio = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            int p = rnd.nextInt(8);
            dpq.enqueue(i, p);
            prio.put(i, p);
        }
        boolean monotonic = true;
        int last = -1;
        int drained = 0;
        Integer e;
        while ((e = dpq.dequeue()) != null) {
            int p = prio.remove(e);
            if (p < last) {
                monotonic = false;
            }
            last = p;
            drained++;
        }
        check("dpq: 1000 pops in non-decreasing priority order, drains to empty",
                monotonic && drained == 1000 && dpq.size() == 0);

        // ---- 8 two independent instances, identical final states -----------------
        String[] ke = {"1", "2", "3"};
        MiniScheduler se1 = new MiniScheduler(MiniStatus.values());
        MiniScheduler se2 = new MiniScheduler(MiniStatus.values());
        for (String k : ke) {
            se1.addTicket(k, se1.byName("FULL"), StatusAdvancingScheduler.NO_OP);
            se2.addTicket(k, se2.byName("FULL"), StatusAdvancingScheduler.NO_OP);
        }
        boolean e1 = driveUntil(se1, () -> allFull(se1, ke), 5000);
        boolean e2 = driveUntil(se2, () -> allFull(se2, ke), 5000);
        check("instances: two independent schedulers converge to identical final status set",
                e1 && e2 && snapshot(se1, ke).equals(snapshot(se2, ke)));

        if (failures == 0) {
            System.out.println("[FlowSchedProbe] PASS (" + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[FlowSchedProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }
}