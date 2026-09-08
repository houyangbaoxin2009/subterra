package io.toterra.subterra.api.worldgen.profiler;

/**
 * One probed block position (p.1.8.30): its integer coordinates and the block id
 * sampled there. Immutable. Pure data.
 * <p>
 * 一个被采样的方块位置（p.1.8.30）：整型坐标与在那里采到的方块 id。不可变。纯数据。
 */
public record PositionRecord(int x, int y, int z, String block) {
}