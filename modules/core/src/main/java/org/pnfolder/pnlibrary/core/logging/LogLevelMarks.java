package org.pnfolder.pnlibrary.core.logging;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.logging.LogLevel;

/**
 * Marks lines the native logger cannot tell apart: it has no success level, so a success would look like plain info.
 * Warnings and errors already carry the platform's own level and colour.
 */
public final class LogLevelMarks {
    public static final String SUCCESS_MARK = "[OK] ";
    public static final String CRITICAL_MARK = "[CRITICAL] ";

    private LogLevelMarks() {
    }

    /** Returns {@code message} with the mark of {@code level}, or unchanged when the level has none. */
    public static @NotNull String decorate(@NotNull LogLevel level, @NotNull String message) {
        switch (level) {
            case SUCCESS:
                return SUCCESS_MARK + message;
            case CRITICAL:
                return CRITICAL_MARK + message;
            default:
                return message;
        }
    }
}
