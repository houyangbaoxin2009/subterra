// Async dispatch design derives from C2ME (MIT, Copyright (c) ishland, https://github.com/ishland/C2ME);
// implementation is original Subterra code.
package io.toterra.subterra.engine.worldgen.async;

import java.util.function.Supplier;

/**
 * The unit of asynchronous chunk work: computes a byte payload for one chunk
 * column. A task must depend only on its own coordinates / inputs and must not
 * touch shared mutable state, so results are deterministic. May be supplied
 * directly by later sub-items (e.g. p.2.6.3) or wrapped from a
 * {@link Supplier} by the {@link DeterministicDispatcher}.
 *
 * <p>异步区块工作单元：为一个区块列计算字节负载。任务只能依赖自身坐标/输入，
 * 不得接触共享可变状态，以保证结果确定。后续子项（如 p.2.6.3）可直接提供，
 * 或由 {@link DeterministicDispatcher} 从 {@link Supplier} 包装而来。
 */
@FunctionalInterface
public interface ChunkTask {

    /**
     * Computes and returns the chunk payload.
     *
     * @return the payload bytes for this chunk.
     */
    byte[] compute();
}