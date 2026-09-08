package io.toterra.subterra.engine.optim.logic.pronounce;

import java.util.List;
import java.util.Locale;

/**
 * Per-character consuming matcher (JEC-style pinyin search core): a query
 * string (pinyin full / initials / romaji / romanization) matches a text when
 * the text's characters can be read in sequence — each character consumes a
 * prefix of the query via one of its readings, in order, until the query is
 * fully consumed. Also supports initials-only matching (one letter per char,
 * e.g. {@code "zs"} for 钻石) and a literal-substring fallback for non-CJK
 * characters so plain ASCII item ids still work.
 *
 * <p>Case-insensitive; deterministic; recursion is bounded by the query
 * length (each recursive call advances qPos by at least 1), so there is no
 * exponential blow-up on practical inputs.</p>
 */
public final class PronounceMatcher {

    private final Lexicon lexicon;

    public PronounceMatcher(Lexicon lexicon) {
        this.lexicon = lexicon;
    }

    /** True when {@code query} matches a pronunciation of {@code haystack}. */
    public boolean contains(CharSequence haystack, CharSequence query) {
        String q = normalize(query);
        if (q.isEmpty()) {
            return true;
        }
        String h = normalize(haystack);
        for (int i = 0; i < h.length(); i++) {
            if (matchFrom(h, i, q, 0)) {
                return true;
            }
        }
        // Literal-substring fallback: characters without pinyin readings
        // (ASCII, symbols, etc.) are matched by plain containment.
        return h.contains(q);
    }

    private boolean matchFrom(String hay, int hayPos, String q, int qPos) {
        if (qPos >= q.length()) {
            return true; // query fully consumed
        }
        if (hayPos >= hay.length()) {
            return false; // ran out of characters first
        }
        char c = hay.charAt(hayPos);
        List<String> readings = lexicon.readings(c);
        int remaining = q.length() - qPos;

        if (!readings.isEmpty()) {
            // 1. Prefix-style typing: the remaining query may stop as a prefix
            //    of this character's reading (e.g. "baob" for 宝杯).
            for (String reading : readings) {
                if (reading.length() >= remaining && q.regionMatches(qPos, reading, 0, remaining)) {
                    return true;
                }
            }
            // 2. Full-reading consumption: this reading matches a prefix of the
            //    query exactly, then recurse on the next haystack char.
            for (String reading : readings) {
                if (q.regionMatches(qPos, reading, 0, reading.length())
                        && matchFrom(hay, hayPos + 1, q, qPos + reading.length())) {
                    return true;
                }
            }
            // 3. Initials-only (声母): consume just the first letter of this
            //    reading and recurse — so "zs" matches 钻石 (zuan+shi).
            for (String reading : readings) {
                if (!reading.isEmpty() && q.charAt(qPos) == reading.charAt(0)
                        && matchFrom(hay, hayPos + 1, q, qPos + 1)) {
                    return true;
                }
            }
        } else {
            // 4. Literal fallback for characters without readings: match the
            //    single character and recurse (handles embedded ASCII such as
            //    "oak_planks" containing "plank" via the prefix path below).
            if (q.charAt(qPos) == c && matchFrom(hay, hayPos + 1, q, qPos + 1)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(CharSequence s) {
        return s.toString().toLowerCase(Locale.ROOT);
    }
}