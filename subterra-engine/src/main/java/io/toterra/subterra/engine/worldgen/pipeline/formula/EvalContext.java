package io.toterra.subterra.engine.worldgen.pipeline.formula;

import java.util.Locale;

/**
 * Immutable evaluation context for a formula: the world coordinates x/y/z and a
 * deterministic seed. Variables are resolved by name ({@link #variable}). This
 * class is read-only and always safe to share across threads.
 *
 * <p>Note: the parsed {@link Expr} instances themselves are only safe for
 * concurrent {@link Expr#eval} when the expression does not contain function
 * calls (their call-argument scratch buffers are reused per evaluation, so a
 * single instance must not be evaluated from multiple threads simultaneously).
 */
public final class EvalContext {

    private final double x;
    private final double y;
    private final double z;
    private final long seed;

    public EvalContext(double x, double y, double z, long seed) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.seed = seed;
    }

    /**
     * Resolves a named variable to its double value. Only {@code "x"}, {@code
     * "y"} and {@code "z"} are defined; any other name throws.
     */
    public double variable(String name) {
        switch (name == null ? "" : name.toLowerCase(Locale.ROOT)) {
            case "x":
                return x;
            case "y":
                return y;
            case "z":
                return z;
            default:
                throw new IllegalArgumentException("unknown variable: " + name);
        }
    }

    /** The deterministic seed carried by this context. */
    public long seed() {
        return seed;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }
}