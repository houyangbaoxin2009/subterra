package io.toterra.subterra.api.worldgen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * p.2.36.2 世界生成配置契约（纯 JDK 不可变值、确定性）：**窗口 + 海平面 + 九维能力位**。
 * 它是 p.2.36 全线（.3 三场定调 / .4 九维关系图 / .5 结构消费 / .6 洞穴群系）的<b>配置地基</b>——
 * 后续每个子项只往这份契约上挂参数段，不另起配置源。
 * <p>
 * 钉死的事实与纪律：
 * <ul>
 *   <li><b>窗口对齐</b>：{@code min_y} 与 {@code height} 必须是 {@value #WINDOW_ALIGN} 的倍数
 *       （MC 维度类型的区段对齐事实），且 {@code min_y + height <= 2032}、{@code height > 0}。</li>
 *   <li><b>海平面必须在窗口内</b>：{@code min_y < sea_level < min_y + height}——这是 p.1.8.33
 *       淹没回归（地表被抬高的海面整体淹掉）的直接守门，越界确定性拒绝。</li>
 *   <li><b>九维固定序</b>：{@code dims} 必须恰为 {@link EcoDim#ALL} 的九个内建维、按该固定序排列，
 *       缺/多/乱序/重复确定性拒绝；缺省全 {@link DimMode#ENABLE}。</li>
 *   <li><b>四方一致</b>：窗口改动必须「JSON ↔ Kind ↔ dimension_type ↔ 探针」同步（p.1.8.33 教训），
 *       本契约是这四方的同一来源。</li>
 * </ul>
 * <b>双接口</b>：{@link #td()} 渲染 td 表字面量、{@link #fromTd(String)} 解析回来（逐字节往返）；
 * 键为扁平点号形式（{@code min_y} / {@code height} / {@code sea_level} / {@code dim.<id>}），
 * 与仓库既有的 {@code key = value} td 风格一致。缺省值只是起点——每键均可覆盖，未声明的维回落
 * {@link DimMode#ENABLE}。
 * <p>
 * p.2.36.2 the worldgen configuration contract (pure-JDK immutable, deterministic): **window + sea level +
 * nine-dimension capability bits**. It is the <b>configuration foundation</b> of the whole p.2.36 line
 * (.3 toning / .4 relation graph / .5 structures / .6 cave biomes) — later sub-items hang their parameter
 * sections off this contract rather than opening new config sources. Pinned facts and discipline: window
 * alignment ({@code min_y} and {@code height} are multiples of {@value #WINDOW_ALIGN}, {@code height > 0},
 * {@code min_y + height <= 2032}); the sea level must sit strictly inside the window (the direct guard
 * against the p.1.8.33 drowning regression); the nine dims must be exactly {@link EcoDim#ALL} in that fixed
 * order; and any window change must keep JSON ↔ Kind ↔ dimension_type ↔ probe in four-way agreement.
 * Dual interface: {@link #td()} renders a td table literal and {@link #fromTd(String)} parses it back
 * byte-exactly; keys are flat and dotted ({@code min_y} / {@code height} / {@code sea_level} /
 * {@code dim.<id>}). Defaults are only a starting point: every key is overridable and undeclared dimensions
 * fall back to {@link DimMode#ENABLE}.
 *
 * @param minY     the window bottom Y (multiple of {@value #WINDOW_ALIGN}).
 * @param height   the window height (positive multiple of {@value #WINDOW_ALIGN}).
 * @param seaLevel the sea level, strictly inside the window.
 * @param dims     the nine capability bits, exactly {@link EcoDim#ALL} in canonical order.
 */
public record WorldgenProfile(int minY, int height, double seaLevel, List<DimCapability> dims) {

    /** The default window bottom Y ({@code -128}), the p.2.36.1 landed default. /
     *  缺省窗口下沿 Y（{@code -128}），p.2.36.1 落地的默认值。 */
    public static final int DEFAULT_MIN_Y = -128;

    /** The default window height ({@code 720}), i.e. the window {@code [-128, 592)}. /
     *  缺省窗口高度（{@code 720}），即窗口 {@code [-128, 592)}。 */
    public static final int DEFAULT_HEIGHT = 720;

    /** The default sea level ({@code 127.0}), the p.1.8.33/34 design value. /
     *  缺省海平面（{@code 127.0}），p.1.8.33/34 的设计值。 */
    public static final double DEFAULT_SEA_LEVEL = 127.0D;

    /** The window alignment every MC dimension type obeys (section alignment, {@code 16}). /
     *  MC 维度类型遵循的窗口对齐（区段对齐，{@code 16}）。 */
    public static final int WINDOW_ALIGN = 16;

    /** The upper bound of {@code min_y + height} allowed by MC dimension types ({@code 2032}). /
     *  MC 维度类型允许的 {@code min_y + height} 上界（{@code 2032}）。 */
    public static final int MAX_WINDOW_TOP = 2032;

    /** The lower bound of {@code min_y} allowed by MC dimension types ({@code -2032}). /
     *  MC 维度类型允许的 {@code min_y} 下界（{@code -2032}）。 */
    public static final int MIN_WINDOW_BOTTOM = -2032;

    /** The td key prefix for dimension capability bits ({@code "dim."}). /
     *  九维能力位的 td 键前缀（{@code "dim."}）。 */
    public static final String DIM_KEY_PREFIX = "dim.";

    public WorldgenProfile {
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
        List<DimCapability> copy = List.copyOf(dims);
        if (copy.size() != EcoDim.ALL.size()) {
            throw new IllegalArgumentException("dims must carry exactly " + EcoDim.ALL.size()
                    + " capability bits: " + copy.size());
        }
        for (int i = 0; i < copy.size(); i++) {
            String expected = EcoDim.ALL.get(i).id();
            String actual = copy.get(i).dim();
            if (!expected.equals(actual)) {
                throw new IllegalArgumentException("dims must follow the canonical order: expected '"
                        + expected + "' at index " + i + " but found '" + actual + "'");
            }
        }
        dims = copy;
    }

    /** The default profile: window {@code [-128, 592)} with sea level {@code 127.0} and all nine
     *  dimensions enabled. / 缺省档案：窗口 {@code [-128, 592)}、海平面 {@code 127.0}、九维全启用。 */
    public static WorldgenProfile defaults() {
        List<DimCapability> bits = new ArrayList<>();
        for (EcoDim dim : EcoDim.ALL) {
            bits.add(DimCapability.enabled(dim.id()));
        }
        return new WorldgenProfile(DEFAULT_MIN_Y, DEFAULT_HEIGHT, DEFAULT_SEA_LEVEL, bits);
    }

    /** The window top Y (exclusive): {@code minY + height}. / 窗口上沿 Y（不含）：{@code minY + height}。 */
    public int windowTop() {
        return minY + height;
    }

    /** The capability bit of the dimension, or {@code null} for an unknown id (pure read). /
     *  该维的能力位，未知 id 返回 {@code null}（纯读）。 */
    public DimCapability capability(String dim) {
        if (dim == null) {
            return null;
        }
        String key = dim.trim().toLowerCase(Locale.ROOT);
        for (DimCapability cap : dims) {
            if (cap.dim().equals(key)) {
                return cap;
            }
        }
        return null;
    }

    /** The mode of the dimension ({@link DimMode#ENABLE} when the id is unknown, so reads never
     *  throw). / 该维的模式（未知 id 视为 {@link DimMode#ENABLE}，读永不抛）。 */
    public DimMode mode(String dim) {
        DimCapability cap = capability(dim);
        return cap == null ? DimMode.ENABLE : cap.mode();
    }

    /** A copy with the window replaced (re-validated; the receiver is unchanged). /
     *  替换窗口的副本（重新校验；原值不变）。 */
    public WorldgenProfile withWindow(int minY, int height, double seaLevel) {
        return new WorldgenProfile(minY, height, seaLevel, dims);
    }

    /** A copy with one capability bit replaced, canonical order preserved (re-validated). /
     *  替换单个能力位的副本，保持规范序（重新校验）。 */
    public WorldgenProfile withDim(String dim, DimMode mode, String target) {
        Objects.requireNonNull(mode, "mode must not be null");
        String key = dim == null ? "" : dim.trim().toLowerCase(Locale.ROOT);
        List<DimCapability> out = new ArrayList<>();
        boolean replaced = false;
        for (DimCapability cap : dims) {
            if (cap.dim().equals(key)) {
                out.add(new DimCapability(key, mode, target == null ? "" : target));
                replaced = true;
            } else {
                out.add(cap);
            }
        }
        if (!replaced) {
            throw new IllegalArgumentException("unknown dimension '" + dim + "'");
        }
        return new WorldgenProfile(minY, height, seaLevel, out);
    }

    /**
     * Renders the profile as a td table literal (canonical key order, deterministic). /
     * 渲染为 td 表字面量（规范键序、确定性）。
     *
     * <pre>{@code
     * [
     *   min_y = -128,
     *   height = 720,
     *   sea_level = 127.0,
     *   dim.terrain = enable,
     *   ...
     * ]
     * }</pre>
     *
     * @return the td document (never null); {@link #fromTd(String)} parses it back losslessly.
     */
    public String td() {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        sb.append("  min_y = ").append(minY).append(",\n");
        sb.append("  height = ").append(height).append(",\n");
        sb.append("  sea_level = ").append(Double.toString(seaLevel)).append(",\n");
        for (DimCapability cap : dims) {
            sb.append("  ").append(DIM_KEY_PREFIX).append(cap.dim()).append(" = ")
                    .append(cap.tdValue()).append(",\n");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Parses a td table literal into a profile. Recognised keys: {@code min_y}, {@code height},
     * {@code sea_level} and {@code dim.<id>} (each built-in dimension at most once). Undeclared
     * dimensions fall back to {@link DimMode#ENABLE}; undeclared window keys fall back to
     * {@link #defaults()}. A {@code type tie<data>} header, {@code //} comments and stray/trailing
     * commas are tolerated; duplicate keys, unknown keys, malformed entries and out-of-window sea
     * levels are rejected with {@link IllegalArgumentException}.
     * / 解析 td 表字面量为档案。识别 {@code min_y} / {@code height} / {@code sea_level} /
     * {@code dim.<id>}（每个内建维至多一次）四类键；未声明的维回落 {@link DimMode#ENABLE}，未声明的
     * 窗口键回落 {@link #defaults()}。容忍 {@code type tie<data>} 头、{@code //} 注释与多余逗号；
     * 重复键、未知键、畸形条目与越窗海平面确定性拒绝。
     *
     * @param source the td source (non-null, non-blank).
     * @return the parsed profile.
     * @throws NullPointerException     if {@code source} is null.
     * @throws IllegalArgumentException on any malformed or out-of-contract input.
     */
    public static WorldgenProfile fromTd(String source) {
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
                continue; // tolerate stray / trailing commas
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
                    if (EcoDim.of(dim) == null) {
                        throw new IllegalArgumentException("unknown td key '" + key + "'");
                    }
                    if (dimValues.put(dim, value) != null) {
                        throw new IllegalArgumentException("duplicate td key '" + key + "'");
                    }
                }
            }
        }
        WorldgenProfile base = defaults();
        int minYValue = minY == null ? base.minY() : minY;
        int heightValue = height == null ? base.height() : height;
        double seaValue = seaLevel == null ? base.seaLevel() : seaLevel;
        List<DimCapability> bits = new ArrayList<>();
        for (DimCapability cap : base.dims()) {
            String value = dimValues.get(cap.dim());
            if (value == null) {
                bits.add(cap);
                continue;
            }
            if (value.startsWith(DimCapability.REDIRECT_PREFIX)) {
                bits.add(new DimCapability(cap.dim(), DimMode.REDIRECT,
                        value.substring(DimCapability.REDIRECT_PREFIX.length()).trim()));
            } else {
                bits.add(new DimCapability(cap.dim(), DimMode.parse(value), DimCapability.NO_TARGET));
            }
        }
        return new WorldgenProfile(minYValue, heightValue, seaValue, bits);
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

    /** Strips {@code //} comments, an optional {@code type ...} header and outer whitespace, joining
     *  the surviving lines with a single space (flat keys only — no nested tables). /
     *  去掉 {@code //} 注释、可选的 {@code type ...} 头与外围空白，保留行以单空格连接
     *  （仅扁平键——无嵌套表）。 */
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
