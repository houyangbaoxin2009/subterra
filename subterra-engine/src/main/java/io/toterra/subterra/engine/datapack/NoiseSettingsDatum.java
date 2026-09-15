package io.toterra.subterra.engine.datapack;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.util.List;

/**
 * Engine-side schema for vanilla noise-settings entries re-exported as canonical
 * td (data/{ns}/worldgen/noise_settings/{path}.td) — the first line of the
 * "td = JSON datapack plus" superset refactor (L1 expression + L2 canonical
 * td export). The payload is an OPAQUE td table whose member/leaf key names mirror
 * the vanilla {@code NoiseGeneratorSettings} JSON codec keys one-to-one, at every
 * nesting depth. This datum only:
 *
 * <ul>
 * <li>{@link #write()} re-emits the canonical td — the eleven fixed top-level
 * fields in a deterministic order (the vanilla codec field order) and every nested
 * member in its (deterministic) source order — while DROPPING the reserved "plus"
 * keys (see {@link #PLUS_PREFIX}); because the order is fixed and stripping is
 * idempotent, {@code write ∘ read ∘ write ≡ write} (byte-identical closed loop).</li>
 * <li>{@link #read(TdTable)} accepts a raw payload table and returns the datum,
 * preserving the payload VERBATIM — so the plain td→td round trip
 * ({@code Td.parse → read → Td.write(payload())}) keeps the full payload including
 * any plus keys. Stripping happens only on the explicit canonical/export paths
 * ({@link #write()}, {@code NoiseSettingsJson}).</li>
 * </ul>
 *
 * <p>L3 plus syntax is a RESERVED INTERFACE ONLY (no runtime evaluation this
 * round): a plus key is any member whose name starts with {@value #PLUS_PREFIX}
 * ({@code tie_}), permitted at any nesting depth. td's identifier grammar cannot
 * carry the literal {@code tie(<fn>)} / {@code @gen(seed=..)} *key* spellings, so
 * they are carried as the <em>value</em> strings of {@code tie_} keys, e.g.
 * {@code tie_final_density = "tie(subterra:density)"} or
 * {@code tie_gen = "@gen(seed=44905237)"}. JSON export (and {@link #write()})
 * delete these plus keys; the parts that are vanilla-legal fields export normally.
 */
public record NoiseSettingsDatum(TdTable payload) {

    /** Reserved L3 plus-key prefix. Keys starting with this (any depth) are stripped on canonical/JSON export. */
    public static final String PLUS_PREFIX = "tie_";

    /** Fixed canonical top-level field order (matches the vanilla NoiseGeneratorSettings codec order). */
    private static final List<String> TOP_LEVEL_ORDER = List.of(
            "aquifers_enabled", "default_block", "default_fluid", "disable_mob_generation",
            "legacy_random_source", "noise", "noise_router", "ore_veins_enabled",
            "sea_level", "spawn_target", "surface_rule");

    /**
     * Accepts a raw noise-settings payload table. Verbs preserve the payload
     * verbatim (plus keys included). Minimal validation: the payload must be a
     * table (it always is) with no duplicate member keys.
     */
    public static NoiseSettingsDatum read(TdTable p) {
        if (!p.duplicates().isEmpty()) {
            throw new IllegalArgumentException("duplicate noise_settings keys: " + p.duplicates());
        }
        return new NoiseSettingsDatum(p);
    }

    /** True when {@code name} is a reserved plus key ({@value #PLUS_PREFIX}-prefixed). */
    public static boolean isPlusKey(String name) {
        return name.startsWith(PLUS_PREFIX);
    }

    /**
     * Returns a deep copy of {@code value} with every plus key removed at any
     * nesting depth (named {@code tie_} members are dropped, scalars and array
     * elements pass through, nested tables are rebuilt). Shared by {@link #write()}
     * and the JSON exporter so both strip identically. Any {@code tie_} value
     * fields that remain (they carry the L3 {@code tie(...)} / {@code @gen(...)}
     * spellings) are dropped by the membership filter, not by value inspection.
     */
    public static TdValue stripPlus(TdValue value) {
        if (!(value instanceof TdTable t)) {
            return value;
        }
        TdTable.Builder b = TdTable.builder();
        for (String k : t.keys()) {
            if (isPlusKey(k)) {
                continue;
            }
            b.put(k, stripPlus(t.get(k)));
        }
        for (TdValue e : t.elements()) {
            b.element(stripPlus(e));
        }
        return b.build();
    }

    /**
     * Writes this datum to its canonical td table: top-level fields in
     * {@link #TOP_LEVEL_ORDER}, nested members in source order, all plus keys
     * stripped. Deterministic and idempotent.
     */
    public TdTable write() {
        TdTable.Builder b = TdTable.builder();
        for (String k : TOP_LEVEL_ORDER) {
            TdValue v = payload.get(k);
            if (v != null) {
                b.put(k, stripPlus(v));
            }
        }
        // Any non-fixed, non-plus top-level field (future vanilla key or a bare union)
        // is appended in source order so nothing is silently lost.
        for (String k : payload.keys()) {
            if (TOP_LEVEL_ORDER.contains(k) || isPlusKey(k)) {
                continue;
            }
            b.put(k, stripPlus(payload.get(k)));
        }
        return b.build();
    }
}