package io.toterra.subterra.engine.scale;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Per-entity, per-tag scale data: a map from an entity-type tag (e.g. the upstream
 * {@code #minecraft:zombie}-style tag id) to a base scale applied to that tag. The map
 * is kept in lexicographic ({@link String} natural) order via a {@link TreeMap}, so
 * iteration is deterministic.
 * <p>
 * Merging with a {@link ScaleData} composes the tag-wide base scale multiplicatively
 * with each stored dimension (consistent with the Pehkui model where {@code base}
 * derives the other dimensions): every dimension value of the base data is multiplied
 * by the tag scale, producing a full-dimension result.
 *
 * <p>每实体、按标签的缩放数据：实体类型标签（例如上游 {@code #minecraft:zombie} 风格标签 id）到
 * 应用于该标签的基础缩放值的映射。映射经 {@link TreeMap} 保持字典序（{@link String} 自然序），
 * 故迭代确定。
 * <p>与 {@link ScaleData} 合并时把标签级基础缩放与各已存维度做乘法复合（与 Pehkui 中
 * {@code base} 派生其它维度的模型一致）：基础数据的每个维度值乘以标签缩放，产出含全部维度的结果。
 */
public record ScaledEntityData(String entityId, Map<String, Double> tagScales) {

    /** @throws IllegalArgumentException if entityId or tagScales is null, or any value is not finite and > 0 */
    public ScaledEntityData {
        if (entityId == null) {
            throw new IllegalArgumentException("entityId must not be null");
        }
        Objects.requireNonNull(tagScales, "tagScales must not be null");
        TreeMap<String, Double> ordered = new TreeMap<>();
        for (Map.Entry<String, Double> e : tagScales.entrySet()) {
            Objects.requireNonNull(e.getKey(), "tag must not be null");
            if (e.getKey().isBlank()) {
                throw new IllegalArgumentException("tag must not be blank");
            }
            if (!Double.isFinite(e.getValue()) || e.getValue() <= 0.0D) {
                throw new IllegalArgumentException("tag scale must be finite and > 0: " + e.getValue());
            }
            ordered.put(e.getKey(), e.getValue());
        }
        tagScales = Collections.unmodifiableMap(ordered);
    }

    /** Tag base scale, or {@code 1.0} when the tag is absent. / 标签基础缩放；未设置时返回 {@code 1.0}。 */
    public double tag(String tag) {
        return tagScales.getOrDefault(tag, 1.0D);
    }

    /**
     * Merges this tag's base scale into {@code base}: every dimension of {@code base}
     * (in {@link ScaleType#values() values()} order) is multiplied by
     * {@link #tag(String)}, returning a full-dimension {@link ScaleData}. Deterministic.
     * / 将本标签的基础缩放并入 {@code base}：把 {@code base} 的每个维度（按
     * {@link ScaleType#values() values()} 序）乘以 {@link #tag(String)}，返回含全部维度的
     * {@link ScaleData}。确定性。
     */
    public ScaleData merge(String tag, ScaleData base) {
        Objects.requireNonNull(base, "base must not be null");
        double tagScale = tag(tag);
        ScaleData result = base;
        for (ScaleType t : ScaleType.values()) {
            result = result.with(t, base.get(t) * tagScale);
        }
        return result;
    }
}