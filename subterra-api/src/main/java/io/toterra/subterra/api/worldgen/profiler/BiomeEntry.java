package io.toterra.subterra.api.worldgen.profiler;

/**
 * One entry of a biome census (p.1.8.30): a namespaced biome id and its share of
 * the profiled window as a numerator/denominator ratio pair. Immutable. Pure
 * data.
 * <p>
 * 群系普查的一条记录（p.1.8.30）：命名空间群系 id 及其在被分析窗口中的占比，以分子/分母
 * 比例对表示。不可变。纯数据。
 */
public record BiomeEntry(String id, long ratioN, long ratioD) {
}