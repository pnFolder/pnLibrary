package ru.privatenull.pnlibrary.bukkit.commands

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.api.commands.ArgumentType
import ru.privatenull.pnlibrary.api.commands.CommandContext
import ru.privatenull.pnlibrary.api.commands.CommandDefinition
import ru.privatenull.pnlibrary.api.commands.command
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.api.runtime.PnLibraryBrand
import ru.privatenull.pnlibrary.api.updates.ProductChange
import ru.privatenull.pnlibrary.api.updates.UpdateActor
import ru.privatenull.pnlibrary.api.updates.UpdatePlanSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateRegistration
import ru.privatenull.pnlibrary.api.updates.UpdateState
import ru.privatenull.pnlibrary.bukkit.updates.UpdateAction
import ru.privatenull.pnlibrary.bukkit.updates.UpdateConfirmationTokens
import ru.privatenull.pnlibrary.core.ui.ChatNotice
import ru.privatenull.pnlibrary.core.ui.ChatTone
import ru.privatenull.pnlibrary.core.updates.catalog.UpdateCatalogCommands
import ru.privatenull.pnlibrary.core.updates.catalog.UpdateCatalogRoutes
import ru.privatenull.pnlibrary.core.updates.catalog.UpdateCatalogView
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import ru.privatenull.pnlibrary.api.commands.CommandSender as Reply

/**
 * Bukkit-only `/pn` behavior expressed through the shared command builder.
 *
 * Every reply is rendered by [UpdateCatalogView], so the status card, update progress and confirmations look like
 * the catalog. The native sender is used only for permissions, identity and player-bound confirmations.
 */
