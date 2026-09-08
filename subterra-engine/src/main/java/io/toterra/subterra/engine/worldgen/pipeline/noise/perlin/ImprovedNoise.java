package io.toterra.subterra.engine.worldgen.pipeline.noise.perlin;

import java.util.function.DoubleSupplier;
import java.util.function.IntUnaryOperator;

import io.toterra.subterra.engine.worldgen.pipeline.noise.LegacyRandom;
import io.toterra.subterra.engine.worldgen.pipeline.noise.XoroRandom;

/**
 * The 3-D Perlin gradient-lattice cell, bit-identical in structure, constants
 * and operation order to Minecraft 1.21.1's
 * {@code net.minecraft.world.level.levelgen.synth.ImprovedNoise}. Clean-room
 * reimplementation verified against the de-obfuscated 1.21.1 class.
 * <p>
 * A 256-entry permutation {@code p[]} is Fisher-Yates shuffled from the RNG
 * (each lattice carries its own seeded table). Every sample offsets the input
 * by the per-instance {@code xo/yo/zo}, floors onto the integer lattice, then
 * trilinearly interpolates the 8 cell corners with the 5th-order smoothstep
 * fade — x-blend inner, then y, then z — exactly as vanilla does via
 * {@code Mth.lerp3}. Each corner's value is the dot of the permutation-derived
 * gradient ({@code & 15} into the shared 16-entry gradient table) with the
 * corner offset. Evaluation is allocation-free and constant-time.
 * <p>
 * 三维 Perlin 梯度晶格单元，其结构、常量与计算顺序均与 Minecraft 1.21.1 的
 * {@code net.minecraft.world.level.levelgen.synth.ImprovedNoise} 逐位一致
 * （对照反混淆后的 1.21.1 类做净室复现）。着色 256 项置换表 {@code p[]}
 * （每个晶格自持各自的建表种子）。每次采样先将输入叠加逐实例偏移
 * {@code xo/yo/zo}，向下取整到整数晶格，再用五阶 smoothstep 渐变对 8 个
 * 单元角做三线性插值——先 x 后 y 再 z——与原生 {@code Mth.lerp3} 完全一致。
 * 每个角的值是置换派生的梯度（低位 {@code & 15} 索引共享的 16 项梯度表）
 * 与该角偏移的点积。求值无分配、常数时间。
 */
public final class ImprovedNoise {

