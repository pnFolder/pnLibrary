package org.pnfolder.pnlibrary.localization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;

import java.util.Arrays;
import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalizationApiTest {
    @Test
    @DisplayName("request normalizes explicit locales and fallback")
    void requestNormalizesExplicitLocalesAndFallback() {
        TranslationRequest request = TranslationRequest.builder()
                .version(MinecraftVersion.V1_21_4)
                .locales("RU-ru", "de_de")
                .fallback("EN-us")
                .build();
        assertEquals(new LinkedHashSet<>(Arrays.asList("ru_ru", "de_de")), request.getLocales());
        assertEquals("en_us", request.getFallbackLocale());
    }

    @Test
    @DisplayName("request rejects unknown version and unsafe locale")
    void requestRejectsUnknownVersionAndUnsafeLocale() {
        assertThrows(IllegalArgumentException.class, () ->
                TranslationRequest.builder().version(MinecraftVersion.UNKNOWN).locale("ru_ru").build());
        assertThrows(IllegalArgumentException.class, () ->
                TranslationRequest.builder().version(MinecraftVersion.V1_21_4).locale("../ru_ru").build());
    }

    @Test
    @DisplayName("dsl names the missing version")
    void dslNamesTheMissingVersion() {
        TranslationRequestDsl dsl = new TranslationRequestDsl();
        dsl.locales("ru_ru");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, dsl::build);
        assertEquals("Minecraft version must be set", error.getMessage());
    }
}
