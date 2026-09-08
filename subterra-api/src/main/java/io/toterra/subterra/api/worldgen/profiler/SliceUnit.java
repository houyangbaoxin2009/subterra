package io.toterra.subterra.api.worldgen.profiler;

/**
 * The unit in which a {@link ProfileSlice} or profiling granularity is measured
 * (p.1.8.30): either single blocks or whole chunks. Pure data; no Minecraft
 * runtime.
 * <p>
 * {@link ProfileSlice} 或采集粒度所用的单位（p.1.8.30）：单个方块或整个区块。
 * 纯数据；不依赖 Minecraft 运行时。
 */
public enum SliceUnit {
    /** Unit is one block. */
    BLOCK,
    /** Unit is one whole chunk (16 blocks). */
    CHUNK,
}