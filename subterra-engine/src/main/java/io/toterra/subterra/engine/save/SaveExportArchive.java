package io.toterra.subterra.engine.save;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * p.2.3.6 save export archive — a content-level serialization of a whole
 * {@link SaveContainer} into a single td document, structurally isomorphic to the
 * datapack-side {@code io.toterra.subterra.engine.datapack.DatapackExportArchive}
 * (named top-level table + {@code type tie<data>} header + {@code version}, so
 * {@code Td.parse} strips the optional table name on rehydration). Every mounted
 * slot carries its own td document ({@code doc}, nested as-is — its key order is
 * produced by the document's own writer and round-trips via
 * {@code Td.parse(Td.write(doc))}); rule-bearing slots carry their slot-level
 * overlay ({@code rules}, key-sorted); the global layer carries {@code global}.
 * The archive carries save content only; the {@code /subterra export <save>}
 * command wiring on top of this builder is done in the p.2.18 engine export hub
 * (this sub-item only documents that landing point and adds no command layer).
 *
 * <p>Document shape (pure JDK, no JSON):
 * <pre>{@code
 * type tie<data>
 * export = [
 *   version = 1,
 *   global = [
 *     [ k = "verbose", v = "on" ],
 *     ...
 *   ],
 *   slots = [
 *     [ slot = "world", rules = [ [ k = "world.scale", v = 3 ], ... ], doc = [ ... ] ],
 *     ...
 *   ],
 * ]
 * }</pre>
 * Determinism: slots iterate {@link SaveContainer#slots()} ({@link SaveSlot} enum
 * order); each slot's {@code rules} and the {@code global} rules are key-sorted
 * via a {@link TreeMap}; {@code doc} is emitted verbatim. Slots with no overlay
 * omit {@code rules}; {@code global} is always emitted (empty table for none).
 *
 * <p>Round-trip contract (asserted in the probe task):
 * {@code export(rehydrate(export(c))).equals(export(c))} byte-for-byte, and the
 * rehydrated container is field-level equal to the source (per-slot document text,
 * overlays, global rules, {@code slots()}).
 */
public final class SaveExportArchive {

    /** Current archive format version. */
    public static final long VERSION = 1;

    private static final String MARKER = "export";

    private SaveExportArchive() {
    }

    /**
     * Serializes a {@link SaveContainer} to a single td document (deterministic:
     * slots in enum order, rules key-sorted, documents emitted verbatim; must be a
     * pure read — the container is never mutated).
     */
    public static String export(SaveContainer c) {
        TdTable.Builder slotsB = TdTable.builder();
        for (SaveSlot slot : c.slots()) {
            TdTable.Builder slotB = TdTable.builder().put("slot", TdValue.str(slot.dir()));
            Map<String, TdValue> overlay = c.ruleOverlays(slot);
            if (!overlay.isEmpty()) {
                slotB.put("rules", rulesTable(overlay));
            }
            slotB.put("doc", c.document(slot));
            slotsB.element(slotB.build());
        }
        TdTable doc = TdTable.builder()
                .put("version", TdValue.of(VERSION))
                .put("global", rulesTable(c.globalRules()))
                .put("slots", slotsB.build())
                .build();
        // Named top-level table (same trick as the datapack archive): wrap so the
        // document is `export = [...]`; Td.parse strips the name on rehydration.
        return "type tie<data>\n" + Td.write(TdTable.builder().put(MARKER, doc).build());
    }

    /**
     * Parses an export-archive document back into a {@link SaveContainer},
     * reattaching each slot's {@code doc} verbatim and re-applying its slot-level
     * {@code rules} overlay plus the {@code global} layer. Malformed input throws
     * {@link IllegalArgumentException}: missing/non-table {@code export}, out-of-range
     * {@code version}, non-table slot entries, a missing slot {@code doc}, a
     * non-table slot {@code rules}, or an unknown slot value.
     */
    public static SaveContainer rehydrate(String source) {
        TdTable root = Td.parse(source);
        TdValue markerValue = root.get(MARKER);
        if (!(markerValue instanceof TdTable doc)) {
            throw new IllegalArgumentException("not a save export archive (missing 'export')");
        }
        long version = doc.get("version") != null ? doc.get("version").asInt() : -1;
        if (version != VERSION) {
            throw new IllegalArgumentException("unsupported save export archive version: " + version);
        }
        SaveContainer container = new SaveContainer();
        TdValue globalValue = doc.get("global");
        if (globalValue instanceof TdTable global) {
            container.globalRules(readRulesList(global));
        }
        TdValue slotsValue = doc.get("slots");
        if (slotsValue != null) {
            if (!(slotsValue instanceof TdTable slots)) {
                throw new IllegalArgumentException("save export archive 'slots' is not a table");
            }
            for (TdValue item : slots.elements()) {
                if (!(item instanceof TdTable t)) {
                    throw new IllegalArgumentException("save export archive slot is not a table");
                }
                TdValue slotValue = t.get("slot");
                String dir = slotValue != null ? slotValue.asString() : "";
                SaveSlot slot = byDir(dir);
                TdValue docValue = t.get("doc");
                if (!(docValue instanceof TdTable slotDoc)) {
                    throw new IllegalArgumentException("save export archive slot missing doc table: " + dir);
                }
                container.attach(slot, slotDoc);
                TdValue rulesValue = t.get("rules");
                if (rulesValue != null) {
                    if (!(rulesValue instanceof TdTable rulesT)) {
                        throw new IllegalArgumentException("save export archive slot rules is not a table: " + dir);
                    }
                    container.overlay(slot, readRulesList(rulesT));
                }
            }
        }
        return container;
    }

    /** Rules table {@code [ [ k = ..., v = ... ], ... ]}, keys sorted deterministically. */
    private static TdTable rulesTable(Map<String, TdValue> rules) {
        TdTable.Builder b = TdTable.builder();
        for (Map.Entry<String, TdValue> e : new TreeMap<>(rules).entrySet()) {
            b.element(TdTable.builder()
                    .put("k", TdValue.str(e.getKey()))
                    .put("v", e.getValue())
                    .build());
        }
        return b.build();
    }

    /** Reads a bare rules list table {@code [ [ k = ..., v = ... ], ... ]} back into a map. */
    private static Map<String, TdValue> readRulesList(TdTable list) {
        Map<String, TdValue> out = new LinkedHashMap<>();
        for (TdValue item : list.elements()) {
            if (!(item instanceof TdTable t)) {
                continue; // schema-free, mirrors DatapackRules.fromManifest
            }
            String k = t.get("k") != null ? t.get("k").asString() : "";
            if (k.isBlank()) {
                continue;
            }
            TdValue v = t.get("v");
            if (v == null) {
                continue;
            }
            out.put(k, v); // our export emits unique sorted keys, so last-wins == first-wins
        }
        return out;
    }

    /** Maps a lowercase slot directory name back to its enum; unknown → throw. */
    private static SaveSlot byDir(String dir) {
        for (SaveSlot slot : SaveSlot.values()) {
            if (slot.dir().equals(dir)) {
                return slot;
            }
        }
        throw new IllegalArgumentException("unknown save slot in export archive: '" + dir + "'");
    }
}