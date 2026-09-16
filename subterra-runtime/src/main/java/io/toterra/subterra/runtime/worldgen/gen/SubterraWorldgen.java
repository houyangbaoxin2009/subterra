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
import io.toterra.subterra.engine.worldgen.assembly.AssemblySeam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        // p.2.29.4 / p.2.35 boot-time rules → assembly snapshot (deterministic): one effective rule
        // table is read once from the boot-override tier (one system property per rule key) and
        // parsed through the single assembly seam (AssemblySeam.of). The default preset (no
        // override) resolves to identity, so the shipped world stays vanilla-equivalent; an invalid
        // value deterministically rejects the whole table and keeps the identity snapshot. The
        // datapack/save-tier hot-reload wiring point is documented in runtime-wiring.md.
        AssemblySeam.Resolved resolved;
        String rejected = null;
        try {
            resolved = AssemblySeam.of(bootAssemblyRules());
        } catch (IllegalArgumentException e) {
            resolved = AssemblySeam.DEFAULTS;
            rejected = e.getMessage();
        }
        SubterraDensity.setAssembly(resolved.density().densityOffset(), resolved.density().densityScale());
        SubterraTrimand.setSnapshot(resolved.trimand());
        // p.2.29.3.1: the assembly ore-density scalar multiplies the feature plan's per-mineral
        // counts (floor + clamp, base plan untouched -> no compounding across boots). d = 1
        // (default) is the identity fast path.
        SubterraFeaturePlan.applyOreDensity(resolved.density().oreDensity());
        if (rejected != null) {
            Subterra.LOGGER.error("[Subterra assembly] reject ({}) -> identity defaults (no behaviour change)", rejected);
        }
        if (System.getProperty("subterra.probe.assembly") != null) {
            Subterra.LOGGER.info("[Subterra assembly] {} (snapshot)", AssemblySeam.render(resolved));
            Subterra.LOGGER.info("{}", SubterraTrimand.statusLine());
        }
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
     * 读取装配规则键的 boot 覆盖层：每个 {@link AssemblySeam#RULE_KEYS} 一条同名 system property
     * （dev 由 gradle {@code -D}/{@code -P} 转发），缺失或空白即「未覆盖」。返回表按
     * {@link AssemblySeam#RULE_KEYS} 的固定规范序，确定性（同属性集恒得同表）；本方法不校验值，
     * 校验由 {@link AssemblySeam#of} 一次性完成（任一条非法即整表拒绝）。
     * <p>
     * Reads the boot-override tier of the assembly rule keys: one same-named system property per
     * {@link AssemblySeam#RULE_KEYS} entry (forwarded by gradle in dev); absent or blank means "not
     * overridden". The returned table follows the fixed canonical order of
     * {@link AssemblySeam#RULE_KEYS} and is deterministic (same properties → same table). Validation
     * is deliberately left to {@link AssemblySeam#of}, which rejects the whole table on any invalid
     * value.
     */
    private static Map<String, String> bootAssemblyRules() {
        Map<String, String> rules = new LinkedHashMap<>();
        for (String key : AssemblySeam.RULE_KEYS) {
            String value = System.getProperty(key);
            if (value != null && !value.isBlank()) {
                rules.put(key, value);
            }
        }
        return rules;
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