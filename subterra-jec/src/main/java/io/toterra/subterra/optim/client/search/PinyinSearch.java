// Ported surface from JustEnoughCharacters (github.com/Towdium/JustEnoughCharacters),
// MIT (c) Towdium. Pinyin data derived from mozillazg/pinyin-data (MIT).
package io.toterra.subterra.optim.client.search;

import com.mojang.logging.LogUtils;
import io.toterra.subterra.optim.logic.pronounce.Lexicon;
import io.toterra.subterra.optim.logic.pronounce.PronounceMatcher;
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
 * Subterra-internal entry point for the JEC-style pinyin search port.
 * Not a {@code @Mod}: Subterra's own mod entry registers this via
 * {@link #bootstrap(ModContainer)} in the mod constructor, keeping the
 * feature an internal capability of the Subterra mod jar.
 *
 * <p>Loads the bundled Chinese pinyin lexicon ({@code assets/subterra/jec/pinyin.lex},
 * PinIn line format {@code <char>: r1, r2}) and exposes a shared
 * {@link PronounceMatcher} for {@link PinyinSuffixArray}. The td config
 * ({@code config/subterra/jec.td}) can disable the feature or point at an
 * extension lexicon; a broken or missing config falls back to defaults.</p>
 */
public final class PinyinSearch {
    public static final String MOD_ID = "subterra_jec";
    public static final String NAME = "JEC-style pinyin search";

    public static final Logger LOGGER = LogUtils.getLogger();

    private static final String BUNDLED_LEXICON = "/assets/subterra/jec/pinyin.lex";

    private static volatile Lexicon lexicon;
    private static volatile PronounceMatcher matcher;
    private static volatile boolean enabled = true;

    private PinyinSearch() {
    }

    /**
     * Preloads the lexicon so a broken resource surfaces at mod construction
     * time; the lazy accessors remain the source of truth.
     */
    public static void bootstrap(ModContainer container) {
        PinyinSearchConfig config = PinyinSearchConfig.load(FMLPaths.GAMEDIR.get());
        enabled = config.enabled();
        Lexicon base = loadBundled();
        if (config.extensionPath() != null) {
            Lexicon ext = loadExtension(config.extensionPath());
            if (ext != null) {
                base = base.withExtension(ext);
                LOGGER.info("[{}] extension lexicon loaded from {}", MOD_ID, config.extensionPath());
            }
        }
        lexicon = base;
        matcher = new PronounceMatcher(base);
        LOGGER.info("[{}] pinyin search {}", MOD_ID, enabled ? "enabled" : "disabled");
    }

    public static PronounceMatcher matcher() {
        PronounceMatcher m = matcher;
        if (m == null) {
            synchronized (PinyinSearch.class) {
                m = matcher;
                if (m == null) {
                    Lexicon l = lexicon;
                    if (l == null) {
                        l = loadBundled();
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

    private static Lexicon loadBundled() {
        try (InputStream in = PinyinSearch.class.getResourceAsStream(BUNDLED_LEXICON)) {
            if (in == null) {
                LOGGER.warn("[{}] bundled lexicon {} not found; pinyin search will match literal text only", MOD_ID, BUNDLED_LEXICON);
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
            LOGGER.warn("[{}] failed to load bundled lexicon: {}", MOD_ID, e);
            return Lexicon.empty();
        }
    }

    private static Lexicon loadExtension(Path path) {
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
