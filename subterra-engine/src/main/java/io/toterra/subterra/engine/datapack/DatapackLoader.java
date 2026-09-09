package io.toterra.subterra.engine.datapack;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * p.2.2 datapack loader — pure td direct loading (no JSON, no vanilla pack
 * pipeline). Discovery is dual:
 *
 * <ul>
 * <li>directory scan of the vanilla-mirroring layout
 * {@code <pack>/data/<namespace>/<kind>/<path>.td};</li>
 * <li>an optional {@code <pack>/pack.td} manifest declaring pack metadata,
 * tie-logic libraries ({@code tie = [ [ lib = .., dll = .. ], ... ]}) and
 * additive entries ({@code entries = [ [ kind, ns, path, file ], ... ]}).</li>
 * </ul>
 *
 * <p>Deterministic: files are visited in sorted relative-path order; entry id =
 * {@code <namespace>:<kind>/<path>}. Malformed td raises
 * {@link IllegalArgumentException} naming the offending file — a broken entry
 * never silently vanishes (it surfaces at load, and per-kinds with it at use).
 */
public final class DatapackLoader {

    private static final String MANIFEST = "pack.td";

    private DatapackLoader() {
    }

    /** Loads a datapack from its directory (throws on malformed td). */
    public static Datapack load(Path packDir) {
        Path root = packDir.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("datapack directory not found: " + root);
        }
        Path manifestPath = root.resolve(MANIFEST);
        TdTable manifest = Files.isRegularFile(manifestPath)
                ? parseTd(root, manifestPath)
                : TdTable.builder().build();

        Map<String, DatapackEntry> entries = new LinkedHashMap<>();
        scanDataDir(root, entries);

        String name = stringField(manifest, "name", root.getFileName().toString());
        String title = stringField(manifest, "title", name);
        List<TieLibDecl> libs = parseTieLibs(root, manifest);
        Map<String, TdValue> rules = DatapackRules.fromManifest(manifest);
        parseManifestEntries(root, manifest, entries);
        return new Datapack(name, title, entries, libs, rules);
    }

    // ---------- directory scan ----------

    private static void scanDataDir(Path root, Map<String, DatapackEntry> into) {
        Path data = root.resolve("data");
        if (!Files.isDirectory(data)) {
            return; // no data/ tree at all
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(data)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".td"))
                    .sorted()
                    .forEach(files::add);
        } catch (IOException e) {
            throw new UncheckedIOException("datapack scan failed: " + root, e);
        }
        for (Path file : files) {
            Path rel = data.relativize(file);
            if (rel.getNameCount() < 3) {
                throw new IllegalArgumentException("datapack " + root.getFileName() + ": invalid td path: data/" + rel
                        + " (expected data/<namespace>/<kind>/<path>.td)");
            }
            String ns = rel.getName(0).toString();
            EntryKind kind = EntryKind.fromDirectory(rel.getName(1).toString());
            String path = joinPath(rel, 2);
            TdTable payload = parseTd(root, file);
            register(into, new DatapackEntry(kind, ns, path, payload), root);
        }
    }

    /** Joins path segments from {@code fromIndex} on, stripping the trailing ".td". */
    private static String joinPath(Path segments, int fromIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = fromIndex; i < segments.getNameCount(); i++) {
            String seg = segments.getName(i).toString();
            if (i == segments.getNameCount() - 1 && seg.endsWith(".td")) {
                seg = seg.substring(0, seg.length() - ".td".length());
            }
            if (i > fromIndex) {
                sb.append('/');
            }
            sb.append(seg);
        }
        return sb.toString();
    }

    // ---------- pack.td manifest ----------

    private static List<TieLibDecl> parseTieLibs(Path root, TdTable manifest) {
        TdValue tieValue = manifest.get("tie");
        if (!(tieValue instanceof TdTable tie)) {
            return List.of();
        }
        List<TieLibDecl> libs = new ArrayList<>();
        for (TdValue item : tie.elements()) {
            if (!(item instanceof TdTable t)) {
                throw new IllegalArgumentException("datapack " + root.getFileName() + ": pack.td tie entry is not a table");
            }
            String lib = t.get("lib") != null ? t.get("lib").asString() : "";
            String dll = t.get("dll") != null ? t.get("dll").asString() : "";
            if (lib.isBlank() || dll.isBlank()) {
                throw new IllegalArgumentException("datapack " + root.getFileName() + ": pack.td tie entry needs lib and dll");
            }
            libs.add(new TieLibDecl(lib, dll));
        }
        return libs;
    }

    private static void parseManifestEntries(Path root, TdTable manifest, Map<String, DatapackEntry> into) {
        TdValue entriesValue = manifest.get("entries");
        if (!(entriesValue instanceof TdTable entries)) {
            return;
        }
        for (TdValue item : entries.elements()) {
            if (!(item instanceof TdTable t)) {
                throw new IllegalArgumentException("datapack " + root.getFileName() + ": pack.td entry is not a table");
            }
            String kindName = t.get("kind") != null ? t.get("kind").asString() : "";
            String ns = t.get("ns") != null ? t.get("ns").asString() : "";
            String path = t.get("path") != null ? t.get("path").asString() : "";
            if (kindName.isBlank() || ns.isBlank() || path.isBlank()) {
                throw new IllegalArgumentException("datapack " + root.getFileName()
                        + ": pack.td entry needs kind, ns and path");
            }
            EntryKind kind = EntryKind.fromDirectory(kindName);
            String rel = t.get("file") != null ? t.get("file").asString() : null;
            TdTable payload;
            if (rel != null) {
                payload = parseTd(root, root.resolve(rel));
            } else {
                DatapackEntry existing = into.get(ns + ":" + kind.dir() + "/" + path);
                if (existing == null) {
                    throw new IllegalArgumentException("datapack " + root.getFileName()
                            + ": manifest entry " + ns + ":" + kind.dir() + "/" + path
                            + " has no file and no scanned counterpart");
                }
                payload = existing.payload();
            }
            register(into, new DatapackEntry(kind, ns, path, payload), root);
        }
    }

    // ---------- shared ----------

    private static void register(Map<String, DatapackEntry> into, DatapackEntry entry, Path root) {
        String id = entry.id();
        if (into.containsKey(id)) {
            throw new IllegalArgumentException("datapack " + root.getFileName()
                    + ": duplicate entry id " + id);
        }
        into.put(id, entry);
    }

    private static TdTable parseTd(Path root, Path file) {
        String source;
        try {
            source = Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("datapack " + root.getFileName() + ": cannot read " + file, e);
        }
        try {
            return Td.parse(source);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("datapack " + root.getFileName() + ": " + root.relativize(file) + ": " + e.getMessage(), e);
        }
    }

    private static String stringField(TdTable table, String key, String dflt) {
        TdValue v = table.get(key);
        return v == null ? dflt : v.asString();
    }
}