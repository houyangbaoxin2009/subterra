package io.toterra.subterra.optim.worldgen.pipeline.formula;

import io.toterra.subterra.optim.worldgen.pipeline.formula.Lexer.Kind;
import io.toterra.subterra.optim.worldgen.pipeline.formula.Lexer.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hand-written recursive-descent parser for the math-formula language (p.1.8.9,
 * self-developed). Builds an immutable {@link Expr} tree that evaluates the hot
 * path with zero allocation (function-call argument scratch buffers are
 * allocated once per node at parse time). {@link #toSource()} on the returned
 * tree emits a normalized canonical source that re-parses to an
 * evaluation-equivalent tree.
 *
 * <p><b>Grammar (documented in {@link Expr#parse}):</b>
 * {@code atom} binds tightest, then {@code ^} (right-associative), then unary
 * {@code +/-}, then {@code * / %}, then binary {@code + / -}. So
 * {@code -2^2 == -4} and {@code 2^3^2 == 512}. Chain of unary signs is allowed
 * ({@code --3 == 3}). Binary left-associative.
 *
 * <p><b>Case sensitivity:</b> variables {@code x},{@code y},{@code z} are
 * case-sensitive (lowercase); constants and function names are case-insensitive.
 */
final class Parser {

    // Precedence levels: higher binds tighter. Used both by the parser
    // (implicitly via the grammar) and by canonical toSource() un-parenthesizing.
    private static final int P_ADD = 1, P_MUL = 2, P_POW = 3, P_UNARY = 4, P_ATOM = 5;

    private final Lexer lexer;

    Parser(String source) {
        if (source == null) {
            throw new IllegalArgumentException("formula source is null");
        }
        this.lexer = new Lexer(source);
    }

    Expr parse() {
        Expr root = parseExpression();
        if (!lexer.eof()) {
            throw fail("trailing junk after the expression");
        }
        return root;
    }

    private Expr parseExpression() {
        Expr left = parseTerm();
        while (!lexer.eof()) {
            Kind k = lexer.peek().kind;
            char op;
            if (k == Kind.PLUS) {
                op = '+';
            } else if (k == Kind.MINUS) {
                op = '-';
            } else {
                break;
            }
            lexer.next();
            Expr right = parseTerm();
            left = new Bin(op, left, right, P_ADD);
        }
        return left;
    }

    private Expr parseTerm() {
        Expr left = parseUnary();
        while (!lexer.eof()) {
            Kind k = lexer.peek().kind;
            char op;
            switch (k) {
                case STAR:
                    op = '*';
                    break;
                case SLASH:
                    op = '/';
                    break;
                case PERCENT:
                    op = '%';
                    break;
                default:
                    return left;
            }
            lexer.next();
            Expr right = parseUnary();
            left = new Bin(op, left, right, P_MUL);
        }
        return left;
    }

    private Expr parseUnary() {
        Kind k = lexer.peek().kind;
        if (k == Kind.PLUS || k == Kind.MINUS) {
            char sign = k == Kind.PLUS ? '+' : '-';
            lexer.next();
            return new Unary(sign, parseUnary());
        }
        return parsePower();
    }

    private Expr parsePower() {
        Expr base = parseAtom();
        if (!lexer.eof() && lexer.peek().kind == Kind.CARET) {
            lexer.next();
            Expr exp = parseUnary();
            return new Bin('^', base, exp, P_POW);
        }
        return base;
    }

    private Expr parseAtom() {
        Token t = lexer.next();
        switch (t.kind) {
            case NUMBER:
                return new Num(t.number);
            case LPAREN: {
                Expr inner = parseExpression();
                if (lexer.eof() || lexer.next().kind != Kind.RPAREN) {
                    throw fail("expected ')'");
                }
                return inner;
            }
            case IDENT: {
                if (!lexer.eof() && lexer.peek().kind == Kind.LPAREN) {
                    return parseFunction(t);
                }
                String id = t.text; // variables are case-sensitive, lowercase
                switch (id) {
                    case "x":
                        return new Var('x');
                    case "y":
                        return new Var('y');
                    case "z":
                        return new Var('z');
                    default:
                        // constants are case-insensitive
                        String lower = id.toLowerCase(Locale.ROOT);
                        switch (lower) {
                            case "pi":
                                return new Num(Math.PI);
                            case "tau":
                                return new Num(2.0 * Math.PI);
                            case "e":
                                return new Num(Math.E);
                            default:
                                throw fail("unknown identifier '" + t.text + "'");
                        }
                }
            }
            default:
                throw fail("expected a value but found token '" + t.kind + "'");
        }
    }

    private Expr parseFunction(Token name) {
        lexer.next(); // consume '('
        List<Expr> args = new ArrayList<>();
        if (!lexer.eof() && lexer.peek().kind == Kind.RPAREN) {
            lexer.next();
        } else {
            for (; ; ) {
                args.add(parseExpression());
                if (lexer.eof()) {
                    throw fail("expected ')' to close call to '" + name.text + "'");
                }
                Kind k = lexer.next().kind;
                if (k == Kind.COMMA) {
                    continue;
                }
                if (k == Kind.RPAREN) {
                    break;
                }
                throw fail("expected ',' or ')' in argument list of '"
                        + name.text + "'");
            }
        }
        if (!MathLib.isKnown(name.text)) {
            throw fail("unknown function '" + name.text + "'");
        }
        int fixed = MathLib.fixedArity(name.text);
        if (fixed >= 0 && args.size() != fixed) {
            throw fail("function '" + name.text + "' expects " + fixed
                    + " argument(s) but got " + args.size());
        }
        return new Func(name.text.toLowerCase(Locale.ROOT), args);
    }

    private IllegalArgumentException fail(String message) {
        return new IllegalArgumentException(message + " at position "
                + lexer.position());
    }

    // ------------------------------------------------------------------------
    // AST nodes (nested, package-private): allocation-free eval, canonical
    // toSource() that re-parses to an evaluation-equivalent tree.
    // ------------------------------------------------------------------------

    private static int precOf(Expr e) {
        if (e instanceof Bin) {
            return ((Bin) e).prec;
        }
        if (e instanceof Unary) {
            return P_UNARY;
        }
        return P_ATOM; // Num, Var, Func
    }

    /** Renders {@code child} into {@code sb}, wrapping in parens if needed. */
    private static void renderTo(StringBuilder sb, Expr child, int minPrec) {
        boolean paren = precOf(child) < minPrec;
        if (paren) {
            sb.append('(');
        }
        sb.append(child.toSource());
        if (paren) {
            sb.append(')');
        }
    }

    /** Formats a double so it rounds-trips exactly through the lexer. */
    private static String formatNum(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1.0e15 && !Double.isNaN(v)
                && !Double.isInfinite(v)) {
            return (long) v + ".0";
        }
        return Double.toString(v);
    }

    private static final class Num implements Expr {
        private final double value;

        Num(double value) {
            this.value = value;
        }

        @Override
        public double eval(EvalContext ctx) {
            return value;
        }

        @Override
        public String toSource() {
            return formatNum(value);
        }

        private void appendSource(StringBuilder sb) {
            sb.append(formatNum(value));
        }
    }

    private static final class Var implements Expr {
        private final char var;

        Var(char var) {
            this.var = var;
        }

        @Override
        public double eval(EvalContext ctx) {
            switch (var) {
                case 'x':
                    return ctx.x();
                case 'y':
                    return ctx.y();
                default:
                    return ctx.z();
            }
        }

        @Override
        public String toSource() {
            return String.valueOf(var);
        }

        private void appendSource(StringBuilder sb) {
            sb.append(var);
        }
    }

    private static final class Unary implements Expr {
        private final char sign;
        private final Expr operand;

        Unary(char sign, Expr operand) {
            this.sign = sign;
            this.operand = operand;
        }

        @Override
        public double eval(EvalContext ctx) {
            double v = operand.eval(ctx);
            return sign == '-' ? -v : v;
        }

        @Override
        public String toSource() {
            StringBuilder sb = new StringBuilder();
            appendSource(sb);
            return sb.toString();
        }

        private void appendSource(StringBuilder sb) {
            sb.append(sign);
            renderTo(sb, operand, P_POW);
        }
    }

    private static final class Bin implements Expr {
        private final char op;
        private final int prec;
        private final Expr left;
        private final Expr right;

        Bin(char op, Expr left, Expr right, int prec) {
            this.op = op;
            this.left = left;
            this.right = right;
            this.prec = prec;
        }

        @Override
        public double eval(EvalContext ctx) {
            double l = left.eval(ctx);
            switch (op) {
                case '+':
                    return l + right.eval(ctx);
                case '-':
                    return l - right.eval(ctx);
                case '*':
                    return l * right.eval(ctx);
                case '/':
                    return l / right.eval(ctx);
                case '%':
                    return l % right.eval(ctx);
                default:
                    return Math.pow(l, right.eval(ctx));
            }
        }

        @Override
        public String toSource() {
            StringBuilder sb = new StringBuilder();
            appendSource(sb);
            return sb.toString();
        }

        private void appendSource(StringBuilder sb) {
            if (prec == P_ADD) {
                renderTo(sb, left, P_ADD);
                sb.append(op);
                renderTo(sb, right, op == '-' ? P_MUL : P_ADD);
            } else if (prec == P_MUL) {
                renderTo(sb, left, P_MUL);
                sb.append(op);
                renderTo(sb, right, (op == '/' || op == '%') ? P_POW : P_MUL);
            } else { // P_POW (^), right-associative
                renderTo(sb, left, P_UNARY);
                sb.append('^');
                renderTo(sb, right, P_UNARY);
            }
        }
    }

    private static final class Func implements Expr {
        private final String name;
        private final Expr[] args;
        private final double[] scratch; // reused per eval => zero hot-path allocation

        Func(String name, List<Expr> args) {
            this.name = name;
            this.args = args.toArray(new Expr[0]);
            this.scratch = new double[this.args.length];
        }

        @Override
        public double eval(EvalContext ctx) {
            for (int i = 0; i < args.length; i++) {
                scratch[i] = args[i].eval(ctx);
            }
            return MathLib.call(name, scratch);
        }

        @Override
        public String toSource() {
            StringBuilder sb = new StringBuilder();
            appendSource(sb);
            return sb.toString();
        }

        private void appendSource(StringBuilder sb) {
            sb.append(name).append('(');
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(args[i].toSource());
            }
            sb.append(')');
        }
    }
}