package ru.privatenull.pnlibrary.bukkit.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.md_5.bungee.api.ChatColor
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.api.commands.CommandDefinition
import ru.privatenull.pnlibrary.api.commands.CommandContext
import ru.privatenull.pnlibrary.api.commands.ArgumentType
import ru.privatenull.pnlibrary.api.commands.command
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.api.runtime.PnLibraryBrand
import ru.privatenull.pnlibrary.api.updates.UpdateSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateState
import ru.privatenull.pnlibrary.api.updates.ProductChange
import ru.privatenull.pnlibrary.api.updates.UpdatePlanSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateRegistration
import ru.privatenull.pnlibrary.api.updates.UpdateActor
import ru.privatenull.pnlibrary.core.updates.UpdateCatalogView
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.time.Duration
import ru.privatenull.pnlibrary.bukkit.updates.UpdateAction
import ru.privatenull.pnlibrary.bukkit.updates.UpdateConfirmationTokens

/** Bukkit-only `/pn` behavior expressed through the shared command builder. */
internal class BukkitControlCommand(
    private val plugin: Plugin,
    private val library: PnLibrary,
) {
    private val tokens = UpdateConfirmationTokens()
    private val testErrors = BukkitDiagnosticTestErrors(plugin, library)

    private fun updateActor(sender: CommandSender): UpdateActor = when (sender) {
        is Player -> UpdateActor(UpdateActor.Kind.PLAYER, sender.uniqueId.toString(), sender.name)
        is ConsoleCommandSender -> UpdateActor(UpdateActor.Kind.CONSOLE, "console", sender.name)
        else -> UpdateActor(UpdateActor.Kind.INTEGRATION, sender.name, sender.name)
    }

    /** Builds the permission-aware control command tree. */
    fun definition(): CommandDefinition = command("pn") {
        permission("pnlibrary.admin")
        executes { showCatalog(it, false) }
        literal("status") {
            executes { native(it) { sender -> sendStatus(sender, null) } }
            argument("plugin", ArgumentType.string()) {
                suggests { library.updates.all().map { it.snapshot.product } }
                executes { native(it) { sender -> sendStatus(sender, it.get<String>("plugin")) } }
            }
        }
        literal("updates") {
            executes { showCatalog(it, false) }
            literal("list") {
                permission("pnlibrary.updates.view")
                executes { showCatalog(it, false) }
                argument("page", ArgumentType.integer()) { executes { showCatalog(it, false) } }
            }
            literal("available") {
                permission("pnlibrary.updates.view")
                executes { showCatalog(it, true) }
                argument("page", ArgumentType.integer()) { executes { showCatalog(it, true) } }
            }
            literal("history") {
                permission("pnlibrary.updates.view")
                executes(::showHistory)
                argument("page", ArgumentType.integer()) { executes(::showHistory) }
            }
            literal("backups") {
                permission("pnlibrary.updates.rollback")
                executes(::showBackups)
                argument("page", ArgumentType.integer()) { executes(::showBackups) }
            }
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
                suggests { library.updates.all().map { it.snapshot.product } }
                executes { native(it) { sender -> update(sender, it.get<String>("plugin")) } }
            }
        }
        literal("update-confirm") {
            argument("token", ArgumentType.string()) {
                executes { native(it) { sender -> confirmUpdate(sender, it.get<String>("token")) } }
            }
        }
        literal("update-status") {
            executes { native(it) { sender -> sendUpdateStatus(sender, null) } }
            argument("plugin", ArgumentType.string()) {
                suggests { library.updates.all().map { it.snapshot.product } }
                executes { native(it) { sender -> sendUpdateStatus(sender, it.get<String>("plugin")) } }
            }
        }
        literal("update-rollback") {
            executes { native(it) { sender -> rollbackUpdate(sender, null) } }
            argument("token", ArgumentType.string()) {
                executes { native(it) { sender -> rollbackUpdate(sender, it.get<String>("token")) } }
            }
        }
        literal("restart") {
            executes { native(it) { sender -> handleRestart(sender, null) } }
            argument("token", ArgumentType.string()) {
                executes { native(it) { sender -> handleRestart(sender, it.get<String>("token")) } }
            }
        }
        literal("debug") {
            executes { native(it) { sender -> sender.sendMessage("§eИспользуйте /pndebug [all|plugin] [--full|--config|--logs]") } }
        }
        literal("support") {
            executes { native(it) { sender -> sender.sendMessage("§eПоддержка pnFolder: §f${PnLibraryBrand.SUPPORT_URL}") } }
        }
        literal("error") { executes { native(it) { sender -> testErrors.unique(sender) } } }
        literal("error-repeat") {
            executes { native(it) { sender -> testErrors.repeated(sender, null) } }
            argument("count", ArgumentType.integer()) {
                suggests { REPEAT_COUNTS }
                executes { native(it) { sender -> testErrors.repeated(sender, it.get<Int>("count")) } }
            }
        }
        literal("error-chain") { executes { native(it) { sender -> testErrors.chained(sender) } } }
    }

    private fun showCatalog(context: CommandContext, updatesOnly: Boolean) {
        if (!ready(context)) return
        val page = requestedPage(context) ?: return
        UpdateCatalogView.catalog(library.updates.all().map { it.snapshot }, page, updatesOnly,
            if (updatesOnly) "/pn updates available" else "/pn updates list").forEach(context.sender::send)
    }

    /** Replies to the invoking sender immediately, then delivers the result on the server thread. */
    private fun checkUpdates(context: CommandContext) {
        if (library.isClosed) {
            context.sender.send(Component.text("pnLibrary не готова.", NamedTextColor.RED))
            return
        }
        val native = (context.sender as? BukkitCommandSender)?.native ?: return
        context.sender.send(Component.text("pnLibrary · Проверяю обновления…", NamedTextColor.YELLOW))
        library.updates.checkNow(updateActor(native)).whenComplete { _, error ->
            if (!library.isClosed) plugin.server.scheduler.runTask(plugin, Runnable {
                if (library.isClosed) return@Runnable
                if (error != null) context.sender.send(Component.text("Не удалось проверить обновления: ${error.message}", NamedTextColor.RED))
                else showCatalog(context, true)
            })
        }
    }

    private fun showBackups(context: CommandContext) {
        if (!ready(context)) return
        val page = requestedPage(context) ?: return
        library.updates.backups().whenComplete { entries, error ->
            if (!library.isClosed) plugin.server.scheduler.runTask(plugin, Runnable {
                if (library.isClosed) return@Runnable
                if (error != null) context.sender.send(Component.text("Не удалось прочитать резервные наборы."))
                else UpdateCatalogView.backups(entries, page, "/pn updates backups").forEach(context.sender::send)
            })
        }
    }

    private fun rollbackSaved(context: CommandContext, restoreConfigurations: Boolean = false) {
        if (!ready(context)) return
        val native = (context.sender as? BukkitCommandSender)?.native ?: return
        val id = runCatching { UUID.fromString(context.get<String>("transaction")) }.getOrNull()
        if (id == null) { context.sender.send(Component.text("Некорректный ID транзакции.")); return }
        val confirmation = if (context.contains("confirmation")) context.get<String>("confirmation") else null
        val action = if (restoreConfigurations) UpdateAction.ROLLBACK_CONFIGS else UpdateAction.ROLLBACK
        if (confirmation == null) {
            val token = if (native is Player) tokens.issue(native.uniqueId, id, 0, action, Duration.ofSeconds(30)) else "confirm"
            val command = "/pn updates rollback $id${if (restoreConfigurations) " configs" else ""} $token"
            context.sender.send(Component.text("Будет восстановлен набор JAR${if (restoreConfigurations) " И выбранные конфигурации через офлайн-установщик" else " без изменения конфигураций"}. Подтвердить: $command")
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(command)))
            return
        }
        val confirmed = if (native is Player) tokens.consume(confirmation, native.uniqueId, id, 0, action)
            else context.sender.isConsole && confirmation == "confirm"
        if (!confirmed) { context.sender.send(Component.text("Подтверждение отката недействительно или истекло.")); return }
        library.updates.rollback(id, restoreConfigurations, updateActor(native)).whenComplete { _, error ->
            if (!library.isClosed) plugin.server.scheduler.runTask(plugin, Runnable {
                if (library.isClosed) return@Runnable
                context.sender.send(Component.text(if (error == null) "Набор $id подготовлен. Полностью перезапустите сервер." else "Не удалось подготовить откат: ${error.message}"))
            })
        }
    }

    private fun showHistory(context: CommandContext) {
        if (!ready(context)) return
        val page = requestedPage(context) ?: return
        library.updates.historyPage(page).whenComplete { entries, error ->
            if (!library.isClosed) plugin.server.scheduler.runTask(plugin, Runnable {
                if (library.isClosed) return@Runnable
                if (error != null) context.sender.send(Component.text("Не удалось прочитать историю обновлений."))
                else UpdateCatalogView.history(entries, page, "/pn updates history").forEach(context.sender::send)
            })
        }
    }

    /** Reads the optional `page` argument, replying with an error and returning null when it is invalid. */
    private fun requestedPage(context: CommandContext): Int? {
        val page = if (context.contains("page")) context.get<Int>("page") else 1
        if (page >= 1) return page
        context.sender.send(Component.text("Номер страницы должен быть положительным."))
        return null
    }

    private fun ready(context: CommandContext): Boolean {
        if (!library.isClosed) return true
        context.sender.send(Component.text("pnLibrary не готова. Дождитесь полного запуска сервера.", NamedTextColor.RED))
        return false
    }

    /** Unwraps the portable sender and runs [action] unless the runtime is already closed. */
    private fun native(context: CommandContext, action: (CommandSender) -> Unit) {
        val sender = (context.sender as? BukkitCommandSender)?.native
        when {
            sender == null -> context.sender.send(Component.text("Unsupported Bukkit sender."))
            library.isClosed -> sender.sendMessage("§cpnLibrary не готова.")
            else -> action(sender)
        }
    }

    /** Chooses console or in-game status output for the requested products. */
    private fun sendStatus(sender: CommandSender, requested: String?) {
        val entries = if (requested == null) {
            library.updates.all()
        } else {
            listOfNotNull(library.updates.get(requested))
        }
        if (sender is ConsoleCommandSender && requested == null) {
            renderConsoleStatus(sender, entries)
            return
        }
        sender.sendMessage("")
        sender.sendMessage("§a «Состояние pnFolder»")
        sender.sendMessage(" §7- §fЯдро: §6${Bukkit.getName()} ${Bukkit.getBukkitVersion()}")
        sender.sendMessage(" §7- §fJava: §6${BukkitStatusLabels.javaRuntime()}")
        sender.sendMessage(" §7- §fpnLibrary: §6${library.version}")
        entries.firstOrNull()?.snapshot?.let { snapshot ->
            sender.sendMessage(" §7- §fКанал: §e${BukkitStatusLabels.channel(snapshot)}")
            snapshot.supportedApi?.let { api -> sender.sendMessage(" §7- §fAPI: §6${api.minimum}–${api.maximum}") }
        }
        if (entries.isEmpty()) {
            sender.sendMessage(" §7- §fПлагины: §7нет зарегистрированных обновлений")
        } else {
            sender.sendMessage(" §7- §fОбновления:")
            entries.forEach { sendUpdateLine(sender, it.snapshot) }
        }
        sender.sendMessage(" §7- §fПоддержка: §e${PnLibraryBrand.SUPPORT_URL}")
        sender.sendMessage("")
    }

    /** Sends the detailed console card without changing in-game formatting. */
    private fun renderConsoleStatus(sender: CommandSender, entries: List<UpdateRegistration>) {
        BukkitConsoleStatusRenderer.render(entries).forEach(sender::sendMessage)
    }

    /** Validates download permissions and selects the requested update registration. */
    private fun update(sender: CommandSender, name: String?) {
        if (!sender.hasPermission("pnlibrary.updates.download")) {
            sender.sendMessage("§cНедостаточно прав для загрузки обновлений.")
            return
        }
        if (name == null) {
            sender.sendMessage("§eИспользование: /pn update <плагин>")
            return
        }
        val registration = library.updates.get(name)
        if (registration == null) {
            sender.sendMessage("§cПлагин $name не зарегистрирован в pnLibrary.")
            return
        }
        checkAndInstallUpdate(sender, registration)
    }

    /** Checks the update plan with progress messages and a bounded user-facing timeout. */
    private fun checkAndInstallUpdate(sender: CommandSender, registration: UpdateRegistration) {
        val product = registration.snapshot.product
        sender.sendMessage("§eПроверяю GitHub и ищу совместимое обновление $product…")
        val finished = AtomicBoolean(false)
        fun scheduleProgress() {
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (finished.get()) return@Runnable
                sender.sendMessage("§7Проверка ещё выполняется: жду ответ GitHub…")
                scheduleProgress()
            }, 100L)
        }
        scheduleProgress()
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (finished.compareAndSet(false, true)) {
                sender.sendMessage("§cПроверка обновления прервана: источники не ответили за 60 секунд.")
                sender.sendMessage("§7Проверьте соединение сервера с raw.githubusercontent.com и повторите команду.")
            }
        }, 1200L)
        library.updates.checkNow(updateActor(sender)).whenComplete { result, error ->
            if (!finished.compareAndSet(false, true)) return@whenComplete
            runOnServerThread {
                if (error != null) showUpdateError(sender, "Проверка обновления не удалась", error)
                else handleUpdateCheck(sender, registration, result)
            }
        }
    }

    /** Selects the requested product change or reports its current update state. */
    private fun handleUpdateCheck(
        sender: CommandSender,
        registration: UpdateRegistration,
        result: UpdatePlanSnapshot,
    ) {
        val change = result.plan?.changes?.firstOrNull {
            it.product.value.equals(registration.snapshot.product, ignoreCase = true)
        }
        if (result.state != UpdateState.UPDATE_AVAILABLE || change == null) {
            BukkitUpdateMessages.status(result to registration.snapshot).forEach(sender::sendMessage)
            return
        }
        stageUpdate(sender, result, change)
    }

    /** Downloads and validates the selected compatible update plan. */
    private fun stageUpdate(sender: CommandSender, plan: UpdatePlanSnapshot, change: ProductChange) {
        sender.sendMessage("§eСовместимое обновление ${change.product} ${change.from} → ${change.to} найдено. Скачиваю и проверяю…")
        library.updates.stage(plan.id, updateActor(sender)).whenComplete { _, error ->
            runOnServerThread {
                if (error != null) showUpdateError(sender, "Не удалось подготовить обновление", error)
                else sender.sendMessage(
                    "§aОбновление ${change.product} ${change.from} → ${change.to} подготовлено. Перезапустите сервер.",
                )
            }
        }
    }

    /** Formats the root failure, including missing release artifacts. */
    private fun showUpdateError(sender: CommandSender, title: String, error: Throwable) {
        val cause = generateSequence(error) { it.cause }.last()
        val rawMessage = cause.message ?: cause.javaClass.simpleName
        val message = if (rawMessage.contains("HTTP 404", ignoreCase = true)) {
            "файл обновления не найден на GitHub (HTTP 404); проверьте ссылку в каталоге релизов"
        } else rawMessage
        sender.sendMessage("§c$title: §f$message")
    }

    /** Schedules command feedback through the native server scheduler. */
    private fun runOnServerThread(action: () -> Unit) {
        plugin.server.scheduler.runTask(plugin, Runnable(action))
    }

    /** Reports the current plan for all or one registered product. */
    private fun sendUpdateStatus(sender: CommandSender, requested: String?) {
        val plan = library.updates.currentPlan().orElse(null)
        if (plan == null) {
            sender.sendMessage("§eПроверка обновлений ещё не завершалась.")
            return
        }
        val registrations = if (requested == null) library.updates.all() else listOfNotNull(library.updates.get(requested))
        if (registrations.isEmpty()) {
            sender.sendMessage("§cПлагин ${requested ?: "с указанным именем"} не зарегистрирован в pnLibrary.")
            return
        }
        registrations.forEach { registration ->
            BukkitUpdateMessages.status(plan to registration.snapshot).forEach(sender::sendMessage)
        }
    }

    /** Validates permissions and confirmation before staging a rollback. */
    @Suppress("DEPRECATION")
    private fun rollbackUpdate(sender: CommandSender, token: String?) {
        if (!sender.hasPermission("pnlibrary.updates.rollback")) {
            sender.sendMessage("§cНедостаточно прав для отката обновлений.")
            return
        }
        val plan = library.updates.currentPlan().orElse(null)
        if (plan == null) {
            sender.sendMessage("§eНет плана обновления, который можно откатить.")
            return
        }
        if (sender is Player && token == null) {
            val issued = tokens.issue(sender.uniqueId, plan.id, plan.revision, UpdateAction.ROLLBACK, Duration.ofSeconds(30))
            val confirm = BukkitChatButtons.runCommand(
                "[ Подтвердить откат ]", ChatColor.RED, "/pn update-rollback $issued",
                "Предыдущие JAR будут подготовлены к перезапуску",
            )
            sender.sendMessage("§eОткат заменит весь связанный план обновления, а не один плагин.")
            sender.spigot().sendMessage(confirm)
            return
        }
        if (sender is Player && !tokens.consume(token.orEmpty(), sender.uniqueId, plan.id, plan.revision, UpdateAction.ROLLBACK)) {
            sender.sendMessage("§cПодтверждение отката истекло или уже использовано.")
            return
        }
        library.updates.rollback(updateActor(sender)).whenComplete { _, error ->
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (error == null) sender.sendMessage("§aПредыдущие версии плагинов подготовлены. Полностью перезапустите сервер.")
                else sender.sendMessage("§cНе удалось подготовить откат: §f${error.message ?: error.javaClass.simpleName}")
            })
        }
    }

    /** Consumes a player-bound confirmation before staging the update plan. */
    private fun confirmUpdate(sender: CommandSender, token: String?) {
        if (sender !is Player || token == null || !sender.hasPermission("pnlibrary.updates.download")) {
            sender.sendMessage("§cНедоступное подтверждение обновления.")
            return
        }
        val plan = library.updates.currentPlan().orElse(null)
        if (plan == null || !tokens.consume(token, sender.uniqueId, plan.id, plan.revision, UpdateAction.DOWNLOAD)) {
            sender.sendMessage("§cПодтверждение устарело или уже использовано.")
            return
        }
        library.updates.stage(plan.id, updateActor(sender)).whenComplete { staged, error ->
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (error == null) sender.sendMessage("§aПлан обновления проверен и подготовлен: ${staged.id}")
                else sender.sendMessage("§cНе удалось подготовить обновление: ${error.message}")
            })
        }
    }

    /** Validates the staged plan and a player confirmation before requesting restart. */
    @Suppress("DEPRECATION")
    private fun handleRestart(sender: CommandSender, supplied: String?) {
        if (!sender.hasPermission("pnlibrary.updates.restart")) {
            sender.sendMessage("§cНедостаточно прав для перезапуска.")
            return
        }
        val plan = library.updates.currentPlan().orElse(null)
        if (plan == null || plan.state != UpdateState.UPDATE_STAGED) {
            sender.sendMessage("§eНет подготовленных обновлений, требующих перезапуска.")
            return
        }
        if (supplied != null && sender is Player) {
            if (!tokens.consume(supplied, sender.uniqueId, plan.id, plan.revision, UpdateAction.RESTART)) {
                sender.sendMessage("§cПодтверждение истекло. Выполните /pn restart ещё раз.")
                return
            }
            if (library.updates.currentPlan().orElse(null)?.let { it.id != plan.id || it.revision != plan.revision } != false) {
                sender.sendMessage("§cПлан обновления изменился; подтвердите заново.")
                return
            }
            Bukkit.broadcastMessage("§e[pnFolder] §fСервер перезапускается для применения обновлений.")
            Bukkit.getScheduler().runTaskLater(plugin, Runnable { Bukkit.spigot().restart() }, 40L)
            return
        }
        if (sender !is Player) {
            sender.sendMessage("§eКонсольный перезапуск должен выполняться настроенной серверной командой.")
            return
        }
        val token = tokens.issue(sender.uniqueId, plan.id, plan.revision, UpdateAction.RESTART, Duration.ofSeconds(30))
        sender.sendMessage("")
        sender.sendMessage("§c§l Подтверждение перезапуска")
        sender.sendMessage(" §7Сейчас на сервере игроков: §f${Bukkit.getOnlinePlayers().size}")
        sender.sendMessage(" §7Подготовленные обновления применятся после полного перезапуска.")
        val confirm = BukkitChatButtons.runCommand(
            "[ Подтвердить перезапуск ]", ChatColor.RED, "/pn restart $token", "Подтверждение действует 30 секунд",
        )
        sender.spigot().sendMessage(confirm)
        sender.sendMessage("")
    }

    /** Formats one product's update state in the established chat style. */
    private fun sendUpdateLine(sender: CommandSender, snapshot: UpdateSnapshot) {
        val product = if (snapshot.product.equals("pnlibrary", true)) "Библиотека" else "Плагин ${snapshot.product}"
        when (snapshot.state) {
            UpdateState.UPDATE_AVAILABLE -> {
                sender.sendMessage(" §7- §f$product: §6${snapshot.currentVersion} §7→ §a${snapshot.latestVersion ?: "новая версия"}")
                sender.sendMessage(" §7   §fКанал: §e${BukkitStatusLabels.channel(snapshot)}")
                snapshot.supportedApi?.let { sender.sendMessage(" §7   §fAPI: §6${it.minimum}–${it.maximum}") }
            }
            UpdateState.UPDATE_STAGED ->
                sender.sendMessage(" §7- §f$product: §a${snapshot.latestVersion ?: snapshot.currentVersion} загружена; нужен перезапуск")
            UpdateState.FAILED ->
                sender.sendMessage(" §7- §f$product: §cпроверка не выполнена")
            UpdateState.BLOCKED, UpdateState.INCOMPATIBLE ->
                sender.sendMessage(" §7- §f$product: §cобновление недоступно")
            UpdateState.FROZEN ->
                sender.sendMessage(" §7- §f$product: §eобновления временно приостановлены")
            else ->
                sender.sendMessage(" §7- §f$product: §aактуальная версия ${snapshot.currentVersion}")
        }
    }

    private companion object {
        val REPEAT_COUNTS = listOf("10", "100", "1000")
    }
}
