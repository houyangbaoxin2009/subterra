package io.toterra.subterra.optim.worldgen.pipeline.formula;

/**
 * Package-private tokenizer for the math-formula language. Whitespace and the
 * underscore {@code _} are ignored (treated as separators). Numbers support an
 * optional decimal point and exponent ({@code 1e3}, {@code 1.5e-2}). Identifiers
 * are {@code [A-Za-z_][A-Za-z0-9_]*}. Produces a flat token stream on demand.
 */
final class Lexer {

    /** Token kinds of the formula grammar. */
    enum Kind {
        NUMBER, IDENT, LPAREN, RPAREN, COMMA,
        PLUS, MINUS, STAR, SLASH, PERCENT, CARET, EOF
    }

    /** A single lexed token with an optional numeric value and identifier text. */
    static final class Token {
        final Kind kind;
        final String text;    // identifier text for IDENT, otherwise null
        final double number;  // numeric value for NUMBER
        final int pos;        // source offset (start of token) for error messages

        Token(Kind kind, String text, double number, int pos) {
            this.kind = kind;
            this.text = text;
            this.number = number;
            this.pos = pos;
        }
    }

    private final String src;
    private final int len;
    private int pos;
    private Token cached;
    private boolean anyDigit; // set by digitOrUnderscore() while scanning a number

    Lexer(String src) {
        this.src = src;
        this.len = src.length();
    }

    /** True when the next unconsumed token is end-of-input. */
    boolean eof() {
        return peek().kind == Kind.EOF;
    }

    Token peek() {
        if (cached == null) {
            cached = scan();
        }
        return cached;
    }

    Token next() {
        Token t = peek();
        cached = null;
        return t;
    }

    /** Current source position (for diagnostics); points at the next token. */
    int position() {
        return cached == null ? pos : cached.pos;
    }

    private void skipIgnored() {
        while (pos < len) {
            char c = src.charAt(pos);
            if (c == '_' || Character.isWhitespace(c)) {
                pos++;
            } else {
                break;
            }
        }
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /** Advances over one digit (returns true), or over an interior '_' between
     *  two digits (returns true too, without counting it as a digit, so digit
     *  scanning continues past the separator). Any other char leaves the cursor
     *  untouched and returns false, ending the numeric run. */
    private boolean digitOrUnderscore() {
        if (pos < len && isDigit(src.charAt(pos))) {
            pos++;
            anyDigit = true;
            return true;
        }
        if (pos + 1 < len && src.charAt(pos) == '_'
                && isDigit(src.charAt(pos + 1))) {
            pos++; // skip an interior separator (not counted as a digit)
            return true;
        }
        return false;
    }

    private static boolean isIdentStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return isIdentStart(c) || isDigit(c);
    }

    private Token scan() {
        skipIgnored();
        if (pos >= len) {
            return new Token(Kind.EOF, null, Double.NaN, pos);
        }
        int start = pos;
        char c = src.charAt(pos);

        // ---- number: digits [. digits] [ (e|E) [+|-] digits ] ----
        if (isDigit(c) || (c == '.' && pos + 1 < len && isDigit(src.charAt(pos + 1)))) {
            anyDigit = false;
            while (digitOrUnderscore()) {
                // advances over integer digits, tolerating '_' separators
            }
            if (pos < len && src.charAt(pos) == '.') {
                pos++;
                while (digitOrUnderscore()) {
                    // fractional digits
                }
            }
            if (!anyDigit) {
                throw error("invalid number", start);
            }
            if (pos < len && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                int save = pos;
                pos++;
                if (pos < len && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                    pos++;
                }
                if (!(pos < len && isDigit(src.charAt(pos)))) {
                    pos = save; // not an exponent: treat letter as separate ident
                } else {
                    while (pos < len && isDigit(src.charAt(pos))) {
                        pos++;
                    }
                }
            }
            double d;
            try {
                String raw = src.substring(start, pos);
                String txt = raw.indexOf('_') >= 0 ? raw.replace("_", "") : raw;
                d = Double.parseDouble(txt);
            } catch (NumberFormatException ex) {
                throw error("invalid number", start);
            }
            return new Token(Kind.NUMBER, null, d, start);
        }

        if (isIdentStart(c)) {
            pos++;
            while (pos < len && isIdentPart(src.charAt(pos))) {
                pos++;
            }
            return new Token(Kind.IDENT, src.substring(start, pos), Double.NaN, start);
        }

        pos++;
        switch (c) {
            case '(':
                return new Token(Kind.LPAREN, null, Double.NaN, start);
            case ')':
                return new Token(Kind.RPAREN, null, Double.NaN, start);
            case ',':
                return new Token(Kind.COMMA, null, Double.NaN, start);
            case '+':
                return new Token(Kind.PLUS, null, Double.NaN, start);
            case '-':
                return new Token(Kind.MINUS, null, Double.NaN, start);
            case '*':
                return new Token(Kind.STAR, null, Double.NaN, start);
            case '/':
                return new Token(Kind.SLASH, null, Double.NaN, start);
            case '%':
                return new Token(Kind.PERCENT, null, Double.NaN, start);
            case '^':
                return new Token(Kind.CARET, null, Double.NaN, start);
            default:
                throw error("unexpected character '" + c + "'", start);
        }
    }

    /** Builds an IllegalArgumentException with position information. */
    private IllegalArgumentException error(String message, int at) {
        return new IllegalArgumentException(message + " at position " + at
                + " in \"" + src + "\"");
    }
}