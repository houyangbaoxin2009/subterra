package io.toterra.subterra.runtime.worldgen.gen;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

import io.toterra.subterra.engine.worldgen.pipeline.router.NoiseRouter;
import io.toterra.subterra.engine.worldgen.tie.TiePoiResidency;
import io.toterra.subterra.Subterra;
import io.toterra.subterra.engine.worldgen.tie.TieTerrainDensityBridge;

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
        // p.1.8.33 fix: the composite anchors (y_clamped_gradient / depth splines / slides) are
        // vanilla-window-bound (surface ~63); a wider Kind window here desynchronises the JSON
        // noise block from the terrain shape (the 2026-09-16 drowned-world regression) — keep
        // the window identical to the vanilla overworld recipe until window re-parameterisation
        // is a designed feature.
        OVERWORLD_FINAL("overworld_final", -64, 384, 127.0),
        ;

        private final String id;
        private final int minY;
        private final int maxY;
        /** 设计海平面（p.1.8.33：Subterra 不再还原原版海平面）。 / The design sea level (p.1.8.33). */
        private final double seaLevel;

        Kind(String id, int minY, int maxY, double seaLevel) {
            this.id = id;
            this.minY = minY;
            this.maxY = maxY;
            this.seaLevel = seaLevel;
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

    // ---- tie 桥（性能改造）：DLL 可用时替代 compute 的纯 Java 求值；否则走下方 Java 树回退 ----
    private static volatile TieTerrainDensityBridge tieBridge;
    private static final Object TIE_BRIDGE_LOCK = new Object();
    /** 一次装载失败后置真，避免每 chunk 重复尝试 load()（静默降级）。 */
    private static volatile boolean tieBridgeFailed = false;

    /** p.1.8.33 默认跳过日志只打一次。 / The p.1.8.33 default-skip log-once flag. */
    private static volatile boolean TIE_BRIDGE_SKIP_LOGGED;

    // ---- lazily seeded router cache (keyed by the world seed) ----
    private volatile long cachedSeed = Long.MIN_VALUE;
    private volatile NoiseRouter cachedRouter;
    /** Logged once per JVM if the router cache ever sees a mid-world seed change. */
    private static volatile boolean SEED_SWITCH_LOGGED = false;

    // ---- p.1.8.32 perf counters (opt-in via system property subterra.perfCount=1) ----
    private static final boolean PERF = Boolean.parseBoolean(
            System.getProperty("subterra.perfCount", "false"));

    // ---- p.2.29.1 boot-time assembly snapshot (rules → density; identity by default = vanilla-equivalent) ----
    private static volatile double assemblyOffset = 0.0;
    private static volatile double assemblyScale = 1.0;

    /**
     * Sets the boot-time density assembly snapshot ({@code value*scale+offset}); defaults {@code 0.0}/{@code 1.0}
     * mean identity (zero behaviour change on the default preset). Deterministic: captured once at server start
     * ( {@link SubterraWorldgen#onServerStarting}); the hot-reload wiring point is documented in runtime-wiring.md.
     * / 设置启动期密度装配快照（{@code value*scale+offset}）；缺省 {@code 0.0}/{@code 1.0} 为恒等（缺省预设零
     * 行为变化）。确定性：服务器启动时一次性捕获；热重载接线点见 runtime-wiring.md。
     */
    public static void setAssembly(double offset, double scale) {
        if (!Double.isFinite(offset) || Math.abs(offset) > 128.0) {
            throw new IllegalArgumentException("density offset must be finite within ±128, got " + offset);
        }
        if (!Double.isFinite(scale) || scale < 0.0 || scale > 4.0) {
            throw new IllegalArgumentException("density scale must be finite in [0, 4], got " + scale);
        }
        assemblyOffset = offset;
        assemblyScale = scale;
    }

    /** Applies the assembly snapshot to a raw density value (identity on defaults). /
     *  对原始密度值应用装配快照（缺省为恒等）。 */
    private static double applyAssembly(double v) {
        return v * assemblyScale + assemblyOffset;
    }

    // p.2.35 density-seam order (deterministic): density offset/scale first, then the Trimand
    // three-field hint via SubterraTrimand.apply — which is a bit-exact pass-through while the
    // Trimand snapshot is identity (the default), so the default preset stays vanilla-equivalent.
    /** Total compute() hot-path calls since the last reset. */
    private static volatile long perfComputeCalls;
    /** Total samples filled (fillArray elements). */
    private static volatile long perfFillSamples;
    /** Distinct (cellX, cellY, cellZ) fingerprints seen — coarse cell-cache-effectiveness probe. */
    private static volatile long perfDistinctCells;

    /** Resets the opt-in counters. */
    public static void perfReset() {
        perfComputeCalls = 0;
        perfFillSamples = 0;
        perfDistinctCells = 0;
    }

    /** Returns a ONE-LINE summary of the opt-in counters (or a no-op line when disabled). */
    public static String perfSummary() {
        return "[subterra_perf] computeCalls=" + perfComputeCalls
                + " fillSamples=" + perfFillSamples
                + " distinctCells=" + perfDistinctCells
                + (PERF ? "" : " (disabled; set -Dsubterra.perfCount=true)");
    }

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
            TieTerrainDensityBridge bridge = tieBridgeForCompute();
            if (bridge != null) {
                // Wave B（修正）：y 分块 + POI 分级调度仅作为"内存驻留/缓存"策略存在，
                // 不改变密度函数的数值。MC 要求每个 (x,y,z) 都返回有效密度——任何点
                // 被替换成占位空气都会让表面搜索塌方成断层/混合（此前回归）。
                // 因此热路径始终返回 tie 真值；POI 侧的低精度先验 LOD 后续以平滑
                // 过渡接入（f\ 边界保持连续），不在本层切值。
                long bx = context.blockX();
                long by = context.blockY();
                long bz = context.blockZ();
                double v = bridge.density(bx, by, bz, seed);
                if (PERF) {
                    perfComputeCalls++;
                    perfDistinctCells++;
                }
                return SubterraTrimand.apply(applyAssembly(v), bx, bz, seed);
            }
        // 回退：纯 Java 的 p.1.8.14 复合主世界路由器（DLL 缺失/失败时与原先逐位一致）。
        long bx = context.blockX();
        long bz = context.blockZ();
        double v = routerFor(seed).finalDensity()
                .eval((double) bx, (double) context.blockY(), (double) bz);
        if (PERF) {
            perfComputeCalls++;
            perfDistinctCells++;
        }
        return SubterraTrimand.apply(applyAssembly(v), bx, bz, seed);
    }

    @Override
    public void fillArray(double[] array, DensityFunction.ContextProvider provider) {
        if (PERF) {
            perfFillSamples += array.length;
        }
        for (int i = 0; i < array.length; i++) {
            DensityFunction.FunctionContext point = provider.forIndex(i);
            array[i] = compute(point);
        }
    }

    @Override
    public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        // p.1.8.29C: hand this leaf to the wiring visitor (the DensityFunction interface
        // default), exactly like every other vanilla leaf. The per-chunk sampler's visitor
        // then wraps us in its cell cache (NoiseChunk$CacheAllInCell), so the final-density
        // tree is sampled once per 4x4x8 cell corner and trilinearly interpolated instead of
        // being re-evaluated per block (the former "return this" skipped the visitor and left
        // the whole sloped-cheese + cave-family tree uncached -> one full-tree eval per block,
        // 100% CPU during chunk generation). The world-level wiring visitor (RandomState
        // NoiseWiringHelper) returns this leaf unchanged.
        return visitor.apply(this);
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

    /**
     * Returns the ready tie {@link TieTerrainDensityBridge}, or {@code null} if it could not be
     * loaded (DLL missing/failed). Single-flight lazy init (volatile double-check); a failure is
     * cached in {@link #tieBridgeFailed} so no per-chunk retry happens. Never throws.
     */
    private static TieTerrainDensityBridge tieBridgeForCompute() {
        // p.1.8.33: the bundled subterra_density.dll is a STALE divergent build — it predates the
        // sea-127 re-anchoring and its outputs diverge from the Java tree by up to ~1.46 (verified
        // by the TerrainAxisProbe bridge audit), so it must NOT drive terrain. Default OFF: the
        // proven pure-Java tree generates terrain; opt in explicitly with
        // -Dsubterra.density.tie=true ONLY after the tie track regenerates an equivalent DLL and
        // an equivalence probe pins it.
        if (!Boolean.getBoolean("subterra.density.tie")) {
            if (!TIE_BRIDGE_SKIP_LOGGED) {
                TIE_BRIDGE_SKIP_LOGGED = true;
                Subterra.LOGGER.info(
                        "Subterra density: tie bridge skipped by default (stale DLL vs the p.1.8.33 sea-level "
                                + "design); the pure-Java tree generates terrain. Opt in with -Dsubterra.density.tie=true.");
            }
            return null;
        }
        TieTerrainDensityBridge current = tieBridge;
        if (current != null) {
            return current.available() ? current : null;
        }
        if (tieBridgeFailed) {
            return null;
        }
        synchronized (TIE_BRIDGE_LOCK) {
            current = tieBridge;
            if (current != null) {
                return current.available() ? current : null;
            }
            if (tieBridgeFailed) {
                return null;
            }
            current = TieTerrainDensityBridge.load();
            if (current == null) {
                tieBridgeFailed = true; // 静默降级：后续次数的 compute 直接走 Java 树，不再尝试装载
            } else {
                tieBridge = current;
                // Wave B：把就绪桥绑定进 POI 调度器（供 block_active / y_to_block 判别）。
                TiePoiResidency.instance().bind(current);
            }
            return current;
        }
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
                current = NoiseRouter.overworld(seed, kind.minY, kind.maxY, kind.seaLevel);
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