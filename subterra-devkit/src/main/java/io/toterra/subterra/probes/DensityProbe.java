package io.toterra.subterra.probes;

import io.toterra.subterra.engine.worldgen.pipeline.density.Densities;
import io.toterra.subterra.engine.worldgen.pipeline.density.Density;
import io.toterra.subterra.engine.worldgen.pipeline.density.NoiseHash;
import io.toterra.subterra.engine.worldgen.pipeline.density.ValueNoise;

/**
 * Deterministic acceptance probe for the p.1.8.3 density-function core:
 * constant/linear arithmetic, combinator math, clamp/blend/scale/offset,
 * value-noise reproducibility, lattice-exactness and field range. Pure JVM.
 */
public final class DensityProbe {

    private DensityProbe() {
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

    public static void main(String[] args) {
        // Constants and ramps.
        check("constant", near(Densities.constant(4.0).eval(1, 2, 3), 4.0));
        check("linear ramp", near(Densities.linearX(2.0, 1.0).eval(5, 0, 0), 11.0));
        check("ramp face", near(Densities.rampX().eval(7, 0, 0), 7.0));

        // Arithmetic combinators.
        check("add", near(Densities.add(Densities.constant(2), Densities.constant(3)).eval(0, 0, 0), 5.0));
        check("mul", near(Densities.mul(Densities.constant(2), Densities.constant(4)).eval(0, 0, 0), 8.0));
        check("min", near(Densities.min(Densities.constant(1), Densities.constant(5)).eval(0, 0, 0), 1.0));
        check("max", near(Densities.max(Densities.constant(1), Densities.constant(5)).eval(0, 0, 0), 5.0));

        // Clamp bounds + validation.
        check("clamp low", near(Densities.clamp(Densities.constant(-5), -1, 1).eval(0, 0, 0), -1.0));
        check("clamp high", near(Densities.clamp(Densities.constant(9), -1, 1).eval(0, 0, 0), 1.0));
        check("clamp keeps inner", near(Densities.clamp(Densities.constant(0.5), -1, 1).eval(0, 0, 0), 0.5));
        check("clamp rejects inverted", rejectsInvertedClamp());

        // Blend and scale.
        check("mix endpoints", near(Densities.mix(Densities.constant(0), Densities.constant(10),
                Densities.constant(0.5)).eval(0, 0, 0), 5.0));
        check("scale", near(Densities.scale(Densities.constant(3), 4).eval(0, 0, 0), 12.0));

        // Domain offset on the ramp: x+2 at x=1 -> 3.
        check("offset", near(Densities.offset(Densities.rampX(), Densities.constant(2), null, null)
                .eval(1, 0, 0), 3.0));
        check("offset nested", near(Densities.offset(
                Densities.offset(Densities.rampX(), Densities.constant(2), null, null),
                Densities.constant(1), null, null).eval(0, 0, 0), 3.0));

        // Value noise reproducibility.
        ValueNoise n1 = new ValueNoise(42L, 8.0);
        double a = n1.eval(1.5, 2.5, 3.5);
        double b = n1.eval(1.5, 2.5, 3.5);
        check("noise reproducible", a == b);
        check("noise seed reproducibility", near(new ValueNoise(42L, 8.0).eval(1.5, 2.5, 3.5), a));
        check("noise different seed", !near(new ValueNoise(7L, 8.0).eval(1.5, 2.5, 3.5), a));
        ValueNoise n2 = new ValueNoise(42L, 8.0);
        check("noise varies off-lattice", !near(n2.eval(1.0, 2.0, 3.0), n2.eval(1.5, 2.5, 3.5)));

        // Lattice exactness: at integer coordinates the field equals grid().
        ValueNoise n3 = new ValueNoise(5L, 4.0);
        check("lattice exact", near(n3.eval(4, 8, 12), n3.grid(1, 2, 3)));

        // Field range and determinism over a small region.
        ValueNoise n4 = new ValueNoise(123L, 3.0);
        boolean inRange = true;
        for (double dx = -10; dx <= 10; dx += 0.7) {
            for (double dy = -3; dy <= 3; dy += 0.7) {
                double v = n4.eval(dx, dy, 0.25);
                if (v < -1.0 || v > 1.0) {
                    inRange = false;
                }
            }
        }
        check("noise range [-1,1]", inRange);

        // Composed tree still deterministic.
        Density tree = Densities.clamp(
                Densities.add(Densities.valueNoise(9, 16.0), Densities.constant(0.5)), -1, 1);
        check("composed tree reproducible", tree.eval(-3.25, 1.75, 0.5) == tree.eval(-3.25, 1.75, 0.5));

        // Hash determinism.
        check("hash deterministic", NoiseHash.scramble(99L) == NoiseHash.scramble(99L));
        check("hash distinct inputs", NoiseHash.gridHash(1, 1, 1, 1) != NoiseHash.gridHash(1, 1, 1, 2));

        if (failures == 0) {
            System.out.println("[DensityProbe] PASS (density-function core, " + 24 + " checks)");
            System.exit(0);
        } else {
            System.out.println("[DensityProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }

    private static boolean rejectsInvertedClamp() {
        try {
            Densities.clamp(Densities.constant(0), 5, 1);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}