package ru.privatenull.pnlibrary.bukkit

import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.api.runtime.PnLibraryBrand
import ru.privatenull.pnlibrary.api.updates.UpdateSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateState
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/** Bukkit lifecycle notifications kept separate from portable command registration. */
internal class BukkitLifecycleListener(
    private val plugin: Plugin,
    @Volatile private var library: PnLibrary?,
) : Listener, AutoCloseable {
    private val closed = AtomicBoolean(false)

    fun start(): BukkitLifecycleListener = apply {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @EventHandler
    @Suppress("DEPRECATION")
    fun onAdministratorJoin(event: PlayerJoinEvent) {
        val player = event.player
        if (!player.isOp && !player.hasPermission("pnlibrary.updates.notify")) return
        val runtime = library ?: return
        runtime.tasks.scope(plugin).laterEntity(player, Duration.ofMillis(50), Runnable {
            val active = library ?: return@Runnable
            val actionable = active.updates.registrations().map { it.snapshot }.filter {
                it.state == UpdateState.AVAILABLE || it.state == UpdateState.DOWNLOADED ||
                    it.state == UpdateState.UPDATE_AVAILABLE || it.state == UpdateState.UPDATE_STAGED
            }
            if (actionable.isEmpty() || !player.isOnline) return@Runnable
            player.sendMessage("")
            player.sendMessage("§e§l pnFolder §8• §fдоступно обновлений: §e${actionable.size}")
            actionable.forEach { sendUpdateLine(player::sendMessage, it) }
            player.sendMessage("§7 Управление: §f/pn updates §8• §fПоддержка: §e${PnLibraryBrand.SUPPORT_URL}")
            val check = button(
                "[ Проверить ]", ChatColor.GREEN,
                ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pn check"),
                "Повторно проверить все обновления",
            )
            val support = button(
                "[ Поддержка ]", ChatColor.GOLD,
                ClickEvent(ClickEvent.Action.OPEN_URL, PnLibraryBrand.SUPPORT_URL),
                "Открыть Discord pnFolder",
            )
            player.spigot().sendMessage(check, TextComponent("  "), support)
            player.sendMessage("")
            player.sendTitle("§eОбновления pnFolder", "§fДоступно: §e${actionable.size} §8• §7/pn updates")
        })
    }

    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        library?.plugins?.unregister(event.plugin)
        library?.tasks?.close(event.plugin)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        HandlerList.unregisterAll(this)
        library = null
    }

    private fun button(text: String, color: ChatColor, action: ClickEvent, hint: String) =
        TextComponent(text).apply {
            this.color = color
            isBold = true
            clickEvent = action
            hoverEvent = HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                ComponentBuilder(hint).color(ChatColor.GRAY).create(),
            )
        }

    private fun sendUpdateLine(send: (String) -> Unit, snapshot: UpdateSnapshot) {
        val state = when (snapshot.state) {
            UpdateState.UP_TO_DATE, UpdateState.CURRENT -> "§aактуальная версия"
            UpdateState.UPDATE_AVAILABLE, UpdateState.AVAILABLE -> "§eдоступна ${snapshot.latestVersion}"
            UpdateState.UPDATE_STAGED, UpdateState.DOWNLOADED -> "§a${snapshot.latestVersion} загружена; нужен перезапуск"
            UpdateState.FROZEN -> "§eобновления временно заморожены"
            UpdateState.INCOMPATIBLE -> "§cнесовместимое обновление"
            UpdateState.BLOCKED -> "§cобновление заблокировано зависимостью"
            UpdateState.CHECKING -> "§eпроверяется"
            UpdateState.DOWNLOADING -> "§eскачивается и проверяется"
            UpdateState.FAILED -> "§cошибка: ${snapshot.message ?: "неизвестная причина"}"
        }
        val auto = if (snapshot.automaticDownload) "автозагрузка включена" else "автозагрузка отключена"
        send(" §7- §f${snapshot.product}: §6${snapshot.currentVersion} §7• $state §7• Java ${snapshot.currentJava}/${snapshot.requiredJava}+ • $auto")
    }
}
