package ru.privatenull.pnlibrary.localization

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion

class LocalizationApiTest {
    @Test fun `request normalizes explicit locales and fallback`() {
        val request = TranslationRequest.builder()
            .version(MinecraftVersion.V1_21_4)
            .locales("RU-ru", "de_de")
            .fallback("EN-us")
            .build()
        assertEquals(setOf("ru_ru", "de_de"), request.locales)
        assertEquals("en_us", request.fallbackLocale)
    }

    @Test fun `request rejects unknown version and unsafe locale`() {
        assertThrows<IllegalArgumentException> {
            TranslationRequest.builder().version(MinecraftVersion.UNKNOWN).locale("ru_ru").build()
        }
        assertThrows<IllegalArgumentException> {
            TranslationRequest.builder().version(MinecraftVersion.V1_21_4).locale("../ru_ru").build()
        }
    }
}
