package org.pnfolder.pnlibrary.core.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.logging.LogLevel;

class LogLevelMarksTest {
    @Test
    void successIsMarkedAndOtherLevelsAreUntouched() {
        assertEquals("[OK] ready", LogLevelMarks.decorate(LogLevel.SUCCESS, "ready"));
        assertEquals("[CRITICAL] down", LogLevelMarks.decorate(LogLevel.CRITICAL, "down"));
        for (LogLevel level : new LogLevel[] {LogLevel.INFO, LogLevel.WARNING, LogLevel.ERROR}) {
            assertEquals("text", LogLevelMarks.decorate(level, "text"));
        }
    }
}
