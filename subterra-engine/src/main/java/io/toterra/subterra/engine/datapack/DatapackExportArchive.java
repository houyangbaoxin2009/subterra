package io.toterra.subterra.engine.datapack;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * p.2.2 export archive — a content-level serialization of a whole datapack into
 * a single td document (the counterpart of {@link DatapackPack}, which is
 * file-level: this one carries the registered entry content, not the files).
 * Every entry is rendered as its canonical td payload (RECIPE → {@link
 * RecipeDatum}.write, TAG → {@link TagDatum}.write, LANG → {@link LangDatum}.write,
 * all other kinds → the original payload pass-through) and can be rehydrated
 * back into a {@link Datapack}. The archive carries entry content only;
 * manifest-level {@code rules} rehydrate empty (rules live in pack.td, not in
 * the entry stream).
 *
 * <p>Document shape (pure JDK, no JSON):
 * <pre>{@code
 * type tie<data>
 * export = [
 *   version = 1,
 *   entries = [
 *     [ kind = "tag", ns = "toterra", path = "item/special", payload = [ ... ] ],
 *     ...
 *   ],
 * ]
 * }</pre>
 * Entries are emitted in {@code dp.entries()} id-sorted order (already
 * deterministic); {@code Td.parse} strips the optional {@code export =} table
 * name on rehydration, so both the bare and the named top-level forms load.
 *
 * <p>Pass-through kinds (LOOT_TABLE / WORLDGEN / STRUCTURE / FUNCTION) preserve
 * the payload td exactly because their schema is validated at rebuild time by
 * the registrar consumers (buildLootTable / buildConfiguredFeature /
 * buildStructure); in particular {@code LootTable} exposes no public pools
 * accessor, so object reconstruction from a vanilla object is impossible and is
 * intentionally not attempted — the td payload is the single source of truth.
 *
 * <p>Round-trip contract (asserted in the probe task):
 * {@code export(rehydrate(export(dp))).equals(export(dp))} byte-for-byte.
 */
public final class DatapackExportArchive {

    /** Current archive format version. */
    public static final long VERSION = 1;

    private static final String MARKER = "export";

    private DatapackExportArchive() {
    }

    /**
     * Serializes a datapack to a single td document (canonical per-kind payloads,
     * entries id-sorted).
     */
    public static String export(Datapack dp) {
        TdTable.Builder entriesB = TdTable.builder();
        for (DatapackEntry e : dp.entries().values()) { // already id-sorted; rely on it
            TdTable payload = canonicalPayload(e);
            entriesB.element(TdTable.builder()
                    .put("kind", TdValue.str(e.kind().dir()))
                    .put("ns", TdValue.str(e.namespace()))
                    .put("path", TdValue.str(e.path()))
                    .put("payload", payload)
                    .build());
        }
        TdTable doc = TdTable.builder()
                .put("version", TdValue.of(VERSION))
                .put("entries", entriesB.build())
                .build();
        // Named top-level table (same trick as DatapackPack.export): wrap so the
        // document is `export = [...]`; Td.parse strips the name on rehydration.
        return "type tie<data>\n" + Td.write(TdTable.builder().put(MARKER, doc).build());
    }

    /**
     * Parses an export-archive document back into a {@link Datapack} named
     * {@code "export-archive"} with empty tie libraries, rebuilding each entry
     * from its kind/ns/path/payload (entries keep
     * {@code data/<ns>/<kind>/<path>} semantics). Malformed input throws
     * {@link IllegalArgumentException} naming the offending entry.
     */
    public static Datapack rehydrate(String source) {
        TdTable root = Td.parse(source);
        TdValue markerValue = root.get(MARKER);
        if (!(markerValue instanceof TdTable doc)) {
            throw new IllegalArgumentException("not a datapack export archive (missing 'export')");
        }
        long version = doc.get("version") != null ? doc.get("version").asInt() : -1;
        if (version != VERSION) {
            throw new IllegalArgumentException("unsupported datapack export archive version: " + version);
        }
        TdValue entriesValue = doc.get("entries");
        if (!(entriesValue instanceof TdTable entries)) {
            throw new IllegalArgumentException("datapack export archive missing 'entries' table");
        }
        Map<String, DatapackEntry> map = new TreeMap<>();
        for (TdValue item : entries.elements()) {
            if (!(item instanceof TdTable t)) {
                throw new IllegalArgumentException("datapack export archive entry is not a table");
            }
            String path = t.get("path") != null ? t.get("path").asString() : "";
            String kind = t.get("kind") != null ? t.get("kind").asString() : "";
            String ns = t.get("ns") != null ? t.get("ns").asString() : "";
            TdValue payloadValue = t.get("payload");
            if (!(payloadValue instanceof TdTable payload)) {
                throw new IllegalArgumentException("datapack export archive entry missing payload table: " + path);
            }
            EntryKind kindEnum;
            try {
                kindEnum = EntryKind.fromDirectory(kind);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("datapack export archive entry has unknown kind '" + kind
                        + "': " + path);
            }
            DatapackEntry entry = new DatapackEntry(kindEnum, ns, path, payload);
            map.put(entry.id(), entry);
        }
        return new Datapack("export-archive", "export-archive", map, List.of(), Map.of());
    }

    /** Canonical td payload per kind; pass-through for the schema-free kinds. */
    private static TdTable canonicalPayload(DatapackEntry e) {
        return switch (e.kind()) {
            case RECIPE -> RecipeDatum.read(e.payload()).write();
            case TAG -> TagDatum.read(e.payload()).write();
            case LANG -> LangDatum.read(e.payload()).write();
            default -> e.payload(); // pass-through, schema validated at rebuild time
        };
    }
}