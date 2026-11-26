package org.pnfolder.pnlibrary.core.journal;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.logging.LogLevel;

/** Severity of a journal record. Each level is stored in its own file set, so retention can differ per level. */
public enum JournalLevel {
    INFO("info"),
    WARNING("warning"),
    ERROR("error");

    private final String fileGroup;

    JournalLevel(String fileGroup) {
        this.fileGroup = fileGroup;
    }

    /** The prefix of the files that hold this level. */
    public @NotNull String getFileGroup() {
        return fileGroup;
    }

    /** Maps a library log level to its journal file set. SUCCESS is routine information. */
    public static @NotNull JournalLevel of(@NotNull LogLevel level) {
        switch (level) {
            case WARNING:
                return WARNING;
            case ERROR:
            case CRITICAL:
                return ERROR;
            default:
                return INFO;
        }
    }
}
