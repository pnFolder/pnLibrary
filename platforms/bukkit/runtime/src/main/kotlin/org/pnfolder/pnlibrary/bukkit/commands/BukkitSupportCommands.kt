package org.pnfolder.pnlibrary.bukkit.commands

import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.commands.CommandSender as Reply

/** The `/pn` support helpers: the support link and the diagnostic test errors. */
internal class BukkitSupportCommands(
    plugin: Plugin,
    library: PnLibrary,
    private val replies: BukkitCommandReplies,
) {
    private val testErrors = BukkitDiagnosticTestErrors(plugin, library)

    /** Shows where to get support. */
    fun support(reply: Reply) =
        replies.say(reply, ChatTone.INFO, "Поддержка pnFolder: ${PnLibraryIdentity.SUPPORT_URL}")

    /** Logs one uniquely identifiable test error. */
    fun error(reply: Reply) = testErrors.unique(replies.reported(reply))

    /** Logs the same test error [count] times, or the default number of times when `null`. */
    fun errorRepeat(reply: Reply, count: Int?) = testErrors.repeated(replies.reported(reply), count)

    /** Logs a test error with a nested cause chain. */
    fun errorChain(reply: Reply) = testErrors.chained(replies.reported(reply))
}
