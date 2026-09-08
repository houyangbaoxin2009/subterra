package io.toterra.subterra.optim.worldgen.pipeline.router;

import io.toterra.subterra.optim.worldgen.pipeline.density.Density;
import io.toterra.subterra.optim.worldgen.pipeline.noise.XoroRandom;
import io.toterra.subterra.optim.worldgen.pipeline.noise.perlin.ImprovedNoise;
import io.toterra.subterra.optim.worldgen.pipeline.noise.perlin.PerlinNoise;

/**
 * The octave-blend computer mirroring MC 1.21.1's
 * {@code net.minecraft.world.level.levelgen.synth.BlendedNoise} — the
 * {@code base_3d_noise} {@code old_blended_noise} density leaf (p.1.8.29B).
 * Clean-room transcription verified via javap on the de-obfuscated 1.21.1 class
 * (constructor, {@code createUnseeded}, {@code withNewRandom}, {@code compute}
 * and {@code MaxBrokenValue} all byte-checked).
 * <p>
 * Three octave-Perlin families are built from ONE shared
 * {@link XoroRandom} stream in vanilla order — {@code minLimitNoise}
 * (octaves -15..0), then {@code maxLimitNoise} (-15..0), then
 * {@code mainNoise} (-7..0) — each via the single-stream descending
 * construction ({@link PerlinNoise#createFromStream}). Vanilla seeds that stream
 * from {@code XoroshiroRandomSource(0)} at template-build time and re-seeds it
 * per world through the wiring visitor's
 * {@code RandomState.random.fromHashOf("terrain")}; {@link #terrainStream(long)}
 * reproduces that per-world derived 128-bit state (MD5("terrain") XOR the
 * {@code PositionalRand} factory base), so the leaf is deterministic and
 * world-seed-sensitive exactly as in 1.21.1.
 * <p>
 * {@code compute} reproduces 1.21.1's operation order exactly: coordinates are
 * scaled by {@code xzMultiplier}/{@code yMultiplier} = {@code 684.412 * xzScale}/
 * {@code yScale}; the main family is sampled at per-octave frequencies divided
 * by {@code xzFactor}/{@code yFactor} with {@code ySmearScaled = yMultiplier *
 * smearScaleMultiplier / yFactor}, summed, mapped by
 * {@code (sum/10 + 1) / 2} and clamped into {@code [0,1]} into the two guards
 * {@code g0 = factor >= 1} and {@code g1 = factor > 0}; the min/max families are
 * sampled per octave (each side only when the matching guard is false) at the
 * unscaled frequencies, and the result is
 * {@code clampedLerp(minSum/512, maxSum/512, factor) / 128}. Every octave sample
 * uses the smoothed 5-arg {@link SmoothedNoise#noise} overload, whose 5th
 * argument is the raw (un-wrapped) y-frequency — transcribed exactly from the
 * bytecode.
 * <p>
 * 八度混合计算机，镜像 MC 1.21.1 {@code synth.BlendedNoise}——即
 * {@code base_3d_noise} 的 {@code old_blended_noise} 密度叶（p.1.8.29B）。经
 * javap 对照反混淆后的 1.21.1 类逐字节核查（构造器、{@code createUnseeded}、
 * {@code withNewRandom}、{@code compute} 与 {@code maxBrokenValue}）。
 * 三个八度 Perlin 族由<em>同一条</em> {@link XoroRandom} 流按原生顺序建表——
 * {@code minLimitNoise}（八度 -15..0）、随后 {@code maxLimitNoise}（-15..0）、
 * 再 {@code mainNoise}（-7..0）——每一族都走单流降序构造
 * （{@link PerlinNoise#createFromStream}）。原生在模板构建时以
 * {@code XoroshiroRandomSource(0)} 建流，并在接线阶段经
 * {@code RandomState.random.fromHashOf("terrain")} 逐世界重播种；
 * {@link #terrainStream(long)} 复现该逐世界派生的 128 位状态（MD5("terrain")
 * 异或 {@code PositionalRand} 工厂基），故叶子确定且如 1.21.1 般随世界种子变化。
 * {@code compute} 逐操作复现 1.21.1 顺序。
 */
public final class BlendedNoise implements Density {

    /** Vanilla scale multiplier (block units to noise units). */
    private static final double SCALE = 684.412d;

    /** The per-world terrain-stream label ({@code RandomState.random.fromHashOf("terrain")}). */
    public static final String TERRAIN_LABEL = "terrain";

    /** Vanilla overworld {@code base_3d_noise} registration (density_function/overworld/base_3d_noise.json). */
    public static final double OVERWORLD_XZ_SCALE = 0.25;
    /** Vanilla overworld {@code base_3d_noise} y-scale. */
    public static final double OVERWORLD_Y_SCALE = 0.125;
    /** Vanilla overworld {@code base_3d_noise} xz main-frequency divisor. */
    public static final double OVERWORLD_XZ_FACTOR = 80.0;
    /** Vanilla overworld {@code base_3d_noise} y main-frequency divisor. */
    public static final double OVERWORLD_Y_FACTOR = 160.0;
    /** Vanilla overworld {@code base_3d_noise} smear multiplier. */
    public static final double OVERWORLD_SMEAR = 8.0;

