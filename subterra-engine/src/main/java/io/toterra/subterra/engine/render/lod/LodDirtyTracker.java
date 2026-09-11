package io.toterra.subterra.engine.render.lod;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Incremental dirty-cell tracker of a LOD pipeline (p.2.28.2, clean-room self-developed): a
 * deduplicated set of dirty block-column coordinates (relative to a section), exposed with a
 * deterministic fixed-order iteration by {@link Cell#compareTo} ({@code x} then {@code z}
 * ascending). Marking is idempotent and call-order-independent; the iteration order is a pure
 * function of the coordinate set, never of insertion order or timing. Deterministic.
 *
 * <p>p.2.28.2 LOD 管线的增量脏单元追踪器（clean-room 自研）：一个去重、相对区块的脏方块列坐标集合，以
 * {@link Cell#compareTo}（{@code x} 再 {@code z} 升序）的确定性固定序迭代暴露。标记幂等且与调用序无关；
 * 迭代序是坐标集的纯函数，绝不依赖插入序或时序。确定性。
 */
public final class LodDirtyTracker {

    private final SortedSet<Cell> cells = new TreeSet<>();

    /** Marks {@code (x, z)} dirty (idempotent). / 标记 {@code (x, z)} 为脏（幂等）。 */
    public void mark(int x, int z) {
        cells.add(new Cell(x, z));
    }

    /** Marks a known cell (idempotent). / 标记已知单元（幂等）。 */
    public void mark(Cell cell) {
        cells.add(Objects.requireNonNull(cell, "cell must not be null"));
    }

    /** Whether {@code (x, z)} is currently dirty. / 当前是否脏。 */
    public boolean isDirty(int x, int z) {
        return cells.contains(new Cell(x, z));
    }

    /** Number of distinct dirty cells. / 去重后脏单元数。 */
    public int size() {
        return cells.size();
    }

    /** Removes all cells; tracker becomes empty. / 清空全部单元；追踪器转为空。 */
    public void clear() {
        cells.clear();
    }

    /** Deterministic fixed-order snapshot (sorted by {@code (x,z)}, unmodifiable). /
     *  确定性固定序快照（按 {@code (x,z)} 排序，不可变）。 */
    public List<Cell> snapshot() {
        return List.copyOf(cells);
    }

    /** Iterates the dirty cells in deterministic fixed order. / 按确定性固定序迭代脏单元。 */
    public void forEach(java.util.function.Consumer<Cell> action) {
        cells.forEach(action);
    }

    /** Returns the fixed-order cell list for batch scheduling ({@code x} then {@code z}). /
     *  返回供批次调度的固定序单元列表（{@code x} 再 {@code z}）。 */
    public List<Cell> ordered() {
        return new ArrayList<>(cells);
    }

    @Override
    public String toString() {
        return "LodDirtyTracker{cells=" + cells.size() + "}";
    }

    /**
     * A dirty block-column coordinate. Natural order is {@code (x, z)} lexicographic — the fixed
     * deterministic iteration order used by the pipeline's budgeted batching.
     * / 脏方块列坐标。自然序为 {@code (x, z)} 字典序——即管线预算批次所用的固定确定性迭代序。
     */
    public record Cell(int x, int z) implements Comparable<Cell> {
        @Override
        public int compareTo(Cell other) {
            int c = Integer.compare(x, other.x);
            return c != 0 ? c : Integer.compare(z, other.z);
        }
    }
}