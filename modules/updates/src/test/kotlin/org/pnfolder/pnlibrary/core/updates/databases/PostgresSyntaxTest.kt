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

    @Test
    fun `an enum type is created once and quotes its labels`() {
        assertEquals(
            "DO \$\$ BEGIN CREATE TYPE \"mood\" AS ENUM (E'happy', E'it''s'); EXCEPTION WHEN duplicate_object THEN NULL; END \$\$",
            PostgresSyntax.createEnum("mood", listOf("happy", "it's")),
        )
        assertEquals(true, PostgresSyntax.createEnum("m", listOf("a\$\$b")).startsWith("DO \$pn\$ "))
    }

    @Test
    fun `a sequence keeps its settings`() {
        assertEquals(
            "CREATE SEQUENCE IF NOT EXISTS \"s\" AS bigint INCREMENT BY 5 MINVALUE 1 MAXVALUE 99 START WITH 10 CYCLE",
            PostgresSyntax.createSequence("s", "bigint", 10, 5, 1, 99, true),
        )
        assertEquals(true, PostgresSyntax.createSequence("s", "integer", 1, 1, 1, 9, false).endsWith("START WITH 1 NO CYCLE"))
    }
}
