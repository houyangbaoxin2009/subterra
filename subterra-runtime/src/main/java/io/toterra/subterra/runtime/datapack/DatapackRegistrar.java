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

    @Override
    public void close() {
        packs.clear();
        byKind.clear();
    }
}