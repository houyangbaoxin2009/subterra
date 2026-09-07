package io.toterra.subterra.optim.worldgen.pipeline.density;

/**
 * Deterministic 3-D density function (p.1.8.3, self-developed, mirroring the
 * MC {@code DensityFunction} seam without upstream code): a pure function
 * {@code (x, y, z) -> double} that can be composed with the combinators in
 * {@link Densities} and combined with deterministic {@link ValueNoise}.
 * Every evaluation is constant-time and free of allocation; the same inputs
 * always yield the same output.
 */
@FunctionalInterface
public interface Density {

    /** Evaluates the density at the given block coordinates. */
    double eval(double x, double y, double z);
}