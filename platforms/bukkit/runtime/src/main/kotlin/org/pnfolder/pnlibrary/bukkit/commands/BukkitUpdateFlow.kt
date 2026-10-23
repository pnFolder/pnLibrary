package org.pnfolder.pnlibrary.bukkit.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.api.updates.UpdatePlanSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.api.updates.release.ProductChange
import org.pnfolder.pnlibrary.bukkit.updates.UpdateAction
import org.pnfolder.pnlibrary.bukkit.updates.UpdateConfirmationTokens
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogCommands
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogView
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import org.pnfolder.pnlibrary.api.commands.CommandSender as Reply

/**
 * The `/pn` update flow: status, check, stage, rollback, restart, and the one confirmation they share.
 *
 * Rollback and restart are asked for first and happen on `/pn update confirm <token>`: the token says which of
 * them it grants, so there is a single confirmation command. A token belongs to the player (or the console) that
 * asked, works once and expires; asynchronous results are delivered on the server thread.
 */
internal class BukkitUpdateFlow(
    private val plugin: Plugin,
    private val library: PnLibrary,
    private val catalog: UpdateCatalogCommands,
    private val replies: BukkitCommandReplies,
) {
    private val tokens = UpdateConfirmationTokens()

    /** Replies immediately, then shows the refreshed list of updates on the server thread. */
    fun checkUpdates(context: CommandContext) {
        if (!replies.ready(context.sender)) return
        val native = (context.sender as? BukkitCommandSender)?.native ?: return
        replies.say(context.sender, ChatTone.PROGRESS, "Проверяю обновления…")
        library.updates.checkNow(replies.updateActor(native)).whenComplete { _, error ->
            replies.onServerThread {
                if (error != null) replies.fail(context.sender, "Не удалось проверить обновления", error)
                else catalog.showAvailable(context)
            }
        }
    }

    /** Asks to confirm a rollback to one saved backup set; the confirmation prepares it for the next restart. */
    fun rollbackSaved(context: CommandContext, restoreConfigurations: Boolean = false) {
        val reply = context.sender
        if (!replies.ready(reply)) return
        val native = (reply as? BukkitCommandSender)?.native ?: return
        val id = runCatching { UUID.fromString(context.get<String>("transaction")) }.getOrNull()
            ?: return replies.say(reply, ChatTone.FAILURE, "Некорректный ID резервного набора.")
        val action = if (restoreConfigurations) UpdateAction.ROLLBACK_CONFIGS else UpdateAction.ROLLBACK
        askToConfirm(
            native, reply, id, 0, action,
            "Откат к резервному набору",
            listOf(
                if (restoreConfigurations) "Вернутся сохранённые JAR и выбранные конфигурации." else "Вернутся сохранённые JAR, конфигурации не изменятся.",
                "Применится после полного перезапуска сервера.",
            ),
            "[Подтвердить откат]",
        )
    }

    /** Shows the confirmation card for [action] with a fresh token and the command that redeems it. */
    private fun askToConfirm(
        native: CommandSender,
        reply: Reply,
        plan: UUID,
        revision: Long,
        action: UpdateAction,
        title: String,
        lines: List<String>,
        label: String,
    ) {
        val token = tokens.issue(identityOf(native), plan, revision, action, CONFIRMATION_TIME)
        replies.confirm(reply, title, lines, label, BukkitControlCommand.confirmCommand(token))
    }

    /** A player's own id, or one fixed id for the console, so that both get tokens only they can use. */
    private fun identityOf(sender: CommandSender): UUID = (sender as? Player)?.uniqueId ?: CONSOLE_ID

    // --- status ------------------------------------------------------------------------------------------------

    /** The detailed console card, or the server state card styled like the catalog. */
    fun sendStatus(sender: CommandSender, reply: Reply, requested: String?) {
        val entries = if (requested == null) library.updates.all() else listOfNotNull(library.updates.get(requested))
        if (sender is ConsoleCommandSender && requested == null) {
            BukkitConsoleStatusRenderer.render(entries).forEach(sender::sendMessage)
            return
        }
        if (requested != null && entries.isEmpty()) return replies.notRegistered(reply, requested)
        val facts = listOf(
            "Ядро" to "${Bukkit.getName()} ${Bukkit.getBukkitVersion()}",
            "Java" to BukkitStatusLabels.javaRuntime(),
            PnLibraryIdentity.NAME to library.version.toString(),
        )
        UpdateCatalogView.status(facts, entries.map { it.snapshot }, replies.routes(reply), PnLibraryIdentity.SUPPORT_URL).forEach(reply::send)
    }

    /** Reports the current plan for all or one registered product. */
    fun sendUpdateStatus(reply: Reply, requested: String?) {
        val plan = library.updates.currentPlan().orElse(null)
            ?: return replies.say(reply, ChatTone.WARNING, "Проверка обновлений ещё не завершалась.", "Запустите её командой /pn check.")
        val registrations = if (requested == null) library.updates.all() else listOfNotNull(library.updates.get(requested))
        if (registrations.isEmpty()) return replies.notRegistered(reply, requested ?: "с указанным именем")
        replies.notices(reply, registrations.flatMap { BukkitUpdateMessages.status(plan to it.snapshot) })
    }

    // --- update ------------------------------------------------------------------------------------------------

    /** Validates download permissions and selects the requested update registration. */
    fun update(sender: CommandSender, reply: Reply, name: String) {
        if (!sender.hasPermission("pnlibrary.updates.download")) {
            return replies.say(reply, ChatTone.FAILURE, "Недостаточно прав для загрузки обновлений.")
        }
        val registration = library.updates.get(name) ?: return replies.notRegistered(reply, name)
        checkAndInstallUpdate(sender, reply, registration)
    }

    /** Checks the update plan with progress messages and a bounded user-facing timeout. */
    private fun checkAndInstallUpdate(sender: CommandSender, reply: Reply, registration: UpdateRegistration) {
        val product = registration.snapshot.product
        replies.say(reply, ChatTone.PROGRESS, "Ищу совместимое обновление $product…")
        val finished = AtomicBoolean(false)
        fun scheduleProgress() {
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (finished.get()) return@Runnable
                replies.say(reply, ChatTone.PROGRESS, "Проверка ещё идёт: жду ответ каталога релизов…")
                scheduleProgress()
            }, PROGRESS_TICKS)
        }
        scheduleProgress()
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (finished.compareAndSet(false, true)) {
                replies.say(reply, ChatTone.FAILURE, "Проверка прервана: источники не ответили за 60 секунд.",
                    "Проверьте доступ сервера к raw.githubusercontent.com и повторите команду.")
            }
        }, TIMEOUT_TICKS)
        library.updates.checkNow(replies.updateActor(sender)).whenComplete { result, error ->
            if (!finished.compareAndSet(false, true)) return@whenComplete
            replies.onServerThread {
                if (error != null) replies.fail(reply, "Проверка обновления не удалась", error)
                else handleUpdateCheck(sender, reply, registration, result)
            }
        }
    }

    /** Stages the requested product change or reports its current update state. */
    private fun handleUpdateCheck(sender: CommandSender, reply: Reply, registration: UpdateRegistration, result: UpdatePlanSnapshot) {
        val change = result.plan?.changes?.firstOrNull {
            it.product.value.equals(registration.snapshot.product, ignoreCase = true)
        }
        if (result.state != UpdateState.UPDATE_AVAILABLE || change == null) {
            replies.notices(reply, BukkitUpdateMessages.status(result to registration.snapshot))
            return
        }
        stageUpdate(sender, reply, result, change)
    }

    /** Downloads and validates the selected compatible update plan. */
    private fun stageUpdate(sender: CommandSender, reply: Reply, plan: UpdatePlanSnapshot, change: ProductChange) {
        val transition = "${change.product} v${change.from} » v${change.to}"
        replies.say(reply, ChatTone.PROGRESS, "Найдено $transition. Скачиваю и проверяю…")
        library.updates.stage(plan.id, replies.updateActor(sender)).whenComplete { _, error ->
            replies.onServerThread {
                if (error != null) replies.fail(reply, "Не удалось подготовить обновление", error)
                else replies.say(reply, ChatTone.SUCCESS, "Обновление $transition подготовлено.", "Перезапустите сервер, чтобы применить его.")
            }
        }
    }

    // --- rollback, restart and the confirmation ------------------------------------------------------------------

    /** Asks to confirm a rollback of the whole current update plan. */
    fun rollbackUpdate(sender: CommandSender, reply: Reply) {
        if (!sender.hasPermission("pnlibrary.updates.rollback")) {
            return replies.say(reply, ChatTone.FAILURE, "Недостаточно прав для отката обновлений.")
        }
        val plan = library.updates.currentPlan().orElse(null)
            ?: return replies.say(reply, ChatTone.WARNING, "Нет плана обновления, который можно откатить.")
        askToConfirm(
            sender, reply, plan.id, plan.revision, UpdateAction.PLAN_ROLLBACK,
            "Откат обновления",
            listOf("Откатится весь связанный план обновления, а не один плагин.", "Предыдущие JAR применятся после перезапуска."),
            "[Подтвердить откат]",
        )
    }

    /** Asks to confirm the restart that applies the staged plan. */
    fun restartUpdate(sender: CommandSender, reply: Reply) {
        if (!sender.hasPermission("pnlibrary.updates.restart")) {
            return replies.say(reply, ChatTone.FAILURE, "Недостаточно прав для перезапуска.")
        }
        val plan = library.updates.currentPlan().orElse(null)
        if (plan == null || plan.state != UpdateState.UPDATE_STAGED) {
            return replies.say(reply, ChatTone.WARNING, "Нет подготовленных обновлений, которым нужен перезапуск.")
        }
        if (sender !is Player) {
            return replies.say(reply, ChatTone.WARNING, "Из консоли перезапускайте сервер его обычной командой.")
        }
        askToConfirm(
            sender, reply, plan.id, plan.revision, UpdateAction.RESTART,
            "Перезапуск сервера",
            listOf("Игроков на сервере: ${Bukkit.getOnlinePlayers().size}.", "Подготовленные обновления применятся после перезапуска."),
            "[Подтвердить перезапуск]",
        )
    }

    /** Runs what [token] grants: a rollback of the plan, a rollback to a saved backup set, or the restart. */
    fun confirm(sender: CommandSender, reply: Reply, token: String) {
        val grant = tokens.redeem(token, identityOf(sender))
            ?: return replies.say(reply, ChatTone.FAILURE, "Подтверждение недействительно, истекло или уже использовано.")
        when (grant.action) {
            UpdateAction.PLAN_ROLLBACK -> runPlanRollback(sender, reply, grant)
            UpdateAction.RESTART -> runRestart(sender, reply, grant)
            UpdateAction.ROLLBACK -> runSavedRollback(sender, reply, grant.plan, restoreConfigurations = false)
            UpdateAction.ROLLBACK_CONFIGS -> runSavedRollback(sender, reply, grant.plan, restoreConfigurations = true)
        }
    }

    private fun planIsStill(grant: UpdateConfirmationTokens.Grant): Boolean =
        library.updates.currentPlan().orElse(null)?.let { it.id == grant.plan && it.revision == grant.revision } == true

    private fun runPlanRollback(sender: CommandSender, reply: Reply, grant: UpdateConfirmationTokens.Grant) {
        if (!sender.hasPermission("pnlibrary.updates.rollback")) {
            return replies.say(reply, ChatTone.FAILURE, "Недостаточно прав для отката обновлений.")
        }
        if (!planIsStill(grant)) return replies.say(reply, ChatTone.FAILURE, "План обновления изменился; запросите откат заново.")
        library.updates.rollback(replies.updateActor(sender)).whenComplete { _, error ->
            replies.onServerThread {
                if (error != null) replies.fail(reply, "Не удалось подготовить откат", error)
                else replies.say(reply, ChatTone.SUCCESS, "Предыдущие версии плагинов подготовлены.", "Полностью перезапустите сервер.")
            }
        }
    }

    private fun runSavedRollback(sender: CommandSender, reply: Reply, id: UUID, restoreConfigurations: Boolean) {
        if (!sender.hasPermission("pnlibrary.updates.rollback")) {
            return replies.say(reply, ChatTone.FAILURE, "Недостаточно прав для отката обновлений.")
        }
        library.updates.rollback(id, restoreConfigurations, replies.updateActor(sender)).whenComplete { _, error ->
            replies.onServerThread {
                if (error != null) replies.fail(reply, "Не удалось подготовить откат", error)
                else replies.say(reply, ChatTone.SUCCESS, "Резервный набор подготовлен.", "Полностью перезапустите сервер, чтобы применить его.")
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun runRestart(sender: CommandSender, reply: Reply, grant: UpdateConfirmationTokens.Grant) {
        if (!sender.hasPermission("pnlibrary.updates.restart")) {
            return replies.say(reply, ChatTone.FAILURE, "Недостаточно прав для перезапуска.")
        }
        if (!planIsStill(grant)) return replies.say(reply, ChatTone.FAILURE, "План обновления изменился; запросите перезапуск заново.")
        Bukkit.broadcastMessage(RESTART_NOTICE)
        Bukkit.getScheduler().runTaskLater(plugin, Runnable { Bukkit.spigot().restart() }, RESTART_DELAY_TICKS)
    }

    private companion object {
        /** The chat-wide restart warning in the palette; the broadcast API takes legacy text on every version. */
        val RESTART_NOTICE: String = LegacyComponentSerializer.legacySection().serialize(
            Component.text("[pnFolder] ", ChatPalette.ACCENT)
                .append(Component.text("Сервер перезапускается для применения обновлений.", ChatPalette.TEXT)),
        )

        val CONFIRMATION_TIME: Duration = Duration.ofSeconds(30)

        /** The identity the console's tokens are bound to. */
        val CONSOLE_ID: UUID = UUID(0L, 0L)
        const val PROGRESS_TICKS = 100L
        const val TIMEOUT_TICKS = 1_200L
        const val RESTART_DELAY_TICKS = 40L
    }
}
