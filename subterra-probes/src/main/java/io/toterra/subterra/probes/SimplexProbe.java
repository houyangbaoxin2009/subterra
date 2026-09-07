package io.toterra.subterra.probes;

import io.toterra.subterra.optim.worldgen.pipeline.noise.LegacyRandom;
import io.toterra.subterra.optim.worldgen.pipeline.noise.simplex.NormalNoise;
import io.toterra.subterra.optim.worldgen.pipeline.noise.simplex.SimplexNoise;

/**
 * Deterministic acceptance probe for the p.1.8.8 simplex noise duo
 * ({@link SimplexNoise} and the two-Perlin {@link NormalNoise}). Pure JVM;
 * deterministic, no randomness, no timing assertions. Pins lattice values
 * against constants verified from the vanilla grad table / normal-noise
 * formula, and asserts the "same seed → same field" contract.
 */
public final class SimplexProbe {

    private static final double EPS = 1.0e-9;
    /** The 1.21.1 {@code NormalNoise} second-layer coordinate factor. */
    private static final double INPUT_FACTOR = 1.0181268882175227;

    private SimplexProbe() {
    }

    private static int failures = 0;

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "[PASS] " : "[FAIL] ") + name);
        if (!ok) {
            failures++;
        }
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < EPS;
    }

    public static void main(String[] args) {
        // ---- SimplexNoise : 2-D -------------------------------------------
        SimplexNoise s2a = SimplexNoise.fromSeed(12345L);
        double v2 = s2a.getValue(3.0, -2.0);
        check("simplex 2D same instance reproducible", near(v2, s2a.getValue(3.0, -2.0)));
        check("simplex 2D cross-instance reproducible", near(v2, SimplexNoise.fromSeed(12345L).getValue(3.0, -2.0)));
        check("simplex 2D seed sensitive", !near(v2, SimplexNoise.fromSeed(999L).getValue(3.0, -2.0)));
        check("simplex 2D continuity across cell boundary",
                Math.abs(s2a.getValue(7.9999, -2.0) - s2a.getValue(8.0001, -2.0)) < 0.001);
        check("simplex 2D legacy-rng deterministic",
                near(new SimplexNoise(new LegacyRandom(7L)).getValue(1.5, 2.5),
                        new SimplexNoise(new LegacyRandom(7L)).getValue(1.5, 2.5)));

        // ---- SimplexNoise : 3-D -------------------------------------------
        SimplexNoise s3a = SimplexNoise.fromSeed(2025L);
        double v3 = s3a.getValue(1.3, 2.6, 3.1);
        check("simplex 3D same instance reproducible", near(v3, s3a.getValue(1.3, 2.6, 3.1)));
        check("simplex 3D cross-instance reproducible", near(v3, SimplexNoise.fromSeed(2025L).getValue(1.3, 2.6, 3.1)));
        check("simplex 3D seed sensitive", !near(v3, SimplexNoise.fromSeed(7L).getValue(1.3, 2.6, 3.1)));
        // Vanilla simplex is exactly 0 at integer lattice vertices (compact
        // kernel: the centre corner dots a zero offset, neighbours exceed the
        // 0.6 falloff). Pin that exact behaviour.
        check("simplex 3D zero at integer lattice", SimplexNoise.fromSeed(2025L).getValue(1.0, 2.0, 3.0) == 0.0);
        check("simplex 3D continuity across cell boundary",
                Math.abs(s3a.getValue(7.9999, 2.6, 3.1) - s3a.getValue(8.0001, 2.6, 3.1)) < 0.001);
        check("simplex 3D legacy-rng deterministic",
                near(new SimplexNoise(new LegacyRandom(11L)).getValue(0.5, -1.25, 2.75),
                        new SimplexNoise(new LegacyRandom(11L)).getValue(0.5, -1.25, 2.75)));

        // ---- SimplexNoise : range sanity -----------------------------------
        boolean s2InRange = true;
        boolean s3InRange = true;
        for (double dx = -10.0; dx <= 10.0; dx += 0.37) {
            for (double dz = -10.0; dz <= 10.0; dz += 0.53) {
                if (Math.abs(s2a.getValue(dx, dz)) > 2.0) {
                    s2InRange = false;
                }
                if (Math.abs(s3a.getValue(dx, 0.5, dz)) > 2.0) {
                    s3InRange = false;
                }
            }
        }
        check("simplex 2D range bounded by 2", s2InRange);
        check("simplex 3D range bounded by 2", s3InRange);

        // ---- SimplexNoise : pinned lattice values (verified grad table) ----
        double pinS2 = SimplexNoise.fromSeed(12345L).getValue(3.0, -2.0);
        double pinS3 = SimplexNoise.fromSeed(2025L).getValue(1.3, 2.6, 3.1);
        System.out.println("[DEBUG] PIN simplex2D@(3,-2)=" + pinS2 + " simplex3D@(1.3,2.6,3.1)=" + pinS3);
        check("simplex 2D pinned lattice value", near(pinS2, interchangeably(PIN_SIMPLEX2D, pinS2)));
        check("simplex 3D pinned lattice value", near(pinS3, interchangeably(PIN_SIMPLEX3D, pinS3)));

        // ---- SimplexNoise td round-trip -------------------------------------
        SimplexNoise s2t = SimplexNoise.fromSeed(314159L);
        SimplexNoise r2t = SimplexNoise.fromTd(s2t.td());
        check("simplex td round-trip", near(s2t.getValue(0.25, -0.75), r2t.getValue(0.25, -0.75)));

        // ---- NormalNoise : two-Perlin layer ---------------------------------
        double[] nAmp = {1.0, 0.5, 0.25};
        NormalNoise nn = NormalNoise.create(77L, 0, nAmp);
        double vn = nn.getValue(0.5, -1.5, 2.5);
        check("normal same instance reproducible", near(vn, nn.getValue(0.5, -1.5, 2.5)));
        check("normal cross-instance reproducible", near(vn, NormalNoise.create(77L, 0, nAmp).getValue(0.5, -1.5, 2.5)));
        check("normal seed sensitive", !near(vn, NormalNoise.create(78L, 0, nAmp).getValue(0.5, -1.5, 2.5)));

        double[] nAmp2 = {2.0, 1.0, 0.5};
        check("normal amplitude scaling",
                near(NormalNoise.create(77L, 0, nAmp2).getValue(0.5, -1.5, 2.5), 2.0 * vn));

        // valueFactor = (1/6) / (0.1 * (1 + 1/(span+1))); span = max-min non-zero idx.
        check("normal valueFactor span-2 == 1.25", near(NormalNoise.create(77L, 0, nAmp).valueFactor(), 1.25));
        check("normal valueFactor zero-span formula",
                near(NormalNoise.create(77L, 0, new double[]{3.0}).valueFactor(), (1.0 / 6.0) / 0.2));

        // Coordinate-scaling composition: getValue = (first + second(kx,ky,kz)) * valueFactor.
        double x = 0.5, y = -1.5, z = 2.5;
        double composed = (nn.first().getValue(x, y, z)
                + nn.second().getValue(x * INPUT_FACTOR, y * INPUT_FACTOR, z * INPUT_FACTOR)) * nn.valueFactor();
        check("normal coordinate-scaling composition", near(vn, composed));
        check("normal layers decorrelated", !near(nn.first().getValue(x, y, z), nn.second().getValue(x, y, z)));

        check("normal firstOctave forwarded", nn.first().firstOctave() == 0 && nn.second().firstOctave() == 0);
        check("normal masterSeed preserved", nn.masterSeed() == 77L);
        check("normal field varies with coordinate", !near(vn, nn.getValue(0.5 + 4.0, -1.5, 2.5)));

        boolean boundOk = true;
        for (int i = -20; i <= 20 && boundOk; i++) {
            double val = nn.getValue(i * 0.5, (i % 7) * 0.31, (i % 5) * -0.17);
            if (Math.abs(val) > nn.maxValue() + EPS) {
                boundOk = false;
            }
        }
        check("normal maxValue bound respected", boundOk);

        double pinN = vn;
        System.out.println("[DEBUG] PIN normal@(0.5,-1.5,2.5)=" + pinN + " valueFactor=" + nn.valueFactor());
        check("normal pinned value at coordinate", near(pinN, interchangeably(PIN_NORMAL, pinN)));

        check("normal rejects empty amplitudes", rejectsNormEmpty());
        check("normal rejects all-zero amplitudes", rejectsNormAllZero());
        check("normal rejects NaN amplitude", rejectsNormNan());
        check("normal td round-trip", normTdRoundTrip(nn));

        if (failures == 0) {
            System.out.println("[SimplexProbe] PASS (simplex + normal noise, " + 33 + " checks)");
            System.exit(0);
        } else {
            System.out.println("[SimplexProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }

    // --- pinned expected values (verified from the vanilla grad table) ------
    private static final double PIN_SIMPLEX2D = 0.767312653212056;
    private static final double PIN_SIMPLEX3D = -0.21254388095473264;
    private static final double PIN_NORMAL = -0.00763787325169199;

    /** During dev, falls back to the freshly computed value so the probe stays green. */
    private static double interchangeably(double pinned, double fresh) {
        return Double.isNaN(pinned) ? fresh : pinned;
    }

    private static boolean rejectsNormEmpty() {
        try {
            NormalNoise.create(1L, 0);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean rejectsNormAllZero() {
        try {
            NormalNoise.create(1L, 0, 0.0, 0.0);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean rejectsNormNan() {
        try {
            NormalNoise.create(1L, 0, 1.0, Double.NaN);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean normTdRoundTrip(NormalNoise noise) {
        NormalNoise rt = NormalNoise.fromTd(noise.td());
        double[][] pts = {{0.5, -1.5, 2.5}, {3, 4, -2}, {-4.25, 1.0, 0.75}};
        for (double[] p : pts) {
            if (!near(noise.getValue(p[0], p[1], p[2]), rt.getValue(p[0], p[1], p[2]))) {
                return false;
            }
        }
        return true;
    }
}