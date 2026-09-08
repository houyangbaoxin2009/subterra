package io.toterra.subterra.engine.worldgen.pipeline.composite;

import java.util.Objects;

/**
 * The vanilla 1-D cubic Hermite spline evaluator (p.1.8.14), mirroring MC 1.21.1's
 * {@code net.minecraft.util.CubicSpline$Multipoint.apply} exactly in structure and
 * operation order. The kernel is the "slope" cubic with a Hermite-style derivative
 * dampening (NOT the plain linear interp): for a segment {@code [loc_i, loc_{i+1}]}
 * with end values {@code v0,v1} and end derivatives {@code d0,d1},
 *
 * <pre>
 *   delta = loc_{i+1} - loc_i
 *   t     = (x - loc_i) / delta
 *   a     = d0 * delta - (v1 - v0)
 *   b     = -d1 * delta + (v1 - v0)
 *   result = v0 + (v1 - v0) * t + t * (1 - t) * (a + (b - a) * t)
 * </pre>
 *
 * At the segment ends the value reproduces the knot exactly and the derivative w.r.t.
 * {@code x} equals the supplied derivative. Outside the knot range the spline is
 * linearly extended using the end derivative ({@code value + deriv * (x - loc)}),
 * exactly like {@code CubicSpline$Multipoint.linearExtend}. A deterministic binary
 * search (never {@code O(n^2)}) locates the interval; the hot path is allocation-free.
 * <p>
 * Note: MC evaluates this basis in {@code float} (the {@code CubicSpline} flattens its
 * {@code locations}/{@code derivatives} to {@code float[]} and uses {@code Mth.lerp} on
 * {@code float}); Subterra evaluates in {@code double} for deterministic, allocation-free
 * accuracy. The operation order is otherwise identical so the shape (and, to
 * float-precision, the value) matches vanilla.
 *
 * <p>原生一维三次 Hermite 样条求值器（p.1.8.14），在结构与运算顺序上精确镜像 MC
 * 1.21.1 的 {@code CubicSpline$Multipoint.apply}。核是带 Hermite 式导数阻尼的
 * "斜率"三次（非纯线性插值）：对段 {@code [loc_i, loc_{i+1}]} 有端点值
 * {@code v0,v1} 与端点导数 {@code d0,d1}，公式见上。
 * 段端点处数值精确复现结点值，对 {@code x} 的导数等于所给导数。结点范围之外以
 * 端点斜率线性外延（{@code value + deriv * (x - loc)}），与
 * {@code CubicSpline$Multipoint.linearExtend} 一致。用确定性二分（绝非
 * {@code O(n^2)}）定位区间；热路径零分配。注意：MC 以 {@code float}（{@code CubicSpline}
 * 把 {@code locations}/{@code derivatives} 压成 {@code float[]}，并经 {@code Mth.lerp}
 * 以 {@code float} 运算）求值本基；Subterra 以 {@code double} 求值以确保确定性、零分配
 * 的精度。运算顺序其余相同，故形状与原生一致（在浮点精度内数值一致）。
 */
public final class SplineFn {

    private final double[] location;
    private final double[] value;
    private final double[] derivative;

