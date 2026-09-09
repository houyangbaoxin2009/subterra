package io.toterra.subterra.engine.datapack;

import io.toterra.subterra.engine.config.TdValue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * p.2.2 a loaded datapack: an ordered registry of entries (sorted by canonical
 * id for determinism) plus optional tie-logic library declarations from
 * {@code pack.td}. Pure JDK; entries are in-memory only — direct loading, no
 * JSON files are ever produced.
 */
public final class Datapack {

    private final String name;
    private final String title;
    private final Map<String, DatapackEntry> entries; // by id, sorted
    private final List<TieLibDecl> tieLibraries;
    private final Map<String, TdValue> rules;

    Datapack(String name, String title, Map<String, DatapackEntry> entries, List<TieLibDecl> tieLibraries,
             Map<String, TdValue> rules) {
        this.name = name;
        this.title = title;
        this.entries = new TreeMap<>(entries);
        this.tieLibraries = List.copyOf(tieLibraries);
        this.rules = new LinkedHashMap<>(rules);
    }

    public String name() {
        return name;
    }

    public String title() {
        return title;
    }

    /** All entries by canonical id, deterministically sorted (no Map.copyOf — that drops order). */
    public Map<String, DatapackEntry> entries() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    /** Entries of one kind, in id order. */
    public List<DatapackEntry> byKind(EntryKind kind) {
        return entries.values().stream().filter(e -> e.kind() == kind).toList();
    }

    public DatapackEntry get(EntryKind kind, String namespace, String path) {
        return entries.get(namespace + ":" + kind.dir() + "/" + path);
    }

    /** tie-logic library declarations (from the optional pack.td manifest). */
    public List<TieLibDecl> tieLibraries() {
        return tieLibraries;
    }

    /**
     * Pack-level key→td-value rules declared in pack.td, intended for
     * datapack-level tuning overridable per save/session. The forward hook to the
     * p.2.17 rule system.
     */
    public Map<String, TdValue> rules() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(rules));
    }
}