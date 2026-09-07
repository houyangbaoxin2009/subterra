package io.toterra.subterra.worldgen.gen;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

import io.toterra.subterra.optim.worldgen.pipeline.router.NoiseRouter;

/**
 * Subterra's vanllia-compatible {@link DensityFunction} leaf (p.1.8.21),
 * registered into {@code Registries.DENSITY_FUNCTION_TYPE} under the JSON key
 * {@code "subterra:density"} so a {@code worldgen/noise_settings} datapack
 * entry can spell:
 *
 * <pre>{@code
 * "final_density": { "type": "subterra:density", "kind": "overworld_final" }
 * }</pre>
 *
 * The instance is immutable and carries only a seed-independent {@link Kind}
 * description ({@code overworld_final}). The actual evaluator is the p.1.8.14
 * vanilla-compatible composite overworld router from the optim core
 * ({@link NoiseRouter#overworld(long, int, int)}), whose {@code finalDensity}
 * field plugs into the {@code (x, y, z) -> double} {@link Density} seam. Because
 * a {@link DensityFunction} in 1.21.1 is <em>not</em> constructed with a seed, the
 * seed is captured separately at server start ({@link SubterraWorldgen}) and the
 * router is built lazily from it on first {@link #compute}; the routed result is
 * cached per world seed (rebuilt if the seed changes, e.g. a single-player world
 * switch in the same JVM).
 * <p>
 * Verified 1.21.1 facts pinned (javap on the mapped {-@literal n}eforge). 1.21.1:
 * {@link DensityFunction#codec()} returns a {@link KeyDispatchDataCodec}; there is
 * <em>no</em> separate {@code Type} interface and no {@code isStateIndependent()} —
 * the registry {@code Registries.DENSITY_FUNCTION_TYPE} is a
 * {@code Registry<MapCodec<? extends DensityFunction>>} that vanilla
 * {@code DensityFunctions.bootstrap}/{@code register} populate. {@code minValue/}
 * {@code maxValue} are set generously (the mirror's composite overworld final
 * density is finite but can spike with the {@code base_3d_noise} leaf), kept wide
 * so the generator never fails to find the surface; the practical surface range is
 * realistic ~[-1, 4] as in vanilla.
 * <p>
 * 与原生兼容的 {@link DensityFunction} 叶子（p.1.8.21），注册进
 * {@code Registries.DENSITY_FUNCTION_TYPE}、JSON 键 {@code "subterra:density"}，
 * 使 {@code worldgen/noise_settings} 数据包可用
 * {@code "type":"subterra:density","kind":"overworld_final"} 拼出 final_density。
 * 实例不可变，仅携带与种子无关的 {@link Kind} 描述；求值委托给 optim 核心
 * p.1.8.14 的原生兼容主世界复合路由器（{@link NoiseRouter#overworld}）的
 * {@code finalDensity}。1.21.1 的 {@code DensityFunction} 不携带种子，故世界种子
 * 由 {@link SubterraWorldgen} 在服务器启动时捕获，{@link #compute} 首次调用时
 * 才按种子惰性构建路由器并按世界种子缓存（种子变化则重建）。
 */
public final class SubterraDensity implements DensityFunction {

    /**
     * Which seed-independent density a {@code "subterra:density"} describes.
     * Each kind fixes the canonical {@code [minY, maxY)} block window used by the
     * router (overworld = &#8722;64..320, matching vanilla overworld.json).
     */
    public enum Kind {
        /** The p.1.8.14 composite overworld {@code finalDensity}. */
        OVERWORLD_FINAL("overworld_final", -64, 320),
        ;

        private final String id;
        private final int minY;
        private final int maxY;

        Kind(String id, int minY, int maxY) {
            this.id = id;
            this.minY = minY;
            this.maxY = maxY;
        }

        /** The JSON {@code "kind"} literal. */
        public String id() {
            return id;
        }

        /** Minimum block Y (inclusive). */
        public int minY() {
            return minY;
        }

        /** Maximum block Y (exclusive). */
        public int maxY() {
            return maxY;
        }

