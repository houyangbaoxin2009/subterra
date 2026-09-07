package io.toterra.subterra.optim.worldgen.pipeline.dimension;

import io.toterra.subterra.api.worldgen.EcoDim;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Field-resolution layer of the diffusion-model plan (design §5, Step 5): from
 * coarse macro fields to fine detail, mapping each layer onto the nine
 * dimensions via {@link #suggested(ModelLayer)}. Models are often cheaper or
 * more stable when a single model produces several related fields at a coarse
 * scale; this is the registry describing that suggested grouping.
 * <p>
 * The mapping is a <em>convention, not enforcement</em>: each dimension stays an
 * independent {@link DimensionSlot} and may be swapped per-dimension by
 * {@link DimensionTerrain#with}. The registry only tells a model builder which
 * dimensions <em>tend</em> to share a layer. Convention (per the design):
 * <ul>
 *   <li>{@link #COARSE} → climate &amp; terrain macro fields</li>
 *   <li>{@link #MID} → terrain (meso terrain, surfaces &amp; strata)</li>
 *   <li>{@link #FINE} → hydro / vegetation</li>
 * </ul>
 * Immutable, deterministic; iteration order follows {@link EcoDim#ALL}.
 * <p>
 * 扩散模型的场分辨率分层（设计 §5，第 5 步）：从粗粒度宏观场到细粒度细节，经
 * {@link #suggested(ModelLayer)} 把每层映射到九个维度。当单个模型以粗粒度一次产出多个
 * 相关场时往往更省或更稳；本注册表描述的就是这种建议分组。
 * <p>
 * 该映射是<em>约定，而非强制</em>：每个维度仍是独立的 {@link DimensionSlot}，可经
 * {@link DimensionTerrain#with} 逐维替换。注册表只是告诉模型构建方哪些维度<em>倾向于</em>
 * 共享一层。约定（依设计）：{@link #COARSE} → 气候与地形宏场；{@link #MID} → 地形
 * （中尺度地形、表面与地层）；{@link #FINE} → 水文 / 植被。
 */
public enum ModelLayer {

    /** Coarse macro scale: climate &amp; terrain macro fields. */
    COARSE,
    /** Mid scale: meso terrain (surface, litho, mineral strata). */
    MID,
    /** Fine scale: hydro / vegetation detail. */
    FINE;

    /**
     * Immutable layer → suggested dimensions registry. Each list is unmodifiable
     * and built once in {@link EcoDim#ALL} order; empty-listed layers mean no
     * dimensions currently suggested at that layer (none here).
     */
    private static final Map<ModelLayer, List<EcoDim>> SUGGESTED = buildRegistry();

    /** Builds the immutable suggested-dimension registry from the convention below. */
    private static Map<ModelLayer, List<EcoDim>> buildRegistry() {
        Map<ModelLayer, List<EcoDim>> map = new EnumMap<>(ModelLayer.class);
        // List.copyOf returns an unmodifiable list; the map is wrapped unmodifiable below.
        map.put(COARSE, List.of(EcoDim.of("climate"), EcoDim.of("terrain")));
        map.put(MID, List.of(EcoDim.of("terrain"), EcoDim.of("surface"),
                EcoDim.of("litho"), EcoDim.of("mineral")));
        map.put(FINE, List.of(EcoDim.of("hydro"), EcoDim.of("vegetation")));
        return Collections.unmodifiableMap(map);
    }

    /**
     * The dimensions conventionally suggested at the given layer. Convention
     * (not enforcement — each dimension stays an independent slot):
     * <ul>
     *   <li>{@link #COARSE} → climate &amp; terrain macro fields</li>
     *   <li>{@link #MID} → terrain (meso terrain, surfaces &amp; strata)</li>
     *   <li>{@link #FINE} → hydro / vegetation</li>
     * </ul>
     *
     * @param layer the resolution layer (never null)
     * @return an unmodifiable list of the suggested {@link EcoDim}s
     * @throws IllegalArgumentException if {@code layer} is null
     */
    public static List<EcoDim> suggested(ModelLayer layer) {
        if (layer == null) {
            throw new IllegalArgumentException("layer must not be null");
        }
        return SUGGESTED.get(layer);
    }
}