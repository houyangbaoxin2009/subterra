package io.toterra.subterra.optim.worldgen.pipeline.biomesrc;

import java.util.Locale;
import java.util.Objects;

/**
 * A six-dimension climate parameter <em>point</em> — the sample produced by
 * {@link ClimateNoise} and the search key for {@link MultiNoiseBiomeSourceCore}
 * (p.1.8.16). The six axes mirror MC 1.21.1's {@code Climate.TargetPoint}
 * record: {@code temperature}, {@code humidity} (the router's vegetation),
 * {@code continentalness}, {@code erosion}, {@code depth} and {@code ridges}
 * (the router's weirdness). Points are immutable.
 * <p>
 * Quantization mirrors MC 1.21.1 {@code Climate.quantizeCoord(F)},
 * {@code (long)(f * 10000.0f)}: {@link #quantize(double)} casts through {@code float}
 * (the JVM's {@code f2l} truncates toward zero) using {@value #QUANTIZATION_FACTOR},
 * so {@code quantize(0.5) == 5000}. <em>Squared</em> distances are computed on the
 * quantized long axes (exactly the {@code Climate.Parameter.distance} +
 * {@code Mth.square} reduction aggregated in {@code ParameterPoint.fitness}).
 * <p>
 * A {@link ClimateBand} is a bundle of six possibly-unbounded
 * {@link ClimateParam.Range}s; {@link #fitness(ClimateBand)} reproduces the vanilla
 * fitness of this point against such a band (sum of squared per-axis linear
 * distances to the axis ranges).
 * <p>
 * 六维气候参数<em>点</em>——{@link ClimateNoise} 产出的样本，也是
 * {@link MultiNoiseBiomeSourceCore} 的搜索键（p.1.8.16）。六个坐标轴镜像 MC 1.21.1
 * {@code Climate.TargetPoint} record：{@code temperature}、{@code humidity}（路由器的
 * vegetation）、{@code continentalness}、{@code erosion}、{@code depth}、
 * {@code ridges}（路由器的 weirdness）。点不可变。
 * 量化镜像 MC 1.21.1 {@code Climate.quantizeCoord(F)}（{@code (long)(f*10000.0f)}）：
 * {@link #quantize(double)} 经 {@code float} 转换并用 {@value #QUANTIZATION_FACTOR}
 * （JVM 的 {@code f2l} 向零截断），故 {@code quantize(0.5)==5000}。<em>平方</em>距离按
 * 量化后的 long 轴计算（即 {@code Climate.Parameter.distance} +
 * {@code Mth.square} 在 {@code ParameterPoint.fitness} 中的聚合）。
 * {@link ClimateBand} 是六个可能无界的 {@link ClimateParam.Range} 的集合；
 * {@link #fitness(ClimateBand)} 复现该点对这样一个 band 的原生 fitness（各轴到范围的
 * 线性距离平方之和）。
 */
public final class ClimateParam {

    /** Number of climate axes (six). */
    public static final int DIMENSIONS = 6;

    /** MC 1.21.1 quantization factor: {@code Climate.QUANTIZATION_FACTOR = 10000.0f}. */
    public static final double QUANTIZATION_FACTOR = 10000.0;

    private static final String[] AXES = {
            "temperature", "humidity", "continentalness", "erosion", "depth", "ridges",
    };

    private final double temperature;
    private final double humidity;
    private final double continentalness;
    private final double erosion;
    private final double depth;
    private final double ridges;

    // quantized (long) axes, computed once for allocation-free scoring
    private final long qTemperature;
    private final long qHumidity;
    private final long qContinentalness;
    private final long qErosion;
    private final long qDepth;
    private final long qRidges;

    /**
     * Creates a climate point. Rejects non-finite inputs.
     *
     * @throws IllegalArgumentException if any axis is NaN or infinite.
     */
    public ClimateParam(double temperature, double humidity, double continentalness,
                        double erosion, double depth, double ridges) {
        this.temperature = requireFinite(temperature, "temperature");
        this.humidity = requireFinite(humidity, "humidity");
        this.continentalness = requireFinite(continentalness, "continentalness");
        this.erosion = requireFinite(erosion, "erosion");
        this.depth = requireFinite(depth, "depth");
        this.ridges = requireFinite(ridges, "ridges");
        this.qTemperature = quantize(temperature);
        this.qHumidity = quantize(humidity);
        this.qContinentalness = quantize(continentalness);
        this.qErosion = quantize(erosion);
        this.qDepth = quantize(depth);
        this.qRidges = quantize(ridges);
    }

    // ---- canonical accessors ----

    /** Temperature axis value (unquantized double). */
    public double temperature() {
        return temperature;
    }

    /** Humidity (router vegetation) axis value. */
    public double humidity() {
        return humidity;
    }

    /** Continentalness axis value. */
    public double continentalness() {
        return continentalness;
    }

    /** Erosion axis value. */
    public double erosion() {
        return erosion;
    }

    /** Depth axis value. */
    public double depth() {
        return depth;
    }

