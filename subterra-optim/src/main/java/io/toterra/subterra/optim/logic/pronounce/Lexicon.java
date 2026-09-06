package io.toterra.subterra.optim.logic.pronounce;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Character → pronunciation lexicon (the multi-language pronunciation data
 * seam for the JEC-style reading search port): readings use the PinIn data
 * format {@code '<char>: r1, r2'} and may carry tone digits (stripped for
 * matching).
 *
 * <p>Two merge semantics are provided: {@link #withExtension(Lexicon)}
 * <em>replaces</em> a character's readings with the extension's (single-language
 * override), while {@link #mergedWith(Lexicon)} <em>appends and dedups</em>
 * readings so pronunciation packs from different languages can coexist on the
 * same character (e.g. 山 = shan from zh + san from ja). Entirely
 * deterministic and immutable after construction.</p>
 */
public final class Lexicon {

    private static final List<String> NONE = List.of();

    private final Map<Character, List<String>> readings;

    private Lexicon(Map<Character, List<String>> readings) {
        Map<Character, List<String>> copy = new HashMap<>();
        for (Map.Entry<Character, List<String>> e : readings.entrySet()) {
            copy.put(e.getKey(), e.getValue());
        }
        this.readings = Map.copyOf(copy);
    }

    /** Empty lexicon (matches nothing). */
    public static Lexicon empty() {
        return new Lexicon(Map.of());
    }

    /**
     * Parses lines of {@code 'c: r1, r2, ...'} (tone digits stripped). Blank
     * lines and comment lines ({@code //}) are ignored.
     */
    public static Lexicon ofLines(Iterable<String> lines) {
        Map<Character, List<String>> map = new HashMap<>();
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("//")) {
                continue;
            }
            int colon = t.indexOf(':');
            if (colon != 1) {
                throw new IllegalArgumentException("malformed lexicon line: " + line);
            }
            char c = t.charAt(0);
            List<String> rs = new ArrayList<>();
            for (String part : t.substring(colon + 1).split(",")) {
                String r = part.trim().toLowerCase(Locale.ROOT);
                if (!r.isEmpty()) {
                    rs.add(r.replaceAll("[0-9]", ""));
                }
            }
            if (!rs.isEmpty()) {
                map.put(c, rs);
            }
        }
        return new Lexicon(map);
    }

    /** Merged lexicon: {@code extra} readings take precedence per character. */
    public Lexicon withExtension(Lexicon extra) {
        Map<Character, List<String>> merged = new HashMap<>(readings);
        for (Map.Entry<Character, List<String>> e : extra.readings.entrySet()) {
            merged.put(e.getKey(), e.getValue());
        }
        return new Lexicon(merged);
    }

    /**
     * Merged lexicon with append-and-dedup semantics: for each character,
     * {@code other}'s readings are appended after this lexicon's, dropping
     * exact duplicates while preserving insertion order (base readings first,
     * then {@code other}'s). This lets pronunciation packs from different
     * languages coexist on the same character (e.g. 山 = shan + san).
     * Single pass, deterministic, no O(n²).
     */
    public Lexicon mergedWith(Lexicon other) {
        Map<Character, LinkedHashSet<String>> acc = new LinkedHashMap<>();
        for (Map.Entry<Character, List<String>> e : readings.entrySet()) {
            acc.computeIfAbsent(e.getKey(), k -> new LinkedHashSet<>()).addAll(e.getValue());
        }
        for (Map.Entry<Character, List<String>> e : other.readings.entrySet()) {
            acc.computeIfAbsent(e.getKey(), k -> new LinkedHashSet<>()).addAll(e.getValue());
        }
        Map<Character, List<String>> merged = new HashMap<>();
        for (Map.Entry<Character, LinkedHashSet<String>> e : acc.entrySet()) {
            merged.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return new Lexicon(merged);
    }

    /** Readings of a character (lowercased, tone-stripped); empty when unknown. */
    public List<String> readings(char c) {
        return readings.getOrDefault(Character.toLowerCase(c), NONE);
    }
}