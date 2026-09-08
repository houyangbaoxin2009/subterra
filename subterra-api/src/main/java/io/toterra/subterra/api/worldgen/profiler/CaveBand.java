package io.toterra.subterra.api.worldgen.profiler;

/**
 * Cave classification for one vertical band {@code [yFrom, yTo) } (p.1.8.30):
 * the number of air, fluid and solid blocks in that band. Immutable. Pure data.
 * <p>
 * 某个竖直带 {@code [yFrom, yTo) } 的洞穴分类（p.1.8.30）：该带内空气、流体与固体方块
 * 的数量。不可变。纯数据。
 */
public record CaveBand(int yFrom, int yTo, long air, long fluid, long solid) {
}