        /**
         * @throws IllegalArgumentException if {@code id} names no known kind.
         */
        static Kind byId(String id) {
            Objects.requireNonNull(id, "kind id");
            for (Kind k : values()) {
                if (k.id.equals(id)) {
                    return k;
                }
            }
            throw new IllegalArgumentException("unknown subterra:density kind '" + id + "'");
        }
    }

    /** {@code { "kind": <enum id> }}, the body of the {@code subterra:density} JSON. */
    public static final MapCodec<SubterraDensity> DATA_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.STRING.fieldOf("kind")
                            .forGetter(d -> d.kind.id)
            ).apply(instance, id -> new SubterraDensity(Kind.byId(id))));

    /** The key-dispatched codec; its {@code MapCodec} is what we register. */
    public static final KeyDispatchDataCodec<SubterraDensity> CODEC =
            KeyDispatchDataCodec.of(DATA_CODEC);

    /**
     * Documented over-generous bounds for the overworld {@code finalDensity}
     * (finite but spikable via the {@code base_3d_noise} leaf; practical range
     * ~[-1, 4], vanilla-like). Wide so the surface search never under-ranges.
     */
    public static final double MIN_VALUE = -1000.0;
    /** See {@link #MIN_VALUE}. */
    public static final double MAX_VALUE = 1000.0;

    private final Kind kind;

    // ---- lazily seeded router cache (keyed by the world seed) ----
    private volatile long cachedSeed = Long.MIN_VALUE;
    private volatile NoiseRouter cachedRouter;
    /** Logged once per JVM if the router cache ever sees a mid-world seed change. */
    private static volatile boolean SEED_SWITCH_LOGGED = false;

    public SubterraDensity(Kind kind) {
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    /** The seed-independent description this density materialises. */
    public Kind kind() {
        return kind;
    }

    @Override
    public double compute(DensityFunction.FunctionContext context) {
        // Hot-path seed query: reads the captured world seed directly (phase-guaranteed at
        // ServerAboutToStart), never touches ServerLifecycleHooks here, never drifts between
        // threads, and logs loudly once if the fallback constant is ever used.
        long seed = SubterraWorldgen.worldSeed();
        return routerFor(seed).finalDensity()
                .eval((double) context.blockX(), (double) context.blockY(), (double) context.blockZ());
    }

    @Override
    public void fillArray(double[] array, DensityFunction.ContextProvider provider) {
        for (int i = 0; i < array.length; i++) {
            DensityFunction.FunctionContext point = provider.forIndex(i);
            array[i] = compute(point);
        }
    }

    @Override
    public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        // The world seed is captured out-of-band (SubterraWorldgen), so the wiring
        // visitor (RandomState$NoiseWiringHelper) does not need to touch this leaf.
        return this;
    }

    @Override
    public double minValue() {
        return MIN_VALUE;
    }

    @Override
    public double maxValue() {
        return MAX_VALUE;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

    /** Returns the seeded overworld router for {@code seed}, cached (single-flight). */
    private NoiseRouter routerFor(long seed) {
        NoiseRouter current = cachedRouter;
        if (current != null && cachedSeed == seed) {
            return current;
        }
        synchronized (this) {
            current = cachedRouter;
            if (current == null || cachedSeed != seed) {
                // A cached router for a DIFFERENT seed means the seed drifted mid-world.
                // Never silently switch: log loudly once (would have caught any drift).
                if (cachedRouter != null && cachedSeed != seed && !SEED_SWITCH_LOGGED) {
                    SEED_SWITCH_LOGGED = true;
                    io.toterra.subterra.Subterra.LOGGER.error(
                            "Subterra density: world seed changed mid-world from {} to {}; terrain is regenerating "
                                    + "with the new seed (this should never occur with phase-guaranteed capture).",
                            cachedSeed, seed);
                }
                current = NoiseRouter.overworld(seed, kind.minY, kind.maxY);
                cachedSeed = seed;
                cachedRouter = current;
            }
        }
        return current;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof SubterraDensity that
                && this.kind == that.kind);
    }

    @Override
    public int hashCode() {
        return kind.hashCode();
    }

    @Override
    public String toString() {
        return "subterra:density[kind=" + kind.id + "]";
    }
}