    /**
     * @param location   strictly increasing knot coordinates.
     * @param value      value at each knot.
     * @param derivative first derivative (slope) at each knot.
     * @throws IllegalArgumentException if arrays are null / mismatched / fewer than two
     *                                  points / non-finite / locations not strictly increasing.
     */
    public SplineFn(double[] location, double[] value, double[] derivative) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(derivative, "derivative");
        int n = location.length;
        if (value.length != n || derivative.length != n) {
            throw new IllegalArgumentException("spline arrays must be equal length");
        }
        for (int i = 0; i < n; i++) {
            if (!finite(location[i]) || !finite(value[i]) || !finite(derivative[i])) {
                throw new IllegalArgumentException("spline values must be finite at index " + i);
            }
            if (i > 0 && location[i] <= location[i - 1]) {
                throw new IllegalArgumentException("spline locations must be strictly increasing");
            }
        }
        if (n < 2) {
            throw new IllegalArgumentException("spline needs at least two points");
        }
        // defensive copies keep the evaluator immutable regardless of caller mutation
        this.location = location.clone();
        this.value = value.clone();
        this.derivative = derivative.clone();
    }

    /** Point list form ({@code x, value, derivative}). */
    public record Point(double location, double value, double derivative) {
    }

    /** Builds a spline from an ordered point list (locations must be strictly increasing). */
    public static SplineFn of(Point... points) {
        if (points == null || points.length < 2) {
            throw new IllegalArgumentException("need at least two points");
        }
        double[] l = new double[points.length];
        double[] v = new double[points.length];
        double[] d = new double[points.length];
        for (int i = 0; i < points.length; i++) {
            Point p = points[i];
            l[i] = p.location();
            v[i] = p.value();
            d[i] = p.derivative();
        }
        return new SplineFn(l, v, d);
    }

    /** Evaluates at {@code x} (O(log n) binary search, allocation-free). */
    public double eval(double x) {
        return eval(location, value, derivative, x);
    }

    /**
     * The kernel, exactly the {@code CubicSpline$Multipoint.apply} algebra above.
     * Left extension uses index 0, right extension uses {@code n - 1}.
     */
    public static double eval(double[] loc, double[] val, double[] der, double x) {
        int n = loc.length;
        int last = n - 1;
        if (x < loc[0]) {
            return linearExtend(x, loc, val[0], der, 0);
        }
        if (x >= loc[last]) {
            return linearExtend(x, loc, val[last], der, last);
        }
        int i = greatestIndexAtOrBelow(loc, x); // in [0, last-1]
        double locStart = loc[i];
        double locEnd = loc[i + 1];
        double delta = locEnd - locStart;
        double t = (x - locStart) / delta;
        double v0 = val[i];
        double v1 = val[i + 1];
        double d0 = der[i] * delta - (v1 - v0);
        double d1 = -der[i + 1] * delta + (v1 - v0);
        return v0 + (v1 - v0) * t + t * (1.0 - t) * (d0 + (d1 - d0) * t);
    }

    /** {@code value + deriv * (x - loc_knot)} at the end knot {@code k}. */
    private static double linearExtend(double x, double[] loc, double value, double[] der, int k) {
        double d = der[k];
        return d == 0.0 ? value : value + d * (x - loc[k]);
    }

    /** Rightmost index {@code i} with {@code loc[i] <= x}; callers ensure {@code loc[0] <= x < loc[last]}. */
    private static int greatestIndexAtOrBelow(double[] loc, double x) {
        int lo = 0;
        int hi = loc.length - 1;
        int ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (loc[mid] <= x) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }

    private static boolean finite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    /** Self-describing params string {@code "SplineFn=[n,loc0,val0,der0,...]"} (accepted by {@link #fromTd}). */
    public String td() {
        StringBuilder sb = new StringBuilder("SplineFn=[").append(location.length);
        for (int i = 0; i < location.length; i++) {
            sb.append(',').append(location[i]).append(',').append(value[i]).append(',').append(derivative[i]);
        }
        return sb.append(']').toString();
    }

    /** Parses a {@link #td()} string back into the same deterministic spline. */
    public static SplineFn fromTd(String source) {
        double[] v = parseDoubles(source, "SplineFn");
        if (v.length < 4) {
            throw new IllegalArgumentException("SplineFn td needs at least two points");
        }
        int n = (int) v[0];
        if (v.length != 1 + 3 * n) {
            throw new IllegalArgumentException("SplineFn td token count mismatch");
        }
        double[] l = new double[n];
        double[] val = new double[n];
        double[] der = new double[n];
        for (int i = 0; i < n; i++) {
            l[i] = v[1 + 3 * i];
            val[i] = v[1 + 3 * i + 1];
            der[i] = v[1 + 3 * i + 2];
        }
        return new SplineFn(l, val, der);
    }

    /** Minimal {@code Name=[a,b,...]} parser returning tokens as doubles. */
    private static double[] parseDoubles(String source, String name) {
        String body = name + "=[";
        if (source == null || !source.startsWith(body) || !source.endsWith("]")) {
            throw new IllegalArgumentException("expected " + body + "...]: " + source);
        }
        String inner = source.substring(body.length(), source.length() - 1);
        if (inner.isEmpty()) {
            return new double[0];
        }
        String[] parts = inner.split(",");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Double.parseDouble(parts[i].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid token in " + name + ": " + parts[i], e);
            }
        }
        return out;
    }
}