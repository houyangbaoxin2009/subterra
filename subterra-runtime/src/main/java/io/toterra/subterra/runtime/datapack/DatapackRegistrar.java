package io.toterra.subterra.runtime.datapack;

import io.toterra.subterra.engine.datapack.Datapack;
import io.toterra.subterra.engine.datapack.DatapackEntry;
import io.toterra.subterra.engine.datapack.DatapackLoader;
import io.toterra.subterra.engine.datapack.EntryKind;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MC-shell datapack registrar (p.2.2 block 2): loads every td pack from the
 * configured datapacks directory through the engine.datapack loader (pure td
 * direct loading — no JSON files, no vanilla pack pipeline), then hands each
 * bundle to the per-kind registration steps (tags / lang / recipes / tie
 * functions). Holds the server-scoped registries and the tie bundle
 * lifecycle; the per-kind wiring lands in the follow-up slices.
 */
public final class DatapackRegistrar implements AutoCloseable {

    public static final String MARKER = "[Subterra datapack]";

    private final MinecraftServer server;
    private final List<Datapack> packs = new ArrayList<>();
    private final Map<EntryKind, List<DatapackEntry>> byKind = new LinkedHashMap<>();
    /** Registered tag index: entry id ({@code ns:tag/path}) → payload values. */
    private final Map<String, List<String>> tagValues = new LinkedHashMap<>();
    /** Registered localization index: key → value (from lang k/v pairs). */
    private final Map<String, String> langMap = new LinkedHashMap<>();

    private DatapackRegistrar(MinecraftServer server) {
        this.server = server;
        for (EntryKind k : EntryKind.values()) {
            byKind.put(k, new ArrayList<>());
        }
    }

    public static DatapackRegistrar forServer(MinecraftServer server) {
        return new DatapackRegistrar(server);
    }

    public MinecraftServer server() {
        return server;
    }

    public Map<EntryKind, List<DatapackEntry>> entriesByKind() {
        return byKind;
    }

    /** Loads every td pack under {@code root} (one subdirectory per pack). */
    public void loadPacks(Path root) {
        if (!Files.isDirectory(root)) {
            DatapackRuntime.LOGGER.info("{} datapacks dir absent: {}", MARKER, root);
            return;
        }
        List<Path> packDirs = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(root)) {
            ds.forEach(p -> {
                if (Files.isDirectory(p)) {
                    packDirs.add(p);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("datapacks dir scan failed: " + root, e);
        }
        packDirs.sort(java.util.Comparator.comparing(Path::toString));
        int loaded = 0;
        for (Path dir : packDirs) {
            try {
                Datapack dp = DatapackLoader.load(dir);
                packs.add(dp);
                loaded++;
                for (DatapackEntry e : dp.entries().values()) {
                    byKind.get(e.kind()).add(e);
                }
            } catch (IllegalArgumentException e) {
                // a broken pack must never take the server down; it is named and skipped
                DatapackRuntime.LOGGER.error("{} skipping broken pack {}: {}", MARKER, dir, e.getMessage());
            }
        }
        logLoadSummary(loaded);
    }

    public void logLoadSummary(int loaded) {
        DatapackRuntime.LOGGER.info("{} loaded {} packs, {} entries (tag={}, lang={}, recipe={}, loot_table={}, "
                        + "worldgen={}, structure={}, function={})",
                MARKER, loaded, byKind.values().stream().mapToInt(List::size).sum(),
                byKind.get(EntryKind.TAG).size(), byKind.get(EntryKind.LANG).size(),
                byKind.get(EntryKind.RECIPE).size(), byKind.get(EntryKind.LOOT_TABLE).size(),
                byKind.get(EntryKind.WORLDGEN).size(), byKind.get(EntryKind.STRUCTURE).size(),
                byKind.get(EntryKind.FUNCTION).size());
    }

    /** Registers the tag and lang indexes and logs deterministic detail markers. */
    public void registerContent() {
        registerTags();
        registerLang();
        for (Map.Entry<String, List<String>> e : tagValues.entrySet()) {
            DatapackRuntime.LOGGER.info("{} tag {} values={}", MARKER, e.getKey(), e.getValue());
        }
        int shown = 0;
        for (Map.Entry<String, String> e : langMap.entrySet()) {
            if (shown < 8) {
                DatapackRuntime.LOGGER.info("{} lang {} = {}", MARKER, e.getKey(), e.getValue());
                shown++;
            }
        }
        if (!langMap.isEmpty()) {
            DatapackRuntime.LOGGER.info("{} lang entries={}", MARKER, langMap.size());
        }
    }

    /** tag index: entry id → payload {@code values}. */
    public Map<String, List<String>> tagValues() {
        return tagValues;
    }

    /** localization index: key → value. */
    public Map<String, String> langMap() {
        return langMap;
    }

    private void registerTags() {
        for (DatapackEntry e : byKind.get(EntryKind.TAG)) {
            List<String> values = stringList(e.payload().get("values"));
            tagValues.put(e.id(), values);
        }
    }

    private void registerLang() {
        for (DatapackEntry e : byKind.get(EntryKind.LANG)) {
            io.toterra.subterra.engine.config.TdTable payload = e.payload();
            for (io.toterra.subterra.engine.config.TdValue item : payload.elements()) {
                if (!(item instanceof io.toterra.subterra.engine.config.TdTable t)) {
                    continue;
                }
                String k = t.get("k") != null ? t.get("k").asString() : "";
                String v = t.get("v") != null ? t.get("v").asString() : "";
                if (!k.isBlank()) {
                    langMap.put(k, v);
                }
            }
        }
    }

    private static List<String> stringList(io.toterra.subterra.engine.config.TdValue v) {
        if (!(v instanceof io.toterra.subterra.engine.config.TdTable t)) {
            return List.of();
        }
        return t.elements().stream().map(io.toterra.subterra.engine.config.TdValue::asString).toList();
    }

    @Override
    public void close() {
        packs.clear();
        byKind.clear();
        tagValues.clear();
        langMap.clear();
    }
}