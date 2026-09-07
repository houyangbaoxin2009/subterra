package io.toterra.subterra.optim.worldgen.pipeline.noise.simplex;

import io.toterra.subterra.optim.worldgen.pipeline.noise.NoiseSalt;
import io.toterra.subterra.optim.worldgen.pipeline.noise.perlin.PerlinNoise;

/**
 * Amplitude-scaled normal noise mirroring Minecraft 1.21.1's
 * {@code net.minecraft.world.level.levelgen.synth.NormalNoise} exactly in
 * structure and constants (clean-room verification against the de-obfuscated
 * 1.21.1 class). 1.21.1's {@code NormalNoise} wraps <em>two</em> Perlin noise
 * layers ({@code first} + {@code second}) and scales the whole sum by a
 * {@code valueFactor}; there is no {@code DoublePerlinNoise} type in 1.21.1.
 * <p>
 * {@link #getValue(double, double, double)} evaluates
 * {@code (first(x,y,z) + second(x*K, y*K, z*K)) * valueFactor} where
 * {@code K = INPUT_FACTOR = 1.0181268882175227} scales only the second layer's
 * coordinates, and
 * {@code valueFactor = (1/6) / expectedDeviation(maxIndex - minIndex)} with
 * {@code expectedDeviation(span) = 0.1 * (1 + 1/(span+1))}; the span is the
 * distance between the lowest and highest non-zero amplitude indices.
 * {@code maxValue = (first.maxValue() + second.maxValue()) * valueFactor}.
 * <p>
 * Here the two layers are {@link PerlinNoise} (the sibling octave-Perlin module,
 * which is the vanilla-identical 1.21.1 mechanism). Because {@link PerlinNoise}
 * is seeded per master-seed rather than drawn from a stream, the second layer is
 * decorrelated from the first by mixing distinct fixed layer salts through
 * {@link NoiseSalt}, keeping everything fully deterministic for a fixed seed.
 * <p>
 * 按振幅缩放的普通噪声，其结构与常量精确镜像 Minecraft 1.21.1 的
 * {@code net.minecraft.world.level.levelgen.synth.NormalNoise}（对照反混淆后的
 * 1.21.1 类做净室验证）。1.21.1 的 {@code NormalNoise} 内嵌<em>两层</em> Perlin
 * 噪声（{@code first} + {@code second}）并用 {@code valueFactor} 缩放总和；1.21.1
 * 中并不存在 {@code DoublePerlinNoise} 类型。
 * {@link #getValue(double, double, double)} 计算
 * {@code (first(x,y,z) + second(x*K, y*K, z*K)) * valueFactor}，其中
 * {@code K = INPUT_FACTOR = 1.0181268882175227} 只缩放第二层坐标，
 * {@code valueFactor = (1/6) / expectedDeviation(maxIndex - minIndex)}，
 * {@code expectedDeviation(span) = 0.1 * (1 + 1/(span+1))}；span 为最大与最小时
 * 非零振幅索引之差。{@code maxValue = (first.maxValue() + second.maxValue()) *
 * valueFactor}。两层采用 {@link PerlinNoise}（相邻八度 Perlin 模块，即 1.21.1
 * 的原生机制）。由于 {@link PerlinNoise} 以主种子为主而非自流抽出，第二层通过与
 * {@link NoiseSalt} 混合两个不同的固定层盐与第一层去相关，固定种子下完全确定。
 */
public final class NormalNoise {

    /** Vanilla input frequency factor applied only to the second layer's coords. */
    private static final double INPUT_FACTOR = 1.0181268882175227;

    /** Layer salts; these decorrelate the two {@link PerlinNoise} lattice sets. */
    private static final long FIRST_LAYER_SALT = 0x1717_1717_1717_1717L;
    private static final long SECOND_LAYER_SALT = 0x2B2B_2B2B_2B2B_2B2BL;

    private final long masterSeed;
    private final int firstOctave;
    private final double[] amplitudes;
    private final double valueFactor;
    private final PerlinNoise first;
    private final PerlinNoise second;
    private final double maxValue;

    /**
     * Builds a normal-noise field from a master seed.
     *
     * @param masterSeed  the seed mixed (via {@link NoiseSalt}) with a per-layer salt.
     * @param firstOctave octave anchor forwarded to both Perlin layers.
     * @param amplitudes  one amplitude per octave; must be non-empty, finite and
     *                    contain at least one non-zero entry.
     * @throws IllegalArgumentException on empty / all-zero / non-finite amplitudes.
     */
    public static NormalNoise create(long masterSeed, int firstOctave, double... amplitudes) {
        return new NormalNoise(masterSeed, firstOctave, requireValid(amplitudes));
    }

