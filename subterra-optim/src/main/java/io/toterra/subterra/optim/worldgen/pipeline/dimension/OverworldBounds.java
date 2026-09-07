package io.toterra.subterra.optim.worldgen.pipeline.dimension;

/**
 * Overworld vertical extent (Step 4): replaces the Step 2 {@code Bounds}
 * stand-in with td presets, immutable block-space window {@code [minY,
 * minY + height)} in which overworld terrain is generated, together with the
 * sea level and the inclusive build ceiling (buildLimit). Built via the
 * {@link #vanilla()} / {@link #tall()} factories or the canonical td
 * round-trip ({@link #td()} / {@link #fromTd(String)}). Immutable,
 * deterministic; construction and parsing are linear in field count (no
 * O(n²)). Java record → structural {@code equals()}/{@code hashCode()}.
 * <p>
 * 主世界纵向范围（第 4 步）：以 td 预设取代第 2 步的 {@code Bounds} 替身，即生成主
 * 世界地形的不可变方块空间窗口 {@code [minY, minY + height)}，连同海平面与含的建筑
 * 高度顶（buildLimit）。经 {@link #vanilla()} / {@link #tall()} 工厂或规范 td 往返
 * （{@link #td()} / {@link #fromTd(String)}）构建。不可变、确定；构建与解析按字段数
 * 线性完成（无 O(n²)）。Java record → 结构 {@code equals()}/{@code hashCode()}。
 *
 * @param minY       the lowest block y (inclusive; negative is fine)
 * @param height     the vertical extent in blocks (must be &gt; 0)
 * @param seaLevel   the water surface y (must be inside {@code [minY, minY + height)})
 * @param buildLimit the inclusive build ceiling (must be within
 *                   {@code [minY, minY + height]})
 */
public record OverworldBounds(int minY, int height, int seaLevel, int buildLimit) {

    /** Vanilla window: {@code [minY=-64, height=384, seaLevel=63, buildLimit=320]}. */
    private static final OverworldBounds VANILLA = new OverworldBounds(-64, 384, 63, 320);

    /** Tall (compressed vertical) window: {@code [minY=-64, height=512, seaLevel=63, buildLimit=448]}. */
    private static final OverworldBounds TALL = new OverworldBounds(-64, 512, 63, 448);

    /**
     * Compact constructor validating the window invariant via
     * IllegalArgumentException: positive {@code height}, sea level below
     * {@code height}, and a build ceiling inside {@code [minY, minY + height]}.
     */
    public OverworldBounds {
        if (height <= 0) {
            throw new IllegalArgumentException("height must be > 0: " + height);
        }
        if (seaLevel < minY || seaLevel >= minY + height) {
            throw new IllegalArgumentException(
                    "seaLevel must be within [minY, minY + height) ["
                            + minY + ", " + (minY + height) + "): " + seaLevel);
        }
        if (buildLimit > minY + height) {
            throw new IllegalArgumentException(
                    "buildLimit must be <= minY + height (" + (minY + height) + "): " + buildLimit);
        }
        if (buildLimit < minY) {
            throw new IllegalArgumentException(
                    "buildLimit must be >= minY (" + minY + "): " + buildLimit);
        }
    }

    /** The vanilla overworld window (Step 4 default). */
    public static OverworldBounds vanilla() {
        return VANILLA;
    }

    /** The tall (compressed-scale) overworld window. */
    public static OverworldBounds tall() {
        return TALL;
    }

    /**
     * The canonical td table-literal form, exactly round-trippable through
     * {@link #fromTd(String)}: {@code [minY=-64, height=384, seaLevel=63,
     * buildLimit=320]}-style.
     * <p>
     * 规范 td 表字面量形式，可经 {@link #fromTd(String)} 精确往返：
     * {@code [minY=-64, height=384, seaLevel=63, buildLimit=320]} 风格。
     */
    public String td() {
        return "[minY=" + minY + ", height=" + height
                + ", seaLevel=" + seaLevel + ", buildLimit=" + buildLimit + "]";
    }

    /**
     * Parses a td table-literal produced by {@link #td()} (or any equivalent
     * comma-separated `key=value` list, with or without the surrounding
     * brackets). Unknown keys are rejected via IllegalArgumentException;
     * numeric values go through the same {@code OverworldBounds} validation as
     * direct construction. Self-contained, linear in the number of pairs.
     * <p>
     * 解析由 {@link #td()} 生成的 td 表字面量（或任何等价、可带或不带外层方括号的
     * 逗号分隔 `key=value` 列表）。未知键经 IllegalArgumentException 拒绝；数值经与
     * 直接构造相同的 {@code OverworldBounds} 校验。自包含、按键值对数量线性完成。
     *
     * @param td the td table-literal to parse (must not be null)
     * @throws IllegalArgumentException if any required key is missing, an
     *         unknown key appears, a value is not an int, or the invariant fails
     */
    public static OverworldBounds fromTd(String td) {
        if (td == null) {
            throw new IllegalArgumentException("td must not be null");
        }
        String body = td.trim();
        if (body.startsWith("[") && body.endsWith("]") && body.length() >= 2) {
            body = body.substring(1, body.length() - 1);
        }
        int minY = 0, height = 0, seaLevel = 0, buildLimit = 0;
        boolean hasMinY = false, hasHeight = false, hasSeaLevel = false, hasBuildLimit = false;
        for (String pair : body.split(",")) {
            String p = pair.trim();
            if (p.isEmpty()) {
                continue;
            }
            int eq = p.indexOf('=');
            if (eq < 0) {
                throw new IllegalArgumentException("malformed td key=value pair: " + p);
            }
            String key = p.substring(0, eq).trim();
            String value = p.substring(eq + 1).trim();
            int v = Integer.parseInt(value);
            switch (key) {
                case "minY" -> {
                    minY = v;
                    hasMinY = true;
                }
                case "height" -> {
                    height = v;
                    hasHeight = true;
                }
                case "seaLevel" -> {
                    seaLevel = v;
                    hasSeaLevel = true;
                }
                case "buildLimit" -> {
                    buildLimit = v;
                    hasBuildLimit = true;
                }
                default -> throw new IllegalArgumentException("unknown td key: " + key);
            }
        }
        if (!hasMinY || !hasHeight || !hasSeaLevel || !hasBuildLimit) {
            throw new IllegalArgumentException(
                    "td must define minY, height, seaLevel, buildLimit: " + td);
        }
        return new OverworldBounds(minY, height, seaLevel, buildLimit);
    }
}