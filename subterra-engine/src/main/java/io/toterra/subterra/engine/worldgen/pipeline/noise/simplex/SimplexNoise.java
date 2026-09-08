package io.toterra.subterra.engine.worldgen.pipeline.noise.simplex;

import java.util.function.IntUnaryOperator;

import io.toterra.subterra.engine.worldgen.pipeline.noise.LegacyRandom;
import io.toterra.subterra.engine.worldgen.pipeline.noise.XoroRandom;

/**
 * Deterministic simplex noise, bit-identical in structure, constants and
 * ordering to Minecraft 1.21.1's
 * {@code net.minecraft.world.level.levelgen.synth.SimplexNoise}. Clean-room
 * reimplementation verified against the de-obfuscated 1.21.1 class.
 * <p>
 * A 512-entry permutation table {@code p[]} is shuffled from the RNG (each
 * non-zero octave instance carries its own seeded table, as in vanilla). Both a
 * 2-D evaluation ({@link #getValue(double, double)}, scale factor 70.0) and a
 * 3-D evaluation ({@link #getValue(double, double, double)}, scale factor
 * 32.0) are provided using the classic simplex skewing constants
 * ({@code F2/G2} for 2-D, {@code 1/3} and {@code 1/6} for 3-D), per-corner
 * weight fall-off ({@code weight - x*x - y*y - z*z}) and the final dot-product
 * summation. Exactly like vanilla, the evaluation itself does not consume the
 * per-instance {@code xo/yo/zo} offsets (they are carried for parity only).
 * <p>
 * 确定性的 simplex 噪声，其结构、常量与计算顺序均与 Minecraft 1.21.1 的
 * {@code net.minecraft.world.level.levelgen.synth.SimplexNoise} 逐位一致
 * （对照反混淆后的 1.21.1 类做净室复现）。由 RNG 洗牌得到 512 项置换表
 * {@code p[]}（每个非零八度实例自持各自的建表种子，与原生一致）。提供
 * 2-D 求值（{@link #getValue(double, double)}，缩放因子 70.0）与 3-D 求值
 * （{@link #getValue(double, double, double)}，缩放因子 32.0），使用经典
 * simplex 斜变常量（二维 {@code F2/G2}，三维 {@code 1/3} 与 {@code 1/6}）、
 * 逐角的权重衰减（{@code weight - x*x - y*y - z*z}）以及最终点积求和。与
 * 原生一致，求值本身不使用逐实例的 {@code xo/yo/zo} 偏移（仅为对位保留）。
 */
public final class SimplexNoise {

