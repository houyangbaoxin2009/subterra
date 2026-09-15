package io.toterra.subterra.engine.worldgen.pipeline.composite;

import java.util.Objects;

/**
 * The vanilla nested climate spline evaluator (p.1.8.27A), mirroring the 1.21.1
 * overworld climate splines exactly: each of {@code overworld/offset.json},
 * {@code overworld/factor.json} and {@code overworld/jaggedness.json} is a nested
 * tree {@code continents → erosion → ridge} whose innermost axis is either the raw
 * {@code overworld/ridges} field or the folded value
 *
 * <pre>
 *   folded = -3 * (-1/3 + | -2/3 + |ridge| |)  =  1 - 3*| |ridge| - 2/3 |        (ridges_folded.json)
 * </pre>
 *
 * The p.1.8.26 {@code Spline2D} reduction (superseded and disposed of in p.1.8.28)
 * collapsed that innermost ridge axis at a
 * fixed folded = 0; this class instead samples the real {@code ridges()} field at
 * the surface point and applies the ridge axis as the innermost 1-D cubic Hermite,
 * so mountain shading / land-sea shape follow the actual ridge warp. Evaluation is
 * a 2-D Hermite over (continents, erosion) whose four corner values are each a 1-D
 * Hermite over the ridge axis (knot values may themselves be nested ridge splines —
 * vanilla nests a folded spline inside the offset {@code erosion=0.45/0.55} cells and
 * a raw-ridge spline inside the jaggedness top knot). All interior continents /
 * erosion knot derivatives are {@code 0.0} in vanilla, so those axes reduce to the
 * tensor-product value Hermite (identical algebra to the former {@code Spline2D}); the ridge
 * axis keeps its real knot derivatives. Outside the knot ranges each axis extends
 * linearly at its end slope exactly like {@code CubicSpline$Multipoint} /
 * {@link SplineFn} (zero slope ⇒ flat clamp). Knot location is a deterministic binary
 * search (no {@code O(n^2)}); the hot-path evaluation is allocation-free. The data
 * tables are private static final and never mutated, so the evaluator is immutable.
 *
 * <p>原生嵌套气候样条求值器（p.1.8.27A），精确镜像 1.21.1 主世界气候样条：
 * {@code overworld/offset.json}、{@code overworld/factor.json}、
 * {@code overworld/jaggedness.json} 均为 {@code continents → erosion → ridge} 的嵌套
 * 树，其最内轴是原始 {@code overworld/ridges} 场或折叠值（{@code ridges_folded.json}，
 * 公式见上）。p.1.8.26 的 {@code Spline2D} 归约（已在 p.1.8.28 处置删除）将该最内
 * ridge 轴在固定 folded=0 处坍缩；
 * 本类改为在表面点采样真实的 {@code ridges()} 场，并把 ridge 轴作为最内层一维三次
 * Hermite 应用，使山峰明暗与海陆形态跟随真实 ridge 扭曲。求值为 (continents, erosion)
 * 上的二维 Hermite，四个角值各自是 ridge 轴上的一维 Hermite（结点值本身可为嵌套
 * ridge 样条——原生在 offset 的 {@code erosion=0.45/0.55} 单元内嵌套了折叠样条，在
 * jaggedness 的顶部结点内嵌套了原始 ridge 样条）。原生 continents/erosion 轴的所有
 * 内部结点导数为 {@code 0.0}，故这两轴退化为张量积值 Hermite（与旧 {@code Spline2D}
 * 代数一致）；ridge 轴保留真实结点导数。结点范围之外每轴以端点斜率线性外延（与
 * {@code CubicSpline$Multipoint} / {@link SplineFn} 一致；零斜率即平面钳制）。结点
 * 定位用确定性二分（绝非 {@code O(n^2)}）；热路径零分配。数据表为私有静态 final
 * 且从不被修改，故求值器不可变。
 */
public final class ClimateSpline {

    /** A ridge-axis knot node: a scalar, or a 1-D Hermite spline whose knot values
     * may be plain scalars or further nested ridge splines. Immutable. */
    static final class Node {
        final boolean folded; // coordinate axis: true = ridges_folded, false = raw ridges
        final float[] loc;    // strictly increasing knot coordinates (float32, as MC); null ⇒ scalar node
        final float[] val;    // knot values (float32; entry unused when sub[k] != null)
        final float[] der;    // knot derivatives (float32)
        final Node[] sub;     // knot sub-nodes; null entry ⇒ scalar value val[k]

