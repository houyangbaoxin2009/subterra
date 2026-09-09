package io.toterra.subterra.engine.sim.core;

/**
 * Deterministic simulant identity key (p.2.8.3) for the whole {@code engine.sim}
 * domain. A pair of {@code long} coordinates {@code (x, z)} that label a single
 * simulant across the world's simulated cells. Being a {@code record}, equality
 * is structural; {@link #compareTo} orders by {@code x} first, then {@code z}, so
 * a {@code List<SimKey>} sorted by natural order (as {@link SimWorld#keys()} is)
 * is canonical and order-independent for merging/diffing.
 *
 * <p>SimKey（p.2.8.3）是整片 {@code engine.sim} 域共享的确定性 simulant 身份键：
 * 用一对 long 坐标 {@code (x, z)} 唯一标识世界中被模拟单元格里的单个 simulant。
 * 作为 record，相等为结构相等；{@link #compareTo} 先按 x 再按 z 排序，因此按自然序
 * 排列的 {@code List<SimKey>}（如 {@link SimWorld#keys()} 所返回者）是规范且与
 * 集合迭代序无关的，便于归并与 diff。
 *
 * @param x the simulant X coordinate.
 * @param z the simulant Z coordinate.
 */
public record SimKey(long x, long z) implements Comparable<SimKey> {

    /**
     * Orders by X first, then Z — the canonical natural order over the {@code (x,z)}
     * grid. Deterministic, no shared state.
     *
     * 先按 X 再按 Z 排序——{@code (x,z)} 网格上的规范自然序。确定、无共享状态。
     */
    @Override
    public int compareTo(SimKey other) {
        int c = Long.compare(x, other.x);
        return c != 0 ? c : Long.compare(z, other.z);
    }
}