    /** Vanilla 16-entry gradient table (three coordinates, z unused in 2-D). */
    private static final int[][] GRADIENT = {
            {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
            {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
            {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1},
            // Indices 12..15 are reachable only via %12 in vanilla code and so
            // are never used, but they are retained to mirror the source.
            {1, 1, 0}, {0, -1, 1}, {-1, 1, 0}, {0, -1, -1},
    };

    private static final double SQRT_3 = Math.sqrt(3.0);
    /** 2-D skew constant: {@code 0.5 * (sqrt(3) - 1)}. */
    private static final double F2 = 0.5 * (SQRT_3 - 1.0);
    /** 2-D un-skew constant: {@code (3 - sqrt(3)) / 6}. */
    private static final double G2 = (3.0 - SQRT_3) / 6.0;

    private final int[] p = new int[512];
    /** Per-instance offsets carried for vanilla parity (unused in evaluation). */
    public final double xo;
    /** Per-instance offsets carried for vanilla parity (unused in evaluation). */
    public final double yo;
    /** Per-instance offsets carried for vanilla parity (unused in evaluation). */
    public final double zo;

    private final long seed;

    /**
     * Builds a simplex-noise instance from a {@link XoroRandom} exactly as
     * vanilla does from its modern {@code RandomSource}.
     *
     * @param random source of the per instance perm-table entropy.
     */
    public SimplexNoise(XoroRandom random) {
        this.seed = 0L;
        this.xo = random.nextDouble() * 256.0;
        this.yo = random.nextDouble() * 256.0;
        this.zo = random.nextDouble() * 256.0;
        shuffle(b -> random.nextInt(b));
    }

    /**
     * Builds a simplex-noise instance from a {@link LegacyRandom}, for callers
     * that reproduce a legacy 1.21.1 stream.
     *
     * @param random source of the per instance perm-table entropy.
     */
    public SimplexNoise(LegacyRandom random) {
        this.seed = 0L;
        this.xo = random.nextDouble() * 256.0;
        this.yo = random.nextDouble() * 256.0;
        this.zo = random.nextDouble() * 256.0;
        shuffle(b -> random.nextInt(b));
    }

    private SimplexNoise(XoroRandom random, long seed) {
        this.seed = seed;
        this.xo = random.nextDouble() * 256.0;
        this.yo = random.nextDouble() * 256.0;
        this.zo = random.nextDouble() * 256.0;
        shuffle(b -> random.nextInt(b));
    }

    /**
     * Reproducible factory from a seed via {@link XoroRandom}; supports the
     * {@link #td()}/{@link #fromTd(String)} round-trip.
     *
     * @param seed master seed.
     * @return a fresh deterministic simplex-noise instance.
     */
    public static SimplexNoise fromSeed(long seed) {
        return new SimplexNoise(new XoroRandom(seed), seed);
    }

    /** The seed this instance was built from (0 if built from a raw RNG). */
    public long seed() {
        return seed;
    }

    /** 2-D evaluation at ({@code x}, {@code y}); vanilla scale factor 70.0. */
    public double getValue(double x, double y) {
        double d = (x + y) * F2;
        int i = (int) Math.floor(x + d);
        int j = (int) Math.floor(y + d);
        double g = (double) (i + j) * G2;
        double x0 = (double) i - g;
        double y0 = (double) j - g;
        double x1 = x - x0;
        double y1 = y - y0;
        boolean cond = x1 > y1;
        int i0 = i & 255;
        int j0 = j & 255;
        int o1x = cond ? 1 : 0;
        int o1y = cond ? 0 : 1;
        double x2 = x1 - (double) o1x + G2;
        double y2 = y1 - (double) o1y + G2;
        double x3 = x1 - 1.0 + 2.0 * G2;
        double y3 = y1 - 1.0 + 2.0 * G2;
        int g1 = p(i0 + p(j0)) % 12;
        int g2 = p(i0 + o1x + p(j0 + o1y)) % 12;
        int g3 = p(i0 + 1 + p(j0 + 1)) % 12;
        double c1 = corner(g1, x1, y1, 0.0, 0.5);
        double c2 = corner(g2, x2, y2, 0.0, 0.5);
        double c3 = corner(g3, x3, y3, 0.0, 0.5);
        return 70.0 * (c1 + c2 + c3);
    }

    /** 3-D evaluation; vanilla scale factor 32.0. */
    public double getValue(double x, double y, double z) {
        double f3 = 1.0 / 3.0;
        double g3 = 1.0 / 6.0;
        double d = (x + y + z) * f3;
        int i = (int) Math.floor(x + d);
        int j = (int) Math.floor(y + d);
        int k = (int) Math.floor(z + d);
        double g = (double) (i + j + k) * g3;
        double x1 = x - ((double) i - g);
        double y1 = y - ((double) j - g);
        double z1 = z - ((double) k - g);
        // Corner offsets for the two diagonal simplex corners (vanilla order).
        int o1x, o1y, o1z, o2x, o2y, o2z;
        if (x1 >= y1) {
            if (y1 >= z1) {
                o1x = 1; o1y = 0; o1z = 0;          // x >= y >= z
                o2x = 1; o2y = 1; o2z = 0;
            } else if (x1 >= z1) {
                o1x = 1; o1y = 0; o1z = 0;          // x >= z > y
                o2x = 1; o2y = 0; o2z = 1;
            } else {
                o1x = 0; o1y = 0; o1z = 1;          // z > x >= y
                o2x = 1; o2y = 0; o2z = 1;
            }
        } else if (y1 < z1) {
            o1x = 0; o1y = 0; o1z = 1;              // z > y >= x
            o2x = 0; o2y = 1; o2z = 1;
        } else if (x1 >= z1) {
            o1x = 0; o1y = 1; o1z = 0;              // y >= x >= z
            o2x = 0; o2y = 1; o2z = 1;
        } else {
            o1x = 0; o1y = 1; o1z = 0;              // y >= z > x
            o2x = 1; o2y = 1; o2z = 0;
        }
        double x2 = x1 - (double) o1x + g3;
        double y2 = y1 - (double) o1y + g3;
        double z2 = z1 - (double) o1z + g3;
        double x3 = x1 - (double) o2x + f3;
        double y3 = y1 - (double) o2y + f3;
        double z3 = z1 - (double) o2z + f3;
        double x4 = x1 - 1.0 + 0.5;
        double y4 = y1 - 1.0 + 0.5;
        double z4 = z1 - 1.0 + 0.5;
        int i2 = i & 255;
        int j2 = j & 255;
        int k2 = k & 255;
        int gi1 = p(i2 + p(j2 + p(k2))) % 12;
        int gi2 = p(i2 + o1x + p(j2 + o1y + p(k2 + o1z))) % 12;
        int gi3 = p(i2 + o2x + p(j2 + o2y + p(k2 + o2z))) % 12;
        int gi4 = p(i2 + 1 + p(j2 + 1 + p(k2 + 1))) % 12;
        double c1 = corner(gi1, x1, y1, z1, 0.6);
        double c2 = corner(gi2, x2, y2, z2, 0.6);
        double c3 = corner(gi3, x3, y3, z3, 0.6);
        double c4 = corner(gi4, x4, y4, z4, 0.6);
        return 32.0 * (c1 + c2 + c3 + c4);
    }

    /** Looks up the perm table with wraparound (vanilla {@code p(int)}). */
    private int p(int index) {
        return this.p[index & 255];
    }

    /** Corner contribution: {@code (t^4) * dot(g, x, y, z)} clamped at 0. */
    private static double corner(int gradIndex, double x, double y, double z, double weight) {
        double t = weight - x * x - y * y - z * z;
        if (t < 0.0) {
            return 0.0;
        }
        t *= t;
        return t * t * dot(GRADIENT[gradIndex], x, y, z);
    }

    /** Vanilla three-coordinate dot product. */
    private static double dot(int[] gradient, double x, double y, double z) {
        return (double) gradient[0] * x + (double) gradient[1] * y + (double) gradient[2] * z;
    }

    /** Fisher-Yates perm-table build mirroring the vanilla constructor. */
    private void shuffle(IntUnaryOperator nextInt) {
        for (int i = 0; i < 256; i++) {
            this.p[i] = i;
        }
        for (int i = 0; i < 256; i++) {
            int n = nextInt.applyAsInt(256 - i);
            int t = this.p[i];
            this.p[i] = this.p[i + n];
            this.p[i + n] = t;
        }
    }

    /**
     * Self-describing params string ({@code SimplexNoise=[seed]}), the exact
     * text accepted by {@link #fromTd(String)}. Only instances built via
     * {@link #fromSeed(long)} round-trip meaningfully.
     */
    public String td() {
        return "SimplexNoise=[" + seed + "]";
    }

    /** Parses {@code SimplexNoise=[seed]} back into a deterministic instance. */
    public static SimplexNoise fromTd(String source) {
        return fromSeed(parseFirstLong(source, "SimplexNoise"));
    }

    /** Minimal {@code Name=[...]} parser returning the sole leading long. */
    private static long parseFirstLong(String source, String name) {
        String body = name + "=[";
        if (source == null || !source.startsWith(body) || !source.endsWith("]")) {
            throw new IllegalArgumentException("expected " + body + "...]: " + source);
        }
        String inner = source.substring(body.length(), source.length() - 1).trim();
        if (inner.isEmpty()) {
            throw new IllegalArgumentException("empty params for " + name);
        }
        int comma = inner.indexOf(',');
        String first = (comma < 0 ? inner : inner.substring(0, comma)).trim();
        try {
            return Long.parseLong(first);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid " + name + " seed: " + first, e);
        }
    }
}