    /** Ridges (router weirdness) axis value. */
    public double ridges() {
        return ridges;
    }

    /** The unquantized double value of axis {@code i} (0..5). */
    public double axis(int i) {
        switch (i) {
            case 0:return temperature;
            case 1:return humidity;
            case 2:return continentalness;
            case 3:return erosion;
            case 4:return depth;
            case 5:return ridges;
            default:throw new IllegalArgumentException("no climate axis " + i);
        }
    }

    /** The quantized long value of axis {@code i} (0..5). */
    public long quantizedAxis(int i) {
        switch (i) {
            case 0:return qTemperature;
            case 1:return qHumidity;
            case 2:return qContinentalness;
            case 3:return qErosion;
            case 4:return qDepth;
            case 5:return qRidges;
            default:throw new IllegalArgumentException("no climate axis " + i);
        }
    }

    // ---- scoring ----

    /**
     * Squared Euclidean distance (on quantized axes) to another point. This is the
     * point-target reduction of {@code ParameterPoint.fitness}: for degenerate
     * (point) ranges {@code distance} reduces to {@code square(min - v)}.
     */
    public long squaredDistance(ClimateParam other) {
        Objects.requireNonNull(other, "other");
        long sum = 0;
        for (int i = 0; i < DIMENSIONS; i++) {
            long d = quantizedAxis(i) - other.quantizedAxis(i);
            sum += d * d;
        }
        return sum;
    }

    /**
     * Vanilla {@code ParameterPoint.fitness} style score of this point against a
     * {@link ClimateBand}: for each axis, the linear distance of the quantized
     * sample to the axis range, squared, then summed. (The vanilla
     * {@code offset} / weight term is added separately by the owning
     * {@link BiomeTarget}.) An unbounded side contributes zero distance via
     * {@link Range#distance(long)}.
     */
    public long fitness(ClimateBand band) {
        Objects.requireNonNull(band, "band");
        long sum = 0;
        for (int i = 0; i < DIMENSIONS; i++) {
            long d = band.axis(i).distance(quantizedAxis(i));
            sum += d * d;
        }
        return sum;
    }

    // ---- quantization & helpers ----

    /**
     * MC 1.21.1 {@code Climate.quantizeCoord} on a double: casts via {@code float}
     * then multiplies by {@value #QUANTIZATION_FACTOR} and truncates toward zero.
     */
    public static long quantize(double v) {
        return (long) ((float) v * (float) QUANTIZATION_FACTOR);
    }

    /** {@code Mth.square} analogue on a long. */
    public static long square(long v) {
        return v * v;
    }

