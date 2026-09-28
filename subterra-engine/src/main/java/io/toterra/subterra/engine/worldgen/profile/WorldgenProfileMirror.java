package io.toterra.subterra.engine.worldgen.profile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * p.2.36.2 引擎侧镜像实现（api 定义形状 / engine 镜像实现范式，对应
 * {@code api.worldgen.WorldgenProfile} 及其伴生值类型）：把 api 的<b>纯语义</b>（窗口/海平面校验、
 * 九维固定序能力位与参数向量、关系图规范序与确定性拓扑排序、三场四元组、过渡带、td 渲染与解析）
 * 以同输入同输出逐字镜像，供 p.2.36.2 探针对照断言（api ↔ mirror 逐位一致）。本镜像<em>不 import</em>
 * api 包——九维固定序在此以 {@link #DIM_ORDER} 独立复刻，探针断言它与 api {@code EcoDim.ALL} 同序，
 * 杜绝两侧漂移。
 * <p>
 * p.2.36.2 engine-side mirror implementation (api-shapes / engine-mirrors pattern, counterpart of
 * {@code api.worldgen.WorldgenProfile} and its companion value types): it mirrors the api's <b>pure
 * semantics</b> (window/sea-level validation, the nine-dimension capability bits and parameter vectors in
 * fixed order, the relation graph's canonical edge order and deterministic topological sort, the three
 * tone-field quads, the transition bands, td render and parse) verbatim with same-input-same-output,
 * asserted by the p.2.36.2 probe. This mirror does <em>not</em> import the api package — the
 * nine-dimension order is reproduced independently as {@link #DIM_ORDER} and the probe asserts it matches
 * api {@code EcoDim.ALL}, so the two sides cannot drift.
 */
public final class WorldgenProfileMirror {

    /** Mirror of {@code DEFAULT_MIN_Y} ({@code -128}). / {@code DEFAULT_MIN_Y} 的镜像（{@code -128}）。 */
    public static final int DEFAULT_MIN_Y = -128;

    /** Mirror of {@code DEFAULT_HEIGHT} ({@code 720}). / {@code DEFAULT_HEIGHT} 的镜像（{@code 720}）。 */
    public static final int DEFAULT_HEIGHT = 720;

    /** Mirror of {@code DEFAULT_SEA_LEVEL} ({@code 127.0}). /
     *  {@code DEFAULT_SEA_LEVEL} 的镜像（{@code 127.0}）。 */
    public static final double DEFAULT_SEA_LEVEL = 127.0D;

    /** Mirror of {@code WINDOW_ALIGN} ({@code 16}). / {@code WINDOW_ALIGN} 的镜像（{@code 16}）。 */
    public static final int WINDOW_ALIGN = 16;

    /** Mirror of {@code MAX_WINDOW_TOP} ({@code 2032}). / {@code MAX_WINDOW_TOP} 的镜像（{@code 2032}）。 */
    public static final int MAX_WINDOW_TOP = 2032;

    /** Mirror of {@code MIN_WINDOW_BOTTOM} ({@code -2032}). /
     *  {@code MIN_WINDOW_BOTTOM} 的镜像（{@code -2032}）。 */
    public static final int MIN_WINDOW_BOTTOM = -2032;

    /** Mirror of {@code DIM_KEY_PREFIX} ({@code "dim."}). /
     *  {@code DIM_KEY_PREFIX} 的镜像（{@code "dim."}）。 */
    public static final String DIM_KEY_PREFIX = "dim.";

    /** Mirror of {@code PARAM_KEY_PREFIX} ({@code "p."}). /
     *  {@code PARAM_KEY_PREFIX} 的镜像（{@code "p."}）。 */
    public static final String PARAM_KEY_PREFIX = "p.";

    /** Mirror of {@code EDGE_KEY_PREFIX} ({@code "edge."}). /
     *  {@code EDGE_KEY_PREFIX} 的镜像（{@code "edge."}）。 */
    public static final String EDGE_KEY_PREFIX = "edge.";

    /** Mirror of {@code FIELD_KEY_PREFIX} ({@code "field."}). /
     *  {@code FIELD_KEY_PREFIX} 的镜像（{@code "field."}）。 */
    public static final String FIELD_KEY_PREFIX = "field.";

    /** Mirror of {@code TRANSITION_KEY_PREFIX} ({@code "transition."}). /
     *  {@code TRANSITION_KEY_PREFIX} 的镜像（{@code "transition."}）。 */
    public static final String TRANSITION_KEY_PREFIX = "transition.";

    /** Mirror of {@code FIELD_IDS}. / {@code FIELD_IDS} 的镜像。 */
    public static final List<String> FIELD_IDS = List.of(
            "coarse_elev", "coarse_mountain", "coarse_climate");

    /** Mirror of {@code TRANSITION_IDS}. / {@code TRANSITION_IDS} 的镜像。 */
    public static final List<String> TRANSITION_IDS = List.of("land_sea", "mountain", "biome");

    /** The canonical nine-dimension order, mirroring api {@code EcoDim.ALL} (asserted by the probe). /
     *  九维规范序，镜像 api {@code EcoDim.ALL}（由探针断言）。 */
    public static final List<String> DIM_ORDER = List.of(
            "terrain", "climate", "vegetation", "hydro", "surface",
            "litho", "mineral", "fauna", "relic");

    private WorldgenProfileMirror() {
    }

    /** Mirror of {@code DimMode}. / {@code DimMode} 的镜像。 */
    public enum Mode {

        /** Mirror of {@code ENABLE}. / {@code ENABLE} 的镜像。 */
        ENABLE,

        /** Mirror of {@code DISABLE}. / {@code DISABLE} 的镜像。 */
        DISABLE,

        /** Mirror of {@code REDIRECT}. / {@code REDIRECT} 的镜像。 */
        REDIRECT;

        /** Mirror of {@code DimMode#id()}. / {@code DimMode#id()} 的镜像。 */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Mirror of {@code DimMode#parse(String)}. / {@code DimMode#parse(String)} 的镜像。 */
        public static Mode parse(String value) {
            Objects.requireNonNull(value, "dim mode must not be null");
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "enable", "on", "1" -> ENABLE;
                case "disable", "off", "0" -> DISABLE;
                case "redirect" -> REDIRECT;
                default -> throw new IllegalArgumentException("unknown dim mode '" + value + "'");
            };
        }
    }

    /** Mirror of {@code Blend}. / {@code Blend} 的镜像。 */
    public enum Blend {

        /** Mirror of {@code MIX}. / {@code MIX} 的镜像。 */
        MIX,

        /** Mirror of {@code ADD}. / {@code ADD} 的镜像。 */
        ADD,

        /** Mirror of {@code MULTIPLY}. / {@code MULTIPLY} 的镜像。 */
        MULTIPLY,

        /** Mirror of {@code MAX}. / {@code MAX} 的镜像。 */
        MAX,

        /** Mirror of {@code MIN}. / {@code MIN} 的镜像。 */
        MIN;

        /** Mirror of {@code Blend#id()}. / {@code Blend#id()} 的镜像。 */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Mirror of {@code Blend#parse(String)}. / {@code Blend#parse(String)} 的镜像。 */
        public static Blend parse(String value) {
            Objects.requireNonNull(value, "blend must not be null");
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "mix" -> MIX;
                case "add" -> ADD;
                case "multiply" -> MULTIPLY;
                case "max" -> MAX;
                case "min" -> MIN;
                default -> throw new IllegalArgumentException("unknown blend '" + value + "'");
            };
        }
    }

    /** Mirror of {@code DimCapability}. / {@code DimCapability} 的镜像。 */
    public record Cap(String dim, Mode mode, String target) {

        /** Mirror of {@code NO_TARGET}. / {@code NO_TARGET} 的镜像。 */
        public static final String NO_TARGET = "";

        /** Mirror of {@code REDIRECT_PREFIX}. / {@code REDIRECT_PREFIX} 的镜像。 */
        public static final String REDIRECT_PREFIX = "redirect:";

        public Cap {
            Objects.requireNonNull(dim, "dim must not be null");
            Objects.requireNonNull(mode, "mode must not be null");
            Objects.requireNonNull(target, "target must not be null");
            dim = dim.trim().toLowerCase(Locale.ROOT);
            if (!DIM_ORDER.contains(dim)) {
                throw new IllegalArgumentException("unknown dimension '" + dim + "'");
            }
            target = target.trim();
            if (mode == Mode.REDIRECT) {
                if (target.isEmpty()) {
                    throw new IllegalArgumentException("redirect target must not be empty for dim '" + dim + "'");
                }
                int colon = target.indexOf(':');
                if (colon <= 0 || target.indexOf(':', colon + 1) >= 0) {
                    throw new IllegalArgumentException("redirect target must be exactly 'namespace:path': " + target);
                }
                if (!target.substring(0, colon).matches("[a-z0-9_.-]+")
                        || !target.substring(colon + 1).matches("[a-z0-9_./-]+")) {
                    throw new IllegalArgumentException("illegal redirect target '" + target + "'");
                }
            } else if (!target.isEmpty()) {
                throw new IllegalArgumentException("target is only allowed for REDIRECT (dim '" + dim + "')");
            }
        }

        /** Mirror of {@code DimCapability#enabled(String)}. /
         *  {@code DimCapability#enabled(String)} 的镜像。 */
        public static Cap enabled(String dim) {
            return new Cap(dim, Mode.ENABLE, NO_TARGET);
        }

        /** Mirror of {@code DimCapability#tdValue()}. / {@code DimCapability#tdValue()} 的镜像。 */
        public String tdValue() {
            return mode == Mode.REDIRECT ? REDIRECT_PREFIX + target : mode.id();
        }
    }

    /** Mirror of {@code DimParams}. / {@code DimParams} 的镜像。 */
    public record Params(double weight, double wavelength, double amplitude, double band) {

        /** Mirror of {@code DEFAULT_WEIGHT}. / {@code DEFAULT_WEIGHT} 的镜像。 */
        public static final double DEFAULT_WEIGHT = 1.0D;

        /** Mirror of {@code DEFAULT_WAVELENGTH}. / {@code DEFAULT_WAVELENGTH} 的镜像。 */
        public static final double DEFAULT_WAVELENGTH = 256.0D;

        /** Mirror of {@code DEFAULT_AMPLITUDE}. / {@code DEFAULT_AMPLITUDE} 的镜像。 */
        public static final double DEFAULT_AMPLITUDE = 1.0D;

        /** Mirror of {@code DEFAULT_BAND}. / {@code DEFAULT_BAND} 的镜像。 */
        public static final double DEFAULT_BAND = 0.5D;

        public Params {
            if (!Double.isFinite(weight) || weight < 0.0D) {
                throw new IllegalArgumentException("weight must be finite and >= 0: " + weight);
            }
            if (!Double.isFinite(wavelength) || wavelength <= 0.0D) {
                throw new IllegalArgumentException("wavelength must be finite and > 0: " + wavelength);
            }
            if (!Double.isFinite(amplitude) || amplitude < 0.0D) {
                throw new IllegalArgumentException("amplitude must be finite and >= 0: " + amplitude);
            }
            if (!Double.isFinite(band) || band < 0.0D || band > 1.0D) {
                throw new IllegalArgumentException("band must be finite within [0,1]: " + band);
            }
        }

        /** Mirror of {@code DimParams#defaults()}. / {@code DimParams#defaults()} 的镜像。 */
        public static Params defaults() {
            return new Params(DEFAULT_WEIGHT, DEFAULT_WAVELENGTH, DEFAULT_AMPLITUDE, DEFAULT_BAND);
        }

        /** Mirror of {@code DimParams#tdValue()}. / {@code DimParams#tdValue()} 的镜像。 */
        public String tdValue() {
            return Double.toString(weight) + ":" + Double.toString(wavelength) + ":"
                    + Double.toString(amplitude) + ":" + Double.toString(band);
        }

        /** Mirror of {@code DimParams#parse(String)}. / {@code DimParams#parse(String)} 的镜像。 */
        public static Params parse(String value) {
            if (value == null) {
                throw new NullPointerException("dim params value must not be null");
            }
            String[] slots = value.trim().split(":");
            if (slots.length != 4) {
                throw new IllegalArgumentException("dim params must carry 4 slots 'w:wl:amp:band': " + value);
            }
            return new Params(num(slots[0], "weight"), num(slots[1], "wavelength"),
                    num(slots[2], "amplitude"), num(slots[3], "band"));
        }

        private static double num(String raw, String slot) {
            try {
                return Double.parseDouble(raw.trim().toLowerCase(Locale.ROOT));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("malformed " + slot + " value '" + raw + "'");
            }
        }
    }

    /** Mirror of {@code RelationEdge}. / {@code RelationEdge} 的镜像。 */
    public record Edge(String from, String to, double weight, Blend blend) {

        /** Mirror of {@code DEFAULT_WEIGHT}. / {@code DEFAULT_WEIGHT} 的镜像。 */
        public static final double DEFAULT_WEIGHT = 1.0D;

        public Edge {
            Objects.requireNonNull(from, "from must not be null");
            Objects.requireNonNull(to, "to must not be null");
            Objects.requireNonNull(blend, "blend must not be null");
            from = from.trim().toLowerCase(Locale.ROOT);
            to = to.trim().toLowerCase(Locale.ROOT);
            if (!DIM_ORDER.contains(from)) {
                throw new IllegalArgumentException("unknown dimension '" + from + "'");
            }
            if (!DIM_ORDER.contains(to)) {
                throw new IllegalArgumentException("unknown dimension '" + to + "'");
            }
            if (from.equals(to)) {
                throw new IllegalArgumentException("self-edge is not allowed: " + from);
            }
            if (!Double.isFinite(weight) || weight < 0.0D) {
                throw new IllegalArgumentException("weight must be finite and >= 0: " + weight);
            }
        }

        /** Mirror of {@code RelationEdge#of(String, String)}. /
         *  {@code RelationEdge#of(String, String)} 的镜像。 */
        public static Edge of(String from, String to) {
            return new Edge(from, to, DEFAULT_WEIGHT, Blend.MIX);
        }

        /** Mirror of {@code RelationEdge#tdKey()}. / {@code RelationEdge#tdKey()} 的镜像。 */
        public String tdKey() {
            return EDGE_KEY_PREFIX + from + "." + to;
        }

        /** Mirror of {@code RelationEdge#tdValue()}. / {@code RelationEdge#tdValue()} 的镜像。 */
        public String tdValue() {
            return Double.toString(weight) + ":" + blend.id();
        }

        /** Mirror of {@code RelationEdge#parse(String, String, String)}. /
         *  {@code RelationEdge#parse(String, String, String)} 的镜像。 */
        public static Edge parse(String from, String to, String value) {
            if (value == null) {
                throw new NullPointerException("edge value must not be null");
            }
            String[] slots = value.trim().split(":");
            if (slots.length != 2) {
                throw new IllegalArgumentException("edge must carry 2 slots '<weight>:<blend>': " + value);
            }
            double weight;
            try {
                weight = Double.parseDouble(slots[0].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("malformed edge weight '" + slots[0] + "'");
            }
            return new Edge(from, to, weight, Blend.parse(slots[1]));
        }
    }

    /** Mirror of {@code FieldQuad}. / {@code FieldQuad} 的镜像。 */
    public record Quad(double gain, double bias, double band, Blend blend) {

        /** Mirror of {@code BYPASS_GAIN}. / {@code BYPASS_GAIN} 的镜像。 */
        public static final double BYPASS_GAIN = 0.0D;

        /** Mirror of {@code DEFAULT_BIAS}. / {@code DEFAULT_BIAS} 的镜像。 */
        public static final double DEFAULT_BIAS = 0.0D;

        /** Mirror of {@code DEFAULT_BAND}. / {@code DEFAULT_BAND} 的镜像。 */
        public static final double DEFAULT_BAND = 0.5D;

        public Quad {
            Objects.requireNonNull(blend, "blend must not be null");
            if (!Double.isFinite(gain)) {
                throw new IllegalArgumentException("gain must be finite: " + gain);
            }
            if (!Double.isFinite(bias)) {
                throw new IllegalArgumentException("bias must be finite: " + bias);
            }
            if (!Double.isFinite(band) || band < 0.0D || band > 1.0D) {
                throw new IllegalArgumentException("band must be finite within [0,1]: " + band);
            }
        }

        /** Mirror of {@code FieldQuad#bypass()}. / {@code FieldQuad#bypass()} 的镜像。 */
        public static Quad bypass() {
            return new Quad(BYPASS_GAIN, DEFAULT_BIAS, DEFAULT_BAND, Blend.MIX);
        }

        /** Mirror of {@code FieldQuad#tdValue()}. / {@code FieldQuad#tdValue()} 的镜像。 */
        public String tdValue() {
            return Double.toString(gain) + ":" + Double.toString(bias) + ":"
                    + Double.toString(band) + ":" + blend.id();
        }

        /** Mirror of {@code FieldQuad#parse(String)}. / {@code FieldQuad#parse(String)} 的镜像。 */
        public static Quad parse(String value) {
            if (value == null) {
                throw new NullPointerException("field quad value must not be null");
            }
            String[] slots = value.trim().split(":");
            if (slots.length != 4) {
                throw new IllegalArgumentException(
                        "field quad must carry 4 slots '<gain>:<bias>:<band>:<blend>': " + value);
            }
            return new Quad(num(slots[0], "gain"), num(slots[1], "bias"),
                    num(slots[2], "band"), Blend.parse(slots[3]));
        }

        private static double num(String raw, String slot) {
            try {
                return Double.parseDouble(raw.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("malformed " + slot + " value '" + raw + "'");
            }
        }
    }

    /** Mirror of {@code TransitionBand} (with the nested {@link Curve}). /
     *  {@code TransitionBand} 的镜像（含嵌套 {@link Curve}）。 */
    public record Band(double width, Curve curve, int order) {

        /** Mirror of {@code DEFAULT_WIDTH}. / {@code DEFAULT_WIDTH} 的镜像。 */
        public static final double DEFAULT_WIDTH = 0.08D;

        /** Mirror of {@code DEFAULT_ORDER}. / {@code DEFAULT_ORDER} 的镜像。 */
        public static final int DEFAULT_ORDER = 1;

        public Band {
            Objects.requireNonNull(curve, "curve must not be null");
            if (!Double.isFinite(width) || width <= 0.0D) {
                throw new IllegalArgumentException("width must be finite and > 0: " + width);
            }
            if (order < 1) {
                throw new IllegalArgumentException("order must be >= 1: " + order);
            }
        }

        /** Mirror of {@code TransitionBand#defaults()}. / {@code TransitionBand#defaults()} 的镜像。 */
        public static Band defaults() {
            return new Band(DEFAULT_WIDTH, Curve.SMOOTHSTEP, DEFAULT_ORDER);
        }

        /** Mirror of {@code TransitionBand#tdValue()}. / {@code TransitionBand#tdValue()} 的镜像。 */
        public String tdValue() {
            return Double.toString(width) + ":" + curve.id() + ":" + order;
        }

        /** Mirror of {@code TransitionBand#parse(String)}. / {@code TransitionBand#parse(String)} 的镜像。 */
        public static Band parse(String value) {
            if (value == null) {
                throw new NullPointerException("transition value must not be null");
            }
            String[] slots = value.trim().split(":");
            if (slots.length != 3) {
                throw new IllegalArgumentException(
                        "transition must carry 3 slots '<width>:<curve>:<order>': " + value);
            }
            double width;
            int order;
            try {
                width = Double.parseDouble(slots[0].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("malformed transition width '" + slots[0] + "'");
            }
            try {
                order = Integer.parseInt(slots[2].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("malformed transition order '" + slots[2] + "'");
            }
            return new Band(width, Curve.parse(slots[1]), order);
        }

        /** Mirror of {@code TransitionBand.Curve}. / {@code TransitionBand.Curve} 的镜像。 */
        public enum Curve {

            /** Mirror of {@code CLAMP_LINEAR}. / {@code CLAMP_LINEAR} 的镜像。 */
            CLAMP_LINEAR,

            /** Mirror of {@code SMOOTHSTEP}. / {@code SMOOTHSTEP} 的镜像。 */
            SMOOTHSTEP;

            /** Mirror of {@code Curve#id()}. / {@code Curve#id()} 的镜像。 */
            public String id() {
                return name().toLowerCase(Locale.ROOT).replace('_', '-');
            }

            /** Mirror of {@code Curve#parse(String)}. / {@code Curve#parse(String)} 的镜像。 */
            public static Curve parse(String value) {
                Objects.requireNonNull(value, "curve must not be null");
                String normalized = value.trim().toLowerCase(Locale.ROOT)
                        .replace('_', '-').replace(" ", "");
                return switch (normalized) {
                    case "clamp-linear", "clamplinear", "linear" -> CLAMP_LINEAR;
                    case "smoothstep", "smooth" -> SMOOTHSTEP;
                    default -> throw new IllegalArgumentException("unknown transition curve '" + value + "'");
                };
            }
        }
    }

    /** Mirror of {@code WorldgenProfile}. / {@code WorldgenProfile} 的镜像。 */
    public record Profile(int minY, int height, double seaLevel, List<Cap> dims, List<Params> params,
                          List<Edge> edges, List<Quad> fields, List<Band> transitions) {

        public Profile {
            if (minY % WINDOW_ALIGN != 0) {
                throw new IllegalArgumentException("min_y must be a multiple of " + WINDOW_ALIGN + ": " + minY);
            }
            if (minY < MIN_WINDOW_BOTTOM) {
                throw new IllegalArgumentException("min_y must be >= " + MIN_WINDOW_BOTTOM + ": " + minY);
            }
            if (height <= 0 || height % WINDOW_ALIGN != 0) {
                throw new IllegalArgumentException(
                        "height must be a positive multiple of " + WINDOW_ALIGN + ": " + height);
            }
            if (minY + height > MAX_WINDOW_TOP) {
                throw new IllegalArgumentException("min_y + height must be <= " + MAX_WINDOW_TOP
                        + ": " + (minY + height));
            }
            if (!Double.isFinite(seaLevel)) {
                throw new IllegalArgumentException("sea_level must be finite: " + seaLevel);
            }
            if (seaLevel <= minY || seaLevel >= minY + height) {
                throw new IllegalArgumentException("sea_level must sit strictly inside the window ["
                        + minY + ", " + (minY + height) + "): " + seaLevel);
            }
            Objects.requireNonNull(dims, "dims must not be null");
            List<Cap> dimsCopy = List.copyOf(dims);
            if (dimsCopy.size() != DIM_ORDER.size()) {
                throw new IllegalArgumentException("dims must carry exactly " + DIM_ORDER.size()
                        + " capability bits: " + dimsCopy.size());
            }
            for (int i = 0; i < dimsCopy.size(); i++) {
                String expected = DIM_ORDER.get(i);
                String actual = dimsCopy.get(i).dim();
                if (!expected.equals(actual)) {
                    throw new IllegalArgumentException("dims must follow the canonical order: expected '"
                            + expected + "' at index " + i + " but found '" + actual + "'");
                }
            }
            dims = dimsCopy;

            Objects.requireNonNull(params, "params must not be null");
            List<Params> paramsCopy = List.copyOf(params);
            if (paramsCopy.size() != DIM_ORDER.size()) {
                throw new IllegalArgumentException("params must carry exactly " + DIM_ORDER.size()
                        + " vectors: " + paramsCopy.size());
            }
            params = paramsCopy;

            Objects.requireNonNull(edges, "edges must not be null");
            List<Edge> edgesCopy = List.copyOf(edges);
            int lastFrom = -1;
            int lastTo = -1;
            for (Edge edge : edgesCopy) {
                int from = indexOf(edge.from());
                int to = indexOf(edge.to());
                if (from < 0 || to < 0) {
                    throw new IllegalArgumentException("edge endpoints must be built-in dimensions: " + edge);
                }
                if (from < lastFrom || (from == lastFrom && to <= lastTo)) {
                    throw new IllegalArgumentException("edges must follow the strict canonical (from, to) "
                            + "order — this also bans duplicate edges: " + edge.tdKey());
                }
                lastFrom = from;
                lastTo = to;
            }
            edges = edgesCopy;

            Objects.requireNonNull(fields, "fields must not be null");
            List<Quad> fieldsCopy = List.copyOf(fields);
            if (fieldsCopy.size() != FIELD_IDS.size()) {
                throw new IllegalArgumentException("fields must carry exactly " + FIELD_IDS.size()
                        + " quads: " + fieldsCopy.size());
            }
            fields = fieldsCopy;

            Objects.requireNonNull(transitions, "transitions must not be null");
            List<Band> transitionsCopy = List.copyOf(transitions);
            if (transitionsCopy.size() != TRANSITION_IDS.size()) {
                throw new IllegalArgumentException("transitions must carry exactly " + TRANSITION_IDS.size()
                        + " bands: " + transitionsCopy.size());
            }
            transitions = transitionsCopy;

            evaluationOrder(dims, edges); // cycle check at construction time (deterministic rejection)
        }

        /** Mirror of {@code windowTop()}. / {@code windowTop()} 的镜像。 */
        public int windowTop() {
            return minY + height;
        }

        /** Mirror of {@code td()}. / {@code td()} 的镜像。 */
        public String td() {
            StringBuilder sb = new StringBuilder();
            sb.append("[\n");
            sb.append("  min_y = ").append(minY).append(",\n");
            sb.append("  height = ").append(height).append(",\n");
            sb.append("  sea_level = ").append(Double.toString(seaLevel)).append(",\n");
            for (Cap cap : dims) {
                sb.append("  ").append(DIM_KEY_PREFIX).append(cap.dim()).append(" = ")
                        .append(cap.tdValue()).append(",\n");
            }
            for (int i = 0; i < params.size(); i++) {
                sb.append("  ").append(PARAM_KEY_PREFIX).append(dims.get(i).dim()).append(" = ")
                        .append(params.get(i).tdValue()).append(",\n");
            }
            if (!edges.equals(defaultEdges())) {
                for (Edge edge : edges) {
                    sb.append("  ").append(edge.tdKey()).append(" = ").append(edge.tdValue()).append(",\n");
                }
            }
            List<Quad> fieldDefaults = defaultFields();
            for (int i = 0; i < fields.size(); i++) {
                if (!fields.get(i).equals(fieldDefaults.get(i))) {
                    sb.append("  ").append(FIELD_KEY_PREFIX).append(FIELD_IDS.get(i)).append(" = ")
                            .append(fields.get(i).tdValue()).append(",\n");
                }
            }
            List<Band> transitionDefaults = defaultTransitions();
            for (int i = 0; i < transitions.size(); i++) {
                if (!transitions.get(i).equals(transitionDefaults.get(i))) {
                    sb.append("  ").append(TRANSITION_KEY_PREFIX).append(TRANSITION_IDS.get(i)).append(" = ")
                            .append(transitions.get(i).tdValue()).append(",\n");
                }
            }
            sb.append("]");
            return sb.toString();
        }
    }

    /** The canonical index of a dimension id, or {@code -1} when unknown. /
     *  维度 id 的规范序下标，未知返回 {@code -1}。 */
    private static int indexOf(String dim) {
        for (int i = 0; i < DIM_ORDER.size(); i++) {
            if (DIM_ORDER.get(i).equals(dim)) {
                return i;
            }
        }
        return -1;
    }

    /** Mirror of {@code defaultEdges()}. / {@code defaultEdges()} 的镜像。 */
    public static List<Edge> defaultEdges() {
        List<Edge> edges = new ArrayList<>();
        edges.add(Edge.of("terrain", "vegetation"));
        edges.add(Edge.of("terrain", "hydro"));
        edges.add(Edge.of("terrain", "surface"));
        edges.add(Edge.of("terrain", "litho"));
        edges.add(Edge.of("terrain", "mineral"));
        edges.add(Edge.of("climate", "vegetation"));
        edges.add(Edge.of("climate", "hydro"));
        edges.add(Edge.of("climate", "surface"));
        edges.add(Edge.of("climate", "fauna"));
        edges.add(Edge.of("climate", "relic"));
        edges.add(Edge.of("hydro", "vegetation"));
        edges.add(Edge.of("hydro", "surface"));
        edges.add(Edge.of("surface", "litho"));
        edges.add(Edge.of("surface", "mineral"));
        edges.add(Edge.of("surface", "fauna"));
        edges.add(Edge.of("surface", "relic"));
        return List.copyOf(edges);
    }

    /** Mirror of {@code defaultFields()}. / {@code defaultFields()} 的镜像。 */
    public static List<Quad> defaultFields() {
        return List.of(Quad.bypass(), Quad.bypass(), Quad.bypass());
    }

    /** Mirror of {@code defaultTransitions()}. / {@code defaultTransitions()} 的镜像。 */
    public static List<Band> defaultTransitions() {
        return List.of(
                new Band(0.08D, Band.Curve.SMOOTHSTEP, 1),
                new Band(0.25D, Band.Curve.SMOOTHSTEP, 2),
                new Band(0.12D, Band.Curve.CLAMP_LINEAR, 1));
    }

    /** Mirror of {@code WorldgenProfile#defaults()}. / {@code WorldgenProfile#defaults()} 的镜像。 */
    public static Profile defaults() {
        List<Cap> bits = new ArrayList<>();
        List<Params> vectors = new ArrayList<>();
        for (String dim : DIM_ORDER) {
            bits.add(Cap.enabled(dim));
            vectors.add(Params.defaults());
        }
        return new Profile(DEFAULT_MIN_Y, DEFAULT_HEIGHT, DEFAULT_SEA_LEVEL,
                bits, vectors, defaultEdges(), defaultFields(), defaultTransitions());
    }

    /** Mirror of {@code WorldgenProfile#fromTd(String)}. /
     *  {@code WorldgenProfile#fromTd(String)} 的镜像。 */
    public static Profile fromTd(String source) {
        Objects.requireNonNull(source, "td source must not be null");
        String text = normalize(source);
        if (text.isEmpty()) {
            throw new IllegalArgumentException("malformed td: empty document");
        }
        if (text.charAt(0) != '[' || text.charAt(text.length() - 1) != ']') {
            throw new IllegalArgumentException("malformed td: expected a '[ ... ]' table literal");
        }
        String body = text.substring(1, text.length() - 1);
        Integer minY = null;
        Integer height = null;
        Double seaLevel = null;
        Map<String, String> dimValues = new LinkedHashMap<>();
        Map<String, String> paramValues = new LinkedHashMap<>();
        Map<String, Edge> edgeValues = new LinkedHashMap<>();
        Map<String, String> fieldValues = new LinkedHashMap<>();
        Map<String, String> transitionValues = new LinkedHashMap<>();
        for (String raw : body.split(",")) {
            String entry = raw.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("malformed td entry: '" + entry + "'");
            }
            String key = entry.substring(0, eq).trim();
            String value = entry.substring(eq + 1).trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("malformed td entry: empty value for '" + key + "'");
            }
            if (key.equals("min_y")) {
                requireAbsent(minY, key);
                minY = parseInt(value, key);
            } else if (key.equals("height")) {
                requireAbsent(height, key);
                height = parseInt(value, key);
            } else if (key.equals("sea_level")) {
                requireAbsent(seaLevel, key);
                seaLevel = parseDouble(value, key);
            } else if (key.startsWith(DIM_KEY_PREFIX)) {
                String dim = requireDimKey(key, DIM_KEY_PREFIX);
                if (dimValues.put(dim, value) != null) {
                    throw new IllegalArgumentException("duplicate td key '" + key + "'");
                }
            } else if (key.startsWith(PARAM_KEY_PREFIX)) {
                String dim = requireDimKey(key, PARAM_KEY_PREFIX);
                if (paramValues.put(dim, value) != null) {
                    throw new IllegalArgumentException("duplicate td key '" + key + "'");
                }
            } else if (key.startsWith(EDGE_KEY_PREFIX)) {
                String pair = key.substring(EDGE_KEY_PREFIX.length());
                int dot = pair.indexOf('.');
                if (dot <= 0 || dot == pair.length() - 1 || pair.indexOf('.', dot + 1) >= 0) {
                    throw new IllegalArgumentException("edge key must be 'edge.<from>.<to>': " + key);
                }
                String from = pair.substring(0, dot).trim().toLowerCase(Locale.ROOT);
                String to = pair.substring(dot + 1).trim().toLowerCase(Locale.ROOT);
                Edge edge = Edge.parse(from, to, value);
                if (edgeValues.put(from + ">" + to, edge) != null) {
                    throw new IllegalArgumentException("duplicate td key '" + key + "'");
                }
            } else if (key.startsWith(FIELD_KEY_PREFIX)) {
                String id = key.substring(FIELD_KEY_PREFIX.length()).trim();
                if (!FIELD_IDS.contains(id)) {
                    throw new IllegalArgumentException("unknown td key '" + key + "'");
                }
                if (fieldValues.put(id, value) != null) {
                    throw new IllegalArgumentException("duplicate td key '" + key + "'");
                }
            } else if (key.startsWith(TRANSITION_KEY_PREFIX)) {
                String id = key.substring(TRANSITION_KEY_PREFIX.length()).trim();
                if (!TRANSITION_IDS.contains(id)) {
                    throw new IllegalArgumentException("unknown td key '" + key + "'");
                }
                if (transitionValues.put(id, value) != null) {
                    throw new IllegalArgumentException("duplicate td key '" + key + "'");
                }
            } else {
                throw new IllegalArgumentException("unknown td key '" + key + "'");
            }
        }
        Profile base = defaults();
        int minYValue = minY == null ? base.minY() : minY;
        int heightValue = height == null ? base.height() : height;
        double seaValue = seaLevel == null ? base.seaLevel() : seaLevel;

        List<Cap> bits = new ArrayList<>();
        List<Params> vectors = new ArrayList<>();
        for (int i = 0; i < base.dims().size(); i++) {
            String dim = base.dims().get(i).dim();
            String capValue = dimValues.get(dim);
            bits.add(capValue == null ? base.dims().get(i) : parseCap(dim, capValue));
            String paramValue = paramValues.get(dim);
            vectors.add(paramValue == null ? base.params().get(i) : Params.parse(paramValue));
        }

        List<Edge> edgeList;
        if (edgeValues.isEmpty()) {
            edgeList = base.edges();
        } else {
            edgeList = new ArrayList<>(edgeValues.values());
            edgeList.sort((a, b) -> {
                int byFrom = Integer.compare(indexOf(a.from()), indexOf(b.from()));
                return byFrom != 0 ? byFrom : Integer.compare(indexOf(a.to()), indexOf(b.to()));
            });
        }

        List<Quad> fieldList = new ArrayList<>(base.fields());
        for (int i = 0; i < FIELD_IDS.size(); i++) {
            String value = fieldValues.get(FIELD_IDS.get(i));
            if (value != null) {
                fieldList.set(i, Quad.parse(value));
            }
        }

        List<Band> transitionList = new ArrayList<>(base.transitions());
        for (int i = 0; i < TRANSITION_IDS.size(); i++) {
            String value = transitionValues.get(TRANSITION_IDS.get(i));
            if (value != null) {
                transitionList.set(i, Band.parse(value));
            }
        }

        return new Profile(minYValue, heightValue, seaValue, bits, vectors,
                edgeList, fieldList, transitionList);
    }

    /** Mirror of the api rejection behaviour (same exceptions, same conditions). /
     *  api 拒绝行为的镜像（同异常、同条件）。 */
    public static boolean rejects(String source) {
        try {
            fromTd(source);
            return false;
        } catch (IllegalArgumentException | NullPointerException e) {
            return true;
        }
    }

    /** Mirror of {@code evaluationOrder()} (deterministic Kahn, canonical tie-break, cycles rejected). /
     *  {@code evaluationOrder()} 的镜像（确定性 Kahn、规范序打破并列、环确定性拒绝）。 */
    public static List<String> evaluationOrder(Profile profile) {
        return evaluationOrder(profile.dims(), profile.edges());
    }

    private static List<String> evaluationOrder(List<Cap> dims, List<Edge> edges) {
        int n = dims.size();
        int[] indegree = new int[n];
        List<List<Integer>> outgoing = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            outgoing.add(new ArrayList<>());
        }
        for (Edge edge : edges) {
            int from = indexOf(edge.from());
            int to = indexOf(edge.to());
            outgoing.get(from).add(to);
            indegree[to]++;
        }
        List<Integer> ready = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (indegree[i] == 0) {
                ready.add(i);
            }
        }
        List<String> order = new ArrayList<>(n);
        while (!ready.isEmpty()) {
            int picked = ready.remove(0);
            order.add(dims.get(picked).dim());
            for (int to : outgoing.get(picked)) {
                indegree[to]--;
                if (indegree[to] == 0) {
                    insertByCanonicalOrder(ready, to);
                }
            }
        }
        if (order.size() != n) {
            throw new IllegalArgumentException("relation graph contains a cycle");
        }
        return List.copyOf(order);
    }

    private static void insertByCanonicalOrder(List<Integer> ready, int index) {
        int at = 0;
        while (at < ready.size() && ready.get(at) < index) {
            at++;
        }
        ready.add(at, index);
    }

    private static Cap parseCap(String dim, String value) {
        if (value.startsWith(Cap.REDIRECT_PREFIX)) {
            return new Cap(dim, Mode.REDIRECT,
                    value.substring(Cap.REDIRECT_PREFIX.length()).trim());
        }
        return new Cap(dim, Mode.parse(value), Cap.NO_TARGET);
    }

    /** Validates a {@code <prefix><dim>} key's dimension id and returns it (lower-cased). /
     *  校验 {@code <前缀><维>} 键的维度 id 并返回（小写）。 */
    private static String requireDimKey(String key, String prefix) {
        String dim = key.substring(prefix.length()).trim().toLowerCase(Locale.ROOT);
        if (!DIM_ORDER.contains(dim)) {
            throw new IllegalArgumentException("unknown td key '" + key + "'");
        }
        return dim;
    }

    private static void requireAbsent(Object existing, String key) {
        if (existing != null) {
            throw new IllegalArgumentException("duplicate td key '" + key + "'");
        }
    }

    private static int parseInt(String value, String key) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("malformed integer for '" + key + "': " + value);
        }
    }

    private static double parseDouble(String value, String key) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("malformed number for '" + key + "': " + value);
        }
    }

    private static String normalize(String source) {
        StringBuilder sb = new StringBuilder(source.length());
        for (String line : source.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("type ")) {
                continue;
            }
            int comment = trimmed.indexOf("//");
            if (comment >= 0) {
                trimmed = trimmed.substring(0, comment).trim();
            }
            if (!trimmed.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(trimmed);
            }
        }
        return sb.toString().trim();
    }
}
