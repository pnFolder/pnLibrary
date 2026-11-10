package org.pnfolder.pnlibrary.core.updates.databases

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TableMaskTest {
    @Test
    fun `an empty list takes everything and a star stands for any text`() {
        assertTrue(TableMask.matches(emptyList(), "anything"))
        assertTrue(TableMask.matches(listOf("clans", "log_*"), "log_2026_11"))
        assertTrue(TableMask.matches(listOf("clans"), "clans"))
        assertFalse(TableMask.matches(listOf("clans"), "clans_old"))
        assertFalse(TableMask.matches(listOf("log_*"), "blog_1"))
    }

    @Test
    fun `regex characters in a mask are plain text`() {
        assertTrue(TableMask.matches(listOf("a.b"), "a.b"))
        assertFalse(TableMask.matches(listOf("a.b"), "axb"))
        assertTrue(TableMask.matches(listOf("t(1)+*"), "t(1)+rest"))
        assertTrue(TableMask.matches(listOf("*"), ""))
    }
}
