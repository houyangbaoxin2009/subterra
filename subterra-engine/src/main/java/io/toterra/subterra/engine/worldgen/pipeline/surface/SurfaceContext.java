package io.toterra.subterra.engine.worldgen.pipeline.surface;

/**
 * Immutable snapshot of a single block position for surface-rule evaluation
 * (p.1.8.5, self-developed). Pure data: integer coordinates, an optional
 * biome tag and an externally-injected density value.
 * <p>
 * 表面规则求值的单个方块位置纯数据快照（p.1.8.5，自研）：整型坐标、可选群系标签与外部注入的密度值。
 *
 * @param x         block x-coordinate
 * @param y         block y-coordinate
 * @param z         block z-coordinate
 * @param biomeTag  biome tag (may be {@code null})
 * @param density   injected density value (default {@code 0})
 */
public record SurfaceContext(int x, int y, int z, String biomeTag, double density) {

    /** Position only; biomeTag defaults to {@code null}, density to {@code 0}. */
    public SurfaceContext(int x, int y, int z) {
        this(x, y, z, null, 0);
    }

    /** Position with a biome tag; density defaults to {@code 0}. */
    public SurfaceContext(int x, int y, int z, String biomeTag) {
        this(x, y, z, biomeTag, 0);
    }
}