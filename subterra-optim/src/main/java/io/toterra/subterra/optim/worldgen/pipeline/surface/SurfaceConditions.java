package io.toterra.subterra.optim.worldgen.pipeline.surface;

import java.util.Objects;

/**
 * Surface-condition factory and combinators (p.1.8.5, self-developed): build and
 * compose {@link SurfaceCondition} predicates — height range, below-Y, biome tag,
 * density-above and the boolean combinators. All are pure and O(1).
 * <p>
 * 表面条件工厂与组合子（p.1.8.5，自研）：构建并组合{@code boolean}谓词，含高度区间、Y 之下、群系标签、
 * 密度之上与布尔组合。全部为纯函数且 O(1)。
 */
public final class SurfaceConditions {

    private SurfaceConditions() {
    }

    /** True when {@code minInclusive <= y <= maxInclusive} (inclusive bounds). */
    public static SurfaceCondition heightRange(int minInclusive, int maxInclusive) {
        if (minInclusive > maxInclusive) {
            throw new IllegalArgumentException("heightRange bounds inverted: min=" + minInclusive + " max=" + maxInclusive);
        }
        return c -> c.y() >= minInclusive && c.y() <= maxInclusive;
    }

    /** True when {@code y < below}. */
    public static SurfaceCondition belowY(int y) {
        return c -> c.y() < y;
    }

    /** True when the context biome tag equals {@code tag} (null-safe). */
    public static SurfaceCondition biome(String tag) {
        return c -> Objects.equals(c.biomeTag(), tag);
    }

    /** True when {@code density > threshold} (strictly greater). */
    public static SurfaceCondition densityAbove(double threshold) {
        return c -> c.density() > threshold;
    }

    /** True when every condition is true (empty list is true). */
    public static SurfaceCondition and(SurfaceCondition... all) {
        return c -> {
            for (SurfaceCondition cond : all) {
                if (!cond.test(c)) {
                    return false;
                }
            }
            return true;
        };
    }

    /** True when at least one condition is true (empty list is false). */
    public static SurfaceCondition or(SurfaceCondition... any) {
        return c -> {
            for (SurfaceCondition cond : any) {
                if (cond.test(c)) {
                    return true;
                }
            }
            return false;
        };
    }

    /** Inverted condition. */
    public static SurfaceCondition not(SurfaceCondition cond) {
        Objects.requireNonNull(cond, "cond");
        return c -> !cond.test(c);
    }

    /** Always-true condition. */
    public static SurfaceCondition alwaysTrue() {
        return c -> true;
    }

    /** Never-true condition. */
    public static SurfaceCondition never() {
        return c -> false;
    }
}