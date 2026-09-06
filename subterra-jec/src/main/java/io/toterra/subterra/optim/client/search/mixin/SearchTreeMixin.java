package io.toterra.subterra.optim.client.search.mixin;

import io.toterra.subterra.optim.client.search.PinyinSearch;
import io.toterra.subterra.optim.client.search.PinyinSuffixArray;
import net.minecraft.client.searchtree.FullTextSearchTree;
import net.minecraft.client.searchtree.SearchTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Replaces the plain-text search tree inside {@link FullTextSearchTree} with a
 * pinyin-aware {@link PinyinSuffixArray}.
 *
 * <p>In Minecraft 1.21.1 {@link SearchTree} is an interface whose static
 * {@code plainText} factory builds a vanilla {@code SuffixArray}. We cannot
 * {@code @Mixin} an interface directly, so we redirect the single
 * {@code SearchTree.plainText} call inside {@code FullTextSearchTree}'s
 * constructor.</p>
 */
@Mixin(FullTextSearchTree.class)
public abstract class SearchTreeMixin {
    @Redirect(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/searchtree/SearchTree;plainText(Ljava/util/List;Ljava/util/function/Function;)Lnet/minecraft/client/searchtree/SearchTree;")
    )
    private <T> SearchTree<T> subterra_jec$usePinyinSuffixArray(
            List<T> entries, Function<T, Stream<String>> keyExtractor) {
        if (PinyinSearch.enabled()) {
            PinyinSuffixArray<T> array = new PinyinSuffixArray<>();
            for (T entry : entries) {
                keyExtractor.apply(entry).forEach(text -> array.add(entry, text));
            }
            // SearchTree is a functional interface; expose the pinyin-aware
            // suffix array's search method directly.
            return array::search;
        }
        return SearchTree.plainText(entries, keyExtractor);
    }
}
