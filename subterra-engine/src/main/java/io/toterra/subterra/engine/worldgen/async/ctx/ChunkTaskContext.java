// Async per-chunk context isolation design derives from C2ME (MIT, Copyright (c) ishland, https://github.com/ishland/C2ME);
// implementation is original Subterra code.
package io.toterra.subterra.engine.worldgen.async.ctx;

/**
 * Per-chunk isolated context (p.2.6.2). Bundles a chunk coordinate plus its own
 * {@link PerChunkRandom} so each chunk task can generate its deterministic stream
 * with zero shared mutable state. {@link #of(long, long, long)} is pure and
 * self-contained: same {@code (worldSeed, chunkX, chunkZ)} always produce an
 * equivalent object behavior (identical random streams, identical accessors), and
 * instances never share any mutation — constructing one independently per task is
 * safe to run concurrently inside the p.2.6.1 dispatcher.
 *
 * <p>p.2.6.2 每区块隔离上下文。捆绑区块坐标与其专属 {@link PerChunkRandom}，使每个
 * 区块任务在零共享可变状态的情况下生成其确定性流。{@link #of(long, long, long)}
 * 为纯且自包含：相同的 {@code (worldSeed, chunkX, chunkZ)} 必然产生等价的实例行为
 * （随机流与访问器逐字节一致），且实例间不共享任何可变更内容——每个任务独立构造
 * 该上下文可在 p.2.6.1 分派器内安全并发运行。
 */
public final class ChunkTaskContext {

    private final long chunkX;
    private final long chunkZ;
    private final PerChunkRandom random;

    private ChunkTaskContext(long worldSeed, long chunkX, long chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.random = PerChunkRandom.of(worldSeed, chunkX, chunkZ);
    }

    /**
     * Builds a fresh, fully isolated per-chunk context for the given coordinates.
     *
     * @param worldSeed the master world seed.
     * @param chunkX    the chunk X coordinate.
     * @param chunkZ    the chunk Z coordinate.
     * @return a new context; repeated calls for identical inputs behave identically.
     */
    public static ChunkTaskContext of(long worldSeed, long chunkX, long chunkZ) {
        return new ChunkTaskContext(worldSeed, chunkX, chunkZ);
    }

    /** Chunk X coordinate of this context. */
    public long chunkX() {
        return chunkX;
    }

    /** Chunk Z coordinate of this context. */
    public long chunkZ() {
        return chunkZ;
    }

    /** The per-chunk deterministic random owned (exclusively) by this context. */
    public PerChunkRandom random() {
        return random;
    }
}