    /** Vanilla nether {@code base_3d_noise} xz-scale (density_function/nether/base_3d_noise.json). */
    public static final double NETHER_XZ_SCALE = 0.25;
    /** Vanilla nether {@code base_3d_noise} y-scale. */
    public static final double NETHER_Y_SCALE = 0.375;
    /** Vanilla nether {@code base_3d_noise} xz main-frequency divisor. */
    public static final double NETHER_XZ_FACTOR = 80.0;
    /** Vanilla nether {@code base_3d_noise} y main-frequency divisor (60, not 160). */
    public static final double NETHER_Y_FACTOR = 60.0;
    /** Vanilla nether {@code base_3d_noise} smear multiplier. */
    public static final double NETHER_SMEAR = 8.0;

    /** Vanilla end {@code base_3d_noise} xz-scale (density_function/end/base_3d_noise.json). */
    public static final double END_XZ_SCALE = 0.25;
    /** Vanilla end {@code base_3d_noise} y-scale. */
    public static final double END_Y_SCALE = 0.25;
    /** Vanilla end {@code base_3d_noise} xz main-frequency divisor. */
    public static final double END_XZ_FACTOR = 80.0;
    /** Vanilla end {@code base_3d_noise} y main-frequency divisor. */
    public static final double END_Y_FACTOR = 160.0;
    /** Vanilla end {@code base_3d_noise} smear multiplier (4, not 8). */
    public static final double END_SMEAR = 4.0;

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

    private final long seedLo;
    private final long seedHi;

    private final double maxValue;

    /** 16 all-ones amplitudes for the min/max families (octaves -15..0). */
    private static final double[] RANGE_16 = ones(16);
    /** 8 all-ones amplitudes for the main family (octaves -7..0). */
    private static final double[] RANGE_8 = ones(8);

    /**
     * The per-world Xoroshiro stream for the {@code base_3d_noise} leaf
     * (p.1.8.29B): {@code RandomState.random.fromHashOf("terrain")} — the 128-bit
     * state {@code MD5("terrain") XOR factoryBase}, used as-is exactly as vanilla
     * {@code XoroshiroPositionalRandomFactory.fromHashOf} hands it to
     * {@code XoroshiroRandomSource(long, long)}.
     *
     * @param worldSeed the master world seed.
     */
    public static XoroRandom terrainStream(long worldSeed) {
        PositionalRand terrain = PositionalRand.ofMaster(worldSeed).fromHashOf(TERRAIN_LABEL);
        return new XoroRandom(terrain.seedLo(), terrain.seedHi());
    }

    /**
     * Vanilla overworld {@code base_3d_noise} for {@code worldSeed}.
     *
     * @param worldSeed the master world seed.
     */
    public static BlendedNoise overworld(long worldSeed) {
        return new BlendedNoise(terrainStream(worldSeed),
                OVERWORLD_XZ_SCALE, OVERWORLD_Y_SCALE, OVERWORLD_XZ_FACTOR, OVERWORLD_Y_FACTOR, OVERWORLD_SMEAR);
    }

    /** Vanilla nether {@code base_3d_noise} for {@code worldSeed}. */
    public static BlendedNoise nether(long worldSeed) {
        return new BlendedNoise(terrainStream(worldSeed),
                NETHER_XZ_SCALE, NETHER_Y_SCALE, NETHER_XZ_FACTOR, NETHER_Y_FACTOR, NETHER_SMEAR);
    }

    /** Vanilla end {@code base_3d_noise} for {@code worldSeed}. */
    public static BlendedNoise end(long worldSeed) {
        return new BlendedNoise(terrainStream(worldSeed),
                END_XZ_SCALE, END_Y_SCALE, END_XZ_FACTOR, END_Y_FACTOR, END_SMEAR);
    }

    /**
     * @param stream               the shared Xoroshiro stream (per-world terrain).
     * @param xzScale              horizontal scale (vanilla overworld default 0.25).
     * @param yScale               vertical scale (vanilla overworld default 0.125).
     * @param xzFactor             horizontal main frequency divisor (overworld 80).
     * @param yFactor              vertical main frequency divisor (overworld 160).
     * @param smearScaleMultiplier vertical smear factor (overworld 8).
     */
    public BlendedNoise(XoroRandom stream, double xzScale, double yScale, double xzFactor, double yFactor,
                        double smearScaleMultiplier) {
        this.seedLo = stream.seedLo();
        this.seedHi = stream.seedHi();
        this.xzScale = xzScale;
        this.yScale = yScale;
        this.xzFactor = xzFactor;
        this.yFactor = yFactor;
        this.smearScaleMultiplier = smearScaleMultiplier;
        this.xzMultiplier = SCALE * xzScale;
        this.yMultiplier = SCALE * yScale;
        // Vanilla family build order over ONE stream: min (16), then max (16), then main (8).
        this.minLimitNoise = PerlinNoise.createFromStream(stream, -15, RANGE_16);
        this.maxLimitNoise = PerlinNoise.createFromStream(stream, -15, RANGE_16);
        this.mainNoise = PerlinNoise.createFromStream(stream, -7, RANGE_8);
        // Vanilla private ctor: maxValue = minLimitNoise.maxBrokenValue(yMultiplier).
        this.maxValue = this.minLimitNoise.maxBrokenValue(this.yMultiplier);
    }

