package io.toterra.subterra.engine.datapack;

import io.toterra.subterra.engine.config.Td;

/** p.2.2 block 5 — export front door for the engine-side td datapack. Pure JDK. */
public final class DatapackExporter {
    private DatapackExporter() {
    }

    /**
     * Exports a RECIPE entry payload to its canonical td text (bare table, no
     * {@code type tie<data>} header). Deterministic; throws
     * IllegalArgumentException for unsupported/malformed recipe types.
     */
    public static String exportRecipeTd(DatapackEntry entry) {
        return Td.write(RecipeDatum.read(entry.payload()).write());
    }

    /**
     * Exports any entry payload to its canonical td text (bare table, no
     * {@code type tie<data>} header), dispatching per kind:
     *
     * <ul>
     * <li>RECIPE → {@link RecipeDatum#read} + {@link RecipeDatum#write} (SHAPED /
     * SMOKING normalization via {@link #exportRecipeTd});</li>
     * <li>TAG → {@link TagDatum#read} + {@link TagDatum#write}
     * ({@code replace} / {@code values});</li>
     * <li>LANG → {@link LangDatum#read} + {@link LangDatum#write}
     * ({@code [k,v]} element tables);</li>
     * <li>NOISE_SETTINGS → {@link NoiseSettingsDatum#read} +
     * {@link NoiseSettingsDatum#write} (canonical td, reserved plus keys stripped;
     * JSON equivalence via {@link NoiseSettingsJson}).</li>
     * <li>LOOT_TABLE / WORLDGEN / STRUCTURE / FUNCTION → canonical pass-through:
     * the payload table is preserved exactly (schema validated at rebuild time by
     * the registrar consumers).</li>
     * </ul>
     *
     * Deterministic; throws IllegalArgumentException for malformed/unsupported
     * RECIPE / TAG / LANG payloads, and passthroughs the rest unchanged.
     */
    public static String exportEntryTd(DatapackEntry entry) {
        return switch (entry.kind()) {
            case RECIPE -> exportRecipeTd(entry);
            case TAG -> Td.write(TagDatum.read(entry.payload()).write());
            case LANG -> Td.write(LangDatum.read(entry.payload()).write());
            case NOISE_SETTINGS -> exportNoiseSettingsTd(entry);
            default -> Td.write(entry.payload()); // schema-free pass-through
        };
    }

    /**
     * Exports a NOISE_SETTINGS entry payload to its canonical td text: fixed
     * top-level field order, reserved {@code tie_} plus keys stripped.
     */
    public static String exportNoiseSettingsTd(DatapackEntry entry) {
        return Td.write(NoiseSettingsDatum.read(entry.payload()).write());
    }

    /**
     * Exports a NOISE_SETTINGS entry payload to vanilla {@code NoiseGeneratorSettings}
     * JSON text (plus keys stripped) — the "td = JSON datapack plus" L2 equivalence export.
     */
    public static String exportNoiseSettingsJson(DatapackEntry entry) {
        return NoiseSettingsJson.toJson(entry.payload());
    }
}