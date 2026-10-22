package org.pnfolder.pnlibrary.core.placeholders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

class PlaceholderBuiltInFormattersTest {
    private val forms = listOf("item", "items", "items-many")

    @Test
    fun `plural formatter handles conventional edge cases`() {
        assertEquals("item", PlaceholderBuiltInFormatters.plural(1, forms))
        assertEquals("items", PlaceholderBuiltInFormatters.plural(24, forms))
        assertEquals("items-many", PlaceholderBuiltInFormatters.plural(11, forms))
        assertEquals("items-many", PlaceholderBuiltInFormatters.plural(-12, forms))
    }

    @Test
    fun `duration formatter preserves sign and omits empty units`() {
        assertEquals("1d 1h 1m 1s", PlaceholderBuiltInFormatters.duration(Duration.ofSeconds(90_061)))
        assertEquals("-1m 1s", PlaceholderBuiltInFormatters.duration(-61))
        assertEquals("0s", PlaceholderBuiltInFormatters.duration(0))
    }

    @Test
    fun `number group and short format numbers and keep other text`() {
        assertEquals("3.14", PlaceholderBuiltInFormatters.number(3.14159, listOf("2")))
        assertEquals("3", PlaceholderBuiltInFormatters.number("2.5", emptyList()))
        assertEquals("abc", PlaceholderBuiltInFormatters.number("abc", listOf("2")))
        assertEquals("1 234 567.5", PlaceholderBuiltInFormatters.group(1234567.5))
        assertEquals("-1 000", PlaceholderBuiltInFormatters.group(-1000))
        assertEquals("999", PlaceholderBuiltInFormatters.short(999))
        assertEquals("1.5k", PlaceholderBuiltInFormatters.short(1500))
        assertEquals("2.3M", PlaceholderBuiltInFormatters.short(2_300_000))
        assertEquals("Hello", PlaceholderBuiltInFormatters.capitalize("hello"))
    }
}
