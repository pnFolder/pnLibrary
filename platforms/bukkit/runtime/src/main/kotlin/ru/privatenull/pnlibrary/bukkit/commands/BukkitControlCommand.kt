package ru.privatenull.pnlibrary.bukkit.commands

import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
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
import ru.privatenull.pnlibrary.api.updates.ReleaseSummary
import ru.privatenull.pnlibrary.api.updates.UpdateChannel
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.time.Duration
import ru.privatenull.pnlibrary.bukkit.updates.UpdateAction
import ru.privatenull.pnlibrary.bukkit.updates.UpdateConfirmationTokens
import ru.privatenull.pnlibrary.console.ConsoleCard
import ru.privatenull.pnlibrary.console.ConsoleTheme

/** Bukkit-only `/pn` behavior expressed through the shared command builder. */
internal class BukkitControlCommand(
    private val plugin: Plugin,
    private val library: PnLibrary,
) {
    private val tokens = UpdateConfirmationTokens()

    fun definition(): CommandDefinition = command("pn") {
        permission("pnlibrary.admin")
        executes(::executeNative)
        literal("status") {
            executes(::executeNative)
            argument("plugin", ArgumentType.string()) {
                suggests { library.updates.all().map { it.snapshot.product } }
                executes(::executeNative)
            }
        }
        literal("updates") { executes(::executeNative) }
        literal("check") { executes(::executeNative) }
        literal("update") {
            argument("plugin", ArgumentType.string()) {
                suggests { library.updates.all().map { it.snapshot.product } }
                executes(::executeNative)
            }
        }
        literal("update-confirm") {
            argument("token", ArgumentType.string()) { executes(::executeNative) }
        }
        literal("update-status") {
            executes(::executeNative)
            argument("plugin", ArgumentType.string()) {
                suggests { library.updates.all().map { it.snapshot.product } }
                executes(::executeNative)
            }
        }
        literal("update-rollback") {
            executes(::executeNative)
            argument("token", ArgumentType.string()) { executes(::executeNative) }
        }
        literal("restart") {
            executes(::executeNative)
            argument("token", ArgumentType.string()) { executes(::executeNative) }
        }
        literal("debug") { executes(::executeNative) }
        literal("support") { executes(::executeNative) }
        literal("error") { executes(::executeNative) }
        literal("error-repeat") {
            executes(::executeNative)
            argument("count", ArgumentType.integer()) {
                suggests { REPEAT_COUNTS }
                executes(::executeNative)
            }
        }
        literal("error-chain") { executes(::executeNative) }
    }

    private fun executeNative(context: CommandContext) {
        val sender = (context.sender as? BukkitCommandSender)?.native
        if (sender == null) context.sender.send(net.kyori.adventure.text.Component.text("Unsupported Bukkit sender."))
        else execute(sender, context.arguments)
    }

    private fun execute(sender: CommandSender, arguments: List<String>) {
        if (library.isClosed) {
            sender.sendMessage("§cpnLibrary не готова.")
            return
        }
        val action = arguments.firstOrNull()?.lowercase(Locale.ROOT) ?: "status"
        when (action) {
            "status" -> sendStatus(sender, arguments.getOrNull(1))
            "updates" -> sendUpdates(sender)
            "update" -> update(sender, arguments.getOrNull(1))
            "update-confirm" -> confirmUpdate(sender, arguments.getOrNull(1))
            "update-status" -> sendUpdateStatus(sender, arguments.getOrNull(1))
            "update-rollback" -> rollbackUpdate(sender, arguments.getOrNull(1))
            "check" -> {
            library.updates.all().forEach { it.checkNow() }
                sender.sendMessage("§eПовторная проверка обновлений запущена.")
            }
            "restart" -> handleRestart(sender, arguments.drop(1))
            "support" -> sender.sendMessage("§eПоддержка pnFolder: §f${PnLibraryBrand.SUPPORT_URL}")
            "debug" -> sender.sendMessage("§eИспользуйте /pndebug [all|plugin] [--full|--config|--logs]")
            "error" -> emitUniqueTestError(sender)
            "error-repeat" -> emitRepeatedTestError(sender, arguments.getOrNull(1))
            "error-chain" -> emitChainedTestError(sender)
            else -> sender.sendMessage("§e/pn [status|updates|check|update|update-status|update-rollback|restart|debug|support|error|error-repeat|error-chain]")
        }
    }

    private fun sendStatus(sender: CommandSender, requested: String?) {
        val entries = if (requested == null) library.updates.all()
        else listOfNotNull(library.updates.get(requested))
        if (sender is ConsoleCommandSender && requested == null) {
            renderConsoleStatus(sender, entries)
            return
        }
        sender.sendMessage("")
        sender.sendMessage("§a «Состояние pnFolder»")
        sender.sendMessage(" §7- §fЯдро: §6${Bukkit.getName()} ${Bukkit.getBukkitVersion()}")
        sender.sendMessage(" §7- §fJava: §6${javaRuntimeLabel()}")
        sender.sendMessage(" §7- §fpnLibrary: §6${library.version}")
        entries.firstOrNull()?.snapshot?.let { snapshot ->
            sender.sendMessage(" §7- §fКанал: §e${channelName(snapshot)}")
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

    private fun renderConsoleStatus(sender: CommandSender, entries: List<UpdateRegistration>) {
        val theme = ConsoleTheme("§6", "§e", "§f", "§8", "§r")
        val card = ConsoleCard.builder(theme, "СОСТОЯНИЕ PNFOLDER")
            .mascot("^.^", "pnLibrary", "библиотека платформы")
            .blank()
            .firstDetail("Продукт", "pnLibrary")
            .detail("Назначение", "общая библиотека pnFolder")
            .detail("Платформа", "Bukkit / Paper")
            .detail("Ядро", Bukkit.getBukkitVersion())
            .detail("Java", javaRuntimeLabel())
            .lastDetail("Поддержка", PnLibraryBrand.SUPPORT_URL)
            .blank()
            .section("ОБНОВЛЕНИЯ")
        if (entries.isEmpty()) {
            card.lastItem("зарегистрированных обновлений нет")
        } else {
            val first = entries.first().snapshot
            card.firstDetail("Выбранный канал", channelName(first))
                .detail("Доступные каналы", "Stable · Beta · Alpha · Dev")
                .lastDetail("Установленная версия", first.currentVersion)
                .blank()
                .section("ПОСЛЕДНИЕ ВЕРСИИ")
            entries.forEachIndexed { index, registration ->
                renderReleaseHistory(card, registration.snapshot)
                if (index != entries.lastIndex) card.blank()
            }
            val available = entries.map { it.snapshot }.filter {
                it.state == UpdateState.UPDATE_AVAILABLE || it.state == UpdateState.AVAILABLE
            }
            if (available.isNotEmpty()) {
                card.blank().section("ДОСТУПНО ОБНОВЛЕНИЕ")
                val selected = available.first()
                card.firstDetail("Установлена", selected.currentVersion)
                    .lastDetail("Новая версия", selected.latestVersion ?: "не указана")
                    .blank()
                    .section("СВЕДЕНИЯ ОБ ОБНОВЛЕНИИ")
                    .firstDetail("Канал", channelName(selected))
                    .detail("Источник", if (selected.releaseUrl.isNullOrBlank()) "не указан" else "GitHub Releases")
                    .detail("Платформа", "Bukkit / Paper")
                    .detail("Совместимость API", selected.supportedApi?.let { "${it.minimum}–${it.maximum}" } ?: "не указана")
                    .detail("Java", "${selected.requiredJava}+")
                    .detail("Почему выбрана", "версия новее и совместима")
                    .lastDetail("Установка", if (selected.automaticDownload) "автоматическая" else "вручную, через /pn update")
            } else {
                card.blank().lastItem("Новых совместимых обновлений не найдено")
            }
        }
        card.blank()
            .section("ПРОВЕРКА")
            .lastItem("Проверка обновлений завершена")
            .blank()
            .status(if (entries.any { it.snapshot.state == UpdateState.UPDATE_AVAILABLE || it.snapshot.state == UpdateState.AVAILABLE }) {
                val channel = entries.firstOrNull { it.snapshot.state == UpdateState.UPDATE_AVAILABLE || it.snapshot.state == UpdateState.AVAILABLE }
                    ?.snapshot?.let(::channelName)
                if (channel == null) "Доступно обновление" else "Доступно обновление · $channel"
            } else {
                "Все зарегистрированные компоненты актуальны"
            })
            .build().render().forEach(sender::sendMessage)
    }

    private fun renderReleaseHistory(card: ConsoleCard.Builder, snapshot: UpdateSnapshot) {
        val releases = snapshot.availableReleases
            .groupBy(ReleaseSummary::channel)
            .mapValues { (_, values) -> values.maxByOrNull { it.version } }
        val channels = listOf(UpdateChannel.STABLE, UpdateChannel.BETA, UpdateChannel.ALPHA, UpdateChannel.DEV)
            .mapNotNull { channel -> releases[channel]?.let { channel to it } }
        if (channels.isEmpty()) {
            card.firstDetail("Продукт", productLabel(snapshot.product))
                .lastDetail("Версия", snapshot.latestVersion ?: snapshot.currentVersion)
            return
        }
        card.firstDetail("Продукт", productLabel(snapshot.product))
        channels.forEachIndexed { index, (channel, release) ->
            val label = channelLabel(channel)
            val value = "${channelColor(channel)}${release.version}${release.publishedAt?.let { " · ${publishedAge(it)}" } ?: ""}§r"
            if (index == channels.lastIndex) card.lastDetail(label, value) else card.detail(label, value)
        }
    }

    private fun publishedAge(instant: java.time.Instant): String {
        val elapsed = Duration.between(instant, java.time.Instant.now()).coerceAtLeast(Duration.ZERO)
        return when {
            elapsed.toMinutes() < 60 -> "опубликована ${elapsed.toMinutes()} мин. назад"
            elapsed.toHours() < 24 -> "опубликована ${elapsed.toHours()} ч. назад"
            else -> "опубликована ${elapsed.toDays()} дн. назад"
        }
    }

    private fun channelLabel(channel: UpdateChannel): String = when (channel) {
        UpdateChannel.STABLE -> "Стабильный канал"
        UpdateChannel.BETA -> "Тестовый канал Beta"
        UpdateChannel.ALPHA -> "Экспериментальный канал Alpha"
        UpdateChannel.DEV -> "Разрабатываемый канал Dev"
    }

    private fun channelColor(channel: UpdateChannel): String = when (channel) {
        UpdateChannel.STABLE -> "§a"
        UpdateChannel.BETA -> "§e"
        UpdateChannel.ALPHA -> "§6"
        UpdateChannel.DEV -> "§c"
    }

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
                sender.sendMessage("§cПроверка обновления прервана: GitHub не ответил за 30 секунд.")
                sender.sendMessage("§7Проверьте соединение сервера с raw.githubusercontent.com и повторите команду.")
            }
        }, 600L)
        library.updates.checkNow().whenComplete { result, error ->
            if (!finished.compareAndSet(false, true)) return@whenComplete
            runOnServerThread {
                if (error != null) showUpdateError(sender, "Проверка обновления не удалась", error)
                else handleUpdateCheck(sender, registration, result)
            }
        }
    }

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

    private fun stageUpdate(sender: CommandSender, plan: UpdatePlanSnapshot, change: ProductChange) {
        sender.sendMessage("§eСовместимое обновление ${change.product} ${change.from} → ${change.to} найдено. Скачиваю и проверяю…")
        library.updates.stage(plan.id).whenComplete { _, error ->
            runOnServerThread {
                if (error != null) showUpdateError(sender, "Не удалось подготовить обновление", error)
                else sender.sendMessage(
                    "§aОбновление ${change.product} ${change.from} → ${change.to} подготовлено. Перезапустите сервер.",
                )
            }
        }
    }

    private fun showUpdateError(sender: CommandSender, title: String, error: Throwable) {
        val cause = generateSequence(error) { it.cause }.last()
        sender.sendMessage("§c$title: §f${cause.message ?: cause.javaClass.simpleName}")
    }

    private fun runOnServerThread(action: () -> Unit) {
        plugin.server.scheduler.runTask(plugin, Runnable(action))
    }

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
            val confirm = TextComponent("[ Подтвердить откат ]").apply {
                color = ChatColor.RED
                isBold = true
                clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pn update-rollback $issued")
                hoverEvent = HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    ComponentBuilder("Предыдущие JAR будут подготовлены к перезапуску").color(ChatColor.GRAY).create(),
                )
            }
            sender.sendMessage("§eОткат заменит весь связанный план обновления, а не один плагин.")
            sender.spigot().sendMessage(confirm)
            return
        }
        if (sender is Player && !tokens.consume(token.orEmpty(), sender.uniqueId, plan.id, plan.revision, UpdateAction.ROLLBACK)) {
            sender.sendMessage("§cПодтверждение отката истекло или уже использовано.")
            return
        }
        library.updates.rollback().whenComplete { _, error ->
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (error == null) sender.sendMessage("§aПредыдущие версии плагинов подготовлены. Полностью перезапустите сервер.")
                else sender.sendMessage("§cНе удалось подготовить откат: §f${error.message ?: error.javaClass.simpleName}")
            })
        }
    }

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
        library.updates.stage(plan.id).whenComplete { staged, error ->
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (error == null) sender.sendMessage("§aПлан обновления проверен и подготовлен: ${staged.id}")
                else sender.sendMessage("§cНе удалось подготовить обновление: ${error.message}")
            })
        }
    }

    private fun emitUniqueTestError(sender: CommandSender) {
        val id = UUID.randomUUID().toString().substring(0, 8)
        library.logging.logger(plugin, "diagnostic-test").error(
            "Unique diagnostic test error [$id]",
            IllegalStateException("Generated unique failure [$id]"),
        )
        sender.sendMessage("§aСоздана уникальная тестовая ошибка: §f$id")
    }

    private fun emitRepeatedTestError(sender: CommandSender, rawCount: String?) {
        val count = rawCount?.toIntOrNull()?.coerceIn(1, 1_000) ?: 10
        val logger = library.logging.logger(plugin, "diagnostic-test")
        repeat(count) { logger.error("Repeated diagnostic test error", repeatedTestException()) }
        sender.sendMessage("§aОдинаковая тестовая ошибка вызвана §f$count §aраз.")
    }

    private fun repeatedTestException(): Throwable = IllegalStateException("Generated repeated failure")

    private fun emitChainedTestError(sender: CommandSender) {
        val root = IllegalArgumentException("Invalid test database response")
        val database = java.sql.SQLException("Test query execution failed", root)
        val completion = java.util.concurrent.CompletionException("Test asynchronous operation failed", database)
        library.logging.logger(plugin, "diagnostic-test").error("Chained diagnostic test error", completion)
        sender.sendMessage("§aСоздана тестовая ошибка с полной цепочкой причин.")
    }

    @Suppress("DEPRECATION")
    private fun handleRestart(sender: CommandSender, arguments: List<String>) {
        if (!sender.hasPermission("pnlibrary.updates.restart")) {
            sender.sendMessage("§cНедостаточно прав для перезапуска.")
            return
        }
        val plan = library.updates.currentPlan().orElse(null)
        if (plan == null || plan.state != UpdateState.UPDATE_STAGED) {
            sender.sendMessage("§eНет подготовленных обновлений, требующих перезапуска.")
            return
        }
        val supplied = arguments.firstOrNull()
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
        val confirm = TextComponent("[ Подтвердить перезапуск ]").apply {
                color = ChatColor.RED
                isBold = true
                clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pn restart $token")
                hoverEvent = HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    ComponentBuilder("Подтверждение действует 30 секунд").color(ChatColor.GRAY).create(),
                )
            }
        sender.spigot().sendMessage(confirm)
        sender.sendMessage("")
    }

    private fun sendUpdates(sender: CommandSender) {
        if (!sender.hasPermission("pnlibrary.updates.view")) {
            sender.sendMessage("§cНедостаточно прав для просмотра обновлений.")
            return
        }
        sender.sendMessage("")
        sender.sendMessage("§e «Обновления pnFolder»")
        val entries = library.updates.all()
        if (entries.isEmpty()) sender.sendMessage(" §7Нет зарегистрированных плагинов.")
        entries.forEach {
            sendUpdateLine(sender, it.snapshot)
        }
        if (sender is Player && sender.hasPermission("pnlibrary.updates.download")) {
            library.updates.currentPlan().orElse(null)?.takeIf { it.state == UpdateState.UPDATE_AVAILABLE }?.let { plan ->
                sendPlanDownloadButton(sender, tokens.issue(
                    sender.uniqueId, plan.id, plan.revision, UpdateAction.DOWNLOAD, Duration.ofMinutes(2),
                ))
            }
        }
        if (sender is Player) sendActionButtons(sender)
        sender.sendMessage(" §7Ручная загрузка: §f/pn update <плагин>")
        sender.sendMessage("")
    }

    @Suppress("DEPRECATION")
    private fun sendPlanDownloadButton(player: Player, token: String) {
        val button = TextComponent("[ Скачать совместимый план ]").apply {
            color = ChatColor.YELLOW
            isBold = true
            clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pn update-confirm $token")
            hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT,
                ComponentBuilder("Скачать и проверить весь план").color(ChatColor.GRAY).create())
        }
        player.spigot().sendMessage(button)
    }

    @Suppress("DEPRECATION")
    private fun sendActionButtons(player: Player) {
        fun button(text: String, color: ChatColor, action: ClickEvent, hint: String): TextComponent =
            TextComponent(text).apply {
                this.color = color
                isBold = true
                clickEvent = action
                hoverEvent = HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    ComponentBuilder(hint).color(ChatColor.GRAY).create(),
                )
            }
        val check = button(
            "[ Проверить ]",
            ChatColor.GREEN,
            ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pn check"),
            "Повторно проверить все обновления",
        )
        val support = button(
            "[ Поддержка ]",
            ChatColor.GOLD,
            ClickEvent(ClickEvent.Action.OPEN_URL, PnLibraryBrand.SUPPORT_URL),
            "Открыть Discord pnFolder",
        )
        player.spigot().sendMessage(check, TextComponent("  "), support)
    }

    private fun sendUpdateLine(sender: CommandSender, snapshot: UpdateSnapshot) {
        val product = if (snapshot.product.equals("pnlibrary", true)) "Библиотека" else "Плагин ${snapshot.product}"
        when (snapshot.state) {
            UpdateState.UPDATE_AVAILABLE, UpdateState.AVAILABLE -> {
                sender.sendMessage(" §7- §f$product: §6${snapshot.currentVersion} §7→ §a${snapshot.latestVersion ?: "новая версия"}")
                sender.sendMessage(" §7   §fКанал: §e${channelName(snapshot)}")
                snapshot.supportedApi?.let { sender.sendMessage(" §7   §fAPI: §6${it.minimum}–${it.maximum}") }
            }
            UpdateState.UPDATE_STAGED, UpdateState.DOWNLOADED ->
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

    private fun javaRuntimeLabel(): String {
        val version = System.getProperty("java.version")?.trim().orEmpty()
        val vm = System.getProperty("java.vm.name")?.trim().orEmpty()
        val runtime = when {
            vm.startsWith("OpenJDK", true) -> "OpenJDK"
            vm.isNotBlank() -> vm.substringBefore(" 64-Bit").trim()
            else -> System.getProperty("java.vm.vendor")?.trim().orEmpty()
        }
        return listOf(runtime, version).filter(String::isNotBlank).joinToString(" ")
            .ifBlank { Runtime.version().toString() }
    }

    private fun channelName(snapshot: UpdateSnapshot): String = when (snapshot.channel.name) {
        "STABLE" -> "стабильный канал"
        "BETA" -> "тестовый канал Beta"
        "ALPHA" -> "экспериментальный канал Alpha"
        else -> "разрабатываемый канал Dev"
    }

    private fun productLabel(product: String): String =
        if (product.equals("pnlibrary", true)) "pnLibrary" else product

    private companion object {
        val CONTROL_ACTIONS = listOf(
            "status", "updates", "check", "update", "update-status", "update-rollback", "restart", "debug", "support",
            "error", "error-repeat", "error-chain",
        )
        val REPEAT_COUNTS = listOf("10", "100", "1000")
    }
}
