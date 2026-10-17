package ru.privatenull.pnlibrary.bukkit

import ru.privatenull.pnlibrary.core.runtime.runtimeTaskScope
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.event.EventHandler
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.EventExecutor
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.api.runtime.PnLibraryBrand
import ru.privatenull.pnlibrary.api.tasks.TaskExecution
import ru.privatenull.pnlibrary.api.tasks.TaskSpec
import ru.privatenull.pnlibrary.api.updates.UpdateSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateState
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import ru.privatenull.pnlibrary.api.updates.UpdateChannel

/** Bukkit lifecycle notifications kept separate from portable command registration. */
internal class BukkitLifecycleListener(
    private val plugin: Plugin,
    @Volatile
    private var library: PnLibrary?,
) : Listener, AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val readyCallbacks = CopyOnWriteArrayList<Runnable>()
    private val serverReady = AtomicBoolean(false)

    /** Runs a callback immediately when ready, otherwise queues it for server startup. */
    fun whenServerReady(task: Runnable) {
        if (closed.get()) return
        if (serverReady.get()) {
            task.run()
            return
        }

        readyCallbacks += task
        if (closed.get()) {
            readyCallbacks.remove(task)
            return
        }
        // Readiness may have been published between the first check and the enqueue.
        // Remove and run this callback here so it cannot remain stranded in the queue.
        if (serverReady.get() && readyCallbacks.remove(task)) task.run()
    }

    /** Registers lifecycle events and the optional modern server-load notification. */
    fun start(): BukkitLifecycleListener = apply {
        plugin.server.pluginManager.registerEvents(this, plugin)
        // ServerLoadEvent exists on modern Bukkit/Paper, but this module also
        // targets old 1.8 APIs. Register it reflectively when available.
        runCatching {
            val eventType = Class.forName("org.bukkit.event.server.ServerLoadEvent").asSubclass(Event::class.java)
            plugin.server.pluginManager.registerEvent(
                eventType,
                this,
                EventPriority.MONITOR,
                EventExecutor { _, _ -> markServerReady() },
                plugin,
            )
        }
    }

    /** Notifies authorized players about available or staged updates after joining. */
    @EventHandler
    fun onAdministratorJoin(event: PlayerJoinEvent) {
        val player = event.player
        if (!player.isOp && !player.hasPermission("pnlibrary.updates.notify")) return
        val runtime = library ?: return
        runtime.runtimeTaskScope().schedule(TaskSpec.builder()
            .execution(TaskExecution.entity(player))
            .delay(Duration.ofMillis(50))
            .action task@{
            val active = library ?: return@task
            val actionable = active.updates.all().map { it.snapshot }.filter {
                it.state == UpdateState.UPDATE_AVAILABLE || it.state == UpdateState.UPDATE_STAGED
            }
            if (actionable.isEmpty() || !player.isOnline) return@task
            player.sendMessage("")
            player.sendMessage("§a «Состояние pnFolder»")
            player.sendMessage(" §7- §fОбновления: §eдоступны новые версии")
            actionable.forEach { sendUpdateLine(player::sendMessage, it) }
            player.sendMessage(" §7- §fПодробнее: §f/pn update-status")
            val check = button(
                "[ Проверить обновления ]", ChatColor.GREEN,
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
            player.sendTitle("§eОбновления pnFolder", "§fДоступно: §e${actionable.size}")
            }
            .build())
    }

    /** Closing the plugin registration releases all its module commands and task scopes. */
    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        library?.plugins?.unregister(event.plugin)
    }

    /** Drains queued readiness callbacks once. */
    private fun markServerReady() {
        if (!serverReady.compareAndSet(false, true)) return
        readyCallbacks.toList().forEach { runCatching(it::run) }
        readyCallbacks.clear()
    }

    /** Unregisters listeners and releases queued callbacks once. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        HandlerList.unregisterAll(this)
        readyCallbacks.clear()
        library = null
    }

    /** Creates a clickable chat button with an explanatory hover hint. */
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

    /** Formats one product's update state for administrator notifications. */
    private fun sendUpdateLine(send: (String) -> Unit, snapshot: UpdateSnapshot) {
        val product = if (snapshot.product.equals("pnlibrary", true)) "Библиотека" else "Плагин ${snapshot.product}"
        when (snapshot.state) {
            UpdateState.UPDATE_AVAILABLE -> {
                send(" §7 - §f$product: §6${snapshot.currentVersion} §7→ §a${snapshot.latestVersion ?: "новая версия"}")
                send(" §7   §fКанал: §e${channelName(snapshot.channel)}")
            }
            UpdateState.UPDATE_STAGED ->
                send(" §7 - §f$product: §a${snapshot.latestVersion ?: snapshot.currentVersion} загружена; нужен перезапуск")
            UpdateState.FAILED ->
                send(" §7 - §f$product: §cпроверка не выполнена")
            UpdateState.BLOCKED, UpdateState.INCOMPATIBLE ->
                send(" §7 - §f$product: §cобновление недоступно")
            UpdateState.FROZEN ->
                send(" §7 - §f$product: §eобновления временно приостановлены")
            else ->
                send(" §7 - §f$product: §aактуальная версия ${snapshot.currentVersion}")
        }
    }

    /** Returns the localized channel label used in administrator messages. */
    private fun channelName(channel: UpdateChannel): String = when (channel) {
        UpdateChannel.STABLE -> "Стабильные версии (Stable)"
        UpdateChannel.RC -> "Кандидаты в стабильные (RC)"
        UpdateChannel.BETA -> "Тестовые версии (Beta)"
        UpdateChannel.ALPHA -> "Экспериментальные версии (Alpha)"
        UpdateChannel.DEV -> "Разрабатываемые версии (Dev)"
    }
}
