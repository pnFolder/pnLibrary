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
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.metrics.PlatformMetricsFactory
import ru.privatenull.pnlibrary.api.platform.PlatformAdapter
import ru.privatenull.pnlibrary.api.platform.PlatformVariant
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.api.runtime.PnLibraryBrand
import ru.privatenull.pnlibrary.api.updates.UpdateState
import ru.privatenull.pnlibrary.bukkit.compat.ServerCapabilities
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticCommandEvent
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticCommandExecutor
import java.io.File
import java.lang.reflect.Constructor
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level

/**
 * Platform adapter for Bukkit, Spigot, Paper (1.12.2 to modern 1.21+), Leaf, and Purpur.
 */
class BukkitPlatformAdapter @JvmOverloads constructor(
    val plugin: Plugin,
    val commandAlias: String = "pndebug",
) : PlatformAdapter, CommandExecutor, TabCompleter, Listener {

    private val closedFlag = AtomicBoolean(false)
    private val restartConfirmations = ConcurrentHashMap<String, Long>()
    private var library: PnLibrary? = null
    private var diagnosticCommands: DiagnosticCommandExecutor? = null

    override val variant: PlatformVariant
        get() = when {
            ServerCapabilities.isPurpur -> PlatformVariant.PURPUR
            ServerCapabilities.isLeaf -> PlatformVariant.LEAF
            ServerCapabilities.isFolia -> PlatformVariant.FOLIA
            ServerCapabilities.isPaper -> PlatformVariant.PAPER
            else -> PlatformVariant.BUKKIT
        }
    override val metricsFactory: PlatformMetricsFactory = BukkitMetricsFactory()
    override val dataFolder = plugin.dataFolder.toPath()

    override fun log(owner: Any, level: LogLevel, message: String, error: Throwable?) {
        val target = (owner as? Plugin)?.logger ?: plugin.logger
        val nativeLevel = when (level) {
            LogLevel.WARNING -> Level.WARNING
            LogLevel.ERROR -> Level.SEVERE
            else -> Level.INFO
        }
        if (error == null) target.log(nativeLevel, message) else target.log(nativeLevel, message, error)
    }

    override fun console(owner: Any, message: String) {
        Bukkit.getConsoleSender().sendMessage(message)
    }

    override fun ownerDetails(owner: Any): Map<String, String> {
        val target = owner as? Plugin ?: return emptyMap()
        return linkedMapOf(
            "name" to target.name,
            "version" to target.description.version,
            "authors" to target.description.authors.joinToString(", ").ifBlank { "pnFolder" },
        )
    }

    override fun bind(library: PnLibrary) {
        this.library = library
        diagnosticCommands = DiagnosticCommandExecutor(library)
        registerCommands()
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    override fun details(): Map<String, Any?> {
        val server = Bukkit.getServer()
        val data = linkedMapOf<String, Any?>()

        data["serverName"] = server.name
        data["serverVersion"] = server.version
        data["bukkitVersion"] = server.bukkitVersion
        data["serverClass"] = server.javaClass.name
        data["isPaper"] = ServerCapabilities.isPaper
        data["isPurpur"] = ServerCapabilities.isPurpur
        data["isLeaf"] = ServerCapabilities.isLeaf

        // TPS
        val tps = ServerCapabilities.getTPS()
        if (tps != null && tps.size >= 3) {
            data["tps"] = linkedMapOf(
                "1m" to String.format(Locale.ROOT, "%.2f", tps[0]),
                "5m" to String.format(Locale.ROOT, "%.2f", tps[1]),
                "15m" to String.format(Locale.ROOT, "%.2f", tps[2]),
            )
        }

        // Worlds & Players counts ONLY (NO names, NO seeds, NO UUIDs/IPs!)
        data["worldCount"] = server.worlds.size
        data["onlinePlayersCount"] = server.onlinePlayers.size
        data["maxPlayers"] = server.maxPlayers

        // Plugins
        val pluginsData = mutableListOf<Map<String, Any?>>()
        for (pl in server.pluginManager.plugins) {
            val pDesc = pl.description
            val pMap = linkedMapOf<String, Any?>()
            pMap["name"] = pl.name
            pMap["version"] = pDesc.version
            pMap["enabled"] = pl.isEnabled
            pMap["mainClass"] = pDesc.main
            pMap["authors"] = pDesc.authors

            runCatching {
                val fileField = pl.javaClass.getMethod("getFile")
                fileField.isAccessible = true
                val jarFile = fileField.invoke(pl) as? File
                if (jarFile != null && jarFile.exists()) {
                    pMap["jarSizeBytes"] = jarFile.length()
                    pMap["lastModifiedUtc"] = Instant.ofEpochMilli(jarFile.lastModified()).toString()
                }
            }
            pluginsData.add(pMap)
        }
        data["plugins"] = pluginsData

        // PlaceholderAPI check
        val papiPlugin = server.pluginManager.getPlugin("PlaceholderAPI")
        if (papiPlugin != null && papiPlugin.isEnabled) {
            data["placeholderApi"] = linkedMapOf(
                "installed" to true,
                "version" to papiPlugin.description.version,
                "expansionsCount" to getPapiExpansionsCount(),
            )
        } else {
            data["placeholderApi"] = linkedMapOf("installed" to false)
        }

        return data
    }

    override fun executeGlobal(task: Runnable) {
        if (closedFlag.get()) return
        if (ServerCapabilities.isFolia) {
            try {
                val scheduler = Bukkit::class.java.getMethod("getGlobalRegionScheduler").invoke(null)
                val run = scheduler.javaClass.getMethod(
                    "run", Plugin::class.java, java.util.function.Consumer::class.java)
                run.invoke(scheduler, plugin, java.util.function.Consumer<Any> { task.run() })
                return
            } catch (error: ReflectiveOperationException) {
                log(plugin, LogLevel.ERROR, "Не удалось передать задачу Folia GlobalRegionScheduler", error)
                return
            }
        }
        if (Bukkit.isPrimaryThread()) {
            task.run()
        } else {
            Bukkit.getScheduler().runTask(plugin, task)
        }
    }

    override fun executeReply(recipient: Any, task: Runnable) {
        if (closedFlag.get()) return
        if (recipient is Player && ServerCapabilities.isFolia) {
            try {
                val getScheduler = recipient.javaClass.getMethod("getScheduler")
                val taskScheduler = getScheduler.invoke(recipient)
                val runMethod = taskScheduler.javaClass.getMethod("run", Plugin::class.java, java.util.function.Consumer::class.java, Runnable::class.java)
                runMethod.invoke(taskScheduler, plugin, java.util.function.Consumer<Any> { task.run() }, null)
                return
            } catch (error: Exception) {
                log(plugin, LogLevel.ERROR, "Не удалось передать задачу Folia EntityScheduler", error)
                return
            }
        }
        executeGlobal(task)
    }

    override fun close() {
        if (closedFlag.compareAndSet(false, true)) {
            HandlerList.unregisterAll(this)
            diagnosticCommands = null
            library = null
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

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (command.name.equals("pn", true)) {
            if (!sender.hasPermission("pnlibrary.admin")) return emptyList()
            val values = when (args.size) {
                1 -> listOf("status", "updates", "check", "update", "restart", "debug", "support")
                2 -> if (args[0].equals("status", true) || args[0].equals("update", true))
                    library?.updates?.registrations()?.map { it.snapshot.product } ?: emptyList()
                else emptyList()
                else -> emptyList()
            }
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

    private fun registerCommands() {
        (plugin as? JavaPlugin)?.getCommand("pn")?.also {
            it.executor = this
            it.tabCompleter = this
        }
        val declared = (plugin as? JavaPlugin)?.getCommand(commandAlias)
        if (declared != null) {
            declared.executor = this
            declared.tabCompleter = this
            return
        }
        runCatching {
            val cmdMapField = Bukkit.getServer().javaClass.getDeclaredField("commandMap")
            cmdMapField.isAccessible = true
            val cmdMap = cmdMapField.get(Bukkit.getServer()) as CommandMap

            val constructor: Constructor<PluginCommand> = PluginCommand::class.java.getDeclaredConstructor(String::class.java, Plugin::class.java)
            constructor.isAccessible = true

            val cmd = constructor.newInstance(commandAlias, plugin)
            cmd.executor = this
            cmd.tabCompleter = this
            cmd.description = "pnLibrary diagnostic report command"
            cmd.permission = "pnlibrary.debug"
            cmd.aliases = listOf("pnlib")

            cmdMap.register(plugin.name, cmd)
        }
    }

    private fun handleControlCommand(sender: CommandSender, args: Array<String>): Boolean {
        if (!sender.hasPermission("pnlibrary.admin")) {
            sender.sendMessage("§cНедостаточно прав.")
            return true
        }
        val runtime = library ?: run { sender.sendMessage("§cpnLibrary не готова."); return true }
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
            else -> sender.sendMessage("§e/pn [status [плагин]|updates|check|update <плагин>|restart|debug|support]")
        }
        return true
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
                color = ChatColor.RED; isBold = true
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

    private fun sendUpdateLine(sender: CommandSender, snapshot: ru.privatenull.pnlibrary.api.updates.UpdateSnapshot) {
        val state = when (snapshot.state) {
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
                .filter { it.state == UpdateState.AVAILABLE || it.state == UpdateState.DOWNLOADED }
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
        library?.tasks?.close(event.plugin)
    }

    private fun getPapiExpansionsCount(): Int {
        return try {
            val klass = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
            val method = klass.getMethod("getRegisteredIdentifiers")
            val list = method.invoke(null) as? Collection<*>
            list?.size ?: 0
        } catch (_: Exception) {
            0
        }
    }
}
