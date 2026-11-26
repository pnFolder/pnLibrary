package org.pnfolder.pnlibrary.localization.layout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pnfolder.pnlibrary.localization.layout.KeyboardLayout.BE;
import static org.pnfolder.pnlibrary.localization.layout.KeyboardLayout.CS;
import static org.pnfolder.pnlibrary.localization.layout.KeyboardLayout.DE;
import static org.pnfolder.pnlibrary.localization.layout.KeyboardLayout.EN_US;
import static org.pnfolder.pnlibrary.localization.layout.KeyboardLayout.ES;
import static org.pnfolder.pnlibrary.localization.layout.KeyboardLayout.FR;
import static org.pnfolder.pnlibrary.localization.layout.KeyboardLayout.GREEK;
import static org.pnfolder.pnlibrary.localization.layout.KeyboardLayout.HE;
import static org.pnfolder.pnlibrary.localization.layout.KeyboardLayout.IT;
import static org.pnfolder.pnlibrary.localization.layout.KeyboardLayout.PT_BR;
import static org.pnfolder.pnlibrary.localization.layout.KeyboardLayout.RU;
import static org.pnfolder.pnlibrary.localization.layout.KeyboardLayout.TR_Q;
import static org.pnfolder.pnlibrary.localization.layout.KeyboardLayout.UK;

class KeyboardLayoutTest {
    private final Map<KeyboardLayout, String> letters = letters();

    private static Map<KeyboardLayout, String> letters() {
        Map<KeyboardLayout, String> map = new LinkedHashMap<KeyboardLayout, String>();
        map.put(EN_US, "qwertyuiopasdfghjklzxcvbnm");
        map.put(RU, "йцукенгшщзхъфывапролджэячсмитьбюё");
        map.put(UK, "йцукенгшщзхїґфівапролджєячсмитьбю");
        map.put(BE, "йцукенгшўзфывапролджэячсмітьбюё");
        map.put(DE, "qwertzuiopüasdfghjklöäyxcvbnmß");
        map.put(FR, "azertyuiopqsdfghjklmùwxcvbn");
        map.put(ES, "qwertyuiopçasdfghjklñzxcvbnm");
        map.put(IT, "qwertyuiopèùasdfghjklòàzxcvbnm");
        map.put(PT_BR, "qwertyuiopasdfghjklçzxcvbnm");
        map.put(TR_Q, "qwertyuıopğüasdfghjklşizxcvbnmöç");
        map.put(CS, "qwertzuiopúasdfghjklůyxcvbnm");
        map.put(GREEK, "ςερτυθιοπασδφγηξκλζχψωβνμ");
        map.put(HE, "קראטוןםפשדגכעיחלךףזסבהנמצתץ");
        return map;
    }

    private final KeyboardLayout dvorak = KeyboardLayout.of("dvorak", "',.pyfgcrl/=\\aoeuidhtns-;qjkxbmwv`[");

    private static List<KeyboardLayout> list(KeyboardLayout... layouts) {
        return Arrays.asList(layouts);
    }

    @Test
    @DisplayName("every pair of built-in layouts round trips")
    void everyPairOfBuiltInLayoutsRoundTrips() {
        for (Map.Entry<KeyboardLayout, String> entry : letters.entrySet()) {
            for (KeyboardLayout to : KeyboardLayout.builtIn) {
                KeyboardLayout from = entry.getKey();
                String sample = entry.getValue();
                assertEquals(sample, KeyboardLayout.convert(KeyboardLayout.convert(sample, from, to), to, from), from + " -> " + to);
            }
        }
    }

    @Test
    @DisplayName("known words convert in both directions")
    void knownWordsConvertInBothDirections() {
        assertEquals("яблоко", KeyboardLayout.convert("z,kjrj", EN_US, RU));
        assertEquals("apple", KeyboardLayout.convert("фззду", RU, EN_US));
        assertEquals("привет", KeyboardLayout.convert("ghbdtn", EN_US, RU));
        assertEquals("qwerty", KeyboardLayout.convert("azerty", FR, EN_US));
        assertEquals("qwerty", KeyboardLayout.convert("qwertz", DE, EN_US));
        assertEquals("іїєґ", KeyboardLayout.convert("s]'\\", EN_US, UK));
        assertEquals("s]'\\", KeyboardLayout.convert("іїєґ", UK, EN_US));
    }

    @Test
    @DisplayName("one known word per added layout converts from US keys")
    void oneKnownWordPerAddedLayoutConvertsFromUsKeys() {
        assertEquals("вада", KeyboardLayout.convert("dflf", EN_US, BE));
        assertEquals("ναι", KeyboardLayout.convert("nai", EN_US, GREEK));
        assertEquals("שלום", KeyboardLayout.convert("akuo", EN_US, HE));
        assertEquals("año", KeyboardLayout.convert("a;o", EN_US, ES));
        assertEquals("più", KeyboardLayout.convert("pi\\", EN_US, IT));
        assertEquals("şeker", KeyboardLayout.convert(";eker", EN_US, TR_Q));
        assertEquals("faça", KeyboardLayout.convert("fa;a", EN_US, PT_BR));
        assertEquals("můj", KeyboardLayout.convert("m;j", EN_US, CS));
        assertEquals(String.valueOf((char) 167), KeyboardLayout.convert("'", EN_US, CS));
    }

