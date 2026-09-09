// Async dispatch design derives from C2ME (MIT, Copyright (c) ishland, https://github.com/ishland/C2ME);
// implementation is original Subterra code.
package io.toterra.subterra.engine.worldgen.async;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Deterministic async dispatch facade for chunk work. Submits a chunk key plus a
 * byte payload supplier, routes it to the owning {@link AsyncWorkerBank} via
 * {@link ChunkPartition#bankIndex(long, long, int)}, and, once all banks are
 * idle, returns results merged in a deterministic order ({@link ChunkResult}
 * natural order = Z then X), independent of bank timing. This is the merge
 * contract that makes the parallel layer byte-deterministic vs. a serial run.
 *
 * <p><b>Duplicates:</b> keys are expected to be disjoint. If a duplicate key is
 * submitted, the map merge is last-write-wins, which is <em>not</em> a
 * deterministic guarantee; callers must ensure disjoint keys. The acceptance
 * probe only ever submits disjoint keys.
 *
 * <p><b>Counters:</b> {@link #submittedCount()} counts accepted submissions;
 * {@link #executedCount()} counts payload computations that have completed.
 * After a successful {@link #awaitAll()} both are equal (final-state assertion).
 *
 * <p>p.2.6.1 确定性异步分派门面：提交"区块键 + 字节负载 supplier"，经
 * {@link ChunkPartition#bankIndex(long, long, int)} 路由到所属
 * {@link AsyncWorkerBank}；待所有 bank 空闲后，按确定性顺序（{@link ChunkResult}
 * 自然序 = 先 Z 后 X）归并返回结果，与各 bank 实际时序无关。正是这一归并契约使
 * 并行层相对串行运行字节级确定。
 *
 * <p><b>重复键：</b> 期望键互斥。若提交重复键，map 归并为 last-write-wins（并非
 * 确定性保证）；调用方必须保证键互斥。验收探针只提交互斥键。
 *
 * <p><b>计数器：</b> {@link #submittedCount()} 统计已接受提交；
 * {@link #executedCount()} 统计已完成计算的负载数。一次成功 {@link #awaitAll()}
 * 后二者相等（终态断言）。
 */
public final class DeterministicDispatcher {

    private final int bankCount;
    private final AsyncWorkerBank[] banks;
    private final Map<ChunkKey, byte[]> results = new ConcurrentHashMap<>();
    private final AtomicInteger submitted = new AtomicInteger();
    private final AtomicInteger executed = new AtomicInteger();

    private DeterministicDispatcher(int bankCount) {
        this.bankCount = bankCount;
        banks = new AsyncWorkerBank[bankCount];
        for (int i = 0; i < bankCount; i++) {
            banks[i] = new AsyncWorkerBank(i);
        }
    }

    /**
     * Creates a dispatcher with a normalized parallel bank count derived from
     * {@code requestedParallelism}.
     *
     * @param requestedParallelism the desired parallelism ({@code <= 0} yields 1 bank).
     * @return the new dispatcher.
     */
    public static DeterministicDispatcher create(int requestedParallelism) {
        return new DeterministicDispatcher(ChunkPartition.bankCount(requestedParallelism));
    }

    /**
     * Submits chunk work. The supplier is invoked exactly once each result is
     * computed (whether or not duplicated); keys must be disjoint (see class doc).
     * Never blocks on a duplicate key; blocks only on bounded backpressure when
     * a bank queue is full.
     *
     * @param x    the chunk X coordinate.
     * @param z    the chunk Z coordinate.
     * @param work the payload supplier.
     */
    public void submit(long x, long z, Supplier<byte[]> work) {
        ChunkKey key = new ChunkKey(x, z);
        int bankIndex = ChunkPartition.bankIndex(key.x(), key.z(), bankCount);
        submitted.incrementAndGet();
        banks[bankIndex].submit(() -> {
            byte[] payload = work.get();
            results.put(key, payload);
            executed.incrementAndGet();
        });
    }

    /**
     * Blocks until every submitted task has completed, then returns the results
     * sorted ascending by {@link ChunkResult} natural order (Z then X) — the
     * deterministic merge contract regardless of bank timing.
     *
     * @return the merged, deterministically ordered result list.
     */
    public List<ChunkResult> awaitAll() {
        for (AsyncWorkerBank bank : banks) {
            bank.awaitIdle();
        }
        List<ChunkResult> out = new ArrayList<>(results.size());
        for (Map.Entry<ChunkKey, byte[]> e : results.entrySet()) {
            ChunkKey k = e.getKey();
            out.add(new ChunkResult(k.x(), k.z(), e.getValue()));
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    /**
     * Releases all bank workers. Work already accepted is drained and executed
     * before workers end (exactly-once preserved). Idempotent.
     */
    public void shutdown() {
        for (AsyncWorkerBank bank : banks) {
            bank.shutdown();
        }
    }

    /**
     * @return the number of accepted submissions.
     */
    public int submittedCount() {
        return submitted.get();
    }

    /**
     * @return the number of completed payload computations.
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

    /**
     * A deterministic chunk result. {@link Comparable} by {@link ChunkKey} order
     * (Z then X).
     *
     * @param x       the chunk X coordinate.
     * @param z       the chunk Z coordinate.
     * @param payload the computed payload bytes.
     */
    public record ChunkResult(long x, long z, byte[] payload) implements Comparable<ChunkResult> {

        @Override
        public int compareTo(ChunkResult other) {
            return new ChunkKey(x, z).compareTo(new ChunkKey(other.x, other.z));
        }
    }
}