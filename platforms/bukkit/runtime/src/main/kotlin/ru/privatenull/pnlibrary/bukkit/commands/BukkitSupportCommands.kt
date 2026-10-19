package ru.privatenull.pnlibrary.bukkit.commands

import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.api.runtime.PnLibraryIdentity
import ru.privatenull.pnlibrary.core.ui.ChatTone
import ru.privatenull.pnlibrary.api.commands.CommandSender as Reply

/** The `/pn` support helpers: debug and support hints, and the diagnostic test errors. */
internal class BukkitSupportCommands(
    plugin: Plugin,
    library: PnLibrary,
    private val replies: BukkitCommandReplies,
) {
    private val testErrors = BukkitDiagnosticTestErrors(plugin, library)

    /** Points to the support report command. */
    fun debug(reply: Reply) =
        replies.say(reply, ChatTone.INFO, "Отчёт для поддержки: /pndebug [all|plugin] [--full|--config|--logs]")

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
