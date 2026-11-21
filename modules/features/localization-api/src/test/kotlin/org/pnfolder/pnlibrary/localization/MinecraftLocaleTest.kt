package org.pnfolder.pnlibrary.localization

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MinecraftLocaleTest {
    @Test
    fun `parse resolves known code`() {
        assertEquals(MinecraftLocale.RU_RU, MinecraftLocale.parse("ru_ru"))
    }

    @Test
    fun `parse normalizes case and hyphen`() {
        assertEquals(MinecraftLocale.RU_RU, MinecraftLocale.parse("  RU-RU "))
        assertEquals(MinecraftLocale.EN_US, MinecraftLocale.parse("EN_US"))
    }

    @Test
    fun `parse returns null for unknown or missing input`() {
        assertNull(MinecraftLocale.parse("xx_yy"))
        assertNull(MinecraftLocale.parse(null))
    }

    @Test
    fun `every code is unique and normalized`() {
        val codes = MinecraftLocale.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size, "duplicate locale codes")
        assertTrue(codes.all { it == it.lowercase() && '-' !in it }, "codes must be lowercase with underscores")
    }

    @Test
    fun `typed builder overloads match their string form`() {
        val typed = TranslationRequest.builder()
            .version(org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion.V1_20_4)
            .locales(MinecraftLocale.RU_RU, MinecraftLocale.EN_US)
            .fallback(MinecraftLocale.EN_US)
            .build()
        assertEquals(setOf("ru_ru", "en_us"), typed.locales)
        assertEquals("en_us", typed.fallbackLocale)
    }
}