    private static double requireFinite(double v, String name) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            throw new IllegalArgumentException("climate axis " + name + " must be finite: " + v);
        }
        return v;
    }

    /** Canonical axis name (used for diagnostics / td). */
    public static String axisName(int i) {
        if (i < 0 || i >= AXES.length) {
            throw new IllegalArgumentException("no climate axis " + i);
        }
        return AXES[i];
    }

    /**
     * Minimal self-description: {@code "[ temperature=<t>, humidity=<h>, continentalness=<c>,
     * erosion=<e>, depth=<d>, ridges=<r> ]"} with {@code %.6f} values.
     */
    public String td() {
        return String.format(Locale.ROOT,
                "[ temperature=%.6f, humidity=%.6f, continentalness=%.6f, erosion=%.6f, depth=%.6f, ridges=%.6f ]",
                temperature, humidity, continentalness, erosion, depth, ridges);
    }

    /** Parses a {@link #td()} snippet back into a {@link ClimateParam}. */
    public static ClimateParam fromTd(String td) {
        if (td == null) {
            throw new IllegalArgumentException("td must not be null");
        }
        double[] v = new double[DIMENSIONS];
        int found = 0;
        int idx = td.indexOf("temperature=");
        if (idx < 0) {
            throw new IllegalArgumentException("cannot parse td: " + td);
        }
        int pos = idx + "temperature=".length();
        for (int i = 0; i < DIMENSIONS; i++) {
            int comma = td.indexOf(',', pos);
            int end = comma < 0 ? td.indexOf(']', pos) : comma;
            if (end < 0) {
                throw new IllegalArgumentException("cannot parse td: " + td);
            }
            try {
                v[i] = Double.parseDouble(td.substring(pos, end).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("bad number in td: " + td, e);
            }
            pos = td.indexOf('=', end) + 1;
            found = i + 1;
        }
        if (found != DIMENSIONS) {
            throw new IllegalArgumentException("malformed td: " + td);
        }
        return new ClimateParam(v[0], v[1], v[2], v[3], v[4], v[5]);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClimateParam)) {
            return false;
        }
        ClimateParam p = (ClimateParam) o;
        return qTemperature == p.qTemperature && qHumidity == p.qHumidity
                && qContinentalness == p.qContinentalness && qErosion == p.qErosion
                && qDepth == p.qDepth && qRidges == p.qRidges;
    }

    @Override
    public int hashCode() {
        int r = 1;
        r = 31 * r + (int) (qTemperature ^ (qTemperature >>> 32));
        r = 31 * r + (int) (qHumidity ^ (qHumidity >>> 32));
        r = 31 * r + (int) (qContinentalness ^ (qContinentalness >>> 32));
        r = 31 * r + (int) (qErosion ^ (qErosion >>> 32));
        r = 31 * r + (int) (qDepth ^ (qDepth >>> 32));
        return r;
    }

    @Override
    public String toString() {
        return td();
    }

    /**
     * A single quantized parameter axis range {@code [min, max]}; one or both sides
     * may be unbounded. Mirrors {@code Climate.Parameter}. {@link #distance(long)}
     * is the linear distance of a quantized value to the range (0 when inside),
     * exactly {@code Climate.Parameter.distance(long)}.
     */
    public static final class Range {
        private final Long min;
        private final Long max;

        private Range(Long min, Long max) {
            if (min != null && max != null && min > max) {
                throw new IllegalArgumentException("empty range: [" + min + ", " + max + "]");
            }
            this.min = min;
            this.max = max;
        }

        /** A degenerate (exact-point) range at quantized {@code v}. */
        public static Range point(double v) {
            long q = quantize(v);
            return new Range(q, q);
        }

        /** A bounded closed range {@code [min, max]} (quantized). */
        public static Range span(double min, double max) {
            if (min > max) {
                throw new IllegalArgumentException("empty span: [" + min + ", " + max + "]");
            }
            return new Range(quantize(min), quantize(max));
        }

        /** A fully unbounded range (every value is inside). */
        public static Range boundless() {
            return new Range(null, null);
        }

        /** A lower-bounded range {@code [min, +oo)}. */
        public static Range minBounded(double min) {
            return new Range(quantize(min), null);
        }

        /** An upper-bounded range {@code (-oo, max]}. */
        public static Range maxBounded(double max) {
            return new Range(null, quantize(max));
        }

        /** Linear distance of a quantized value to this range (0 when inside). */
        public long distance(long v) {
            if (max != null && v > max) {
                return v - max;
            }
            if (min != null && v < min) {
                return min - v;
            }
            return 0;
        }

        /** Whether {@code v} (a quantized value) lies inside this range. */
        public boolean contains(long v) {
            return distance(v) == 0;
        }

        public Long min() {
            return min;
        }

        public Long max() {
            return max;
        }

        @Override
        public String toString() {
            if (min == null && max == null) {
                return "[-oo,+oo]";
            }
            return "[" + (min == null ? "-oo" : min) + ", " + (max == null ? "+oo" : max) + "]";
        }
    }

    /**
     * A bundle of six {@link Range}s (one per axis), the target-side model consumed
     * by {@link #fitness(ClimateBand)}. Mirrors the six {@code Parameter}s of
     * {@code Climate.ParameterPoint}. Immutable.
     */
    public static final class ClimateBand {
        private final Range[] axes;

        /** Builds a band from six ranges in canonical axis order. */
        public ClimateBand(Range temperature, Range humidity, Range continentalness,
                           Range erosion, Range depth, Range ridges) {
            this.axes = new Range[]{Objects.requireNonNull(temperature, "temperature"),
                    Objects.requireNonNull(humidity, "humidity"),
                    Objects.requireNonNull(continentalness, "continentalness"),
                    Objects.requireNonNull(erosion, "erosion"),
                    Objects.requireNonNull(depth, "depth"),
                    Objects.requireNonNull(ridges, "ridges")};
        }

        /** Builds a band from an array of exactly six ranges. */
        public ClimateBand(Range[] axes) {
            Objects.requireNonNull(axes, "axes");
            if (axes.length != DIMENSIONS) {
                throw new IllegalArgumentException("a climate band needs exactly " + DIMENSIONS
                        + " ranges, got " + axes.length);
            }
            this.axes = axes.clone();
            for (int i = 0; i < DIMENSIONS; i++) {
                if (this.axes[i] == null) {
                    throw new IllegalArgumentException("axis " + i + " range is null");
                }
            }
        }

        /** The range for axis {@code i}. */
        public Range axis(int i) {
            if (i < 0 || i >= DIMENSIONS) {
                throw new IllegalArgumentException("no climate axis " + i);
            }
            return axes[i];
        }

        /** The axis ranges. */
        public Range[] axes() {
            return axes.clone();
        }

        /** Builds a degenerate (exact-point) band from a {@code ClimateParam} center. */
        public static ClimateBand fromCenter(ClimateParam c) {
            Objects.requireNonNull(c, "center");
            Range[] rs = new Range[DIMENSIONS];
            for (int i = 0; i < DIMENSIONS; i++) {
                long q = c.quantizedAxis(i);
                rs[i] = new Range(q, q);
            }
            return new ClimateBand(rs);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("ClimateBand[");
            for (int i = 0; i < DIMENSIONS; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(axisName(i)).append('=').append(axes[i]);
            }
            return sb.append(']').toString();
        }
    }
}