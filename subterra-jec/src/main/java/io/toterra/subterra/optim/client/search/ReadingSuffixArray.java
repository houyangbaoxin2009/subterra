// Ported surface from JustEnoughCharacters (github.com/Towdium/JustEnoughCharacters),
// MIT (c) Towdium. The reading matching engine is Subterra's own
// (io.toterra.subterra.engine.optim.logic.pronounce); this class mirrors JEC's
// FakeArray approach: extend SuffixArray and replace its add/generate/search
// with a reading-aware linear scan.
package io.toterra.subterra.optim.client.search;

import io.toterra.subterra.engine.optim.logic.pronounce.PronounceMatcher;
import net.minecraft.client.searchtree.SuffixArray;

import java.util.ArrayList;
import java.util.List;

/**
 * A drop-in replacement for {@link SuffixArray} that matches search queries
 * against pronunciation readings (pinyin, kana romaji, …) as well as the
 * literal text.
 *
 * <p>Vanilla {@code SearchTree.plainText} builds a {@code SuffixArray} from
 * the item names; JEC replaces that construction with a reading-aware tree.
 * We do the same via a mixin redirect on {@code SearchTree.plainText}, so
 * every creative-inventory / name search gains reading support for free.</p>
 *
 * <p>The scan is linear in the number of entries. For the creative inventory
 * (a few thousand stacks) this is well within a frame budget; the engine
 * keeps the per-character match recursion bounded by the query length.</p>
 */
public final class ReadingSuffixArray<T> extends SuffixArray<T> {

    private final List<Entry<T>> entries = new ArrayList<>();

    @Override
    public void add(T value, String key) {
        // Key is already lowercased by SearchTree.plainText before add().
        entries.add(new Entry<>(value, key));
    }

    @Override
    public void generate() {
        // No suffix array to build: entries are stored directly and scanned
        // at search time. (Vanilla SuffixArray.generate() sorts the suffix
        // list for binary search; we don't need it.)
    }

    @Override
    public List<T> search(String query) {
        if (query == null || query.isEmpty()) {
            List<T> all = new ArrayList<>(entries.size());
            for (Entry<T> e : entries) {
                all.add(e.value);
            }
            return all;
        }
        PronounceMatcher matcher = ReadingSearch.matcher();
        List<T> result = new ArrayList<>();
        for (Entry<T> e : entries) {
            if (matcher.contains(e.key, query)) {
                result.add(e.value);
            }
        }
        return result;
    }

    private record Entry<T>(T value, String key) {
    }
}