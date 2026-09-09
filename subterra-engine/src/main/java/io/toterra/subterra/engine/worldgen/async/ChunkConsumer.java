// Async chunk pipeline design derives from C2ME (MIT, Copyright (c) ishland, https://github.com/ishland/C2ME);
// implementation is original Subterra code.
package io.toterra.subterra.engine.worldgen.async;

import io.toterra.subterra.engine.worldgen.async.DeterministicDispatcher.ChunkResult;

/**
 * Merge-side callback for produced chunks (p.2.6.3). {@link #onResult} is invoked by
 * the pipeline with each {@link ChunkResult} in the <em>deterministic key order</em>
 * (Z then X) — the dispatcher merge contract — so a consumer can treat the stream of
 * callbacks as already-ordered: this is the framework's deterministic "collect /
 * recombine" step. The runtime shell later hands merged chunks to the game here,
 * one call per chunk, strictly ordered.
 *
 * <p>The callback must be reentrant-safe to this thread: the pipeline invokes it
 * sequentially on the calling (merge) thread, never from multiple banks.
 *
 * <p>p.2.6.3 已生成区块的归并侧回调。流水线按<em>确定性键序</em>（先 Z 后 X）——即分派器
 * 归并契约——以每个 {@link ChunkResult} 调用一次 {@link #onResult}，故消费者可将回调流视为
 * 已有序：这正是框架的确定性"收集/重组"步骤。运行时外壳稍后在此处把已归并的区块交给
 * 游戏，按区块逐次、严格有序地调用。
 *
 * <p>回调只需对当前线程可重入：流水线在调用（归并）线程上串行调用它，绝不经多个 bank
 * 并发调用。
 */
@FunctionalInterface
public interface ChunkConsumer {

    /**
     * Receives one merged chunk result in deterministic key order (Z then X).
     *
     * @param result the produced chunk result; payload is {@code byte[]} and must
     *               not be mutated by the consumer if the caller may reuse it.
     */
    void onResult(ChunkResult result);
}