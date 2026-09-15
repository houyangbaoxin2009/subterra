package io.toterra.subterra.engine.datapack;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

/**
 * JSON ↔ td bridge for noise-settings (L2 equivalence exporter) — pure JDK, no
 * third-party library. Two directions:
 *
 * <ul>
 * <li>{@link #toJson(TdTable)} renders a canonical noise-settings td table as
 * standard vanilla {@code NoiseGeneratorSettings} JSON text (compact, valid,
 * no extra whitespace), after stripping the reserved L3 plus keys
 * ({@value NoiseSettingsDatum#PLUS_PREFIX}-prefixed) so the output is exactly the
 * subset a vanilla codec can decode.</li>
 * <li>{@link #parseJson(String)} parses vanilla JSON (object / array / string /
 * number / boolean / null) into a td table. It covers only the structures a
 * noise-settings document uses; {@code null} is carried by a reserved sentinel
 * string that {@link #toJson} maps back to JSON {@code null} (td has no null
 * scalar type).</li>
 * </ul>
 *
 * <p>Number fidelity preserves the td kind: a token with a decimal point or an
 * exponent maps to a td FLOAT, a pure integer token to a td INT, and
 * {@link #toJson} re-emits them in the same shape, so a parsed tree round-trips
 * to an equal parsed tree.
 */
public final class NoiseSettingsJson {

    private NoiseSettingsJson() {
    }

    /** Sentinel td string carrying a JSON {@code null} (no null scalar in td). */
    static final String JSON_NULL_MARK = "\u0000null";

    // ------------------------------------------------------------------
    // td -> JSON text
    // ------------------------------------------------------------------

    /**
     * Renders a noise-settings td table as compact vanilla JSON text. The
     * reserved plus keys are stripped first so the result is the vanilla subset.
     */
    public static String toJson(TdTable td) {
        StringBuilder sb = new StringBuilder();
        writeJsonValue(sb, NoiseSettingsDatum.stripPlus(td));
        return sb.toString();
    }

    private static void writeJsonValue(StringBuilder sb, TdValue value) {
        if (value instanceof TdTable t) {
            if (!t.keys().isEmpty()) {
                sb.append('{');
                boolean first = true;
                for (String k : t.keys()) {
                    if (!first) {
                        sb.append(',');
                    }
                    first = false;
                    quote(sb, k);
                    sb.append(':');
                    writeJsonValue(sb, t.get(k));
                }
                sb.append('}');
            } else {
                sb.append('[');
                boolean first = true;
                for (TdValue e : t.elements()) {
                    if (!first) {
                        sb.append(',');
                    }
                    first = false;
                    writeJsonValue(sb, e);
                }
                sb.append(']');
            }
            return;
        }
        TdValue.Scalar s = value.scalar();
        switch (s.kind()) {
            case STRING -> {
                if (JSON_NULL_MARK.equals(s.str())) {
                    sb.append("null");
                } else {
                    quote(sb, s.str());
                }
            }
            case INT -> sb.append(s.i());
            case FLOAT -> {
                double d = s.f();
                if (Double.isInfinite(d) || Double.isNaN(d)) {
                    throw new IllegalArgumentException("non-finite float cannot be JSON");
                }
                sb.append(Double.toString(d));
            }
            case BOOL -> sb.append(s.b() ? "true" : "false");
        }
    }

