package io.toterra.subterra.optim.worldgen.pipeline.composite;

import java.util.Objects;

/**
 * The vanilla 2-D cubic-Hermite spline evaluator (p.1.8.26), mirroring MC 1.21.1's
 * {@code overworld/offset.json} &amp; {@code overworld/factor.json} climate splines.
 * Those vanilla files are nested trees {@code continents → erosion → ridges}; every
 * interior knot's {@code derivative} is {@code 0.0}, so the continents×erosion surface is
 * faithfully a <em>tensor-product cubic Hermite</em> in the two climate axes. This class
 * evaluates that surface:
 *
 * <pre>
 *   per axis:  Hermite value weights  h0 = 2t^3-3t^2+1, h1 = 1-h0 = -2t^3+3t^2
 *              Hermite slope weights  g0 = t^3-2t^2+t,  g1 = t^3-t^2
 *   V(x,y) = Σ_{a,b∈{0,1}} hX_a*hY_b*val[ia+a][ib+b]
 *            + gX_a*hY_b*derX[ia+a][ib+b]*hx     (x slope term)
 *            + hX_a*gY_b*derY[ia+a][ib+b]*hy     (y slope term)
 * </pre>
 *
 * (the {h,g} basis is the standard Hermite; the mixed {@code ∂²/∂x∂y} cross term is not
 * present in the vanilla data and is dropped). Outside the knot rectangle each axis
 * extends <em>linearly</em> at its end slope exactly like {@code CubicSpline$Multipoint}
 * / {@link SplineFn#linear-extend}; with the vanilla ``derivative 0.0'' knots that means
 * a flat clamp. Both axes locate their cell by deterministic binary search
 * ({@code O(log n)} + {@code O(log m)}), and the hot-path evaluation is allocation-free.
 * The value grid is defensive-copied so the evaluator is immutable.
 * <p>
 * 原生二维三次 Hermite 样条求值器（p.1.8.26），镜像 MC 1.21.1 的
 * {@code overworld/offset.json}、{@code overworld/factor.json} 气候样条。它们本是
 * {@code continents → erosion → ridges} 的嵌套树；每个内部结点的 {@code derivative}
 * 均为 {@code 0.0}，故 continents×erosion 表面可忠实地作为两气候轴上的<em>张量积三次
 * Hermite</em> 求值。类内公式见上（{h,g} 为标准 Hermite 基；原生数据不含混偏
 * {@code ∂²/∂x∂y} 项，故省略）。结点矩形之外每轴按端点斜率<em>线性外延</em>（与
 * {@code CubicSpline$Multipoint} / {@link SplineFn} 一致；配合原生 "derivative 0.0"
 * 即平面钳制）。双轴均以确定性二分定位单元（{@code O(log n)+O(log m)}），热路径零分配。
 * 值网格做防御性拷贝，保证求值器不可变。
 */
public final class Spline2D {

    private final double[] xLevels;
    private final double[] yLevels;
    private final double[][] value;
    private final double[][] derivX;
    private final double[][] derivY;
    private final int nx;
    private final int ny;

    /**
     * @param xLevels strictly increasing X-knot coordinates.
     * @param yLevels strictly increasing Y-knot coordinates.
     * @param value   {@code value[i][j]} at {@code (xLevels[i], yLevels[j])}; sized {@code nx×ny}.
     * @param derivX  {@code ∂V/∂x} at each knot (same shape); may be all zero.
     * @param derivY  {@code ∂V/∂y} at each knot (same shape); may be all zero.
     * @throws IllegalArgumentException on null / ragged / non-finite / non-increasing input,
     *                                  or fewer than two knots per axis.
     */
    public Spline2D(double[] xLevels, double[] yLevels, double[][] value,
                    double[][] derivX, double[][] derivY) {
        Objects.requireNonNull(xLevels, "xLevels");
        Objects.requireNonNull(yLevels, "yLevels");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(derivX, "derivX");
        Objects.requireNonNull(derivY, "derivY");
        int nX = xLevels.length;
        int nY = yLevels.length;
        if (nX < 2 || nY < 2) {
            throw new IllegalArgumentException("spline2d needs at least two knots per axis");
        }
        for (int i = 0; i < nX; i++) {
            requireFinite(xLevels[i], "xLevels", i);
            if (i > 0 && xLevels[i] <= xLevels[i - 1]) {
                throw new IllegalArgumentException("xLevels must be strictly increasing");
            }
        }
        for (int j = 0; j < nY; j++) {
            requireFinite(yLevels[j], "yLevels", j);
            if (j > 0 && yLevels[j] <= yLevels[j - 1]) {
                throw new IllegalArgumentException("yLevels must be strictly increasing");
            }
        }
        if (value.length != nX || derivX.length != nX || derivY.length != nX) {
            throw new IllegalArgumentException("value/derivX/derivY rows must match xLevels");
        }
        for (int i = 0; i < nX; i++) {
            if (value[i].length != nY || derivX[i].length != nY || derivY[i].length != nY) {
                throw new IllegalArgumentException("value/derivX/derivY columns must match yLevels");
            }
            for (int j = 0; j < nY; j++) {
                if (!finite(value[i][j]) || !finite(derivX[i][j]) || !finite(derivY[i][j])) {
                    throw new IllegalArgumentException("non-finite knot at (" + i + "," + j + ")");
                }
            }
        }
        this.xLevels = xLevels.clone();
        this.yLevels = yLevels.clone();
        this.value = clone(value);
        this.derivX = clone(derivX);
        this.derivY = clone(derivY);
        this.nx = nX;
        this.ny = nY;
    }