        Node(boolean folded, double[] loc, double[] val, double[] der, Node[] sub) {
            this.folded = folded;
            this.loc = toF32(loc);
            this.val = toF32(val);
            this.der = toF32(der);
            this.sub = sub;
        }
    }

    /** {@code null}-safe cast of a knot table to intel float32 ({@code CubicSpline` float nodes). */
    private static float[] toF32(double[] a) {
        if (a == null) {
            return null;
        }
        float[] r = new float[a.length];
        for (int i = 0; i < a.length; i++) {
            r[i] = (float) a[i];
        }
        return r;
    }

    // ------------------------------------------------------------------
    //  data-table builders (private; the tables are static final literals)
    // ------------------------------------------------------------------

    /** A scalar ridge node (no ridge dependence). */
    private static Node scalar(double v) {
        return new Node(false, null, new double[]{v}, null, null);
    }

    /** Folded 2-point spline {(-1, v1, d1), (1, v2, d2)}. */
    private static Node f2(double v1, double d1, double v2, double d2) {
        return new Node(true, new double[]{-1.0, 1.0}, new double[]{v1, v2},
                new double[]{d1, d2}, null);
    }

    /** Folded 3-point spline {(-1, v1, 0), (0, v2, d2), (1, v3, d3)}. */
    private static Node f3(double v1, double v2, double d2, double v3, double d3) {
        return new Node(true, new double[]{-1.0, 0.0, 1.0}, new double[]{v1, v2, v3},
                new double[]{0.0, d2, d3}, null);
    }

    /** Folded 5-point spline over the fixed locations {-1, -0.4, 0, 0.4, 1}. */
    private static Node f5(double v1, double d1, double v2, double d2,
                           double v3, double d3, double v4, double d4,
                           double v5, double d5) {
        return new Node(true, new double[]{-1.0, -0.4, 0.0, 0.4, 1.0},
                new double[]{v1, v2, v3, v4, v5}, new double[]{d1, d2, d3, d4, d5}, null);
    }

    /** The offset {@code erosion=-0.4} 6-point folded plateau. */
    private static Node f6() {
        return new Node(true,
                new double[]{-1.0, -0.75, -0.65, 0.5954547, 0.6054547, 1.0},
                new double[]{-0.2222, -0.2222, 0.0, 2.9802322E-8, 2.9802322E-8, 0.100000024},
                new double[]{0.0, 0.0, 0.0, 0.0, 0.2534563, 0.2534563}, null);
    }

    /** Raw-ridge 2-point spline with zero derivatives {(l1, v1), (l2, v2)}. */
    private static Node r2(double l1, double v1, double l2, double v2) {
        return new Node(false, new double[]{l1, l2}, new double[]{v1, v2},
                new double[]{0.0, 0.0}, null);
    }

    /** Folded 3-point spline with a nested node at -0.4: {(-1, v1, 0), (-0.4, sub, 0), (0, v3, 0)}. */
    private static Node f3sub(double v1, Node sub, double v3) {
        return new Node(true, new double[]{-1.0, -0.4, 0.0}, new double[]{v1, 0.0, v3},
                new double[]{0.0, 0.0, 0.0}, new Node[]{null, sub, null});
    }

    /** Folded 2-point spline with a nested node at the second knot. */
    private static Node f2sub(double l1, double v1, double l2, Node sub) {
        return new Node(true, new double[]{l1, l2}, new double[]{v1, 0.0},
                new double[]{0.0, 0.0}, new Node[]{null, sub});
    }

    /** Folded 2-point spline with a nested node at the first knot. */
    private static Node f2subFirst(double l1, Node sub, double l2, double v2) {
        return new Node(true, new double[]{l1, l2}, new double[]{0.0, v2},
                new double[]{0.0, 0.0}, new Node[]{sub, null});
    }

    /** The jaggedness folded spline {(0.19999999, 0), (0.44999996, 0), (1, high, 0)}. */
    private static Node jagNode(Node high) {
        return new Node(true, new double[]{0.19999999, 0.44999996, 1.0},
                new double[]{0.0, 0.0, 0.0}, new double[]{0.0, 0.0, 0.0},
                new Node[]{null, null, high});
    }

