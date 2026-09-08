package io.toterra.subterra.api.worldgen.profiler;

/**
 * One entry of a block census (p.1.8.30): a namespaced block id and the number
 * of blocks counted. Immutable. Pure data.
 * <p>
 * 方块普查的一条记录（p.1.8.30）：命名空间方块 id 与统计到的方块数量。不可变。纯数据。
 */
public record BlockEntry(String id, long count) {
}