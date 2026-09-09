// Async chunk pipeline design derives from C2ME (MIT, Copyright (c) ishland, https://github.com/ishland/C2ME);
// implementation is original Subterra code.
package io.toterra.subterra.engine.worldgen.async;

import io.toterra.subterra.engine.worldgen.async.DeterministicDispatcher.ChunkResult;
import io.toterra.subterra.engine.worldgen.async.ctx.ChunkTaskContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The facade of the async chunk-generation core (p.2.6.3): a parallel generator
 * that dispatches {@link ChunkProducer} work through the deterministic
 * {@link DeterministicDispatcher} (S-1) with per-chunk isolation via
 * {@link io.toterra.subterra.engine.worldgen.async.ctx.ChunkTaskContext} (S-2), and
 * merges results in the deterministic key order (Z then X) through a
 * {@link ChunkConsumer}. Serves both a parallel {@link #generate} path and a serial
 * golden {@link #generateSerial} path; byte equality between the two is this
 * milestone's determinism contract.
 *
 * <p><b>Generator lifecycle &amp; repeatability:</b> {@link DeterministicDispatcher}
 * accumulates its result map without clearing, so each {@link #generate} call uses a
 * fresh dispatcher (submit → await merge → mark results → consumer → shutdown) rather
 * than reusing one. This keeps repeated {@code generate} batches strictly disjoint and
 * therefore deterministically repeatable. After each call the per-call dispatcher is
 * shut down and its daemon virtual workers joined, so no non-daemon threads ever
 * escape the call. {@link #close} just latches the pipeline closed (idempotent); the
 * underlying per-call dispatchers are already fully released.
 *
 * <p><b>Scope decision (explicit):</b> dependency ordering between chunks — stage
 * graphs such as "neighbouring chunk must be produced before this one" — is
 * intentionally <em>not</em> part of the p.2.6 core; it is runtime-shell territory.
 * At the framework level, the delivery of "依赖排序" is the deterministic merge order:
 * results always surface in Z-then-X key order regardless of bank timing.
 *
 * <p>p.2.6.3 异步区块生成核心的门面：通过确定性 {@link DeterministicDispatcher}（S-1）
 * 分派 {@link ChunkProducer} 工作，并用 {@link io.toterra.subterra.engine.worldgen.async.ctx.ChunkTaskContext}
 *（S-2）做每区块隔离，再经 {@link ChunkConsumer} 按确定性键序（先 Z 后 X）归并结果。
 * 同时提供并行 {@link #generate} 路径与串行金样 {@link #generateSerial} 路径；两者字节
 * 等价即本里程碑的确定性契约。
 *
 * <p><b>生成器生命周期与可重复性：</b> {@link DeterministicDispatcher} 只增不清其结果 map，
 * 故每次 {@link #generate} 使用全新的分派器（提交 → 等待归并 → 记录结果 → 消费者 → 关闭）
 * 而非复用。这使多次 {@code generate} 批次严格互斥、确定可复现。每次调用后该次分派器即被
 * 关闭、其守护虚拟工作线程已 join，因此任何非守护线程都不会逸出调用。{@link #close} 只是
 * 将流水线锁死（幂等）；其下层每次调用的分派器已完全释放。
 *
 * <p><b>范围决策（明确）：</b> 区块间的依赖排序（如"先产出邻居区块再产出本区块"的阶段图）
 * 刻意<em>不</em>属 p.2.6 核心，而属运行时外壳领域。在框架层，"依赖排序"的交付即确定性归并
 * 顺序：无论 bank 时序如何，结果始终以 Z-then-X 键序呈现。
 */
public final class AsyncChunkPipeline {

    private final ChunkProducer producer;
    private final ChunkConsumer consumer;
    private final long worldSeed;
    private final int requestedParallelism;
    private final AtomicBoolean closed = new AtomicBoolean();

    private AsyncChunkPipeline(ChunkProducer producer, ChunkConsumer consumer,
                               long worldSeed, int requestedParallelism) {
        this.producer = Objects.requireNonNull(producer, "producer");
        this.consumer = Objects.requireNonNull(consumer, "consumer");
        this.worldSeed = worldSeed;
        this.requestedParallelism = requestedParallelism;
    }

    /**
     * Builds a pipeline over the given producer/consumer at the desired parallelism.
     *
     * @param producer             the per-chunk payload producer (must be pure &amp; concurrency-safe).
     * @param consumer             the merge-side callback invoked in deterministic key order.
     * @param worldSeed            the master world seed handed to each {@link ChunkTaskContext#of}.
     * @param requestedParallelism desired parallel bank count ({@code <= 0} ⇒ serial-equivalent 1 bank).
     * @return the new pipeline.
     */
    public static AsyncChunkPipeline of(ChunkProducer producer, ChunkConsumer consumer,
                                        long worldSeed, int requestedParallelism) {
        return new AsyncChunkPipeline(producer, consumer, worldSeed, requestedParallelism);
    }

    /**
     * Generates all requested keys in parallel through the deterministic dispatcher.
     * Each key is submitted as {@code producer.produce(x, z, ChunkTaskContext.of(worldSeed, x, z))};
     * after the merge the results are returned in Z-then-X order <em>and</em> the consumer is
     * invoked for every result in that same deterministic order. Disjoint keys only.
     *
     * @param keys the disjoint chunk keys to generate.
     * @return the merged results sorted Z-then-X.
     * @throws IllegalStateException if the pipeline is closed.
     */
    public List<ChunkResult> generate(List<ChunkKey> keys) {
        ensureOpen();
        DeterministicDispatcher dispatcher = DeterministicDispatcher.create(requestedParallelism);
        try {
            for (ChunkKey key : keys) {
                final long x = key.x();
                final long z = key.z();
                dispatcher.submit(x, z, () -> producer.produce(
                        x, z, ChunkTaskContext.of(worldSeed, x, z)));
            }
            List<ChunkResult> merged = dispatcher.awaitAll();
            for (ChunkResult r : merged) {
                consumer.onResult(r);
            }
            return merged;
        } finally {
            dispatcher.shutdown();
        }
    }

    /**
     * The golden synchronous path. Executes the same producer/consumer sequentially
     * over the keys in {@link ChunkKey} natural order (Z then X) — by construction
     * equivalent to a banks==1 run — regardless of {@code requestedParallelism}.
     * <p><b>Determinism contract:</b> {@code generateSerial(keys)} must produce, per
     * chunk and in list order, results byte-for-byte identical to {@code generate(keys)}.
     *
     * @param keys the disjoint chunk keys to generate.
     * @return the merged results sorted Z-then-X.
     * @throws IllegalStateException if the pipeline is closed.
     */
    public List<ChunkResult> generateSerial(List<ChunkKey> keys) {
        ensureOpen();
        List<ChunkKey> ordered = new ArrayList<>(keys);
        ordered.sort(ChunkKey::compareTo);
        List<ChunkResult> out = new ArrayList<>(ordered.size());
        for (ChunkKey key : ordered) {
            long x = key.x();
            long z = key.z();
            var ctx = ChunkTaskContext.of(worldSeed, x, z);
            ChunkResult r = new ChunkResult(x, z, producer.produce(x, z, ctx));
            consumer.onResult(r);
            out.add(r);
        }
        return out;
    }

    /**
     * Latches the pipeline closed, making further {@link #generate}/generates impossible.
     * Idempotent; the per-call dispatchers have already joined and released their daemon
     * workers by the time {@code generate} returns, so no non-daemon threads are leaked here.
     */
    public void close() {
        closed.set(true);
    }

    /** @return true after {@link #close()} has been called. */
    public boolean isClosed() {
        return closed.get();
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("AsyncChunkPipeline is closed");
        }
    }
}