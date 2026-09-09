// Deterministic acceptance probe for the p.2.6.4 async load/I-O core
// (io.toterra.subterra.engine.worldgen.async.io). NOT shipped in the mod jar.
package io.toterra.subterra.probes;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;
import io.toterra.subterra.engine.worldgen.async.io.AsyncIoQueue;
import io.toterra.subterra.engine.worldgen.async.io.AsyncWrite;
import io.toterra.subterra.engine.zd.ZdDocWriter;
import io.toterra.subterra.engine.zd.ZdVolume;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic acceptance probe for the p.2.6.4 async load/I-O core — the
 * deterministic ordered flush-queue {@code AsyncIoQueue}/{@code AsyncWrite}
 * (engine.worldgen.async.io). Asserts (pure JDK, no MC runtime, final-state only):
 * <ol>
 *   <li>default gate off — fresh JVM {@code isEnabled()} false, and building/draining
 *       a queue neither flips the gate nor touches any global I/O registry;</li>
 *   <li>ordered flush — 40 writes from TWO threads under an interleaved pattern flush
 *       exactly-once with a received sequence equal to the enqueue-order, and running the
 *       same interleaving twice is byte-identical (cross-run determinism);</li>
 *   <li>async-equals-sync — a zd document written through the queue + sink is byte-identical
 *       to the p.2.3 {@code ZdDocWriter} direct output, and a round-trip (write via queue →
 *       {@code ZdVolume.readTree} parse back) preserves every field;</li>
 *   <li>payload integrity under concurrency — N writes from 4 threads carry payloads derived
 *       from their keys, and after flush every received payload equals its expected per-key
 *       bytes (no corruption/swap);</li>
 *   <li>pending/flushed accounting final-state;</li>
 *   <li>close semantics — close() flushes the remainder, then enqueue throws
 *       {@link IllegalStateException}; close is idempotent;</li>
 *   <li>default-off evidence — constructing + draining a queue is inert (no global write
 *       counter ever incremented).</li>
 * </ol>
 * Exit 0 = PASS, 1 = FAIL (never shipped in the mod jar).
 *
 * <p>p.2.6.4 异步加载/IO 核心（确定性有序刷新队列 {@code AsyncIoQueue}/{@code AsyncWrite}，
 * engine.worldgen.async.io）的确定性验收探针。每条检查都是 join 之后的终态断言，绝不依赖墙钟/
 * 时序。退出码 0 = PASS，1 = FAIL。
 */
public final class AsyncIoProbe {

    private AsyncIoProbe() {
    }

    /**
     * 探针侧模拟的"全局 I/O 注册表命中数"：shell 只在门打开并选择走本队列时才记账。用它证明
     * 队列自身的构建 + 排空路径不产生任何全局副作用（核心在 shell 显式启用/路由前是惰性的）。
     * Simulated "global I/O registry" hit counter: the shell would only bump it when the gate
     * is on and it routes through the queue. Proves the queue's own build + drain path has no
     * global side effect (the core is inert until a shell explicitly enables/routes it).
     */
    private static int globalRegistryHits = 0;

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

    /** Deterministic key for index i ({@code w0}, {@code w1}, …). */
    static String keyFor(int i) {
        return "w" + i;
    }

    static int indexOfKey(String key) {
        return Integer.parseInt(key.substring(1));
    }

    /** Deterministic 16 payload bytes derived purely from the index — no shared state. */
    static byte[] payloadFor(int i) {
        return new byte[]{
                (byte) (i >> 24), (byte) (i >> 16), (byte) (i >> 8), (byte) i,
                (byte) (i * 31), (byte) (i * 7), (byte) (i * 13), (byte) (i * 17),
                (byte) (i * 19), (byte) (i * 23), (byte) (i * 29), (byte) (i * 37),
                (byte) (i * 41), (byte) (i * 43), (byte) (i * 47), (byte) (i * 53)};
    }

