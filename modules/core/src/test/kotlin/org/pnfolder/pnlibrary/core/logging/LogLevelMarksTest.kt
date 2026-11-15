package org.pnfolder.pnlibrary.core.logging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.logging.LogLevel

class LogLevelMarksTest {
    @Test
    fun successIsMarkedAndOtherLevelsAreUntouched() {
        assertEquals("[OK] ready", LogLevelMarks.decorate(LogLevel.SUCCESS, "ready"))
        assertEquals("[CRITICAL] down", LogLevelMarks.decorate(LogLevel.CRITICAL, "down"))
        listOf(LogLevel.INFO, LogLevel.WARNING, LogLevel.ERROR).forEach {
            assertEquals("text", LogLevelMarks.decorate(it, "text"))
        }
    }
}
