package org.pnfolder.pnlibrary.core.logging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.logging.LogLevel

class ConsoleStyleTest {
    @Test
    fun successIsMarkedAndOtherLevelsAreUntouched() {
        assertEquals("[OK] ready", ConsoleStyle.decorate(LogLevel.SUCCESS, "ready"))
        listOf(LogLevel.INFO, LogLevel.WARNING, LogLevel.ERROR).forEach {
            assertEquals("text", ConsoleStyle.decorate(it, "text"))
        }
    }
}