    /** Vanilla 16-entry gradient table (indices 12..15 all reachable via {@code & 15}). */
    private static final int[][] GRADIENT = {
            {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
            {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
            {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1},
            {1, 1, 0}, {0, -1, 1}, {-1, 1, 0}, {0, -1, -1},
    };

    private final int[] p;

    /** Per-instance lattice offset (vanilla parity). */
    public final double xo;
    /** Per-instance lattice offset (vanilla parity). */
    public final double yo;
    /** Per-instance lattice offset (vanilla parity). */
    public final double zo;

    /**
     * Builds a lattice from a {@link LegacyRandom} exactly as vanilla does:
     * three seed-doubles become the offset, then a Fisher-Yates shuffle of the
     * 256-entry identity table with {@code nextInt(256 - i)}.
     *
     * @param random source of the per-instance permutation and offset.
     */
    public ImprovedNoise(LegacyRandom random) {
        this(build(random::nextDouble, random::nextInt));
    }

    /**
     * Builds a lattice from a {@link XoroRandom} (p.1.8.29B), consuming the
     * shared stream with the identical order as the legacy path — three
     * {@code nextDouble} for the offset, then 256 Fisher-Yates {@code nextInt}
     * draws — matching vanilla's {@code ImprovedNoise(RandomSource)} exactly for
     * the {@code XoroshiroRandomSource} stream used by {@code BlendedNoise}.
     *
     * @param random source of the per-instance permutation and offset.
     */
    public ImprovedNoise(XoroRandom random) {
        this(build(random::nextDouble, random::nextInt));
    }

    private record Table(double xo, double yo, double zo, int[] p) {
    }

    private ImprovedNoise(Table table) {
        this.xo = table.xo;
        this.yo = table.yo;
        this.zo = table.zo;
        this.p = table.p;
    }

    /** Vanilla lattice build: three seed-doubles, then a 256-entry Fisher-Yates. */
    private static Table build(DoubleSupplier nextDouble, IntUnaryOperator nextInt) {
        double xo = nextDouble.getAsDouble() * 256.0;
        double yo = nextDouble.getAsDouble() * 256.0;
        double zo = nextDouble.getAsDouble() * 256.0;
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        for (int i = 0; i < 256; i++) {
            int j = i + nextInt.applyAsInt(256 - i);
            int t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        return new Table(xo, yo, zo, p);
    }

    /**
     * Evaluates the lattice at ({@code x}, {@code y}, {@code z}): adds the
     * offset, floors onto the unit lattice and trilinearly interpolates the 8
     * cell corners with the smoothstep fade.
     *
     * @return the interpolated cell value (roughly within {@code (-sqrt(3), sqrt(3))}).
     */
    public double noise(double x, double y, double z) {
        double xp = x + this.xo;
        double yp = y + this.yo;
        double zp = z + this.zo;
        int xi = floor(xp);
        int yi = floor(yp);
        int zi = floor(zp);
        double dx = xp - (double) xi;
        double dy = yp - (double) yi;
        double dz = zp - (double) zi;
        return sampleAndLerp(xi, yi, zi, dx, dy, dz, dy);
    }

    /** Permutation lookup with 256-wrap (vanilla {@code p(int)}). */
    public int perm(int index) {
        return this.p[index & 255];
    }

    /**
     * The permutation-derived gradient dot at a corner: {@code GRADIENT[hash & 15]
     * · (dx, dy, dz)}. Public so probes can hand-verify lattice values.
     */
    public static double gradientDot(int hash, double dx, double dy, double dz) {
        int[] g = GRADIENT[hash & 15];
        return (double) g[0] * dx + (double) g[1] * dy + (double) g[2] * dz;
    }

    /** Trilinear corner interpolation (vanilla {@code sampleAndLerp}). */
    private double sampleAndLerp(int ix, int iy, int iz, double dx, double dy, double dz, double dy2) {
        int i = perm(ix);
        int i1 = perm(ix + 1);
        int k = perm(i + iy);
        int k1 = perm(i + iy + 1);
        int l = perm(i1 + iy);
        int l1 = perm(i1 + iy + 1);

        double d0 = gradientDot(perm(k + iz), dx, dy, dz);
        double d1 = gradientDot(perm(l + iz), dx - 1.0, dy, dz);
        double d2 = gradientDot(perm(k1 + iz), dx, dy - 1.0, dz);
        double d3 = gradientDot(perm(l1 + iz), dx - 1.0, dy - 1.0, dz);
        double d4 = gradientDot(perm(k + iz + 1), dx, dy, dz - 1.0);
        double d5 = gradientDot(perm(l + iz + 1), dx - 1.0, dy, dz - 1.0);
        double d6 = gradientDot(perm(k1 + iz + 1), dx, dy - 1.0, dz - 1.0);
        double d7 = gradientDot(perm(l1 + iz + 1), dx - 1.0, dy - 1.0, dz - 1.0);

        double sx = smoothstep(dx);
        double sy = smoothstep(dy2);
        double sz = smoothstep(dz);
        return lerp3(sx, sy, sz, d0, d1, d2, d3, d4, d5, d6, d7);
    }

    /** Vanilla {@code Mth.smoothstep}: {@code t^3 (t (6t - 15) + 10)}. */
    private static double smoothstep(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    /** Vanilla {@code Mth.lerp3}: x-pairs first, then y, then z. */
    private static double lerp3(double a, double b, double c, double d0, double d1, double d2, double d3,
                                double d4, double d5, double d6, double d7) {
        return lerp(c, lerp2(a, b, d0, d1, d2, d3), lerp2(a, b, d4, d5, d6, d7));
    }

    /** Vanilla {@code Mth.lerp2}: {@code lerp(b, lerp(a, x, y), lerp(a, z, w))}. */
    private static double lerp2(double a, double b, double d0, double d1, double d2, double d3) {
        return lerp(b, lerp(a, d0, d1), lerp(a, d2, d3));
    }

    /** Vanilla {@code Mth.lerp}: {@code start + delta * (end - start)}. */
    private static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    /** Vanilla {@code Mth.floor(double)}. */
    private static int floor(double value) {
        int i = (int) value;
        return value < (double) i ? i - 1 : i;
    }
}