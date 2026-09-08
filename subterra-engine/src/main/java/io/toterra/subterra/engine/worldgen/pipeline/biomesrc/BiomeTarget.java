package io.toterra.subterra.engine.worldgen.pipeline.biomesrc;

import java.util.Locale;
import java.util.Objects;

/**
 * A weighted biome candidate for the multi-noise search (p.1.8.16): a biome string id,
 * a six-dimension {@link ClimateParam} <em>center</em>, and an optional (default 0)
 * long <em>weight</em>. Immutable; construction validates the id and the center.
 * <p>
 * The {@link #fitness(ClimateParam)} of a candidate against a sample reproduces the
 * MC 1.21.1 {@code Climate.ParameterPoint.fitness} reduction: the squared quantized
 * distance from the sample to the candidate's center band plus {@code weight^2}
 * (mirroring the vanilla {@code offset} term, the 7th entry of
 * {@code parameterSpace()}).
 * <p>
 * A weighted biome candidate for the multi-noise search（p.1.8.16）：一个生物群系字符串 id、
 * 一个六维 {@link ClimateParam} <em>中心</em>与一个可选（默认为 0）的 long <em>权重</em>。
 * 不可变；构造时校验 id 与中心。
 * {@link #fitness(ClimateParam)} 对样本复现 MC 1.21.1
 * {@code Climate.ParameterPoint.fitness} 归约：样本到候选中心 band 的量化平方距离加上
 * {@code weight^2}（镜像原生的 {@code offset} 项，即 {@code parameterSpace()} 第 7 项）。
 */
public final class BiomeTarget {

    private final String id;
    private final ClimateParam center;
    private final long weight;

    /**
     * @param id     biome id (non-empty, must contain a namespace colon).
     * @param center six-dimension climate center.
     * @param weight the vanilla offset / weight (quantized units); may be zero.
     * @throws IllegalArgumentException if {@code id} is blank or lacks a namespace, or
     *                                  {@code center} is null.
     */
    public BiomeTarget(String id, ClimateParam center, long weight) {
        Objects.requireNonNull(center, "center");
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("biome id must be non-empty");
        }
        if (id.indexOf(':') < 0) {
            throw new IllegalArgumentException("biome id must be namespaced: " + id);
        }
        this.id = id;
        this.center = center;
        this.weight = weight;
    }

    /** The biome id (e.g. {@code "minecraft:plains"}). */
    public String id() {
        return id;
    }

    /** The six-dimension climate center. */
    public ClimateParam center() {
        return center;
    }

    /** The optional weight (vanilla offset). */
    public long weight() {
        return weight;
    }

    /**
     * Selection score of this candidate against a sample; lower is closer.
     * Equals {@code squaredDistance} plus {@code weight^2 * ...} — specifically
     * {@code sum(square(delta_i)) + weight^2}.
     */
    public long fitness(ClimateParam sample) {
        Objects.requireNonNull(sample, "sample");
        return sample.squaredDistance(center) + weight * weight;
    }

    /** Self-description: {@code "[ id=<id>, weight=<w>, center=<td> ]"}. */
    public String td() {
        return String.format(Locale.ROOT, "[ id=%s, weight=%d, center=%s ]", id, weight, center.td());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BiomeTarget)) {
            return false;
        }
        BiomeTarget b = (BiomeTarget) o;
        return weight == b.weight && id.equals(b.id) && center.equals(b.center);
    }

    @Override
    public int hashCode() {
        int r = id.hashCode();
        r = 31 * r + center.hashCode();
        return 31 * r + (int) (weight ^ (weight >>> 32));
    }

    @Override
    public String toString() {
        return td();
    }
}