package io.toterra.subterra.engine.datapack;

import java.util.List;

/**
 * p.2.2.8 dual-interface read facade over a {@link Datapack}: the SAME datum is
 * reachable two ways —
 *
 * <ul>
 * <li><b>typed API route</b> — {@link #entry} / {@link #byKind} return the
 * loaded {@link DatapackEntry} objects directly (identity-preserving, the very
 * instances in the registry);</li>
 * <li><b>td text route</b> — {@link #tdText} yields the canonical td text via
 * {@link DatapackExporter#exportEntryTd}, byte-identical to what the loader /
 * registrar consumers read, and parseable by tiec {@code config.parse_data}
 * (tie consumption of datapack content), with the p.2.1 tie bridge as the FFM
 * binding entry (scalar string / i64 params).</li>
 * </ul>
 *
 * <p>Both routes are deterministic and agree on the same underlying datapack
 * entry. Pure JDK; no external dependencies.
 */
public final class DatapackLookup {
    private DatapackLookup() {
    }

    /** Typed API route: the entry for kind/ns/path (null when absent). */
    public static DatapackEntry entry(Datapack dp, EntryKind kind, String namespace, String path) {
        return dp.get(kind, namespace, path);
    }

    /** Typed API route: all entries of one kind, in id order. */
    public static List<DatapackEntry> byKind(Datapack dp, EntryKind kind) {
        return dp.byKind(kind);
    }

    /** td route: canonical td text of one entry (bare table, no header). */
    public static String tdText(DatapackEntry entry) {
        return DatapackExporter.exportEntryTd(entry);
    }

    /**
     * td route: canonical td text of the entry for kind/ns/path; empty string
     * when absent.
     */
    public static String tdText(Datapack dp, EntryKind kind, String namespace, String path) {
        DatapackEntry entry = dp.get(kind, namespace, path);
        return entry != null ? DatapackExporter.exportEntryTd(entry) : "";
    }
}