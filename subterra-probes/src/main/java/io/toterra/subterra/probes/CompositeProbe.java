package io.toterra.subterra.probes;

import io.toterra.subterra.optim.worldgen.pipeline.composite.DensityComposite;
import io.toterra.subterra.optim.worldgen.pipeline.composite.JaggednessFn;
import io.toterra.subterra.optim.worldgen.pipeline.composite.ShiftedNoiseFn;
import io.toterra.subterra.optim.worldgen.pipeline.composite.SlideFn;
import io.toterra.subterra.optim.worldgen.pipeline.composite.SplineFn;
import io.toterra.subterra.optim.worldgen.pipeline.density.Density;
import io.toterra.subterra.optim.worldgen.pipeline.router.NoiseRouter;

/**
 * 确定性验收探针：p.1.8.14 组合密度场核心（SplineFn / SlideFn / JaggednessFn /
 * ShiftedNoiseFn / DensityComposite 装配）。Deterministic acceptance probe.
 * Pure JVM; asserts the vanilla-compatible spline cubic-Hermite basis against
 * hand-computed exact doubles, the overworld slide floor/ceiling clamps, the
 * quarter/half-negative jaggedness term, the shifted-noise offset algebra, and that
 * {@code NoiseRouter.overworld(seed)} now installs the composite pipeline for its
 * {@code depth}/{@code initialDensityWithoutJaggedness}/{@code finalDensity} fields
 * while the router stays deterministic and finite across fresh assemblies. No timing
 * asserts.
 */
public final class CompositeProbe {

    private CompositeProbe() {
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
        return Math.abs(a - b) < 1.0e-12;
    }

