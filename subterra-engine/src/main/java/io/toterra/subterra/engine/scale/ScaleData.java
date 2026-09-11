package io.toterra.subterra.engine.scale;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Per-entity, per-dimension scale values. A scale of {@code 1.0} is the vanilla /
 * neutral value; absent dimensions report {@code 1.0} via {@link #get(ScaleType)}.
 * <p>
 * Deterministic and immutable: the scale map is normalized to the fixed {@link ScaleType}
 * {@link ScaleType#values() values()} order (regardless of build order), values must be
 * finite and {@code > 0}, and {@link #with(ScaleType, double)} derives a new instance.
 * {@code equals}/{@code hashCode} use {@link Map} semantics (order-insensitive) so
 * equality is independent of insertion order.
 *
 * <p>每实体、每维度的缩放值。{@code 1.0} 为原版 / 中性值；未设置的维度经 {@link #get(ScaleType)}
 * 返回 {@code 1.0}。
 * <p>确定性与不可变：缩放表统一归一化为固定 {@link ScaleType} {@link ScaleType#values() values()}
 * 序（与构建顺序无关）；取值必须有限且 {@code > 0}；{@link #with(ScaleType, double)} 派生出新实例。
 */
public record ScaleData(String entityId, Map<ScaleType, Double> scales) {

    /** @throws IllegalArgumentException if entityId or scales is null, or any scale value is not finite and > 0 */
    public ScaleData {
        if (entityId == null) {
            throw new IllegalArgumentException("entityId must not be null");
        }
        Objects.requireNonNull(scales, "scales must not be null");
        Map<ScaleType, Double> ordered = new TreeMap<>(Comparator.comparingInt(ScaleType::ordinal));
        for (Map.Entry<ScaleType, Double> e : scales.entrySet()) {
            Objects.requireNonNull(e.getKey(), "scale type must not be null");
            validateScale(e.getValue());
            ordered.put(e.getKey(), e.getValue());
        }
        scales = Collections.unmodifiableMap(ordered);
    }

    /** Builds a {@link ScaleData} with the given entity id and no dimensions set. /
     *  以给定实体 id 构建一个未设置任何维度的 {@link ScaleData}。 */
    public static ScaleData of(String entityId) {
        return new ScaleData(entityId, Map.of());
    }

    /** Dimension value, or {@code 1.0} when absent. / 维度值；未设置时返回 {@code 1.0}。 */
    public double get(ScaleType type) {
        return scales.getOrDefault(type, 1.0D);
    }

    /** Derives a new {@link ScaleData} with one dimension replaced. / 派生一个替换某维度后的新实例。 */
    public ScaleData with(ScaleType type, double value) {
        Objects.requireNonNull(type, "type must not be null");
        validateScale(value);
        Map<ScaleType, Double> copy = new LinkedHashMap<>(scales);
        copy.put(type, value);
        return new ScaleData(entityId, copy);
    }

    private static void validateScale(double value) {
        if (!Double.isFinite(value) || value <= 0.0D) {
            throw new IllegalArgumentException("scale value must be finite and > 0: " + value);
        }
    }
}