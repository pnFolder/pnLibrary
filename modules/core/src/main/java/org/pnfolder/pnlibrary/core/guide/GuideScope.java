package org.pnfolder.pnlibrary.core.guide;

import org.jetbrains.annotations.Nullable;

/** Where a command exists. */
public enum GuideScope {
    /** On every platform. */
    EVERYWHERE(null),
    /** Only on a game server. */
    GAME_SERVER("Есть только на игровом сервере."),
    /** Only on a proxy. */
    PROXY("Есть только на прокси (BungeeCord, Velocity).");

    private final String note;

    GuideScope(String note) {
        this.note = note;
    }

    /** The sentence printed under the command, or null for none. */
    public @Nullable String getNote() {
        return note;
    }
}