    private static boolean finite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    private static boolean rejects(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static final double[] L0 = {0.0, 1.0};
    private static final double[] V01 = {0.0, 1.0};
    private static final double[] D0 = {0.0, 0.0};
    private static final double[] D1 = {1.0, 1.0};

    public static void main(String[] args) {
        long seed = 0x1A2B_3C4D_5E6F_7081L;

        // ============================ (a) SplineFn ============================
        SplineFn s = new SplineFn(L0, V01, D0);
        check("spn endpoints exact", s.eval(0.0) == 0.0 && s.eval(1.0) == 1.0);
        // hand-computed cubic-Hermite interior pins (dyadic-exact in double)
        check("spn hand cubic t=0.25 = 0.15625", s.eval(0.25) == 0.15625);
        check("spn hand cubic t=0.50 = 0.5", s.eval(0.5) == 0.5);
        check("spn hand cubic t=0.75 = 0.84375", s.eval(0.75) == 0.84375);
        check("spn monotone non-decreasing on [0,1]", monotoneNonDecreasing(s, 0.0, 1.0));
        check("spn linear extend left (der 0)", s.eval(-1.0) == 0.0);
        check("spn linear extend right (der 0)", s.eval(2.0) == 1.0);

        SplineFn sd = new SplineFn(L0, V01, D1);
        check("spn extend uses end derivative left", sd.eval(-1.0) == -1.0);
        check("spn extend uses end derivative right", sd.eval(2.0) == 2.0);
        check("spn knots reproduced at end with der", sd.eval(0.0) == 0.0 && sd.eval(1.0) == 1.0);

        // multi-knot interior knot reproduction + cross-knot binary search
        SplineFn multi = new SplineFn(
                new double[]{0.0, 2.0, 5.0, 9.0},
                new double[]{0.0, 1.0, 1.0, 3.0},
                new double[]{0.0, 0.0, 0.0, 0.0});
        check("spn multi interior knot reproduced",
                multi.eval(2.0) == 1.0 && multi.eval(5.0) == 1.0 && multi.eval(9.0) == 3.0);
        boolean deterministicGrid = true;
        boolean finiteGrid = true;
        for (double x = -8.0; x <= 16.0; x += 0.37) {
            double a = multi.eval(x);
            double b = new SplineFn(new double[]{0.0, 2.0, 5.0, 9.0},
                    new double[]{0.0, 1.0, 1.0, 3.0}, new double[]{0.0, 0.0, 0.0, 0.0}).eval(x);
            deterministicGrid &= a == b;
            finiteGrid &= finite(a);
        }
        check("spn deterministic cross-instance over grid", deterministicGrid);
        check("spn finite over grid", finiteGrid);

        check("spn rejects non-increasing locations",
                rejects(() -> new SplineFn(new double[]{0.0, 0.0}, V01, D0)));
        check("spn rejects mismatched arrays",
                rejects(() -> new SplineFn(new double[]{0.0, 1.0, 2.0}, V01, D0)));
        check("spn rejects fewer than two points",
                rejects(() -> new SplineFn(new double[]{3.0}, new double[]{1.0}, new double[]{0.0})));
        check("spn rejects non-finite",
                rejects(() -> new SplineFn(new double[]{0.0, Double.NaN}, V01, D0)));

        String sTd = new SplineFn(L0, V01, new double[]{0.5, -0.5}).td();
        check("spn td round-trips eval",
                near(SplineFn.fromTd(sTd).eval(0.3), new SplineFn(L0, V01, new double[]{0.5, -0.5}).eval(0.3)));
        check("spn td rejects malformed",
                rejects(() -> SplineFn.fromTd("SplineFn=[]")) && rejects(() -> SplineFn.fromTd("oops")));

        // ============================ (b) SlideFn ============================
        check("slide floor clamps to 0.1171875", SlideFn.overworldSlide(123.0, -70.0) == 0.1171875);
        check("slide ceiling clamps to -0.078125", SlideFn.overworldSlide(-999.0, 300.0) == -0.078125);
        // neutral band y=100 is outside both bands -> overworld slide is identity on raw
        check("slide identity in neutral band", SlideFn.overworldSlide(7.75, 100.0) == 7.75);
        check("slide monotone increasing in raw at fixed y",
                SlideFn.overworldSlide(1.0, 50.0) <= SlideFn.overworldSlide(2.0, 50.0));
        check("slide grad clamped range", SlideFn.grad(0.0, -64.0, -40.0, 0.0, 1.0) == 1.0
                && SlideFn.grad(-100.0, -64.0, -40.0, 0.0, 1.0) == 0.0);
        Density slideRaw = (x, y, z) -> 10.0 + y * 0.5;
        check("slide Density form matches kernel",
                near(SlideFn.overworld(slideRaw).eval(1.0, 50.0, 2.0), SlideFn.overworldSlide(slideRaw.eval(1.0, 50.0, 2.0), 50.0)));
        bothFinite("slide finite", SlideFn.overworld(slideRaw));

        // ============================ (c) Jaggedness / mapped fns ============================
        check("qn positive unchanged, negative quartered, zero zero",
                JaggednessFn.quarterNegative(4.0) == 4.0
                        && JaggednessFn.quarterNegative(-4.0) == -1.0
                        && JaggednessFn.quarterNegative(0.0) == 0.0);
        check("hn positive unchanged, negative halved, zero zero",
                JaggednessFn.halfNegative(4.0) == 4.0
                        && JaggednessFn.halfNegative(-4.0) == -2.0
                        && JaggednessFn.halfNegative(0.0) == 0.0);
        check("squeeze(0)=0, squeeze peaks at +/-11/24",
                JaggednessFn.squeeze(0.0) == 0.0
                        && near(JaggednessFn.squeeze(100.0), 11.0 / 24.0)
                        && near(JaggednessFn.squeeze(-100.0), -11.0 / 24.0));
        Density jagFactor = (x, y, z) -> 2.0;
        Density jagNoise = (x, y, z) -> -4.0;
        Density jagTerm = JaggednessFn.apply(jagFactor, jagNoise);
        check("jagged term = factor * half_negative(noise)",
                jagTerm.eval(0.0, 0.0, 0.0) == -4.0);
        Density jagTermPos = JaggednessFn.apply((x, y, z) -> 2.0, (x, y, z) -> 4.0);
        check("jagged term sign: positive noise unchanged", jagTermPos.eval(0.0, 0.0, 0.0) == 8.0);
        Density jagGen = JaggednessFn.apply(jagFactor, (x, y, z) -> 3.0 * x * x - 2.0 * z);
        bothFinite("jagged term finite", jagGen);

        // ============================ (d) ShiftedNoiseFn ============================
        Density rampX = (x, y, z) -> x;
        Density zeroC = (x, y, z) -> 0.0;
        check("shifted equals identity when shift zero",
                ShiftedNoiseFn.shiftedNoise2d(rampX, zeroC, zeroC, 1.0).eval(3.0, 5.0, 7.0) == 3.0);
        Density shift2 = (x, y, z) -> 2.0;
        check("shifted offset xz honored",
                ShiftedNoiseFn.shiftedNoise2d(rampX, shift2, zeroC, 1.0).eval(3.0, 5.0, 7.0) == 5.0);
        check("shifted xzScale scales coordinates",
                ShiftedNoiseFn.shiftedNoise2d(rampX, zeroC, zeroC, 2.0).eval(3.0, 5.0, 7.0) == 6.0);
        Density rawY = (x, y, z) -> y;
        check("shifted yScale + shiftY honored",
                ShiftedNoiseFn.of(rawY, zeroC, (x, y, z) -> 1.0, zeroC, 1.0, 2.0).eval(0.0, 5.0, 0.0) == 11.0);
        boolean shiftDeterministic = true;
        Density sh = ShiftedNoiseFn.shiftedNoise2d(rampX, (x, y, z) -> x * 0.1, (x, y, z) -> z * 0.2, 1.0);
        Density sh2 = ShiftedNoiseFn.shiftedNoise2d(rampX, (x, y, z) -> x * 0.1, (x, y, z) -> z * 0.2, 1.0);
        for (double p = -5; p <= 5; p += 1.31) {
            shiftDeterministic &= sh.eval(p, 0.0, p) == sh2.eval(p, 0.0, p);
        }
        check("shifted deterministic cross-instance", shiftDeterministic);

        // ============================ (e) Composite assembly on router ============================
        NoiseRouter r1 = NoiseRouter.overworld(seed);
        NoiseRouter r2 = NoiseRouter.overworld(seed, -64, 320);
        Density d1 = r1.depth();
        Density i1 = r1.initialDensityWithoutJaggedness();
        Density f1 = r1.finalDensity();

        bothFinite("composite depth finite", d1);
        bothFinite("composite initial finite", i1);
        bothFinite("composite final finite", f1);

        boolean compDeterministic = true;
        for (int i = 8; i <= 10; i += 1) {
            for (int y = -64; y <= 320; y += 64) {
                int idx = i == 8 ? 8 : (i == 9 ? 10 : 11);
                compDeterministic &= near(r1.fieldAt(idx).eval(12.5, y, -33.25),
                        r2.fieldAt(idx).eval(12.5, y, -33.25));
            }
        }
        check("composite deterministic across two fresh assemblies", compDeterministic);

        // The router now installs the composite (slide-floored/ceiled) fields.
        check("composite final slides to 0.1171875 below floor", f1.eval(100.0, -100.0, 50.0) == 0.1171875);
        check("composite final slides to -0.078125 above ceiling", f1.eval(100.0, 300.0, 50.0) == -0.078125);
        check("composite initial slides to 0.1171875 below floor", i1.eval(100.0, -100.0, 50.0) == 0.1171875);
        check("composite initial slides to -0.078125 above ceiling", i1.eval(100.0, 300.0, 50.0) == -0.078125);

        // depth depends on the Y gradient (from 1.5 below to -1.5 above), so deeper = higher.
        check("composite depth rises with y (gradient)",
                d1.eval(10.0, -30.0, 10.0) > d1.eval(10.0, 300.0, 10.0));

        // differs from the p.1.8.12 reduced stand-in at an interior point
        double midY = 128.0;
        double height = 384.0;
        Density cont = r1.continents();
        Density eros = r1.erosion();
        Density ridge = r1.ridges();
        double cx = 12.5, cy = 80.0, cz = -33.25;
        double oldInit = 4.0 - 1.5625 * (1.0 + 0.5 * cont.eval(cx, cy, cz) + 0.5 * eros.eval(cx, cy, cz));
        double oldFinal = 2.0 * (cy - midY) / height + 0.5 * oldInit + 0.25 * ridge.eval(cx, cy, cz);
        check("final differs from p.1.8.12 stand-in", !near(f1.eval(cx, cy, cz), oldFinal));

        // same-seed reproducibility of the three composite fields
        check("composite same-seed reproducible",
                near(r1.depth().eval(cx, cy, cz), r2.depth().eval(cx, cy, cz))
                        && near(r1.initialDensityWithoutJaggedness().eval(cx, cy, cz),
                        r2.initialDensityWithoutJaggedness().eval(cx, cy, cz))
                        && near(r1.finalDensity().eval(cx, cy, cz), r2.finalDensity().eval(cx, cy, cz)));

        // different world seeds -> composite fields differ
        NoiseRouter r3 = NoiseRouter.overworld(seed + 123L);
        check("composite differs across world seeds",
                !near(r1.depth().eval(cx, cy, cz), r3.depth().eval(cx, cy, cz))
                        && !near(r1.finalDensity().eval(cx, cy, cz), r3.finalDensity().eval(cx, cy, cz)));

        // router p.1.8.12 invariants still hold conceptually: 15 fields, deterministic, finite
        boolean routerAllDet = true;
        boolean routerAllFin = true;
        for (int i = 0; i < 15; i++) {
            Density fa = r1.fieldAt(i);
            Density fb = r2.fieldAt(i);
            double va = fa.eval(12.5, 80.0, -33.25);
            routerAllDet &= va == fb.eval(12.5, 80.0, -33.25);
            routerAllFin &= finite(va) && finite(fa.eval(-120.0, 40.0, 300.0)) && finite(fa.eval(0.0, 0.0, 0.0));
        }
        check("router all 15 fields present + deterministic", r1.fieldCount() == 15 && routerAllDet);
        check("router all 15 fields finite", routerAllFin);

        // DensityComposite td / fromTd round-trip
        DensityComposite.Overworld comp = DensityComposite.overworld(r1);
        DensityComposite.Overworld comp2 = DensityComposite.fromTd(comp.td());
        check("composite td round-trips finalDensity",
                near(comp2.finalDensity().eval(cx, cy, cz), r1.finalDensity().eval(cx, cy, cz)));

        // verified vanilla constants pinned
        check("pinned offset base -0.5037500262260437", DensityComposite.OFFSET_BASE == -0.5037500262260437);
        check("pinned depth shift -0.703125", DensityComposite.DEPTH_SHIFT == -0.703125);
        check("pinned slide bottom 0.1171875 / top -0.078125",
                DensityComposite.SLIDE_BOTTOM_VALUE == 0.1171875 && DensityComposite.SLIDE_TOP_VALUE == -0.078125);
        check("pinned cheese scale 4 & range 1.5625",
                DensityComposite.CHEESE_SCALE == 4.0 && DensityComposite.CHEESE_RANGE_MAX == 1.5625);

        if (failures == 0) {
            System.out.println("[CompositeProbe] PASS (composite density-fields core, " + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[CompositeProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }

    private static boolean monotoneNonDecreasing(SplineFn s, double lo, double hi) {
        double prev = s.eval(lo);
        for (double x = lo; x <= hi; x += 0.03) {
            double v = s.eval(x);
            if (v < prev - 1.0e-12) {
                return false;
            }
            prev = v;
        }
        return true;
    }

    private static void bothFinite(String name, Density d) {
        boolean ok = true;
        for (double dx : new double[]{-120.0, -3.0, 0.0, 12.5, 300.0}) {
            for (double dy : new double[]{-80.0, 0.0, 80.0, 320.0}) {
                for (double dz : new double[]{-100.0, 0.0, 55.0}) {
                    ok &= finite(d.eval(dx, dy, dz));
                }
            }
        }
        check(name, ok);
    }
}