    private NormalNoise(long masterSeed, int firstOctave, double[] amplitudes) {
        this.masterSeed = masterSeed;
        this.firstOctave = firstOctave;
        this.amplitudes = amplitudes;
        this.first = PerlinNoise.create(NoiseSalt.mix(masterSeed, FIRST_LAYER_SALT), firstOctave, amplitudes);
        this.second = PerlinNoise.create(NoiseSalt.mix(masterSeed, SECOND_LAYER_SALT), firstOctave, amplitudes);
        int minIndex = Integer.MAX_VALUE;
        int maxIndex = Integer.MIN_VALUE;
        for (int i = 0; i < amplitudes.length; i++) {
            if (amplitudes[i] != 0.0) {
                minIndex = Math.min(minIndex, i);
                maxIndex = Math.max(maxIndex, i);
            }
        }
        if (minIndex == Integer.MAX_VALUE) {
            minIndex = maxIndex = 0;
        }
        double expectedDeviation = 0.1 * (1.0 + 1.0 / (double) (maxIndex - minIndex + 1));
        this.valueFactor = (1.0 / 6.0) / expectedDeviation;
        this.maxValue = (first.maxValue() + second.maxValue()) * valueFactor;
    }

    /** The master seed this instance was built from. */
    public long masterSeed() {
        return masterSeed;
    }

    /** Octave anchor forwarded to both Perlin layers. */
    public int firstOctave() {
        return firstOctave;
    }

    /** Per-octave amplitudes (defensive copy). */
    public double[] amplitudes() {
        return amplitudes.clone();
    }

    /** The derived inner scaling factor ({@code (1/6) / expectedDeviation}). */
    public double valueFactor() {
        return valueFactor;
    }

    /** Deterministic conservative upper bound on the output magnitude. */
    public double maxValue() {
        return maxValue;
    }

    /** The first Perlin layer (exposed for composition verification). */
    public PerlinNoise first() {
        return first;
    }

    /** The second Perlin layer (evaluated at coordinates scaled by {@code INPUT_FACTOR}). */
    public PerlinNoise second() {
        return second;
    }

    /**
     * Evaluates {@code (first(x,y,z) + second(x*K, y*K, z*K)) * valueFactor}.
     */
    public double getValue(double x, double y, double z) {
        return (first.getValue(x, y, z)
                + second.getValue(x * INPUT_FACTOR, y * INPUT_FACTOR, z * INPUT_FACTOR)) * valueFactor;
    }

    private static double[] requireValid(double[] amplitudes) {
        if (amplitudes == null || amplitudes.length == 0) {
            throw new IllegalArgumentException("amplitudes must be non-empty");
        }
        for (double amp : amplitudes) {
            if (Double.isNaN(amp) || Double.isInfinite(amp)) {
                throw new IllegalArgumentException("amplitude must be finite: " + amp);
            }
        }
        return amplitudes;
    }

    /**
     * Self-describing params string ({@code NormalNoise=[masterSeed, firstOctave,
     * amp0,...]}), the exact text accepted by {@link #fromTd(String)}.
     */
    public String td() {
        StringBuilder sb = new StringBuilder("NormalNoise=[").append(masterSeed).append(',').append(firstOctave);
        for (double amp : amplitudes) {
            sb.append(',').append(amp);
        }
        return sb.append(']').toString();
    }

    /** Parses a {@code NormalNoise=[...]} string back into a deterministic field. */
    public static NormalNoise fromTd(String source) {
        double[] v = parseDoubles(source, "NormalNoise");
        if (v.length < 2) {
            throw new IllegalArgumentException("NormalNoise params need masterSeed + firstOctave");
        }
        long masterSeed = (long) v[0];
        int firstOctave = (int) v[1];
        double[] amps = new double[v.length - 2];
        System.arraycopy(v, 2, amps, 0, amps.length);
        return create(masterSeed, firstOctave, amps);
    }

    /** Minimal {@code Name=[a,b,...]} parser returning tokens as doubles. */
    private static double[] parseDoubles(String source, String name) {
        String body = name + "=[";
        if (source == null || !source.startsWith(body) || !source.endsWith("]")) {
            throw new IllegalArgumentException("expected " + body + "...]: " + source);
        }
        String inner = source.substring(body.length(), source.length() - 1);
        if (inner.isEmpty()) {
            return new double[0];
        }
        String[] parts = inner.split(",");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Double.parseDouble(parts[i].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid token in " + name + ": " + parts[i], e);
            }
        }
        return out;
    }
}