    /** A shared 1-knot erosion set for the scalar continents knots. */
    private static final double[] ONE_E = {0.0};

    // ==================================================================
    //  p.1.8.27A transcriptions (verified against the shipped 1.21.1
    //  client jar data/minecraft/worldgen/density_function/overworld/*).
    // ==================================================================

    /** The vanilla offset spline (overworld/offset.json, blend_alpha = 1). */
    public static final ClimateSpline OFFSET = new ClimateSpline(
            new double[]{-1.1, -1.02, -0.51, -0.44, -0.18, -0.16, -0.15, -0.1, 0.25, 1.0},
            new double[][]{
                    ONE_E, ONE_E, ONE_E, ONE_E, ONE_E,
                    {-0.85, -0.7, -0.4, -0.35, -0.1, 0.2, 0.7},
                    {-0.85, -0.7, -0.4, -0.35, -0.1, 0.2, 0.7},
                    {-0.85, -0.7, -0.4, -0.35, -0.1, 0.2, 0.7},
                    {-0.85, -0.7, -0.4, -0.35, -0.1, 0.2, 0.4, 0.45, 0.55, 0.58, 0.7},
                    {-0.85, -0.7, -0.4, -0.35, -0.1, 0.2, 0.4, 0.45, 0.55, 0.58, 0.7},
            },
            new Node[][]{
                    {scalar(0.044)},
                    {scalar(-0.2222)},
                    {scalar(-0.2222)},
                    {scalar(-0.12)},
                    {scalar(-0.12)},
                    { // continents = -0.16
                            f2(-0.08880186, 0.38940096, 0.69000006, 0.38940096),        // e -0.85
                            f2(-0.115760356, 0.37788022, 0.6400001, 0.37788022),        // e -0.7
                            f6(),                                                        // e -0.4
                            f5(-0.3, 0.5, 0.05, 0.0, 0.05, 0.0, 0.05, 0.0, 0.060000002, 0.007000001), // e -0.35
                            f5(-0.15, 0.5, 0.0, 0.0, 0.0, 0.0, 0.05, 0.1, 0.060000002, 0.007000001),    // e -0.1
                            f5(-0.15, 0.5, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),                    // e 0.2
                            f5(-0.02, 0.0, -0.03, 0.0, -0.03, 0.0, 0.0, 0.06, 0.0, 0.0),                // e 0.7
                    },
                    { // continents = -0.15 (identical to -0.16 in vanilla)
                            f2(-0.08880186, 0.38940096, 0.69000006, 0.38940096),        // e -0.85
                            f2(-0.115760356, 0.37788022, 0.6400001, 0.37788022),        // e -0.7
                            f6(),                                                        // e -0.4
                            f5(-0.3, 0.5, 0.05, 0.0, 0.05, 0.0, 0.05, 0.0, 0.060000002, 0.007000001), // e -0.35
                            f5(-0.15, 0.5, 0.0, 0.0, 0.0, 0.0, 0.05, 0.1, 0.060000002, 0.007000001),    // e -0.1
                            f5(-0.15, 0.5, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),                    // e 0.2
                            f5(-0.02, 0.0, -0.03, 0.0, -0.03, 0.0, 0.0, 0.06, 0.0, 0.0),                // e 0.7
                    },
                    { // continents = -0.1
                            f2(-0.08880186, 0.38940096, 0.69000006, 0.38940096),
                            f2(-0.115760356, 0.37788022, 0.6400001, 0.37788022),
                            f6(),
                            f5(-0.25, 0.5, 0.05, 0.0, 0.05, 0.0, 0.05, 0.0, 0.060000002, 0.007000001), // e -0.35
                            f5(-0.1, 0.5, 0.001, 0.01, 0.003, 0.01, 0.05, 0.094000004, 0.060000002, 0.007000001), // e -0.1
                            f5(-0.1, 0.5, 0.01, 0.0, 0.01, 0.0, 0.03, 0.04, 0.1, 0.049),              // e 0.2
                            f5(-0.02, 0.0, -0.03, 0.0, -0.03, 0.0, 0.03, 0.12, 0.1, 0.049),           // e 0.7
                    },
                    { // continents = 0.25
                            f3(0.20235021, 0.7161751, 0.5138249, 1.23, 0.5138249),                     // e -0.85
                            f3(0.2, 0.44682026, 0.43317974, 0.88, 0.43317974),                          // e -0.7
                            f3(0.2, 0.30829495, 0.3917051, 0.70000005, 0.3917051),                      // e -0.4
                            f5(-0.25, 0.5, 0.35, 0.0, 0.35, 0.0, 0.35, 0.0, 0.42000002, 0.049000014),  // e -0.35
                            f5(-0.1, 0.5, 0.0069999998, 0.07, 0.021, 0.07, 0.35, 0.658, 0.42000002, 0.049000014), // e -0.1
                            f5(-0.1, 0.5, 0.01, 0.0, 0.01, 0.0, 0.03, 0.04, 0.1, 0.049),               // e 0.2
                            f5(-0.1, 0.5, 0.01, 0.0, 0.01, 0.0, 0.03, 0.04, 0.1, 0.049),               // e 0.4
                            f3sub(-0.1, f5(-0.1, 0.5, 0.01, 0.0, 0.01, 0.0, 0.03, 0.04, 0.1, 0.049), 0.17), // e 0.45
                            f3sub(-0.1, f5(-0.1, 0.5, 0.01, 0.0, 0.01, 0.0, 0.03, 0.04, 0.1, 0.049), 0.17), // e 0.55
                            f5(-0.1, 0.5, 0.01, 0.0, 0.01, 0.0, 0.03, 0.04, 0.1, 0.049),               // e 0.58
                            f5(-0.02, 0.0, -0.03, 0.0, -0.03, 0.0, 0.03, 0.12, 0.1, 0.049),            // e 0.7
                    },
                    { // continents = 1.0
                            f3(0.34792626, 0.9239631, 0.5760369, 1.5, 0.5760369),                       // e -0.85
                            f3(0.2, 0.5391705, 0.4608295, 1.0, 0.4608295),                              // e -0.7
                            f3(0.2, 0.5391705, 0.4608295, 1.0, 0.4608295),                              // e -0.4
                            f5(-0.2, 0.5, 0.5, 0.0, 0.5, 0.0, 0.5, 0.0, 0.6, 0.070000015),             // e -0.35
                            f5(-0.05, 0.5, 0.01, 0.099999994, 0.03, 0.099999994, 0.5, 0.94, 0.6, 0.070000015), // e -0.1
                            f5(-0.05, 0.5, 0.01, 0.0, 0.01, 0.0, 0.03, 0.04, 0.1, 0.049),              // e 0.2
                            f5(-0.05, 0.5, 0.01, 0.0, 0.01, 0.0, 0.03, 0.04, 0.1, 0.049),              // e 0.4
                            f3sub(-0.05, f5(-0.05, 0.5, 0.01, 0.0, 0.01, 0.0, 0.03, 0.04, 0.1, 0.049), 0.17), // e 0.45
                            f3sub(-0.05, f5(-0.05, 0.5, 0.01, 0.0, 0.01, 0.0, 0.03, 0.04, 0.1, 0.049), 0.17), // e 0.55
                            f5(-0.05, 0.5, 0.01, 0.0, 0.01, 0.0, 0.03, 0.04, 0.1, 0.049),              // e 0.58
                            f5(-0.02, 0.015, 0.01, 0.0, 0.01, 0.0, 0.03, 0.04, 0.1, 0.049),            // e 0.7
                    },
            });

