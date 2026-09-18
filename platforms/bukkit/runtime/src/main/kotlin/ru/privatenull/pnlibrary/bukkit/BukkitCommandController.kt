package ru.privatenull.pnlibrary.bukkit

import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandMap
import org.bukkit.command.CommandSender
import org.bukkit.command.PluginCommand
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.api.runtime.PnLibraryBrand
import ru.privatenull.pnlibrary.api.updates.UpdateSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateState
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticCommandEvent
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticCommandExecutor
import java.lang.reflect.Constructor
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level

/**
 * Owns pnLibrary's Bukkit commands and administrator-facing lifecycle listeners.
 *
 * [start] registers command delegates and Bukkit listeners as one lifecycle unit. [close] removes
 * those registrations, clears restart confirmations, and releases the runtime reference. The
 * controller is deliberately separate from [BukkitPlatformAdapter], which remains focused on the
 * cross-platform SPI and scheduler dispatch.
 */
internal class BukkitCommandController(
    private val plugin: Plugin,
    private val commandAlias: String,
    @Volatile private var library: PnLibrary?,
) : CommandExecutor, TabCompleter, Listener, AutoCloseable {
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val restartConfirmations = ConcurrentHashMap<String, Long>()
    private val declaredCommands = mutableListOf<PluginCommand>()
    private var dynamicCommand: Pair<PluginCommand, CommandMap>? = null
    @Volatile
    private var diagnosticCommands: DiagnosticCommandExecutor? =
        library?.let(::DiagnosticCommandExecutor)

    /** Registers command delegates and native listeners, returning this controller. */
    fun start(): BukkitCommandController = apply {
        check(!closed.get()) { "Bukkit command controller is closed" }
        check(started.compareAndSet(false, true)) { "Bukkit command controller is already started" }
        try {
            registerCommands()
            Bukkit.getPluginManager().registerEvents(this, plugin)
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (command.name.equals("pn", true)) return handleControlCommand(sender, args)
        if (!sender.hasPermission("pnlibrary.debug")) {
            sender.sendMessage("§cНедостаточно прав.")
            return true
        }

        val executor = diagnosticCommands
        if (executor == null || library?.isClosed != false) {
            sender.sendMessage("§cpnLibrary не готова.")
            return true
        }
        executor.execute(args, command.name.equals("pnlib", true), sender.name, sender) { event ->
            sendDiagnosticEvent(sender, event)
        }
        return true
    }

    private fun sendDiagnosticEvent(sender: CommandSender, event: DiagnosticCommandEvent) {
        when (event) {
            DiagnosticCommandEvent.InvalidUsage ->
                sender.sendMessage("§e/$commandAlias [all|plugin] [--full|--config|--logs] [--local]")
            is DiagnosticCommandEvent.CoolingDown ->
                sender.sendMessage("§eПодождите ${event.seconds} сек. перед следующим отчётом.")
            is DiagnosticCommandEvent.Started ->
                sender.sendMessage("§7Собираю отчёт: ${event.target} ...")
            is DiagnosticCommandEvent.Completed -> {
                val report = event.report
                val output = report.uploadedUrl
                    ?: "plugins/${plugin.name}/reports/${report.localFile.fileName}"
                sender.sendMessage("§aОтчёт готов: $output")
                report.uploadError?.let { sender.sendMessage("§eЗагрузить отчёт не удалось: $it") }
            }
            is DiagnosticCommandEvent.Failed ->
                sender.sendMessage("§cОшибка при создании отчёта: ${event.message}")
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> {
        if (command.name.equals("pn", true)) {
            if (!sender.hasPermission("pnlibrary.admin")) return emptyList()
            val values = controlCompletions(args)
            val prefix = args.lastOrNull()?.lowercase(Locale.ROOT).orEmpty()
            return values.filter { it.lowercase(Locale.ROOT).startsWith(prefix) }
        }
        val options = mutableListOf<String>()
        if (!sender.hasPermission("pnlibrary.debug")) return options

        if (args.size <= 1) {
            options.add("all")
            for (pl in Bukkit.getPluginManager().plugins) {
                options.add(pl.name)
            }
            options.add("--local")
            options.add("--full")
            options.add("--config")
            options.add("--logs")
        }

        val prefix = if (args.isEmpty()) "" else args.last().lowercase(Locale.ROOT)
        options.removeIf { !it.lowercase(Locale.ROOT).startsWith(prefix) }
        return options
    }

    private fun controlCompletions(args: Array<out String>): List<String> = when (args.size) {
        1 -> CONTROL_ACTIONS
        2 -> when {
            args[0].equals("status", true) || args[0].equals("update", true) ->
                library?.updates?.registrations()?.map { it.snapshot.product }.orEmpty()
            args[0].equals("error-repeat", true) -> REPEAT_COUNTS
            else -> emptyList()
        }
        else -> emptyList()
    }

    private fun registerCommands() {
        (plugin as? JavaPlugin)?.getCommand("pn")?.also {
            it.executor = this
            it.tabCompleter = this
            declaredCommands += it
        }
        val declared = (plugin as? JavaPlugin)?.getCommand(commandAlias)
        if (declared != null) {
            declared.executor = this
            declared.tabCompleter = this
            declaredCommands += declared
            return
        }
        try {
            val cmdMapField = Bukkit.getServer().javaClass.getDeclaredField("commandMap")
            cmdMapField.isAccessible = true
            val cmdMap = cmdMapField.get(Bukkit.getServer()) as CommandMap

            val constructor: Constructor<PluginCommand> = PluginCommand::class.java.getDeclaredConstructor(
                String::class.java,
                Plugin::class.java,
            )
            constructor.isAccessible = true

            val cmd = constructor.newInstance(commandAlias, plugin)
            cmd.executor = this
            cmd.tabCompleter = this
            cmd.description = "pnLibrary diagnostic report command"
            cmd.permission = "pnlibrary.debug"
            cmd.aliases = listOf("pnlib")

            cmdMap.register(plugin.name, cmd)
            dynamicCommand = cmd to cmdMap
        } catch (error: Exception) {
            plugin.logger.log(
                Level.WARNING,
                "Could not register dynamic /$commandAlias diagnostics command",
                error,
            )
        }
    }

    private fun unregisterCommands() {
        declaredCommands.forEach { command ->
            command.executor = null
            command.tabCompleter = null
        }
        declaredCommands.clear()
        dynamicCommand?.let { (command, commandMap) ->
            command.unregister(commandMap)
        }
        dynamicCommand = null
    }

    private fun handleControlCommand(sender: CommandSender, args: Array<String>): Boolean {
        if (!sender.hasPermission("pnlibrary.admin")) {
            sender.sendMessage("§cНедостаточно прав.")
            return true
        }
        val runtime = library
        if (runtime == null || runtime.isClosed) {
            sender.sendMessage("§cpnLibrary не готова.")
            return true
        }
        val action = args.firstOrNull()?.lowercase(Locale.ROOT) ?: "status"
        when (action) {
            "status" -> {
                val requested = args.getOrNull(1)
                val entries = if (requested == null) runtime.updates.registrations()
                    else listOfNotNull(runtime.updates.find(requested))
                sender.sendMessage("")
                sender.sendMessage("§a «Состояние pnFolder»")
                sender.sendMessage(" §7- §fЯдро: §6${Bukkit.getName()} ${Bukkit.getBukkitVersion()}")
                sender.sendMessage(" §7- §fJava: §6${Runtime.version().feature()} §7(${System.getProperty("java.version")})")
                sender.sendMessage(" §7- §fpnLibrary: §6${runtime.version}")
                if (entries.isEmpty()) sender.sendMessage(" §7- §fПлагины: §7нет зарегистрированных обновлений")
                entries.forEach { sendUpdateLine(sender, it.snapshot) }
                sender.sendMessage(" §7- §fПоддержка: §e${PnLibraryBrand.SUPPORT_URL}")
                sender.sendMessage("")
            }
            "updates" -> sendUpdates(sender, runtime)
            "update" -> {
                val name = args.getOrNull(1)
                if (name == null) {
                    sender.sendMessage("§eИспользование: /pn update <плагин>")
                } else {
                    val registration = runtime.updates.find(name)
                    if (registration == null) sender.sendMessage("§cПлагин $name не зарегистрирован в pnLibrary.")
                    else {
                        registration.downloadNow()
                        sender.sendMessage("§eЗапущена проверка и ручная загрузка обновления ${registration.snapshot.product}.")
                    }
                }
            }
            "check" -> {
                runtime.updates.registrations().forEach { it.checkNow() }
                sender.sendMessage("§eПовторная проверка обновлений запущена.")
            }
            "restart" -> handleRestart(sender, args.drop(1))
            "support" -> sender.sendMessage("§eПоддержка pnFolder: §f${PnLibraryBrand.SUPPORT_URL}")
            "debug" -> sender.sendMessage("§eИспользуйте /$commandAlias [all|plugin] [--full|--config|--logs]")
            "error" -> emitUniqueTestError(sender, runtime)
            "error-repeat" -> emitRepeatedTestError(sender, runtime, args.getOrNull(1))
            "error-chain" -> emitChainedTestError(sender, runtime)
            else -> sender.sendMessage("§e/pn [status|updates|check|update|restart|debug|support|error|error-repeat|error-chain]")
        }
        return true
    }

    private fun emitUniqueTestError(sender: CommandSender, runtime: PnLibrary) {
        val id = UUID.randomUUID().toString().substring(0, 8)
        runtime.logging.logger(plugin, "diagnostic-test").error(
            "Unique diagnostic test error [$id]",
            IllegalStateException("Generated unique failure [$id]"),
        )
        sender.sendMessage("§aСоздана уникальная тестовая ошибка: §f$id")
    }

    private fun emitRepeatedTestError(sender: CommandSender, runtime: PnLibrary, rawCount: String?) {
        val count = rawCount?.toIntOrNull()?.coerceIn(1, 1_000) ?: 10
        val logger = runtime.logging.logger(plugin, "diagnostic-test")
        repeat(count) {
            logger.error("Repeated diagnostic test error", repeatedTestException())
        }
        sender.sendMessage("§aОдинаковая тестовая ошибка вызвана §f$count §aраз.")
    }

    private fun repeatedTestException(): Throwable =
        IllegalStateException("Generated repeated failure")

    private fun emitChainedTestError(sender: CommandSender, runtime: PnLibrary) {
        val root = IllegalArgumentException("Invalid test database response")
        val database = java.sql.SQLException("Test query execution failed", root)
        val completion = java.util.concurrent.CompletionException("Test asynchronous operation failed", database)
        runtime.logging.logger(plugin, "diagnostic-test").error("Chained diagnostic test error", completion)
        sender.sendMessage("§aСоздана тестовая ошибка с полной цепочкой причин.")
    }

    @Suppress("DEPRECATION")
    private fun handleRestart(sender: CommandSender, args: List<String>) {
        val runtime = library ?: return
        if (runtime.updates.registrations().none { it.snapshot.state == UpdateState.DOWNLOADED }) {
            sender.sendMessage("§eНет подготовленных обновлений, требующих перезапуска.")
            return
        }
        val key = sender.name.lowercase(Locale.ROOT)
        if (args.firstOrNull().equals("confirm", true)) {
            val expires = restartConfirmations.remove(key) ?: 0L
            if (expires < System.currentTimeMillis()) {
                sender.sendMessage("§cПодтверждение истекло. Выполните /pn restart ещё раз.")
                return
            }
            Bukkit.broadcastMessage("§e[pnFolder] §fСервер перезапускается для применения обновлений.")
            Bukkit.getScheduler().runTaskLater(plugin, Runnable { Bukkit.spigot().restart() }, 40L)
            return
        }
        restartConfirmations[key] = System.currentTimeMillis() + 30_000L
        val online = Bukkit.getOnlinePlayers().size
        sender.sendMessage("")
        sender.sendMessage("§c§l Подтверждение перезапуска")
        sender.sendMessage(" §7Сейчас на сервере игроков: §f$online")
        sender.sendMessage(" §7Подготовленные обновления применятся после полного перезапуска.")
        if (sender is Player) {
            val confirm = TextComponent("[ Подтвердить перезапуск ]").apply {
                color = ChatColor.RED
                isBold = true
                clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pn restart confirm")
                hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    ComponentBuilder("Подтверждение действует 30 секунд").color(ChatColor.GRAY).create())
            }
            sender.spigot().sendMessage(confirm)
        } else sender.sendMessage(" §c/pn restart confirm §7— подтверждение действует 30 секунд")
        sender.sendMessage("")
    }

    private fun sendUpdates(sender: CommandSender, runtime: PnLibrary) {
        sender.sendMessage("")
        sender.sendMessage("§e «Обновления pnFolder»")
        val entries = runtime.updates.registrations()
        if (entries.isEmpty()) sender.sendMessage(" §7Нет зарегистрированных плагинов.")
        entries.forEach {
            sendUpdateLine(sender, it.snapshot)
            if (sender is Player && it.snapshot.state == UpdateState.AVAILABLE) {
                sendDownloadButton(sender, it.snapshot.product)
            }
        }
        if (sender is Player) sendActionButtons(sender)
        sender.sendMessage(" §7Ручная загрузка: §f/pn update <плагин>")
        sender.sendMessage("")
    }

    @Suppress("DEPRECATION")
    private fun sendActionButtons(player: Player) {
        fun button(text: String, color: ChatColor, action: ClickEvent, hint: String): TextComponent = TextComponent(text).apply {
            this.color = color
            isBold = true
            clickEvent = action
            hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, ComponentBuilder(hint).color(ChatColor.GRAY).create())
        }
        val check = button("[ Проверить ]", ChatColor.GREEN,
            ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pn check"), "Повторно проверить все обновления")
        val support = button("[ Поддержка ]", ChatColor.GOLD,
            ClickEvent(ClickEvent.Action.OPEN_URL, PnLibraryBrand.SUPPORT_URL), "Открыть Discord pnFolder")
        player.spigot().sendMessage(check, TextComponent("  "), support)
    }

    @Suppress("DEPRECATION")
    private fun sendDownloadButton(player: Player, product: String) {
        val button = TextComponent("   [ Скачать $product сейчас ]").apply {
            color = ChatColor.YELLOW
            isBold = true
            clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pn update $product")
            hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT,
                ComponentBuilder("Скачать, проверить и подготовить обновление").color(ChatColor.GRAY).create())
        }
        player.spigot().sendMessage(button)
    }

    private fun sendUpdateLine(sender: CommandSender, snapshot: UpdateSnapshot) {
        val state = when (snapshot.state) {
            UpdateState.UP_TO_DATE -> "§aактуальная версия"
            UpdateState.UPDATE_AVAILABLE -> "§eдоступна ${snapshot.latestVersion}"
            UpdateState.UPDATE_STAGED -> "§a${snapshot.latestVersion} подготовлена; нужен перезапуск"
            UpdateState.FROZEN -> "§eобновления временно заморожены"
            UpdateState.INCOMPATIBLE -> "§cнесовместимое обновление"
            UpdateState.BLOCKED -> "§cобновление заблокировано зависимостью"
            UpdateState.CHECKING -> "§eпроверяется"
            UpdateState.CURRENT -> "§aактуальная версия"
            UpdateState.AVAILABLE -> "§eдоступна ${snapshot.latestVersion}"
            UpdateState.DOWNLOADED -> "§a${snapshot.latestVersion} загружена; нужен перезапуск"
            UpdateState.FAILED -> "§cошибка: ${snapshot.message ?: "неизвестная причина"}"
        }
        val auto = if (snapshot.automaticDownload) "автозагрузка включена" else "автозагрузка отключена"
        sender.sendMessage(" §7- §f${snapshot.product}: §6${snapshot.currentVersion} §7• $state §7• Java ${snapshot.currentJava}/${snapshot.requiredJava}+ • $auto")
    }

    @EventHandler
    @Suppress("DEPRECATION")
    fun onAdministratorJoin(event: PlayerJoinEvent) {
        val player = event.player
        if (!player.hasPermission("pnlibrary.admin")) return
        val runtime = library ?: return
        runtime.tasks.scope(plugin).laterEntity(player, java.time.Duration.ofSeconds(5), Runnable {
            val active = library ?: return@Runnable
            val actionable = active.updates.registrations().map { it.snapshot }
                .filter {
                    it.state == UpdateState.AVAILABLE || it.state == UpdateState.DOWNLOADED ||
                        it.state == UpdateState.UPDATE_AVAILABLE || it.state == UpdateState.UPDATE_STAGED
                }
            if (actionable.isEmpty() || !player.isOnline) return@Runnable
            player.sendMessage("")
            player.sendMessage("§e§l pnFolder §8• §fдоступно обновлений: §e${actionable.size}")
            actionable.forEach { sendUpdateLine(player, it) }
            player.sendMessage("§7 Управление: §f/pn updates §8• §fПоддержка: §e${PnLibraryBrand.SUPPORT_URL}")
            sendActionButtons(player)
            player.sendMessage("")
            player.sendTitle("§eОбновления pnFolder", "§fДоступно: §e${actionable.size} §8• §7/pn updates")
        })
    }

    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        library?.plugins?.unregisterOwner(event.plugin)
        library?.tasks?.close(event.plugin)
    }


    /** Removes every command delegate and listener owned by this controller. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        HandlerList.unregisterAll(this)
        unregisterCommands()
        restartConfirmations.clear()
        diagnosticCommands = null
        library = null
        started.set(false)
    }

    private companion object {
        val CONTROL_ACTIONS = listOf(
            "status",
            "updates",
            "check",
            "update",
            "restart",
            "debug",
            "support",
            "error",
            "error-repeat",
            "error-chain",
        )
        val REPEAT_COUNTS = listOf("10", "100", "1000")
    }
}
