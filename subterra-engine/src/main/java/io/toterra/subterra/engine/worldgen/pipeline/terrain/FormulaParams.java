package io.toterra.subterra.engine.worldgen.pipeline.terrain;

import java.util.Objects;

/**
 * Immutable generation parameters for the math-formula terrain (p.1.8.10,
 * self-developed clean-room). Bundles the user formula string plus the scalar
 * controls that {@link FormulaTerrain} applies: horizontal scale, vertical
 * height multiplier, the noise-overlay amplitude, a low-pass smoothing blend
 * factor, the overlay noise flavour and the deterministic seed.
 * <p>
 * The complete {@link #td()} self-description round-trips losslessly through
 * {@link #fromTd(String)}. Because <code>subterra-optim</code> depends only on
 * <code>subterra-api</code> (iron-law dependency direction), the robust td
 * parser lives in the sibling <code>subterra-config</code> module and is NOT
 * importable here; {@link #fromTd(String)} therefore carries a minimal,
 * self-contained parser for exactly this type's shape
 * ({@code [ key = value, ... ]}).
 * <p>
 * 数学公式地形的不可变生成参数（p.1.8.10，净室自研）：封装用户公式字符串叠加
 * {@link FormulaTerrain} 所施加的标量控制——水平缩放、垂直高度倍率、噪声叠加
 * 振幅、低通平滑混合因子、叠加噪声种类以及确定性种子。
 * {@link #td()} 完整自描述可通过 {@link #fromTd(String)} 无损往返。
 * 因为 <code>subterra-optim</code> 仅依赖 <code>subterra-api</code>（铁律依赖
 * 方向），健壮的 td 解析器位于相邻 <code>subterra-config</code> 模块、此处无法
 * 引入；{@link #fromTd(String)} 因此内嵌一个仅针对本类型形态
 * （{@code [ key = value, ... ]}）的最小自包含解析器。
 */
public final class FormulaParams {

    /** Overlay noise flavour selector. 叠加噪声种类。 */
    public enum Noise {
        NONE, PERLIN, SIMPLEX, NORMAL, VALUE
    }

    private final String formula;
    private final double scale;
    private final double height;
    private final double variation;
    private final double smoothing;
    private final Noise noise;
    private final long seed;

    private FormulaParams(String formula, double scale, double height, double variation,
                          double smoothing, Noise noise, long seed) {
        this.formula = formula;
        this.scale = scale;
        this.height = height;
        this.variation = variation;
        this.smoothing = smoothing;
        this.noise = noise;
        this.seed = seed;
    }

    /**
     * The full validating constructor (also what the builder funnels into).
     *
     * @param formula    a non-blank math formula; parsed eagerly by
     *                   {@link FormulaTerrain}, not here.
     * @param scale      horizontal (x/z) coordinate multiplier, must be &gt; 0.
     * @param height     vertical (y) coordinate multiplier, must be finite.
     * @param variation  amplitude of the {@link Noise} overlay (0 = disabled).
     * @param smoothing  low-pass blend factor in {@code [0,1]} (0 = disabled).
     * @param noise      the overlay noise flavour, never null.
     * @param seed       deterministic seed shared by the formula context and overlay.
     */
    public static FormulaParams of(String formula, double scale, double height, double variation,
                                   double smoothing, Noise noise, long seed) {
        Objects.requireNonNull(noise, "noise must not be null");
        if (formula == null || formula.isBlank()) {
            throw new IllegalArgumentException("formula must be non-blank");
        }
        requireFinitePositive(scale, "scale");
        requireFinite(height, "height");
        requireFinite(variation, "variation");
        requireFinite(smoothing, "smoothing");
        if (smoothing < 0.0 || smoothing > 1.0) {
            throw new IllegalArgumentException("smoothing must be in [0,1]: " + smoothing);
        }
        Noise n = (noise == null) ? Noise.NONE : noise;
        return new FormulaParams(formula, scale, height, variation, smoothing, n, seed);
    }

    /**
     * Default build: pure zero formula, unit scale, no overlay, no smoothing.
     * The {@link FormulaTerrain} built from a default yields the pure formula
     * (variation 0), i.e. vanilla-identical when nothing is customized.
     * 默认构建：纯 0 公式、单位缩放、无叠加、无平滑。
     */
    public static FormulaParams defaults() {
        return of("0", 1.0, 1.0, 0.0, 0.0, Noise.NONE, 0L);
    }

