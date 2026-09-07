package io.toterra.subterra.optim.worldgen.pipeline.dimension;

import io.toterra.subterra.api.worldgen.EcoDim;
import io.toterra.subterra.optim.worldgen.pipeline.density.Densities;
import io.toterra.subterra.optim.worldgen.pipeline.density.Density;
import io.toterra.subterra.optim.worldgen.pipeline.terrain.FormulaParams;
import io.toterra.subterra.optim.worldgen.pipeline.terrain.FormulaTerrain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable per-dimension algorithm plan (Step 6, p.1.8.11): maps each
 * {@link EcoDim} of the nine-dimension model to an {@link Entry} — a
 * {@link DimAlgo} plus a canonical parameters string — that {@link
 * #materialize(long)} turns into a real {@link DimensionSlot} within a {@link
 * DimensionTerrain}. The <em>default plan</em> keeps every dimension on its
 * vanilla-compatible mirror slot; a custom plan swaps individual dimensions to
 * a math formula ({@link DimAlgo#FORMULA}), a constant
 * ({@link DimAlgo#CONSTANT}) or (reserved, not yet implemented)
 * {@link DimAlgo#MODEL}.
 * <p>
 * The td front-end accepts the canonical table-literal shape
 * {@code dimName=[algo, param…]}, e.g.
 * <pre>
 *   terrain=[formula, "sin(x/8)*cos(z/8)*10", scale=1, height=1]
 *   hydro=[constant, 63]
 * </pre>
 * {@link #fromTd(String)} parses {@code dimName=[algo, param…]} entries — one
 * or many, with or without an outer {@code [ … ]} wrapper — rejecting unknown
 * dimension names, unknown algo tokens, and malformed entries. A
 * {@link DimAlgo#FORMULA} entry requires a non-blank quoted formula string
 * (any remaining {@code key=value} params — {@code scale}, {@code height},
 * {@code variation}, {@code smoothing}, {@code noise} — are optional); a
 * {@link DimAlgo#CONSTANT} entry requires exactly one numeric param;
 * {@link DimAlgo#VANILLA} and {@link DimAlgo#MODEL} take no params. The seed is
 * <em>not</em> part of the plan: {@link #materialize(long)} binds the seed at
 * materialisation time (a {@code seed=N} formula param, if given, is accepted
 * but overridden by the materialise seed).
 * <p>
 * The module depends only on <code>subterra-api</code> plus sibling pipeline
 * packages; because the robust td parser lives in the not-importable
 * <code>subterra-config</code> module, {@link #fromTd(String)} carries a
 * minimal self-contained scanner for exactly this type's shape.
 * <p>
 * 不可变的逐维算法规划（第 6 步，p.1.8.11）：将九维模型的每个 {@link EcoDim} 映射到一个
 * {@link Entry}——即 {@link DimAlgo} 加上规范参数字符串——由 {@link #materialize(long)}
 * 把它变成 {@link DimensionTerrain} 中的一个真实 {@link DimensionSlot}。<em>默认规划</em>
 * 让每个维度保持其原版兼容镜像槽；自定义规划把单个维度切换为数学公式
 * （{@link DimAlgo#FORMULA}）、常量（{@link DimAlgo#CONSTANT}）或（预留、尚未实现）
 * {@link DimAlgo#MODEL}。
 * <p>
 * td 前端接受规范表字面量形态 {@code dimName=[algo, param…]}，例如
 * {@code terrain=[formula, "sin(x/8)*cos(z/8)*10", scale=1, height=1]}、
 * {@code hydro=[constant, 63]}。{@link #fromTd(String)} 解析一个或多个
 * {@code dimName=[algo, param…]} 条目（可带或不带外层 {@code [ … ]} 包壳），拒绝未知
 * 维度名、未知算法 token 以及畸形条目。{@link DimAlgo#FORMULA} 条目需要非空引号公式字符串
 * （其余 {@code key=value} 参数——{@code scale}、{@code height}、{@code variation}、
 * {@code smoothing}、{@code noise}——可选）；{@link DimAlgo#CONSTANT} 条目恰好需要一个数值
 * 参数；{@link DimAlgo#VANILLA} 与 {@link DimAlgo#MODEL} 不接受参数。种子<em>不属于</em>
 * 规划：{@link #materialize(long)} 在物化时才绑定种子（若给出 {@code seed=N} 公式参数也会
 * 被接受，但以物化种子为准）。
 *
 * @param entries the explicit overrides; any dimension absent from this map
 *        keeps its default VANILLA mirror slot (never modified; unmodifiable)
 */
public record DimensionPlan(Map<EcoDim, Entry> entries) {

    /**
     * Compact constructor: defensively copies into an unmodifiable map and drops
     * VANILLA entries (absence already equals "keep the default mirror slot"),
     * so {@code td()}/{@code fromTd()} round-trips are canonical.
     */
    public DimensionPlan {
        Map<EcoDim, Entry> norm = new LinkedHashMap<>(entries.size());
        for (Map.Entry<EcoDim, Entry> e : entries.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                throw new IllegalArgumentException("plan entries must not be null");
            }
            if (e.getValue().algo() == DimAlgo.VANILLA) {
                continue; // absence is the default
            }
            norm.put(e.getKey(), e.getValue());
        }
        entries = Collections.unmodifiableMap(norm);
    }

    /**
     * The default plan: every dimension keeps its vanilla-compatible mirror
     * slot (all-VANILLA).
     */
    public static DimensionPlan defaultPlan() {
        return new DimensionPlan(Map.of());
    }

    /**
     * Returns the algorithm planned for the given dimension, VANILLA when the
     * plan has no override for it.
     */
    public DimAlgo algo(EcoDim dim) {
        if (dim == null) {
            throw new IllegalArgumentException("dim must not be null");
        }
        Entry e = entries.get(dim);
        return e == null ? DimAlgo.VANILLA : e.algo();
    }

    /**
     * The exact {@code key=[algo, param…]} table-literal accepted by
     * {@link #fromTd(String)}, in canonical {@link EcoDim#ALL} order. Guarantees
     * {@code fromTd(td()).equals(this)}.
     */
    public String td() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (EcoDim dim : EcoDim.ALL) {
            Entry e = entries.get(dim);
            if (e == null) {
                continue;
            }
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(dim.id()).append("=[").append(e.algo().name().toLowerCase());
            if (!e.params().isEmpty()) {
                sb.append(", ").append(e.params());
            }
            sb.append(']');
        }
        return sb.append(']').toString();
    }

    /**
     * Self-contained parser for the {@code dimName=[algo, param…]} table-literal
     * emitted by {@link #td()} (or any equivalent). One or many entries may be
     * given, wrapped in an outer {@code [ … ]} or bare. Unknown dimension names
     * ({@link EcoDim#of}), unknown algo {@link DimAlgo#fromTd tokens}, and
     * malformed entries are rejected via {@link IllegalArgumentException}. A
     * {@link DimAlgo#CONSTANT} entry must carry exactly one numeric param; a
     * {@link DimAlgo#FORMULA} entry must carry a non-blank quoted formula string
     * (remaining params optional). The returned plan is normalized so missing
     * dimensions default to VANILLA.
     *
     * @param source the td table-literal (must not be null)
     */
    public static DimensionPlan fromTd(String source) {
        if (source == null) {
            throw new IllegalArgumentException("td must not be null");
        }
        Tokens t = new Tokens(source);
        int depth = 0;
        while (t.peek('[')) {
            depth++;
            t.next();
        }
        Map<EcoDim, Entry> map = new LinkedHashMap<>();
        while (true) {
            if (t.done()) {
                if (depth != 0) {
                    throw new IllegalArgumentException("td: unterminated table: " + source);
                }
                return new DimensionPlan(map);
            }
            char c = t.src.charAt(t.pos);
            if (c == ']') {
                if (depth == 0) {
                    throw new IllegalArgumentException("td: unexpected ']': " + source);
                }
                depth--;
                t.next();
                continue;
            }
            if (c == ',') {
                t.next();
                continue;
            }
            String name = t.readIdent();
            if (name == null) {
                throw new IllegalArgumentException("td: expected dimension name: " + source);
            }
            if (!t.consume('=')) {
                throw new IllegalArgumentException("td: expected '=' after " + name);
            }
            EcoDim dim = EcoDim.of(name);
            if (dim == null) {
                throw new IllegalArgumentException("td: unknown dimension: " + name);
            }
            if (t.peek('[')) {
                String body = t.readBracketed();
                map.put(dim, parseEntry(dim, body));
            } else {
                throw new IllegalArgumentException(
                        "td: expected '[' entry for " + name + ": " + source);
            }
        }
    }

    /**
     * Materialises this plan into a {@link DimensionTerrain} at the given seed:
     * dimensions with no override (and reserved {@link DimAlgo#MODEL} entries,
     * whose backend is not implemented this batch) keep the candidate set's
     * default mirror slot; {@link DimAlgo#VANILLA} is unsatisfiable here (already
     * normalized to absence); {@link DimAlgo#FORMULA} builds a
     * {@link FormulaTerrain} density (seed = the given materialise seed);
     * {@link DimAlgo#CONSTANT} builds {@link Densities#constant}. The default
     * {@link OverworldBounds} (vanilla) is used.
     *
     * @param seed the deterministic seed for noise / formula fields
     */
    public DimensionTerrain materialize(long seed) {
        DimensionTerrain terrain = new DimensionTerrain(seed);
        for (EcoDim dim : EcoDim.ALL) {
            Entry e = entries.get(dim);
            if (e == null) {
                continue;
            }
            switch (e.algo()) {
                case FORMULA -> terrain =
                        terrain.with(dim, formulaDensity(e, seed), DimAlgo.FORMULA);
                case CONSTANT -> terrain =
                        terrain.with(dim, Densities.constant(constantValue(e)), DimAlgo.CONSTANT);
                case MODEL, VANILLA -> {
                    // MODEL backend is not implemented; VANILLA is normalized
                    // away — keep the dimension's default mirror slot.
                }
                default -> throw new IllegalStateException("unhandled algo: " + e.algo());
            }
        }
        return terrain;
    }

    /**
     * An immutable per-dimension plan entry: the {@link DimAlgo} plus the
     * canonical parameter string emitted inside the td bracket. The params
     * string holds exactly what {@link DimensionPlan#td()} writes after the
     * algo token (e.g. {@code "sin(x)", scale=1, height=1} for a formula, a
     * single numeric token for a constant, empty for vanilla/model).
     *
     * @param algo   the algorithm (never null)
     * @param params the canonical parameters (never null; empty for none)
     */
    public record Entry(DimAlgo algo, String params) {

        /** Compact constructor normalising a null params to the empty string. */
        public Entry {
            if (algo == null) {
                throw new IllegalArgumentException("algo must not be null");
            }
            if (params == null) {
                params = "";
            }
        }
    }

    // ---------- per-algo parsing ----------

    /** Parses a single {@code [algo, param…]} body into a normalized {@link Entry}. */
    private static Entry parseEntry(EcoDim dim, String body) {
        List<String> items = splitTopLevel(body);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("td: empty entry for " + dim.id());
        }
        DimAlgo algo = DimAlgo.fromTd(items.get(0)); // throws on unknown / blank
        if (algo == null) {
            throw new IllegalArgumentException("td: blank algo for " + dim.id());
        }
        switch (algo) {
            case FORMULA:
                return parseFormula(dim, items);
            case CONSTANT:
                return parseConstant(dim, items);
            case MODEL:
                requireNoParams(dim, items);
                return new Entry(DimAlgo.MODEL, "");
            case VANILLA:
                requireNoParams(dim, items);
                return new Entry(DimAlgo.VANILLA, "");
            default:
                throw new IllegalStateException("unhandled algo: " + algo);
        }
    }

    private static void requireNoParams(EcoDim dim, List<String> items) {
        if (items.size() != 1) {
            throw new IllegalArgumentException(
                    "td: " + dim.id() + " entry takes no params, got: " + items.subList(1, items.size()));
        }
    }

    /** {@code [formula, "…", scale=…, height=…, …]} — non-blank quoted formula required. */
    private static Entry parseFormula(EcoDim dim, List<String> items) {
        if (items.size() < 2) {
            throw new IllegalArgumentException(
                    "td: " + dim.id() + "=[formula, \"…\"] requires a formula string");
        }
        String fTok = items.get(1).trim();
        if (!isQuoted(fTok)) {
            throw new IllegalArgumentException(
                    "td: " + dim.id() + " formula must be a quoted string, got: " + fTok);
        }
        String formula = unquote(fTok);
        if (formula.isBlank()) {
            throw new IllegalArgumentException("td: " + dim.id() + " formula must be non-blank");
        }
        double scale = 1.0, height = 1.0, variation = 0.0, smoothing = 0.0;
        FormulaParams.Noise noise = FormulaParams.Noise.NONE;
        for (int i = 2; i < items.size(); i++) {
            String kv = items.get(i).trim();
            int eq = kv.indexOf('=');
            if (eq < 0) {
                throw new IllegalArgumentException(
                        "td: " + dim.id() + " formula param must be key=value: " + kv);
            }
            String key = kv.substring(0, eq).trim();
            String val = kv.substring(eq + 1).trim();
            switch (key) {
                case "scale" -> scale = numericParam(dim, key, val);
                case "height" -> height = numericParam(dim, key, val);
                case "variation" -> variation = numericParam(dim, key, val);
                case "smoothing" -> smoothing = numericParam(dim, key, val);
                case "noise" -> noise = paramNoise(dim, val);
                case "seed" -> {
                    // Accepted for tolerance; overridden by the materialise seed.
                }
                default -> throw new IllegalArgumentException(
                        "td: " + dim.id() + " unknown formula param: " + key);
            }
        }
        // Canonical, order-fixed parameter body so td()/fromTd() round-trips exactly.
        String params = "\"" + escape(formula) + "\""
                + ", scale=" + num(scale)
                + ", height=" + num(height)
                + ", variation=" + num(variation)
                + ", smoothing=" + num(smoothing)
                + ", noise=\"" + noise.name() + "\"";
        return new Entry(DimAlgo.FORMULA, params);
    }

    /** {@code [constant, VALUE]} — exactly one numeric param. */
    private static Entry parseConstant(EcoDim dim, List<String> items) {
        if (items.size() != 2) {
            throw new IllegalArgumentException(
                    "td: " + dim.id() + "=[constant, VALUE] requires exactly one numeric param");
        }
        String vTok = items.get(1).trim();
        if (isQuoted(vTok) || !isFiniteNumber(vTok)) {
            throw new IllegalArgumentException(
                    "td: " + dim.id() + " constant param must be numeric, got: " + vTok);
        }
        return new Entry(DimAlgo.CONSTANT, num(Double.parseDouble(vTok)));
    }

    private static boolean isFiniteNumber(String tok) {
        try {
            double d = Double.parseDouble(tok);
            return !Double.isNaN(d) && !Double.isInfinite(d);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static double numericParam(EcoDim dim, String key, String val) {
        if (isQuoted(val) || !isFiniteNumber(val)) {
            throw new IllegalArgumentException("td: " + dim.id() + " " + key + " must be numeric, got: " + val);
        }
        return Double.parseDouble(val);
    }

    private static FormulaParams.Noise paramNoise(EcoDim dim, String val) {
        String tok = isQuoted(val) ? unquote(val) : val;
        try {
            return FormulaParams.Noise.valueOf(tok.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("td: " + dim.id() + " bad noise: " + val);
        }
    }

    // ---------- materialisation ----------

    /** Builds a {@link FormulaTerrain} density from the canonical formula entry. */
    private static Density formulaDensity(Entry e, long seed) {
        List<String> items = splitTopLevel(e.params());
        double scale = 1.0, height = 1.0, variation = 0.0, smoothing = 0.0;
        FormulaParams.Noise noise = FormulaParams.Noise.NONE;
        for (int i = 1; i < items.size(); i++) {
            String kv = items.get(i).trim();
            int eq = kv.indexOf('=');
            String key = kv.substring(0, eq).trim();
            String val = kv.substring(eq + 1).trim();
            switch (key) {
                case "scale" -> scale = Double.parseDouble(val);
                case "height" -> height = Double.parseDouble(val);
                case "variation" -> variation = Double.parseDouble(val);
                case "smoothing" -> smoothing = Double.parseDouble(val);
                case "noise" -> noise = FormulaParams.Noise.valueOf(unquote(val).toUpperCase());
                default -> {
                    // canonical params never carry anything else.
                }
            }
        }
        String formula = unquote(items.get(0));
        FormulaParams fp = FormulaParams.of(formula, scale, height, variation, smoothing, noise, seed);
        return new FormulaTerrain(fp);
    }

    /** The constant value from a canonical CONSTANT entry. */
    private static double constantValue(Entry e) {
        return Double.parseDouble(e.params().trim());
    }

    // ---------- minimal self-contained td scanning utilities ----------

    /**
     * Splits a body on top-level commas, skipping quoted strings (so commas
     * inside a formula survive) and nested brackets; returns non-empty trimmed
     * items.
     */
    private static List<String> splitTopLevel(String body) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        int start = 0;
        int i = 0;
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c == '"') {
                // skipString returns the index of the closing quote; resume
                // scanning AFTER it so the closing quote is not reinterpreted
                // as the opener of a fresh (empty) string.
                i = skipString(body, i) + 1;
                continue;
            }
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            } else if (c == ',' && depth == 0) {
                String item = body.substring(start, i).trim();
                if (!item.isEmpty()) {
                    out.add(item);
                }
                start = i + 1;
            }
            i++;
        }
        String last = body.substring(start).trim();
        if (!last.isEmpty()) {
            out.add(last);
        }
        return out;
    }

    /** Advances past a double-quoted string starting at {@code p} (char &lt; p). */
    private static int skipString(String s, int p) {
        int i = p + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '"') {
                return i;
            }
            i++;
        }
        throw new IllegalArgumentException("td: unterminated string: " + s);
    }

    private static boolean isQuoted(String s) {
        return s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"';
    }

    private static String unquote(String s) {
        return isQuoted(s) ? s.substring(1, s.length() - 1) : s;
    }

    /** Escapes a string for re-emission inside quotes. */
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

    /** Formats a double as an integral token when integral, else via toString. */
    private static String num(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value) && Math.abs(value) < 1.0e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    /** Minimal char scanner for the outer table shape. */
    private static final class Tokens {
        private final String src;
        private int pos;

        Tokens(String src) {
            this.src = src;
        }

        void skip() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        boolean done() {
            skip();
            return pos >= src.length();
        }

        boolean peek(char c) {
            skip();
            return pos < src.length() && src.charAt(pos) == c;
        }

        void next() {
            if (pos < src.length()) {
                pos++;
            }
        }

        boolean consume(char c) {
            skip();
            if (pos < src.length() && src.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        String readIdent() {
            skip();
            int start = pos;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
                if (!ok) {
                    break;
                }
                pos++;
            }
            return pos == start ? null : src.substring(start, pos);
        }

        /** Consumes a {@code [ … ]} group (quote- and bracket-aware) and returns its inner body. */
        String readBracketed() {
            skip();
            if (pos >= src.length() || src.charAt(pos) != '[') {
                throw new IllegalArgumentException("td: expected '['");
            }
            int start = pos + 1;
            int depth = 1;
            pos = start;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == '"') {
                    pos = skipString(src, pos) + 1;
                    continue;
                }
                if (c == '[') {
                    depth++;
                } else if (c == ']') {
                    depth--;
                    if (depth == 0) {
                        String body = src.substring(start, pos);
                        pos++;
                        return body.trim();
                    }
                }
                pos++;
            }
            throw new IllegalArgumentException("td: unterminated '[': " + src);
        }
    }
}