    /** The low 64-bit state word of the terrain stream (for td self-description). */
    public long seedLo() {
        return this.seedLo;
    }

    /** The high 64-bit state word of the terrain stream (for td self-description). */
    public long seedHi() {
        return this.seedHi;
    }

    /** Vanilla {@code maxValue} (worst-case blended bound; min = -maxValue). */
    public double maxValue() {
        return this.maxValue;
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

        // main family: 8 octaves. The smoothed 5-arg overload takes
        // (x, y, z, yScale, yMax); the 5th argument is the raw, un-wrapped y frequency.
        double mainSum = 0.0;
        double factor = 1.0;
        for (int oct = 0; oct < 8; oct++) {
            ImprovedNoise n = this.mainNoise.getOctaveNoise(oct);
            if (n != null) {
                double xv = xzScaled * factor;
                double yv = yScaled * factor;
                double zv = zScaled * factor;
                double sv = ySmearScaled * factor;
                mainSum += SmoothedNoise.noise(
                        n,
                        PerlinNoise.wrap(xv),
                        PerlinNoise.wrap(yv),
                        PerlinNoise.wrap(zv),
                        PerlinNoise.wrap(sv),
                        yScaled * factor) / factor;
            }
            factor /= 2.0;
        }

        double mainFrac = (mainSum / 10.0 + 1.0) / 2.0;
        boolean g0 = mainFrac >= 1.0;
        boolean g1 = mainFrac > 0.0;

        // minLimit / maxLimit families: 16 octaves at the unscaled frequencies.
        double minSum = 0.0;
        double maxSum = 0.0;
        factor = 1.0;
        for (int oct = 0; oct < 16; oct++) {
            double xv = PerlinNoise.wrap(xPos * factor);
            double yv = PerlinNoise.wrap(yPos * factor);
            double zv = PerlinNoise.wrap(zPos * factor);
            double sv = PerlinNoise.wrap(ySmearScale * factor);
            if (!g0) {
                ImprovedNoise n = this.minLimitNoise.getOctaveNoise(oct);
                if (n != null) {
                    minSum += SmoothedNoise.noise(n, xv, yv, zv, sv, yPos * factor) / factor;
                }
            }
            if (!g1) {
                ImprovedNoise n = this.maxLimitNoise.getOctaveNoise(oct);
                if (n != null) {
                    maxSum += SmoothedNoise.noise(n, xv, yv, zv, sv, yPos * factor) / factor;
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

    /** Self-describing params string: {@code "[ seedLo = .., seedHi = .., ... ]"}. */
    public String td() {
        return "[ seedLo = " + this.seedLo + ", seedHi = " + this.seedHi
                + ", xzScale = " + this.xzScale + ", yScale = " + this.yScale
                + ", xzFactor = " + this.xzFactor + ", yFactor = " + this.yFactor
                + ", smear = " + this.smearScaleMultiplier + " ]";
    }

    /** Parses a {@link #td()} snippet back into an equal instance. */
    public static BlendedNoise fromTd(String source) {
        if (source == null) {
            throw new IllegalArgumentException("td must not be null");
        }
        String body = source.substring(source.indexOf('[') + 1, source.indexOf(']'));
        long seedLo = 0;
        long seedHi = 0;
        double xzScale = OVERWORLD_XZ_SCALE;
        double yScale = OVERWORLD_Y_SCALE;
        double xzFactor = OVERWORLD_XZ_FACTOR;
        double yFactor = OVERWORLD_Y_FACTOR;
        double smear = OVERWORLD_SMEAR;
        for (String part : body.split(",")) {
            String[] kv = part.trim().split("=");
            if (kv.length != 2) {
                throw new IllegalArgumentException("bad token in td: " + part);
            }
            String key = kv[0].trim();
            String value = kv[1].trim();
            try {
                switch (key) {
                    case "seedLo" -> seedLo = Long.parseLong(value);
                    case "seedHi" -> seedHi = Long.parseLong(value);
                    case "xzScale" -> xzScale = Double.parseDouble(value);
                    case "yScale" -> yScale = Double.parseDouble(value);
                    case "xzFactor" -> xzFactor = Double.parseDouble(value);
                    case "yFactor" -> yFactor = Double.parseDouble(value);
                    case "smear" -> smear = Double.parseDouble(value);
                    default -> throw new IllegalArgumentException("unknown key in td: " + key);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("bad value in td: " + part, e);
            }
        }
        return new BlendedNoise(new XoroRandom(seedLo, seedHi), xzScale, yScale, xzFactor, yFactor, smear);
    }
}