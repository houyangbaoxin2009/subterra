package io.toterra.subterra.optim.worldgen.pipeline.terrain;

import java.util.concurrent.ConcurrentHashMap;

import io.toterra.subterra.optim.worldgen.pipeline.density.ValueNoise;
import io.toterra.subterra.optim.worldgen.pipeline.formula.MathLib;
import io.toterra.subterra.optim.worldgen.pipeline.noise.XoroRandom;
import io.toterra.subterra.optim.worldgen.pipeline.noise.perlin.PerlinNoise;
import io.toterra.subterra.optim.worldgen.pipeline.noise.simplex.NormalNoise;
import io.toterra.subterra.optim.worldgen.pipeline.noise.simplex.SimplexNoise;

/**
 * Math-formula terrain's deterministic noise / pseudo-random functions (p.1.8.10,
 * clean-room). Registers into {@link MathLib} the seed-aware functions a user
 * formula can call:
 * <ul>
 *   <li>{@code perlin(x,y,z)} — octave {@link PerlinNoise}, deterministic per seed;</li>
 *   <li>{@code simplex(x,y,z)} — {@link SimplexNoise}, deterministic per seed;</li>
 *   <li>{@code normal(x,y,z)} — {@link NormalNoise}, deterministic per seed;</li>
 *   <li>{@code value(x,y,z)} — lattice {@link ValueNoise}, deterministic per seed;</li>
 *   <li>{@code rand(x,y,z)} — deterministic pseudo-random in {@code [0,1)};</li>
 *   <li>{@code randrange(lo,hi,x,y,z)} — deterministic in {@code [lo,hi)}.</li>
 * </ul>
 * Every function is <em>deterministic by design</em>: the same seed + the same
 * integer coordinates always yield the same value, at any time, on any JVM. This
 * is a deliberate departure from the GPL original, whose non-deterministic RNG
 * functions caused per-chunk slowdowns; ours never re-seed from entropy or the
 * wall clock.
 * <p>
 * The fixed {@link MathLib.MathFunction} contract ({@code apply(double[])}) and
 * {@code EvalContext} carry no seed, so the per-evaluation seed is supplied by
 * {@link FormulaTerrain} through a lightweight per-thread frame channel
 * ({@link #begin}/{@link #end}); the value the channel carries is exactly the
 * {@code EvalContext.seed()} {@link FormulaTerrain} builds. Outside a
 * {@link FormulaTerrain} frame the functions fall back to seed {@code 0}, which
 * remains deterministic.
 * <p>
 * {@link #install()} is idempotent ({@code isKnown} guard) and never overwrites
 * a pre-registered {@link MathLib} built-in.
 * <p>
 * 数学公式地形确定的噪声 / 伪随机函数（p.1.8.10，净室）。向 {@link MathLib}
 * 注册用户公式可调用的感知种子的函数。每个函数<em>按设计确定</em>：相同种子 +
 * 相同整数坐标任意时刻、任意 JVM 都给出相同值。这是对 GPL 原版的刻意偏离——
 * 原版不确定的 RNG 函数导致逐区块卡顿；本实现从不从熵或墙钟重新播种。
 * 由于固定的 {@link MathLib.MathFunction} 契约（{@code apply(double[])}）与
 * {@code EvalContext} 不携带种子，每次求值的种子由 {@link FormulaTerrain}
 * 经轻量线程帧通道（{@link #begin}/{@link #end}）注入；通道所带的值恰为
 * {@link FormulaTerrain} 构建的 {@code EvalContext.seed()}。在
 * {@link FormulaTerrain} 帧之外，函数回退到种子 {@code 0}，仍然确定。
 */
public final class FormulaNoiseFunctions {

    /** FIXED lattice cell size (blocks) used by the in-formula {@code value()} function. */
    private static final double VALUE_FUNCTION_SCALE = 1.0;

    private static final ThreadLocal<long[]> CURRENT_SEED = ThreadLocal.withInitial(() -> new long[]{0L});

    // Lazy per-seed noise handles so the hot path never re-seeds a field.
    private static final ConcurrentHashMap<Long, PerlinNoise> PERLIN = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, SimplexNoise> SIMPLEX = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, NormalNoise> NORMAL = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, ValueNoise> VALUE = new ConcurrentHashMap<>();

    private static volatile boolean installed = false;

    private FormulaNoiseFunctions() {
    }

