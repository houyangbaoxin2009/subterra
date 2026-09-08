package io.toterra.subterra.optim.worldgen.pipeline.noise.perlin;

import io.toterra.subterra.optim.worldgen.pipeline.noise.LegacyRandom;
import io.toterra.subterra.optim.worldgen.pipeline.noise.XoroRandom;

/**
 * The octave-Perlin-noise family carried by an amplitude list over a lattice of
 * {@link ImprovedNoise} cells — structurally identical to Minecraft 1.21.1's
 * {@code net.minecraft.world.level.levelgen.synth.PerlinNoise} (an
 * {@code OctaveNoise<ImprovedNoise>}). Clean-room reimplementation verified
 * against the de-obfuscated 1.21.1 class.
 * <p>
 * Octave seeding follows vanilla's <em>single-stream</em> construction (the
 * {@code boolean == false} branch of the private constructor, used by
 * {@code createLegacyForBlendedNoise}): every {@link ImprovedNoise} lattice is
 * built from ONE shared {@link LegacyRandom}, positions consumed in descending
 * index order ({@code size-1 .. 0}); an enabled octave builds a lattice (one
 * {@code ImprovedNoise}, 262 draws), a zero-amplitude octave only advances the
 * stream via {@link #skipOctave} (262 discarded {@code nextInt()} calls), so
 * each octave's permutation is pinned to its stream position exactly like
 * vanilla. An amplitude array spans octaves
 * {@code firstOctave .. firstOctave + size-1}; the value factors of zero
 * entries still advance. {@link #getValue} walks octaves from lowest frequency
 * up: the input is wrapped into {@code [-2^25, 2^25]} and scaled by
 * {@code 2^octave}, the cell is sampled, and the sum weighted by the amplitude
 * and by a low-frequency value factor {@code 2^(size-1) / (2^size - 1)} halving
 * per octave — exactly vanilla's operation order.
 * <p>
 * 携振幅列表、基于 {@link ImprovedNoise} 单元的八度 Perlin 噪声族——结构上与
 * Minecraft 1.21.1 的 {@code net.minecraft.world.level.levelgen.synth.PerlinNoise}
 * （即 {@code OctaveNoise<ImprovedNoise>}）一致（对照反混淆后的 1.21.1 类做
 * 净室复现）。八度建表遵循原生的<em>单流</em>构造（私有构造器的
 * {@code boolean == false} 分支，即 {@code createLegacyForBlendedNoise} 所用）：
 * 所有 {@link ImprovedNoise} 晶格均由同一个共享 {@link LegacyRandom} 建表，
 * 按下标降序（{@code size-1 .. 0}）消耗流；启用八度建一个晶格（消耗 262 次），
 * 零振幅八度仅通过 {@link #skipOctave} 前进流（丢弃 262 个 {@code nextInt()}），
 * 于是每个八度的置换表都如其原生地钉在其流位置。振幅数组横跨八度
 * {@code firstOctave .. firstOctave + size-1}；零振幅项的值因子仍递进。
 * {@link #getValue} 自低频向高频遍历：先将输入折叠进 {@code [-2^25, 2^25]}
 * 并按 {@code 2^octave} 缩放，采样单元，再以振幅与每八度减半的低频值因子
 * {@code 2^(size-1) / (2^size - 1)} 加权求和——与原生计算顺序完全一致。
 */
public final class PerlinNoise {

    /** Vanilla wrap half-period {@code 2^25}. */
    private static final double WRAP = 3.3554432E7d;

    private final ImprovedNoise[] noiseLevels;
    private final int firstOctave;
    private final double[] amplitudes;
    private final double lowestFreqInputFactor;
    private final double lowestFreqValueFactor;
    private final double maxValue;

    private PerlinNoise(int firstOctave, double[] amplitudes, ImprovedNoise[] noiseLevels) {
        this.firstOctave = firstOctave;
        this.amplitudes = amplitudes;
        this.noiseLevels = noiseLevels;
        int size = amplitudes.length;
        this.lowestFreqInputFactor = Math.pow(2.0, (double) firstOctave);
        this.lowestFreqValueFactor = Math.pow(2.0, (double) (size - 1)) / (Math.pow(2.0, (double) size) - 1.0);
        this.maxValue = edgeValue(2.0);
    }

