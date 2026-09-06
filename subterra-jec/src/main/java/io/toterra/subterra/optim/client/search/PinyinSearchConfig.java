// Ported surface from JustEnoughCharacters (github.com/Towdium/JustEnoughCharacters),
// MIT (c) Towdium.
package io.toterra.subterra.optim.client.search;

import io.toterra.subterra.config.TdTable;
import io.toterra.subterra.config.TdValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pinyin search config, loaded from td (see {@code config/subterra/jec.td})
 * via subterra-config. Unknown or invalid fields fall back to defaults so a
 * broken config file can never take the port down.
 *
 * <pre>{@code
 * [
 *   pinyin_search = [
 *     enabled = true,
 *     extension_lexicon = "config/subterra/pinyin_extra.lex",
 *   ],
 * ]
 * }</pre>
 */
public final class PinyinSearchConfig {

    private static final boolean DEFAULT_ENABLED = true;

    private final boolean enabled;
    private final Path extensionPath;

    private PinyinSearchConfig(boolean enabled, Path extensionPath) {
        this.enabled = enabled;
        this.extensionPath = extensionPath;
    }

    public static PinyinSearchConfig defaults() {
        return new PinyinSearchConfig(DEFAULT_ENABLED, null);
    }

    /**
     * Reads {@code config/subterra/jec.td} under {@code gameDir}; falls back
     * to defaults on absence, malformed input or invalid values.
     */
    public static PinyinSearchConfig load(Path gameDir) {
        Path config = gameDir.resolve("config/subterra/jec.td");
        if (!Files.isRegularFile(config)) {
            return defaults();
        }
        try {
            String text = Files.readString(config, StandardCharsets.UTF_8);
            if (text.isBlank()) {
                return defaults();
            }
            TdTable root = io.toterra.subterra.config.Td.parse(text);
            return fromTd(root, gameDir);
        } catch (IllegalArgumentException | IOException e) {
            PinyinSearch.LOGGER.warn("pinyin search: {} ignored ({}), using defaults", config, e);
            return defaults();
        }
    }

    static PinyinSearchConfig fromTd(TdTable root, Path gameDir) {
        TdValue ps = root.get("pinyin_search");
        if (!(ps instanceof TdTable table)) {
            return defaults();
        }
        boolean enabled = enabled(table.get("enabled"));
        Path ext = extensionPath(table.get("extension_lexicon"), gameDir);
        return new PinyinSearchConfig(enabled, ext);
    }

    private static boolean enabled(TdValue v) {
        return v != null ? v.asBool() : DEFAULT_ENABLED;
    }

    private static Path extensionPath(TdValue v, Path gameDir) {
        if (v == null) {
            return null;
        }
        String s = v.asString();
        if (s == null || s.isBlank()) {
            return null;
        }
        Path p = Path.of(s);
        if (!p.isAbsolute()) {
            p = gameDir.resolve(p);
        }
        return p;
    }

    public boolean enabled() {
        return enabled;
    }

    public Path extensionPath() {
        return extensionPath;
    }

    @Override
    public String toString() {
        return "PinyinSearchConfig{enabled=" + enabled + ", extensionPath=" + extensionPath + '}';
    }
}
