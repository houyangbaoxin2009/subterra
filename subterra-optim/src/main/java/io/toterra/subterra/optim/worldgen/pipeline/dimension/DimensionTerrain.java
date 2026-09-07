package io.toterra.subterra.optim.worldgen.pipeline.dimension;

import io.toterra.subterra.api.worldgen.EcoDim;
import io.toterra.subterra.optim.worldgen.pipeline.density.Densities;
import io.toterra.subterra.optim.worldgen.pipeline.density.Density;
import io.toterra.subterra.optim.worldgen.pipeline.density.ValueNoise;
import io.toterra.subterra.optim.worldgen.pipeline.noise.NoiseSalt;
import io.toterra.subterra.optim.worldgen.pipeline.noise.perlin.PerlinNoise;
import io.toterra.subterra.optim.worldgen.pipeline.noise.simplex.NormalNoise;
import io.toterra.subterra.optim.worldgen.pipeline.surface.SurfaceRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The nine-dimension terrain set (Step 2), keyed by
 * {@link io.toterra.subterra.api.worldgen.EcoDim}.ALL, composed of immutable
 * {@link DimensionSlot}s. Default construction produces five scalar-dimension
 * vanilla-compatible mirror fields plus reserved slots:
 * <ul>
 *   <li>{@code terrain} — an octave {@link PerlinNoise}-backed
 *       {@link Density} (mirror)</li>
 *   <li>{@code climate} — a {@link NormalNoise}-backed {@link Density}
 *       (mirror)</li>
 *   <li>{@code hydro} — the dual-slot (Step 3): the {@link Density} field still
 *       samples the water-surface height (constant sea level 63 by default, so
 *       the uniform slot contract holds), while {@link #waterLevel()} and
 *       {@link #waterClass()} expose the semantic seams — a {@link WaterLevel}
 *       ({@link ConstantWaterLevel} 63) and a {@link WaterClassSampler}
 *       ({@link VanillaWaterClass} over the terrain field vs the sea level).</li>
 *   <li>{@code litho} / {@code mineral} — mirror-primitive placeholders
 *       (deterministic noise)</li>
 *   <li>{@code surface} — a placeholder {@link Density}; the real seam is the
 *       p.1.8.5 SurfaceEvaluator assembly point exposed via
 *       {@link #surfaceRules()} (unused-for-now, wired in a later batch)</li>
 *   <li>{@code vegetation} / {@code fauna} / {@code relic} — reserved slots,
 *       default {@link Density} = constant 0; semantic seams deferred</li>
 * </ul>
 * The formula path (p.1.8.10) is <em>not</em> used by any default — the
 * default mirrors do not depend on the {@code terrain} (formula) packages
 * (the {@code dimension} package as a whole imports the p.1.8.10
 * {@code FormulaTerrain} only for {@link DimensionPlan} FORMULA entries); the
 * formula algorithm enters per-dimension via
 * {@link #with(EcoDim, Density, DimAlgo)} with {@link DimAlgo#FORMULA} (or,
 * wholesale, via {@link #fromPlan(DimensionPlan, long)}). Everything is
 * immutable, deterministic
 * (fixed seed → identical fields), and free of O(n²) construction; hot-path
 * {@code Density.eval} is allocation-free.
 * <p>
 * 九维地形集合（第 2 步），以 {@link io.toterra.subterra.api.worldgen.EcoDim}
 * 的 ALL 为键，由不可变的 {@link DimensionSlot} 组成。默认构造产出五维标量原版
 * 兼容镜像场与预留槽。公式路径（p.1.8.10）不参与任何默认——默认镜像不依赖 terrain
 * （公式）包（整个 dimension 包仅 {@link DimensionPlan} 的 FORMULA 条目会引用
 * p.1.8.10 {@code FormulaTerrain}）；公式算法经 {@link #with} + {@link DimAlgo#FORMULA}
 * （后续亦有 {@code DimensionPlan}）逐维进入。一切不可变、确定（固定种子→相同场），
 * 无 O(n²) 构造；热路径 {@code Density.eval} 无分配。
 */
public final class DimensionTerrain {

    /** Default seed used by the no-arg default construction. */
    public static final long DEFAULT_SEED = 0L;

    /** Sea level for the default {@code hydro} field (vanilla 63). */
    public static final double HYDRO_SEA_LEVEL = 63.0;

    /** The {@code terrain} dimension, used by the default water-class sampler. */
    private static final EcoDim TERRAIN = EcoDim.of("terrain");
    /** The {@code hydro} dimension, backing the default water-level sampler. */
    private static final EcoDim HYDRO = EcoDim.of("hydro");

    /** Salt separating the {@code terrain} octave-Perlin lattice. */
    private static final long TERRAIN_SALT = 0x1010_1010_1010_1010L;
    /** Salt separating the {@code climate} normal-noise lattice. */
    private static final long CLIMATE_SALT = 0x2020_2020_2020_2020L;
    /** Salt separating the {@code litho} value-noise lattice. */
    private static final long LITHO_SALT = 0x3030_3030_3030_3030L;
    /** Salt separating the {@code mineral} octave-Perlin lattice. */
    private static final long MINERAL_SALT = 0x4040_4040_4040_4040L;

    /** Lattice cell size of the {@code litho} default value-noise field. */
    private static final double LITHO_SCALE = 32.0;

    /** The terrain default octave amplitudes. */
    private static final double[] TERRAIN_AMPLITUDES = {1.0, 0.5, 0.25};
    /** The mineral default octave amplitudes. */
    private static final double[] MINERAL_AMPLITUDES = {0.6, 0.3};

    private final Map<EcoDim, DimensionSlot> slots;
    private final OverworldBounds bounds;
    private final List<SurfaceRule> surfaceRules;
    private final WaterLevel waterLevel;
    private final WaterClassSampler waterClass;

    /**
     * Full-arg constructor storing the hydro dual-slot accessors; rejects null
     * accessors via IllegalArgumentException.
     */
    private DimensionTerrain(Map<EcoDim, DimensionSlot> slots, OverworldBounds bounds, List<SurfaceRule> surfaceRules,
                             WaterLevel waterLevel, WaterClassSampler waterClass) {
        if (waterLevel == null) {
            throw new IllegalArgumentException("waterLevel must not be null");
        }
        if (waterClass == null) {
            throw new IllegalArgumentException("waterClass must not be null");
        }
        this.slots = Collections.unmodifiableMap(new LinkedHashMap<>(slots));
        this.bounds = bounds;
        this.surfaceRules = surfaceRules;
        this.waterLevel = waterLevel;
        this.waterClass = waterClass;
    }

    /** Default dual-slot construction: constant water level 63 + vanilla water class. */
    private DimensionTerrain(Map<EcoDim, DimensionSlot> slots, OverworldBounds bounds, List<SurfaceRule> surfaceRules) {
        this(slots, bounds, surfaceRules, waterLevelDefault(), waterClassDefault(slots, bounds));
    }

    /** Default construction with {@link #DEFAULT_SEED} (vanilla mirrors, vanilla bounds). */
    public DimensionTerrain() {
        this(DEFAULT_SEED);
    }

    /** Default construction at the given seed (vanilla mirrors, vanilla bounds). */
    public DimensionTerrain(long seed) {
        this(buildDefaults(seed), OverworldBounds.vanilla(), null);
    }

    /**
     * Materialises the given {@link DimensionPlan} at the given seed into this
     * set (returns a new instance; the default constructor remains available).
     * Dimensions without a plan override keep their vanilla mirror slot;
     * {@link DimAlgo#FORMULA} entries build a formula {@link Density},
     * {@link DimAlgo#CONSTANT} a constant {@link Density}, and
     * {@link DimAlgo#MODEL} (backend not implemented) defers to the default
     * slot. See {@link DimensionPlan#materialize(long)}.
     *
     * @param plan the algorithm plan to apply (never null)
     * @param seed the deterministic seed for noise / formula fields
     * @throws IllegalArgumentException if {@code plan} is null
     */
    public static DimensionTerrain fromPlan(DimensionPlan plan, long seed) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }
        return plan.materialize(seed);
    }

    /**
     * Returns the slot for the given dimension, or {@code null} if it is not
     * present. All nine {@link EcoDim#ALL} dimensions are always present on the
     * default set.
     *
     * @param dim the ecosystem dimension (must not be null)
     * @return the matching {@link DimensionSlot}, or {@code null}
     * @throws IllegalArgumentException if {@code dim} is null
     */
    public DimensionSlot slot(EcoDim dim) {
        if (dim == null) {
            throw new IllegalArgumentException("dim must not be null");
        }
        return slots.get(dim);
    }

    /**
     * Returns a new {@link DimensionTerrain} with the given dimension's
     * {@link DimensionSlot} replaced by a slot carrying {@code density} and
     * {@code algo}. All other slots, the {@link #bounds()} and the
     * {@link #surfaceRules()} are preserved (immutability — the receiver is not
     * modified). This is the entry point for the formula path
     * ({@link DimAlgo#FORMULA}) and any per-dimension algorithm swap.
     *
     * @throws IllegalArgumentException if any argument is null, or {@code dim}
     *         is not a dimension of this set
     */
    public DimensionTerrain with(EcoDim dim, Density density, DimAlgo algo) {
        if (dim == null) {
            throw new IllegalArgumentException("dim must not be null");
        }
        if (density == null) {
            throw new IllegalArgumentException("density must not be null");
        }
        if (algo == null) {
            throw new IllegalArgumentException("algo must not be null");
        }
        if (!slots.containsKey(dim)) {
            throw new IllegalArgumentException("unknown dimension: " + dim);
        }
        Map<EcoDim, DimensionSlot> next = new LinkedHashMap<>(slots);
        next.put(dim, new DimensionSlot(dim, density, algo));
        // The dual-slot accessors read the current (replacement) slot: swapping
        // hydro updates the water-level sampler to the new water-surface height,
        // swapping terrain updates the water-class sampler to the new terrain field.
        WaterLevel wl = waterLevel;
        WaterClassSampler wc = waterClass;
        if (dim.equals(HYDRO)) {
            wl = (x, z) -> density.eval(x, 0.0, z);
        } else if (dim.equals(TERRAIN)) {
            wc = new VanillaWaterClass(density, bounds.seaLevel());
        }
        return new DimensionTerrain(next, bounds, surfaceRules, wl, wc);
    }

    /**
     * The vertical extent of this set, defaulting to vanilla
     * {@link OverworldBounds#vanilla()} ({@code [-64, 320]}, sea level 63).
     */
    public OverworldBounds bounds() {
        return bounds;
    }

    /**
     * The p.1.8.5 SurfaceEvaluator assembly point: the ordered surface rule
     * list, currently unused-for-now ({@code null} by default — no surface
     * behavior is invented here). A later batch wires the surface dimension
     * through {@link #withSurfaceRules}. May return {@code null}.
     */
    public List<SurfaceRule> surfaceRules() {
        return surfaceRules;
    }

    /**
     * The hydro dual-slot's water-surface seam: the default {@link WaterLevel},
     * equal to {@link ConstantWaterLevel}({@link #HYDRO_SEA_LEVEL}) (63) for the
     * seed-independent default. Consumers are the terrain height clamp, surface
     * underwater variants and biome zoning. This accessor reads the <em>current</em>
     * slot: if {@link #with} replaced the hydro {@link Density} (its water-surface
     * height), this returns a {@link WaterLevel} reading that replacement.
     *
     * @return the current water-level sampler (never null)
     */
    public WaterLevel waterLevel() {
        return waterLevel;
    }

    /**
     * The hydro dual-slot's classification seam: the default
     * {@link WaterClassSampler}, {@link VanillaWaterClass} over the terrain
     * dimension's field vs the sea level from {@link #bounds()}. It yields only
     * {@link WaterClass#NONE} / {@link WaterClass#OCEAN} (never a
     * {@link WaterClass#isReserved() reserved} class). This accessor reads the
     * <em>current</em> slot: if {@link #with} replaced the terrain field, this
     * reverts to classification over that replacement. Note the classification
     * threshold is the fixed {@code bounds().seaLevel()}, independent of the
     * current {@link #waterLevel()} (swapping only the hydro height does not
     * move the shoreline); the later hydrology batch replaces both samplers
     * together.
     *
     * @return the current water-class sampler (never null)
     */
    public WaterClassSampler waterClass() {
        return waterClass;
    }

    /** Default hydro water level: flat vanilla sea level {@link #HYDRO_SEA_LEVEL} (63). */
    private static WaterLevel waterLevelDefault() {
        return new ConstantWaterLevel(HYDRO_SEA_LEVEL);
    }

    /** Default hydro water class: vanilla fallback over the terrain field vs the sea level. */
    private static WaterClassSampler waterClassDefault(Map<EcoDim, DimensionSlot> slots, OverworldBounds bounds) {
        DimensionSlot terrain = slots.get(TERRAIN);
        if (terrain == null) {
            throw new IllegalStateException("terrain dimension missing; cannot derive default water-class sampler");
        }
        return new VanillaWaterClass(terrain.density(), bounds.seaLevel());
    }

    /**
     * Returns a new {@link DimensionTerrain} with the given surface rule list ,
     * preserving all slots and bounds (immutability). Pass {@code null} or an
     * empty list to keep the surface seam unwired. This is the p.1.8.5 wiring
     * point for a later batch; it does not change any density default.
     *
     * @param rules the ordered surface rules (may be null / empty)
     */
    public DimensionTerrain withSurfaceRules(List<SurfaceRule> rules) {
        List<SurfaceRule> copy = rules == null ? null : Collections.unmodifiableList(new ArrayList<>(rules));
        return new DimensionTerrain(slots, bounds, copy, waterLevel, waterClass);
    }

    /**
     * An unmodifiable view of all dimension slots, in canonical
     * {@link EcoDim#ALL} order (deterministic iteration).
     */
    public Map<EcoDim, DimensionSlot> slots() {
        return slots;
    }

    /**
     * Builds the nine default slots at the given seed. Deterministic: iteration
     * follows {@link EcoDim#ALL} order; each field depends only on the seed and
     * its per-dimension salt.
     */
    private static Map<EcoDim, DimensionSlot> buildDefaults(long seed) {
        Map<EcoDim, DimensionSlot> map = new LinkedHashMap<>();
        for (EcoDim dim : EcoDim.ALL) {
            switch (dim.id()) {
                case "terrain":
                    map.put(dim, new DimensionSlot(dim,
                            PerlinNoise.create(NoiseSalt.mix(seed, TERRAIN_SALT), 0, TERRAIN_AMPLITUDES)::getValue,
                            DimAlgo.VANILLA));
                    break;
                case "climate":
                    map.put(dim, new DimensionSlot(dim,
                            NormalNoise.create(NoiseSalt.mix(seed, CLIMATE_SALT), 0, 1.0)::getValue,
                            DimAlgo.VANILLA));
                    break;
                case "hydro":
                    // Step 3 ConstantWaterLevel not present yet: constant sea
                    // level 63 Density stands in; Step 3 replaces the sampler.
                    map.put(dim, new DimensionSlot(dim,
                            Densities.constant(HYDRO_SEA_LEVEL),
                            DimAlgo.CONSTANT));
                    break;
                case "litho":
                    map.put(dim, new DimensionSlot(dim,
                            new ValueNoise(NoiseSalt.mix(seed, LITHO_SALT), LITHO_SCALE)::eval,
                            DimAlgo.VANILLA));
                    break;
                case "mineral":
                    map.put(dim, new DimensionSlot(dim,
                            PerlinNoise.create(NoiseSalt.mix(seed, MINERAL_SALT), 0, MINERAL_AMPLITUDES)::getValue,
                            DimAlgo.VANILLA));
                    break;
                case "surface":
                    // The density here is an unused placeholder; the real seam
                    // is section 5 of the design — the p.1.8.5 evaluator via
                    // surfaceRules().
                    map.put(dim, new DimensionSlot(dim,
                            Densities.constant(0.0),
                            DimAlgo.VANILLA));
                    break;
                case "vegetation":
                case "fauna":
                case "relic":
                    // Reserved slots; semantic seams deferred to a later batch.
                    map.put(dim, new DimensionSlot(dim,
                            Densities.constant(0.0),
                            DimAlgo.CONSTANT));
                    break;
                default:
                    throw new IllegalStateException("unhandled dimension: " + dim.id());
            }
        }
        return map;
    }
}