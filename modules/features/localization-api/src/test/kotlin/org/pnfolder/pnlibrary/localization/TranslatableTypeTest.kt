package org.pnfolder.pnlibrary.localization

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Optional

private class MapLocaleTranslations(private val values: Map<String, String>) : LocaleTranslations {
    override val locale = "en_us"
    override val metadata = TranslationMetadata(TranslationSource.MEMORY, null, false)
    override fun translate(key: String): Optional<String> = Optional.ofNullable(values[key])
    override fun translations(): Map<String, String> = values
    override fun keys() = error("not needed for this test")
}

private class FixedType(private val keys: List<String>, private val fallback: String) : TranslatableType<Unit> {
    override fun keysOf(value: Unit): List<String> = keys
    override fun fallbackName(value: Unit): String = fallback
}

class TranslatableTypeTest {
    private val table = MapLocaleTranslations(mapOf("second.key" to "Second"))

    @Test
    fun `translate picks the first candidate key present in the table`() {
        val result = table.translate(FixedType(listOf("missing.key", "second.key"), "fallback"), Unit)
        assertEquals("second.key", result.key)
        assertEquals("Second", result.text)
        assertTrue(result.localized)
    }

    @Test
    fun `translate returns a fallback when no candidate key is present`() {
        val result = table.translate(FixedType(listOf("missing.one", "missing.two"), "fallback"), Unit)
        assertEquals("missing.one", result.key)
        assertEquals("fallback", result.text)
        assertFalse(result.localized)
    }

    @Test
    fun `translate reports a null key when there are no candidates`() {
        val result = table.translate(FixedType(emptyList(), "fallback"), Unit)
        assertNull(result.key)
        assertEquals("fallback", result.text)
        assertFalse(result.localized)
    }
}
