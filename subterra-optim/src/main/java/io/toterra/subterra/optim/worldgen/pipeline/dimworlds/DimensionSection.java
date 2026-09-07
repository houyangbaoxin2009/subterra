package io.toterra.subterra.optim.worldgen.pipeline.dimworlds;

import io.toterra.subterra.optim.worldgen.pipeline.dimension.OverworldBounds;

/**
 * An immutable per-world section (p.1.8.19): pairs a {@link WorldDim} with its
 * coordinate scale (a user-overridable copy of the world's canonical scale), its
 * vertical block-space {@link OverworldBounds} window (the same window type the
 * overworld uses — it also fits the nether/end 0/256 windows), and the vanilla
 * dimension-type {@link WorldDim.Flags}. The {@code vanilla()} factory (and the
 * td parser) default every field to the pinned vanilla values, so the default
 * table reproduces the 1.21.1 triple exactly.
 * <p>
 * Note: the compact constructor validates non-null parts but <em>does not</em>
 * reject {@code coordinateScale <= 0} — canonical factories and
 * {@link #fromTd(WorldDim, String)} enforce a positive scale at their boundary,
 * while {@link DimensionWorlds#validate()} re-checks it as a health rule so a
 * deliberately corrupted instance stays representable for checkers.
 * <p>
 * 单世界不可变章节（p.1.8.19）：把一个 {@link WorldDim} 与其坐标尺度（世界规范尺度的
 * 用户可覆盖副本）、其纵向方块空间 {@link OverworldBounds} 窗口（与主世界同类型，同样适配
 * 下界/末地 0/256 窗口）及原版维度型 {@link WorldDim.Flags} 组合。<em>不</em>校验
 * {@code coordinateScale <= 0}——规范工厂与 {@link #fromTd(WorldDim, String)} 在各自边界
 * 强制正尺度，而 {@link DimensionWorlds#validate()} 会作为健康规则复检，使被刻意破坏的
 * 实例仍可表示以便检查器发现。
 *
 * @param worldDim         the world this section belongs to (never null)
 * @param coordinateScale  the effective coordinate scale (default = the world's canonical scale)
 * @param window           the vertical block-space window (never null)
 * @param flags            the dimension-type flags (never null)
 */
