package io.toterra.subterra.api.lod;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * p.2.28.6 距离档位契约（对外契约面，纯 JDK）：不可变的按级距离阈值聚合，语义与
 * {@code engine.render.lod.LodConfigDoc.DEFAULT_DISTANCES} 及
 * {@code engine.render.lod.LodDistanceSelector}（p.2.28.3）完全对齐——本处为契约形状、engine 为实现侧
 * 镜像，api 不依赖 engine。每档为一条 {@code [level, distance]} 切分（{@code level} 为细节层级
 * {@code 1..5}、{@code distance} 为其方块空间半径）；切分按 {@code level} 升序且距离严格递增，与
 * engine 的校验（{@code LodConfigDoc.validate}/{@code LodDistanceSelector.tierListOf}）一致。空表被
 * 拒绝。
 * <p>确定性：{@link #defaults()} 返回 engine 钉死缺省表（{@code L1@64, L2@128, L3@256, L4@512}）；
 * {@link #distanceForLevel(int)} 确定性读取某级阈值；{@link #maxLevel()} 返回最高启用级；
 * {@link #tiers()} 以升层级固定序返回不可变切分。无随机、无时序。
 * <p>
 * p.2.28.6 the distance-tier contract (the external contract surface, pure JDK): an immutable aggregate of
 * per-level distance thresholds, semantically aligned with {@code engine.render.lod.LodConfigDoc.DEFAULT_DISTANCES}
 * and {@code engine.render.lod.LodDistanceSelector} (p.2.28.3) — this is the contract shape, the engine mirrors
 * as its implementation, and the api does not depend on the engine. Each tier is one {@code [level, distance]}
 * cut ({@code level} is a detail level {@code 1..5}, {@code distance} its block-space radius); cuts are ordered
 * ascending by {@code level} with strictly increasing distances, matching the engine's validation
 * ({@code LodConfigDoc.validate}/{@code LodDistanceSelector.tierListOf}). An empty table is rejected.
 * <p>Deterministic: {@link #defaults()} returns the engine's pinned default table
 * ({@code L1@64, L2@128, L3@256, L4@512}); {@link #distanceForLevel(int)} reads one level's threshold
 * deterministically; {@link #maxLevel()} returns the highest enabled level; {@link #tiers()} returns the
 * immutable cuts in fixed ascending-level order. No randomness, no timing.
 */
public final class LodDistanceSpec {

    /**
     * A single fixed distance cut. {@code level} is the detail level ({@code 1..5}), {@code distance} its
     * block-space radius. Ordered ascending by {@code level}. / 单条固定距离切分。{@code level} 为细节层级
     * （{@code 1..5}），{@code distance} 为其方块空间半径。按 {@code level} 升序。
     *
     * @param level    the detail level ({@code 1..5}).
     * @param distance the block-space radius ({@code > 0}).
     */
    public record Cut(int level, int distance) {
    }

    private final List<Cut> cuts;

    private LodDistanceSpec(List<Cut> cuts) {
        this.cuts = List.copyOf(cuts);
    }

    /**
     * Builds a {@link LodDistanceSpec} from the given cuts. Cuts must be non-empty and strictly increasing in
     * both {@code level} ({@code 1..5}) and {@code distance} ({@code > 0}), mirroring the engine's validation.
     * / 由给定切分构建 {@link LodDistanceSpec}。切分须非空，且 {@code level}（{@code 1..5}）与
     * {@code distance}（{@code > 0}）均严格递增，镜像 engine 的校验。
     *
     * @param cuts the distance cuts in ascending-level order (non-null, non-empty).
     * @return a new immutable {@link LodDistanceSpec}.
     * @throws NullPointerException     if {@code cuts} is null.
     * @throws IllegalArgumentException if the list is empty or not strictly increasing in level/distance.
     */
    public static LodDistanceSpec of(List<Cut> cuts) {
        Objects.requireNonNull(cuts, "cuts must not be null");
        if (cuts.isEmpty()) {
            throw new IllegalArgumentException("lod distance tiers must not be empty");
        }
        int prevLevel = 0;
        long prevDist = -1;
        for (Cut c : cuts) {
            if (c.level() < 1 || c.level() > 5) {
                throw new IllegalArgumentException("lod distance level must be in [1,5] but was " + c.level());
            }
            if (c.distance() <= 0) {
                throw new IllegalArgumentException("lod distance must be > 0 but was " + c.distance());
            }
            if (c.level() <= prevLevel || (long) c.distance() <= prevDist) {
                throw new IllegalArgumentException("lod distance tiers must be strictly increasing in level/distance");
            }
            prevLevel = c.level();
            prevDist = c.distance();
        }
        return new LodDistanceSpec(cuts);
    }

    /** The engine's pinned default distance spec {@code L1@64, L2@128, L3@256, L4@512} (mirrors
     *  {@code LodConfigDoc.DEFAULT_DISTANCES}). / engine 钉死缺省距离 spec {@code L1@64, L2@128, L3@256,
     *  L4@512}（镜像 {@code LodConfigDoc.DEFAULT_DISTANCES}）。 */
    public static LodDistanceSpec defaults() {
        return of(List.of(new Cut(1, 64), new Cut(2, 128), new Cut(3, 256), new Cut(4, 512)));
    }

    /** The block-space threshold for the given detail level, or {@code -1} if the level has no cut. /
     *  给定细节层级的方块空间阈值；该层级无切分时返回 {@code -1}。 */
    public int distanceForLevel(int level) {
        for (Cut c : cuts) {
            if (c.level() == level) {
                return c.distance();
            }
        }
        return -1;
    }

    /** The highest enabled detail level (the last cut's level). / 最高启用细节层级（末条切分的 level）。 */
    public int maxLevel() {
        return cuts.get(cuts.size() - 1).level();
    }

    /** The fixed cuts, ascending by level (unmodifiable). / 固定切分，升层级序（不可变）。 */
    public List<Cut> tiers() {
        return cuts;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof LodDistanceSpec s && cuts.equals(s.cuts));
    }

    @Override
    public int hashCode() {
        return 31 + cuts.hashCode();
    }

    @Override
    public String toString() {
        return "LodDistanceSpec{cuts=" + cuts + '}';
    }
}