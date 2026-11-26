package org.pnfolder.pnlibrary.api.chat;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.commands.CommandSender;

/** Who reads a message and what their client can do. */
public final class ChatReader {
    /** A player whose client shows buttons and hover cards. */
    public static final ChatReader PLAYER = new ChatReader(true, false);

    /** A server console: plain text in ASCII. */
    public static final ChatReader CONSOLE = new ChatReader(false, true);

    private final boolean interactive;
    private final boolean ascii;

    /**
     * @param interactive whether the reader can click and hover; without it buttons are left out and pages list the
     *     commands to type instead
     * @param ascii whether to avoid characters outside ASCII and Cyrillic, for consoles with a legacy code page
     */
    public ChatReader(boolean interactive, boolean ascii) {
        this.interactive = interactive;
        this.ascii = ascii;
    }

    /** A reader that can click and hover and shows any character. */
    public ChatReader() {
        this(true, false);
    }

    /** Whether the reader can click and hover. */
    public boolean isInteractive() {
        return interactive;
    }

    /** Whether to avoid characters outside ASCII and Cyrillic. */
    public boolean isAscii() {
        return ascii;
    }

    /** The reader behind a command {@code sender}: buttons only while its transport keeps them, ASCII on the console. */
    public static @NotNull ChatReader of(@NotNull CommandSender sender) {
        return new ChatReader(sender.getSupportsInteraction(), AudienceExtensions.isConsole(sender));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ChatReader)) {
            return false;
        }
        ChatReader that = (ChatReader) other;
        return interactive == that.interactive && ascii == that.ascii;
    }

    @Override
    public int hashCode() {
        return Objects.hash(interactive, ascii);
    }

    @Override
    public String toString() {
        return "ChatReader(interactive=" + interactive + ", ascii=" + ascii + ")";
    }
}
