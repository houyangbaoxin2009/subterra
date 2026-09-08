package io.toterra.subterra.engine.datapack;

import io.toterra.subterra.engine.tie.TieFunction;
import io.toterra.subterra.engine.tie.TieLibrary;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * p.2.2 tie-logic loading chain for a datapack: the {@code pack.td} tie
 * declarations are resolved into loaded {@link TieLibrary}s; FUNCTION entries
 * bind their payload ({@code lib} / {@code fn}) to the exported symbol
 * {@code <lib>$<fn>} (ABI: tie {@code namespace::fn} → FFM symbol
 * {@code namespace$fn}). Owns the library lifecycle — {@link #close()} releases
 * every loaded library.
 */
public final class TieLogicBundle implements AutoCloseable {

    private final Map<String, TieLibrary> libraries;
    private final Datapack datapack;

    TieLogicBundle(Datapack datapack, Map<String, TieLibrary> libraries) {
        this.datapack = datapack;
        this.libraries = Map.copyOf(libraries);
    }

    /** Loaded libraries by lib name. */
    public Map<String, TieLibrary> libraries() {
        return libraries;
    }

    /**
     * Resolves a FUNCTION entry to its exported tie function. The payload keys
     * {@code lib} (tie namespace) and {@code fn} select the symbol; a missing
     * {@code fn} defaults to the entry path. Throws on kinds other than
     * FUNCTION, undeclared libs and unexported symbols — a tie binding that
     * references nothing never silently no-ops.
     */
    public TieFunction resolve(DatapackEntry entry) {
        if (entry.kind() != EntryKind.FUNCTION) {
            throw new IllegalArgumentException("datapack " + datapack.name() + ": tie binding requested for non-function entry " + entry.id());
        }
        String lib = entry.payload().get("lib") != null ? entry.payload().get("lib").asString() : "";
        TieLibrary library = libraries.get(lib);
        if (library == null) {
            throw new IllegalArgumentException("datapack " + datapack.name() + ": function entry " + entry.id()
                    + " references undeclared tie lib '" + lib + "' (pack.td tie list)");
        }
        String fn = entry.payload().get("fn") != null ? entry.payload().get("fn").asString() : defaultFn(entry);
        String symbol = lib + "$" + fn;
        Optional<TieFunction> func = library.find(symbol);
        if (func.isEmpty()) {
            throw new IllegalArgumentException("datapack " + datapack.name() + ": tie symbol not exported: " + symbol);
        }
        return func.get();
    }

    private static String defaultFn(DatapackEntry entry) {
        String path = entry.path();
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    /** Releases every loaded library; later resolves fail. */
    @Override
    public void close() {
        for (TieLibrary lib : libraries.values()) {
            lib.close();
        }
    }
}