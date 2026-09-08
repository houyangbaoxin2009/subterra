package io.toterra.subterra.api.worldgen.profiler;

/**
 * The destination for a completed world profile (p.1.8.30). The sink decides
 * whether a report is only cached in memory, persisted to the world or filesystem,
 * or discarded entirely. Pure data; no Minecraft runtime.
 * <p>
 * 完成的档案的落点（p.1.8.30）。落点决定报告是仅在内存中缓存、持久化到世界或文件系统，
 * 还是完全丢弃。纯数据；不依赖 Minecraft 运行时。
 */
public enum ProfileSink {
    /** Run the collection and drop the resulting report (no cache/persist). */
    RUN,
    /** Run, cache the report in memory and persist it to the world store. */
    WORLD,
    /** Run and cache the report in memory only (nothing written out). */
    NONE,
}