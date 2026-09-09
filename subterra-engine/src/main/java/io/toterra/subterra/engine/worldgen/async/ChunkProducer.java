// Async chunk pipeline design derives from C2ME (MIT, Copyright (c) ishland, https://github.com/ishland/C2ME);
// implementation is original Subterra code.
package io.toterra.subterra.engine.worldgen.async;

import io.toterra.subterra.engine.worldgen.async.ctx.ChunkTaskContext;

/**
 * Functional producer for one chunk (p.2.6.3). Given a chunk key and its isolated
 * {@link ChunkTaskContext} it returns the chunk's computed {@code byte[]} payload.
 *
 * <p><b>Purity contract:</b> {@link #produce} must be a pure function of its
 * inputs plus whatever <em>immutable</em> config the implementation closes over.
 * The result depends only on {@code (chunkX, chunkZ)} and the closed-over config —
 * never on shared mutable state. Instances must therefore be safe to execute
 * concurrently from many {@link AsyncWorkerBank} threads. Implementations that
 * need any per-chunk working objects should construct them fresh inside
 * {@code produce} (zero shared mutation) or share only effectively-read-only
 * objects established before dispatch.
 *
 * <p>p.2.6.3 单个区块的函数式生产者。给定区块键及其隔离的 {@link ChunkTaskContext}，
 * 返回该区块计算出的 {@code byte[]} 负载。
 *
 * <p><b>纯度契约：</b> {@link #produce} 必须是其输入加上实现所闭包绑定的<em>不可变</em>
 * 配置的纯函数。结果仅取决于 {@code (chunkX, chunkZ)} 与闭包配置，绝不依赖共享可变状态。
 * 因此实例必须可在多个 {@link AsyncWorkerBank} 线程中安全并发执行。需要临时工作对象的
 * 实现应在 {@code produce} 内部新建（零共享修改），或仅共享构造后有效只读的对象。
 */
@FunctionalInterface
public interface ChunkProducer {

    /**
     * Computes the deterministic payload for one chunk.
     *
     * @param chunkX the chunk X coordinate.
     * @param chunkZ the chunk Z coordinate.
     * @param ctx    the isolated per-chunk context (own {@link ChunkTaskContext#random()}).
     * @return the chunk's payload bytes; byte-for-byte a pure function of the inputs.
     */
    byte[] produce(long chunkX, long chunkZ, ChunkTaskContext ctx);
}