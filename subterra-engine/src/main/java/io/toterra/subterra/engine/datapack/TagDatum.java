package io.toterra.subterra.engine.datapack;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.util.List;

/**
 * Engine-side schema for vanilla tag entries re-exported as canonical td
 * (p.2.2 block 6: DataPack → td round-trip extended to all kinds). The normalizer
 * mirrors the MC-shell consumer {@code DatapackRegistrar.registerTags} (which
 * indexes the {@code values} string list by entry id) plus the seed's
 * {@code replace} field:
 *
 * <ul>
 * <li>{@code replace}: boolean, optional, default {@code false};</li>
 * <li>{@code values}: array of string elements (the registered item ids).</li>
 * </ul>
 *
 * <p>{@link #read} normalizes the payload (replace defaults to false; values are
 * the string elements). {@link #write()} is deterministic and canonical,
 * emitting {@code [ replace = <bool>, values = [ <strings...> ] ]} in that field
 * order, so {@code replace} is always written (it carries a default) and the
 * output equals {@code Td.write} of the canonical seed payload. {code write} is
 * therefore idempotent, satisfying the engine-side round-trip contract.
 */
public record TagDatum(boolean replace, List<String> values) {

    /**
     * Reads a tag payload table into a canonical {@link TagDatum}; {@code replace}
     * defaults to {@code false} when absent.
     */
    public static TagDatum read(TdTable p) {
        boolean replace = p.get("replace") != null ? p.get("replace").asBool() : false;
        List<String> values = elementStrings(p.get("values"));
        return new TagDatum(replace, List.copyOf(values));
    }

    /**
     * Writes this datum back to its canonical td table (deterministic field
     * order: {@code replace} then {@code values}).
     */
    public TdTable write() {
        TdTable.Builder b = TdTable.builder();
        b.put("replace", TdValue.of(replace));
        TdTable.Builder valuesB = TdTable.builder();
        for (String v : values) {
            valuesB.element(TdValue.str(v));
        }
        b.put("values", valuesB.build());
        return b.build();
    }

    /** String elements of a table value; empty list when absent or not a table. */
    private static List<String> elementStrings(TdValue v) {
        if (!(v instanceof TdTable t)) {
            return List.of();
        }
        return t.elements().stream().map(TdValue::asString).toList();
    }
}