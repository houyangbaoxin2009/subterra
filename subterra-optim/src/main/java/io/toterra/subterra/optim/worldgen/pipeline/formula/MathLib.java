package io.toterra.subterra.optim.worldgen.pipeline.formula;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Static registry of the deterministic math functions available to formulas.
 * The registry is case-insensitive (names are normalized to lowercase) and is
 * fully determined by the built-in set plus any external registrations, so
 * evaluations are reproducible across instances and JVMs. All functions use
 * only {@code java.lang.Math} primitives — no {@code Random}, no wall clock.
 */
public final class MathLib {

    /** A callable math function; implementations must validate their own arity. */
    public interface MathFunction {
        /** @param args the (already non-null) argument array; may be empty */
        double apply(double[] args);
    }

    /** Registered entry: a function plus its fixed arity (-1 = external/var).
     *  Register with {@link MathLib#register(String, MathFunction)} sets -1. */
    private static final class Entry {
        final int arity;
        final MathFunction fn;

        Entry(int arity, MathFunction fn) {
            this.arity = arity;
            this.fn = fn;
        }
    }

    private static final Map<String, Entry> REGISTRY = new TreeMap<>();

    private MathLib() {
    }

    static {
        registerKnown(1, "abs", a -> Math.abs(a[0]));
        registerKnown(1, "signum", MathLib::signum);
        // "sign" is a documented alias of signum.
        registerKnown(1, "sign", MathLib::signum);
        registerKnown(1, "sqrt", a -> Math.sqrt(a[0]));
        registerKnown(1, "cbrt", a -> Math.cbrt(a[0]));
        registerKnown(1, "exp", a -> Math.exp(a[0]));
        registerKnown(1, "log", a -> Math.log(a[0]));      // natural log
        registerKnown(1, "log10", a -> Math.log10(a[0]));
        registerKnown(1, "log2", a -> Math.log(a[0]) / Math.log(2.0));
        registerKnown(2, "pow", a -> Math.pow(a[0], a[1]));
        registerKnown(2, "min", a -> Math.min(a[0], a[1]));
        registerKnown(2, "max", a -> Math.max(a[0], a[1]));
        registerKnown(3, "clamp", MathLib::clamp);
        registerKnown(1, "floor", a -> Math.floor(a[0]));
        registerKnown(1, "ceil", a -> Math.ceil(a[0]));
        registerKnown(1, "round", a -> (double) Math.round(a[0]));
        registerKnown(1, "frac", MathLib::frac);           // v - floor(v)
        registerKnown(1, "sin", a -> Math.sin(a[0]));
        registerKnown(1, "cos", a -> Math.cos(a[0]));
        registerKnown(1, "tan", a -> Math.tan(a[0]));
        registerKnown(1, "asin", a -> Math.asin(a[0]));
        registerKnown(1, "acos", a -> Math.acos(a[0]));
        registerKnown(1, "atan", a -> Math.atan(a[0]));
        registerKnown(2, "atan2", a -> Math.atan2(a[0], a[1]));
        registerKnown(1, "sinh", a -> Math.sinh(a[0]));
        registerKnown(1, "cosh", a -> Math.cosh(a[0]));
        registerKnown(1, "tanh", a -> Math.tanh(a[0]));
        registerKnown(1, "asinh", a -> Math.log(a[0] + Math.sqrt(a[0] * a[0] + 1.0)));
        registerKnown(1, "acosh", a -> Math.log(a[0] + Math.sqrt(a[0] * a[0] - 1.0)));
        registerKnown(1, "atanh", a -> 0.5 * Math.log((1.0 + a[0]) / (1.0 - a[0])));
        registerKnown(1, "deg", a -> Math.toDegrees(a[0]));
        registerKnown(1, "rad", a -> Math.toRadians(a[0]));
    }

    /**
     * Registers an external function. Arity is not known here, so the provided
     * {@code fn} must validate its own argument count (throwing
     * IllegalArgumentException on mismatch) and stays un-checked at parse time.
     *
     * @param name   case-insensitive function name (null is rejected)
     * @param fn     the implementation (null is rejected)
     * @throws IllegalArgumentException if name or fn is null or name is blank
     */
    public static void register(String name, MathFunction fn) {
        registerKnown(-1, normalized(name), fn);
    }

    /** Internal registration with a fixed arity, enabling parse-time checks. */
    private static void registerKnown(int arity, String name, MathFunction fn) {
        if (fn == null) {
            throw new IllegalArgumentException("function implementation is null");
        }
        REGISTRY.put(normalized(name), new Entry(arity, fn));
    }

    /**
     * Calls the named function with the given arguments.
     *
     * @param name case-insensitive function name; must not be null
     * @param args argument array; must not be null
     * @return the result, deterministic per IEEE
     * @throws IllegalArgumentException when name or args is null, the function
     *         is unknown, or a fixed-arity built-in gets the wrong arity
     */
    public static double call(String name, double[] args) {
        if (name == null) {
            throw new IllegalArgumentException("function name is null");
        }
        if (args == null) {
            throw new IllegalArgumentException("function arguments are null");
        }
        Entry e = REGISTRY.get(normalized(name));
        if (e == null) {
            throw new IllegalArgumentException("unknown function: " + name);
        }
        if (e.arity >= 0 && args.length != e.arity) {
            throw new IllegalArgumentException("function '" + name + "' expects "
                    + e.arity + " argument(s) but got " + args.length);
        }
        return e.fn.apply(args);
    }

    /** Whether a function with the given (case-insensitive) name is registered. */
    public static boolean isKnown(String name) {
        return name != null && REGISTRY.containsKey(normalized(name));
    }

    /**
     * Returns the fixed arity of a registered function, or {@code -1} when it is
     * either external (unknown arity) or unknown. Used by the parser for
     * parse-time arity checks on built-ins.
     */
    static int fixedArity(String name) {
        Entry e = name == null ? null : REGISTRY.get(normalized(name));
        return e == null ? -1 : e.arity;
    }

    private static String normalized(String name) {
        if (name == null) {
            throw new IllegalArgumentException("function name is null");
        }
        String s = name.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            throw new IllegalArgumentException("function name is blank: " + name);
        }
        return s;
    }

    private static double signum(double[] a) {
        return Math.signum(a[0]);
    }

    private static double clamp(double[] a) {
        double v = a[0];
        double lo = a[1];
        double hi = a[2];
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** Fractional part defined as {@code v - floor(v)}, so {@code frac(-1.25) == 0.75}. */
    private static double frac(double[] a) {
        return a[0] - Math.floor(a[0]);
    }
}