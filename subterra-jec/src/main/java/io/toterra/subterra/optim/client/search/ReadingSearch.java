// Ported surface from JustEnoughCharacters (github.com/Towdium/JustEnoughCharacters),
// MIT (c) Towdium. Chinese pinyin data derived from mozillazg/pinyin-data (MIT);
// Japanese kana data is hand-authored for Subterra.
package io.toterra.subterra.optim.client.search;

import com.mojang.logging.LogUtils;
import io.toterra.subterra.engine.optim.logic.pronounce.Lexicon;
import io.toterra.subterra.engine.optim.logic.pronounce.PronounceMatcher;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Subterra-internal entry point for the JEC-style reading search port.
 * Not a {@code @Mod}: Subterra's own mod entry registers this via
 * {@link #bootstrap(ModContainer)} in the mod constructor, keeping the
 * feature an internal capability of the Subterra mod jar.
 *
 * <p>Language-agnostic: pronunciation packs (bundled in the jar under
 * {@code assets/subterra/jec/reading/{id}.lex}, plus user extension files)
 * are appended into one shared {@link Lexicon} in declaration order, so
 * Chinese pinyin, Japanese kana romaji and any other reading pack all plug
 * in without touching the core engine. The td config
 * ({@code config/subterra/jec.td}) can disable the feature, select a subset
 * of bundled packs, or attach per-pack extension lexicons; a broken or
 * missing config falls back to defaults (all bundled packs enabled).</p>
 */
public final class ReadingSearch {
    public static final String MOD_ID = "subterra_jec";
    public static final String NAME = "JEC-style reading search";

    public static final Logger LOGGER = LogUtils.getLogger();

    private static final String READING_DIR = "/assets/subterra/jec/reading/";

    /** Bundled pronunciation packs shipped in the jar (import = enabled by default). */
    private record PackedLexicon(String id, String resourcePath) {
    }

    private static final List<PackedLexicon> BUNDLED = List.of(
            new PackedLexicon("zh", READING_DIR + "zh.lex"),
            new PackedLexicon("ja", READING_DIR + "ja.lex"),
            new PackedLexicon("ko", READING_DIR + "ko.lex"),
            new PackedLexicon("gr", READING_DIR + "gr.lex"));

    private static volatile Lexicon lexicon;
    private static volatile PronounceMatcher matcher;
    private static volatile boolean enabled = true;

    private ReadingSearch() {
    }

    /**
     * Preloads the packs so a broken resource surfaces at mod construction
     * time; the lazy accessors remain the source of truth.
     */
    public static void bootstrap(ModContainer container) {
        ReadingSearchConfig config = ReadingSearchConfig.load(FMLPaths.GAMEDIR.get());
        enabled = config.enabled();
        Lexicon acc = Lexicon.empty();
        if (enabled) {
            List<String> loaded = new ArrayList<>();
            for (PackedLexicon pack : BUNDLED) {
                if (config.packs() == null || config.packs().contains(pack.id())) {
                    acc = acc.mergedWith(loadResource(pack.id(), pack.resourcePath()));
                    loaded.add(pack.id());
                }
            }
            for (ReadingSearchConfig.ExtensionSpec ext : config.extensions()) {
                if (ext.id() == null || ext.id().isBlank()) {
                    LOGGER.warn("[{}] skipping extension with blank pack id", MOD_ID);
                    continue;
                }
                if (config.packs() != null && !config.packs().contains(ext.id())) {
                    continue; // pack not selected; extension has nothing to attach to
                }
                Lexicon extra = loadExtensionFile(ext.lexiconPath());
                if (extra != null) {
                    acc = acc.mergedWith(extra);
                    LOGGER.info("[{}] extension lexicon for pack '{}' loaded from {}", MOD_ID, ext.id(), ext.lexiconPath());
                }
            }
            LOGGER.info("[{}] reading packs loaded: {}", MOD_ID, loaded.isEmpty() ? "none" : String.join(", ", loaded));
        }
        lexicon = acc;
        matcher = new PronounceMatcher(acc);
        LOGGER.info("[{}] reading search {}", MOD_ID, enabled ? "enabled" : "disabled");
    }

    public static PronounceMatcher matcher() {
        PronounceMatcher m = matcher;
        if (m == null) {
            synchronized (ReadingSearch.class) {
                m = matcher;
                if (m == null) {
                    Lexicon l = lexicon;
                    if (l == null) {
                        l = Lexicon.empty();
                        for (PackedLexicon pack : BUNDLED) {
                            l = l.mergedWith(loadResource(pack.id(), pack.resourcePath()));
                        }
                        lexicon = l;
                    }
                    m = new PronounceMatcher(l);
                    matcher = m;
                }
            }
        }
        return m;
    }

    public static boolean enabled() {
        return enabled;
    }

    private static Lexicon loadResource(String id, String resourcePath) {
        try (InputStream in = ReadingSearch.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                LOGGER.warn("[{}] bundled pack '{}' ({}) not found; skipped", MOD_ID, id, resourcePath);
                return Lexicon.empty();
            }
            List<String> lines = new ArrayList<>();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    lines.add(line);
                }
            }
            return Lexicon.ofLines(lines);
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warn("[{}] failed to load bundled pack '{}': {}", MOD_ID, id, e);
            return Lexicon.empty();
        }
    }

    private static Lexicon loadExtensionFile(Path path) {
        if (!Files.isRegularFile(path)) {
            LOGGER.warn("[{}] extension lexicon {} not found", MOD_ID, path);
            return null;
        }
        try {
            return Lexicon.ofLines(Files.readAllLines(path, StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warn("[{}] failed to load extension lexicon {}: {}", MOD_ID, path, e);
            return null;
        }
    }
}