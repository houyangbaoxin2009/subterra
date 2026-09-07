package io.toterra.subterra.optim.worldgen.pipeline.formula;

/**
 * A parsed math-formula tree. Parse a source string with {@link #parse}, then
 * evaluate it many times with {@link #eval}. Parsing is allocation-only;
 * evaluation of the hot path is allocation-free and deterministic (IEEE
 * NaN-propagating, division by zero yields Infinity exactly like Java {@code
 * double} arithmetic).
 */
public interface Expr {

    /**
     * Evaluates this expression for the given immutable context.
     *
     * @return NaN when any operand is NaN; May be {@code +/-Infinity} on
     *         overflow or division by zero, matching Java {@code double}
     *         semantics.
     */
    double eval(EvalContext ctx);

    /**
     * Parses a formula source into an expression tree. The grammar is:
     *
     * <pre>
     *   formula := expr
     *   expr    := term  ( ('+'|'-') term )*
     *   term    := unary ( ('*'|'/'|'%') unary )*
     *   unary   := ('+'|'-') unary | power
     *   power   := atom  ( '^' unary )?        // right-associative
     *   atom    := number | '(' expr ')' | constant | func-call
     *   func-call := ident '(' ( expr ( ',' expr )* )? ')'
     * </pre>
     *
     * <p>Precedence, tightest to loosest: {@code ^} (right-assoc) → unary
     * {@code + / -} → {@code * / %} → binary {@code + / -}. Consequently
     * {@code -2^2 == -(2^2) == -4} (the exponent binds tighter than the leading
     * unary minus) and {@code 2^3^2 == 2^(3^2) == 512}.
     *
     * <p>Variables are {@code x},{@code y},{@code z} (case-sensitive, lowercase).
     * Constants {@code pi},{@code tau},{@code e} and function names are
     * case-insensitive. Whitespace and {@code _} are ignored. Function arity is
     * validated at parse time for the built-in registry.
     *
     * @param source the formula to parse; must not be blank
     * @return the parsed expression tree
     * @throws IllegalArgumentException on a blank formula, lexer error,
     *         unbalanced parentheses, trailing junk, an unknown identifier, an
     *         unknown function name, or a wrong built-in arity
     */
    static Expr parse(String source) {
        return new Parser(source).parse();
    }

    /**
     * Returns a normalized canonical source string for this tree (minimal
     * parentheses, deterministic double formatting) that re-parses to an
     * evaluation-equivalent tree.
     */
    String toSource();
}