package io.toterra.subterra.probes;

import io.toterra.subterra.engine.worldgen.pipeline.density.Density;
import io.toterra.subterra.engine.worldgen.pipeline.terrain.SubterraTerrain;

/**
 * TEMPORARY reference dump (pre-equivalence RCA): prints raw-bit values of the Java
 * SubterraTerrain.finalDensity reference at the tie smoke reference points. Not an
 * acceptance probe; removed once the equivalence probe lands.
 */
public final class DensityRefProbe {
    public static void main(String[] args) {
        long seed = 42;
        Density d = SubterraTerrain.finalDensity(seed);
        double[][] pts = {
                {0, 63, 0},
                {64, -64, -128},
                {-512, 127, 512},
                {512, 200, -448},
                {-64, 0, 64},
        };
        for (double[] p : pts) {
            double v = d.eval(p[0], p[1], p[2]);
            System.out.printf("seed=42 (%.0f,%.0f,%.0f) = %s 0x%016X%n",
                    p[0], p[1], p[2], v, Double.doubleToRawLongBits(v));
        }
        long seed2 = 7;
        Density d2 = SubterraTerrain.finalDensity(seed2);
        double v2 = d2.eval(64, -64, -128);
        System.out.printf("seed=7 (64,-64,-128) = %s 0x%016X%n",
                v2, Double.doubleToRawLongBits(v2));
    }
}
