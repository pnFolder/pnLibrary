package org.pnfolder.pnlibrary.localization;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayouts;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslatableTypeTest {
    private static final class MapLocaleTranslations implements LocaleTranslations {
        private final Map<String, String> values;

        MapLocaleTranslations(Map<String, String> values) {
            this.values = values;
        }

        @NotNull
        @Override
        public String getLocale() {
            return "en_us";
        }

        @NotNull
        @Override
        public TranslationMetadata getMetadata() {
            return new TranslationMetadata(TranslationSource.MEMORY, null, false);
        }

        @NotNull
        @Override
        public KeyboardLayouts getLayouts() {
            return KeyboardLayouts.builtIn;
        }

        @NotNull
        @Override
        public Optional<String> translate(@NotNull String key) {
            return Optional.ofNullable(values.get(key));
        }

        @NotNull
        @Override
        public Map<String, String> translations() {
            return values;
        }

        @NotNull
        @Override
        public TranslationIndex<String> keys() {
            throw new IllegalStateException("not needed for this test");
        }
    }

    private static final class FixedType implements TranslatableType<Object> {
        private final List<String> keys;
        private final String fallback;

        FixedType(List<String> keys, String fallback) {
            this.keys = keys;
            this.fallback = fallback;
        }

        @NotNull
        @Override
        public List<String> keysOf(Object value) {
            return keys;
        }

        @NotNull
        @Override
        public String fallbackName(Object value) {
            return fallback;
        }
    }

    private final MapLocaleTranslations table =
            new MapLocaleTranslations(Collections.singletonMap("second.key", "Second"));

    @Test
    @DisplayName("translate picks the first candidate key present in the table")
    void translatePicksTheFirstCandidateKeyPresentInTheTable() {
        Translation result = table.translate(new FixedType(java.util.Arrays.asList("missing.key", "second.key"), "fallback"), "x");
        assertEquals("second.key", result.getKey());
        assertEquals("Second", result.getText());
        assertTrue(result.isLocalized());
    }

    @Test
    @DisplayName("translate returns a fallback when no candidate key is present")
    void translateReturnsAFallbackWhenNoCandidateKeyIsPresent() {
        Translation result = table.translate(new FixedType(java.util.Arrays.asList("missing.one", "missing.two"), "fallback"), "x");
        assertEquals("missing.one", result.getKey());
        assertEquals("fallback", result.getText());
        assertFalse(result.isLocalized());
    }

    @Test
    @DisplayName("translate reports a null key when there are no candidates")
    void translateReportsANullKeyWhenThereAreNoCandidates() {
        Translation result = table.translate(new FixedType(Collections.emptyList(), "fallback"), "x");
        assertNull(result.getKey());
        assertEquals("fallback", result.getText());
        assertFalse(result.isLocalized());
    }
}