    /**
     * Builds an octave-Perlin field from a single {@link LegacyRandom} stream
     * seeded with {@code masterSeed}, consuming positions in descending index
     * order as vanilla's legacy construction does.
     *
     * @param masterSeed  seed of the one shared octave stream.
     * @param firstOctave the octave of amplitude index 0 (may be negative; coarser
     *                    octaves sample the input scaled by {@code 2^firstOctave}).
     * @param amplitudes  one amplitude per octave; must be non-empty with at least
     *                    one non-zero entry.
     * @throws IllegalArgumentException if {@code amplitudes} is null/empty or all zero.
     */
    public static PerlinNoise create(long masterSeed, int firstOctave, double[] amplitudes) {
        if (amplitudes == null || amplitudes.length == 0) {
            throw new IllegalArgumentException("Need some octaves!");
        }
        boolean any = false;
        for (double a : amplitudes) {
            if (a != 0.0) {
                any = true;
                break;
            }
        }
        if (!any) {
            throw new IllegalArgumentException("Need at least one non-zero amplitude");
        }
        LegacyRandom random = new LegacyRandom(masterSeed);
        ImprovedNoise[] levels = new ImprovedNoise[amplitudes.length];
        for (int k = amplitudes.length - 1; k >= 0; k--) {
            if (amplitudes[k] != 0.0) {
                levels[k] = new ImprovedNoise(random);
            } else {
                skipOctave(random);
            }
        }
        return new PerlinNoise(firstOctave, amplitudes, levels);
    }

    /**
     * Advances a stream by one octave's worth of draws without building a
     * lattice — the vanilla {@code skipOctave}, i.e. {@code consumeCount(262)},
     * which is 262 discarded {@link LegacyRandom#nextInt()} calls (one
     * {@code ImprovedNoise} consumes exactly 262 {@code next(32)} draws: 6 from
     * three {@code nextDouble} and 256 from the Fisher-Yates shuffle). Exposed
     * so callers can reproduce the exact stream position of any octave.
     */
    public static void skipOctave(LegacyRandom random) {
        for (int i = 0; i < 262; i++) {
            random.nextInt();
        }
    }

    /**
     * Vanilla {@code skipOctave} over a shared {@link XoroRandom} stream
     * (p.1.8.29B): {@code consumeCount(262)} = 262 discarded {@code nextInt()}
     * draws, exactly the stream-advance {@code PerlinNoise.createLegacyForBlendedNoise}
     * applies to a zero-amplitude octave when the stream is the blended-noise
     * {@code XoroshiroRandomSource}.
     */
    public static void skipOctave(XoroRandom random) {
        for (int i = 0; i < 262; i++) {
            random.nextInt();
        }
    }

    /**
     * Builds an octave-Perlin field consuming a SHARED stream (p.1.8.29B) — the
     * vanilla non-legacy {@code PerlinNoise(RandomSource, ...)} construction used
     * by {@code createLegacyForBlendedNoise}: the first lattice is drawn from
     * {@code stream} at amplitude index {@code -firstOctave}, then octaves
     * {@code -firstOctave-1 .. 0} are consumed in descending order (a non-zero
     * amplitude builds a lattice, a zero amplitude advances the stream by one
     * octave). The stream is left advanced exactly past this family, so several
     * families can share one source — as {@code BlendedNoise} does (min, then
     * max, then main over a single Xoroshiro stream).
     *
     * @param stream      the shared stream to consume from.
     * @param firstOctave the octave of amplitude index 0.
     * @param amplitudes  one amplitude per octave.
     * @throws IllegalArgumentException if an octave index is positive (vanilla
     *                                  "Positive octaves are temporarily disabled").
     */
    public static PerlinNoise createFromStream(XoroRandom stream, int firstOctave, double[] amplitudes) {
        if (amplitudes == null || amplitudes.length == 0) {
            throw new IllegalArgumentException("Need some octaves!");
        }
        ImprovedNoise[] levels = new ImprovedNoise[amplitudes.length];
        ImprovedNoise first = new ImprovedNoise(stream);
        int firstIdx = -firstOctave;
        if (firstIdx >= 0 && firstIdx < amplitudes.length && amplitudes[firstIdx] != 0.0) {
            levels[firstIdx] = first;
        }
        for (int i = firstIdx - 1; i >= 0; i--) {
            if (i < amplitudes.length) {
                if (amplitudes[i] != 0.0) {
                    levels[i] = new ImprovedNoise(stream);
                } else {
                    skipOctave(stream);
                }
            } else {
                skipOctave(stream);
            }
        }
        if (firstOctave >= 0) {
            throw new IllegalArgumentException("Positive octaves are temporarily disabled");
        }
        return new PerlinNoise(firstOctave, amplitudes, levels);
    }

