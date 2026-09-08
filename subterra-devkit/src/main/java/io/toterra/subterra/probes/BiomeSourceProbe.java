package io.toterra.subterra.probes;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.toterra.subterra.optim.worldgen.pipeline.biomesrc.BiomeTarget;
import io.toterra.subterra.optim.worldgen.pipeline.biomesrc.ClimateNoise;
import io.toterra.subterra.optim.worldgen.pipeline.biomesrc.ClimateParam;
import io.toterra.subterra.optim.worldgen.pipeline.biomesrc.MultiNoiseBiomeSourceCore;
import io.toterra.subterra.optim.worldgen.pipeline.router.NoiseRouter;

/**
 * 确定性验收探针：p.1.8.16 多噪声生物群系源核心（ClimateParam / ClimateBand /
 * ClimateNoise / BiomeTarget / MultiNoiseBiomeSourceCore）。Deterministic acceptance
 * probe. Pure JVM; asserts: quantization pin (MC 1.21.1 {@code QUANTIZATION_FACTOR =
 * 10000}, {@code quantize(0.5)==5000}), squared-distance monotonicity & unbounded
 * range boundaries, the climate sampler's determinism + cell-width (division factor
 * 4) pin, the embedded overworld target list (non-empty, count + id validity),
 * nearest-parameter selection (single target, nearer-wins, deterministic tie-break,
 * quantized boundary shift), and td round-trips. No timing asserts.
 */
public final class BiomeSourceProbe {

