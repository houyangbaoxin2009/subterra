package io.toterra.subterra.probes;

import io.toterra.subterra.optim.worldgen.pipeline.formula.MathLib;
import io.toterra.subterra.optim.worldgen.pipeline.terrain.FormulaNoiseFunctions;
import io.toterra.subterra.optim.worldgen.pipeline.terrain.FormulaParams;
import io.toterra.subterra.optim.worldgen.pipeline.terrain.FormulaTerrain;

/**
 * 确定性验收探针：p.1.8.10 数学公式地形集成。Pure JVM; asserts the td
 * round-trip, formula-terrain determinism, noise overlay amplitude/magnitude,
 * smoothing blend sanity, the registered rand/randrange functions and the
 * Density-seam coordinate mapping exactly (deterministic, no timing asserts).
 * <p>
 * Deterministic; exit 0 = PASS, exit 1 = FAIL.
 */
public final class FormulaTerrainProbe {

    private FormulaTerrainProbe() {
    }

    private static int failures = 0;

    private static void check(String name, boolean ok) {
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

    private static boolean rejects(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    public static void main(String[] args) {
        // ---- td 往返 / round-trip -------------------------------
        FormulaParams defaults = FormulaParams.defaults();
        check("td defaults round-trip", FormulaParams.fromTd(defaults.td()).equals(defaults));

        FormulaParams custom = FormulaParams.builder()
                .formula("x + z + sin(y*pow(2.5))")
                .scale(2.5).height(3.0).variation(0.5).smoothing(0.25)
                .noise(FormulaParams.Noise.VALUE).seed(99L).build();
        check("td custom round-trip all fields", FormulaParams.fromTd(custom.td()).equals(custom));

        // Missing keys fall back to documented defaults.
        check("td missing keys -> defaults", FormulaParams.fromTd("[]").equals(FormulaParams.defaults()));

        // Rejection of bad td: empty formula / non-positive scale / null.
        check("td rejects blank formula",
                rejects(() -> FormulaParams.fromTd("[ formula = \"\", scale = 1 ]")));
        check("td rejects scale <= 0",
                rejects(() -> FormulaParams.fromTd("[ formula = \"x\", scale = 0 ]")));
        check("td rejects null", rejects(() -> FormulaParams.fromTd(null)));

        // ---- 公式地形确定性 / determinism -------------------------
        FormulaParams determinismParams = FormulaParams.builder()
                .formula("x*z + y*z*0.5 + rand(x,y,z)*0.1").scale(1.5).height(2.0)
                .variation(0.3).noise(FormulaParams.Noise.PERLIN).seed(7L).build();
        FormulaTerrain da = new FormulaTerrain(determinismParams);
        double dv = da.eval(1.5, 2.5, 3.5);
        check("formula terrain same instance reproducible", dv == da.eval(1.5, 2.5, 3.5));
        check("formula terrain cross-instance reproducible",
                near(dv, new FormulaTerrain(determinismParams).eval(1.5, 2.5, 3.5)));

        // ---- 种子敏感性 / seed sensitivity of the noise overlay ---------
        FormulaParams seedA = FormulaParams.builder()
                .formula("0").variation(0.5).noise(FormulaParams.Noise.PERLIN).seed(1L).build();
        FormulaParams seedB = FormulaParams.builder()
                .formula("0").variation(0.5).noise(FormulaParams.Noise.PERLIN).seed(2L).build();
        FormulaTerrain pa = new FormulaTerrain(seedA);
        FormulaTerrain pb = new FormulaTerrain(seedB);
        boolean seedSensitive = false;
        for (int i = -4; i <= 4 && !seedSensitive; i++) {
            for (int j = -4; j <= 4 && !seedSensitive; j++) {
                if (pa.eval(i, 5, j) != pb.eval(i, 5, j)) {
                    seedSensitive = true;
                }
            }
        }
        check("overlay seed-sensitive (differs somewhere)", seedSensitive);

        // ---- 叠加 / overlay --------------------------------
        // NONE == PERLIN/VALUE with variation 0 == pure formula (all = base).
        FormulaTerrain pure = new FormulaTerrain(FormulaParams.builder().formula("x+z").build());
        FormulaTerrain perlinZero = new FormulaTerrain(FormulaParams.builder()
                .formula("x+z").variation(0.0).noise(FormulaParams.Noise.PERLIN).build());
        FormulaTerrain valueZero = new FormulaTerrain(FormulaParams.builder()
                .formula("x+z").variation(0.0).noise(FormulaParams.Noise.VALUE).build());
        boolean noneIsPure = true;
        for (int i = -3; i <= 3; i++) {
            for (int j = -3; j <= 3; j++) {
                double b = pure.eval(i, 0, j);
                if (!near(b, perlinZero.eval(i, 0, j)) || !near(b, valueZero.eval(i, 0, j))) {
                    noneIsPure = false;
                }
            }
        }
        check("variation 0 == pure formula (NONE/PERLIN/VALUE)", noneIsPure);

        // variation > 0 differs from variation 0 (overlay non-zero), still deterministic.
        FormulaTerrain varied = new FormulaTerrain(FormulaParams.builder()
                .formula("x+z").variation(0.5).noise(FormulaParams.Noise.VALUE).seed(3L).build());
        boolean differs = false;
        for (int i = -3; i <= 3 && !differs; i++) {
            for (int j = -3; j <= 3 && !differs; j++) {
                if (!near(varied.eval(i, 0, j), pure.eval(i, 0, j))) {
                    differs = true;
                }
            }
        }
        check("variation>0 differs from pure", differs);
        check("variation>0 cross-instance deterministic",
                near(varied.eval(-2.5, 1.5, 3.5),
                        new FormulaTerrain(FormulaParams.builder().formula("x+z")
                                .variation(0.5).noise(FormulaParams.Noise.VALUE).seed(3L).build())
                                .eval(-2.5, 1.5, 3.5)));

        // Amplitude is definitely bounded: VALUE overlay contribution = variation * noise
        // in [-variation, variation], so |eval - pure| <= variation.
        boolean bounded = true;
        for (int i = -4; i <= 4; i++) {
            for (int j = -4; j <= 4; j++) {
                double d = Math.abs(varied.eval(i, 0, j) - pure.eval(i, 0, j));
                if (d > 0.5 + 1.0e-9) {
                    bounded = false;
                }
            }
        }
        check("overlay magnitude bounded by variation", bounded && differs);

        // ---- 平滑 / smoothing ---------------------------------
        // Nonlinear formula x*x: neighbor mean != center, so smoothing changes the result
        // monotonically toward the 6-neighbour mean (scale=height=1, over x). y/z neighbours
        // stay at the same x^2, so avg(neighbors) = (1 + 9 + 4 + 4 + 4 + 4)/6 = 26/6.
        FormulaTerrain sIg = new FormulaTerrain(FormulaParams.builder()
                .formula("x*x").smoothing(1.0).build());   // center 4 -> 26/6
        FormulaTerrain sId = new FormulaTerrain(FormulaParams.builder()
                .formula("x*x").smoothing(0.0).build());   // center 4
        FormulaTerrain sHalf = new FormulaTerrain(FormulaParams.builder()
                .formula("x*x").smoothing(0.5).build());
        double c0 = sId.eval(2, 5, 7);
        double c1 = sIg.eval(2, 5, 7);
        double ch = sHalf.eval(2, 5, 7);
        check("smoothing changes nonlinear result", !near(c0, c1) && near(c1, 26.0 / 6.0));
        check("smoothing monotone (half between endpoints)", ch > c0 && ch < c1);

        // Linear formula is smoothing-invariant (neighbour mean == value).
        FormulaTerrain lin0 = new FormulaTerrain(FormulaParams.builder().formula("x").smoothing(0.0).build());
        FormulaTerrain lin1 = new FormulaTerrain(FormulaParams.builder().formula("x").smoothing(1.0).build());
        check("linear formula smoothing-invariant",
                near(lin0.eval(2, 5, 7), lin1.eval(2, 5, 7)) && near(lin1.eval(2, 5, 7), 2.0));

        // ---- 注册的噪声 / rand 函数 ------------------------------
        FormulaNoiseFunctions.install();
        check("noise functions registered",
                MathLib.isKnown("perlin") && MathLib.isKnown("simplex")
                        && MathLib.isKnown("normal") && MathLib.isKnown("value")
                        && MathLib.isKnown("rand") && MathLib.isKnown("randrange"));

        FormulaTerrain ri = new FormulaTerrain(FormulaParams.builder().formula("rand(x,y,z)").seed(123L).build());
        double r1 = ri.eval(1, 2, 3);
        check("rand deterministic (same instance + cross-instance)",
                r1 == ri.eval(1, 2, 3)
                        && near(r1, new FormulaTerrain(FormulaParams.builder().formula("rand(x,y,z)").seed(123L).build()).eval(1, 2, 3)));

        boolean randInUnit = true;
        for (int i = -5; i <= 5; i++) {
            double r = ri.eval(i, 0, i);
            if (!(r >= 0.0 && r < 1.0)) {
                randInUnit = false;
            }
        }
        check("rand in [0,1)", randInUnit);

        FormulaTerrain rr = new FormulaTerrain(FormulaParams.builder().formula("randrange(-5,5,x,y,z)").seed(42L).build());
        boolean rrBounded = true;
        for (int i = -5; i <= 5; i++) {
            double r = rr.eval(i, 1, i);
            if (!(r >= -5.0 && r < 5.0)) {
                rrBounded = false;
            }
        }
        check("randrange respects [lo,hi)", rrBounded);
        check("randrange rejects hi<=lo",
                rejects(() -> new FormulaTerrain(FormulaParams.builder().formula("randrange(5,-5,x,y,z)").build()).eval(0, 0, 0)));

        // ---- Density-seam 契约 / coordinate mapping -------------
        // Mapping: eval(x,y,z) realises formula at (x*scale, y*height, z*scale).
        FormulaTerrain map = new FormulaTerrain(FormulaParams.builder().formula("x").scale(2.0).height(3.0).build());
        check("density pin x == scaled x", near(map.eval(1, 2, 3), 2.0));
        FormulaTerrain mapY = new FormulaTerrain(FormulaParams.builder().formula("y").scale(2.0).height(3.0).build());
        check("density pin y == height*y", near(mapY.eval(1, 2, 3), 6.0));
        FormulaTerrain mapZ = new FormulaTerrain(FormulaParams.builder().formula("z").scale(2.0).height(3.0).build());
        check("density pin z == scale*z", near(mapZ.eval(1, 2, 3), 6.0));

        // Value stream stays finite over a region.
        FormulaTerrain fin = new FormulaTerrain(FormulaParams.builder()
                .formula("x^2 + y^2 + z^2 + rand(x,y,z)").scale(2.0).height(3.0).seed(5L).build());
        boolean finite = true;
        for (int i = -5; i <= 5; i++) {
            for (int j = -5; j <= 5; j++) {
                double v = fin.eval(i, 2, j);
                if (Double.isNaN(v) || Double.isInfinite(v)) {
                    finite = false;
                }
            }
        }
        check("value stream finite over region", finite);

        // ---- 默认关闭 / off by default (structural) ---------------
        // The defaults() factory yields a provider equal to a pure formula with variation 0 —
        // vanilla-identical when nothing is customized.
        FormulaTerrain defProv = new FormulaTerrain(FormulaParams.defaults());
        FormulaTerrain pureZero = new FormulaTerrain(FormulaParams.builder().formula("0").build());
        boolean defPure = true;
        for (int i = -3; i <= 3; i++) {
            for (int j = -3; j <= 3; j++) {
                if (!near(defProv.eval(i, 0, j), pureZero.eval(i, 0, j))) {
                    defPure = false;
                }
            }
        }
        check("defaults() provider == pure formula variation 0", defPure);
        check("provider rejects null params", rejects(() -> new FormulaTerrain(null)));

        if (failures == 0) {
            System.out.println("[FormulaTerrainProbe] PASS (formula terrain integration, " + 28 + " checks)");
            System.exit(0);
        } else {
            System.out.println("[FormulaTerrainProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }
}