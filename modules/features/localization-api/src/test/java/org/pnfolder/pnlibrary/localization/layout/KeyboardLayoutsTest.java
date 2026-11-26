package org.pnfolder.pnlibrary.localization.layout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.localization.TranslationIndex;
import org.pnfolder.pnlibrary.localization.TranslationMatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyboardLayoutsTest {
    private final KeyboardLayout dvorak = KeyboardLayout.of("test-dvorak", "',.pyfgcrl/=\\aoeuidhtns-;qjkxbmwv`[");
    private final KeyboardLayout other = KeyboardLayout.of("test-other",
            new StringBuilder("qwertyuiop[]\\asdfghjkl;'zxcvbnm,.`-").reverse().toString());

    private static TranslationIndex<String> apples(KeyboardLayouts layouts) {
        return TranslationIndex.of(
                Collections.singletonList(new TranslationMatch<>("item.apple", "Apple", "apple")), layouts);
    }

    private static List<KeyboardLayout> builtInPlus(KeyboardLayout... extra) {
        List<KeyboardLayout> all = new ArrayList<>(KeyboardLayout.builtIn);
        Collections.addAll(all, extra);
        return all;
    }

    private static String bestKey(TranslationIndex<String> index, String text) {
        TranslationMatch<String> best = index.best(text);
        return best == null ? null : best.getKey();
    }

    @Test
    @DisplayName("a new registry holds the built-in layouts")
    void aNewRegistryHoldsTheBuiltInLayouts() {
        assertEquals(KeyboardLayout.builtIn, KeyboardLayouts.create().all());
    }

    @Test
    @DisplayName("registered layouts follow the built-in ones in registration order")
    void registeredLayoutsFollowTheBuiltInOnesInRegistrationOrder() {
        KeyboardLayouts layouts = KeyboardLayouts.create();
        layouts.register(dvorak);
        layouts.register(other);

        assertEquals(builtInPlus(dvorak, other), layouts.all());
    }

    @Test
    @DisplayName("an id that is taken is rejected")
    void anIdThatIsTakenIsRejected() {
        KeyboardLayouts layouts = KeyboardLayouts.create();
        layouts.register(dvorak);

        assertThrows(IllegalArgumentException.class,
                () -> layouts.register(KeyboardLayout.of("test-dvorak", "abcdefghijklmnopqrstuvwxyz012345678")));
        assertThrows(IllegalArgumentException.class, () -> layouts.register(KeyboardLayout.RU));
        assertEquals(builtInPlus(dvorak), layouts.all());
    }

    @Test
    @DisplayName("closing the handle removes the layout and may repeat")
    void closingTheHandleRemovesTheLayoutAndMayRepeat() throws Exception {
        KeyboardLayouts layouts = KeyboardLayouts.create();
        AutoCloseable handle = layouts.register(dvorak);

        handle.close();
        handle.close();

        assertEquals(KeyboardLayout.builtIn, layouts.all());
        layouts.register(dvorak);
        assertTrue(layouts.all().contains(dvorak));
    }

    @Test
    @DisplayName("a spent handle does not remove a later registration of the same layout")
    void aSpentHandleDoesNotRemoveALaterRegistrationOfTheSameLayout() throws Exception {
        KeyboardLayouts layouts = KeyboardLayouts.create();
        AutoCloseable first = layouts.register(dvorak);
        first.close();
        layouts.register(dvorak);

        first.close();

        assertTrue(layouts.all().contains(dvorak));
    }

    @Test
    @DisplayName("closing the registry drops registrations and rejects new ones")
    void closingTheRegistryDropsRegistrationsAndRejectsNewOnes() throws Exception {
        KeyboardLayouts layouts = KeyboardLayouts.create();
        AutoCloseable handle = layouts.register(dvorak);

        layouts.close();
        handle.close();

        assertEquals(KeyboardLayout.builtIn, layouts.all());
        assertThrows(IllegalStateException.class, () -> layouts.register(dvorak));
    }

    @Test
    @DisplayName("the built-in registry is read-only and closing it does nothing")
    void theBuiltInRegistryIsReadOnlyAndClosingItDoesNothing() {
        assertThrows(UnsupportedOperationException.class, () -> KeyboardLayouts.builtIn.register(dvorak));
        KeyboardLayouts.builtIn.close();
        assertEquals(KeyboardLayout.builtIn, KeyboardLayouts.builtIn.all());
    }

    @Test
    @DisplayName("an index finds text typed on a registered layout without passing layouts")
    void anIndexFindsTextTypedOnARegisteredLayoutWithoutPassingLayouts() throws Exception {
        KeyboardLayouts layouts = KeyboardLayouts.create();
        TranslationIndex<String> index = apples(layouts);
        assertNull(index.best("arrpd"));

        AutoCloseable handle = layouts.register(dvorak);
        assertEquals("item.apple", bestKey(index, "arrpd"));
        List<String> suggested = new ArrayList<>();
        for (TranslationMatch<String> match : index.suggest("arrpd")) {
            suggested.add(match.getKey());
        }
        assertEquals(Collections.singletonList("item.apple"), suggested);

        handle.close();
        assertNull(index.best("arrpd"));
    }

    @Test
    @DisplayName("layouts passed to a call replace the registry")
    void layoutsPassedToACallReplaceTheRegistry() {
        KeyboardLayouts layouts = KeyboardLayouts.create();
        layouts.register(dvorak);
        TranslationIndex<String> index = apples(layouts);

        assertNull(index.best("arrpd", 0.7, Collections.emptyList()));
        assertNull(index.best("arrpd", 0.7, KeyboardLayout.builtIn));
        assertEquals("item.apple", index.best("arrpd", 0.7, builtInPlus(dvorak)).getKey());
    }

    @Test
    @DisplayName("an index built without a registry uses the built-in layouts")
    void anIndexBuiltWithoutARegistryUsesTheBuiltInLayouts() {
        TranslationIndex<String> index = TranslationIndex.of(
                Collections.singletonList(new TranslationMatch<>("item.apple", "Яблоко", "apple")));

        assertSame(KeyboardLayouts.builtIn, index.getLayouts());
        assertEquals("item.apple", bestKey(index, "z,kjrj"));
    }
}
