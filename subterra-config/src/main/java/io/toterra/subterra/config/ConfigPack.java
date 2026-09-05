package io.toterra.subterra.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Configuration package: one-click export/import of a set of td configuration
 * files as a single td document (architecture subterra-config contract).
 * <p>
 * Package format (file names live as string values, never as bare td keys,
 * since td keys are identifiers):
 * <pre>{@code
 * type tie<data>
 * package = [
 *   version = 1,
 *   files = [
 *     [ name = "log.td", config = [ ...table content... ] ],
 *     [ name = "worldgen.td", config = [ ...table content... ] ],
 *   ],
 * ]
 * }</pre>
 * Round-trips are exact: {@link #parse} restores the exact same tables that
 * {@link #export} packed. Pure JDK; deterministic (files sorted by name).
 */
public final class ConfigPack {

    /** Current package format version. */
    public static final long VERSION = 1;

    private ConfigPack() {
    }

    /** Packs named td tables into a single td document (files sorted by name). */
    public static String export(Map<String, TdTable> files) {
        TdTable.Builder fileList = TdTable.builder();
        Map<String, TdTable> ordered = new TreeMap<>(files);
        for (Map.Entry<String, TdTable> e : ordered.entrySet()) {
            fileList.element(TdTable.builder()
                    .put("name", TdValue.str(e.getKey()))
                    .put("config", e.getValue())
                    .build());
        }
        TdTable pack = TdTable.builder()
                .put("version", TdValue.of(VERSION))
                .put("files", fileList.build())
                .build();
        // Bare-table root: a top-level `package = [...]` would be misread as an
        // optional table name by parse's name stripping.
        return "type tie<data>\n" + Td.write(TdTable.builder().put("package", pack).build());
    }

    /**
     * Unpacks a configuration package document back into named tables.
     *
     * @throws IllegalArgumentException on malformed or non-package input
     */
    public static Map<String, TdTable> parse(String source) {
        TdTable root = Td.parse(source);
        TdValue packValue = root.get("package");
        if (!(packValue instanceof TdTable pack)) {
            throw new IllegalArgumentException("not a configuration package (missing 'package')");
        }
        long version = pack.get("version") != null ? pack.get("version").asInt() : -1;
        if (version != VERSION) {
            throw new IllegalArgumentException("unsupported package version: " + version);
        }
        TdValue filesValue = pack.get("files");
        if (!(filesValue instanceof TdTable files)) {
            throw new IllegalArgumentException("package missing 'files' table");
        }
        Map<String, TdTable> out = new LinkedHashMap<>();
        for (TdValue entry : files.elements()) {
            if (!(entry instanceof TdTable item)) {
                throw new IllegalArgumentException("package file entry is not a table");
            }
            String name = item.get("name") != null ? item.get("name").asString() : "";
            if (name.isBlank()) {
                throw new IllegalArgumentException("package file entry missing name");
            }
            TdValue config = item.get("config");
            if (!(config instanceof TdTable table)) {
                throw new IllegalArgumentException("package file entry missing config table: " + name);
            }
            out.put(name, table);
        }
        return out;
    }
}