    /**
     * Evaluates the octave field at ({@code x}, {@code y}, {@code z}). The input
     * is wrapped, scaled per octave, each lattice sampled, and the amplitudes /
     * value-factors weighted exactly as vanilla.
     *
     * @return the summed octave value (un-normalized; the low-frequency factor bounds it).
     */
    public double getValue(double x, double y, double z) {
        double sum = 0.0;
        double input = this.lowestFreqInputFactor;
        double valueFactor = this.lowestFreqValueFactor;
        for (int k = 0; k < this.noiseLevels.length; k++) {
            ImprovedNoise level = this.noiseLevels[k];
            if (level != null) {
                double v = level.noise(wrap(x * input), wrap(y * input), wrap(z * input));
                sum += this.amplitudes[k] * v * valueFactor;
            }
            input *= 2.0;
            valueFactor /= 2.0;
        }
        return sum;
    }

    /** The lowest octave number (amplitude index 0). */
    public int firstOctave() {
        return this.firstOctave;
    }

    /** A defensive copy of the amplitude list. */
    public double[] amplitudes() {
        return this.amplitudes.clone();
    }

    /** The octave lattice at amplitude index {@code k} (null for a zero amplitude). */
    public ImprovedNoise octaveAt(int k) {
        return this.noiseLevels[k];
    }

    /**
     * Vanilla {@code getOctaveNoise(i)}: the octave lattice at the <em>reversed</em>
     * amplitude index {@code noiseLevels.length - 1 - i}. BlendedNoise (like vanilla
     * {@code synth.PerlinNoise.getOctaveNoise}) walks octaves 0..size-1 where octave
     * 0 is the highest-frequency lattice with the largest coordinate step — the exact
     * pairing the octave-blend loop relies on.
     */
    public ImprovedNoise getOctaveNoise(int i) {
        return this.noiseLevels[this.noiseLevels.length - 1 - i];
    }

    /** Vanilla {@code maxValue()}: worst-case edge magnitude (used by density callers). */
    public double maxValue() {
        return this.maxValue;
    }

    /**
     * Vanilla {@code maxBrokenValue(at)} = {@code edgeValue(at + 2.0)} — the
     * worst-case edge magnitude at a given amplitude; used by
     * {@code BlendedNoise} to bound its blended field ({@code maxValue =
     * minLimitNoise.maxBrokenValue(yMultiplier)}).
     */
    public double maxBrokenValue(double at) {
        return edgeValue(at + 2.0);
    }

    /** Vanilla {@code wrap}: fold a coordinate into {@code [-2^25, 2^25]}. */
    public static double wrap(double value) {
        return value - Math.floor(value / WRAP + 0.5) * WRAP;
    }

    /** Vanilla {@code edgeValue}: magnitude of the summed field at a fixed amplitude. */
    private double edgeValue(double at) {
        double sum = 0.0;
        double valueFactor = this.lowestFreqValueFactor;
        for (int k = 0; k < this.noiseLevels.length; k++) {
            if (this.noiseLevels[k] != null) {
                sum += this.amplitudes[k] * at * valueFactor;
            }
            valueFactor /= 2.0;
        }
        return sum;
    }

    /**
     * Self-describing params string ({@code PerlinNoise=[firstOctave, a0, a1, ...]});
     * the exact text accepted by {@link #fromTd(long, String)}.
     */
    public String td() {
        StringBuilder sb = new StringBuilder("PerlinNoise=[").append(this.firstOctave);
        for (double a : this.amplitudes) {
            sb.append(',').append(format(a));
        }
        return sb.append(']').toString();
    }

    /** Parses {@code PerlinNoise=[firstOctave, a0, a1, ...]} back into a field. */
    public static PerlinNoise fromTd(long masterSeed, String source) {
        String body = "PerlinNoise=[";
        if (source == null || !source.startsWith(body) || !source.endsWith("]")) {
            throw new IllegalArgumentException("expected " + body + "...]: " + source);
        }
        String inner = source.substring(body.length(), source.length() - 1).trim();
        if (inner.isEmpty()) {
            throw new IllegalArgumentException("empty params for PerlinNoise");
        }
        String[] parts = inner.split(",");
        final int firstOctave;
        try {
            firstOctave = Integer.parseInt(parts[0].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid firstOctave: " + parts[0], e);
        }
        double[] amps = new double[parts.length - 1];
        try {
            for (int i = 1; i < parts.length; i++) {
                amps[i - 1] = Double.parseDouble(parts[i].trim());
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid amplitude in: " + source, e);
        }
        return create(masterSeed, firstOctave, amps);
    }

    private static String format(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value) && Math.abs(value) < 1.0e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}