package io.toterra.subterra.engine.worldgen.pipeline.dimworlds;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The three primary worlds (p.1.8.19): {@link #OVERWORLD}, {@link #THE_NETHER}
 * and {@link #THE_END}, each with its canonical vanilla 1.21.1 id and
 * coordinate scale, plus the vanilla dimension-type flags embedded as immutable
 * constants (the {@link Flags} each world {@code flags()} exposes). The
 * coordinate scale and flags are the <em>vanilla defaults</em> of the td table —
 * {@link DimensionSection} carries the user-overridable copies, so the canonical
 * values here are what {@code validate()} health-checks against. Pure,
 * deterministic; construction validates via IllegalArgumentException (known id,
 * strictly positive scale, non-null flags).
 * <p>
 * 三个主世界（p.1.8.19）：{@link #OVERWORLD}、{@link #THE_NETHER} 与 {@link #THE_END}，
 * 各带原生 1.21.1 id 与坐标尺度，并把原版维度型标志作为不可变常量内嵌（各世界
 * {@code flags()} 暴露的 {@link Flags}）。坐标尺度与标志是 td 表的<em>原版默认</em>——
 * {@link DimensionSection} 携带用户可覆盖副本，故这里的规范值正是
 * {@code validate()} 健康检查所比对的基线。纯粹、确定；构造以
 * IllegalArgumentException 校验（id 已知、坐标尺度严格为正、标志非空）。
 */
public enum WorldDim {

    /** Vanilla overworld: coordinate scale 1.0. */
    OVERWORLD("minecraft:overworld", 1.0,
            new Flags(true, false, true, false, true, false, false, true)),
    /** Vanilla nether: coordinate scale 8.0 (thin, hot, ceiling'd). */
    THE_NETHER("minecraft:the_nether", 8.0,
            new Flags(false, true, false, true, false, true, true, true)),
    /** Vanilla end: coordinate scale 1.0. */
    THE_END("minecraft:the_end", 1.0,
            new Flags(false, false, false, false, false, false, false, true));

    /** The three primary worlds, in canonical registration order. */
    public static final List<WorldDim> ALL = List.of(values());

    private static final Map<String, WorldDim> BY_ID;
    static {
        BY_ID = new LinkedHashMap<>();
        for (WorldDim w : values()) {
            BY_ID.put(w.id, w);
        }
    }

    private final String id;
    private final double coordinateScale;
    private final Flags flags;

    WorldDim(String id, double coordinateScale, Flags flags) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("dimension id must be non-blank");
        }
        if (!(coordinateScale > 0.0)) {
            throw new IllegalArgumentException(
                    "coordinate scale must be > 0: " + coordinateScale);
        }
        if (flags == null) {
            throw new IllegalArgumentException("flags must not be null");
        }
        this.id = id;
        this.coordinateScale = coordinateScale;
        this.flags = flags;
    }

    /** The dimension id, e.g. {@code "minecraft:overworld"}. */
    public String id() {
        return id;
    }

    /**
     * The canonical vanilla coordinate scale (1.0 overworld/end, 8.0 nether),
     * strictly positive by construction.
     */
    public double coordinateScale() {
        return coordinateScale;
    }

    /** The canonical vanilla dimension-type flags for this world. */
    public Flags flags() {
        return flags;
    }

    /**
     * Resolves a dimension id to a {@link WorldDim}, or {@code null} when
     * unknown — {@link EcoDim}-style lookup. The built-in three are always
     * resolvable.
     *
     * @param id the dimension id (e.g. {@code "minecraft:overworld"})
     * @return the matching world, or {@code null}
     */
    public static WorldDim of(String id) {
        if (id == null) {
            return null;
        }
        return BY_ID.get(id);
    }

    /**
     * The vanilla dimension-type flags (pinned from the 1.21.1
     * {@code dimension_type} resources): an immutable tuple of the boolean
     * properties that {@link DimensionSection} defaults per world.
     *
     * @param natural             whether rain/sky mobs &amp; natural spawning occur
     * @param ultrawarm           whether the dimension is hot (nether)
     * @param hasSkylight         whether skylight penetrates to the floor
     * @param hasCeiling          whether the dimension has a solid ceiling (nether)
     * @param bedWorks            whether beds can be used as spawn points
     * @param respawnAnchorWorks  whether respawn anchors function
     * @param piglinSafe          whether piglins are safe here (no zombification)
     * @param hasRaids            whether raid events may trigger
     */
    public record Flags(boolean natural, boolean ultrawarm, boolean hasSkylight,
                        boolean hasCeiling, boolean bedWorks, boolean respawnAnchorWorks,
                        boolean piglinSafe, boolean hasRaids) {
    }
}