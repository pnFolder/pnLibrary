package org.pnfolder.pnlibrary.localization;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.localization.internal.TranslationSearchIndex;
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout;
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayouts;

import java.util.Collection;
import java.util.List;

/** Deterministic exact and fuzzy reverse lookup over one translation table. */
public interface TranslationIndex<T> {
    /** The layouts that {@code suggest} and {@code best} read queries against when a call passes none. */
    @NotNull
    default KeyboardLayouts getLayouts() {
        return KeyboardLayouts.builtIn;
    }

    /** Returns every entry whose localized text exactly equals {@code text}. */
    @NotNull
    List<TranslationMatch<T>> findExact(@NotNull String text);

    /** Returns deterministic entries whose localized text matches {@code text}. */
    @NotNull
    List<TranslationMatch<T>> search(@NotNull String text);

    /**
     * Returns entries whose localized text, or any single word of it, resembles {@code text} despite typos, best
     * first and at most {@code limit} of them. {@code minSimilarity} runs from 0.0 to 1.0, where 1.0 is an identical
     * word. Similarity is the better of an edit score (a missing, extra, wrong or swapped neighbouring letter is one
     * error) and a score over shared letter triples, which still ranks long words with scrambled letters.
     */
    @NotNull
    List<TranslationMatch<T>> fuzzy(@NotNull String text, double minSimilarity, int limit);

    /** Same as the three-argument form with a similarity of 0.7 and at most 5 results. */
    @NotNull
    default List<TranslationMatch<T>> fuzzy(@NotNull String text) {
        return fuzzy(text, 0.7, 5);
    }

    /**
     * "Did you mean" candidates for {@code text}, at most {@code limit}. Entries containing the text come first, in
     * {@link #search} order, followed by fuzzy matches of at least 0.5 similarity, best first and then by key. The
     * text is also read as if typed on another of {@code layouts}; a match of the text as typed wins a tie. An empty
     * collection turns this off.
     */
    @NotNull
    List<TranslationMatch<T>> suggest(@NotNull String text, int limit, @NotNull Collection<KeyboardLayout> layouts);

    /** Same as the three-argument form with this index's layouts. */
    @NotNull
    default List<TranslationMatch<T>> suggest(@NotNull String text, int limit) {
        return suggest(text, limit, getLayouts().all());
    }

    /** Same as the two-argument form with at most 5 results. */
    @NotNull
    default List<TranslationMatch<T>> suggest(@NotNull String text) {
        return suggest(text, 5);
    }

    /**
     * Returns the single most similar entry, or {@code null} when even that is below {@code minSimilarity}. Entries
     * that tie on similarity are not ambiguous: the one with the lowest key wins. The layouts work as in
     * {@link #suggest(String, int, Collection)}.
     */
    @Nullable
    TranslationMatch<T> best(@NotNull String text, double minSimilarity, @NotNull Collection<KeyboardLayout> layouts);

    /** Same as the three-argument form with this index's layouts. */
    @Nullable
    default TranslationMatch<T> best(@NotNull String text, double minSimilarity) {
        return best(text, minSimilarity, getLayouts().all());
    }

    /** Same as the two-argument form with a similarity of 0.7. */
    @Nullable
    default TranslationMatch<T> best(@NotNull String text) {
        return best(text, 0.7);
    }

    /** Builds a reverse-search index over the built-in keyboard layouts; see {@link #of(List, KeyboardLayouts)}. */
    @NotNull
    static <T> TranslationIndex<T> of(@NotNull List<TranslationMatch<T>> matches) {
        return of(matches, KeyboardLayouts.builtIn);
    }

    /**
     * Builds a reverse-search index from pre-resolved {@code matches}; used by platform modules to expose their own
     * value types. Pass the table's {@link LocaleTranslations#getLayouts()} so that registered layouts apply to the
     * index.
     */
    @NotNull
    static <T> TranslationIndex<T> of(@NotNull List<TranslationMatch<T>> matches, @NotNull KeyboardLayouts layouts) {
        return new TranslationSearchIndex<>(matches, layouts);
    }
}
