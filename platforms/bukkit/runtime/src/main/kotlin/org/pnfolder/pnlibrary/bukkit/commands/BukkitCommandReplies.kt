package org.pnfolder.pnlibrary.bukkit.commands

import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.updates.UpdateActor
import org.pnfolder.pnlibrary.api.chat.ChatNotice
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogRoutes
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogView
import org.pnfolder.pnlibrary.api.commands.CommandSender as Reply

/**
 * Shared reply rendering and sender plumbing of the `/pn` handlers.
 *
 * Every reply is rendered by [UpdateCatalogView], so notices and confirmations look like the catalog.
 */
internal class BukkitCommandReplies(
    private val plugin: Plugin,
    private val library: PnLibrary,
) {
    /** Routes for this reader: buttons only when it can click, ASCII for the console. */
    fun routes(reply: Reply): UpdateCatalogRoutes =
        BukkitControlCommand.ROUTES.copy(interactive = reply.supportsInteraction, ascii = reply.isConsole)

    fun say(reply: Reply, tone: ChatTone, text: String, detail: String? = null) =
        notices(reply, listOf(ChatNotice(tone, text, detail)))

    fun notices(reply: Reply, notices: List<ChatNotice>) =
        UpdateCatalogView.notices(notices, routes(reply)).forEach(reply::send)

    fun confirm(reply: Reply, title: String, lines: List<String>, label: String, command: String) =
        UpdateCatalogView.confirmation(title, lines + "Подтверждение действует 30 секунд.", label, command,
            "Выполнить: $command", routes(reply)).forEach(reply::send)

    /** Returns a callback that reports [reply]-bound successes. */
    fun reported(reply: Reply): (String) -> Unit = { text -> say(reply, ChatTone.SUCCESS, text) }

    fun notRegistered(reply: Reply, product: String) =
        say(reply, ChatTone.FAILURE, "Плагин $product не зарегистрирован в pnLibrary.")

    /** Reports the root cause; a missing release file gets an explanation instead of a bare HTTP code. */
    fun fail(reply: Reply, title: String, error: Throwable) {
        val cause = generateSequence(error) { it.cause }.last()
        val message = cause.message ?: cause.javaClass.simpleName
        val detail = if (message.contains("HTTP 404", ignoreCase = true)) {
            "Файл обновления не найден (HTTP 404): проверьте ссылку в каталоге релизов."
        } else message
        say(reply, ChatTone.FAILURE, "$title.", detail)
    }

    /** Returns whether the runtime is open, telling [reply] to wait otherwise. */
    fun ready(reply: Reply): Boolean {
        if (!library.isClosed) return true
        say(reply, ChatTone.FAILURE, "pnLibrary ещё не готова. Дождитесь полного запуска сервера.")
        return false
    }

    /** Unwraps the native sender and runs [action] with it and the reply channel, unless the runtime is closed. */
    fun native(context: CommandContext, action: (CommandSender, Reply) -> Unit) {
        val sender = (context.sender as? BukkitCommandSender)?.native
        when {
            sender == null -> say(context.sender, ChatTone.FAILURE, "Эта команда доступна только на Bukkit.")
            ready(context.sender) -> action(sender, context.sender)
        }
    }

    /** Delivers asynchronous results on the server thread while the runtime is open. */
    fun onServerThread(action: () -> Unit) {
        if (library.isClosed) return
        plugin.server.scheduler.runTask(plugin, Runnable { if (!library.isClosed) action() })
    }

    /** Identifies [sender] in update audit records. */
    fun updateActor(sender: CommandSender): UpdateActor = when (sender) {
        is Player -> UpdateActor(UpdateActor.Kind.PLAYER, sender.uniqueId.toString(), sender.name)
        is ConsoleCommandSender -> UpdateActor(UpdateActor.Kind.CONSOLE, "console", sender.name)
        else -> UpdateActor(UpdateActor.Kind.INTEGRATION, sender.name, sender.name)
    }
}
