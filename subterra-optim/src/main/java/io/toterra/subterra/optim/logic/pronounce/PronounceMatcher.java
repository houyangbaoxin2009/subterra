package io.toterra.subterra.optim.logic.pronounce;

import java.util.Locale;

/**
 * Per-character consuming matcher (JEC-style pinyin search core): a query
 * string (pinyin initials / romaji / romanization) matches a text when the
 * text's characters can be read in sequence — each character consumes a
 * prefix of the query via one of its readings, in order, until the query is
 * fully consumed. Case-insensitive; deterministic; recursion bounded by query
 * length so there is no exponential blow-up on practical inputs.
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
        for (int i = 0; i < haystack.length(); i++) {
            if (matchFrom(haystack, i, q, 0)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchFrom(CharSequence hay, int hayPos, String q, int qPos) {
        if (qPos >= q.length()) {
            return true; // query fully consumed
        }
        if (hayPos >= hay.length()) {
            return false; // ran out of characters first
        }
        char c = hay.charAt(hayPos);
        // Prefix-style typing: the remaining query may stop as a prefix of the
        // next character's reading (e.g. "baob" for 宝杯).
        int remaining = q.length() - qPos;
        for (String reading : lexicon.readings(c)) {
            if (reading.length() >= remaining && q.regionMatches(qPos, reading, 0, remaining)) {
                return true;
            }
        }
        for (String reading : lexicon.readings(c)) {
            if (q.regionMatches(qPos, reading, 0, reading.length())
                    && matchFrom(hay, hayPos + 1, q, qPos + reading.length())) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(CharSequence s) {
        return s.toString().toLowerCase(Locale.ROOT);
    }
}