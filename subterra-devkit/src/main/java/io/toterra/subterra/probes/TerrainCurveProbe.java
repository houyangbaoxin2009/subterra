package io.toterra.subterra.probes;

import io.toterra.subterra.optim.worldgen.pipeline.curve.FbmNoise;
import io.toterra.subterra.optim.worldgen.pipeline.curve.GroundSurface;
import io.toterra.subterra.optim.worldgen.pipeline.curve.TerrainProfile;
import io.toterra.subterra.optim.worldgen.pipeline.density.ValueNoise;

/**
 * 确定性验收探针：p.1.8.4 terrain-curve 核心（FbmNoise / GroundSurface / TerrainProfile）。
 * Deterministic acceptance probe for the p.1.8.4 terrain-curve core. Pure JVM;
 * asserts the intended semantics of the fBm floor, ground surface and the
 * continentality/elevation curve exactly (deterministic, no timing asserts).
 */
public final class TerrainCurveProbe {

    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    private TerrainCurveProbe() {
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

    /** FbmNoise 构造校验：非正 scale 必须被拒绝。Rejects a non-positive scale. */
    private static boolean rejectsScale(double scale) {
        try {
            new FbmNoise(1L, scale, 2, 0.5);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    /** FbmNoise 构造校验：octaves<1 必须被拒绝。Rejects octaves below one. */
    private static boolean rejectsOctaves(int octaves) {
        try {
            new FbmNoise(1L, 8.0, octaves, 0.5);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    /** FbmNoise 构造校验：persistence 越界（<=0 或 >1）必须被拒绝。Rejects out-of-range persistence. */
    private static boolean rejectsPersistence(double p) {
        try {
            new FbmNoise(1L, 8.0, 2, p);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    public static void main(String[] args) {
        // ---- FbmNoise：构造校验 -------------------------------------------
        check("fbm rejects scale <= 0", rejectsScale(0.0) && rejectsScale(-4.0));
        check("fbm rejects octaves < 1", rejectsOctaves(0));
        check("fbm rejects persistence <= 0", rejectsPersistence(0.0) && rejectsPersistence(-0.5));
        check("fbm rejects persistence > 1", rejectsPersistence(1.5) && rejectsPersistence(Double.NaN));
        check("fbm rejects illegal base noise", reusesValueNoiseValidation());

        // ---- FbmNoise：确定性 ---------------------------------------------
        FbmNoise fa = new FbmNoise(12345L, 16.0, 4, 0.5);
        double va = fa.eval(1.5, 2.5, 3.5);
        check("fbm same instance reproducible", va == fa.eval(1.5, 2.5, 3.5));
        check("fbm cross-instance reproducible", near(va, new FbmNoise(12345L, 16.0, 4, 0.5).eval(1.5, 2.5, 3.5)));
        check("fbm different seed differs", !near(va, new FbmNoise(999L, 16.0, 4, 0.5).eval(1.5, 2.5, 3.5)));

        // ---- FbmNoise：octaves=1 等于单层 value noise ----------------------
        FbmNoise single = new FbmNoise(12345L, 16.0, 1, 0.5);
        // octave 0 uses sub-seed seed+1*GOLDEN and scale 16.0 exactly.
        ValueNoise base = new ValueNoise(12345L + GOLDEN, 16.0);
        check("fbm octaves=1 equals single ValueNoise", near(single.eval(1.5, 2.5, 3.5), base.eval(1.5, 2.5, 3.5)));

        // ---- FbmNoise：归一化范围 [-1,1] ----------------------------------
        FbmNoise range = new FbmNoise(7L, 8.0, 5, 0.6);
        boolean inRange = true;
        for (double dx = -20.0; dx <= 20.0; dx += 0.7) {
            for (double dz = -20.0; dz <= 20.0; dz += 0.7) {
                double v = range.eval(dx, 0.0, dz);
                if (v < -1.0 || v > 1.0) {
                    inRange = false;
                }
            }
        }
        check("fbm normalized range [-1,1]", inRange);

        // ---- FbmNoise：更高 octaves 有贡献 ----------------------------------
        FbmNoise p1 = new FbmNoise(50L, 8.0, 1, 0.5);
        FbmNoise p3 = new FbmNoise(50L, 8.0, 3, 0.5);
        // Octave 0 dominates; higher octaves (persistence 0.5) add detail, so the
        // 3-octave field differs from the single-layer one off-lattice.
        check("fbm higher octaves add detail", !near(p1.eval(1.2, 3.4, 5.6), p3.eval(1.2, 3.4, 5.6)));

        // ---- GroundSurface：确定性与边界 ----------------------------------
        GroundSurface seam = new GroundSurface(new FbmNoise(3L, 10.0, 2, 0.5), 63.0, 0.0, 63.0);
        check("ground height deterministic", seam.height(4, 4) == seam.height(4, 4));
        check("ground boundary height == seaLevel", near(seam.height(4, 4), 63.0));
        // Semantics: exactly at sea level -> NOT underwater -> isOcean false.
        check("ground exactly at seaLevel is not underwater", !seam.isUnderwater(4, 4));
        check("ground terrainAt at seaLevel returns sea", near(seam.terrainAt(4, 4), 63.0));

        GroundSurface ocean = new GroundSurface(new FbmNoise(11L, 12.0, 3, 0.4), 0.0, 10.0, 63.0);
        check("ground underwater is detected", ocean.isUnderwater(0, 0) && ocean.isUnderwater(-7, 3));
        check("ground underwater terrainAt caps to sea", near(ocean.terrainAt(0, 0), 63.0) && near(ocean.terrainAt(5, -2), 63.0));
        check("ground height/reproducible cross-instance",
                near(new GroundSurface(new FbmNoise(11L, 12.0, 3, 0.4), 0.0, 10.0, 63.0).height(2, 2),
                        ocean.height(2, 2)));

        // ---- TerrainProfile：确定性 + surfaceY/height 一致性 ---------------
        TerrainProfile tpOcean = new TerrainProfile(new FbmNoise(9L, 6.0, 2, 0.5), 63, 0, 8);
        check("profile surfaceY deterministic", tpOcean.surfaceY(3, 1) == tpOcean.surfaceY(3, 1));
        // surfaceY must equal floor of the underlying ground height.
        GroundSurface tpGround = new GroundSurface(tpOcean.continentality(),
                (double) tpOcean.baseHeight(), (double) tpOcean.amplitude(), (double) tpOcean.seaLevel());
        boolean floorOk = true;
        for (int i = -5; i <= 5; i++) {
            for (int j = -5; j <= 5; j++) {
                if (tpOcean.surfaceY(i, j) != (int) Math.floor(tpGround.height(i, j))) {
                    floorOk = false;
                }
            }
        }
        check("profile surfaceY == floor(height)", floorOk);

        TerrainProfile seamProfile =
                new TerrainProfile(new FbmNoise(1L, 5.0, 1, 0.5), 63, 63, 0);
        check("profile exactly at seaLevel is not ocean", !seamProfile.isOcean(2, 2));
        check("profile surfaceY at seaLevel == seaLevel", seamProfile.surfaceY(2, 2) == 63);
        check("profile ocean column detected", tpOcean.isOcean(3, 1));
        check("profile underwater surfaceY below sea", tpOcean.surfaceY(3, 1) < 63);
        check("profile rejects null continentality", rejectsNullContinentality());
        check("profile rejects negative amplitude", rejectsNegativeAmplitude());

        // ---- 大样本不炸（调用计数确定，禁时序断言）------------------------
        FbmNoise big = new FbmNoise(6L, 7.0, 4, 0.5);
        long evals = 0;
        for (int i = 0; i < 10_000; i++) {
            int cx = (i % 100) - 50;
            int cz = (i / 100) - 50;
            double v = big.eval(cx, 0.0, cz);
            if (v >= -1.0 && v <= 1.0) {
                evals++;
            }
        }
        check("fbm 10000 evals run (counting, no timing)", evals == 10_000);

        if (failures == 0) {
            System.out.println("[TerrainCurveProbe] PASS (terrain-curve core, " + 27 + " checks)");
            System.exit(0);
        } else {
            System.out.println("[TerrainCurveProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }

    /** ValueNoise 的构造校验被复用：scale<=0 时底层噪声本身即拒绝。 */
    private static boolean reusesValueNoiseValidation() {
        try {
            new FbmNoise(1L, 0.0, 1, 0.5);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean rejectsNullContinentality() {
        try {
            new TerrainProfile(null, 63, 0, 8);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean rejectsNegativeAmplitude() {
        try {
            new TerrainProfile(new FbmNoise(1L, 8.0, 2, 0.5), 63, 0, -1);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}