    /**
     * Registers the formula functions into {@link MathLib} exactly once
     * (idempotent; each name is guarded by {@link MathLib#isKnown}). Safe to
     * call repeatedly, including concurrently. Called automatically by
     * {@link FormulaTerrain}'s constructor.
     */
    public static void install() {
        if (installed) {
            return;
        }
        register("perlin", 3, args -> FormulaNoiseFunctions.perlin(args[0], args[1], args[2]));
        register("simplex", 3, args -> FormulaNoiseFunctions.simplex(args[0], args[1], args[2]));
        register("normal", 3, args -> FormulaNoiseFunctions.normal(args[0], args[1], args[2]));
        register("value", 3, args -> FormulaNoiseFunctions.value(args[0], args[1], args[2]));
        register("rand", 3, args -> FormulaNoiseFunctions.rand(args[0], args[1], args[2]));
        register("randrange", 5, args -> FormulaNoiseFunctions.randRange(
                args[0], args[1], args[2], args[3], args[4]));
        installed = true;
    }

    /** Idempotent single registration. */
    private static void register(String name, int arity, MathLib.MathFunction fn) {
        if (!MathLib.isKnown(name)) {
            MathLib.register(name, (args) -> {
                if (args.length != arity) {
                    throw new IllegalArgumentException("function '" + name + "' expects "
                            + arity + " argument(s) but got " + args.length);
                }
                return fn.apply(args);
            });
        }
    }

    /**
     * Opens an evaluation frame carrying the given seed; returns the previous
     * seed so {@link #end} can restore it. Package-private, called by
     * {@link FormulaTerrain} around {@code Expr.eval}.
     */
    static long begin(long seed) {
        long[] ref = CURRENT_SEED.get();
        long prev = ref[0];
        ref[0] = seed;
        return prev;
    }

    /** Closes an evaluation frame, restoring the seed captured by {@link #begin}. */
    static void end(long previous) {
        CURRENT_SEED.get()[0] = previous;
    }

    /** The seed for the current evaluation frame (0 outside any frame). */
    private static long currentSeed() {
        return CURRENT_SEED.get()[0];
    }

    // ---------- the registered functions ----------

    /** {@code perlin(x,y,z)} — deterministic octave Perlin field for the current seed. */
    private static double perlin(double x, double y, double z) {
        return PERLIN.computeIfAbsent(currentSeed(),
                s -> PerlinNoise.create(s, 0, new double[]{1.0})).getValue(x, y, z);
    }

    /** {@code simplex(x,y,z)} — deterministic simplex field for the current seed. */
    private static double simplex(double x, double y, double z) {
        return SIMPLEX.computeIfAbsent(currentSeed(), SimplexNoise::fromSeed).getValue(x, y, z);
    }

    /** {@code normal(x,y,z)} — deterministic normal field for the current seed. */
    private static double normal(double x, double y, double z) {
        return NORMAL.computeIfAbsent(currentSeed(),
                s -> NormalNoise.create(s, 0, 1.0)).getValue(x, y, z);
    }

    /** {@code value(x,y,z)} — deterministic lattice value field for the current seed. */
    private static double value(double x, double y, double z) {
        return VALUE.computeIfAbsent(currentSeed(),
                s -> new ValueNoise(s, VALUE_FUNCTION_SCALE)).eval(x, y, z);
    }

    /**
     * {@code rand(x,y,z)} — deterministic pseudo-random value in {@code [0,1)}
     * derived from the seed and the integer lattice cell of the coordinates via
     * {@link XoroRandom}, so the same seed + cell always yields the same value.
     */
    private static double rand(double x, double y, double z) {
        long c = coordSeed(currentSeed(), floorLong(x), floorLong(y), floorLong(z));
        return new XoroRandom(c).nextDouble();
    }

    /** {@code randrange(lo,hi,x,y,z)} — deterministic uniform value in {@code [lo,hi)}. */
    private static double randRange(double lo, double hi, double x, double y, double z) {
        if (!(hi > lo)) {
            throw new IllegalArgumentException("randrange requires hi > lo, got lo=" + lo + " hi=" + hi);
        }
        return lo + rand(x, y, z) * (hi - lo);
    }

    /** Deterministic per-coordinate seed mixing a master seed with integer cell coords. */
    private static long coordSeed(long seed, long ix, long iy, long iz) {
        long h = seed;
        h ^= (ix * 0x9E3779B97F4A7C15L) ^ Long.rotateLeft(iy, 17) ^ Long.rotateLeft(iz, 33);
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        return h;
    }

    private static long floorLong(double v) {
        return (long) Math.floor(v);
    }
}