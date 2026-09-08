package io.toterra.subterra.runtime.worldgen.gen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import io.toterra.subterra.Subterra;

import java.util.List;

/**
 * Registration + seed-wiring hub for Subterra's MC-layer worldgen adoption
 * (p.1.8.21, td-gated): makes "Subterra" selectable as a world type by
 * registering the {@code subterra:density} density-function codec and capturing
 * the world seed the {@link SubterraDensity} leaf evaluates against.
 * <p>
 * Two responsibilities:
 * <ul>
 *   <li>{@link #registerDensityFunctionType} — NeoForge {@link RegisterEvent} for
 *       {@code Registries.DENSITY_FUNCTION_TYPE}: registers
 *       {@code "subterra:density"} {@code ->} {@link SubterraDensity#CODEC}'s
 *       {@code MapCodec}. This is the same dispatch registry vanilla
 *       {@code DensityFunctions} populate, so the datapack
 *       {@code worldgen/noise_settings} JSON's {@code {"type":"subterra:density",
 *       "kind":"overworld_final"}} decodes through the standard key-dispatch codec.</li>
 *   <li>{@link #onServerStarting} — captures the world seed from
 *       {@code server.getWorldData().worldGenOptions().seed()} once the world data
 *       exists (seeded before any chunk generation). A 1.21.1
 *       {@code DensityFunction} carries no seed, and its wiring visitor
 *       ({@code RandomState$NoiseWiringHelper}) hides the seed as a private field,
 *       so the seed is captured out-of-band here; {@link SubterraDensity#compute}
 *       lazily builds the router the first time it runs.</li>
 * </ul>
 * Calls are guarded so a spurious double invocation (e.g. a re-registration) is
 * harmless.
 * <p>
 * Datapack ids shipped alongside:
 * {@code subterra:subterra_overworld} (noise_settings) and {@code subterra:subterra}
 * (world_preset). The preset is unused unless selected at world creation, so the
 * default (vanilla) path is completely unaffected — zero effect on the boot gate.
 * <p>
 * 注册与种子接线中枢（p.1.8.21，td 门控）：把 "Subterra" 装成可选世界类型——
 * 注册 {@code subterra:density} 密度函数编解码器，并在服务器启动时捕获世界种子。
 * 1.21.1 的 {@code DensityFunction} 不携带种子，其接线 visitor 又把种子藏在私有字段，
 * 故此处额外捕获种子供 {@link SubterraDensity} 惰性构建路由器。
 */
public final class SubterraWorldgen {

    /** Sentinel: no world seed captured yet. */
    public static final long SEED_UNKNOWN = Long.MIN_VALUE;

    /** Fixed deterministic seed used only if capture is impossible (pre-server init / datagen wiring). */
    public static final long FALLBACK_SEED = 0L;

    /** The density-function type id: {@code {"type": "subterra:density", ...}}. */
    public static final ResourceLocation DENSITY_TYPE_ID =
            ResourceLocation.parse("subterra:density");
    /** The shipped noise-settings id (overworld data with the subterra final density). */
    public static final String NOISE_SETTINGS_OVERWORLD = "subterra:subterra_overworld";
    /** The shipped world-preset id (world-type option). */
    public static final String WORLD_PRESET = "subterra:subterra";
    /** Lang display-name key for the preset. */
    public static final String WORLD_PRESET_LANG_KEY = "subterra.world_preset.subterra";

    private static volatile long worldSeed = SEED_UNKNOWN;
    private static volatile boolean densityTypeRegistered = false;
    /** Only the first hot-path fallback-to-constant is logged loudly per JVM. */
    private static volatile boolean fallbackLogged = false;

    private SubterraWorldgen() {
    }