    /** The vanilla factor spline (overworld/factor.json, blend_alpha = 1). */
    public static final ClimateSpline FACTOR = new ClimateSpline(
            new double[]{-0.19, -0.15, -0.1, 0.03, 0.06},
            new double[][]{
                    ONE_E,
                    {-0.6, -0.5, -0.35, -0.25, -0.1, 0.03, 0.35, 0.45, 0.55, 0.62},
                    {-0.6, -0.5, -0.35, -0.25, -0.1, 0.03, 0.35, 0.45, 0.55, 0.62},
                    {-0.6, -0.5, -0.35, -0.25, -0.1, 0.03, 0.35, 0.45, 0.55, 0.62},
                    {-0.6, -0.5, -0.35, -0.25, -0.1, 0.03, 0.05, 0.4, 0.45, 0.55, 0.58},
            },
            new Node[][]{
                    {scalar(3.95)},
                    { // continents = -0.15
                            r2(-0.2, 6.3, 0.2, 6.25),                                        // e -0.6
                            r2(-0.05, 6.3, 0.05, 2.67),                                       // e -0.5
                            r2(-0.2, 6.3, 0.2, 6.25),                                         // e -0.35
                            r2(-0.2, 6.3, 0.2, 6.25),                                         // e -0.25
                            r2(-0.05, 2.67, 0.05, 6.3),                                       // e -0.1
                            r2(-0.2, 6.3, 0.2, 6.25),                                         // e 0.03
                            scalar(6.25),                                                     // e 0.35
                            f2sub(-0.9, 6.25, -0.69, r2(0.0, 6.25, 0.1, 0.625)),              // e 0.45
                            f2sub(-0.9, 6.25, -0.69, r2(0.0, 6.25, 0.1, 0.625)),              // e 0.55
                            scalar(6.25),                                                     // e 0.62
                    },
                    { // continents = -0.1
                            r2(-0.2, 6.3, 0.2, 5.47),
                            r2(-0.05, 6.3, 0.05, 2.67),
                            r2(-0.2, 6.3, 0.2, 5.47),
                            r2(-0.2, 6.3, 0.2, 5.47),
                            r2(-0.05, 2.67, 0.05, 6.3),
                            r2(-0.2, 6.3, 0.2, 5.47),
                            scalar(5.47),
                            f2sub(-0.9, 5.47, -0.69, r2(0.0, 5.47, 0.1, 0.625)),
                            f2sub(-0.9, 5.47, -0.69, r2(0.0, 5.47, 0.1, 0.625)),
                            scalar(5.47),
                    },
                    { // continents = 0.03
                            r2(-0.2, 6.3, 0.2, 5.08),
                            r2(-0.05, 6.3, 0.05, 2.67),
                            r2(-0.2, 6.3, 0.2, 5.08),
                            r2(-0.2, 6.3, 0.2, 5.08),
                            r2(-0.05, 2.67, 0.05, 6.3),
                            r2(-0.2, 6.3, 0.2, 5.08),
                            scalar(5.08),
                            f2sub(-0.9, 5.08, -0.69, r2(0.0, 5.08, 0.1, 0.625)),
                            f2sub(-0.9, 5.08, -0.69, r2(0.0, 5.08, 0.1, 0.625)),
                            scalar(5.08),
                    },
                    { // continents = 0.06
                            r2(-0.2, 6.3, 0.2, 4.69),                                        // e -0.6
                            r2(-0.05, 6.3, 0.05, 2.67),                                       // e -0.5
                            r2(-0.2, 6.3, 0.2, 4.69),                                         // e -0.35
                            r2(-0.2, 6.3, 0.2, 4.69),                                         // e -0.25
                            r2(-0.05, 2.67, 0.05, 6.3),                                       // e -0.1
                            r2(-0.2, 6.3, 0.2, 4.69),                                         // e 0.03
                            f2subFirst(0.45, r2(-0.2, 6.3, 0.2, 4.69), 0.7, 1.56),            // e 0.05
                            f2subFirst(0.45, r2(-0.2, 6.3, 0.2, 4.69), 0.7, 1.56),            // e 0.4
                            f2subFirst(-0.7, r2(-0.2, 6.3, 0.2, 4.69), -0.15, 1.37),          // e 0.45
                            f2subFirst(-0.7, r2(-0.2, 6.3, 0.2, 4.69), -0.15, 1.37),          // e 0.55
                            scalar(4.69),                                                     // e 0.58
                    },
            });

