package org.pnfolder.pnlibrary.core.updates.databases

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PostgresSyntaxTest {
    @Test
    fun `a literal stays on one line and escapes quotes backslashes and line breaks`() {
        assertEquals("E'it''s a\\\\b\\nc\\r'", PostgresSyntax.literal("it's a\\b\nc\r"))
    }

    @Test
    fun `an identifier doubles its quotes and bytes become a hex literal`() {
        assertEquals("\"a\"\"b\"", PostgresSyntax.identifier("a\"b"))
        assertEquals("'\\x00ff'", PostgresSyntax.bytes(byteArrayOf(0, -1)))
    }
}
