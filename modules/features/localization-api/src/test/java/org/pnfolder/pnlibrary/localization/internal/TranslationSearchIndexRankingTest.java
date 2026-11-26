package org.pnfolder.pnlibrary.localization.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.localization.TranslationMatch;
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout;
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayouts;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationSearchIndexRankingTest {
    private final List<String> words = Arrays.asList(
            "алмазный", "меч", "железный", "кирка", "деревянный", "топор", "золотой", "шлем", "блок", "камня", "руда", "дверь",
            "стекло", "панель", "ступени", "плита", "заготовка", "зелье", "свиток", "шерсть", "diamond", "sword", "iron", "pickaxe");
    private final List<TranslationMatch<String>> matches = matches();
    private final List<KeyboardLayout> layouts = KeyboardLayouts.builtIn.all();

    private List<TranslationMatch<String>> matches() {
        Random random = new Random(3);
        List<TranslationMatch<String>> result = new ArrayList<TranslationMatch<String>>();
        for (int index = 0; index < 1500; index++) {
            int count = 1 + random.nextInt(3);
            StringBuilder text = new StringBuilder();
            for (int word = 1; word <= count; word++) {
                if (word > 1) {
                    text.append(' ');
                }
                text.append(words.get(random.nextInt(words.size())));
            }
            result.add(new TranslationMatch<String>("item.k" + index, text.toString(), "item.k" + index));
        }
        return result;
    }

    /** Queries typed exactly, with a typo, cut short, and in the wrong layout. */
    private List<String> queries() {
        Random random = new Random(11);
        List<String> result = new ArrayList<String>();
        for (int n = 0; n < 100; n++) {
            String source = matches.get(random.nextInt(matches.size())).getTranslation();
            switch (n % 4) {
                case 0:
                    result.add(source);
                    break;
                case 1:
                    result.add(source.substring(0, 1) + "ы" + source.substring(2));
                    break;
                case 2:
                    result.add(source.substring(0, Math.min(3 + random.nextInt(5), source.length())));
                    break;
                default:
                    List<String> alternatives = KeyboardLayout.alternatives(source, layouts);
                    result.add(alternatives.isEmpty() ? source : alternatives.get(0));
                    break;
            }
        }
        return result;
    }

    private static List<String> keys(List<TranslationMatch<String>> matches) {
        List<String> keys = new ArrayList<String>();
        for (TranslationMatch<String> match : matches) {
            keys.add(match.getKey());
        }
        return keys;
    }

    private static List<String> keysOf(TranslationMatch<String> match) {
        return match == null ? Collections.<String>singletonList(null) : Collections.singletonList(match.getKey());
    }

    @Test
    @DisplayName("suggest, fuzzy and best match the previous algorithm on 100 queries")
    void suggestFuzzyAndBestMatchThePreviousAlgorithmOn100Queries() {
        TranslationSearchIndex<String> index = new TranslationSearchIndex<String>(matches, KeyboardLayouts.builtIn);
        ReferenceRanking reference = new ReferenceRanking(matches);
        for (String text : queries()) {
            String query = TranslationSearchIndex.normalize(text);
            List<Collection<KeyboardLayout>> choices = new ArrayList<Collection<KeyboardLayout>>();
            choices.add(Collections.<KeyboardLayout>emptyList());
            choices.add(layouts);
            for (Collection<KeyboardLayout> chosen : choices) {
                assertEquals(keys(reference.suggest(index, query, 10, chosen)), keys(index.suggest(text, 10, chosen)), text);
                assertEquals(keysOf(reference.best(query, 0.7, chosen)), keysOf(index.best(text, 0.7, chosen)), text);
            }
            List<TranslationMatch<String>> ranked = reference.ranked(query, 0.6, Collections.<KeyboardLayout>emptyList());
            assertEquals(keys(ranked.subList(0, Math.min(5, ranked.size()))), keys(index.fuzzy(text, 0.6, 5)), text);
        }
    }

    private static long allocated() {
        return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean())
                .getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    @Test
    @DisplayName("scoring computes far fewer distances and allocates less than the previous algorithm")
    void scoringComputesFarFewerDistancesAndAllocatesLessThanThePreviousAlgorithm() {
        final AtomicInteger counted = new AtomicInteger();
        TranslationSearchIndex<String> index = new TranslationSearchIndex<String>(matches, KeyboardLayouts.builtIn,
                (a, b) -> {
                    counted.incrementAndGet();
                    return TranslationSearchIndex.editDistance(a, b);
                });
        ReferenceRanking reference = new ReferenceRanking(matches);
        Set<String> distinct = new LinkedHashSet<String>();
        for (String query : queries()) {
            distinct.add(TranslationSearchIndex.normalize(query));
        }
        List<String> queries = new ArrayList<String>(distinct);
        for (String query : queries) {
            index.suggest(query, 10, layouts); // warm up classes, not the per-query results
        }
        TranslationSearchIndex<String> fresh = new TranslationSearchIndex<String>(matches, KeyboardLayouts.builtIn,
                (a, b) -> {
                    counted.incrementAndGet();
                    return TranslationSearchIndex.editDistance(a, b);
                });
        counted.set(0);

        long newStart = allocated();
        for (String query : queries) {
            fresh.suggest(query, 10, layouts);
        }
        long newBytes = allocated() - newStart;
        long oldStart = allocated();
        for (String query : queries) {
            reference.suggest(fresh, query, 10, layouts);
        }
        long oldBytes = allocated() - oldStart;

        assertTrue(counted.get() * 5 <= reference.distances, "distances new=" + counted.get() + " old=" + reference.distances);
        assertTrue(newBytes * 2 <= oldBytes, "allocated new=" + newBytes + " old=" + oldBytes);
    }

    @Test
    @DisplayName("a repeated suggestion is answered from the cache without scoring")
    void aRepeatedSuggestionIsAnsweredFromTheCacheWithoutScoring() {
        final AtomicInteger counted = new AtomicInteger();
        TranslationSearchIndex<String> index = new TranslationSearchIndex<String>(matches, KeyboardLayouts.builtIn,
                (a, b) -> {
                    counted.incrementAndGet();
                    return TranslationSearchIndex.editDistance(a, b);
                });
        List<TranslationMatch<String>> first = index.suggest("алмазн", 10, layouts);
        int after = counted.get();
        assertEquals(first, index.suggest("алмазн", 10, layouts));
        assertEquals(after, counted.get());
    }

    /** Previous algorithm kept verbatim to compare against. */
    static final class ReferenceRanking {
        private final List<TranslationMatch<String>> originals = new ArrayList<TranslationMatch<String>>();
        private final List<String> normalized = new ArrayList<String>();
        int distances = 0;

        ReferenceRanking(List<TranslationMatch<String>> matches) {
            for (TranslationMatch<String> match : matches) {
                originals.add(match);
                normalized.add(TranslationSearchIndex.normalize(match.getTranslation()));
            }
        }

        List<TranslationMatch<String>> suggest(TranslationSearchIndex<String> index, String query, int limit,
                                               Collection<KeyboardLayout> layouts) {
            Map<String, TranslationMatch<String>> distinct = new LinkedHashMap<String, TranslationMatch<String>>();
            List<TranslationMatch<String>> all = new ArrayList<TranslationMatch<String>>(index.search(query));
            all.addAll(ranked(query, 0.5, layouts));
            for (TranslationMatch<String> match : all) {
                if (!distinct.containsKey(match.getKey())) {
                    distinct.put(match.getKey(), match);
                }
            }
            List<TranslationMatch<String>> result = new ArrayList<TranslationMatch<String>>(distinct.values());
            return result.size() > limit ? new ArrayList<TranslationMatch<String>>(result.subList(0, limit)) : result;
        }

        TranslationMatch<String> best(String text, double floor, Collection<KeyboardLayout> layouts) {
            List<TranslationMatch<String>> ranked = ranked(TranslationSearchIndex.normalize(text), floor, layouts);
            return ranked.isEmpty() ? null : ranked.get(0);
        }

        List<TranslationMatch<String>> ranked(String query, double floor, Collection<KeyboardLayout> layouts) {
            Set<String> variants = new LinkedHashSet<String>();
            variants.add(query);
            for (String alternative : KeyboardLayout.alternatives(query, layouts)) {
                variants.add(TranslationSearchIndex.normalize(alternative));
            }
            List<Scored> scored = new ArrayList<Scored>();
            for (int i = 0; i < originals.size(); i++) {
                String text = normalized.get(i);
                Set<String> candidates = new LinkedHashSet<String>();
                candidates.add(text);
                candidates.addAll(Arrays.asList(text.split(" ", -1)));
                List<Double> scores = new ArrayList<Double>();
                for (String variant : variants) {
                    double top = Double.NEGATIVE_INFINITY;
                    for (String candidate : candidates) {
                        top = Math.max(top, similarity(variant, candidate, floor));
                    }
                    scores.add(top);
                }
                double score = Collections.max(scores);
                if (score >= floor) {
                    scored.add(new Scored(originals.get(i), score, scores.get(0) >= score));
                }
            }
            Collections.sort(scored, new Comparator<Scored>() {
                @Override
                public int compare(Scored a, Scored b) {
                    int byScore = Double.compare(b.score, a.score);
                    if (byScore != 0) {
                        return byScore;
                    }
                    int byTyped = Boolean.compare(b.typed, a.typed);
                    return byTyped != 0 ? byTyped : a.match.getKey().compareTo(b.match.getKey());
                }
            });
            List<TranslationMatch<String>> result = new ArrayList<TranslationMatch<String>>();
            for (Scored item : scored) {
                result.add(item.match);
            }
            return result;
        }

        private double similarity(String a, String b, double floor) {
            int longest = Math.max(a.length(), b.length());
            double edit = 1.0 - (double) Math.abs(a.length() - b.length()) / longest < floor
                    ? 0.0 : 1.0 - (double) distance(a, b) / longest;
            if (a.length() < 3 || b.length() < 3) {
                return edit;
            }
            Set<String> left = windowed(a);
            Set<String> right = windowed(b);
            int common = 0;
            for (String item : left) {
                if (right.contains(item)) {
                    common++;
                }
            }
            return Math.max(edit, 2.0 * common / (left.size() + right.size()));
        }

        private static Set<String> windowed(String text) {
            Set<String> result = new LinkedHashSet<String>();
            for (int i = 0; i + 3 <= text.length(); i++) {
                result.add(text.substring(i, i + 3));
            }
            return result;
        }

        private int distance(String a, String b) {
            distances++;
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

        private static final class Scored {
            final TranslationMatch<String> match;
            final double score;
            final boolean typed;

            Scored(TranslationMatch<String> match, double score, boolean typed) {
                this.match = match;
                this.score = score;
                this.typed = typed;
            }
        }
    }
}
