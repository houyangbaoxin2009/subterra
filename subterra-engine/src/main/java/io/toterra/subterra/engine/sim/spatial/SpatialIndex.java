// Original Subterra clean-room implementation; no external derivation.
package io.toterra.subterra.engine.sim.spatial;

import io.toterra.subterra.engine.parallel.KeyPartition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A partitioned spatial index (p.2.8.2) backed by a fixed-cell grid. Each world
 * position is collapsed onto a {@link CellCoord} cell via {@link CellCoord#of},
 * and every cell holds an ordered, de-duplicated set of keys. Neighborhood queries
 * ({@link #query}) enumerate <em>only</em> the {@code (2·radius+1)²} cells within the
 * window around the center cell — never a global scan — so a query's cost is
 * proportional to the window size, not the total index size.
 *
 * <p>Determinism: both the cell map and each cell's key set are {@link TreeMap} /
 * {@link TreeSet} backed, so iteration order is the natural (sorted) order at every
 * level. Query results are ordered by cell natural order and, within each cell, by
 * key natural order. Insert is idempotent: re-adding an already-present {@code (cell, key)}
 * is a no-op.
 *
 * <p>Partitioning: cells are assigned to parallel banks through the p.2.7
 * {@link KeyPartition#bankIndex(long, long, int)} two-{@code long} path, so the spatial
 * domain flows into {@code engine.parallel}'s deterministic bank partitioning.
 *
 * <p>p.2.8.2 分区化空间索引，以固定 cell 网格为底座。每个世界坐标经 {@link CellCoord#of}
 * 折叠到某个 {@link CellCoord} cell，每个 cell 保存一组有序、去重的键。邻域查询
 * （{@link #query}）<em>只</em> 枚举中心 cell 周围 {@code (2·radius+1)²} 窗口内的 cell，
 * 绝不做全局扫描——因此查询开销与窗口大小成正比，而不是与索引总规模成正比。
 *
 * <p>确定性：cell 映射与每个 cell 的键集合分别由 {@link TreeMap} / {@link TreeSet}
 * 承载，因而各级迭代顺序都是自然（有序）序。查询结果先按 cell 自然序、cell 内再按键自然序
 * 排列。插入幂等：对已存在的 {@code (cell, key)} 重复插入是 no-op。
 *
 * <p>分区：cell 通过 p.2.7 {@link KeyPartition#bankIndex(long, long, int)} 双 {@code long}
 * 路径划入并行 bank，从而把空间域接入 {@code engine.parallel} 的确定性键分区。
 *
 * @param <K> the key type; must be {@link Comparable} with a stable natural order.
 */
public final class SpatialIndex<K extends Comparable<K>> {

    private final TreeMap<CellCoord, TreeSet<K>> cells = new TreeMap<>();

    /** Creates an empty spatial index. */
    public SpatialIndex() {
    }

    /**
     * Inserts a key into the given cell, de-duplicating by natural key order. Inserting
     * a {@code (cell, key)} pair that is already present is an idempotent no-op.
     *
     * @param cell the target cell (non-null).
     * @param key  the key to store (non-null).
     */
    public void insert(CellCoord cell, K key) {
        Objects.requireNonNull(cell, "cell");
        Objects.requireNonNull(key, "key");
        cells.computeIfAbsent(cell, c -> new TreeSet<>()).add(key);
    }

    /**
     * Queries for every key whose cell lies within the square window of radius
     * {@code radius} around the cell holding {@code (worldX, worldZ)}. Only the
     * {@code (2·radius+1)²} window cells are touched; all others (including keys stored
     * far away) are never visited.
     *
     * @param worldX   the world X coordinate of the query center.
     * @param worldZ   the world Z coordinate of the query center.
     * @param radius   the Chebyshev window radius, {@code >= 0}.
     * @param cellBits the cell granularity bits (must match the granularity used at insert time).
     * @return the matching keys, ordered by cell natural order then key natural order.
     * @throws IllegalArgumentException if {@code radius} is negative or {@code cellBits} is out of range.
     */
    public List<K> query(long worldX, long worldZ, int radius, int cellBits) {
        if (radius < 0) {
            throw new IllegalArgumentException("radius must be >= 0, was " + radius);
        }
        CellCoord center = CellCoord.of(worldX, worldZ, cellBits);
        long cx = center.x();
        long cz = center.z();

        List<K> out = new ArrayList<>();
        for (long dx = -radius; dx <= radius; dx++) {
            for (long dz = -radius; dz <= radius; dz++) {
                CellCoord cell = new CellCoord(cx + dx, cz + dz);
                TreeSet<K> keys = cells.get(cell);
                if (keys != null) {
                    out.addAll(keys);
                }
            }
        }
        return out;
    }

    /**
     * @return the total number of distinct keys across all cells.
     */
    public int size() {
        int total = 0;
        for (TreeSet<K> keys : cells.values()) {
            total += keys.size();
        }
        return total;
    }

    /**
     * @return the number of non-empty cells held by the index.
     */
    public int cellCount() {
        return cells.size();
    }

    /**
     * Assigns a cell to one of {@code bankCount} parallel banks, reusing the p.2.7
     * two-{@code long} partition path so the spatial domain maps identically into
     * {@code engine.parallel}'s deterministic bank partitioning.
     *
     * @param cell      the cell to partition (non-null).
     * @param bankCount the number of banks ({@code >= 1}); normalized like {@link KeyPartition}.
     * @return a bank index in {@code [0, bankCount)}.
     */
    public static int bankIndex(CellCoord cell, int bankCount) {
        return KeyPartition.bankIndex(cell.x(), cell.z(), bankCount);
    }
}