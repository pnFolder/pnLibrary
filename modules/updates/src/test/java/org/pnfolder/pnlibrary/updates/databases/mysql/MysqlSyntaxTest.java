package org.pnfolder.pnlibrary.updates.databases.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MysqlSyntaxTest {
    @Test
    void aLiteralStaysOnOneLineAndEscapesQuotesAndBackslashes() {
        String text = "it's a\\b\nc\r" + '\u0000' + '\u001a';
        assertEquals("'it\\'s a\\\\b\\nc\\r\\0\\Z'", MysqlSyntax.literal(text));
    }

    @Test
    void anIdentifierDoublesItsBackticks() {
        assertEquals("`a``b`", MysqlSyntax.identifier("a`b"));
    }

    @Test
    void lineBreaksInsideQuotesAreEscapedAndOthersBecomeSpaces() {
        String sql = "CREATE TABLE `t` (\n `a` int DEFAULT 'x\ny\\'z\r' COMMENT \"c\nd\"\n)";
        String expected = "CREATE TABLE `t` (  `a` int DEFAULT 'x\\ny\\'z\\r' COMMENT \"c\\nd\" )";
        assertEquals(expected, MysqlSyntax.singleLine(sql));
    }
}
