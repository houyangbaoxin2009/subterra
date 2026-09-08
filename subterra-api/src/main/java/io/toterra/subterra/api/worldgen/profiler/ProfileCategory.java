package io.toterra.subterra.api.worldgen.profiler;

/**
 * A data family that a world profile may collect (p.1.8.30 "World Profiler").
 * The profiler samples the pure-data reading of the world (see {@link
 * WorldSampler}) per category; a {@link ProfilePlan} lists which categories are
 * enabled for any given run. Pure and deterministic; no Minecraft runtime.
 * <p>
 * 世界档案可采集的数据类别（p.1.8.30 "World Profiler"）。分析器按类别对世界的纯数据
 * 读取（见 {@link WorldSampler}）进行采样；{@link ProfilePlan} 列出每个运行启用了哪些
 * 类别。纯且确定；不依赖 Minecraft 运行时。
 */
public enum ProfileCategory {
    /** Terrain surface-height statistics for the profiled column grid. */
    TERRAIN,
    /** Block census and per-band block tallies across the window. */
    BLOCKS,
    /** Cave/cavern air-fluid-solid classification and connectivity. */
    CAVES,
    /** Biome census and climate-field histograms for the window. */
    BIOME,
}