package io.toterra.subterra.probes;

import io.toterra.subterra.engine.worldgen.pipeline.formula.EvalContext;
import io.toterra.subterra.engine.worldgen.pipeline.formula.Expr;

/**
 * 确定性验收探针：p.1.8.9 math-formula 地形引擎核心。
 * Deterministic acceptance probe for the p.1.8.9 math-formula engine core.
 * Pure JVM, no timing asserts, all expected values hand-computed. Covers
 * precedence, unary/^ binding, '%', the full MathLib set, parse-error cases,
 * determinism, case-sensitivity policy and canonical toSource() round-trips.
 */
public final class FormulaProbe {

    private FormulaProbe() {
    }

    /** Single value/shape epsilon suited to these hand-computed cases. */
    private static final double EPS = 1.0e-12;

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
        return Math.abs(a - b) < EPS;
    }

    /** Eval a source at a fixed context. */
    private static double at(String src, double x, double y, double z) {
        return Expr.parse(src).eval(new EvalContext(x, y, z, 1L));
    }

    /** Eval a source at the origin. */
    private static double ev(String src) {
        return at(src, 0, 0, 0);
    }

    private static boolean rejects(String src) {
        try {
            Expr.parse(src);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    public static void main(String[] args) {
        // ---- 优先级 / 结合律 Precedence & associativity --------------------
        check("prec: 2+3*4 == 14", ev("2+3*4") == 14.0);
        check("prec: (2+3)*4 == 20", ev("(2+3)*4") == 20.0);
        check("prec: 2^3^2 == 512 (right assoc)", ev("2^3^2") == 512.0);
        check("prec: -2^2 == -4 (^ binds tighter than unary)", ev("-2^2") == -4.0);
        check("prec: 2^-2 == 0.25", ev("2^-2") == 0.25);
        check("prec: unary chain --3 == 3 (accepted)", ev("--3") == 3.0);
        check("prec: 7%3 == 1", ev("7%3") == 1.0);
        check("prec: 7.5%2 == 1.5", ev("7.5%2") == 1.5);
        check("prec: 6/3*2 == 4 (left assoc)", ev("6/3*2") == 4.0);
        check("prec: 2*3%4 == 2", ev("2*3%4") == 2.0);
        check("prec: -5+2 == -3", ev("-5+2") == -3.0);

        // ---- 函数 Functions (MathLib set) -----------------------------------
        check("fn: abs(-3) == 3", ev("abs(-3)") == 3.0);
        check("fn: sign(-2.5) == -1", ev("sign(-2.5)") == -1.0);
        check("fn: sign(0) == 0", ev("sign(0)") == 0.0);
        check("fn: signum(7) == 1", ev("signum(7)") == 1.0);
        check("fn: sqrt(16) == 4", ev("sqrt(16)") == 4.0);
        check("fn: cbrt(27) == 3", ev("cbrt(27)") == 3.0);
        check("fn: exp(0) == 1", ev("exp(0)") == 1.0);
        check("fn: log(1) == 0", ev("log(1)") == 0.0);
        check("fn: log(e) ~ 1", near(ev("log(e)"), 1.0));
        check("fn: log10(100) == 2", ev("log10(100)") == 2.0);
        check("fn: log2(8) == 3", ev("log2(8)") == 3.0);
        check("fn: pow(2,3) == 8", ev("pow(2,3)") == 8.0);
        check("fn: min(3,7) == 3", ev("min(3,7)") == 3.0);
        check("fn: max(-2,9) == 9", ev("max(-2,9)") == 9.0);
        check("fn: clamp(-1,0,1) == 0", ev("clamp(-1,0,1)") == 0.0);
        check("fn: clamp(5,0,1) == 1", ev("clamp(5,0,1)") == 1.0);
        check("fn: clamp(3,0,10) == 3", ev("clamp(3,0,10)") == 3.0);
        check("fn: floor(2.7) == 2", ev("floor(2.7)") == 2.0);
        check("fn: ceil(2.1) == 3", ev("ceil(2.1)") == 3.0);
        check("fn: round(2.6) == 3", ev("round(2.6)") == 3.0);
        check("fn: frac(-1.25) == 0.75 (v-floor(v))", ev("frac(-1.25)") == 0.75);
        check("fn: frac(2.5) == 0.5", ev("frac(2.5)") == 0.5);
        check("fn: sin(0) == 0", ev("sin(0)") == 0.0);
        check("fn: cos(0) == 1", ev("cos(0)") == 1.0);
        check("fn: tan(0) == 0", ev("tan(0)") == 0.0);
        check("fn: asin(1) ~ pi/2", near(ev("asin(1)"), Math.PI / 2));
        check("fn: acos(1) == 0", ev("acos(1)") == 0.0);
        check("fn: atan(0) == 0", ev("atan(0)") == 0.0);
        check("fn: atan2(1,0) ~ pi/2", near(ev("atan2(1,0)"), Math.PI / 2));
        check("fn: sinh(0) == 0", ev("sinh(0)") == 0.0);
        check("fn: cosh(0) == 1", ev("cosh(0)") == 1.0);
        check("fn: tanh(0) == 0", ev("tanh(0)") == 0.0);
        check("fn: asinh(0) == 0", ev("asinh(0)") == 0.0);
        check("fn: acosh(1) == 0", ev("acosh(1)") == 0.0);
        check("fn: atanh(0) == 0", ev("atanh(0)") == 0.0);
        check("fn: deg(pi) == 180", ev("deg(pi)") == 180.0);
        check("fn: rad(180) ~ pi", near(ev("rad(180)"), Math.PI));
        check("const: pi", near(ev("pi"), 3.141592653589793));
        check("const: e", near(ev("e"), 2.718281828459045));
        check("const: tau == 2*pi", near(ev("tau"), 2 * Math.PI));

        // ---- 常量/函数大小写 Constant & function case policy ---------------
        check("case: functions case-insensitive SIN==sin", at("SIN(x)", Math.PI / 2, 0, 0)
                == at("sin(x)", Math.PI / 2, 0, 0));
        check("case: constants case-insensitive PI==pi", at("PI", 0, 0, 0) == at("pi", 0, 0, 0));
        check("case: variable x case-sensitive, X rejects", rejects("X"));
        check("case: mixed-case variable rejects", rejects("2*X+1"));

        // ---- 错误输入 Parse-error cases -------------------------------------
        check("err: empty rejects", rejects(""));
        check("err: whitespace-only rejects", rejects("   "));
        check("err: unbalanced ( rejects", rejects("("));
        check("err: unbalanced (1 rejects", rejects("(1"));
        check("err: bare ) rejects", rejects("1)"));
        check("err: trailing junk '1 2' rejects", rejects("1 2"));
        check("err: trailing junk '1+' rejects", rejects("1+"));
        check("err: double operator '1+*2' rejects", rejects("1+*2"));
        check("err: unknown identifier 'q' rejects", rejects("q"));
        check("err: unknown function rejects", rejects("foo(1)"));
        check("err: wrong arity sqrt(1,2) rejects", rejects("sqrt(1,2)"));
        check("err: wrong arity clamp(1) rejects", rejects("clamp(1)"));
        check("err: wrong arity pow(2) rejects", rejects("pow(2)"));
        check("err: sin() zero-arg rejects", rejects("sin()"));

        // ---- 确定性 Determinism -------------------------------------------
        double d1 = at("sin(x)*log(y)+frac(z)", 1.3, 2.7, -1.25) ;
        double d2 = at("sin(x)*log(y)+frac(z)", 1.3, 2.7, -1.25);
        double d3 = at("sin(x)*log(y)+frac(z)", 1.3, 2.7, -1.25);
        check("det: 3 fresh parses identical", d1 == d2 && d2 == d3);
        check("det: same source+ctx across parses reproducible",
                at("2+3*x-pow(y,2)", 4, 5, 6) == at("2+3*x-pow(y,2)", 4, 5, 6));
        // No Random / wall clock anywhere: value only depends on source+ctx.
        check("det: NaN propagates", Double.isNaN(at("sin(1e400)", 0, 0, 0)));
        check("det: div-by-zero -> Infinity", Double.isInfinite(ev("1/0")));

        // ---- toSource 规范化往返 Canonical round-trip ----------------------
        String[] forms = {
            "2+3*x",
            "sin(x)+cos(y)*z",
            "2^3^2",
            "(2+3)*4",
            "pow(x,2)+frac(y)",
            "-2^2",
            "clamp(z,0,1)*atan2(y,x)"
        };
        for (String s : forms) {
            Expr a = Expr.parse(s);
            Expr b = Expr.parse(a.toSource());
            boolean ok = true;
            for (double x = -2.5; x <= 2.5; x += 1.7) {
                for (double y = -2.5; y <= 2.5; y += 1.7) {
                    for (double z = -2.5; z <= 2.5; z += 1.7) {
                        EvalContext ctx = new EvalContext(x, y, z, 7L);
                        double va = a.eval(ctx);
                        double vb = b.eval(ctx);
                        if (Double.isNaN(va)) {
                            if (!Double.isNaN(vb)) {
                                ok = false;
                            }
                        } else if (va != vb) {
                            ok = false;
                        }
                    }
                }
            }
            check("source round-trip: '" + s + "'", ok);
        }
        check("src: whitespace tolerant ' sin ( x ) '",
                at(" sin ( x ) ", 0.5, 0, 0) == at("sin(x)", 0.5, 0, 0));
        check("src: underscore digit separators tolerated",
                at("1_000 + 2", 1, 0, 0) == 1002.0);

        if (failures == 0) {
            System.out.println("[FormulaProbe] PASS (math-formula engine core, "
                    + checks + " checks)");
            System.exit(0);
        } else {
            System.out.println("[FormulaProbe] FAIL: " + failures + " assertion(s)");
            System.exit(1);
        }
    }
}