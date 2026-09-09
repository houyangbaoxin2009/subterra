// Async dispatch design derives from C2ME (MIT, Copyright (c) ishland, https://github.com/ishland/C2ME);
// implementation is original Subterra code.
package io.toterra.subterra.engine.worldgen.async;

/**
 * Immutable, comparable key for a single chunk column. Natural order compares
 * Z first, then X (both via {@link Long#compare}), giving a stable total order
 * that the determinism layer uses to merge results irrespective of bank timing.
 * Reusable as the coordination key for later async sub-items (e.g. p.2.6.3).
 *
 * <p>单个区块列的不可变可比键。自然序先比 Z 再比 X（均用 {@link Long#compare}），
 * 提供稳定全序；确定性层用它不受 bank 时序影响地归并结果。可复用于后续异步子项
 * （如 p.2.6.3）。
 */
public record ChunkKey(long x, long z) implements Comparable<ChunkKey> {

    @Override
    public int compareTo(ChunkKey other) {
        int c = Long.compare(this.z, other.z);
        return c != 0 ? c : Long.compare(this.x, other.x);
    }

    @Override
    public String toString() {
        return "(" + x + "," + z + ")";
    }
}