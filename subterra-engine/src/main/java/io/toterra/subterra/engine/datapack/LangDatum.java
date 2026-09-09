package io.toterra.subterra.engine.datapack;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Engine-side schema for vanilla localization entries re-exported as canonical
 * td (p.2.2 block 6: DataPack → td round-trip extended to all kinds). Localized
 * strings travel as key/value pairs, mirroring the MC-shell consumer
 * {@code DatapackRegistrar.registerLang} (which feeds each {@code [k,v]} element
 * table into the key → value map):
 *
 * <ul>
 * <li>the payload is an element-list of {@code [ k = "<key>", v = "<value>" ]}
 * tables;</li>
 * <li>an element table with an empty {@code k} is skipped (weak entry).</li>
 * </ul>
 *
 * <p>{@link #read} preserves element order; {@link #write()} emits the element
 * tables in that same order, so {@code write} equals {@code Td.write} of the
 * canonical seed payload and is idempotent, satisfying the engine-side
 * round-trip contract.
 */
public record LangDatum(List<Entry> entries) {

    /** A single localization pair: a key string and its value string. */
    public record Entry(String k, String v) {
    }

    /**
     * Reads a lang payload table into a canonical {@link LangDatum}, preserving
     * element order and skipping element tables whose {@code k} is empty.
     */
    public static LangDatum read(TdTable p) {
        List<Entry> list = new ArrayList<>();
        for (TdValue item : p.elements()) {
            if (!(item instanceof TdTable t)) {
                continue;
            }
            String k = t.get("k") != null ? t.get("k").asString() : "";
            if (k.isEmpty()) {
                continue; // weak entry — skip
            }
            String v = t.get("v") != null ? t.get("v").asString() : "";
            list.add(new Entry(k, v));
        }
        return new LangDatum(List.copyOf(list));
    }

    /**
     * Writes this datum back to its canonical td table (deterministic: one
     * {@code [k,v]} element table per entry, order preserved).
     */
    public TdTable write() {
        TdTable.Builder b = TdTable.builder();
        for (Entry e : entries) {
            TdTable.Builder row = TdTable.builder();
            row.put("k", TdValue.str(e.k()));
            row.put("v", TdValue.str(e.v()));
            b.element(row.build());
        }
        return b.build();
    }
}