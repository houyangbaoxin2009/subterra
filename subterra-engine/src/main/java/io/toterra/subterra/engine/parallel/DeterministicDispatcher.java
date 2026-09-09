// Async dispatch design derives from C2ME (MIT, Copyright (c) ishland, https://github.com/ishland/C2ME);
// implementation is original Subterra code.
package io.toterra.subterra.engine.parallel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Generic deterministic async dispatch facade over arbitrary comparable keys.
 * This is the key-agnostic generalization of the p.2.6
 * {@code engine.worldgen.async.DeterministicDispatcher}: any {@code Comparable}
 * key plus a result supplier is routed to the owning {@link WorkerBank} via
 * {@link KeyPartition#bankIndex(Object, int)}, and once all banks are idle the
 * results are returned merged in {@link TaskResult} natural order (= key order),
 * independent of bank timing.
 *
 * <p><b>Contract upgrade over p.2.6 (duplicate keys).</b> p.2.6 merged a
 * duplicate key last-write-wins, which is <em>not</em> a deterministic
 * guarantee. Here a duplicate key is <em>explicitly rejected</em>: the second
 * submission of an already-accepted key throws {@link IllegalArgumentException},
 * and the rejected work supplier is never invoked. This is a deterministic hit —
 * a key is accepted at most once, and the rejected side always throws — so
 * callers no longer need to prove key disjointness themselves; the framework
 * enforces it.
 *
 * <p><b>Counters:</b> {@link #submittedCount()} counts only accepted submissions
 * (rejected duplicates never count); {@link #executedCount()} counts completed
 * result computations. After a successful {@link #awaitAll()},
 * {@code submitted == executed} (final-state assertion).
 *
 * <p>p.2.7.3 任意可比较键上的通用确定性异步分派门面。这是 p.2.6
 * {@code engine.worldgen.async.DeterministicDispatcher} 的键无关泛化：任意
 * {@code Comparable} 键 + 结果 supplier 经 {@link KeyPartition#bankIndex(Object, int)}
 * 路由到所属 {@link WorkerBank}；待所有 bank 空闲后按 {@link TaskResult} 自然序
 * （= key 序）归并返回结果，与 bank 时序无关。
 *
 * <p><b>相对 p.2.6 的契约升级（重复键）。</b>p.2.6 对重复键做 last-write-wins 归并
 * （并非确定性保证）。这里对重复键<em>显式拒绝</em>：再次提交已被接受的键抛
 * {@link IllegalArgumentException}，且被拒的 work supplier 绝不调用。这是确定性
 * 命中——同一键最多被接受一次，被拒方必抛——调用方无需再自证键互斥，框架强制。
 *
 * <p><b>计数器：</b> {@link #submittedCount()} 只统计已接受的提交（被拒的重复键
 * 不计）；{@link #executedCount()} 统计已完成计算的结果数。一次成功
 * {@link #awaitAll()} 后 {@code submitted == executed}（终态断言）。
 */
public final class DeterministicDispatcher<K extends Comparable<K>, R> {

    private final int bankCount;
    private final WorkerBank[] banks;
    private final Map<K, R> results = new ConcurrentHashMap<>();
    private final Set<K> accepted = ConcurrentHashMap.newKeySet();
    private final AtomicInteger submitted = new AtomicInteger();
    private final AtomicInteger executed = new AtomicInteger();

    private DeterministicDispatcher(int bankCount) {
        this.bankCount = bankCount;
        banks = new WorkerBank[bankCount];
        for (int i = 0; i < bankCount; i++) {
            banks[i] = new WorkerBank(i);
        }
    }

    /**
     * Creates a dispatcher with a normalized parallel bank count derived from
     * {@code requestedParallelism}.
     *
     * @param requestedParallelism the desired parallelism ({@code <= 0} yields 1 bank).
     * @return the new dispatcher.
     */
    public static <K extends Comparable<K>, R> DeterministicDispatcher<K, R> create(int requestedParallelism) {
        return new DeterministicDispatcher<>(KeyPartition.bankCount(requestedParallelism));
    }

    /**
     * Submits keyed work. The supplier is invoked exactly once, only if the key
     * is accepted. A duplicate key (already accepted) is explicitly rejected with
     * {@link IllegalArgumentException} and the work supplier is never invoked.
     * Blocks only on bounded backpressure when a bank queue is full.
     *
     * @param key  the task key (non-null, must be unique across accepted submissions).
     * @param work the result supplier.
     * @throws IllegalArgumentException if the key was already accepted.
     * @throws IllegalStateException    if the dispatcher has been shut down.
     */
    public void submit(K key, Supplier<R> work) {
        Objects.requireNonNull(key, "key");
        if (!accepted.add(key)) {
            throw new IllegalArgumentException("duplicate key: " + key);
        }
        submitted.incrementAndGet();
        int bankIndex = KeyPartition.bankIndex(key, bankCount);
        banks[bankIndex].submit(() -> {
            R r = work.get();
            results.put(key, r);
            executed.incrementAndGet();
        });
    }

    /**
     * Blocks until every accepted task has completed, then returns the results
     * sorted ascending by {@link TaskResult} natural order (key order) — the
     * deterministic merge contract regardless of bank timing.
     *
     * @return the merged, deterministically ordered result list.
     */
    public List<TaskResult<K, R>> awaitAll() {
        for (WorkerBank bank : banks) {
            bank.awaitIdle();
        }
        List<TaskResult<K, R>> out = new ArrayList<>(results.size());
        for (Map.Entry<K, R> e : results.entrySet()) {
            out.add(new TaskResult<>(e.getKey(), e.getValue()));
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    /**
     * Releases all bank workers. Work already accepted is drained and executed
     * before workers end (exactly-once preserved). Idempotent.
     */
    public void shutdown() {
        for (WorkerBank bank : banks) {
            bank.shutdown();
        }
    }

    /**
     * @return the number of accepted submissions (rejected duplicates not counted).
     */
    public int submittedCount() {
        return submitted.get();
    }

    /**
     * @return the number of completed result computations.
     */
    public int executedCount() {
        return executed.get();
    }

    /**
     * @return the normalized bank count in use.
     */
    public int bankCount() {
        return bankCount;
    }
}
