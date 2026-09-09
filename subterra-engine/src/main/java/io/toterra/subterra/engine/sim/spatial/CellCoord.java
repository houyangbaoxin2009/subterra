// Original Subterra clean-room implementation; no external derivation.
package io.toterra.subterra.engine.sim.spatial;

import java.util.Objects;

/**
 * An immutable cell coordinate in the partitioned spatial index's fixed-cell
 * grid. A cell is derived from a world position by a right shift of
 * {@code cellBits} bits, so every {@code (2^cellBits) x (2^cellBits)} block of
 * world space collapses onto a single cell. {@code CellCoord} is a fully
 * ordered value type (compare by x, then z) so that TreeMap/TreeSet-based
 * indexes iterate cells deterministically in natural order — the foundation of
 * reproducible neighborhood-query results.
 *
 * <p>p.2.8.2 分区化空间索引的不可变格点坐标。cell 由世界坐标按 {@code cellBits} 位右移得到，
 * 因此世界空间每 {@code (2^cellBits) x (2^cellBits)} 一块折叠为一个 cell。
 * {@code CellCoord} 是完整有序值类型（先按 x、再按 z 比较），使基于 TreeMap/TreeSet 的
 * 索引能按自然序确定性迭代 cell——这是邻域查询结果可复现的基础。
 */
public record CellCoord(long x, long z) implements Comparable<CellCoord> {

    /**
     * Derives the cell holding the given world position.
     *
     * @param worldX   the world X coordinate.
     * @param worldZ   the world Z coordinate.
     * @param cellBits the cell granularity in bits, {@code [0, 63]}.
     * @return the cell containing {@code (worldX, worldZ)}.
     * @throws IllegalArgumentException if {@code cellBits} is outside {@code [0, 63]}.
     */
    public static CellCoord of(long worldX, long worldZ, int cellBits) {
        if (cellBits < 0 || cellBits > 63) {
            throw new IllegalArgumentException("cellBits must be in [0,63], was " + cellBits);
        }
        return new CellCoord(worldX >> cellBits, worldZ >> cellBits);
    }

    /**
     * Natural order of cells: by x ascending, then by z ascending.
     */
    @Override
    public int compareTo(CellCoord other) {
        Objects.requireNonNull(other, "other");
        int c = Long.compare(x, other.x);
        return c != 0 ? c : Long.compare(z, other.z);
    }
}