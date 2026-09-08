package io.toterra.subterra.probes;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import io.toterra.subterra.optim.worldgen.pipeline.density.Density;
import io.toterra.subterra.optim.worldgen.pipeline.router.BlendedNoise;
import io.toterra.subterra.optim.worldgen.pipeline.router.InterpolatedNoise;
import io.toterra.subterra.optim.worldgen.pipeline.router.NoiseRouter;
import io.toterra.subterra.optim.worldgen.pipeline.router.PositionalRand;

/**
 * 确定性验收探针：p.1.8.12 噪声路由器组合核心（PositionalRand / BlendedNoise /
 * InterpolatedNoise / NoiseRouter 15 字段装配表）。Deterministic acceptance probe.
 * Pure JVM; asserts: the MD5-based {@code fromHashOf}/{@code forkPositional} seed
 * chain against a JDK {@link MessageDigest} reference vector (NOTE: MC 1.21.1 hashes
 * with MD5, not SHA-256), octave-blend determinism/finiteness, and the full
 * 15-field overworld router — presence, finiteness, determinism across two fresh
 * assemblies, world-seed sensitivity, and the pinned vanilla overworld constants.
 */
public final class NoiseRouterProbe {

    private NoiseRouterProbe() {
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

    private static boolean finite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    /** JDK MD5 reference vector for a label (hand-computed via MessageDigest). */
    private static String md5Ref(String label) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] h = md.digest(label.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) {
                int v = b & 0xFF;
                sb.append(Character.forDigit(v >>> 4, 16)).append(Character.forDigit(v & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Density[] fields(NoiseRouter r) {
        Density[] a = new Density[r.fieldCount()];
        for (int i = 0; i < a.length; i++) {
            a[i] = r.fieldAt(i);
        }
        return a;
    }

    public static void main(String[] args) {
        long seed = 0x5EED_CAFE_1234_5678L;

        // ============ (a) PositionalRand : MD5 seed chain ============
        // --- forkPositional / ofMaster reproducibility ---
        PositionalRand base1 = PositionalRand.ofMaster(seed);
        PositionalRand base2 = PositionalRand.ofMaster(seed);
        check("pr ofMaster reproducible across instances",
                base1.seedLo() == base2.seedLo() && base1.seedHi() == base2.seedHi());
        check("pr ofMaster differs across world seeds",
                base1.seedLo() != PositionalRand.ofMaster(seed + 1L).seedLo()
                        || base1.seedHi() != PositionalRand.ofMaster(seed + 1L).seedHi());

        // --- fromHashOf label XOR semantics vs verified MD5 vector ---
        String label = "minecraft:temperature";
        PositionalRand child = base1.fromHashOf(label);
        long[] pair = PositionalRand.md5Pair(label);
        check("pr fromHashOf = MD5(label) XOR base",
                child.seedLo() == (pair[0] ^ base1.seedLo())
                        && child.seedHi() == (pair[1] ^ base1.seedHi()));

        // --- MD5 against the JDK MessageDigest reference vector ---
        check("pr md5 vector 'minecraft:temperature'",
                PositionalRand.md5Hex(label).equals(md5Ref(label)));
        check("pr md5 lower-case hex",
                PositionalRand.md5Hex(label).equals(PositionalRand.md5Hex(label).toLowerCase()));
        check("pr md5 differs between labels",
                !PositionalRand.md5Hex("minecraft:temperature").equals(
                        PositionalRand.md5Hex("minecraft:contentalness")));

        // --- different labels produce different derived sources ---
        PositionalRand a = base1.fromHashOf("minecraft:continents");
        PositionalRand b = base1.fromHashOf("minecraft:erosion");
        check("pr different labels differ",
                a.seedLo() != b.seedLo() || a.seedHi() != b.seedHi());

        // --- fromHashOf determinism across independent factories ---
        PositionalRand c1 = PositionalRand.ofMaster(seed).fromHashOf(label);
        PositionalRand c2 = PositionalRand.ofMaster(seed).fromHashOf(label);
        check("pr fromHashOf deterministic across instances",
                c1.seedLo() == c2.seedLo() && c1.seedHi() == c2.seedHi());

        // --- fromSeed semantics ---
        PositionalRand fs = base1.fromSeed(99L);
        check("pr fromSeed = (seed ^ baseLo, seed ^ baseHi)",
                fs.seedLo() == (99L ^ base1.seedLo()) && fs.seedHi() == (99L ^ base1.seedHi()));

        // --- deriveLong convenience ---
        check("pr deriveLong deterministic", PositionalRand.deriveLong(seed, label)
                == PositionalRand.deriveLong(seed, label));
        check("pr deriveLong differs across world seeds", PositionalRand.deriveLong(seed, label)
                != PositionalRand.deriveLong(seed + 7L, label));
        check("pr deriveLong differs across labels", PositionalRand.deriveLong(seed, "minecraft:temperature")
                != PositionalRand.deriveLong(seed, "minecraft:erosion"));

        // --- Xoroshiro128++ stream reproducibility ---
        PositionalRand s1 = base1.fromHashOf(label);
        PositionalRand s2 = base1.fromHashOf(label);
        long n1 = s1.nextLong();
        long n2 = s2.nextLong();
        check("pr stream nextLong reproducible", n1 == n2);
        double d1 = s1.nextDouble();
        check("pr stream nextDouble in [0,1)", d1 >= 0.0 && d1 < 1.0);
        check("pr nextInt(bound) in range", s2.nextInt(1000) >= 0 && s2.nextInt(1000) < 1000);
        check("pr nextInt bound validated",
                rejectsIntBound(base1.fromHashOf(label)));

        // --- td round-trip ---
        PositionalRand sa = base1.fromHashOf(label);
        String ptd = sa.td();
        PositionalRand sb = PositionalRand.fromTd(ptd);
        check("pr td round-trips state",
                sb.seedLo() == sa.seedLo() && sb.seedHi() == sa.seedHi());
        boolean streamEq = true;
        for (int k = 0; k < 8; k++) {
            streamEq &= sa.nextLong() == sb.nextLong();
        }
        check("pr td stream equals original stream", streamEq);
        check("pr td rejects malformed",
                rejectsTd("oops") && rejectsTd("[]"));

        // ============ (b) InterpolatedNoise : legacy seam ============
        InterpolatedNoise in1 = new InterpolatedNoise(seed);
        InterpolatedNoise in2 = new InterpolatedNoise(seed);
        double iv = in1.eval(12.5, 80.0, -33.25);
        check("in deterministic cross-instance", iv == in2.eval(12.5, 80.0, -33.25));
        check("in reproducible in-place", iv == in1.eval(12.5, 80.0, -33.25));
        check("in finite at pinned coords",
                finite(iv) && finite(in1.eval(-4.0, 0.5, 8.0)) && finite(in1.eval(0.0, 0.0, 0.0)));
        check("in seed sensitive", !near(iv, new InterpolatedNoise(seed + 41L).eval(12.5, 80.0, -33.25)));
        check("in y-sensitive within column",
                !near(in1.eval(12.5, 10.0, -33.25), in1.eval(12.5, 300.0, -33.25)));
        boolean inGridFinite = true;
        for (double dx = -8; dx <= 8; dx += 1.7) {
            for (double dz = -8; dz <= 8; dz += 1.7) {
                for (double dy = 0; dy <= 128; dy += 64) {
                    inGridFinite &= finite(in1.eval(dx, dy, dz));
                }
            }
        }
        check("in finite over grid", inGridFinite);

        // ============ (c) BlendedNoise : legacy seam ============
        BlendedNoise bn1 = new BlendedNoise(seed);
        BlendedNoise bn2 = new BlendedNoise(seed);
        double bv = bn1.eval(7.0, 60.0, 11.0);
        check("bn deterministic cross-instance", bv == bn2.eval(7.0, 60.0, 11.0));
        check("bn reproducible in-place", bv == bn1.eval(7.0, 60.0, 11.0));
        check("bn finite at pinned coords",
                finite(bv) && finite(bn1.eval(-3.0, 0.0, 2.0)) && finite(bn1.eval(0.0, 0.0, 0.0)));
        check("bn seed sensitive", !near(bv, new BlendedNoise(seed + 31L).eval(7.0, 60.0, 11.0)));
        boolean bnGridFinite = true;
        for (double dx = -6; dx <= 6; dx += 1.3) {
            for (double dz = -6; dz <= 6; dz += 1.3) {
                bnGridFinite &= finite(bn1.eval(dx, 50.0, dz));
            }
        }
        check("bn finite over grid", bnGridFinite);

        // ============ (d) NoiseRouter overworld assembly ============
        NoiseRouter r1 = NoiseRouter.overworld(seed);
        NoiseRouter r2 = NoiseRouter.overworld(seed, -64, 320);
        check("router has all 15 fields", r1.fieldCount() == 15
                && fields(r1).length == 15 && NoiseRouter.FIELD_NAMES.length == 15);

        // every field is a Density whose eval is finite + deterministic across builds
        boolean allDeterministic = true;
        boolean allFinite = true;
        for (int i = 0; i < 15; i++) {
            Density f1 = r1.fieldAt(i);
            Density f2 = r2.fieldAt(i);
            double v1 = f1.eval(12.5, 80.0, -33.25);
            double v2 = f2.eval(12.5, 80.0, -33.25);
            allDeterministic &= v1 == v2;
            allFinite &= finite(v1) && finite(f1.eval(-120.0, 40.0, 300.0))
                    && finite(f1.eval(0.0, 0.0, 0.0));
        }
        check("router all 15 deterministic across fresh assemblies", allDeterministic);
        check("router all 15 finite at pinned coords", allFinite);

        // canonical accessors expose the same objects as fieldAt
        boolean accessorsMatch = r1.temperature() == r1.fieldAt(4)
                && r1.vegetation() == r1.fieldAt(5)
                && r1.continents() == r1.fieldAt(6)
                && r1.erosion() == r1.fieldAt(7)
                && r1.depth() == r1.fieldAt(8)
                && r1.ridges() == r1.fieldAt(9)
                && r1.initialDensityWithoutJaggedness() == r1.fieldAt(10)
                && r1.finalDensity() == r1.fieldAt(11)
                && r1.veinToggle() == r1.fieldAt(12)
                && r1.veinRidged() == r1.fieldAt(13)
                && r1.veinGap() == r1.fieldAt(14)
                && r1.barrierNoise() == r1.fieldAt(0)
                && r1.fluidLevelFloodednessNoise() == r1.fieldAt(1)
                && r1.fluidLevelSpreadNoise() == r1.fieldAt(2)
                && r1.lavaNoise() == r1.fieldAt(3);
        check("router canonical accessors match fieldAt", accessorsMatch);

        // different worldSeed -> router differs (at least one field, and finalDensity)
        NoiseRouter r3 = NoiseRouter.overworld(seed + 123L);
        boolean routerDiffers = !near(r1.finalDensity().eval(12.5, 80.0, -33.25),
                r3.finalDensity().eval(12.5, 80.0, -33.25));
        boolean anyFieldDiffers = false;
        for (int i = 0; i < 15; i++) {
            if (!near(r1.fieldAt(i).eval(12.5, 80.0, -33.25), r3.fieldAt(i).eval(12.5, 80.0, -33.25))) {
                anyFieldDiffers = true;
            }
        }
        check("router different worldSeed differs", routerDiffers && anyFieldDiffers);

        // validation
        check("router rejects inverted Y range",
                rejectsY(seed) && rejectsY2(seed, 320, 320));

        // ============ (e) vanilla 1.21.1 noise registrations embedded (p.1.8.28) ============
        check("router pinned continentalness octave -9",
                NoiseRouter.registration("minecraft:continentalness").firstOctave() == -9);
        check("router pinned continentalness amplitudes [1,1,2,2,2,1,1,1,1]",
                Arrays.equals(NoiseRouter.registration("minecraft:continentalness").amplitudes(),
                        new double[]{1.0, 1.0, 2.0, 2.0, 2.0, 1.0, 1.0, 1.0, 1.0}));
        check("router pinned erosion octave -9 amplitudes [1,1,0,1,1]",
                NoiseRouter.registration("minecraft:erosion").firstOctave() == -9
                        && Arrays.equals(NoiseRouter.registration("minecraft:erosion").amplitudes(),
                        new double[]{1.0, 1.0, 0.0, 1.0, 1.0}));
        check("router pinned temperature octave -10 amps [1.5,0,1,0,0,0] (1.5 baked into the registration)",
                NoiseRouter.registration("minecraft:temperature").firstOctave() == -10
                        && Arrays.equals(NoiseRouter.registration("minecraft:temperature").amplitudes(),
                        new double[]{1.5, 0.0, 1.0, 0.0, 0.0, 0.0}));
        check("router pinned vegetation octave -8 amps [1,1,0,0,0,0]",
                NoiseRouter.registration("minecraft:vegetation").firstOctave() == -8
                        && Arrays.equals(NoiseRouter.registration("minecraft:vegetation").amplitudes(),
                        new double[]{1.0, 1.0, 0.0, 0.0, 0.0, 0.0}));
        check("router pinned ridge octave -7 amps [1,2,1,0,0,0]",
                NoiseRouter.registration("minecraft:ridge").firstOctave() == -7
                        && Arrays.equals(NoiseRouter.registration("minecraft:ridge").amplitudes(),
                        new double[]{1.0, 2.0, 1.0, 0.0, 0.0, 0.0}));
        check("router pinned shift (offset) octave -3 amps [1,1,1,0]",
                NoiseRouter.registration("minecraft:offset").firstOctave() == -3
                        && Arrays.equals(NoiseRouter.registration("minecraft:offset").amplitudes(),
                        new double[]{1.0, 1.0, 1.0, 0.0}));
        check("router pinned jagged -16 [1x16] and cave_entrance -7 [0.4,0.5,1.0]",
                NoiseRouter.registration("minecraft:jagged").firstOctave() == -16
                        && Arrays.equals(NoiseRouter.registration("minecraft:jagged").amplitudes(),
                        new double[]{1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0,
                                1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0})
                        && NoiseRouter.registration("minecraft:cave_entrance").firstOctave() == -7
                        && Arrays.equals(NoiseRouter.registration("minecraft:cave_entrance").amplitudes(),
                        new double[]{0.4, 0.5, 1.0}));
        check("router pinned aquifer xz-scales 0.5 / 0.67 / 0.7142857142857143",
                NoiseRouter.BARRIER_XZ_SCALE == 0.5
                        && NoiseRouter.FLOODEDNESS_XZ_SCALE == 0.67
                        && near(NoiseRouter.SPREAD_XZ_SCALE, 0.7142857142857143));

        // ============ (f) td round-trips / self description ============
        String rtd = r1.td();
        check("router td round-trips to same finalDensity",
                NoiseRouter.fromTd(rtd).finalDensity().eval(12.5, 80.0, -33.25)
                        == r1.finalDensity().eval(12.5, 80.0, -33.25));
        check("router td rejects malformed", rejectsRouterTd("[]"));

        if (failures == 0) {
            System.out.println("[NoiseRouterProbe] PASS (composition core, " + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[NoiseRouterProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }

    private static boolean rejectsIntBound(PositionalRand r) {
        try {
            r.nextInt(0);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean rejectsTd(String td) {
        try {
            PositionalRand.fromTd(td);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean rejectsRouterTd(String td) {
        try {
            NoiseRouter.fromTd(td);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean rejectsY(long seed) {
        try {
            NoiseRouter.overworld(seed, 320, -64);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean rejectsY2(long seed, int minY, int maxY) {
        try {
            NoiseRouter.overworld(seed, minY, maxY);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}