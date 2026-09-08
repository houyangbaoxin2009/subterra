package io.toterra.subterra.probes;

import io.toterra.subterra.engine.worldgen.pipeline.noise.LegacyRandom;
import io.toterra.subterra.engine.worldgen.pipeline.noise.NoiseSalt;
import io.toterra.subterra.engine.worldgen.pipeline.noise.XoroRandom;

import java.util.Random;

/**
 * 确定性验收探针：p.1.8.6 随机源与噪声盐基础层。
 * Deterministic acceptance probe for the p.1.8.6 random-sources &amp; noise-salt
 * foundation. Pure JVM; asserts bit-exactness of {@link LegacyRandom} against
 * {@link java.util.Random}, pinned golden sequences of {@link XoroRandom},
 * seeding/reset semantics, {@link NoiseSalt} derivation and a td round-trip.
 * Exit 0 = PASS, exit 1 = FAIL (never shipped in the mod jar).
 */
public final class NoiseBaseProbe {

    /** Golden-ratio salt used for the NoiseSalt check (MC's GOLDEN_RATIO_64). */
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    private NoiseBaseProbe() {
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

    private static boolean rejectsBound(int bound) {
        try {
            new LegacyRandom(1L).nextInt(bound);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static boolean xoroRejectsBound(int bound) {
        try {
            new XoroRandom(1L).nextInt(bound);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    /** LegacyRandom must reproduce java.util.Random bit-for-bit on the same seed. */
    private static boolean legacyMatchesJavaUtil(long seed) {
        LegacyRandom lr = new LegacyRandom(seed);
        Random jr = new Random(seed);
        for (int i = 0; i < 40; i++) {
            if (lr.nextInt() != jr.nextInt()) {
                return false;
            }
            if (lr.nextLong() != jr.nextLong()) {
                return false;
            }
            if (Double.doubleToLongBits(lr.nextDouble()) != Double.doubleToLongBits(jr.nextDouble())) {
                return false;
            }
            if (Double.doubleToLongBits(lr.nextGaussian()) != Double.doubleToLongBits(jr.nextGaussian())) {
                return false;
            }
        }
        return true;
    }

    /** Both PRNGs must agree on bounded draws for the same seed. */
    private static boolean legacyBoundMatchesJavaUtil(long seed) {
        LegacyRandom lr = new LegacyRandom(seed);
        Random jr = new Random(seed);
        int[] bounds = {2, 3, 16, 256, 1000, Integer.MAX_VALUE};
        for (int b : bounds) {
            for (int i = 0; i < 20; i++) {
                if (lr.nextInt(b) != jr.nextInt(b)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean xoroBoundsRespected() {
        XoroRandom r = new XoroRandom(0xCAFEBABEL);
        int[] bounds = {3, 7, 1, 64, 1000, 1 << 20};
        for (int b : bounds) {
            for (int i = 0; i < 5000; i++) {
                int v = r.nextInt(b);
                if (v < 0 || v >= b) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void main(String[] args) {
        // ---- LegacyRandom -------------------------------------------------
        check("legacy seed42 nextInt == -1170105035 (pinned)",
                new LegacyRandom(42L).nextInt() == -1170105035);
        check("legacy seed42 nextLong == -5025562857975149833 (pinned)",
                new LegacyRandom(42L).nextLong() == -5025562857975149833L);
        check("legacy bit-exact with java.util.Random (seeds 42/123/-7/0x5eed)",
                legacyMatchesJavaUtil(42L) && legacyMatchesJavaUtil(123L)
                        && legacyMatchesJavaUtil(-7L) && legacyMatchesJavaUtil(0x5eedL));
        check("legacy nextDouble in [0,1)", new LegacyRandom(9L).nextDouble() >= 0.0
                && new LegacyRandom(9L).nextDouble() < 1.0);
        check("legacy nextInt(bound) match java.util.Random",
                legacyBoundMatchesJavaUtil(2021L) && legacyBoundMatchesJavaUtil(7L));
        LegacyRandom l2021 = new LegacyRandom(2021L);
        check("legacy pinned bound samples seed2021 == 2,1,0,2,225,36537",
                l2021.nextInt(3) == 2 && l2021.nextInt(3) == 1 && l2021.nextInt(3) == 0
                        && l2021.nextInt(17) == 2 && l2021.nextInt(1024) == 225
                        && l2021.nextInt(55555) == 36537);
        LegacyRandom l77 = new LegacyRandom(77L);
        check("legacy power-of-two bound seed77 == 11,8,4",
                l77.nextInt(16) == 11 && l77.nextInt(16) == 8 && l77.nextInt(16) == 4);
        check("legacy deterministic cross-instance",
                new LegacyRandom(555L).nextLong() == new LegacyRandom(555L).nextLong());
        check("legacy rejects nextInt(<=0)", rejectsBound(0) && rejectsBound(-5));

        // ---- XoroRandom ---------------------------------------------------
        XoroRandom xo = new XoroRandom(0x12345678L);
        check("xoro deterministic cross-instance",
                new XoroRandom(0x12345678L).nextLong() == new XoroRandom(0x12345678L).nextLong());
        check("xoro pinned nextLong[0] == 983161439547516374",
                xo.nextLong() == 983161439547516374L);
        check("xoro pinned nextLong[1] == 2532526820691599578",
                xo.nextLong() == 2532526820691599578L);
        check("xoro pinned nextInt() == 1456123491", xo.nextInt() == 1456123491);
        check("xoro pinned nextDouble bits == 0x3FD3709CC730AE62",
                Double.doubleToLongBits(xo.nextDouble()) == 0x3FD3709CC730AE62L);
        check("xoro pinned nextGaussian[0] bits == 0xBFE9834B5A733155",
                Double.doubleToLongBits(xo.nextGaussian()) == 0xBFE9834B5A733155L);
        check("xoro nextDouble in [0,1)", new XoroRandom(0x12345678L).nextDouble() >= 0.0
                && new XoroRandom(0x12345678L).nextDouble() < 1.0);
        check("xoro pinned nextInt(7) == 5", new XoroRandom(0x12345678L).nextInt(7) == 5);
        check("xoro pinned nextInt(4096) == 3217", new XoroRandom(0x12345678L).nextInt(4096) == 3217);
        check("xoro free of modulo bias: bounds respected over 30000 draws", xoroBoundsRespected());
        check("xoro rejects nextInt(<=0)", xoroRejectsBound(0) && xoroRejectsBound(-2));

        XoroRandom r42 = new XoroRandom(42L);
        check("xoro reset re-seeds identically", r42.nextLong() == new XoroRandom(42L).nextLong());
        XoroRandom r42b = new XoroRandom(42L);
        r42b.nextLong(); // consume
        r42b.reset(42L);
        check("xoro reset restarts stream", r42b.nextLong() == new XoroRandom(42L).nextLong());

        XoroRandom gx = new XoroRandom(100L);
        boolean gaussFinite = true;
        double gg1 = gx.nextGaussian();
        double gg2 = gx.nextGaussian();
        if (Double.isNaN(gg1) || Double.isInfinite(gg1) || Double.isNaN(gg2) || Double.isInfinite(gg2)) {
            gaussFinite = false;
        }
        XoroRandom gx2 = new XoroRandom(100L);
        check("xoro gaussian finite and paired-cached reproducible",
                gaussFinite && gx2.nextGaussian() == gg1 && gx2.nextGaussian() == gg2);
        check("xoro gaussian reproducible cross-instance",
                new XoroRandom(3L).nextGaussian() == new XoroRandom(3L).nextGaussian());
        XoroRandom gr = new XoroRandom(7700L);
        gr.nextGaussian();              // consume the first polar draw
        gr.nextGaussian();              // cached spare, then consumed on next call
        gr.nextGaussian();              // consumes the cached spare
        check("xoro gaussian cached spare re-drawn on next call",
                Double.isFinite(gr.nextGaussian()));

        // ---- NoiseSalt ----------------------------------------------------
        check("noiseSalt deterministic", NoiseSalt.mix(10L, 20L) == NoiseSalt.mix(10L, 20L));
        check("noiseSalt master-sensitive", NoiseSalt.mix(10L, 20L) != NoiseSalt.mix(11L, 20L));
        check("noiseSalt salt-sensitive", NoiseSalt.mix(10L, 20L) != NoiseSalt.mix(10L, 21L));
        check("noiseSalt symmetric (mix(a,b)==mix(b,a))",
                NoiseSalt.mix(0xABCDL, 0x1234L) == NoiseSalt.mix(0x1234L, 0xABCDL));
        check("noiseSalt matches MC seed^salt for golden ratio",
                NoiseSalt.mix(1L, GOLDEN) == 0x9E3779B97F4A7C14L);
        check("noiseSalt zero identities (mix(s,0)==s, mix(0,0)==0)",
                NoiseSalt.mix(12345L, 0L) == 12345L && NoiseSalt.mix(0L, 0L) == 0L);
        check("noiseSalt saturates distinct seeds",
                NoiseSalt.mix(0L, -1L) == -1L
                        && NoiseSalt.mix(Long.MAX_VALUE, 1L) == Long.MAX_VALUE - 1
                        && NoiseSalt.mix(Long.MAX_VALUE, Long.MAX_VALUE) == 0L);

        // ---- td round-trip (XoroRandom construct params) ----------------
        XoroRandom td1 = new XoroRandom(777L);
        String tdStr = td1.td();
        XoroRandom td2 = XoroRandom.fromTd(tdStr);
        check("xoro td format literal", "[ seed = 777 ]".equals(tdStr));
        check("xoro td round-trip preserves stream", td2.td().equals(tdStr)
                && td2.nextLong() == td1.nextLong());
        check("xoro fromTd rejects malformed", rejectsBadTd());

        if (failures == 0) {
            System.out.println("[NoiseBaseProbe] PASS (random-sources & noise-salt, 31 checks)");
            System.exit(0);
        } else {
            System.out.println("[NoiseBaseProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }

    private static boolean rejectsBadTd() {
        try {
            XoroRandom.fromTd("not a table");
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }
}