    private static void quote(StringBuilder sb, String v) {
        sb.append('"');
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------
    // JSON text -> td table
    // ------------------------------------------------------------------

    /**
     * Parses vanilla JSON into a td table (root object or array). Handles
     * object / array / string / number / boolean / null; {@code null} maps to the
     * {@link #JSON_NULL_MARK} sentinel string. Throws {@link IllegalArgumentException}
     * on malformed input.
     */
    public static TdTable parseJson(String json) {
        Parser p = new Parser(json);
        TdValue root = p.parseValue();
        p.skipWs();
        if (!p.atEnd()) {
            throw p.error("trailing content after JSON value");
        }
        if (!(root instanceof TdTable)) {
            throw new IllegalArgumentException("JSON root must be an object or array");
        }
        return (TdTable) root;
    }

    private static final class Parser {
        private final String src;
        private int i;

        Parser(String src) {
            this.src = src;
        }

        boolean atEnd() {
            return i >= src.length();
        }

        IllegalArgumentException error(String msg) {
            return new IllegalArgumentException("json parse error at offset " + i + ": " + msg);
        }

        void skipWs() {
            while (i < src.length()) {
                char c = src.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    i++;
                } else {
                    break;
                }
            }
        }

        TdValue parseValue() {
            skipWs();
            if (atEnd()) {
                throw error("expected value");
            }
            char c = src.charAt(i);
            switch (c) {
                case '{' -> {
                    return parseObject();
                }
                case '[' -> {
                    return parseArray();
                }
                case '"' -> {
                    return TdValue.str(readString());
                }
                case 't' -> {
                    expectLiteral("true");
                    return TdValue.of(true);
                }
                case 'f' -> {
                    expectLiteral("false");
                    return TdValue.of(false);
                }
                case 'n' -> {
                    expectLiteral("null");
                    return TdValue.str(JSON_NULL_MARK);
                }
                default -> {
                    return parseNumber();
                }
            }
        }

        private void expectLiteral(String lit) {
            if (!src.startsWith(lit, i)) {
                throw error("expected " + lit);
            }
            i += lit.length();
        }

        private TdTable parseObject() {
            i++; // '{'
            TdTable.Builder b = TdTable.builder();
            skipWs();
            if (!atEnd() && src.charAt(i) == '}') {
                i++;
                return b.build();
            }
            while (true) {
                skipWs();
                if (atEnd() || src.charAt(i) != '"') {
                    throw error("expected object key string");
                }
                String key = readString();
                skipWs();
                if (atEnd() || src.charAt(i) != ':') {
                    throw error("expected ':'");
                }
                i++;
                b.put(key, parseValue());
                skipWs();
                if (atEnd()) {
                    throw error("unterminated object");
                }
                char c = src.charAt(i);
                if (c == ',') {
                    i++;
                } else if (c == '}') {
                    i++;
                    return b.build();
                } else {
                    throw error("expected ',' or '}'");
                }
            }
        }

        private TdTable parseArray() {
            i++; // '['
            TdTable.Builder b = TdTable.builder();
            skipWs();
            if (!atEnd() && src.charAt(i) == ']') {
                i++;
                return b.build();
            }
            while (true) {
                b.element(parseValue());
                skipWs();
                if (atEnd()) {
                    throw error("unterminated array");
                }
                char c = src.charAt(i);
                if (c == ',') {
                    i++;
                } else if (c == ']') {
                    i++;
                    return b.build();
                } else {
                    throw error("expected ',' or ']'");
                }
            }
        }

        private TdValue parseNumber() {
            int start = i;
            if (i < src.length() && src.charAt(i) == '-') {
                i++;
            }
            boolean dot = false;
            boolean exp = false;
            boolean digits = false;
            while (i < src.length()) {
                char c = src.charAt(i);
                if (c >= '0' && c <= '9') {
                    digits = true;
                    i++;
                } else if (c == '.' && !dot) {
                    dot = true;
                    i++;
                } else if ((c == 'e' || c == 'E') && !exp) {
                    exp = true;
                    dot = true;
                    i++;
                    if (i < src.length() && (src.charAt(i) == '+' || src.charAt(i) == '-')) {
                        i++;
                    }
                } else {
                    break;
                }
            }
            if (!digits) {
                throw error("invalid number");
            }
            String token = src.substring(start, i);
            if (dot) {
                return TdValue.of(Double.parseDouble(token));
            }
            return TdValue.of(Long.parseLong(token));
        }

        private String readString() {
            i++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw error("unterminated string");
                }
                char c = src.charAt(i);
                if (c == '"') {
                    i++;
                    return sb.toString();
                }
                if (c == '\\') {
                    if (i + 1 >= src.length()) {
                        throw error("dangling escape");
                    }
                    char e = src.charAt(i + 1);
                    i += 2;
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (i + 4 > src.length()) {
                                throw error("truncated \\u escape");
                            }
                            String hex = src.substring(i, i + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        }
                        default -> throw error("unknown escape \\" + e);
                    }
                } else {
                    sb.append(c);
                    i++;
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // structural equality of two parsed trees
    // ------------------------------------------------------------------

    /**
     * Recursive key-by-key equivalence of two parsed trees (used to assert JSON
     * round-trip equality without depending on key order or whitespace). Objects
     * compare as key sets (order-insensitive); arrays compare positionally.
     */
    public static boolean treesEqual(TdValue a, TdValue b) {
        if (a == b) {
            return true;
        }
        if (a instanceof TdTable ta && b instanceof TdTable tb) {
            if (ta.keys().size() != tb.keys().size()) {
                return false;
            }
            for (String k : ta.keys()) {
                if (tb.get(k) == null || !treesEqual(ta.get(k), tb.get(k))) {
                    return false;
                }
            }
            if (ta.elements().size() != tb.elements().size()) {
                return false;
            }
            for (int i = 0; i < ta.elements().size(); i++) {
                if (!treesEqual(ta.elements().get(i), tb.elements().get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof TdValue.Scalar sa && b instanceof TdValue.Scalar sb) {
            if (sa.kind() != sb.kind()) {
                return false;
            }
            return switch (sa.kind()) {
                case STRING -> sa.str().equals(sb.str());
                case INT -> sa.i() == sb.i();
                case FLOAT -> Double.compare(sa.f(), sb.f()) == 0;
                case BOOL -> sa.b() == sb.b();
            };
        }
        return false;
    }
}