    /** The vanilla jaggedness spline (overworld/jaggedness.json, blend_alpha = 1). */
    public static final ClimateSpline JAGGEDNESS = new ClimateSpline(
            new double[]{-0.11, 0.03},
            new double[][]{
                    ONE_E,
                    {-1.0, -0.78, -0.5775, -0.375},
            },
            new Node[][]{
                    {scalar(0.0)},
                    { // continents = 0.03
                            jagNode(r2(-0.01, 0.63, 0.01, 0.3)),    // e -1.0
                            jagNode(r2(-0.01, 0.315, 0.01, 0.15)),  // e -0.78
                            jagNode(r2(-0.01, 0.315, 0.01, 0.15)),  // e -0.5775
                            scalar(0.0),                             // e -0.375
                    },
            });

    // ==================================================================
    //  evaluator
    // ==================================================================

    private final double[] c;     // continents knot coordinates (kept for the validation / metadata)
    private final double[][] e;   // e[i] = erosion knots for continents knot i
    private final Node[][] n;     // n[i][j] = ridge node at (c[i], e[i][j])
    // float32 mirrors of the axis knots, used by the bit-exact float evaluator below.
    private final float[] cF;
    private final float[][] eF;

    /**
     * @param cLevels strictly increasing continents knots.
     * @param eLevels {@code eLevels[i]} strictly increasing erosion knots for the
     *                {@code i}-th continents knot (a single dummy knot for scalar knots).
     * @param nodes   {@code nodes[i][j]} sized like {@code eLevels[i]}.
     * @throws IllegalArgumentException on null / ragged / non-finite / non-increasing input.
     */
    public ClimateSpline(double[] cLevels, double[][] eLevels, Node[][] nodes) {
        Objects.requireNonNull(cLevels, "cLevels");
        Objects.requireNonNull(eLevels, "eLevels");
        Objects.requireNonNull(nodes, "nodes");
        if (cLevels.length < 2) {
            throw new IllegalArgumentException("climate spline needs at least two continents knots");
        }
        for (int i = 0; i < cLevels.length; i++) {
            requireFinite(cLevels[i], "cLevels", i);
            if (i > 0 && cLevels[i] <= cLevels[i - 1]) {
                throw new IllegalArgumentException("cLevels must be strictly increasing");
            }
        }
        if (eLevels.length != cLevels.length || nodes.length != cLevels.length) {
            throw new IllegalArgumentException("eLevels/nodes rows must match cLevels");
        }
        for (int i = 0; i < eLevels.length; i++) {
            double[] el = eLevels[i];
            Node[] row = nodes[i];
            if (el == null || row == null || el.length < 1 || el.length != row.length) {
                throw new IllegalArgumentException("ragged erosion/nodes row at continents knot " + i);
            }
            for (int j = 0; j < el.length; j++) {
                requireFinite(el[j], "eLevels", j);
                if (j > 0 && el[j] <= el[j - 1]) {
                    throw new IllegalArgumentException("eLevels must be strictly increasing");
                }
                if (row[j] == null) {
                    throw new IllegalArgumentException("null node at (" + i + "," + j + ")");
                }
            }
        }
        this.c = cLevels.clone();
        this.e = new double[eLevels.length][];
        this.n = new Node[nodes.length][];
        this.cF = toF32(cLevels);
        this.eF = new float[eLevels.length][];
        for (int i = 0; i < eLevels.length; i++) {
            this.e[i] = eLevels[i].clone();
            this.n[i] = nodes[i].clone();
            this.eF[i] = toF32(eLevels[i]);
        }
    }

