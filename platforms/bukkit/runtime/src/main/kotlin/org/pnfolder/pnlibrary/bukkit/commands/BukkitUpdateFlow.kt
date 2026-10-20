package org.pnfolder.pnlibrary.bukkit.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.api.updates.ProductChange
import org.pnfolder.pnlibrary.api.updates.UpdatePlanSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.bukkit.updates.UpdateAction
import org.pnfolder.pnlibrary.bukkit.updates.UpdateConfirmationTokens
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogCommands
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogView
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import org.pnfolder.pnlibrary.api.commands.CommandSender as Reply

/**
 * The `/pn` update flow: status, check, stage, confirm, rollback, and restart.
 *
 * Destructive steps require a player-bound confirmation token; asynchronous results are delivered on the server thread.
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

    /** Asks to confirm, then prepares exactly the saved transaction for the next restart. */
    fun rollbackSaved(context: CommandContext, restoreConfigurations: Boolean = false) {
        val reply = context.sender
        if (!replies.ready(reply)) return
        val native = (reply as? BukkitCommandSender)?.native ?: return
        val id = runCatching { UUID.fromString(context.get<String>("transaction")) }.getOrNull()
            ?: return replies.say(reply, ChatTone.FAILURE, "Некорректный ID резервного набора.")
        val confirmation = if (context.contains("confirmation")) context.get<String>("confirmation") else null
        val action = if (restoreConfigurations) UpdateAction.ROLLBACK_CONFIGS else UpdateAction.ROLLBACK
        if (confirmation == null) {
            val token = if (native is Player) tokens.issue(native.uniqueId, id, 0, action, CONFIRMATION_TIME) else "confirm"
            replies.confirm(
                reply,
                "Откат к резервному набору",
                listOf(
                    if (restoreConfigurations) "Вернутся сохранённые JAR и выбранные конфигурации." else "Вернутся сохранённые JAR, конфигурации не изменятся.",
                    "Применится после полного перезапуска сервера.",
                ),
                "[Подтвердить откат]",
                BukkitControlCommand.ROUTES.rollback(id, restoreConfigurations) + " $token",
            )
            return
        }
        val confirmed = if (native is Player) tokens.consume(confirmation, native.uniqueId, id, 0, action)
            else reply.isConsole && confirmation == "confirm"
        if (!confirmed) return replies.say(reply, ChatTone.FAILURE, "Подтверждение отката недействительно или истекло.")
        library.updates.rollback(id, restoreConfigurations, replies.updateActor(native)).whenComplete { _, error ->
            replies.onServerThread {
                if (error != null) replies.fail(reply, "Не удалось подготовить откат", error)
                else replies.say(reply, ChatTone.SUCCESS, "Резервный набор подготовлен.", "Полностью перезапустите сервер, чтобы применить его.")
            }
        }
    }

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
            "pnLibrary" to library.version.toString(),
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

    /** Consumes a player-bound confirmation before staging the update plan. */
    fun confirmUpdate(sender: CommandSender, reply: Reply, token: String) {
        if (sender !is Player || !sender.hasPermission("pnlibrary.updates.download")) {
            return replies.say(reply, ChatTone.FAILURE, "Это подтверждение недоступно.")
        }
        val plan = library.updates.currentPlan().orElse(null)
        if (plan == null || !tokens.consume(token, sender.uniqueId, plan.id, plan.revision, UpdateAction.DOWNLOAD)) {
            return replies.say(reply, ChatTone.FAILURE, "Подтверждение устарело или уже использовано.")
        }
        library.updates.stage(plan.id, replies.updateActor(sender)).whenComplete { staged, error ->
            replies.onServerThread {
                if (error != null) replies.fail(reply, "Не удалось подготовить обновление", error)
                else replies.say(reply, ChatTone.SUCCESS, "План обновления проверен и подготовлен.", "План: ${staged.id}")
            }
        }
    }

    // --- rollback and restart ----------------------------------------------------------------------------------

    /** Validates permissions and confirmation before staging a rollback of the whole current plan. */
    fun rollbackUpdate(sender: CommandSender, reply: Reply, token: String?) {
        if (!sender.hasPermission("pnlibrary.updates.rollback")) {
            return replies.say(reply, ChatTone.FAILURE, "Недостаточно прав для отката обновлений.")
        }
        val plan = library.updates.currentPlan().orElse(null)
            ?: return replies.say(reply, ChatTone.WARNING, "Нет плана обновления, который можно откатить.")
        if (sender is Player && token == null) {
            val issued = tokens.issue(sender.uniqueId, plan.id, plan.revision, UpdateAction.ROLLBACK, CONFIRMATION_TIME)
            replies.confirm(
                reply,
                "Откат обновления",
                listOf("Откатится весь связанный план обновления, а не один плагин.", "Предыдущие JAR применятся после перезапуска."),
                "[Подтвердить откат]",
                "/pn update-rollback $issued",
            )
            return
        }
        if (sender is Player && !tokens.consume(token.orEmpty(), sender.uniqueId, plan.id, plan.revision, UpdateAction.ROLLBACK)) {
            return replies.say(reply, ChatTone.FAILURE, "Подтверждение отката истекло или уже использовано.")
        }
        library.updates.rollback(replies.updateActor(sender)).whenComplete { _, error ->
            replies.onServerThread {
                if (error != null) replies.fail(reply, "Не удалось подготовить откат", error)
                else replies.say(reply, ChatTone.SUCCESS, "Предыдущие версии плагинов подготовлены.", "Полностью перезапустите сервер.")
            }
        }
    }

    /** Validates the staged plan and a player confirmation before requesting restart. */
    @Suppress("DEPRECATION")
    fun handleRestart(sender: CommandSender, reply: Reply, supplied: String?) {
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
        if (supplied == null) {
            val token = tokens.issue(sender.uniqueId, plan.id, plan.revision, UpdateAction.RESTART, CONFIRMATION_TIME)
            replies.confirm(
                reply,
                "Перезапуск сервера",
                listOf("Игроков на сервере: ${Bukkit.getOnlinePlayers().size}.", "Подготовленные обновления применятся после перезапуска."),
                "[Подтвердить перезапуск]",
                "/pn restart $token",
            )
            return
        }
        if (!tokens.consume(supplied, sender.uniqueId, plan.id, plan.revision, UpdateAction.RESTART)) {
            return replies.say(reply, ChatTone.FAILURE, "Подтверждение истекло. Выполните /pn restart ещё раз.")
        }
        if (library.updates.currentPlan().orElse(null)?.let { it.id != plan.id || it.revision != plan.revision } != false) {
            return replies.say(reply, ChatTone.FAILURE, "План обновления изменился; подтвердите заново.")
        }
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
        const val PROGRESS_TICKS = 100L
        const val TIMEOUT_TICKS = 1_200L
        const val RESTART_DELAY_TICKS = 40L
    }
}