    private BiomeSourceProbe() {
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

    private static ClimateParam p(double t, double h, double c, double e, double d, double r) {
        return new ClimateParam(t, h, c, e, d, r);
    }

    private static boolean finite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    public static void main(String[] args) {
        long seed = 0x5EED_0000_0000_0016L;

        // ---------------------------------------------------------------
        // (a) ClimateParam : quantization + axis model
        // ---------------------------------------------------------------
        check("cp quantization factor 10000", ClimateParam.QUANTIZATION_FACTOR == 10000.0);
        check("cp quantize(0.5)==5000", ClimateParam.quantize(0.5) == 5000L);
        check("cp quantize(-0.5)==-5000", ClimateParam.quantize(-0.5) == -5000L);
        check("cp quantize(0.0)==0", ClimateParam.quantize(0.0) == 0L);
        check("cp dimensions==6", ClimateParam.DIMENSIONS == 6);

        ClimateParam cp = p(0.5, -0.25, 0.75, -1.0, 2.0, -0.5);
        check("cp accessors & axes", cp.temperature() == 0.5 && cp.humidity() == -0.25
                && cp.continentalness() == 0.75 && cp.erosion() == -1.0
                && cp.depth() == 2.0 && cp.ridges() == -0.5
                && cp.axis(0) == 0.5 && cp.axis(5) == -0.5);
        check("cp quantizedAxis mirrors quantize", cp.quantizedAxis(0) == ClimateParam.quantize(0.5)
                && cp.quantizedAxis(3) == ClimateParam.quantize(-1.0));

        // squared-distance monotonicity
        ClimateParam origin = p(0, 0, 0, 0, 0, 0);
        ClimateParam near = p(0.1, 0, 0, 0, 0, 0);
        ClimateParam far = p(0.3, 0, 0, 0, 0, 0);
        check("cp distance self == 0", origin.squaredDistance(origin) == 0);
        check("cp distance monotonic", origin.squaredDistance(far) > origin.squaredDistance(near)
                && origin.squaredDistance(near) > 0);
        check("cp distance symmetric", origin.squaredDistance(near) == near.squaredDistance(origin));
        check("cp distance single-axis equals square of quantized delta",
                origin.squaredDistance(p(0.1, 0, 0, 0, 0, 0))
                        == ClimateParam.square(ClimateParam.quantize(0.1)));

        // validation
        check("cp rejects NaN/Inf",
                rejectsBuild(true, Double.NaN, 0, 0, 0, 0, 0)
                        && rejectsBuild(false, 0, 0, 0, 0, 0, Double.POSITIVE_INFINITY));

        // td round-trip on simple (6-decimal-exact) values
        ClimateParam simple = p(0.05, -0.75, 0.25, -0.4, 0.3, 0.7);
        check("cp td round-trips point", ClimateParam.fromTd(simple.td()).equals(simple));
        check("cp td rejects malformed", rejectsTd("oops") && rejectsTd("temperature="));

        // ---------------------------------------------------------------
        // (b) Range / ClimateBand : unbounded + linear boundaries
        // ---------------------------------------------------------------
        ClimateParam.Range pt = ClimateParam.Range.point(0.0);
        ClimateParam.Range span = ClimateParam.Range.span(0.0, 1.0);
        check("range point inside == 0", pt.distance(ClimateParam.quantize(0)) == 0);
        check("range span inside == 0", span.distance(ClimateParam.quantize(0.5)) == 0);
        check("range span lower-bound distance",
                span.distance(ClimateParam.quantize(-0.5)) == 5000);
        check("range span upper-bound distance",
                span.distance(ClimateParam.quantize(1.5)) == 5000);
        check("range boundless distance 0",
                ClimateParam.Range.boundless().distance(ClimateParam.quantize(1234.5)) == 0);
        check("range maxBounded lower quiet",
                ClimateParam.Range.maxBounded(1.0).distance(ClimateParam.quantize(-0.5)) == 0);
        check("range maxBounded upper distance",
                ClimateParam.Range.maxBounded(1.0).distance(ClimateParam.quantize(5.0)) == 40000);
        check("range minBounded lower distance",
                ClimateParam.Range.minBounded(0.0).distance(ClimateParam.quantize(-0.25)) == 2500);
        check("range span rejects min>max", rejectsRangeSpan());

        ClimateParam.Range[] rs = new ClimateParam.Range[6];
        for (int i = 0; i < 6; i++) {
            rs[i] = ClimateParam.Range.boundless();
        }
        ClimateParam.ClimateBand full = new ClimateParam.ClimateBand(rs);
        check("band boundless fitness 0", origin.fitness(full) == 0);

        ClimateParam.ClimateBand box = new ClimateParam.ClimateBand(
                ClimateParam.Range.span(-1, -0.5), ClimateParam.Range.boundless(),
                ClimateParam.Range.boundless(), ClimateParam.Range.boundless(),
                ClimateParam.Range.boundless(), ClimateParam.Range.boundless());
        check("band fitness known axis", origin.fitness(box) == ClimateParam.square(5000)); // |0-(-5000)|=5000

        // ---------------------------------------------------------------
        // (c) ClimateNoise : sampler determinism + cell-width pin
        // ---------------------------------------------------------------
        check("climateNoise cell width == 4",
                ClimateNoise.CELL_WIDTH == 4 && MultiNoiseBiomeSourceCore.CELL_WIDTH == 4);
        NoiseRouter router = NoiseRouter.overworld(seed);
        ClimateNoise sampler = new ClimateNoise(router);
        ClimateNoise sampler2 = new ClimateNoise(NoiseRouter.overworld(seed));
        ClimateParam s1 = sampler.sample(3, 80, -2);
        ClimateParam s2 = sampler.sample(3, 80, -2);
        ClimateParam s3 = sampler2.sample(3, 80, -2);
        check("climateNoise deterministic in place", s1.equals(s2));
        check("climateNoise deterministic across fresh routers", s1.equals(s3));
        boolean finite6 = finite(s1.temperature()) && finite(s1.humidity()) && finite(s1.continentalness())
                && finite(s1.erosion()) && finite(s1.depth()) && finite(s1.ridges());
        check("climateNoise sample finite", finite6);
        ClimateParam blk = sampler.sampleAtBlock(3 * ClimateNoise.CELL_WIDTH,
                80 * ClimateNoise.CELL_WIDTH, -2 * ClimateNoise.CELL_WIDTH);
        check("climateNoise quart->block equals direct block sample", s1.equals(blk));
        check("climateNoise different coords differ",
                !s1.equals(sampler.sample(4, 80, -2)) || !s1.equals(sampler.sample(3, 90, -2)));
        ClimateNoise samplerB = new ClimateNoise(NoiseRouter.overworld(seed + 99L));
        check("climateNoise different seed may differ", !s1.equals(samplerB.sample(3, 80, -2)));

        // ---------------------------------------------------------------
        // (d) MultiNoiseBiomeSourceCore : embedded overworld targets
        // ---------------------------------------------------------------
        List<BiomeTarget> ow = MultiNoiseBiomeSourceCore.overworldTargets();
        check("overworld targets non-empty", !ow.isEmpty());
        check("overworld targets count pin", ow.size() == MultiNoiseBiomeSourceCore.OVERWORLD_TARGET_COUNT
                && ow.size() == MultiNoiseBiomeSourceCore.overworldTargetCount());
        boolean validIds = true;
        boolean idSet = true;
        Set<String> ids = new HashSet<>();
        for (BiomeTarget t : ow) {
            validIds &= t.id() != null && !t.id().isEmpty() && t.id().startsWith("minecraft:");
            idSet &= ids.add(t.id());
        }
        check("overworld targets valid ids", validIds);
        check("overworld targets ids unique", idSet && ids.size() == ow.size());
        boolean centersFinite = true;
        for (BiomeTarget t : ow) {
            for (int i = 0; i < ClimateParam.DIMENSIONS; i++) {
                centersFinite &= finite(t.center().axis(i));
            }
        }
        check("overworld targets centers finite", centersFinite);
        check("overworld targets has oceans/plains",
                ids.contains("minecraft:ocean") && ids.contains("minecraft:deep_ocean")
                        && ids.contains("minecraft:plains") && ids.contains("minecraft:forest"));

        // ---------------------------------------------------------------
        // (e) MultiNoiseBiomeSourceCore : nearest-parameter selection
        // ---------------------------------------------------------------
        BiomeTarget only = new BiomeTarget("minecraft:solo", p(0, 0, 0, 0, 0, 0), 0);
        check("pick single target always returns it",
                MultiNoiseBiomeSourceCore.pick(Collections.singletonList(only), p(1, 2, 3, -4, 5, -6))
                        .equals("minecraft:solo"));

        BiomeTarget a = new BiomeTarget("minecraft:a", p(0, 0, 0, 0, 0, 0), 0);
        BiomeTarget b = new BiomeTarget("minecraft:b", p(1.0, 0, 0, 0, 0, 0), 0);
        List<BiomeTarget> ab = List.of(a, b);
        check("pick nearer-left wins", MultiNoiseBiomeSourceCore.pick(ab, p(0.1, 0, 0, 0, 0, 0))
                .equals("minecraft:a"));
        check("pick nearer-right wins", MultiNoiseBiomeSourceCore.pick(ab, p(0.8, 0, 0, 0, 0, 0))
                .equals("minecraft:b"));
        check("pick known-distance boundary favors closer",
                MultiNoiseBiomeSourceCore.pick(ab, p(0.49, 0, 0, 0, 0, 0)).equals("minecraft:a"));

        // deterministic tie-break: identical centers, sample at that center -> first
        check("pick deterministic tie-break (lowest index)",
                MultiNoiseBiomeSourceCore.pick(ab, p(0, 0, 0, 0, 0, 0)).equals("minecraft:a"));

        // quantized fuzz shifts exact midpoint slightly: sample exactly 0.5 is a tie (a),
        // sample 0.5001 tips strictly to b because of truncation to integer units
        check("pick quantized midpoint ties to first",
                MultiNoiseBiomeSourceCore.pick(ab, p(0.5, 0, 0, 0, 0, 0)).equals("minecraft:a"));
        check("pick quantized fuzz shifts boundary",
                MultiNoiseBiomeSourceCore.pick(ab, p(0.5001, 0, 0, 0, 0, 0)).equals("minecraft:b"));

        // validation
        check("pick rejects empty targets", rejectsPick(Collections.emptyList(), p(0, 0, 0, 0, 0, 0)));

        // ---------------------------------------------------------------
        // (f) router-fed end-to-end
        // ---------------------------------------------------------------
        for (int[] cell : new int[][]{{0, 90, 0}, {1, 60, 1}, {-2, 40, 3}}) {
            ClimateParam sm = sampler.sample(cell[0], cell[1], cell[2]);
            String picked = MultiNoiseBiomeSourceCore.pick(ow, sm);
            String pickedAgain = MultiNoiseBiomeSourceCore.pick(ow, sampler.sample(cell[0], cell[1], cell[2]));
            check("e2e pick reproduces for cell (" + cell[0] + "," + cell[1] + "," + cell[2] + ")",
                    picked.equals(pickedAgain) && ids.contains(picked));
        }

        // ---------------------------------------------------------------
        // (g) td round-trips
        // ---------------------------------------------------------------
        check("core td round-trip",
                MultiNoiseBiomeSourceCore.fromTd(MultiNoiseBiomeSourceCore.td())
                        == MultiNoiseBiomeSourceCore.overworldTargetCount());
        check("core td rejects malformed", rejectsCoreTd("x") && rejectsCoreTd("n="));

        if (failures == 0) {
            System.out.println("[BiomeSourceProbe] PASS (multinoise core, " + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[BiomeSourceProbe] FAIL: " + failures + " assertion(s) of " + checks);
            System.exit(1);
        }
    }

    /** Builds a ClimateParam; expects the construction to fail with IllegalArgumentException. */
    private static boolean rejectsBuild(boolean unused, double t, double h, double c, double e, double d, double r) {
        try {
            new ClimateParam(t, h, c, e, d, r);
            return false;
        } catch (IllegalArgumentException ex) {
            return true;
        }
    }

    private static boolean rejectsRangeSpan() {
        try {
            ClimateParam.Range.span(2.0, 1.0);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean rejectsTd(String td) {
        try {
            ClimateParam.fromTd(td);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean rejectsPick(List<BiomeTarget> targets, ClimateParam sample) {
        try {
            MultiNoiseBiomeSourceCore.pick(targets, sample);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean rejectsCoreTd(String td) {
        try {
            MultiNoiseBiomeSourceCore.fromTd(td);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}