    /** Builds a 2-D spline with all-zero axis slopes (the vanilla offset/factor case). */
    public Spline2D(double[] xLevels, double[] yLevels, double[][] value) {
        this(xLevels, yLevels, value, zeros(value), zeros(value));
    }

    /** Evaluates at {@code (x, y)} (binary-search cell locate + O(1) Hermite, allocation-free). */
    public double eval(double x, double y) {
        double[] xl = xLevels;
        double[] yl = yLevels;
        int lastX = nx - 1;
        int lastY = ny - 1;

        int ia;
        boolean xLin;
        if (x <= xl[0]) {
            ia = 0;
            xLin = x < xl[0];
        } else if (x >= xl[lastX]) {
            ia = lastX - 1;
            xLin = true;
        } else {
            ia = greatestIndexAtOrBelow(xl, x);
            xLin = false;
        }
        int ib;
        boolean yLin;
        if (y <= yl[0]) {
            ib = 0;
            yLin = y < yl[0];
        } else if (y >= yl[lastY]) {
            ib = lastY - 1;
            yLin = true;
        } else {
            ib = greatestIndexAtOrBelow(yl, y);
            yLin = false;
        }

        double hx = xl[ia + 1] - xl[ia];
        double hy = yl[ib + 1] - yl[ib];
        double tx = (x - xl[ia]) / hx;
        double ty = (y - yl[ib]) / hy;

        // corner values and per-axis slopes
        double f00 = value[ia][ib];
        double f10 = value[ia + 1][ib];
        double f01 = value[ia][ib + 1];
        double f11 = value[ia + 1][ib + 1];
        double d00 = derivX[ia][ib];
        double d10 = derivX[ia + 1][ib];
        double d01 = derivX[ia][ib + 1];
        double d11 = derivX[ia + 1][ib + 1];
        double e00 = derivY[ia][ib];
        double e10 = derivY[ia + 1][ib];
        double e01 = derivY[ia][ib + 1];
        double e11 = derivY[ia + 1][ib + 1];

        // X-axis Hermite basis (h: value weights, g: slope weights)
        double h0x;
        double h1x;
        double g0x;
        double g1x;
        if (xLin) {
            h0x = 1.0;
            h1x = 0.0;
            g0x = tx;
            g1x = 0.0;
        } else {
            double t2 = tx * tx;
            double t3 = t2 * tx;
            h0x = 2.0 * t3 - 3.0 * t2 + 1.0;
            h1x = -2.0 * t3 + 3.0 * t2;
            g0x = t3 - 2.0 * t2 + tx;
            g1x = t3 - t2;
        }
        // Y-axis Hermite basis
        double h0y;
        double h1y;
        double g0y;
        double g1y;
        if (yLin) {
            h0y = 1.0;
            h1y = 0.0;
            g0y = ty;
            g1y = 0.0;
        } else {
            double t2 = ty * ty;
            double t3 = t2 * ty;
            h0y = 2.0 * t3 - 3.0 * t2 + 1.0;
            h1y = -2.0 * t3 + 3.0 * t2;
            g0y = t3 - 2.0 * t2 + ty;
            g1y = t3 - t2;
        }

        // tensor-product Hermite, dropping the (missing) mixed cross term
        double v = 0.0;
        v += h0x * h0y * f00 + h1x * h0y * f10 + h0x * h1y * f01 + h1x * h1y * f11;
        v += g0x * h0y * (d00 * hx) + g1x * h0y * (d10 * hx)
                + g0x * h1y * (d01 * hx) + g1x * h1y * (d11 * hx);
        v += h0x * g0y * (e00 * hy) + h1x * g0y * (e10 * hy)
                + h0x * g1y * (e01 * hy) + h1x * g1y * (e11 * hy);
        return v;
    }

    /** Rightmost index {@code i} with {@code loc[i] <= v}; callers keep {@code loc[0] <= v} {@code <= loc[last]}. */
    private static int greatestIndexAtOrBelow(double[] loc, double v) {
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

    private static double[][] clone(double[][] a) {
        double[][] out = new double[a.length][];
        for (int i = 0; i < a.length; i++) {
            out[i] = a[i].clone();
        }
        return out;
    }

    private static double[][] zeros(double[][] like) {
        double[][] z = new double[like.length][];
        for (int i = 0; i < like.length; i++) {
            z[i] = new double[like[i].length];
        }
        return z;
    }

    private static void requireFinite(double v, String name, int i) {
        if (!finite(v)) {
            throw new IllegalArgumentException(name + " must be finite at index " + i);
        }
    }

    private static boolean finite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }
}