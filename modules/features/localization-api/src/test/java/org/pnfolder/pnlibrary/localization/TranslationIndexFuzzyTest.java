package org.pnfolder.pnlibrary.localization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationIndexFuzzyTest {
    /** Builds an index from alternating key and text arguments. */
    private static TranslationIndex<String> index(String... keysAndTexts) {
        List<TranslationMatch<String>> matches = new ArrayList<TranslationMatch<String>>();
        for (int i = 0; i < keysAndTexts.length; i += 2) {
            matches.add(new TranslationMatch<String>(keysAndTexts[i], keysAndTexts[i + 1], keysAndTexts[i]));
        }
        return TranslationIndex.of(matches);
    }

    private static List<String> keys(List<TranslationMatch<String>> matches) {
        List<String> keys = new ArrayList<String>();
        for (TranslationMatch<String> match : matches) {
            keys.add(match.getKey());
        }
        return keys;
    }

    private static List<String> keysFor(TranslationIndex<String> index, String text) {
        return keysFor(index, text, 0.7, 5);
    }

    private static List<String> keysFor(TranslationIndex<String> index, String text, double minSimilarity) {
        return keysFor(index, text, minSimilarity, 5);
    }

    private static List<String> keysFor(TranslationIndex<String> index, String text, double minSimilarity, int limit) {
        return keys(index.fuzzy(text, minSimilarity, limit));
    }

    private static String bestKey(TranslationIndex<String> index, String text) {
        TranslationMatch<String> best = index.best(text);
        return best == null ? null : best.getKey();
    }

    private static List<String> list(String... values) {
        return Arrays.asList(values);
    }

    @Test
    @DisplayName("a wrong letter is tolerated")
    void aWrongLetterIsTolerated() {
        assertEquals(list("item.apple"), keys(index("item.apple", "Яблоко").fuzzy("яблако")));
    }

    @Test
    @DisplayName("swapped neighbouring letters count as one error")
    void swappedNeighbouringLettersCountAsOneError() {
        assertEquals(list("item.apple"), keysFor(index("item.apple", "Яблоко"), "яблкоо"));
    }

    @Test
    @DisplayName("two errors in a six letter word need a lower threshold")
    void twoErrorsInASixLetterWordNeedALowerThreshold() {
        TranslationIndex<String> apples = index("item.apple", "Яблоко");
        assertTrue(keysFor(apples, "ябока", 0.7).isEmpty());
        assertEquals(list("item.apple"), keysFor(apples, "ябока", 0.6));
    }

    @Test
    @DisplayName("a single word inside a longer name is matched")
    void aSingleWordInsideALongerNameIsMatched() {
        assertEquals(list("item.golden_apple"), keysFor(index("item.golden_apple", "Золотое яблоко"), "яблако"));
    }

    @Test
    @DisplayName("closer matches come first even when their key sorts later")
    void closerMatchesComeFirstEvenWhenTheirKeySortsLater() {
        List<String> found = keysFor(index("a.tree", "Яблоня", "z.apple", "Яблоко"), "яблоко", 0.6);
        assertEquals(list("z.apple", "a.tree"), found);
    }

    @Test
    @DisplayName("equally similar entries are ordered by key")
    void equallySimilarEntriesAreOrderedByKey() {
        assertEquals(list("a.x", "b.x"), keysFor(index("b.x", "Яблоко", "a.x", "Яблоко"), "яблоко"));
    }

    @Test
    @DisplayName("the limit caps the result")
    void theLimitCapsTheResult() {
        TranslationIndex<String> apples = index("a", "Яблоко", "b", "Яблоко", "c", "Яблоко");
        assertEquals(list("a", "b"), keysFor(apples, "яблоко", 0.7, 2));
    }

    @Test
    @DisplayName("blank queries find nothing")
    void blankQueriesFindNothing() {
        assertTrue(index("item.apple", "Яблоко").fuzzy("   ").isEmpty());
    }

    @Test
    @DisplayName("case and yo are normalized")
    void caseAndYoAreNormalized() {
        assertEquals(list("block.spruce"), keysFor(index("block.spruce", "Ёлка"), "ЕЛКА"));
        assertEquals(list("item.apple"), keysFor(index("item.apple", "Яблоко"), "ЯБЛАКО"));
    }

    @Test
    @DisplayName("one error in a three letter word is too many at the default threshold")
    void oneErrorInAThreeLetterWordIsTooManyAtTheDefaultThreshold() {
        TranslationIndex<String> bows = index("item.bow", "Лук");
        assertEquals(list("item.bow"), keysFor(bows, "лук"));
        assertTrue(bows.fuzzy("люк").isEmpty());
    }

    @Test
    @DisplayName("scrambled long words are caught by letter triples")
    void scrambledLongWordsAreCaughtByLetterTriples() {
        assertEquals(list("block.sunflower"), keysFor(index("block.sunflower", "Подсолнух"), "солнухпод"));
    }

    @Test
    @DisplayName("out of range arguments are rejected")
    void outOfRangeArgumentsAreRejected() {
        TranslationIndex<String> apples = index("item.apple", "Яблоко");
        assertThrows(IllegalArgumentException.class, () -> apples.fuzzy("яблоко", -0.1, 5));
        assertThrows(IllegalArgumentException.class, () -> apples.fuzzy("яблоко", 1.1, 5));
        assertThrows(IllegalArgumentException.class, () -> apples.fuzzy("яблоко", 0.7, 0));
        assertThrows(IllegalArgumentException.class, () -> apples.fuzzy("яблоко", 0.7, -1));
        assertThrows(IllegalArgumentException.class, () -> apples.suggest("яблоко", 0));
        assertThrows(IllegalArgumentException.class, () -> apples.best("яблоко", 1.5));
    }

    private final TranslationIndex<String> food =
            index("item.apple", "Яблоко", "item.melon", "Арбуз", "item.bow", "Лук");

    private static String firstKey(List<TranslationMatch<String>> matches) {
        return matches.isEmpty() ? null : matches.get(0).getKey();
    }

    @Test
    @DisplayName("suggest puts the intended entry on top for damaged words")
    void suggestPutsTheIntendedEntryOnTopForDamagedWords() {
        for (String typed : list("яблако", "ябока", "ябло", "яблоко")) {
            assertEquals("item.apple", firstKey(food.suggest(typed)), typed);
        }
    }

    @Test
    @DisplayName("suggest never throws on short input and finds containing entries")
    void suggestNeverThrowsOnShortInputAndFindsContainingEntries() {
        for (String typed : list("я", "к", "лю", "ук", "zz", "1")) {
            food.suggest(typed);
        }
        assertEquals(list("item.bow"), keys(food.suggest("ук")));
        assertTrue(!food.suggest("к").isEmpty());
    }

    @Test
    @DisplayName("suggest is deterministic, deduplicated and capped")
    void suggestIsDeterministicDeduplicatedAndCapped() {
        TranslationIndex<String> apples = index("b.k", "Яблоко", "a.k", "Яблоко", "c.k", "Золотое яблоко");
        assertEquals(list("a.k", "b.k", "c.k"), keys(apples.suggest("яблоко")));
        assertEquals(apples.suggest("яблако"), apples.suggest("яблако"));
        assertEquals(1, apples.suggest("яблоко", 1).size());
    }

    @Test
    @DisplayName("best returns the intended entry or null")
    void bestReturnsTheIntendedEntryOrNull() {
        assertEquals("item.apple", bestKey(food, "яблако"));
        assertNull(food.best("zzzzzz"));
        assertNull(food.best("ябока"));
    }

    @Test
    @DisplayName("best breaks a tie by the lowest key")
    void bestBreaksATieByTheLowestKey() {
        assertEquals("a.x", bestKey(index("b.x", "Яблоко", "a.x", "Яблоко"), "яблоко"));
    }

    @Test
    @DisplayName("empty input suggests and matches nothing")
    void emptyInputSuggestsAndMatchesNothing() {
        assertTrue(food.suggest("").isEmpty());
        assertTrue(food.suggest("  ").isEmpty());
        assertNull(food.best(""));
    }

    @Test
    @DisplayName("a query typed on the wrong keyboard layout is understood")
    void aQueryTypedOnTheWrongKeyboardLayoutIsUnderstood() {
        assertEquals("item.apple", bestKey(food, "z,kjrj"));
        assertEquals("item.apple", firstKey(food.suggest("z,kjrj")));
        assertEquals("item.apple", bestKey(index("item.apple", "Apple"), "фззду"));
        assertEquals("greeting", bestKey(index("greeting", "Привет"), "ghbdtn"));
    }

    @Test
    @DisplayName("an empty layout set turns the fix off and fuzzy never uses layouts")
    void anEmptyLayoutSetTurnsTheFixOffAndFuzzyNeverUsesLayouts() {
        assertNull(food.best("z,kjrj", 0.7, Collections.<KeyboardLayout>emptyList()));
        assertTrue(food.suggest("z,kjrj", 5, Collections.<KeyboardLayout>emptyList()).isEmpty());
        assertTrue(food.fuzzy("z,kjrj").isEmpty());
        assertEquals("item.apple", food.best("z,kjrj", 0.7, KeyboardLayout.builtIn).getKey());
    }

    @Test
    @DisplayName("a custom layout passed to a call is used and the defaults ignore it")
    void aCustomLayoutPassedToACallIsUsedAndTheDefaultsIgnoreIt() {
        KeyboardLayout dvorak = KeyboardLayout.of("dvorak", "',.pyfgcrl/=\\aoeuidhtns-;qjkxbmwv`[");
        TranslationIndex<String> apples = index("item.apple", "Apple");
        List<KeyboardLayout> withDvorak = new ArrayList<KeyboardLayout>(KeyboardLayout.builtIn);
        withDvorak.add(dvorak);
        assertTrue(apples.suggest("arrpd").isEmpty());
        assertEquals("item.apple", firstKey(apples.suggest("arrpd", 5, withDvorak)));
        assertEquals("item.apple", apples.best("arrpd", 0.7, withDvorak).getKey());
    }

    @Test
    @DisplayName("a match of the text as typed beats an equal match after a layout fix")
    void aMatchOfTheTextAsTypedBeatsAnEqualMatchAfterALayoutFix() {
        TranslationIndex<String> entries = index("a.russian", "Привет", "z.typed", "ghbdtn");
        assertEquals("z.typed", bestKey(entries, "ghbdtn"));
        assertEquals(list("z.typed", "a.russian"), keys(entries.suggest("ghbdtn")));
    }
}
