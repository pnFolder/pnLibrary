package ru.privatenull.pnlibrary.bukkit.commands

import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
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
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Bukkit-only `/pn` behavior expressed through the shared command builder. */
internal class BukkitControlCommand(
    private val plugin: Plugin,
    private val library: PnLibrary,
) {
    private val restartConfirmations = ConcurrentHashMap<String, Long>()

    fun definition(): CommandDefinition = command("pn") {
        permission("pnlibrary.admin")
        executes(::executeNative)
        literal("status") {
            executes(::executeNative)
            argument("plugin", ArgumentType.string()) {
                suggests { library.updates.registrations().map { it.snapshot.product } }
                executes(::executeNative)
            }
        }
        literal("updates") { executes(::executeNative) }
        literal("check") { executes(::executeNative) }
        literal("update") {
            argument("plugin", ArgumentType.string()) {
                suggests { library.updates.registrations().map { it.snapshot.product } }
                executes(::executeNative)
            }
        }
        literal("restart") {
            executes(::executeNative)
            literal("confirm") { executes(::executeNative) }
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
            "check" -> {
                library.updates.registrations().forEach { it.checkNow() }
                sender.sendMessage("§eПовторная проверка обновлений запущена.")
            }
            "restart" -> handleRestart(sender, arguments.drop(1))
            "support" -> sender.sendMessage("§eПоддержка pnFolder: §f${PnLibraryBrand.SUPPORT_URL}")
            "debug" -> sender.sendMessage("§eИспользуйте /pndebug [all|plugin] [--full|--config|--logs]")
            "error" -> emitUniqueTestError(sender)
            "error-repeat" -> emitRepeatedTestError(sender, arguments.getOrNull(1))
            "error-chain" -> emitChainedTestError(sender)
            else -> sender.sendMessage("§e/pn [status|updates|check|update|restart|debug|support|error|error-repeat|error-chain]")
        }
    }

    private fun sendStatus(sender: CommandSender, requested: String?) {
        val entries = if (requested == null) library.updates.registrations()
        else listOfNotNull(library.updates.find(requested))
        sender.sendMessage("")
        sender.sendMessage("§a «Состояние pnFolder»")
        sender.sendMessage(" §7- §fЯдро: §6${Bukkit.getName()} ${Bukkit.getBukkitVersion()}")
        sender.sendMessage(" §7- §fJava: §6${Runtime.version().feature()} §7(${System.getProperty("java.version")})")
        sender.sendMessage(" §7- §fpnLibrary: §6${library.version}")
        if (entries.isEmpty()) sender.sendMessage(" §7- §fПлагины: §7нет зарегистрированных обновлений")
        entries.forEach { sendUpdateLine(sender, it.snapshot) }
        sender.sendMessage(" §7- §fПоддержка: §e${PnLibraryBrand.SUPPORT_URL}")
        sender.sendMessage("")
    }

    private fun update(sender: CommandSender, name: String?) {
        if (name == null) {
            sender.sendMessage("§eИспользование: /pn update <плагин>")
            return
        }
        val registration = library.updates.find(name)
        if (registration == null) {
            sender.sendMessage("§cПлагин $name не зарегистрирован в pnLibrary.")
        } else {
            registration.downloadNow()
            sender.sendMessage("§eЗапущена проверка и ручная загрузка обновления ${registration.snapshot.product}.")
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
        if (library.updates.registrations().none { it.snapshot.state == UpdateState.DOWNLOADED }) {
            sender.sendMessage("§eНет подготовленных обновлений, требующих перезапуска.")
            return
        }
        val key = sender.name.lowercase(Locale.ROOT)
        if (arguments.firstOrNull().equals("confirm", true)) {
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
        sender.sendMessage("")
        sender.sendMessage("§c§l Подтверждение перезапуска")
        sender.sendMessage(" §7Сейчас на сервере игроков: §f${Bukkit.getOnlinePlayers().size}")
        sender.sendMessage(" §7Подготовленные обновления применятся после полного перезапуска.")
        if (sender is Player) {
            val confirm = TextComponent("[ Подтвердить перезапуск ]").apply {
                color = ChatColor.RED
                isBold = true
                clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pn restart confirm")
                hoverEvent = HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    ComponentBuilder("Подтверждение действует 30 секунд").color(ChatColor.GRAY).create(),
                )
            }
            sender.spigot().sendMessage(confirm)
        } else sender.sendMessage(" §c/pn restart confirm §7— подтверждение действует 30 секунд")
        sender.sendMessage("")
    }

    private fun sendUpdates(sender: CommandSender) {
        sender.sendMessage("")
        sender.sendMessage("§e «Обновления pnFolder»")
        val entries = library.updates.registrations()
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

    @Suppress("DEPRECATION")
    private fun sendDownloadButton(player: Player, product: String) {
        val button = TextComponent("   [ Скачать $product сейчас ]").apply {
            color = ChatColor.YELLOW
            isBold = true
            clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pn update $product")
            hoverEvent = HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                ComponentBuilder("Скачать, проверить и подготовить обновление").color(ChatColor.GRAY).create(),
            )
        }
        player.spigot().sendMessage(button)
    }

    private fun sendUpdateLine(sender: CommandSender, snapshot: UpdateSnapshot) {
        val state = when (snapshot.state) {
            UpdateState.UP_TO_DATE, UpdateState.CURRENT -> "§aактуальная версия"
            UpdateState.UPDATE_AVAILABLE, UpdateState.AVAILABLE -> "§eдоступна ${snapshot.latestVersion}"
            UpdateState.UPDATE_STAGED, UpdateState.DOWNLOADED ->
                "§a${snapshot.latestVersion} загружена; нужен перезапуск"
            UpdateState.FROZEN -> "§eобновления временно заморожены"
            UpdateState.INCOMPATIBLE -> "§cнесовместимое обновление"
            UpdateState.BLOCKED -> "§cобновление заблокировано зависимостью"
            UpdateState.CHECKING -> "§eпроверяется"
            UpdateState.FAILED -> "§cошибка: ${snapshot.message ?: "неизвестная причина"}"
        }
        val auto = if (snapshot.automaticDownload) "автозагрузка включена" else "автозагрузка отключена"
        sender.sendMessage(
            " §7- §f${snapshot.product}: §6${snapshot.currentVersion} §7• $state " +
                "§7• Java ${snapshot.currentJava}/${snapshot.requiredJava}+ • $auto",
        )
    }

    private companion object {
        val CONTROL_ACTIONS = listOf(
            "status", "updates", "check", "update", "restart", "debug", "support",
            "error", "error-repeat", "error-chain",
        )
        val REPEAT_COUNTS = listOf("10", "100", "1000")
    }
}
