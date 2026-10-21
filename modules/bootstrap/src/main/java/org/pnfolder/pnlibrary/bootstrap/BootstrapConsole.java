package org.pnfolder.pnlibrary.bootstrap;

import org.pnfolder.pnlibrary.console.ConsoleTheme;

/** How a platform shows the installer's console cards: its colours and its console. */
public interface BootstrapConsole {

    /** Mood of a card. */
    enum Tone {
        /** Work in progress or a warning. */
        PROGRESS,
        /** Done. */
        SUCCESS,
        /** Failed; the plugin will not start. */
        FAILURE
    }

    /**
     * Returns the colours for a card of {@code tone}.
     *
     * @param tone mood of the card
     * @return the theme; {@link ConsoleTheme#plain()} for a console without colours
     */
    ConsoleTheme theme(Tone tone);

    /**
     * Prints one rendered line.
     *
     * @param line text with the theme's colour codes
     */
    void send(String line);
}
