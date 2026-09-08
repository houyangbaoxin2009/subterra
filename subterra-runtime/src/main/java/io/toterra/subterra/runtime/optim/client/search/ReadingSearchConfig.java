// Ported surface from JustEnoughCharacters (github.com/Towdium/JustEnoughCharacters),
// MIT (c) Towdium. The reading-search config model (packs/extensions) is
// Subterra's own language-agnostic design.
package io.toterra.subterra.runtime.optim.client.search;

import io.toterra.subterra.engine.config.TdTable;
import io.toterra.subterra.engine.config.TdValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reading search config, loaded from td (see {@code config/subterra/jec.td})
 * via engine.config. Unknown or invalid fields fall back to defaults so a
 * broken config file can never take the port down.
 *
 * <pre>{@code
 * [
 *   reading_search = [
 *     enabled = true,              // master switch (default true)
 *     packs = [ "zh", "ja" ],      // bundled pack subset; absent/empty = all
 *     extensions = [               // per-pack user dictionaries (appended)
 *       [ id = "ja", lexicon = "config/subterra/ja_extra.lex" ],
 *     ],
 *   ],
 * ]
 * }</pre>
 */
public final class ReadingSearchConfig {

    private static final boolean DEFAULT_ENABLED = true;

    /** A user-supplied extension dictionary attached to a bundled pack id. */
    public record ExtensionSpec(String id, Path lexiconPath) {
    }

    private final boolean enabled;
    private final List<String> packs;
    private final List<ExtensionSpec> extensions;

    private ReadingSearchConfig(boolean enabled, List<String> packs, List<ExtensionSpec> extensions) {
        this.enabled = enabled;
        this.packs = packs;
        this.extensions = extensions;
    }

    public static ReadingSearchConfig defaults() {
        return new ReadingSearchConfig(DEFAULT_ENABLED, null, List.of());
    }

    /**
     * Reads {@code config/subterra/jec.td} under {@code gameDir}; falls back
     * to defaults on absence, malformed input or invalid values.
     */
    public static ReadingSearchConfig load(Path gameDir) {
        Path config = gameDir.resolve("config/subterra/jec.td");
        if (!Files.isRegularFile(config)) {
            return defaults();
        }
        try {
            String text = Files.readString(config, StandardCharsets.UTF_8);
            if (text.isBlank()) {
                return defaults();
            }
            TdTable root = io.toterra.subterra.engine.config.Td.parse(text);
            return fromTd(root, gameDir);
        } catch (IllegalArgumentException | IOException e) {
            ReadingSearch.LOGGER.warn("reading search: {} ignored ({}), using defaults", config, e);
            return defaults();
        }
    }

    static ReadingSearchConfig fromTd(TdTable root, Path gameDir) {
        TdValue rs = root.get("reading_search");
        if (!(rs instanceof TdTable table)) {
            return defaults();
        }
        boolean enabled = enabled(table.get("enabled"));
        List<String> packs = packs(table.get("packs"));
        List<ExtensionSpec> extensions = extensions(table.get("extensions"), gameDir);
        return new ReadingSearchConfig(enabled, packs, extensions);
    }

    private static boolean enabled(TdValue v) {
        return v != null ? v.asBool() : DEFAULT_ENABLED;
    }

    /**
     * Bundled pack subset; null (absent or empty list) means all built-in
     * packs. Unknown ids are tolerated here and simply skipped at load time.
     */
    private static List<String> packs(TdValue v) {
        List<TdValue> items = v != null ? v.asList() : List.of();
        List<String> ids = new ArrayList<>();
        for (TdValue item : items) {
            String id = item.asString();
            if (id != null && !id.isBlank()) {
                ids.add(id);
            }
        }
        return ids.isEmpty() ? null : List.copyOf(ids);
    }

    private static List<ExtensionSpec> extensions(TdValue v, Path gameDir) {
        List<TdValue> items = v != null ? v.asList() : List.of();
        List<ExtensionSpec> specs = new ArrayList<>();
        for (TdValue item : items) {
            if (!(item instanceof TdTable t)) {
                continue;
            }
            TdValue idV = t.get("id");
            TdValue lexV = t.get("lexicon");
            if (idV == null || lexV == null) {
                continue;
            }
            String id = idV.asString();
            String lex = lexV.asString();
            if (id == null || id.isBlank() || lex == null || lex.isBlank()) {
                continue;
            }
            Path p = Path.of(lex);
            if (!p.isAbsolute()) {
                p = gameDir.resolve(p);
            }
            specs.add(new ExtensionSpec(id, p));
        }
        return List.copyOf(specs);
    }

    public boolean enabled() {
        return enabled;
    }

    /** Bundled pack ids to load; null = all built-in packs. */
    public List<String> packs() {
        return packs;
    }

    public List<ExtensionSpec> extensions() {
        return extensions;
    }

    @Override
    public String toString() {
        return "ReadingSearchConfig{enabled=" + enabled + ", packs=" + packs + ", extensions=" + extensions + '}';
    }
}