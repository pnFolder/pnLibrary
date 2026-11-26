package org.pnfolder.pnlibrary.localization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftLocaleTest {
    @Test
    @DisplayName("parse resolves known code")
    void parseResolvesKnownCode() {
        assertEquals(MinecraftLocale.RU_RU, MinecraftLocale.parse("ru_ru"));
    }

    @Test
    @DisplayName("parse normalizes case and hyphen")
    void parseNormalizesCaseAndHyphen() {
        assertEquals(MinecraftLocale.RU_RU, MinecraftLocale.parse("  RU-RU "));
        assertEquals(MinecraftLocale.EN_US, MinecraftLocale.parse("EN_US"));
    }

    @Test
    @DisplayName("parse returns null for unknown or missing input")
    void parseReturnsNullForUnknownOrMissingInput() {
        assertNull(MinecraftLocale.parse("xx_yy"));
        assertNull(MinecraftLocale.parse(null));
    }

    @Test
    @DisplayName("every code is unique and normalized")
    void everyCodeIsUniqueAndNormalized() {
        Set<String> unique = new HashSet<String>();
        int count = 0;
        boolean normalized = true;
        for (MinecraftLocale locale : MinecraftLocale.values()) {
            count++;
            unique.add(locale.getCode());
            normalized &= locale.getCode().equals(locale.getCode().toLowerCase(Locale.ROOT))
                    && locale.getCode().indexOf('-') < 0;
        }
        assertEquals(count, unique.size(), "duplicate locale codes");
        assertTrue(normalized, "codes must be lowercase with underscores");
    }

    @Test
    @DisplayName("typed builder overloads match their string form")
    void typedBuilderOverloadsMatchTheirStringForm() {
        TranslationRequest typed = TranslationRequest.builder()
                .version(MinecraftVersion.V1_20_4)
                .locales(MinecraftLocale.RU_RU, MinecraftLocale.EN_US)
                .fallback(MinecraftLocale.EN_US)
                .build();
        assertEquals(new LinkedHashSet<String>(Arrays.asList("ru_ru", "en_us")), typed.getLocales());
        assertEquals("en_us", typed.getFallbackLocale());
    }
}
