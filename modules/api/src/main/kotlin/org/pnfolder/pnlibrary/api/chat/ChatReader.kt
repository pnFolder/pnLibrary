package org.pnfolder.pnlibrary.api.chat

import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.api.sender.isConsole

/**
 * Who reads a message and what their client can do.
 *
 * @property interactive whether the reader can click and hover; without it buttons are left out and pages list the
 *   commands to type instead
 * @property ascii whether to avoid characters outside ASCII and Cyrillic, for consoles with a legacy code page
 */
data class ChatReader(val interactive: Boolean = true, val ascii: Boolean = false) {
    /** Ready-made readers for the common cases and the factory for a command sender. */
    companion object {
        /** A player whose client shows buttons and hover cards. */
        @JvmField val PLAYER = ChatReader()

        /** A server console: plain text in ASCII. */
        @JvmField val CONSOLE = ChatReader(interactive = false, ascii = true)

        /** The reader behind a command [sender]: buttons only while its transport keeps them, ASCII on the console. */
        @JvmStatic
        fun of(sender: CommandSender): ChatReader = ChatReader(sender.supportsInteraction, sender.isConsole)
    }
}