    /**
     * Evaluates the nested spline at a climate point.
     *
     * @param continents the router's {@code continents()} value at the surface point.
     * @param erosion    the router's {@code erosion()} value at the surface point.
     * @param ridge      the router's {@code ridges()} value at the surface point
     *                   (raw ridge; the folded coordinate is derived internally).
     */
    public double eval(double continents, double erosion, double ridge) {
        float cc = (float) continents;
        float ee = (float) erosion;
        // ridges_folded is a separate double Mapped density function; the spline shows it
        // down to float32 when it becomes a CubicSpline coordinate (net.minecraft.util.CubicSpline
        // evaluates entirely in float, then DensityFunctions$Spline.compute f2d-promotes the result).
        float folded = (float) (1.0 - 3.0 * Math.abs(Math.abs(ridge) - 2.0 / 3.0));
        float rr = (float) ridge;
        return (double) continentsEval(cc, ee, rr, folded);
    }

    /** Continuum axis (continents): a float CubicSpline$Multipoint over {@code cc}. */
    private float continentsEval(float cc, float ee, float rr, float folded) {
        float[] cl = cF;
        int lastC = cl.length - 1;
        int ic = greatestIndexAtOrBelow(cl, cc);
        if (ic < 0) {
            return lineExtend(cc, cl[0], erosionEval(0, ee, rr, folded), 0.0f);
        }
        if (ic >= lastC) {
            return lineExtend(cc, cl[lastC], erosionEval(lastC, ee, rr, folded), 0.0f);
        }
        float v0 = erosionEval(ic, ee, rr, folded);
        float v1 = erosionEval(ic + 1, ee, rr, folded);
        // continents/erosion axes have all-zero knot derivatives in every vanilla spline,
        // so the Multipoint correction passes d0=d1=0 (bit-identical float algebra).
        return multipointRound(cc, ic, cl, v0, v1, 0.0f, 0.0f);
    }