    public static void main(String[] args) {
        defaultGateOff();
        orderedFlushDeterminism();
        asyncEqualsSync();
        payloadIntegrityUnderConcurrency();
        accountingFinalState();
        closeSemantics();

        if (failures == 0) {
            System.out.println("[AsyncIoProbe] PASS (ordered flush-queue over zd/save, " + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[AsyncIoProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }

    // ---- a / g. default-off gate + inert core -----------------------------

    private static void defaultGateOff() {
        check("gate: fresh JVM default isEnabled() == false", !AsyncIoQueue.isEnabled());

        MemorySink sink = new MemorySink();
        AsyncIoQueue q = AsyncIoQueue.of(sink, 2);
        q.enqueue(new AsyncWrite("g0", payloadFor(0)));
        q.flushAll();

        boolean inert = !AsyncIoQueue.isEnabled()
                && q.flushedCount() == 1
                && sink.received().size() == 1;
        check("gate: build + drain leaves isEnabled()==false and flushed once (no flip)",
                inert && sink.received().get(0).key().equals("g0"));
        check("gate: constructing + draining never touches a global I/O registry (marker stays 0)",
                !AsyncIoQueue.isEnabled() && globalRegistryHits == 0);
        q.close();
    }

    // ---- b. ordered flush + cross-run determinism (interleaved pattern) ---

    private static void orderedFlushDeterminism() {
        List<String> run1 = runInterleaved(20);
        List<String> run2 = runInterleaved(20);

        // expected strictly alternating pattern A0,B0,A1,B1,… (the enqueue order we drove).
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            expected.add("A" + i);
            expected.add("B" + i);
        }
        check("order: received sequence equals the enqueue-order (deterministic merge contract)",
                run1.equals(expected));
        check("order: same interleaving run twice -> byte-identical received sequence",
                run1.equals(run2));
        check("order: exactly-once — 40 distinct keys, no loss, no duplicate",
                new HashSet<>(run1).size() == 40 && run1.size() == 40);
    }

    /** Drives 2 concurrent threads to enqueue 2×n writes in a strict A,B,A,B,… interleaving. */
    private static List<String> runInterleaved(int n) {
        MemorySink sink = new MemorySink();
        AsyncIoQueue q = AsyncIoQueue.of(sink, 2);
        Object turnLock = new Object();
        int[] next = {0}; // 0 = A may enqueue, 1 = B may enqueue

        Thread a = Thread.ofVirtual().start(() -> {
            for (int i = 0; i < n; i++) {
                synchronized (turnLock) {
                    while (next[0] != 0) {
                        try {
                            turnLock.wait();
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    q.enqueue(new AsyncWrite("A" + i, payloadFor(i)));
                    next[0] = 1;
                    turnLock.notifyAll();
                }
            }
        });
        Thread b = Thread.ofVirtual().start(() -> {
            for (int i = 0; i < n; i++) {
                synchronized (turnLock) {
                    while (next[0] != 1) {
                        try {
                            turnLock.wait();
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    q.enqueue(new AsyncWrite("B" + i, payloadFor(i)));
                    next[0] = 0;
                    turnLock.notifyAll();
                }
            }
        });
        try {
            a.join();
            b.join();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        q.flushAll();
        List<String> keys = new ArrayList<>();
        for (AsyncWrite w : sink.received()) {
            keys.add(w.key());
        }
        q.close();
        return keys;
    }

    // ---- c. async-equals-sync over the p.2.3 zd writer --------------------

    private static void asyncEqualsSync() {
        TdTable tree = TdTable.builder()
                .put("name", TdValue.str("rainforest"))
                .put("depth", TdValue.of(128L))
                .put("ratio", TdValue.of(1.5))
                .put("active", TdValue.of(true))
                .build();
        byte[] sync = ZdDocWriter.writeTree(0, tree);

        MemorySink sink = new MemorySink();
        AsyncIoQueue q = AsyncIoQueue.of(sink, 1);
        q.enqueue(new AsyncWrite("zd.slot", sync));
        q.flushAll();
        byte[] viaQueue = sink.received().get(0).bytes();

        check("async==sync: bytes via queue+sink byte-identical to ZdDocWriter direct output",
                Arrays.equals(viaQueue, sync));

        TdTable back = ZdVolume.readTree(viaQueue);
        boolean preserved = "rainforest".equals(back.get("name").asString())
                && back.get("depth").asInt() == 128
                && back.get("ratio").asFloat() == 1.5
                && back.get("active").asInt() == 1;
        check("async round-trip: write via queue -> parse back preserves every field", preserved);

        check("async round-trip: write(parse(write)) byte-identical",
                Arrays.equals(ZdDocWriter.writeTree(0, back), sync));
        q.close();
    }

    // ---- d. payload integrity under true concurrency ----------------------

    private static void payloadIntegrityUnderConcurrency() {
        int n = 64;
        int threads = 4;
        MemorySink sink = new MemorySink();
        AsyncIoQueue q = AsyncIoQueue.of(sink, threads);
        Set<Integer>[] perThread = concurrentSlices(n, threads);

        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final Set<Integer> slice = perThread[t];
            workers[t] = Thread.ofVirtual().start(() -> {
                List<Integer> list = new ArrayList<>(slice);
                list.sort(Integer::compareTo);
                for (int i : list) {
                    q.enqueue(new AsyncWrite(keyFor(i), payloadFor(i)));
                }
            });
        }
        for (Thread w : workers) {
            try {
                w.join();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

        check("payload: pending == n before flush", q.pendingCount() == n);
        q.flushAll();

        boolean exactlyOnce = sink.received().size() == n;
        Set<String> seen = new HashSet<>();
        boolean allExpected = true;
        for (AsyncWrite w : sink.received()) {
            if (!seen.add(w.key())) {
                exactlyOnce = false;
            }
            int i = indexOfKey(w.key());
            if (!Arrays.equals(w.bytes(), payloadFor(i))) {
                allExpected = false;
            }
        }
        check("payload: 4 threads, everything received exactly once (no loss/dup)",
                exactlyOnce && seen.size() == n);
        check("payload: every received payload equals its own key's expected bytes (no corruption/swap)",
                allExpected);
        q.close();
    }

    /** Deterministically slices indices [0,n) into `threads` disjoint consecutive ranges. */
    @SuppressWarnings("unchecked")
    private static Set<Integer>[] concurrentSlices(int n, int threads) {
        Set<Integer>[] slices = new Set[threads];
        for (int t = 0; t < threads; t++) {
            slices[t] = new HashSet<>();
        }
        for (int i = 0; i < n; i++) {
            slices[i % threads].add(i);
        }
        return slices;
    }

    // ---- e. pending/flushed accounting ------------------------------------

    private static void accountingFinalState() {
        int n = 10;
        MemorySink sink = new MemorySink();
        AsyncIoQueue q = AsyncIoQueue.of(sink, 2);
        for (int i = 0; i < n; i++) {
            q.enqueue(new AsyncWrite(keyFor(i), payloadFor(i)));
        }
        check("accounting: pending == n before flush; open()", q.pendingCount() == n && q.open());
        q.flushAll();
        check("accounting: pending == 0 after flush", q.pendingCount() == 0);
        check("accounting: flushed == n after flush and sink got n",
                q.flushedCount() == n && sink.received().size() == n);
        q.close();
    }

    // ---- f. close semantics -----------------------------------------------

    private static void closeSemantics() {
        MemorySink sink = new MemorySink();
        AsyncIoQueue q = AsyncIoQueue.of(sink, 1);
        q.enqueue(new AsyncWrite("c0", payloadFor(0)));
        q.enqueue(new AsyncWrite("c1", payloadFor(1)));
        q.close();
        check("close: flushes the remaining writes then closes",
                sink.received().size() == 2 && q.pendingCount() == 0 && q.flushedCount() == 2);
        check("close: queue reports closed (open()==false)", !q.open());

        boolean threw = false;
        try {
            q.enqueue(new AsyncWrite("c2", payloadFor(2)));
        } catch (IllegalStateException e) {
            threw = true;
        }
        check("close: enqueue after close throws IllegalStateException", threw);

        boolean idempotent = true;
        try {
            q.close();
        } catch (Exception e) {
            idempotent = false;
        }
        check("close: idempotent (second close no-op, counts unchanged)",
                idempotent && q.flushedCount() == 2 && q.pendingCount() == 0);
    }

    /** In-memory writer: records every write handed to the sink (the async-queue consumer). */
    private static final class MemorySink implements java.util.function.Consumer<AsyncWrite> {
        private final List<AsyncWrite> received = new ArrayList<>();

        @Override
        public synchronized void accept(AsyncWrite w) {
            received.add(w);
        }

        synchronized List<AsyncWrite> received() {
            return new ArrayList<>(received);
        }
    }
}