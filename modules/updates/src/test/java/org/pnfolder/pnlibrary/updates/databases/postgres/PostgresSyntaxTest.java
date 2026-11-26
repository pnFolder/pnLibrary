package org.pnfolder.pnlibrary.updates.databases.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PostgresSyntaxTest {
    @Test
    void aLiteralStaysOnOneLineAndEscapesQuotesBackslashesAndLineBreaks() {
        assertEquals("E'it''s a\\\\b\\nc\\r'", PostgresSyntax.literal("it's a\\b\nc\r"));
    }

    @Test
    void anIdentifierDoublesItsQuotesAndBytesBecomeAHexLiteral() {
        assertEquals("\"a\"\"b\"", PostgresSyntax.identifier("a\"b"));
        assertEquals("'\\x00ff'", PostgresSyntax.bytes(new byte[] {0, -1}));
    }

    @Test
    void anEnumTypeIsCreatedOnceAndQuotesItsLabels() {
        assertEquals(
                "DO $$ BEGIN CREATE TYPE \"mood\" AS ENUM (E'happy', E'it''s'); EXCEPTION WHEN duplicate_object THEN NULL; END $$",
                PostgresSyntax.createEnum("mood", Arrays.asList("happy", "it's")));
        assertEquals(true, PostgresSyntax.createEnum("m", Arrays.asList("a$$b")).startsWith("DO $pn$ "));
    }

    @Test
    void aSequenceKeepsItsSettings() {
        assertEquals(
                "CREATE SEQUENCE IF NOT EXISTS \"s\" AS bigint INCREMENT BY 5 MINVALUE 1 MAXVALUE 99 START WITH 10 CYCLE",
                PostgresSyntax.createSequence("s", "bigint", 10, 5, 1, 99, true));
        assertEquals(true, PostgresSyntax.createSequence("s", "integer", 1, 1, 1, 9, false).endsWith("START WITH 1 NO CYCLE"));
    }
}
