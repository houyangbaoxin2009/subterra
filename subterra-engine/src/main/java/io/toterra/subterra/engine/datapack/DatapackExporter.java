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
}