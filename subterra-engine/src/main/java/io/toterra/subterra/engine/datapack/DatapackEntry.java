package io.toterra.subterra.engine.datapack;

import io.toterra.subterra.engine.config.TdTable;

/**
 * p.2.2 a single datapack entry: kind + namespace + path carrying an opaque td
 * table payload. Schema-free — the consumer (recipe loader, tag registrar, …)
 * interprets the payload; a broken entry surfaces at consumption time, never
 * silently drops out of the registry.
 */
public record DatapackEntry(EntryKind kind, String namespace, String path, TdTable payload) {

    /** Canonical id {@code <namespace>:<kind>/<path>} (deterministic). */
    public String id() {
        return namespace + ":" + kind.dir() + "/" + path;
    }
}