    @Test
    @DisplayName("case is kept and other characters pass through")
    void caseIsKeptAndOtherCharactersPassThrough() {
        assertEquals("Яблоко", KeyboardLayout.convert("Z,kjrj", EN_US, RU));
        assertEquals("ЯбЛОКО", KeyboardLayout.convert("Z,KJRJ", EN_US, RU));
        assertEquals("яблоко 123!", KeyboardLayout.convert("z,kjrj 123!", EN_US, RU));
    }

    @Test
    @DisplayName("detect finds the layouts that hold every letter")
    void detectFindsTheLayoutsThatHoldEveryLetter() {
        List<KeyboardLayout> latin = KeyboardLayout.detect("hello");
        assertEquals(EN_US, latin.get(0));
        assertTrue(latin.containsAll(list(DE, FR, ES, IT, PT_BR, TR_Q, CS)));
        assertFalse(latin.contains(GREEK) || latin.contains(HE));
        assertEquals(list(RU, UK), KeyboardLayout.detect("привет"));
        assertEquals(list(RU, BE), KeyboardLayout.detect("ты"));
        assertEquals(list(UK), KeyboardLayout.detect("їжак"));
        assertEquals(list(DE), KeyboardLayout.detect("straße"));
        assertEquals(list(GREEK), KeyboardLayout.detect("ναι"));
        assertEquals(list(HE), KeyboardLayout.detect("שלום"));
        assertTrue(KeyboardLayout.detect("яблоko").isEmpty());
        assertTrue(KeyboardLayout.detect("123 !").isEmpty());
    }

    @Test
    @DisplayName("alternatives are ordered, distinct and never the text itself")
    void alternativesAreOrderedDistinctAndNeverTheTextItself() {
        List<String> alternatives = KeyboardLayout.alternatives("z,kjrj");
        assertEquals("яблоко", alternatives.get(0));
        assertFalse(alternatives.contains("z,kjrj"));
        assertEquals(new ArrayList<String>(new LinkedHashSet<String>(alternatives)), alternatives);
        assertEquals(alternatives, KeyboardLayout.alternatives("z,kjrj"));
        assertTrue(KeyboardLayout.alternatives("123").isEmpty());
    }

    @Test
    @DisplayName("alternatives of a converted word lead back to the original")
    void alternativesOfAConvertedWordLeadBackToTheOriginal() {
        assertTrue(KeyboardLayout.alternatives("яблоко").contains("z,kjrj"));
        assertTrue(KeyboardLayout.alternatives("фззду").contains("apple"));
    }

    @Test
    @DisplayName("built-in layouts keep a stable order")
    void builtInLayoutsKeepAStableOrder() {
        List<String> ids = new ArrayList<String>();
        for (KeyboardLayout layout : KeyboardLayout.builtIn) {
            ids.add(layout.getId());
        }
        assertEquals(Arrays.asList("en_us", "ru", "uk", "be", "de", "fr", "es", "it", "pt_br", "tr_q", "cs", "el", "he"), ids);
    }

    @Test
    @DisplayName("layouts are equal by id and keys")
    void layoutsAreEqualByIdAndKeys() {
        String keys = "qwertyuiop[]\\asdfghjkl;'zxcvbnm,.`-";
        assertEquals(EN_US, KeyboardLayout.of("en_us", keys));
        assertEquals(EN_US.hashCode(), KeyboardLayout.of("en_us", keys).hashCode());
        assertNotEquals(EN_US, KeyboardLayout.of("other", keys));
        assertNotEquals(EN_US, RU);
        assertEquals("ru", RU.toString());
    }

    @Test
    @DisplayName("of rejects a blank id, a wrong key count and repeated keys")
    void ofRejectsABlankIdAWrongKeyCountAndRepeatedKeys() {
        String keys = "qwertyuiop[]\\asdfghjkl;'zxcvbnm,.`-";
        assertThrows(IllegalArgumentException.class, () -> KeyboardLayout.of(" ", keys));
        assertThrows(IllegalArgumentException.class, () -> KeyboardLayout.of("short", keys.substring(0, keys.length() - 1)));
        assertThrows(IllegalArgumentException.class, () -> KeyboardLayout.of("long", keys + "a"));
        StringBuilder repeatedKeys = new StringBuilder();
        for (int i = 0; i < 35; i++) {
            repeatedKeys.append('q');
        }
        IllegalArgumentException repeated = assertThrows(IllegalArgumentException.class,
                () -> KeyboardLayout.of("twice", repeatedKeys.toString()));
        assertTrue(repeated.getMessage().contains("twice"));
    }

    @Test
    @DisplayName("a custom layout converts and extends the set of a call")
    void aCustomLayoutConvertsAndExtendsTheSetOfACall() {
        String sample = letters.get(EN_US);
        List<KeyboardLayout> withDvorak = new ArrayList<KeyboardLayout>(KeyboardLayout.builtIn);
        withDvorak.add(dvorak);
        assertEquals(sample, KeyboardLayout.convert(KeyboardLayout.convert(sample, EN_US, dvorak), dvorak, EN_US));
        assertEquals("apple", KeyboardLayout.convert("arrpd", EN_US, dvorak));
        assertFalse(KeyboardLayout.alternatives("arrpd").contains("apple"));
        assertTrue(KeyboardLayout.alternatives("arrpd", withDvorak).contains("apple"));
        assertEquals("apple", KeyboardLayout.alternatives("arrpd", list(EN_US, dvorak)).get(0));
        assertTrue(KeyboardLayout.alternatives("arrpd", Collections.<KeyboardLayout>emptyList()).isEmpty());
    }
}
