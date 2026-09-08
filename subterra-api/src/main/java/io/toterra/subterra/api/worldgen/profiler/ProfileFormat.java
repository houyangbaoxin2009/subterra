package io.toterra.subterra.api.worldgen.profiler;

/**
 * The serialized output formats a world profile report may be written as
 * (p.1.8.30). The runner renders a completed {@link ProfileReport} to the chosen
 * format(s) when the {@link ProfileSink} requests output. Pure data; no
 * Minecraft runtime.
 * <p>
 * 世界档案报告可被写入的序列化输出格式（p.1.8.30）。当 {@link ProfileSink} 要求输出时，
 * 运行器把完成的 {@link ProfileReport} 渲染为所选格式。纯数据；不依赖 Minecraft 运行时。
 */
public enum ProfileFormat {
    /** Jump density table format (Subterra's dense array table-literal). */
    ZD,
    /** Tabular / delimited-text format. */
    TD,
}