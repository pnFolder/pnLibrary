package org.pnfolder.pnlibrary.bukkit.commands

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.chat.ConfirmChoice
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateAction
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateConfirmationTokens
import java.time.Duration
import java.util.UUID
import org.pnfolder.pnlibrary.api.commands.CommandSender as Reply

/** How long a confirmation token stays valid. */
internal val CONFIRMATION_TIME: Duration = Duration.ofSeconds(30)

/** The identity the console's tokens are bound to. */
private val CONSOLE_ID: UUID = UUID(0L, 0L)

/** A player's own id, or one fixed id for the console, so that both get tokens only they can use. */
internal fun identityOf(sender: CommandSender): UUID = (sender as? Player)?.uniqueId ?: CONSOLE_ID

/** Asks for, confirms and cancels the server restart that applies a staged update plan. */
internal class BukkitRestartFlow(
    private val library: PnLibrary,
    private val replies: BukkitCommandReplies,
    private val tokens: UpdateConfirmationTokens,
    private val restarter: ServerRestarter,
) {
    /** Asks to confirm the restart that applies the staged plan. */
    fun restartUpdate(sender: CommandSender, reply: Reply) {
        if (!sender.hasPermission("pnlibrary.updates.restart")) {
            return replies.say(reply, ChatTone.FAILURE, "Недостаточно прав для перезапуска.")
        }
        val plan = library.updates.currentPlan().orElse(null)
        if (plan == null || plan.state != UpdateState.UPDATE_STAGED && plan.state != UpdateState.ROLLED_BACK) {
            return replies.say(reply, ChatTone.WARNING, "Нет подготовленных обновлений, которым нужен перезапуск.")
        }
        askRestart(sender, reply, plan.id, plan.revision)
    }

    /** Offers the three ways to restart; each button carries its own confirmation. */
    private fun askRestart(sender: CommandSender, reply: Reply, plan: UUID, revision: Long) {
        fun command(action: UpdateAction) =
            BukkitControlCommand.confirmCommand(tokens.issue(identityOf(sender), plan, revision, action, CONFIRMATION_TIME))
        val script = if (restarter.script() == null) {
            "restart-script в spigot.yml не задан: сервер остановится и поднимется, только если его запустит панель или скрипт автозапуска."
        } else {
            "Сервер поднимется сам."
        }
        replies.confirmChoices(
            reply,
            "Перезапуск сервера",
            listOf("Игроков на сервере: ${Bukkit.getOnlinePlayers().size}.", "Подготовленные версии применятся после перезапуска.", script),
            listOf(
                ConfirmChoice("Сейчас", command(UpdateAction.RESTART), "Перезапустить сразу, игрокам придёт отсчёт"),
                ConfirmChoice("Через 5 мин", command(UpdateAction.RESTART_LATER), "Предупредить игроков и перезапустить через 5 минут"),
                ConfirmChoice("Когда никого нет", command(UpdateAction.RESTART_EMPTY), "Перезапустить, как только все выйдут"),
            ),
        )
    }

    /** Offers a restart when [sender] may restart, otherwise just says the server has to be restarted. */
    fun suggest(sender: CommandSender, reply: Reply) {
        if (sender.hasPermission("pnlibrary.updates.restart")) restartUpdate(sender, reply)
    }

    /** Withdraws a restart that is counting down or waiting for the server to empty. */
    fun cancelRestart(reply: Reply) {
        if (restarter.cancel()) replies.say(reply, ChatTone.SUCCESS, "Перезапуск отменён.")
        else replies.say(reply, ChatTone.WARNING, "Перезапуск не запланирован.")
    }

    fun isStill(grant: UpdateConfirmationTokens.Grant): Boolean =
        library.updates.currentPlan().orElse(null)?.let { it.id == grant.plan && it.revision == grant.revision } == true

    fun run(sender: CommandSender, reply: Reply, grant: UpdateConfirmationTokens.Grant, mode: ServerRestarter.Mode) {
        if (!sender.hasPermission("pnlibrary.updates.restart")) {
            return replies.say(reply, ChatTone.FAILURE, "Недостаточно прав для перезапуска.")
        }
        if (!isStill(grant)) return replies.say(reply, ChatTone.FAILURE, "План обновления изменился; запросите перезапуск заново.")
        when (restarter.start(mode)) {
            ServerRestarter.Started.COUNTDOWN -> replies.say(reply, ChatTone.SUCCESS, "Перезапуск запланирован.", "Отменить: /pn update restart cancel")
            ServerRestarter.Started.WAITING_FOR_EMPTY ->
                replies.say(reply, ChatTone.SUCCESS, "Перезапущу сервер, когда на нём никого не останется.", "Отменить: /pn update restart cancel")
        }
    }
}
