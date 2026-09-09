// Async dispatch design derives from C2ME (MIT, Copyright (c) ishland, https://github.com/ishland/C2ME);
// implementation is original Subterra code.
package io.toterra.subterra.engine.parallel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The key-agnostic facade of the p.2.7 parallel core: a parallel {@link #run}
 * path that dispatches arbitrary comparable keys through the deterministic
 * {@link DeterministicDispatcher}, and a serial golden {@link #runSerial} path;
 * byte equality between the two is this sub-item's determinism contract.
 *
 * <p><b>Lifecycle &amp; repeatability:</b> {@link DeterministicDispatcher}
 * accumulates its result map without clearing, so each {@link #run} call uses a
 * fresh dispatcher (submit → awaitAll merge → shutdown join) rather than reusing
 * one. This keeps repeated {@code run} batches strictly disjoint and therefore
 * deterministically repeatable; after each call the per-call dispatcher is shut
 * down and its daemon virtual workers joined, so no non-daemon threads ever
 * escape the call.
 *
 * <p><b>Determinism contract:</b> for the same key set, {@code run} (parallel;
 * merge order = key natural order, independent of submission order or bank
 * timing) and {@code runSerial} (serial, key order) are element-for-element,
 * byte-for-byte identical. {@code run}'s submission order is exactly the
 * caller's key order, but the merged output is always in key order (input-order
 * independent). {@code fn} must be a pure function of the key (per-key
 * isolation; no shared mutable state). Duplicate keys are rejected on both
 * paths with {@link IllegalArgumentException} (the same determinism contract).
 *
 * <p>p.2.7 并行核心的键无关门面：并行 {@link #run} 路径经确定性
 * {@link DeterministicDispatcher} 分派任意可比较键，串行金样 {@link #runSerial}
 * 路径按 key 序逐个执行；两者字节等价即本子项的确定性契约。
 *
 * <p><b>生命周期与可重复性：</b> {@link DeterministicDispatcher} 只增不清其结果
 * map，故每次 {@link #run} 使用全新的分派器（提交 → awaitAll 归并 → shutdown join）
 * 而非复用。这使多次 {@code run} 批次严格互斥、确定可复现；每次调用后该次分派器即被
 * 关闭、其守护虚拟工作线程已 join，因此任何非守护线程都不会逸出调用。
 *
 * <p><b>确定性契约：</b> 对同一键集，{@code run}（并行；归并序 = key 自然序，与
 * 提交序/bank 时序无关）与 {@code runSerial}（串行按 key 序）逐元素、逐字节一致。
 * {@code run} 的提交序即调用方给出的键序，但归并输出恒为 key 序（输入序无关）。
 * {@code fn} 必须是对 key 的纯函数（per-key 隔离，禁共享易变状态）。两路径对重复键
 * 均以 {@link IllegalArgumentException} 拒绝（同一确定性契约）。
 */
public final class ParallelRunner {

    /**
     * A pure per-key computation: {@code apply(key)} returns the deterministic
     * result for exactly that key, with no shared mutable state.
     *
     * @param <K> the key type.
     * @param <R> the result type.
     */
    @FunctionalInterface
    public interface KeyFunction<K, R> {
        R apply(K key);
    }

    private final int bankCount;

    /**
     * Creates a runner with a normalized parallel bank count derived from
     * {@code requestedParallelism}.
     *
     * @param requestedParallelism the desired parallelism ({@code <= 0} yields 1 bank).
     */
    public ParallelRunner(int requestedParallelism) {
        this.bankCount = KeyPartition.bankCount(requestedParallelism);
    }

    /**
     * Runs the keys through a fresh deterministic dispatcher in parallel and
     * returns the results merged in key natural order. Duplicate keys are
     * rejected by the dispatcher with {@link IllegalArgumentException}
     * (contract passthrough); the rejected work supplier is never invoked.
     *
     * @param keys the keys to process (duplicates rejected).
     * @param fn   the per-key pure function.
     * @return the merged results in key natural order.
     */
    public <K extends Comparable<K>, R> List<TaskResult<K, R>> run(Collection<K> keys, KeyFunction<K, R> fn) {
        DeterministicDispatcher<K, R> dispatcher = DeterministicDispatcher.create(bankCount);
        try {
            for (K key : keys) {
                dispatcher.submit(key, () -> fn.apply(key));
            }
            return dispatcher.awaitAll();
        } finally {
            dispatcher.shutdown();
        }
    }

    /**
     * The golden synchronous path. Rejects duplicate keys upfront (same
     * determinism contract as {@link #run}: a duplicate key throws
     * {@link IllegalArgumentException} before any work runs), then executes
     * {@code fn} serially over the keys in natural order — by construction
     * equivalent to a banks==1 run regardless of {@code requestedParallelism}.
     * <p><b>Determinism contract:</b> {@code runSerial(keys, fn)} must produce,
     * per key and in list order, results byte-for-byte identical to
     * {@code run(keys, fn)}.
     *
     * @param keys the keys to process (duplicates rejected).
     * @param fn   the per-key pure function.
     * @return the results in key natural order.
     */
    public <K extends Comparable<K>, R> List<TaskResult<K, R>> runSerial(Collection<K> keys, KeyFunction<K, R> fn) {
        List<K> ordered = new ArrayList<>(keys);
        ordered.sort(null);
        Set<K> seen = new HashSet<>(ordered.size());
        for (K key : ordered) {
            if (!seen.add(key)) {
                throw new IllegalArgumentException("duplicate key: " + key);
            }
        }
        List<TaskResult<K, R>> out = new ArrayList<>(ordered.size());
        for (K key : ordered) {
            out.add(new TaskResult<>(key, fn.apply(key)));
        }
        return out;
    }

    /**
     * @return the normalized bank count in use.
     */
    public int bankCount() {
        return bankCount;
    }
}