    /** Fluent builder rooted at {@link #defaults()}. 根植于 {@link #defaults()} 的流式构建器。 */
    public static Builder builder() {
        return new Builder();
    }

    public String formula() {
        return formula;
    }

    public double scale() {
        return scale;
    }

    public double height() {
        return height;
    }

    public double variation() {
        return variation;
    }

    public double smoothing() {
        return smoothing;
    }

    public Noise noise() {
        return noise;
    }

    public long seed() {
        return seed;
    }

    // ---------- record-style equality ----------

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FormulaParams that)) {
            return false;
        }
        return scale == that.scale && height == that.height
                && variation == that.variation && smoothing == that.smoothing
                && seed == that.seed && noise == that.noise
                && formula.equals(that.formula);
    }

    @Override
    public int hashCode() {
        int h = formula.hashCode();
        h = 31 * h + Double.hashCode(scale);
        h = 31 * h + Double.hashCode(height);
        h = 31 * h + Double.hashCode(variation);
        h = 31 * h + Double.hashCode(smoothing);
        h = 31 * h + noise.hashCode();
        h = 31 * h + Long.hashCode(seed);
        return h;
    }

    @Override
    public String toString() {
        return "FormulaParams" + td();
    }

    /**
     * Self-describing td table literal of every field, the exact text accepted
     * by {@link #fromTd(String)}. The formula is written as a double-quoted,
     * escaped string; numbers keep integral values integral for readability and
     * round-trip exactly.
     * <p>
     * 每个字段的 td 表字面量自描述，即 {@link #fromTd(String)} 所接受的确切文本。
     * 公式以双引号转义字符串写出；整数型数值保持整数形态以便阅读并精确往返。
     */
    public String td() {
        return "[ formula = \"" + escape(formula)
                + "\", scale = " + num(scale)
                + ", height = " + num(height)
                + ", variation = " + num(variation)
                + ", smoothing = " + num(smoothing)
                + ", noise = \"" + noise.name() + "\""
                + ", seed = " + seed + " ]";
    }

    /**
     * Parses the td table literal produced by {@link #td()} back into an equal
     * {@link FormulaParams}. Missing keys fall back to the documented defaults;
     * an over-any-constraint result (blank formula, {@code scale <= 0}) is
     * rejected via the same validation the constructor applies.
     */
    public static FormulaParams fromTd(String source) {
        if (source == null) {
            throw new IllegalArgumentException("td must not be null");
        }
        // Minimal self-contained parser: yields (key, rawValue) entries.
        Parsed parsed = new Parser(source).parse();
        String formula = parsed.stringValue("formula", "0");
        double scale = parsed.doubleValue("scale", 1.0);
        double height = parsed.doubleValue("height", 1.0);
        double variation = parsed.doubleValue("variation", 0.0);
        double smoothing = parsed.doubleValue("smoothing", 0.0);
        Noise noise = parsed.noiseValue("noise", Noise.NONE);
        long seed = parsed.longValue("seed", 0L);
        if (formula == null || formula.isBlank()) {
            throw new IllegalArgumentException("td: formula must be non-blank");
        }
        if (!(scale > 0.0)) {
            throw new IllegalArgumentException("td: scale must be > 0: " + scale);
        }
        return FormulaParams.of(formula, scale, height, variation, smoothing, noise, seed);
    }

    // ---------- td helpers (module-internal, upstream-data-shaped) ----------

    /** Parsed key/value table with typed accessors. */
    private static final class Parsed {
        private final java.util.Map<String, String> fields = new java.util.HashMap<>();
        private final java.util.LinkedHashSet<String> order = new java.util.LinkedHashSet<>();

        void put(String key, String value) {
            fields.put(key, value);
            order.add(key);
        }

        boolean has(String key) {
            return fields.containsKey(key);
        }

        String stringValue(String key, String dflt) {
            return has(key) ? fields.get(key) : dflt;
        }

        double doubleValue(String key, double dflt) {
            String v = fields.get(key);
            if (v == null) {
                return dflt;
            }
            try {
                double d = Double.parseDouble(v);
                if (Double.isNaN(d) || Double.isInfinite(d)) {
                    throw new IllegalArgumentException("td: non-finite " + key + ": " + v);
                }
                return d;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("td: bad " + key + ": " + v, e);
            }
        }

        long longValue(String key, long dflt) {
            String v = fields.get(key);
            if (v == null) {
                return dflt;
            }
            try {
                return Long.parseLong(v);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("td: bad " + key + ": " + v, e);
            }
        }

        Noise noiseValue(String key, Noise dflt) {
            String v = fields.get(key);
            if (v == null || v.isBlank()) {
                return dflt;
            }
            try {
                return Noise.valueOf(v.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("td: bad " + key + ": " + v, e);
            }
        }
    }

    /** Minimal {@code [ key = value, ... ]} scanner for FormulaParams' own shape. */
    private static final class Parser {
        private final String src;
        private int pos;

        Parser(String src) {
            this.src = src;
        }

        Parsed parse() {
            Parsed out = new Parsed();
            skipWs(); // then any number of open brackets (e.g. "[ [ ...")
            while (pos < src.length() && src.charAt(pos) == '[') {
                pos++;
                skipWs();
            }
            while (true) {
                skipWs();
                if (pos >= src.length()) {
                    throw new IllegalArgumentException("td: unterminated table: " + src);
                }
                char c = src.charAt(pos);
                if (c == ']') {
                    pos++;
                    break;
                }
                if (c == ',') {
                    pos++; // tolerate stray separators
                    continue;
                }
                String key = readIdent();
                if (key == null) {
                    throw new IllegalArgumentException("td: expected key at offset " + pos);
                }
                skipWs();
                if (pos >= src.length() || src.charAt(pos) != '=') {
                    throw new IllegalArgumentException("td: expected '=' after " + key);
                }
                pos++;
                skipWs();
                String value = readValue();
                out.put(key, value);
                skipWs();
                if (pos < src.length() && src.charAt(pos) == ',') {
                    pos++;
                }
            }
            return out;
        }

        private void skipWs() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private String readIdent() {
            int start = pos;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                boolean first = pos == start;
                boolean ident = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_'
                        || (!first && c >= '0' && c <= '9');
                if (!ident) {
                    break;
                }
                pos++;
            }
            return pos == start ? null : src.substring(start, pos);
        }

        private String readValue() {
            if (pos >= src.length()) {
                throw new IllegalArgumentException("td: expected value");
            }
            char c = src.charAt(pos);
            if (c == '"') {
                return readString();
            }
            int start = pos;
            while (pos < src.length()) {
                char v = src.charAt(pos);
                if (v == ',' || v == ']' || v == ' ' || v == '\t' || v == '\n' || v == '\r') {
                    break;
                }
                pos++;
            }
            if (pos == start) {
                throw new IllegalArgumentException("td: empty value at offset " + start);
            }
            return src.substring(start, pos);
        }

        private String readString() {
            pos++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= src.length()) {
                    throw new IllegalArgumentException("td: unterminated string");
                }
                char c = src.charAt(pos);
                if (c == '"') {
                    pos++;
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos + 1 >= src.length()) {
                        throw new IllegalArgumentException("td: dangling escape");
                    }
                    char e = src.charAt(pos + 1);
                    pos += 2;
                    switch (e) {
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        default -> throw new IllegalArgumentException("td: unknown escape \\" + e);
                    }
                } else {
                    sb.append(c);
                    pos++;
                }
            }
        }
    }

    private static String escape(String value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                case '\r' -> sb.append("\\r");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Formats a double as an integral token when it is one, else via toString. */
    private static String num(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value) && Math.abs(value) < 1.0e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private static void requireFinitePositive(double value, String name) {
        if (!(value > 0.0) || Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be a finite positive value: " + value);
        }
    }

    private static void requireFinite(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
    }

    /** Fluent builder; every field optional, defaults from {@link #defaults()}. */
    public static final class Builder {
        private String formula = "0";
        private double scale = 1.0;
        private double height = 1.0;
        private double variation = 0.0;
        private double smoothing = 0.0;
        private Noise noise = Noise.NONE;
        private long seed = 0L;

        public Builder formula(String formula) {
            this.formula = formula;
            return this;
        }

        public Builder scale(double scale) {
            this.scale = scale;
            return this;
        }

        public Builder height(double height) {
            this.height = height;
            return this;
        }

        public Builder variation(double variation) {
            this.variation = variation;
            return this;
        }

        public Builder smoothing(double smoothing) {
            this.smoothing = smoothing;
            return this;
        }

        public Builder noise(Noise noise) {
            this.noise = noise;
            return this;
        }

        public Builder seed(long seed) {
            this.seed = seed;
            return this;
        }

        /** Builds and validates. 构建并校验。 */
        public FormulaParams build() {
            return FormulaParams.of(formula, scale, height, variation, smoothing, noise, seed);
        }
    }
}