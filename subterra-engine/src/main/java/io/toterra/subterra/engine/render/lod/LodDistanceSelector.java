package io.toterra.subterra.engine.render.lod;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic distance-to-detail-level selector (p.2.28.3, clean-room self-developed):
 * given a player chunk coordinate and a {@link LodConfigDoc} distance ladder it maps every
 * chunk coordinate to an expected {@link LodLevel}. The ladder is taken from
 * {@link LodConfigDoc#distances()}: an ascending-by-level list of
 * {@code [level, distance]} cuts where {@code level} is a detail level ({@code 1..5}, mapped
 * to {@link LodLevel#values()[level]}) and {@code distance} is a block-space radius. The
 * projected level obeys the fixed-tiering rule — the closer the chunk, the finer the level;
 * the farthest tier (the last listed cut) absorbs everything beyond it; anything closer than
 * the first cut falls to {@link LodLevel#L0} (no LOD). Same distance always yields the same
 * level.
 *
 * <p><b>Determinism.</b> All arithmetic is pure {@code long} integer on squared block
 * distance: {@code distSq = (dx*16)² + (dz*16)²} in block units, compared against each cut's
 * {@code distance²}. There is no {@code java.lang.Math} sqrt, no float, no timestamp, no
 * iteration-order dependence — the level is a pure monotone function of {@code (dx,dz)}.
 *
 * <p>The fixed default threshold table is pinned as {@link #DEFAULT_TIERS} (from
 * {@link LodConfigDoc#DEFAULT_DISTANCES}): it is the golden target the p.2.28.6 probe
 * exercises, so it must never be re-guessed.
 *
 * <p>确定性「距离→细节层级」选择器（p.2.28.3，clean-room 自研）：给定玩家区块坐标与
 * {@link LodConfigDoc} 距离阶梯，把每个区块坐标映射为期望 {@link LodLevel}。阶梯取自
 * {@link LodConfigDoc#distances()}：按级升序的 {@code [level, distance]} 切分表，其中
 * {@code level} 为细节层级（{@code 1..5}，映射到 {@link LodLevel#values()[level]}），
 * {@code distance} 为方块空间半径。投影层级遵循固定分档规则——区块越近距离越细；最后一档（列表中最后的
 * 切分）吸收其外的一切；比首档更近者落到 {@link LodLevel#L0}（不启 LOD）。同距离恒得同层级。
 *
 * <p><b>确定性。</b>全部用纯 {@code long} 整数基于方块距离平方计算：
 * {@code distSq = (dx*16)² + (dz*16)²}，与每档的 {@code distance²} 比较。无
 * {@code java.lang.Math} 开方、无浮点、无时间戳、无迭代序依赖——层级为 {@code (dx,dz)} 的纯单调函数。
 *
 * <p>固定缺省阈值表被钉死为 {@link #DEFAULT_TIERS}（源自 {@link LodConfigDoc#DEFAULT_DISTANCES}）：
 * 它是 p.2.28.6 探针黄金化的对象，绝不可重猜。
 */
public final class LodDistanceSelector {

    /** Indicator: the nearest band maps to "no LOD". / 指示符：最近档映射到「不启 LOD」。 */
    private static final int NO_LOD = 0;

    /** How many blocks in one chunk. / 一个区块的方块数。 */
    private static final int BLOCKS_PER_CHUNK = 16;

    /**
     * A single fixed distance cut. {@code level} is the detail level ({@code 1..5}) matching
     * {@link LodLevel#values()}, {@code distance} its block radius. Ordered by {@code level}.
     * / 单条固定距离切分。{@code level} 为细节层级（{@code 1..5}，对 {@link LodLevel#values()}），
     * {@code distance} 为其方块半径。按 {@code level} 升序。
     */
    public record Tier(int level, int distance) {
    }

    /**
     * The pinned fixed default threshold table (from {@link LodConfigDoc#DEFAULT_DISTANCES}):
     * {@code L1@64, L2@128, L3@256, L4@512} blocks. Golden target for p.2.28.6.
     * / 钉死的固定缺省阈值表（源自 {@link LodConfigDoc#DEFAULT_DISTANCES}）：距离
     * {@code L1@64, L2@128, L3@256, L4@512} 方块。为 p.2.28.6 的黄金目标。
     */
    public static final List<Tier> DEFAULT_TIERS = tierListOf(LodConfigDoc.DEFAULT_DISTANCES);

    private final List<Tier> tiers;

    private LodDistanceSelector(List<Tier> tiers) {
        this.tiers = List.copyOf(tiers);
    }

    /**
     * Builds a selector from the config document's distance ladder. The document's distances
     * are copied verbatim in their ascending-level order; the list is treated as ordered.
     * / 由配置文档的距离阶梯构建选择器。文档距离按其升层级原样复制；列表视为有序。
     *
     * @param doc the {@link LodConfigDoc} whose {@link LodConfigDoc#distances()} set the cuts.
     * @throws NullPointerException     if {@code doc} is null.
     * @throws IllegalArgumentException if the ladder is empty or not strictly increasing in
     *     {@code distance}.
     */
    public static LodDistanceSelector from(LodConfigDoc doc) {
        Objects.requireNonNull(doc, "doc must not be null");
        return new LodDistanceSelector(tierListOf(doc.distances()));
    }

    /** A fully-default selector over {@link #DEFAULT_TIERS}. / 基于 {@link #DEFAULT_TIERS} 的全缺省选择器。 */
    public static LodDistanceSelector defaults() {
        return from(LodConfigDoc.defaults());
    }

    /**
     * Projects the expected level for a chunk-space squared distance in block units
     * ({@code distSqBlocks = (Δx*16)² + (Δz*16)²}). Monotone non-decreasing: farther → coarser.
     * A non-positive or sub-first-cut distance yields {@link LodLevel#L0}.
     * / 对以方块为单位的区块空间距离平方（{@code distSqBlocks}）投影期望层级。单调不减：越远越粗。
     * 非正或小于首档的距离得 {@link LodLevel#L0}。
     *
     * @param distSqBlocks the squared block-space distance from the player.
     * @return the expected {@link LodLevel}.
     */
    public LodLevel levelForDistanceSq(long distSqBlocks) {
        if (distSqBlocks <= 0) {
            return LodLevel.L0;
        }
        int chosenLevel = NO_LOD;
        for (Tier t : tiers) {
            long cutSq = (long) t.distance() * (long) t.distance();
            if (distSqBlocks < cutSq) {
                break; // farther cuts are even larger; stop at the first exceeded cut
            }
            chosenLevel = t.level();
        }
        return LodLevel.values()[chosenLevel];
    }

    /**
     * Projects the expected level for a chunk-index delta {@code (dx, dz)} (positive = east /
     * south). Coordinates given as blocks per {@code BLOCKS_PER_CHUNK}. Pure, deterministic.
     * / 对区块索引增量 {@code (dx, dz)}（正=东/南）投影期望层级。坐标按 {@code BLOCKS_PER_CHUNK}
     * 换算方块。纯函数、确定性。
     */
    public LodLevel levelForChunkDelta(long dxChunk, long dzChunk) {
        long dxb = dxChunk * BLOCKS_PER_CHUNK;
        long dzb = dzChunk * BLOCKS_PER_CHUNK;
        long distSq = dxb * dxb + dzb * dzb;
        return levelForDistanceSq(distSq);
    }

    /**
     * Projects the expected level for an absolute chunk coordinate against the player chunk
     * coordinate. / 对绝对区块坐标相对玩家区块坐标投影期望层级。
     */
    public LodLevel levelForChunk(long playerChunkX, long playerChunkZ, long chunkX, long chunkZ) {
        return levelForChunkDelta(chunkX - playerChunkX, chunkZ - playerChunkZ);
    }

    /**
     * @return the fixed cuts this selector uses, in ascending-level order (unmodifiable).
     * / 本选择器使用的固定切分，升层级序（不可变）。
     */
    public List<Tier> tiers() {
        return tiers;
    }

    private static List<Tier> tierListOf(List<LodConfigDoc.Distance> distances) {
        Objects.requireNonNull(distances, "distances must not be null");
        List<Tier> out = new ArrayList<>(distances.size());
        long prev = -1;
        for (LodConfigDoc.Distance d : distances) {
            if (d.level() < 1 || d.level() > 5) {
                throw new IllegalArgumentException(
                        "lod selector tier level must be in [1,5] but was " + d.level());
            }
            if (d.distance() <= 0) {
                throw new IllegalArgumentException(
                        "lod selector tier distance must be > 0 but was " + d.distance());
            }
            if ((long) d.distance() <= prev) {
                throw new IllegalArgumentException(
                        "lod selector tier distances must be strictly increasing; saw "
                                + prev + " then " + d.distance());
            }
            prev = d.distance();
            out.add(new Tier(d.level(), d.distance()));
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("lod selector tier list must not be empty");
        }
        return List.copyOf(out);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof LodDistanceSelector s && tiers.equals(s.tiers));
    }

    @Override
    public int hashCode() {
        return 31 + tiers.hashCode();
    }

    @Override
    public String toString() {
        return "LodDistanceSelector{tiers=" + tiers + '}';
    }
}