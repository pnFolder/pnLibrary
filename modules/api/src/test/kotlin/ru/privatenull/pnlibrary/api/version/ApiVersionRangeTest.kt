package ru.privatenull.pnlibrary.api.version

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApiVersionRangeTest {
    @Test
    fun `supports inclusive API generation bounds`() {
        val range = ApiVersionRange(4, 5)

        assertFalse(range.supports(3))
        assertTrue(range.supports(4))
        assertTrue(range.supports(5))
        assertFalse(range.supports(6))
    }

    @Test
    fun `rejects non-positive and reversed API ranges`() {
        assertThrows(IllegalArgumentException::class.java) { ApiVersionRange(0, 1) }
        assertThrows(IllegalArgumentException::class.java) { ApiVersionRange(1, 0) }
        assertThrows(IllegalArgumentException::class.java) { ApiVersionRange(5, 4) }
    }

    @Test
    fun `publishes the current independent API generation`() {
        assertEquals(1, PnLibraryApi.VERSION)
    }
}