internal class BukkitControlCommand(
    private val plugin: Plugin,
    private val library: PnLibrary,
) {
    private val tokens = UpdateConfirmationTokens()
    private val testErrors = BukkitDiagnosticTestErrors(plugin, library)
    private val feedback = BukkitSettingFeedback(plugin)
    private val catalog = UpdateCatalogCommands(
        library = library,
        routes = ROUTES,
        viewPermission = "pnlibrary.updates.view",
        backupsPermission = "pnlibrary.updates.rollback",
        managePermission = "pnlibrary.updates.settings",
        deliver = { task -> if (!library.isClosed) plugin.server.scheduler.runTask(plugin, task) },
        onSaved = { context, saved -> (context.sender as? BukkitCommandSender)?.native?.let { it as? Player }?.let { feedback.show(it, saved) } },
    )

    /** Builds the permission-aware control command tree. */
    fun definition(): CommandDefinition = command("pn") {
        permission("pnlibrary.admin")
        executes(catalog::showList)
        literal("status") {
            executes { native(it) { sender, reply -> sendStatus(sender, reply, null) } }
            argument("plugin", ArgumentType.string()) {
                suggests { productNames() }
                executes { native(it) { sender, reply -> sendStatus(sender, reply, it.get<String>("plugin")) } }
            }
        }
        literal("updates") {
            executes(catalog::showList)
            catalog.install { name, configure -> literal(name, configure) }
            literal("rollback") {
                permission("pnlibrary.updates.rollback")
                argument("transaction", ArgumentType.string()) {
                    executes { rollbackSaved(it) }
                    argument("confirmation", ArgumentType.string()) { executes { rollbackSaved(it) } }
                    literal("configs") {
                        executes { rollbackSaved(it, true) }
                        argument("confirmation", ArgumentType.string()) { executes { rollbackSaved(it, true) } }
                    }
                }
            }
        }
        literal("check") { executes(::checkUpdates) }
        literal("update") {
            argument("plugin", ArgumentType.string()) {
                suggests { productNames() }
                executes { native(it) { sender, reply -> update(sender, reply, it.get<String>("plugin")) } }
            }
        }
        literal("update-confirm") {
            argument("token", ArgumentType.string()) {
                executes { native(it) { sender, reply -> confirmUpdate(sender, reply, it.get<String>("token")) } }
            }
        }
        literal("update-status") {
            executes { native(it) { _, reply -> sendUpdateStatus(reply, null) } }
            argument("plugin", ArgumentType.string()) {
                suggests { productNames() }
                executes { native(it) { _, reply -> sendUpdateStatus(reply, it.get<String>("plugin")) } }
            }
        }
        literal("update-rollback") {
            executes { native(it) { sender, reply -> rollbackUpdate(sender, reply, null) } }
            argument("token", ArgumentType.string()) {
                executes { native(it) { sender, reply -> rollbackUpdate(sender, reply, it.get<String>("token")) } }
            }
        }
        literal("restart") {
            executes { native(it) { sender, reply -> handleRestart(sender, reply, null) } }
            argument("token", ArgumentType.string()) {
                executes { native(it) { sender, reply -> handleRestart(sender, reply, it.get<String>("token")) } }
            }
        }
        literal("debug") {
            executes { native(it) { _, reply -> say(reply, ChatTone.INFO, "Отчёт для поддержки: /pndebug [all|plugin] [--full|--config|--logs]") } }
        }
        literal("support") {
            executes { native(it) { _, reply -> say(reply, ChatTone.INFO, "Поддержка pnFolder: ${PnLibraryBrand.SUPPORT_URL}") } }
        }
        literal("error") { executes { native(it) { _, reply -> testErrors.unique(reported(reply)) } } }
        literal("error-repeat") {
            executes { native(it) { _, reply -> testErrors.repeated(reported(reply), null) } }
            argument("count", ArgumentType.integer()) {
                suggests { REPEAT_COUNTS }
                executes { native(it) { _, reply -> testErrors.repeated(reported(reply), it.get<Int>("count")) } }
            }
        }
        literal("error-chain") { executes { native(it) { _, reply -> testErrors.chained(reported(reply)) } } }
    }

    // --- catalog -----------------------------------------------------------------------------------------------

    /** Replies immediately, then shows the refreshed list of updates on the server thread. */
    private fun checkUpdates(context: CommandContext) {
        if (!ready(context.sender)) return
        val native = (context.sender as? BukkitCommandSender)?.native ?: return
        say(context.sender, ChatTone.PROGRESS, "Проверяю обновления…")
        library.updates.checkNow(updateActor(native)).whenComplete { _, error ->
            onServerThread {
                if (error != null) fail(context.sender, "Не удалось проверить обновления", error)
                else catalog.showAvailable(context)
            }
        }
    }

    /** Asks to confirm, then prepares exactly the saved transaction for the next restart. */
    private fun rollbackSaved(context: CommandContext, restoreConfigurations: Boolean = false) {
        val reply = context.sender
        if (!ready(reply)) return
        val native = (reply as? BukkitCommandSender)?.native ?: return
        val id = runCatching { UUID.fromString(context.get<String>("transaction")) }.getOrNull()
            ?: return say(reply, ChatTone.FAILURE, "Некорректный ID резервного набора.")
        val confirmation = if (context.contains("confirmation")) context.get<String>("confirmation") else null
        val action = if (restoreConfigurations) UpdateAction.ROLLBACK_CONFIGS else UpdateAction.ROLLBACK
        if (confirmation == null) {
            val token = if (native is Player) tokens.issue(native.uniqueId, id, 0, action, CONFIRMATION_TIME) else "confirm"
            confirm(
                reply,
                "Откат к резервному набору",
                listOf(
                    if (restoreConfigurations) "Вернутся сохранённые JAR и выбранные конфигурации." else "Вернутся сохранённые JAR, конфигурации не изменятся.",
                    "Применится после полного перезапуска сервера.",
                ),
                "[Подтвердить откат]",
                ROUTES.rollback(id, restoreConfigurations) + " $token",
            )
            return
        }
        val confirmed = if (native is Player) tokens.consume(confirmation, native.uniqueId, id, 0, action)
            else reply.isConsole && confirmation == "confirm"
        if (!confirmed) return say(reply, ChatTone.FAILURE, "Подтверждение отката недействительно или истекло.")
        library.updates.rollback(id, restoreConfigurations, updateActor(native)).whenComplete { _, error ->
            onServerThread {
                if (error != null) fail(reply, "Не удалось подготовить откат", error)
                else say(reply, ChatTone.SUCCESS, "Резервный набор подготовлен.", "Полностью перезапустите сервер, чтобы применить его.")
            }
        }
    }

    // --- status ------------------------------------------------------------------------------------------------

    /** The detailed console card, or the server state card styled like the catalog. */
    private fun sendStatus(sender: CommandSender, reply: Reply, requested: String?) {
        val entries = if (requested == null) library.updates.all() else listOfNotNull(library.updates.get(requested))
        if (sender is ConsoleCommandSender && requested == null) {
            BukkitConsoleStatusRenderer.render(entries).forEach(sender::sendMessage)
            return
        }
        if (requested != null && entries.isEmpty()) return notRegistered(reply, requested)
        val facts = listOf(
            "Ядро" to "${Bukkit.getName()} ${Bukkit.getBukkitVersion()}",
            "Java" to BukkitStatusLabels.javaRuntime(),
            "pnLibrary" to library.version.toString(),
        )
        UpdateCatalogView.status(facts, entries.map { it.snapshot }, routes(reply), PnLibraryBrand.SUPPORT_URL).forEach(reply::send)
    }

    /** Reports the current plan for all or one registered product. */
    private fun sendUpdateStatus(reply: Reply, requested: String?) {
        val plan = library.updates.currentPlan().orElse(null)
            ?: return say(reply, ChatTone.WARNING, "Проверка обновлений ещё не завершалась.", "Запустите её командой /pn check.")
        val registrations = if (requested == null) library.updates.all() else listOfNotNull(library.updates.get(requested))
        if (registrations.isEmpty()) return notRegistered(reply, requested ?: "с указанным именем")
        notices(reply, registrations.flatMap { BukkitUpdateMessages.status(plan to it.snapshot) })
    }

    // --- update ------------------------------------------------------------------------------------------------

    /** Validates download permissions and selects the requested update registration. */
    private fun update(sender: CommandSender, reply: Reply, name: String) {
        if (!sender.hasPermission("pnlibrary.updates.download")) {
            return say(reply, ChatTone.FAILURE, "Недостаточно прав для загрузки обновлений.")
        }
        val registration = library.updates.get(name) ?: return notRegistered(reply, name)
        checkAndInstallUpdate(sender, reply, registration)
    }

    /** Checks the update plan with progress messages and a bounded user-facing timeout. */
    private fun checkAndInstallUpdate(sender: CommandSender, reply: Reply, registration: UpdateRegistration) {
        val product = registration.snapshot.product
        say(reply, ChatTone.PROGRESS, "Ищу совместимое обновление $product…")
        val finished = AtomicBoolean(false)
        fun scheduleProgress() {
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (finished.get()) return@Runnable
                say(reply, ChatTone.PROGRESS, "Проверка ещё идёт: жду ответ каталога релизов…")
                scheduleProgress()
            }, PROGRESS_TICKS)
        }
        scheduleProgress()
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (finished.compareAndSet(false, true)) {
                say(reply, ChatTone.FAILURE, "Проверка прервана: источники не ответили за 60 секунд.",
                    "Проверьте доступ сервера к raw.githubusercontent.com и повторите команду.")
            }
        }, TIMEOUT_TICKS)
        library.updates.checkNow(updateActor(sender)).whenComplete { result, error ->
            if (!finished.compareAndSet(false, true)) return@whenComplete
            onServerThread {
                if (error != null) fail(reply, "Проверка обновления не удалась", error)
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
            notices(reply, BukkitUpdateMessages.status(result to registration.snapshot))
            return
        }
        stageUpdate(sender, reply, result, change)
    }

    /** Downloads and validates the selected compatible update plan. */
    private fun stageUpdate(sender: CommandSender, reply: Reply, plan: UpdatePlanSnapshot, change: ProductChange) {
        val transition = "${change.product} v${change.from} » v${change.to}"
        say(reply, ChatTone.PROGRESS, "Найдено $transition. Скачиваю и проверяю…")
        library.updates.stage(plan.id, updateActor(sender)).whenComplete { _, error ->
            onServerThread {
                if (error != null) fail(reply, "Не удалось подготовить обновление", error)
                else say(reply, ChatTone.SUCCESS, "Обновление $transition подготовлено.", "Перезапустите сервер, чтобы применить его.")
            }
        }
    }

    /** Consumes a player-bound confirmation before staging the update plan. */
    private fun confirmUpdate(sender: CommandSender, reply: Reply, token: String) {
        if (sender !is Player || !sender.hasPermission("pnlibrary.updates.download")) {
            return say(reply, ChatTone.FAILURE, "Это подтверждение недоступно.")
        }
        val plan = library.updates.currentPlan().orElse(null)
        if (plan == null || !tokens.consume(token, sender.uniqueId, plan.id, plan.revision, UpdateAction.DOWNLOAD)) {
            return say(reply, ChatTone.FAILURE, "Подтверждение устарело или уже использовано.")
        }
        library.updates.stage(plan.id, updateActor(sender)).whenComplete { staged, error ->
            onServerThread {
                if (error != null) fail(reply, "Не удалось подготовить обновление", error)
                else say(reply, ChatTone.SUCCESS, "План обновления проверен и подготовлен.", "План: ${staged.id}")
            }
        }
    }

    // --- rollback and restart ----------------------------------------------------------------------------------

    /** Validates permissions and confirmation before staging a rollback of the whole current plan. */
    private fun rollbackUpdate(sender: CommandSender, reply: Reply, token: String?) {
        if (!sender.hasPermission("pnlibrary.updates.rollback")) {
            return say(reply, ChatTone.FAILURE, "Недостаточно прав для отката обновлений.")
        }
        val plan = library.updates.currentPlan().orElse(null)
            ?: return say(reply, ChatTone.WARNING, "Нет плана обновления, который можно откатить.")
        if (sender is Player && token == null) {
            val issued = tokens.issue(sender.uniqueId, plan.id, plan.revision, UpdateAction.ROLLBACK, CONFIRMATION_TIME)
            confirm(
                reply,
                "Откат обновления",
                listOf("Откатится весь связанный план обновления, а не один плагин.", "Предыдущие JAR применятся после перезапуска."),
                "[Подтвердить откат]",
                "/pn update-rollback $issued",
            )
            return
        }
        if (sender is Player && !tokens.consume(token.orEmpty(), sender.uniqueId, plan.id, plan.revision, UpdateAction.ROLLBACK)) {
            return say(reply, ChatTone.FAILURE, "Подтверждение отката истекло или уже использовано.")
        }
        library.updates.rollback(updateActor(sender)).whenComplete { _, error ->
            onServerThread {
                if (error != null) fail(reply, "Не удалось подготовить откат", error)
                else say(reply, ChatTone.SUCCESS, "Предыдущие версии плагинов подготовлены.", "Полностью перезапустите сервер.")
            }
        }
    }

    /** Validates the staged plan and a player confirmation before requesting restart. */
    @Suppress("DEPRECATION")
    private fun handleRestart(sender: CommandSender, reply: Reply, supplied: String?) {
        if (!sender.hasPermission("pnlibrary.updates.restart")) {
            return say(reply, ChatTone.FAILURE, "Недостаточно прав для перезапуска.")
        }
        val plan = library.updates.currentPlan().orElse(null)
        if (plan == null || plan.state != UpdateState.UPDATE_STAGED) {
            return say(reply, ChatTone.WARNING, "Нет подготовленных обновлений, которым нужен перезапуск.")
        }
        if (sender !is Player) {
            return say(reply, ChatTone.WARNING, "Из консоли перезапускайте сервер его обычной командой.")
        }
        if (supplied == null) {
            val token = tokens.issue(sender.uniqueId, plan.id, plan.revision, UpdateAction.RESTART, CONFIRMATION_TIME)
            confirm(
                reply,
                "Перезапуск сервера",
                listOf("Игроков на сервере: ${Bukkit.getOnlinePlayers().size}.", "Подготовленные обновления применятся после перезапуска."),
                "[Подтвердить перезапуск]",
                "/pn restart $token",
            )
            return
        }
        if (!tokens.consume(supplied, sender.uniqueId, plan.id, plan.revision, UpdateAction.RESTART)) {
            return say(reply, ChatTone.FAILURE, "Подтверждение истекло. Выполните /pn restart ещё раз.")
        }
        if (library.updates.currentPlan().orElse(null)?.let { it.id != plan.id || it.revision != plan.revision } != false) {
            return say(reply, ChatTone.FAILURE, "План обновления изменился; подтвердите заново.")
        }
        Bukkit.broadcastMessage("§e[pnFolder] §fСервер перезапускается для применения обновлений.")
        Bukkit.getScheduler().runTaskLater(plugin, Runnable { Bukkit.spigot().restart() }, RESTART_DELAY_TICKS)
    }

    // --- replies -----------------------------------------------------------------------------------------------

    /** Routes for this reader: buttons only when it can click, ASCII for the console. */
    private fun routes(reply: Reply): UpdateCatalogRoutes =
        ROUTES.copy(interactive = reply.supportsInteraction, ascii = reply.isConsole)

    private fun say(reply: Reply, tone: ChatTone, text: String, detail: String? = null) =
        notices(reply, listOf(ChatNotice(tone, text, detail)))

    private fun notices(reply: Reply, notices: List<ChatNotice>) =
        UpdateCatalogView.notices(notices, routes(reply)).forEach(reply::send)

    private fun confirm(reply: Reply, title: String, lines: List<String>, label: String, command: String) =
        UpdateCatalogView.confirmation(title, lines + "Подтверждение действует 30 секунд.", label, command,
            "Выполнить: $command", routes(reply)).forEach(reply::send)

    private fun reported(reply: Reply): (String) -> Unit = { text -> say(reply, ChatTone.SUCCESS, text) }

    private fun notRegistered(reply: Reply, product: String) =
        say(reply, ChatTone.FAILURE, "Плагин $product не зарегистрирован в pnLibrary.")

    /** Reports the root cause; a missing release file gets an explanation instead of a bare HTTP code. */
    private fun fail(reply: Reply, title: String, error: Throwable) {
        val cause = generateSequence(error) { it.cause }.last()
        val message = cause.message ?: cause.javaClass.simpleName
        val detail = if (message.contains("HTTP 404", ignoreCase = true)) {
            "Файл обновления не найден (HTTP 404): проверьте ссылку в каталоге релизов."
        } else message
        say(reply, ChatTone.FAILURE, "$title.", detail)
    }

    private fun ready(reply: Reply): Boolean {
        if (!library.isClosed) return true
        say(reply, ChatTone.FAILURE, "pnLibrary ещё не готова. Дождитесь полного запуска сервера.")
        return false
    }

    /** Unwraps the native sender and runs [action] with it and the reply channel, unless the runtime is closed. */
    private fun native(context: CommandContext, action: (CommandSender, Reply) -> Unit) {
        val sender = (context.sender as? BukkitCommandSender)?.native
        when {
            sender == null -> say(context.sender, ChatTone.FAILURE, "Эта команда доступна только на Bukkit.")
            ready(context.sender) -> action(sender, context.sender)
        }
    }

    /** Delivers asynchronous results on the server thread while the runtime is open. */
    private fun onServerThread(action: () -> Unit) {
        if (library.isClosed) return
        plugin.server.scheduler.runTask(plugin, Runnable { if (!library.isClosed) action() })
    }

    private fun productNames(): List<String> = library.updates.all().map { it.snapshot.product }

    private fun updateActor(sender: CommandSender): UpdateActor = when (sender) {
        is Player -> UpdateActor(UpdateActor.Kind.PLAYER, sender.uniqueId.toString(), sender.name)
        is ConsoleCommandSender -> UpdateActor(UpdateActor.Kind.CONSOLE, "console", sender.name)
        else -> UpdateActor(UpdateActor.Kind.INTEGRATION, sender.name, sender.name)
    }

    internal companion object {
        /** Catalog routes of the `/pn` tree, shared with the join notice. */
        val ROUTES = UpdateCatalogRoutes("/pn updates", update = { product -> "/pn update $product" }, check = "/pn check")

        private val REPEAT_COUNTS = listOf("10", "100", "1000")
        private val CONFIRMATION_TIME: Duration = Duration.ofSeconds(30)
        private const val PROGRESS_TICKS = 100L
        private const val TIMEOUT_TICKS = 1_200L
        private const val RESTART_DELAY_TICKS = 40L
    }
}
