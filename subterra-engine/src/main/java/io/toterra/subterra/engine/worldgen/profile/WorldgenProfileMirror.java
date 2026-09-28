package io.toterra.subterra.engine.worldgen.profile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * p.2.36.2 引擎侧镜像实现（api 定义形状 / engine 镜像实现范式，对应
 * {@code api.worldgen.WorldgenProfile} + {@code DimCapability} + {@code DimMode}）：把 api 的
 * <b>纯语义</b>（窗口/海平面校验、九维固定序能力位、td 渲染与解析）以同输入同输出逐字镜像，
 * 供 p.2.36.2 探针对照断言（api ↔ mirror 逐位一致）。本镜像<em>不 import</em> api 包——
 * 九维固定序在此以 {@link #DIM_ORDER} 独立复刻，探针断言它与 api {@code EcoDim.ALL} 同序，
 * 杜绝两侧漂移。
 * <p>
 * p.2.36.2 engine-side mirror implementation (api-shapes / engine-mirrors pattern, counterpart of
 * {@code api.worldgen.WorldgenProfile}): it mirrors the api's <b>pure semantics</b> (window/sea-level
 * validation, the nine-dimension capability bits in fixed order, td render and parse) verbatim with
 * same-input-same-output, asserted by the p.2.36.2 probe. This mirror does <em>not</em> import the api
 * package — the nine-dimension order is reproduced independently as {@link #DIM_ORDER} and the probe
 * asserts it matches api {@code EcoDim.ALL}, so the two sides cannot drift.
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

    /** Mirror of {@code WorldgenProfile}. / {@code WorldgenProfile} 的镜像。 */
    public record Profile(int minY, int height, double seaLevel, List<Cap> dims) {

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
            List<Cap> copy = List.copyOf(dims);
            if (copy.size() != DIM_ORDER.size()) {
                throw new IllegalArgumentException("dims must carry exactly " + DIM_ORDER.size()
                        + " capability bits: " + copy.size());
            }
            for (int i = 0; i < copy.size(); i++) {
                String expected = DIM_ORDER.get(i);
                String actual = copy.get(i).dim();
                if (!expected.equals(actual)) {
                    throw new IllegalArgumentException("dims must follow the canonical order: expected '"
                            + expected + "' at index " + i + " but found '" + actual + "'");
                }
            }
            dims = copy;
        }

        /** Mirror of {@code WorldgenProfile#windowTop()}. /
         *  {@code WorldgenProfile#windowTop()} 的镜像。 */
        public int windowTop() {
            return minY + height;
        }

        /** Mirror of {@code WorldgenProfile#td()}. / {@code WorldgenProfile#td()} 的镜像。 */
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
            sb.append("]");
            return sb.toString();
        }
    }

    /** Mirror of {@code WorldgenProfile#defaults()}. / {@code WorldgenProfile#defaults()} 的镜像。 */
    public static Profile defaults() {
        List<Cap> bits = new ArrayList<>();
        for (String dim : DIM_ORDER) {
            bits.add(Cap.enabled(dim));
        }
        return new Profile(DEFAULT_MIN_Y, DEFAULT_HEIGHT, DEFAULT_SEA_LEVEL, bits);
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
            switch (key) {
                case "min_y" -> {
                    requireAbsent(minY, key);
                    minY = parseInt(value, key);
                }
                case "height" -> {
                    requireAbsent(height, key);
                    height = parseInt(value, key);
                }
                case "sea_level" -> {
                    requireAbsent(seaLevel, key);
                    seaLevel = parseDouble(value, key);
                }
                default -> {
                    if (!key.startsWith(DIM_KEY_PREFIX)) {
                        throw new IllegalArgumentException("unknown td key '" + key + "'");
                    }
                    String dim = key.substring(DIM_KEY_PREFIX.length()).trim().toLowerCase(Locale.ROOT);
                    if (!DIM_ORDER.contains(dim)) {
                        throw new IllegalArgumentException("unknown td key '" + key + "'");
                    }
                    if (dimValues.put(dim, value) != null) {
                        throw new IllegalArgumentException("duplicate td key '" + key + "'");
                    }
                }
            }
        }
        Profile base = defaults();
        int minYValue = minY == null ? base.minY() : minY;
        int heightValue = height == null ? base.height() : height;
        double seaValue = seaLevel == null ? base.seaLevel() : seaLevel;
        List<Cap> bits = new ArrayList<>();
        for (Cap cap : base.dims()) {
            String value = dimValues.get(cap.dim());
            if (value == null) {
                bits.add(cap);
                continue;
            }
            if (value.startsWith(Cap.REDIRECT_PREFIX)) {
                bits.add(new Cap(cap.dim(), Mode.REDIRECT,
                        value.substring(Cap.REDIRECT_PREFIX.length()).trim()));
            } else {
                bits.add(new Cap(cap.dim(), Mode.parse(value), Cap.NO_TARGET));
            }
        }
        return new Profile(minYValue, heightValue, seaValue, bits);
    }

    /** Mirror of the api rejection message shape (same exceptions, same conditions). /
     *  api 拒绝行为的镜像（同异常、同条件）。 */
    public static boolean rejects(String source) {
        try {
            fromTd(source);
            return false;
        } catch (IllegalArgumentException | NullPointerException e) {
            return true;
        }
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