public record DimensionSection(WorldDim worldDim, double coordinateScale,
                               OverworldBounds window, WorldDim.Flags flags) {

    /** Vanilla nether window: {@code [minY=0, height=256, seaLevel=32, buildLimit=256]}. */
    static final OverworldBounds NETHER_WINDOW = new OverworldBounds(0, 256, 32, 256);
    /** Vanilla end window: {@code [minY=0, height=256, seaLevel=0, buildLimit=256]}. */
    static final OverworldBounds END_WINDOW = new OverworldBounds(0, 256, 0, 256);

    /**
     * Compact constructor validating non-null parts; coordinate scale is left
     * unchecked here on purpose (see the class Javadoc).
     */
    public DimensionSection {
        if (worldDim == null) {
            throw new IllegalArgumentException("worldDim must not be null");
        }
        if (window == null) {
            throw new IllegalArgumentException("window must not be null");
        }
        if (flags == null) {
            throw new IllegalArgumentException("flags must not be null");
        }
    }

    /**
     * The pinned vanilla section for the given world: canonical scale,
     * vanilla window and vanilla flags.
     */
    public static DimensionSection vanilla(WorldDim world) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        OverworldBounds win = switch (world) {
            case OVERWORLD -> OverworldBounds.vanilla();
            case THE_NETHER -> NETHER_WINDOW;
            case THE_END -> END_WINDOW;
        };
        return new DimensionSection(world, world.coordinateScale(), win, world.flags());
    }

    /**
     * The canonical td table-literal, exactly round-trippable through
     * {@link #fromTd(WorldDim, String)} — a flat {@code key=value} list covering
     * the coordinate scale, the window and all flags.
     * <p>
     * 规范 td 表字面量，可经 {@link #fromTd(WorldDim, String)} 精确往返——覆盖坐标尺度、
     * 窗口与全部标志的扁平 {@code key=value} 列表。
     */
    public String td() {
        return "[coordScale=" + num(coordinateScale)
                + ", minY=" + window.minY()
                + ", height=" + window.height()
                + ", seaLevel=" + window.seaLevel()
                + ", buildLimit=" + window.buildLimit()
                + ", natural=" + flags.natural()
                + ", ultrawarm=" + flags.ultrawarm()
                + ", hasSkylight=" + flags.hasSkylight()
                + ", hasCeiling=" + flags.hasCeiling()
                + ", bedWorks=" + flags.bedWorks()
                + ", respawnAnchorWorks=" + flags.respawnAnchorWorks()
                + ", piglinSafe=" + flags.piglinSafe()
                + ", hasRaids=" + flags.hasRaids()
                + "]";
    }

    /**
     * Parses a per-dimension td body for the given world, accepting partial /
     * empty bodies and defaulting every omitted field to the vanilla section.
     * Unknown keys, a non-positive coordinate scale, a non-boolean flag and any
     * window rejected by {@link OverworldBounds} validation are each refused via
     * IllegalArgumentException. The {@code plan} key is <em>not</em> understood
     * here — {@link DimensionWorlds} strips per-world plans before delegating.
     * <p>
     * 为给定世界解析逐维 td 主体，接受部分/空主体并把每个缺省字段回落到原版章节。未知键、
     * 非正坐标尺度、非布尔标志以及任何被 {@link OverworldBounds} 校验拒绝的窗口，都会经
     * IllegalArgumentException 拒绝。{@code plan} 键在<em>此</em>不被理解——
     * {@link DimensionWorlds} 在委派前会抽走逐世界规划。
     *
     * @param world the world dimension (never null)
     * @param body  the naked key=value body (may be blank/null -> vanilla section)
     */
    public static DimensionSection fromTd(WorldDim world, String body) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        if (body == null || body.trim().isEmpty()) {
            return vanilla(world);
        }
        DimensionSection base = vanilla(world);
        double coordScale = base.coordinateScale();
        int minY = base.window().minY();
        int height = base.window().height();
        int sea = base.window().seaLevel();
        int build = base.window().buildLimit();
        boolean natural = base.flags().natural();
        boolean ultrawarm = base.flags().ultrawarm();
        boolean hasSkylight = base.flags().hasSkylight();
        boolean hasCeiling = base.flags().hasCeiling();
        boolean bedWorks = base.flags().bedWorks();
        boolean respawn = base.flags().respawnAnchorWorks();
        boolean piglin = base.flags().piglinSafe();
        boolean hasRaids = base.flags().hasRaids();
        for (String item : DimensionWorlds.splitTopLevel(body)) {
            int eq = item.indexOf('=');
            if (eq < 0) {
                throw new IllegalArgumentException("malformed td key=value pair: " + item);
            }
            String key = item.substring(0, eq).trim();
            String val = item.substring(eq + 1).trim();
            switch (key) {
                case "coordScale" -> coordScale = parseScale(val);
                case "minY" -> minY = parseInt(key, val);
                case "height" -> height = parseInt(key, val);
                case "seaLevel" -> sea = parseInt(key, val);
                case "buildLimit" -> build = parseInt(key, val);
                case "natural" -> natural = parseBool(key, val);
                case "ultrawarm" -> ultrawarm = parseBool(key, val);
                case "hasSkylight" -> hasSkylight = parseBool(key, val);
                case "hasCeiling" -> hasCeiling = parseBool(key, val);
                case "bedWorks" -> bedWorks = parseBool(key, val);
                case "respawnAnchorWorks" -> respawn = parseBool(key, val);
                case "piglinSafe" -> piglin = parseBool(key, val);
                case "hasRaids" -> hasRaids = parseBool(key, val);
                default -> throw new IllegalArgumentException("unknown td key: " + key);
            }
        }
        OverworldBounds win = new OverworldBounds(minY, height, sea, build);
        return new DimensionSection(world, coordScale, win,
                new WorldDim.Flags(natural, ultrawarm, hasSkylight, hasCeiling,
                        bedWorks, respawn, piglin, hasRaids));
    }

    private static double parseScale(String val) {
        double d;
        try {
            d = Double.parseDouble(val);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("coordinate scale must be numeric: " + val);
        }
        if (Double.isNaN(d) || Double.isInfinite(d) || !(d > 0.0)) {
            throw new IllegalArgumentException("coordinate scale must be > 0: " + val);
        }
        return d;
    }

    private static int parseInt(String key, String val) {
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("td: " + key + " must be an int: " + val);
        }
    }

    private static boolean parseBool(String key, String val) {
        if ("true".equalsIgnoreCase(val)) {
            return true;
        }
        if ("false".equalsIgnoreCase(val)) {
            return false;
        }
        throw new IllegalArgumentException("td: " + key + " must be true/false: " + val);
    }

    /** Formats a double as a whole token when integral, else via toString. */
    static String num(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value) && Math.abs(value) < 1.0e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}