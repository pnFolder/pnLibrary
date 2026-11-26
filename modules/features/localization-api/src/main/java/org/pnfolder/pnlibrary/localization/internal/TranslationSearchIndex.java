package org.pnfolder.pnlibrary.localization.internal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.localization.TranslationIndex;
import org.pnfolder.pnlibrary.localization.TranslationMatch;
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout;
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayouts;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntBiFunction;
import java.util.regex.Pattern;

/**
 * Search index over translated names: exact lookup, search and fuzzy matching that also tolerates text typed in a
 * different keyboard layout.
 */
public final class TranslationSearchIndex<T> implements TranslationIndex<T> {
    /** Runs of whitespace collapsed by {@link #normalize}. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final int SEARCH_CACHE_CAPACITY = 64;
    private static final double SUGGEST_FLOOR = 0.5;
    private static final int MAX_CACHED_RANKED = 2000;

    private final KeyboardLayouts layouts;
    private final ToIntBiFunction<String, String> distance;
    /** All indexed translations with their normalized text. */
    private final List<Indexed<T>> entries;
    /** Normalized text to its matches, for {@link #findExact}. */
    private final Map<String, List<TranslationMatch<T>>> exact;
    /** Small LRU of ranked results per query, floor and layouts, so a repeated suggestion does no scoring; guarded by its own monitor. */
    private final Map<RankedKey, List<Ranked<T>>> rankedCache = new LinkedHashMap<RankedKey, List<Ranked<T>>>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<RankedKey, List<Ranked<T>>> eldest) {
            return size() > SEARCH_CACHE_CAPACITY;
        }
    };
    /** Small LRU of substring-search results; guarded by its own monitor. */
    private final Map<String, List<TranslationMatch<T>>> searchCache = new LinkedHashMap<String, List<TranslationMatch<T>>>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<TranslationMatch<T>>> eldest) {
            return size() > SEARCH_CACHE_CAPACITY;
        }
    };

    /** Creates an index over {@code matches} that reads queries against {@code layouts}. */
    public TranslationSearchIndex(@NotNull List<TranslationMatch<T>> matches, @NotNull KeyboardLayouts layouts) {
        this(matches, layouts, TranslationSearchIndex::editDistance);
    }

    TranslationSearchIndex(List<TranslationMatch<T>> matches, KeyboardLayouts layouts,
                           ToIntBiFunction<String, String> distance) {
        this.layouts = layouts;
        this.distance = distance;
        List<Indexed<T>> indexed = new ArrayList<Indexed<T>>(matches.size());
        Map<String, List<TranslationMatch<T>>> grouped = new LinkedHashMap<String, List<TranslationMatch<T>>>();
        for (TranslationMatch<T> match : matches) {
            Indexed<T> entry = new Indexed<T>(match, normalize(match.getTranslation()));
            indexed.add(entry);
            List<TranslationMatch<T>> group = grouped.get(entry.normalized);
            if (group == null) {
                group = new ArrayList<TranslationMatch<T>>();
                grouped.put(entry.normalized, group);
            }
            group.add(match);
        }
        for (Map.Entry<String, List<TranslationMatch<T>>> entry : grouped.entrySet()) {
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
        this.entries = indexed;
        this.exact = grouped;
    }

    /** Builds an index whose results are keyed by the translation key itself. */
    static TranslationSearchIndex<String> keys(Map<String, String> values, KeyboardLayouts layouts) {
        List<TranslationMatch<String>> matches = new ArrayList<TranslationMatch<String>>(values.size());
        for (Map.Entry<String, String> entry : values.entrySet()) {
            matches.add(new TranslationMatch<String>(entry.getKey(), entry.getValue(), entry.getKey()));
        }
        return new TranslationSearchIndex<String>(matches, layouts);
    }

    /** Folds case, width and whitespace and treats {@code ё} as {@code е}, so lookups ignore those differences. */
    static String normalize(String value) {
        String folded = Whitespace.strip(Normalizer.normalize(value, Normalizer.Form.NFKC))
                .toLowerCase(Locale.ROOT).replace('ё', 'е');
        return WHITESPACE.matcher(folded).replaceAll(" ");
    }

    @NotNull
    @Override
    public KeyboardLayouts getLayouts() {
        return layouts;
    }

    @NotNull
    @Override
    public List<TranslationMatch<T>> findExact(@NotNull String text) {
        List<TranslationMatch<T>> found = exact.get(normalize(text));
        return found == null ? Collections.<TranslationMatch<T>>emptyList() : found;
    }

    @NotNull
    @Override
    public List<TranslationMatch<T>> search(@NotNull String text) {
        String query = normalize(text);
        if (query.isEmpty()) {
            return Collections.emptyList();
        }
        synchronized (searchCache) {
            List<TranslationMatch<T>> cached = searchCache.get(query);
            if (cached != null) {
                return cached;
            }
        }
        List<Indexed<T>> found = new ArrayList<Indexed<T>>();
        for (Indexed<T> entry : entries) {
            if (entry.normalized.contains(query)) {
                found.add(entry);
            }
        }
        final String lookup = query;
        Collections.sort(found, new Comparator<Indexed<T>>() {
            @Override
            public int compare(Indexed<T> a, Indexed<T> b) {
                int byRank = Integer.compare(rank(a, lookup), rank(b, lookup));
                return byRank != 0 ? byRank : a.match.getKey().compareTo(b.match.getKey());
            }
        });
        List<TranslationMatch<T>> matches = new ArrayList<TranslationMatch<T>>(found.size());
        for (Indexed<T> entry : found) {
            matches.add(entry.match);
        }
        List<TranslationMatch<T>> result = Collections.unmodifiableList(matches);
        synchronized (searchCache) {
            searchCache.put(query, result);
        }
        return result;
    }

    private static int rank(Indexed<?> entry, String query) {
        if (entry.normalized.equals(query)) {
            return 0;
        }
        return entry.normalized.startsWith(query) ? 1 : 2;
    }

    @NotNull
    @Override
    public List<TranslationMatch<T>> fuzzy(@NotNull String text, double minSimilarity, int limit) {
        requireSimilarity(minSimilarity);
        requireLimit(limit);
        String query = normalize(text);
        if (query.isEmpty()) {
            return Collections.emptyList();
        }
        return matches(ranked(query, minSimilarity, Collections.<KeyboardLayout>emptyList()), limit);
    }

    @NotNull
    @Override
    public List<TranslationMatch<T>> suggest(@NotNull String text, int limit, @NotNull Collection<KeyboardLayout> layouts) {
        requireLimit(limit);
        String query = normalize(text);
        if (query.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, TranslationMatch<T>> distinct = new LinkedHashMap<String, TranslationMatch<T>>();
        for (TranslationMatch<T> match : search(query)) {
            if (!distinct.containsKey(match.getKey())) {
                distinct.put(match.getKey(), match);
            }
        }
        for (Ranked<T> ranked : ranked(query, SUGGEST_FLOOR, layouts)) {
            if (!distinct.containsKey(ranked.match.getKey())) {
                distinct.put(ranked.match.getKey(), ranked.match);
            }
        }
        List<TranslationMatch<T>> result = new ArrayList<TranslationMatch<T>>(distinct.values());
        return result.size() > limit ? new ArrayList<TranslationMatch<T>>(result.subList(0, limit)) : result;
    }

    @Nullable
    @Override
    public TranslationMatch<T> best(@NotNull String text, double minSimilarity, @NotNull Collection<KeyboardLayout> layouts) {
        requireSimilarity(minSimilarity);
        String query = normalize(text);
        if (query.isEmpty()) {
            return null;
        }
        List<Ranked<T>> ranked = ranked(query, minSimilarity, layouts);
        return ranked.isEmpty() ? null : ranked.get(0).match;
    }

    private static void requireSimilarity(double minSimilarity) {
        if (!(minSimilarity >= 0.0 && minSimilarity <= 1.0)) {
            throw new IllegalArgumentException("minSimilarity must be within 0.0..1.0, was " + minSimilarity);
        }
    }

    private static void requireLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive, was " + limit);
        }
    }

    private List<TranslationMatch<T>> matches(List<Ranked<T>> ranked, int limit) {
        List<TranslationMatch<T>> result = new ArrayList<TranslationMatch<T>>();
        for (int i = 0; i < ranked.size() && i < limit; i++) {
            result.add(ranked.get(i).match);
        }
        return result;
    }

    /**
     * Entries scoring at least {@code floor} against {@code query} or any re-reading of it on another of
     * {@code layouts}; most similar first, then those the query matched as typed, then by key.
     */
    private List<Ranked<T>> ranked(String query, double floor, Collection<KeyboardLayout> layouts) {
        RankedKey key = new RankedKey(query, floor, new ArrayList<KeyboardLayout>(layouts));
        synchronized (rankedCache) {
            List<Ranked<T>> cached = rankedCache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        List<Ranked<T>> result = computeRanked(query, floor, layouts);
        if (result.size() <= MAX_CACHED_RANKED) {
            synchronized (rankedCache) {
                rankedCache.put(key, result);
            }
        }
        return result;
    }

    private List<Ranked<T>> computeRanked(String query, double floor, Collection<KeyboardLayout> layouts) {
        Set<String> variants = new LinkedHashSet<String>();
        variants.add(query);
        for (String alternative : KeyboardLayout.alternatives(query, layouts)) {
            variants.add(normalize(alternative));
        }
        List<Probe> probes = new ArrayList<Probe>(variants.size());
        for (String variant : variants) {
            probes.add(new Probe(variant));
        }
        List<Ranked<T>> result = new ArrayList<Ranked<T>>();
        for (Indexed<T> entry : entries) {
            double typedAsIs = 0.0;
            double best = 0.0;
            for (int index = 0; index < probes.size(); index++) {
                double score = 0.0;
                for (Candidate candidate : entry.candidates) {
                    score = Math.max(score, similarity(probes.get(index), candidate, floor));
                }
                if (index == 0) {
                    typedAsIs = score;
                }
                best = Math.max(best, score);
            }
            if (best >= floor) {
                result.add(new Ranked<T>(entry.match, best, typedAsIs >= best));
            }
        }
        Collections.sort(result, new Comparator<Ranked<T>>() {
            @Override
            public int compare(Ranked<T> a, Ranked<T> b) {
                int byScore = Double.compare(b.score, a.score);
                if (byScore != 0) {
                    return byScore;
                }
                int byTyped = Boolean.compare(b.typedAsIs, a.typedAsIs);
                return byTyped != 0 ? byTyped : a.match.getKey().compareTo(b.match.getKey());
            }
        });
        return result;
    }

    /**
     * The better of the edit and letter-triple scores, both between 0.0 and 1.0. A pair that cannot reach
     * {@code floor} may get any value below it; the edit distance is only computed when the triple overlap leaves it
     * a chance to win.
     */
    private double similarity(Probe probe, Candidate candidate, double floor) {
        String a = probe.text;
        String b = candidate.text;
        int longest = Math.max(a.length(), b.length());
        int lengthGap = Math.abs(a.length() - b.length());
        double lengthBound = 1.0 - (double) lengthGap / longest;
        if (a.length() < 3 || b.length() < 3) {
            return lengthBound < floor ? 0.0 : editScore(a, b, longest);
        }
        Set<String> left = probe.trigrams;
        Set<String> right = candidate.trigrams();
        int common = 0;
        for (String trigram : left) {
            if (right.contains(trigram)) {
                common++;
            }
        }
        double triple = 2.0 * common / (left.size() + right.size());
        if (lengthBound < floor) {
            return triple;
        }
        // One edit spoils at most four letter triples and changes at most two letter counts, so what differs bounds the distance from below.
        int unmatched = Math.max(left.size(), right.size()) - common;
        int lowest = Math.max(lengthGap, Math.max((unmatched + 3) / 4,
                (letterDifference(probe.letters, candidate.letters()) + 1) / 2));
        double bound = 1.0 - (double) lowest / longest;
        if (bound <= triple || bound < floor && triple < floor) {
            return triple;
        }
        return Math.max(editScore(a, b, longest), triple);
    }

    /** Sum of the differences between how often each letter occurs in two sorted letter arrays. */
    private static int letterDifference(char[] x, char[] y) {
        int i = 0;
        int j = 0;
        int difference = 0;
        while (i < x.length && j < y.length) {
            if (x[i] == y[j]) {
                i++;
                j++;
            } else if (x[i] < y[j]) {
                i++;
                difference++;
            } else {
                j++;
                difference++;
            }
        }
        return difference + (x.length - i) + (y.length - j);
    }

    private double editScore(String a, String b, int longest) {
        return 1.0 - (double) distance.applyAsInt(a, b) / longest;
    }

    private static Set<String> trigramsOf(String text) {
        if (text.length() < 3) {
            return Collections.emptySet();
        }
        Set<String> trigrams = new LinkedHashSet<String>();
        for (int i = 0; i + 3 <= text.length(); i++) {
            trigrams.add(text.substring(i, i + 3));
        }
        return trigrams;
    }

    private static char[] sortedLetters(String text) {
        char[] letters = text.toCharArray();
        Arrays.sort(letters);
        return letters;
    }

    /** Optimal string alignment distance: insertion, deletion, substitution and swapping two neighbours each cost 1. */
    static int editDistance(String a, String b) {
        int[] older = new int[b.length() + 1];
        int[] previous = new int[b.length() + 1];
        for (int j = 0; j < previous.length; j++) {
            previous[j] = j;
        }
        int[] current = new int[b.length() + 1];
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int best = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1), previous[j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    best = Math.min(best, older[j - 2] + 1);
                }
                current[j] = best;
            }
            int[] recycled = older;
            older = previous;
            previous = current;
            current = recycled;
        }
        return previous[b.length()];
    }

    /** A match with its similarity score; {@code typedAsIs} is true when the query matched without layout conversion. */
    private static final class Ranked<T> {
        final TranslationMatch<T> match;
        final double score;
        final boolean typedAsIs;

        Ranked(TranslationMatch<T> match, double score, boolean typedAsIs) {
            this.match = match;
            this.score = score;
            this.typedAsIs = typedAsIs;
        }
    }

    /** A match with its normalized text; the candidates are the whole text and each word, compared separately. */
    private static final class Indexed<T> {
        final TranslationMatch<T> match;
        final String normalized;
        final List<Candidate> candidates;

        Indexed(TranslationMatch<T> match, String normalized) {
            this.match = match;
            this.normalized = normalized;
            Set<String> texts = new LinkedHashSet<String>();
            texts.add(normalized);
            texts.addAll(Arrays.asList(normalized.split(" ", -1)));
            List<Candidate> list = new ArrayList<Candidate>(texts.size());
            for (String text : texts) {
                list.add(new Candidate(text));
            }
            this.candidates = list;
        }
    }

    /** Text to compare against, with its letter triples computed on first use and then kept. */
    private static final class Candidate {
        final String text;
        private volatile Set<String> trigrams;
        private volatile char[] letters;

        Candidate(String text) {
            this.text = text;
        }

        Set<String> trigrams() {
            Set<String> value = trigrams;
            if (value == null) {
                value = trigramsOf(text);
                trigrams = value;
            }
            return value;
        }

        char[] letters() {
            char[] value = letters;
            if (value == null) {
                value = sortedLetters(text);
                letters = value;
            }
            return value;
        }
    }

    /** A query variant with its letter triples, computed once per search. */
    private static final class Probe {
        final String text;
        final Set<String> trigrams;
        final char[] letters;

        Probe(String text) {
            this.text = text;
            this.trigrams = trigramsOf(text);
            this.letters = sortedLetters(text);
        }
    }

    private static final class RankedKey {
        private final String query;
        private final double floor;
        private final List<KeyboardLayout> layouts;

        RankedKey(String query, double floor, List<KeyboardLayout> layouts) {
            this.query = query;
            this.floor = floor;
            this.layouts = layouts;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof RankedKey)) {
                return false;
            }
            RankedKey that = (RankedKey) other;
            return Double.compare(floor, that.floor) == 0 && query.equals(that.query) && layouts.equals(that.layouts);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * query.hashCode() + Double.hashCode(floor)) + layouts.hashCode();
        }
    }
}
