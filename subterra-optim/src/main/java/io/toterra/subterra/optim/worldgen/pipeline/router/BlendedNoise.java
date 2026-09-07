package io.toterra.subterra.optim.worldgen.pipeline.router;

import io.toterra.subterra.optim.worldgen.pipeline.density.Density;
import io.toterra.subterra.optim.worldgen.pipeline.noise.perlin.ImprovedNoise;
import io.toterra.subterra.optim.worldgen.pipeline.noise.perlin.PerlinNoise;

/**
 * The octave-blend computer mirroring MC 1.21.1's
 * {@code net.minecraft.world.level.levelgen.synth.BlendedNoise} (verified via
 * javap on the de-obfuscated 1.21.1 class — this type IS alive in 1.21.1, but it
 * is NOT part of the overworld {@code NoiseRouter} recipe, so it is wired here as
 * a documented legacy seam). It combines three octave-Perlin families —
 * {@code mainNoise} (8 octaves), {@code minLimitNoise} and {@code maxLimitNoise}
 * (16 octaves each) — into a single {@link Density}.
 * <p>
 * The {@code compute(x,y,z)} body reproduces 1.21.1's operation order exactly:
 * coordinates are scaled by {@code xzMultiplier}/{@code yMultiplier} =
 * {@code 684.412 * xzScale}/{@code yScale}; the main family is sampled at
 * per-octave frequencies divided by {@code xzFactor}/{@code yFactor} with
 * {@code yScale = yMultiplier*smearScaleMultiplier/yFactor}, summed, mapped by
 * {@code (sum/10 + 1) / 2} and clamped into {@code [0,1]} into the two guards
 * {@code g0 = factor>=1} and {@code g1 = factor>0}. The min/max families are then
 * sampled per octave (each side active only when the matching guard is false) at
 * the unscaled frequencies, and the result is
 * {@code clampedLerp(minSum/512, maxSum/512, factor) / 128}. Each octave sample
 * uses the smoothed 5-arg {@link SmoothedNoise#noise} overload.
 * <p>
 * 镜像 MC 1.21.1 {@code synth.BlendedNoise} 的八度混合计算机（经 javap 对照反混淆
 * 后的 1.21.1 类验证——本类型在 1.21.1 中确实存在，但<em>不在</em>主世界
 * {@code NoiseRouter} 配方中，故此处作为文档化遗留接缝接线）。它把三个八度
 * Perlin 族——{@code mainNoise}（8 个八度）、{@code minLimitNoise} 与
 * {@code maxLimitNoise}（各 16 个八度）——组合为单个 {@link Density}。
 * {@code compute} 逐操作复现 1.21.1 顺序：坐标按
 * {@code xzMultiplier}/{@code yMultiplier}= {@code 684.412*xzScale}/{@code yScale}
 * 缩放；主族在除以 {@code xzFactor}/{@code yFactor} 的逐八度频率下采样、
 * 取 {@code (sum/10+1)/2} 并截断到 {@code [0,1]}，得到两个守卫
 * {@code g0 = factor>=1}、{@code g1 = factor>0}；min/max 族在未缩放频率下逐八度
 * 采样（仅在对应守卫为 false 时活跃），输出
 * {@code clampedLerp(minSum/512, maxSum/512, factor)/128}。每个八度样本用
 * {@link SmoothedNoise#noise} 平滑五参重载。
 */
public final class BlendedNoise implements Density {

    /** Vanilla scale multiplier (block units → noise units). */
    private static final double SCALE = 684.412d;

    private final PerlinNoise minLimitNoise;
    private final PerlinNoise maxLimitNoise;
    private final PerlinNoise mainNoise;

    private final double xzMultiplier;
    private final double yMultiplier;
    private final double xzScale;
    private final double yScale;
    private final double xzFactor;
    private final double yFactor;
    private final double smearScaleMultiplier;

    private final long seed;

    /** 16 all-ones amplitudes for the min/max families (octaves -15..0). */
    private static final double[] RANGE_16 = ones(16);
    /** 8 all-ones amplitudes for the main family (octaves -7..0). */
    private static final double[] RANGE_8 = ones(8);

