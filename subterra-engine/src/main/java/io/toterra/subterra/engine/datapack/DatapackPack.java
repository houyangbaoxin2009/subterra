package io.toterra.subterra.engine.datapack;

import io.toterra.subterra.engine.config.Td;
import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

/**
 * p.2.2 datapack package — one-click pack/unpack of a whole td datapack into a
 * single td document (the datapack counterpart of {@code engine.config.
 * ConfigPack}, over the file-tree level). Packing is deterministic (files
 * sorted by relative path) and exact: unpacking the document reproduces the
 * same td files, so re-loading yields the identical registry.
 *
 * <p>Document shape (content is base64 so no escaping hazards, and binary
 * content would survive if ever needed):
 * <pre>{@code
 * type tie<data>
 * datapack = [
 *   version = 1,
 *   files = [
 *     [ path = "pack.td", content = "..." ],
 *     [ path = "data/toterra/lang/en_us.td", content = "..." ],
 *   ],
 * ]
 * }</pre>
 * Plain td files only (tie dlls are binary and stay outside the document; a
 * pack.td tie declaration pointing at a dll path travels fine).
 */
public final class DatapackPack {

    /** Current package format version. */
    public static final long VERSION = 1;

    private static final String MARKER = "datapack";

    private DatapackPack() {
    }

    /** Packs every {@code .td} file of a datapack directory (sorted, exact). */
    public static String export(Path packDir) {
        Path root = packDir.toAbsolutePath().normalize();
        List<Path> files = scanTd(root);
        TdTable.Builder fileList = TdTable.builder();
        for (Path file : files) {
            String rel = root.relativize(file).toString().replace('\\', '/');
            fileList.element(TdTable.builder()
                    .put("path", TdValue.str(rel))
                    .put("content", TdValue.str(encode(readText(file))))
                    .build());
        }
        TdTable pack = TdTable.builder()
                .put("version", TdValue.of(VERSION))
                .put("files", fileList.build())
                .build();
        // Bare-table root (same trick as ConfigPack.export): a top-level
        // `datapack = [...]` would be misread as an optional table name.
        return "type tie<data>\n" + Td.write(TdTable.builder().put(MARKER, pack).build());
    }

    /**
     * Unpacks a packed document into {@code targetDir} (created as needed) and
     * returns it. Path traversal outside the target is rejected.
     */
    public static Path unpack(String source, Path targetDir) {
        TdTable root = Td.parse(source);
        TdValue markerValue = root.get(MARKER);
        if (!(markerValue instanceof TdTable pack)) {
            throw new IllegalArgumentException("not a datapack package (missing 'datapack')");
        }
        long version = pack.get("version") != null ? pack.get("version").asInt() : -1;
        if (version != VERSION) {
            throw new IllegalArgumentException("unsupported datapack package version: " + version);
        }
        TdValue filesValue = pack.get("files");
        if (!(filesValue instanceof TdTable files)) {
            throw new IllegalArgumentException("datapack package missing 'files' table");
        }
        Path target = targetDir.toAbsolutePath().normalize();
        for (TdValue item : files.elements()) {
            if (!(item instanceof TdTable t)) {
                throw new IllegalArgumentException("datapack package file entry is not a table");
            }
            String path = t.get("path") != null ? t.get("path").asString() : "";
            if (path.isBlank()) {
                throw new IllegalArgumentException("datapack package file entry missing path");
            }
            Path file = target.resolve(path).normalize();
            if (!file.startsWith(target)) {
                throw new IllegalArgumentException("datapack package path escapes target: " + path);
            }
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, decode(t.get("content") != null ? t.get("content").asString() : ""));
            } catch (IOException e) {
                throw new UncheckedIOException("datapack package write failed: " + file, e);
            }
        }
        return target;
    }

    // ---------- helpers ----------

    private static List<Path> scanTd(Path root) {
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("datapack directory not found: " + root);
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".td"))
                    .sorted()
                    .forEach(files::add);
        } catch (IOException e) {
            throw new UncheckedIOException("datapack scan failed: " + root, e);
        }
        return files;
    }

    private static String readText(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("datapack read failed: " + file, e);
        }
    }

    private static String encode(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String s) {
        return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
    }
}