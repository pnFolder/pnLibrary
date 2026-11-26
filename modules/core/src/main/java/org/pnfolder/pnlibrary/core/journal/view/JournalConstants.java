package org.pnfolder.pnlibrary.core.journal.view;

/** Sizes shared by the journal screens. */
final class JournalConstants {
    static final int PAGE_SIZE = 5;

    // Keeps lines from flooding a player's chat; the console and the card show the full message.
    static final int CHAT_CHARS = 46;

    // Records read to fold repeats and to count how often an event happened.
    static final int SCAN_LIMIT = 5_000;

    static final int HOVER_WIDTH = 44;
    static final int HOVER_MESSAGE_LINES = 4;
    static final int CARD_WIDTH = 56;
    static final int REPEAT_ROWS = 15;

    private JournalConstants() {
    }
}
