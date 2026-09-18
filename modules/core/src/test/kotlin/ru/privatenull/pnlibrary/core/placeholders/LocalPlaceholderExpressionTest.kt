package ru.privatenull.pnlibrary.core.placeholders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LocalPlaceholderExpressionTest {
    @Test
    fun `parses local reference and formatter pipeline`() {
        val expression = requireNotNull(
            LocalPlaceholderExpression.parse("[player-level|default:0|upper]"),
        )

        assertEquals("player-level", expression.reference)
        assertEquals(listOf("default:0", "upper"), expression.formatters)
    }

    @Test
    fun `rejects malformed or empty local expressions`() {
        listOf("player", "[]", "[ ]", "[|upper]", "[[player]]", "[player").forEach {
            assertNull(LocalPlaceholderExpression.parse(it), it)
        }
    }
}
