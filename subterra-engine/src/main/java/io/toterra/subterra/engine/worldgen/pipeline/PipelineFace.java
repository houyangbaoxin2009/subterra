package io.toterra.subterra.engine.worldgen.pipeline;

/**
 * The eight world-generation pipeline faces of the p.1.8+ track
 * (re-keyed from the R8.0–R8.7 plan): the 1.21.1 pipeline is data-driven —
 * each face is a {@code Registry<Codec>} surface extensible via
 * {@code data/<namespace>/worldgen/<folder>} JSON (plus the two registry-less
 * input faces). {@link PipelineFaces} validates folder + registry-id tokens
 * deterministically so the MC shells and data packs stay consistent.
 */
public enum PipelineFace {

    /** NoiseGeneratorSettings: noise router, surface rule, sea level, bedrock. */
    NOISE_SETTINGS("worldgen/noise_settings", "worldgen/noise_settings"),
    /** DensityFunctions: the fifteen NoiseRouter DensityFunction fields. */
    DENSITY_FUNCTIONS("worldgen/density_function", "worldgen/density_function"),
    /** SurfaceRules: RuleSource / ConditionSource trees. */
    SURFACE_RULES("worldgen/surface_rule", "worldgen/surface_rule"),
    /** BiomeSource / Biome registry (EcoDims model hooks in here). */
    BIOMES("worldgen/biome", "worldgen/biome"),
    /** Structure features + placement (WorldGenGuard collision guard hooks here). */
    STRUCTURES("worldgen/structure", "worldgen/structure_set"),
    /** Carvers + ore placed features. */
    CAVES_ORES("worldgen/placed_feature", "worldgen/configured_carver"),
    /** WorldPreset: presets over noise_settings + generator type. */
    PRESETS("worldgen/world_preset", "worldgen/world_preset"),
    /** Perf validation across all faces (probe-gated analogue). */
    PERF("probes/perf", null);

    private final String dataFolder;
    private final String registryId;

    PipelineFace(String dataFolder, String registryId) {
        this.dataFolder = dataFolder;
        this.registryId = registryId;
    }

    /** Data-pack folder under {@code data/<namespace>/}. */
    public String dataFolder() {
        return dataFolder;
    }

    /** Registry id for codec-driven faces; null for registry-less rows. */
    public String registryId() {
        return registryId;
    }
}