    /** Evaluates the erosion spline of continents knot {@code ci} (also a float Multipoint). */
    private float erosionEval(int ci, float ee, float rr, float folded) {
        float[] el = eF[ci];
        Node[] row = n[ci];
        int lastE = el.length - 1;
        int ie = greatestIndexAtOrBelow(el, ee);
        if (ie < 0) {
            return lineExtend(ee, el[0], nodeEval(row[0], rr, folded), 0.0f);
        }
        if (ie >= lastE) {
            return lineExtend(ee, el[lastE], nodeEval(row[lastE], rr, folded), 0.0f);
        }
        float v0 = nodeEval(row[ie], rr, folded);
        float v1 = nodeEval(row[ie + 1], rr, folded);
        return multipointRound(ee, ie, el, v0, v1, 0.0f, 0.0f);
    }

    /**
     * Evaluates a ridge node: a float CubicSpline (the innermost axis with the real knot
     * derivatives), identical to MC's {@code CubicSpline$Multipoint.apply}.
     */
    private static float nodeEval(Node node, float ridge, float folded) {
        float[] loc = node.loc;
        if (loc == null) {
            return node.val[0];
        }
        float x = node.folded ? folded : ridge;
        int last = loc.length - 1;
        int k = greatestIndexAtOrBelow(loc, x);
        if (k < 0) {
            return lineExtend(x, loc[0], knotValue(node, 0, ridge, folded), node.der[0]);
        }
        if (k >= last) {
            return lineExtend(x, loc[last], knotValue(node, last, ridge, folded), node.der[last]);
        }
        float v0 = knotValue(node, k, ridge, folded);
        float v1 = knotValue(node, k + 1, ridge, folded);
        return multipointRound(x, k, loc, v0, v1, node.der[k], node.der[k + 1]);
    }

    /** The value at ridge knot {@code k}: a nested node when present, else the scalar. */
    private static float knotValue(Node node, int k, float ridge, float folded) {
        if (node.sub != null && node.sub[k] != null) {
            return nodeEval(node.sub[k], ridge, folded);
        }
        return node.val[k];
    }

    /** Two-sample Multipoint Hermite, exactly {@code CubicSpline$Multipoint.apply} in float32:
     * {@code lerp(t0,v0,v1) + t*(1-t)*lerp(t, f14, f15)} with {@code f14=d0*Δ-(v1-v0)},
     * {@code f15=(v1-v0)-d1*Δ} (zero derivative ⇒ pure float lerp). All ops float; bit-faithful. */
    private static float multipointRound(float x, int k, float[] loc, float v0, float v1, float d0, float d1) {
        float a = loc[k];
        float b = loc[k + 1];
        float delta = b - a;
        float t = (x - a) / delta;
        float f14 = d0 * delta - (v1 - v0);
        float f15 = -d1 * delta + (v1 - v0);
        float base = v0 + (v1 - v0) * t;
        float h = f14 + (f15 - f14) * t;
        return base + t * (1.0f - t) * h;
    }

    /** Linear extension outside the knot range, matching MC {@code CubicSpline$Multipoint.linearExtend}:
     * flat (the value) when the endpoint slope is 0, else {@code value + der*(x-location)}. */
    private static float lineExtend(float x, float location, float value, float der) {
        return der == 0.0f ? value : value + der * (x - location);
    }

    /** Rightmost index {@code i} with {@code loc[i] <= v} (float bisection, as {@code Mth.binarySearch}). */
    private static int greatestIndexAtOrBelow(float[] loc, float v) {
        int lo = 0;
        int hi = loc.length - 1;
        int ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (loc[mid] <= v) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }

    private static void requireFinite(double v, String name, int i) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            throw new IllegalArgumentException(name + " must be finite at index " + i);
        }
    }
}
