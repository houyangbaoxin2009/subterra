package io.toterra.subterra.optim.worldgen.pipeline.composite;

import io.toterra.subterra.optim.worldgen.pipeline.density.Density;

/**
 * Per-thread single-slot value caches (p.1.8.32 perf). The composite tree evaluates
 * several sub-densities many times at the SAME corner (the depth/initial/cheese/cave
 * arms all sample the same 2-D climate splines per (x,z), and sloped_cheese is
 * re-evaluated by the range-choice and cave family). These wrappers collapse the
 * repeated equal-input evaluations into one real call — pure deduplication, so the
 * numeric result is bit-identical to the uncached tree. A single slot is enough
 * because the sampler visits corners in a strict order and repeats the same
 * coordinate many times before moving on; {@link ThreadLocal} keeps concurrent
 * chunk threads pollution-free.
 * <p>
 * 每线程单槽值缓存（p.1.8.32 性能）。复合树在<em>同一角点</em>多次求值若干子密度（depth/
 * initial/cheese/cave 各臂都按 (x,z) 采样相同的二维气候样条，sloped_cheese 被 range-choice
 * 与洞穴族反复求值）。本包装把等输入的重复求值折叠为一次真实调用——纯去重，数值与未缓存树
 * 逐位一致。单槽足够：采样器按严格顺序访问角点，并在换点前反复请求同坐标；{@link ThreadLocal}
 * 保证并发区块线程互不污染。
 */
final class CachedDensity {

    private CachedDensity() {
    }

    /** 2-D single-slot cache keyed by the (x, z) block coordinates (y dropped, 2-D leaf). */
    static Density cached2d(Density d) {
        ThreadLocal<long[]> slot = ThreadLocal.withInitial(() -> new long[2]);
        return (x, y, z) -> {
            long k = pack2(x, z);
            long[] s = slot.get();
            if (s[0] == k) {
                return Double.longBitsToDouble(s[1]);
            }
            double v = d.eval(x, 0.0, z);
            s[0] = k;
            s[1] = Double.doubleToRawLongBits(v);
            return v;
        };
    }

    /** 3-D single-slot cache keyed by (x, y, z). */
    static Density cached3d(Density d) {
        ThreadLocal<long[]> slot = ThreadLocal.withInitial(() -> new long[2]);
        return (x, y, z) -> {
            long k = pack3(x, y, z);
            long[] s = slot.get();
            if (s[0] == k) {
                return Double.longBitsToDouble(s[1]);
            }
            double v = d.eval(x, y, z);
            s[0] = k;
            s[1] = Double.doubleToRawLongBits(v);
            return v;
        };
    }

    private static long pack2(double x, double z) {
        return (((long) (int) x) << 32) ^ ((long) (int) z & 0xFFFF_FFFFL);
    }

    private static long pack3(double x, double y, double z) {
        long k = ((long) (int) x & 0xFFFFF) << 44;
        k ^= ((long) (int) y & 0x3FFF) << 30;
        return k ^ ((long) (int) z & 0x3FFF_FFFFL);
    }
}