    /**
     * Called from the mod constructor: wires the registration + seed listeners.
     *
     * @param modEventBus the mod event bus (for {@link RegisterEvent}); never null.
     */
    public static void bootstrap(IEventBus modEventBus) {
        modEventBus.addListener(SubterraWorldgen::registerDensityFunctionType);
        // Capture the seed as early as guaranteed (server data is present before any
        // chunk generation) and clear it on stop so a world switch never reuses a stale seed.
        NeoForge.EVENT_BUS.addListener(SubterraWorldgen::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(SubterraWorldgen::onServerStarting);
        NeoForge.EVENT_BUS.addListener(SubterraWorldgen::onServerStopped);
    }

    /**
     * Registers {@code subterra:density} into the density-function-type registry.
     * {@code public static} (not {@code private}) to exactly match the repo's
     * proven {@code NeoForge.EVENT_BUS.addListener(Class::method)} idiom.
     */
    public static void registerDensityFunctionType(RegisterEvent event) {
        if (event.getRegistryKey() != Registries.DENSITY_FUNCTION_TYPE) {
            return;
        }
        if (densityTypeRegistered) {
            return; // harmless double-invocation guard
        }
        event.register(Registries.DENSITY_FUNCTION_TYPE, DENSITY_TYPE_ID,
                () -> SubterraDensity.CODEC.codec());
        densityTypeRegistered = true;
        Subterra.LOGGER.info("Subterra worldgen: registered density-function type {} "
                + "(preset {} / noise_settings {})", DENSITY_TYPE_ID, WORLD_PRESET, NOISE_SETTINGS_OVERWORLD);
    }

    /** Captures the world seed at {@link ServerAboutToStartEvent}: world data exists,
     * strictly before any chunk generation, so the leaf is never evaluated seedless. */
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        captureSeed(event.getServer());
    }

    /** Recomputes + logs the captured seed and the shipped subterra datapack ids. */
    public static void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        captureSeed(server);
        var registryAccess = server.registryAccess();
        List<ResourceLocation> worldPresets = registryAccess
                .registryOrThrow(Registries.WORLD_PRESET).keySet().stream()
                .filter(k -> k.getNamespace().equals("subterra"))
                .toList();
        List<ResourceLocation> noiseSettings = registryAccess
                .registryOrThrow(Registries.NOISE_SETTINGS).keySet().stream()
                .filter(k -> k.getNamespace().equals("subterra"))
                .toList();
        Subterra.LOGGER.info("Subterra worldgen: present subterra world_presets={} "
                + "noise_settings={}", worldPresets, noiseSettings);
    }

    /** Clears the captured seed on server stop so a world switch never reuses a stale seed. */
    public static void onServerStopped(ServerStoppedEvent event) {
        worldSeed = SEED_UNKNOWN;
        fallbackLogged = false;
        Subterra.LOGGER.info("Subterra worldgen: world seed cleared (server stopped)");
    }

    /** Shared capture: read the authoritative world seed from {@code worldGenOptions}. */
    private static void captureSeed(MinecraftServer server) {
        if (server == null || server.getWorldData() == null) {
            return;
        }
        worldSeed = server.getWorldData().worldGenOptions().seed();
        Subterra.LOGGER.info("Subterra worldgen: captured world seed {} for {}",
                worldSeed, DENSITY_TYPE_ID);
    }

    /**
     * Hot-path seed getter used by {@link SubterraDensity#compute}. Returns the captured
     * world seed, or the process-wide {@link #FALLBACK_SEED} if capture has not happened
     * (pre-server-init / datagen wiring), logging the fallback loudly once per JVM. This
     * never touches {@link ServerLifecycleHooks} (thread-safe, allocation-free, consistent —
     * a seed can never drift between threads mid-world).
     */
    public static long worldSeed() {
        long seed = SubterraWorldgen.worldSeed;
        if (seed != SEED_UNKNOWN) {
            return seed;
        }
        if (!fallbackLogged) {
            fallbackLogged = true;
            Subterra.LOGGER.error("Subterra worldgen: computed density BEFORE world seed capture; "
                    + "using fixed fallback seed {} (should not happen after ServerAboutToStart)", FALLBACK_SEED);
        }
        return FALLBACK_SEED;
    }

    /**
     * The captured world seed, or {@link #SEED_UNKNOWN} if no server is live yet.
     * Stored {@code volatile} so the multi-threaded chunk-generation workers observe
     * it reliably. When the game-bus {@link ServerStartingEvent} has not been seen for
     * a boot (defensive; see {@link #onServerStarting}), falls back to asking
     * {@link ServerLifecycleHooks#getCurrentServer()} for the live server's seed once,
     * so {@link SubterraDensity} is always seed-sensitive by the time a chunk generates.
     * Not used on the hot path (use {@link #worldSeed()}).
     */
    public static long worldSeedOrUnknown() {
        long seed = SubterraWorldgen.worldSeed;
        if (seed != SEED_UNKNOWN) {
            return seed;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.getWorldData() != null) {
            seed = server.getWorldData().worldGenOptions().seed();
            worldSeed = seed;
        }
        return seed;
    }
}