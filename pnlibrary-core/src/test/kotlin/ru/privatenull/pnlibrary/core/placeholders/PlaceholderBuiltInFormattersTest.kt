package ru.privatenull.pnlibrary.core.placeholders

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
}
