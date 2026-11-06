package org.pnfolder.pnlibrary.core.updates.databases

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MysqlSyntaxTest {
    @Test
    fun `a literal stays on one line and escapes quotes and backslashes`() {
        val text = "it's a\\b\nc\r" + '\u0000' + '\u001a'
        assertEquals("'it\\'s a\\\\b\\nc\\r\\0\\Z'", MysqlSyntax.literal(text))
    }

    @Test
    fun `an identifier doubles its backticks`() {
        assertEquals("`a``b`", MysqlSyntax.identifier("a`b"))
    }
}
