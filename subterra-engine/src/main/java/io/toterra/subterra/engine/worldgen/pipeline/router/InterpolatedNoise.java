package io.toterra.subterra.engine.worldgen.pipeline.router;

import io.toterra.subterra.engine.worldgen.pipeline.density.Density;
import io.toterra.subterra.engine.worldgen.pipeline.noise.perlin.ImprovedNoise;
import io.toterra.subterra.engine.worldgen.pipeline.noise.perlin.PerlinNoise;

/**
 * The final-density octave computer mirroring the historical (pre-1.21)
 * {@code net.minecraft.world.level.levelgen.synth.InterpolatedNoise}. IMPORTANT
 * FINDING: this class is <em>removed / dead</em> in MC 1.21.1 — it does not exist
 * under {@code net/minecraft/world/level/levelgen/synth/} in the 1.21.1 jar. Its
 * role (composing the old {@code final_density} from the {@code xy}/{@code zy}
 * and {@code xz} octave families) is subsumed by the machine in
 * {@code NoiseRouterData}/{@code DensityFunctions} (shifted-noise, spline, slide,
 * range-choice). This type is therefore provided as a documented legacy seam only,
 * kept deterministic and allocation-free so consumers can sample a
 * {@code final_density}-shaped field for parity/compatibility work.
 * <p>
 * Structurally it matches its surviving twin {@link BlendedNoise} (both share MC's
 * octave-blend skeleton): a {@code mainNoise} family (8 octaves) plus
 * {@code minLimitNoise}/{@code maxLimitNoise} (16 octaves), each octave sampled
 * through the smoothed 5-arg {@link SmoothedNoise#noise}; the result is
 * {@code clampedLerp(minSum/512, maxSum/512, mainFrac) / 128 * 2 + densityOffset},
 * where {@code densityOffset} defaults to the overworld {@code 1.5}. The exact
 * historical offsets/blending of the pre-1.21 class are approximated here; do not
 * rely on bit-exact parity for a removed class.
 * <p>
 * 镜像历史上的（1.21 之前）最终密度 octave 计算机
 * {@code synth.InterpolatedNoise}。重要发现：此类在 MC 1.21.1 中<em>已被移除 /
 * 死亡</em>——1.21.1 jar 中不存在于 {@code synth/} 之下。其作用（由
 * {@code xy}/{@code zy} 与 {@code xz} octave 族组装旧 {@code final_density}）
 * 已被 {@code NoiseRouterData}/{@code DensityFunctions}（shifted-noise、spline、
 * slide、range-choice）的机器取代。故本类型仅作为文档化遗留接缝提供，保持确定性
 * 与无分配，以便消费者为对位/兼容工作采样一个 {@code final_density} 形状的场。
 * 结构上与其幸存孪生 {@link BlendedNoise} 一致（共享 MC 的 octave 混合骨架）：
 * {@code mainNoise} 族（8 octave）+ {@code minLimitNoise}/{@code maxLimitNoise}
 * （16 octave），每个 octave 经平滑五参 {@link SmoothedNoise#noise} 采样；结果为
 * {@code clampedLerp(minSum/512, maxSum/512, mainFrac)/128*2 + densityOffset}，
 * {@code densityOffset} 默认主世界 {@code 1.5}。1.21 之前类的精确历史偏移/混合在此
 * 仅作近似；对已移除的类请勿依赖逐位对位。
 */
public final class InterpolatedNoise implements Density {

    /** Vanilla scale multiplier (block units → noise units). */
    private static final double SCALE = 684.412d;

    private final PerlinNoise minLimitNoise;
    private final PerlinNoise maxLimitNoise;
    private final PerlinNoise mainNoise;

    private final double xzMultiplier;
    private final double yMultiplier;
    private final double xzFactor;
    private final double yFactor;
    private final double smearScaleMultiplier;
    private final double densityOffset;

    private final long seed;

    private static final double[] RANGE_16 = ones(16);
    private static final double[] RANGE_8 = ones(8);

    /**
     * @param seed                 master seed for all three octave families.
     * @param xzFactor             horizontal main frequency divisor.
     * @param yFactor              vertical main frequency divisor.
     * @param smearScaleMultiplier vertical smear factor.
     * @param densityOffset        the legacy final-density offset term.
     */
    public InterpolatedNoise(long seed, double xzFactor, double yFactor, double smearScaleMultiplier,
                             double densityOffset) {
        this.seed = seed;
        this.xzFactor = xzFactor;
        this.yFactor = yFactor;
        this.smearScaleMultiplier = smearScaleMultiplier;
        this.densityOffset = densityOffset;
        this.xzMultiplier = SCALE;
        this.yMultiplier = SCALE;
        this.minLimitNoise = PerlinNoise.create(seed, -15, RANGE_16);
        this.maxLimitNoise = PerlinNoise.create(seed + 0x9E37_79B9_7F4A_7C15L, -15, RANGE_16);
        this.mainNoise = PerlinNoise.create(seed + 0x53C5_CAFD_2F63_31A4L, -7, RANGE_8);
    }

    /** Historical overworld defaults. */
    public InterpolatedNoise(long seed) {
        this(seed, 8.555150000000001D, 4.277575000000001D, 2.0, 1.5);
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

        double mainSum = sampleFamily(this.mainNoise, 8,
                xzScaled, yScaled, zScaled, ySmearScaled);

        double mainFrac = (mainSum / 10.0 + 1.0) / 2.0;
        boolean g0 = mainFrac >= 1.0;
        boolean g1 = mainFrac > 0.0;

        double minSum = sampleLimits(this.minLimitNoise, g0, xPos, yPos, zPos, ySmearScale);
        double maxSum = sampleLimits(this.maxLimitNoise, g1, xPos, yPos, zPos, ySmearScale);

        double blended = clampedLerp(minSum / 512.0, maxSum / 512.0, mainFrac);
        return blended / 128.0 * 2.0 + this.densityOffset;
    }

    private double sampleFamily(PerlinNoise pn, int octaves,
                                double xzScaled, double yScaled, double zScaled, double ySmearScaled) {
        double sum = 0.0;
        double factor = 1.0;
        for (int oct = 0; oct < octaves; oct++) {
            ImprovedNoise n = pn.octaveAt(oct);
            if (n != null) {
                double xv = xzScaled * factor;
                double yv = yScaled * factor;
                double zv = zScaled * factor;
                double sv = ySmearScaled * factor;
                double yv2 = yScaled * factor;
                sum += SmoothedNoise.noise(n,
                        PerlinNoise.wrap(xv), PerlinNoise.wrap(yv), PerlinNoise.wrap(zv),
                        PerlinNoise.wrap(sv), PerlinNoise.wrap(yv2)) / factor;
            }
            factor /= 2.0;
        }
        return sum;
    }

    private double sampleLimits(PerlinNoise pn, boolean active,
                                double xPos, double yPos, double zPos, double ySmearScale) {
        if (active) {
            return 0.0;
        }
        double sum = 0.0;
        double factor = 1.0;
        for (int oct = 0; oct < 16; oct++) {
            ImprovedNoise n = pn.octaveAt(oct);
            if (n != null) {
                sum += SmoothedNoise.noise(n,
                        PerlinNoise.wrap(xPos * factor), PerlinNoise.wrap(yPos * factor),
                        PerlinNoise.wrap(zPos * factor), PerlinNoise.wrap(ySmearScale * factor),
                        PerlinNoise.wrap(yPos * factor)) / factor;
            }
            factor /= 2.0;
        }
        return sum;
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