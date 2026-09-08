package io.toterra.subterra.probes;

import io.toterra.subterra.engine.worldgen.pipeline.noise.LegacyRandom;
import io.toterra.subterra.engine.worldgen.pipeline.noise.perlin.ImprovedNoise;
import io.toterra.subterra.engine.worldgen.pipeline.noise.perlin.PerlinNoise;

/**
 * 确定性验收探针：p.1.8.7 perlin 八度噪声族（PerlinNoise / ImprovedNoise）。
 * Deterministic acceptance probe for the p.1.8.7 perlin octave-noise family.
 * Pure JVM; asserts the vanilla single-stream (legacy) octave layout: the top
 * octave is built first from the stream start, disabled octaves advance the
 * stream via {@link PerlinNoise#skipOctave} (262 draws), and each enabled
 * octave's permutation is pinned to its stream position. Plus determinism,
 * hand-computed lattice values, negative-octave input shifting, value-factor
 * weighting, range bounds, cell-boundary continuity and the td round-trip.
 */
public final class PerlinProbe {

    private static final double WRAP = 3.3554432E7d;

    private PerlinProbe() {
    }

    private static int failures = 0;
    private static int checks = 0;

    private static void check(String name, boolean ok) {
        checks++;
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1.0e-9;
    }

    private static boolean rejectsNull() {
        try {
            PerlinNoise.create(1L, 0, null);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean rejectsEmpty() {
        try {
            PerlinNoise.create(1L, 0, new double[0]);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean rejectsAllZero() {
        try {
            PerlinNoise.create(1L, 0, new double[]{0.0, 0.0});
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    /** Two lattices are bit-identical iff offsets and permutation match. */
    private static boolean sameLattice(ImprovedNoise a, ImprovedNoise b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.xo != b.xo || a.yo != b.yo || a.zo != b.zo) {
            return false;
        }
        for (int i = 0; i < 300; i++) {
            if (a.perm(i) != b.perm(i)) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        long seed = 0x5EED_CAFE_1234_5678L;

        // ---- 构造校验 ------------------------------------------------
        check("pn rejects null amplitudes", rejectsNull());
        check("pn rejects empty amplitudes", rejectsEmpty());
        check("pn rejects all-zero amplitudes", rejectsAllZero());

        // ---- 确定性 ------------------------------------------------
        PerlinNoise single = PerlinNoise.create(seed, 0, new double[]{1.0});
        double sv = single.getValue(1.3, 2.6, 3.7);
        check("pn same instance reproducible", sv == single.getValue(1.3, 2.6, 3.7));
        check("pn cross-instance reproducible",
                sv == PerlinNoise.create(seed, 0, new double[]{1.0}).getValue(1.3, 2.6, 3.7));
        check("pn negative-firstOctave reproducible cross-instance",
                PerlinNoise.create(seed, -1, new double[]{1.0}).getValue(3.0, 4.0, 5.0)
                        == PerlinNoise.create(seed, -1, new double[]{1.0}).getValue(3.0, 4.0, 5.0));

        // ---- 种子敏感 ------------------------------------------------
        check("pn different seed differs",
                !near(sv, PerlinNoise.create(seed + 77L, 0, new double[]{1.0}).getValue(1.3, 2.6, 3.7)));

        // ---- 单流 / 降序 (size-1..0) 八度建表 -------------------------
        check("pn single octave exposes a lattice", single.octaveAt(0) != null);

        // The highest octave (index size-1) is built FIRST from stream start,
        // so it equals a size-1 single-octave field built from the same seed.
        PerlinNoise top = PerlinNoise.create(seed, 0, new double[]{0.0, 1.0});
        check("pn top octave built first from stream start",
                sameLattice(top.octaveAt(1), single.octaveAt(0)) && top.octaveAt(0) == null);

        // A zero-amplitude high octave advances the stream (skip), so the lower
        // octave of [1,0] is exactly what a fresh stream yields after one skip.
        PerlinNoise p10 = PerlinNoise.create(seed, 0, new double[]{1.0, 0.0});
        LegacyRandom rSkip = new LegacyRandom(seed);
        PerlinNoise.skipOctave(rSkip);
        check("pn skip-then-build reproduces octave-0 of [1,0]",
                sameLattice(p10.octaveAt(0), new ImprovedNoise(rSkip)) && p10.octaveAt(1) == null);

        // MANDATE: two-octave [1,1] octave-0 matches an independent single
        // lattice built from a fresh identical-seed stream that first consumed
        // exactly the octave-1 (index 1) permutation draws.
        PerlinNoise p11 = PerlinNoise.create(seed, 0, new double[]{1.0, 1.0});
        LegacyRandom rBuild = new LegacyRandom(seed);
        new ImprovedNoise(rBuild);            // consume octave-1 permutation
        ImprovedNoise oct0 = new ImprovedNoise(rBuild);
        check("pn two-octave octave-0 == fresh stream after octave-1 draws",
                sameLattice(p11.octaveAt(0), oct0) && p11.octaveAt(1) != null);

        // One-octave firstOctave F == the fine (index size-1) octave of a
        // leading-zero multi-octave field: both are first-built from stream start.
        PerlinNoise singleF = PerlinNoise.create(seed, 3, new double[]{1.0});
        PerlinNoise multiF = PerlinNoise.create(seed, 1, new double[]{0.0, 0.0, 1.0});
        check("pn one-octave equals multi leading-zero fine octave",
                sameLattice(singleF.octaveAt(0), multiF.octaveAt(2)));

        // ---- 晶格精确值：与手写参考逐点对比 ---------------------------
        ImprovedNoise oct = single.octaveAt(0);
        check("pn lattice matches hand-reference @A", single.getValue(1.3, 2.6, 3.7) == refNoise(oct, 1.3, 2.6, 3.7));
        check("pn lattice matches hand-reference @B", single.getValue(-4.0, 0.0, 8.0) == refNoise(oct, -4.0, 0.0, 8.0));
        check("pn lattice matches hand-reference @C", single.getValue(-3.5, 0.25, 8.75) == refNoise(oct, -3.5, 0.25, 8.75));
        check("pn lattice origin value is exactly 0",
                oct.noise(-oct.xo, -oct.yo, -oct.zo) == 0.0 && single.getValue(-oct.xo, -oct.yo, -oct.zo) == 0.0);

        // ---- 负八度坐标平移行为 -----------------------------------------
        // firstOctave=-1 => initial input factor 2^-1 => samples lattice at 0.5x.
        PerlinNoise neg = PerlinNoise.create(seed, -1, new double[]{1.0});
        check("pn negative octave samples at half coords",
                neg.getValue(7.0, 9.0, 11.0) == neg.octaveAt(0).noise(7.0 * 0.5, 9.0 * 0.5, 11.0 * 0.5));
        check("pn negative octave differs from base octave",
                !near(neg.getValue(7.0, 9.0, 11.0), oct.noise(7.0, 9.0, 11.0)));

        // ---- 多八度 value-factor 加权和（与自身晶格同源，测组合语义） ----
        PerlinNoise negTwo = PerlinNoise.create(seed, -1, new double[]{1.0, 1.0}); // octaves -1,0
        double expNeg = negTwo.octaveAt(0).noise(4.0 * 0.5, 5.0 * 0.5, 6.0 * 0.5) * (2.0 / 3.0)
                + negTwo.octaveAt(1).noise(4.0, 5.0, 6.0) * (1.0 / 3.0);
        check("pn two-octave negative weighted sum (2/3,1/3)", negTwo.getValue(4.0, 5.0, 6.0) == expNeg);

        PerlinNoise tri = PerlinNoise.create(seed, 0, new double[]{1.0, 1.0, 1.0});
        double expTri = tri.octaveAt(0).noise(2.0, 3.0, 4.0) * (4.0 / 7.0)
                + tri.octaveAt(1).noise(4.0, 6.0, 8.0) * (2.0 / 7.0)
                + tri.octaveAt(2).noise(8.0, 12.0, 16.0) * (1.0 / 7.0);
        check("pn 3-octave weighted sum (4/7,2/7,1/7)", tri.getValue(2.0, 3.0, 4.0) == expTri);
        check("pn higher octaves add detail",
                !near(tri.getValue(2.0, 3.0, 4.0), single.getValue(2.0, 3.0, 4.0)));

        // ---- 范围合理性 ------------------------------------------------
        PerlinNoise range = PerlinNoise.create(999L, 0, new double[]{1.0, 0.5, 0.25, 0.125});
        double bound = Math.abs(range.maxValue()) + 1.0e-9;
        boolean inRange = true;
        boolean latticeBounded = true;
        for (double dx = -20.0; dx <= 20.0; dx += 0.9) {
            for (double dz = -20.0; dz <= 20.0; dz += 0.9) {
                inRange &= Math.abs(range.getValue(dx, 0.0, dz)) <= bound;
                latticeBounded &= Math.abs(oct.noise(dx, 0.0, dz)) < 2.0;
            }
        }
        check("pn multi-octave within maxValue bound", inRange);
        check("pn lattice value bounded in (-2,2)", latticeBounded);

        // ---- 单元边界连续性（小邻域变化连续；反混淆原生三线性即 C0） ----
        check("pn cell-boundary continuity @face",
                Math.abs(oct.noise(2.25, 3.5, 4.75) - oct.noise(2.250001, 3.5, 4.75)) < 1.0e-4);
        check("pn cell-boundary continuity @near-edge",
                Math.abs(oct.noise(5.0, -1.0, 0.5) - oct.noise(5.000001, -0.999999, 0.500001)) < 1.0e-4);

        // ---- td 往返 -------------------------------------------------
        String td = tri.td();
        check("pn td round-trips to same field",
                PerlinNoise.fromTd(seed, td).getValue(2.0, 3.0, 4.0) == tri.getValue(2.0, 3.0, 4.0));
        check("pn td round-trip text is stable", PerlinNoise.fromTd(seed, td).td().equals(td));
        check("pn td rejects malformed input",
                rejectsTd(seed, "PerlinNoise=[oops]") && rejectsTd(seed, "[]"));

        // ---- wrap 折叠 -------------------------------------------------
        check("pn wrap identity for small coords",
                PerlinNoise.wrap(1.5) == 1.5 && PerlinNoise.wrap(-0.25) == -0.25);
        check("pn wrap folds large coords into range",
                Math.abs(PerlinNoise.wrap(WRAP + 5.0)) <= WRAP * 0.5 + 5.0
                        && PerlinNoise.wrap(WRAP + 1.0) > -WRAP
                        && PerlinNoise.wrap(WRAP + 1.0) < WRAP);

        if (failures == 0) {
            System.out.println("[PerlinProbe] PASS (perlin octave-noise family, " + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[PerlinProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }

    private static boolean rejectsTd(long seed, String source) {
        try {
            PerlinNoise.fromTd(seed, source);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    /** Independent lattice expression used to hand-verify {@link ImprovedNoise#noise}. */
    private static double refNoise(ImprovedNoise n, double x, double y, double z) {
        double xp = x + n.xo, yp = y + n.yo, zp = z + n.zo;
        int xi = fl(xp), yi = fl(yp), zi = fl(zp);
        double dx = xp - (double) xi, dy = yp - (double) yi, dz = zp - (double) zi;
        int i = n.perm(xi), i1 = n.perm(xi + 1);
        int k = n.perm(i + yi), k1 = n.perm(i + yi + 1);
        int l = n.perm(i1 + yi), l1 = n.perm(i1 + yi + 1);
        double d0 = ImprovedNoise.gradientDot(n.perm(k + zi), dx, dy, dz);
        double d1 = ImprovedNoise.gradientDot(n.perm(l + zi), dx - 1.0, dy, dz);
        double d2 = ImprovedNoise.gradientDot(n.perm(k1 + zi), dx, dy - 1.0, dz);
        double d3 = ImprovedNoise.gradientDot(n.perm(l1 + zi), dx - 1.0, dy - 1.0, dz);
        double d4 = ImprovedNoise.gradientDot(n.perm(k + zi + 1), dx, dy, dz - 1.0);
        double d5 = ImprovedNoise.gradientDot(n.perm(l + zi + 1), dx - 1.0, dy, dz - 1.0);
        double d6 = ImprovedNoise.gradientDot(n.perm(k1 + zi + 1), dx, dy - 1.0, dz - 1.0);
        double d7 = ImprovedNoise.gradientDot(n.perm(l1 + zi + 1), dx - 1.0, dy - 1.0, dz - 1.0);
        return lerp3(smoothstep(dx), smoothstep(dy), smoothstep(dz), d0, d1, d2, d3, d4, d5, d6, d7);
    }

    private static int fl(double v) {
        int i = (int) v;
        return v < (double) i ? i - 1 : i;
    }

    private static double smoothstep(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    private static double lerp2(double a, double b, double d0, double d1, double d2, double d3) {
        return lerp(b, lerp(a, d0, d1), lerp(a, d2, d3));
    }

    private static double lerp3(double a, double b, double c, double d0, double d1, double d2, double d3,
                                double d4, double d5, double d6, double d7) {
        return lerp(c, lerp2(a, b, d0, d1, d2, d3), lerp2(a, b, d4, d5, d6, d7));
    }
}