    /**
     * @param seed                 master seed for all three octave families.
     * @param xzScale              horizontal scale (vanilla overworld default 1.0).
     * @param yScale               vertical scale (vanilla overworld default 1.0).
     * @param xzFactor             horizontal main frequency divisor (8.55515...).
     * @param yFactor              vertical main frequency divisor (4.277575...).
     * @param smearScaleMultiplier vertical smear factor.
     */
    public BlendedNoise(long seed, double xzScale, double yScale, double xzFactor, double yFactor,
                        double smearScaleMultiplier) {
        this.seed = seed;
        this.xzScale = xzScale;
        this.yScale = yScale;
        this.xzFactor = xzFactor;
        this.yFactor = yFactor;
        this.smearScaleMultiplier = smearScaleMultiplier;
        this.xzMultiplier = SCALE * xzScale;
        this.yMultiplier = SCALE * yScale;
        this.minLimitNoise = PerlinNoise.create(seed, -15, RANGE_16);
        this.maxLimitNoise = PerlinNoise.create(seed + 0x9E37_79B9_7F4A_7C15L, -15, RANGE_16);
        this.mainNoise = PerlinNoise.create(seed + 0x53C5_CAFD_2F63_31A4L, -7, RANGE_8);
    }

    /** Constructor with the historical overworld factor defaults. */
    public BlendedNoise(long seed) {
        this(seed, 1.0, 1.0, 8.555150000000001D, 4.277575000000001D, 2.0);
    }

    /** The master seed this instance was built from. */
    public long seed() {
        return seed;
    }

    @Override
    public double eval(double x, double y, double z) {
        double xPos = x * this.xzMultiplier;
        double yPos = y * this.yMultiplier;
        double zPos = z * this.xzMultiplier;

        double xzScaled = xPos / this.xzFactor;
        double yScaled = yPos / this.yFactor;
        double zScaled = zPos / this.xzFactor;
        double ySmearScale = this.yMultiplier * this.smearScaleMultiplier;
        double ySmearScaled = ySmearScale / this.yFactor;

        // main family: 8 octaves.
        double mainSum = 0.0;
        double factor = 1.0;
        for (int oct = 0; oct < 8; oct++) {
            ImprovedNoise n = this.mainNoise.octaveAt(oct);
            if (n != null) {
                double xv = xzScaled * factor;
                double yv = yScaled * factor;
                double zv = zScaled * factor;
                double sv = ySmearScaled * factor;
                double yv2 = yScaled * factor;
                mainSum += SmoothedNoise.noise(
                        n,
                        PerlinNoise.wrap(xv),
                        PerlinNoise.wrap(yv),
                        PerlinNoise.wrap(zv),
                        PerlinNoise.wrap(sv),
                        PerlinNoise.wrap(yv2)) / factor;
            }
            factor /= 2.0;
        }

        double mainFrac = (mainSum / 10.0 + 1.0) / 2.0;
        boolean g0 = mainFrac >= 1.0;
        boolean g1 = mainFrac > 0.0;

        // minLimit / maxLimit families: 16 octaves.
        double minSum = 0.0;
        double maxSum = 0.0;
        factor = 1.0;
        for (int oct = 0; oct < 16; oct++) {
            double xv = PerlinNoise.wrap(xPos * factor);
            double yv = PerlinNoise.wrap(yPos * factor);
            double zv = PerlinNoise.wrap(zPos * factor);
            double sv = PerlinNoise.wrap(ySmearScale * factor);
            double yv2 = PerlinNoise.wrap(yPos * factor);
            if (!g0) {
                ImprovedNoise n = this.minLimitNoise.octaveAt(oct);
                if (n != null) {
                    minSum += SmoothedNoise.noise(n, xv, yv, zv, sv, yv2) / factor;
                }
            }
            if (!g1) {
                ImprovedNoise n = this.maxLimitNoise.octaveAt(oct);
                if (n != null) {
                    maxSum += SmoothedNoise.noise(n, xv, yv, zv, sv, yv2) / factor;
                }
            }
            factor /= 2.0;
        }

        return clampedLerp(minSum / 512.0, maxSum / 512.0, mainFrac) / 128.0;
    }

    private static double clampedLerp(double start, double end, double delta) {
        if (delta < 0.0) {
            return start;
        }
        if (delta > 1.0) {
            return end;
        }
        return start + delta * (end - start);
    }

    private static double[] ones(int n) {
        double[] a = new double[n];
        java.util.Arrays.fill(a, 1.0);
        return a;
    }
}