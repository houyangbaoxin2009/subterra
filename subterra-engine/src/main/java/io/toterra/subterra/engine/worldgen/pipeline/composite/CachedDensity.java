package io.toterra.subterra.engine.worldgen.pipeline.composite;

import io.toterra.subterra.engine.worldgen.pipeline.density.Density;

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
 * NOTE on {@link #cached2d(Density)} / {@link #cached3d(Density)}: the slot is
 * zero-initialised ({@code key 0, value 0.0}) and the check is {@code slotKey == key};
 * the key of the block coordinate {@code (0,0)} (resp. {@code (0,0,0)}) is {@code 0},
 * so the very first request for that coordinate returns the initial {@code 0.0} without
 * computing. This is a deliberate bit-compatibility shim — the frozen golden asset
 * encodes that value, so those two methods are left exactly as shipped. The dedicated
 * {@link #cached2d(Density, int)} leaf cache uses an explicit {@code used[]} table and
 * therefore never mis-runs {@code (0,0)}; it is safe for the previously-<em>uncached</em>
 * climate leaves, whose true value is identical either way.
 * <p>
 * 每线程单槽值缓存（p.1.8.32 性能）。复合树在<em>同一角点</em>多次求值若干子密度（depth/
 * initial/cheese/cave 各臂都按 (x,z) 采样相同的二维气候样条，sloped_cheese 被 range-choice
 * 与洞穴族反复求值）。本包装把等输入的重复求值折叠为一次真实调用——纯去重，数值与未缓存树
 * 逐位一致。单槽足够：采样器按严格顺序访问角点，并在换点前反复请求同坐标；{@link ThreadLocal}
 * 保证并发区块线程互不污染。注意：{@link #cached2d(Density)}/{@link #cached3d(Density)}
 * 的槽零初始化（{@code key 0, value 0.0}），而块坐标 {@code (0,0)}（及 {@code (0,0,0)}）
 * 的 key 恰为 0，故对该坐标的首次请求直接返回初始值 0.0 而不求值。这是刻意的逐位兼容垫片——
 * 冻结的 golden 资产编码了该值，故此两方法保持原样。专用 {@link #cached2d(Density, int)}
 * 叶子缓存带显式 {@code used[]} 表，不会对 {@code (0,0)} 误命中；它仅供此前<em>未缓存</em>
 * 的气候叶子使用，其真实值两种方式都相同。
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

    /**
     * 2-D bounded key-validated table keyed on the full {@code (x, z)} pair (y dropped).
     * Direct-mapped by a hash of the packed (x,z); a mismatched stored key forces a
     * recompute and re-store, so output is bit-identical to the wrapped {@code d} for
     * every input — a pure dedup of a y-independent function. Unlike the single-slot
     * {@link #cached2d(Density)}, an explicit {@code used[]} marker means a request for
     * the block coordinate {@code (0,0)} is computed (not mistaken for an empty slot),
     * which is exactly right for the previously-uncached climate <em>leaves</em>.
     * {@code capacity} is rounded up to a power of two. Per-thread state, no locks.
     */
    static Density cached2d(Density d, int capacity) {
        int cap = Integer.highestOneBit(Math.max(2, capacity));
        int mask = cap - 1;
        ThreadLocal<long[]> keys = ThreadLocal.withInitial(() -> new long[cap]);
        ThreadLocal<long[]> vals = ThreadLocal.withInitial(() -> new long[cap]);
        ThreadLocal<boolean[]> used = ThreadLocal.withInitial(() -> new boolean[cap]);
        return (x, y, z) -> {
            long k = pack2(x, z);
            int idx = (int) ((k ^ (k >>> 32)) & 0x7FFF_FFFFL) & mask;
            long[] ks = keys.get();
            boolean[] u = used.get();
            if (u[idx] && ks[idx] == k) {
                return Double.longBitsToDouble(vals.get()[idx]);
            }
            double v = d.eval(x, 0.0, z);
            ks[idx] = k;
            vals.get()[idx] = Double.doubleToRawLongBits(v);
            u[idx] = true;
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