package org.pnfolder.pnlibrary.bukkit

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.api.tasks.TaskSpec
import org.pnfolder.pnlibrary.bukkit.updates.UpdateReminders
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.events.PlayerLifecyclePublisher
import org.pnfolder.pnlibrary.core.lifecycle.journalJoinNotice
import org.pnfolder.pnlibrary.core.lifecycle.runtimeTaskScope
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level

/** Bukkit lifecycle notifications kept separate from portable command registration. */
internal class BukkitLifecycleListener(
    private val plugin: Plugin,
    @Volatile
    private var library: PnLibrary?,
) : Listener, AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val readyCallbacks = CopyOnWriteArrayList<Runnable>()
    private val serverReady = AtomicBoolean(false)
    private val reminders = UpdateReminders(plugin) { library }

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

    fun start(): BukkitLifecycleListener = apply {
        plugin.server.pluginManager.registerEvents(this, plugin)
        reminders.start()
        // ServerLoadEvent exists on modern Bukkit/Paper, but this module also
        // targets old 1.8 APIs. Register it reflectively when available.
        runCatchingNonFatal {
            val eventType = Class.forName("org.bukkit.event.server.ServerLoadEvent").asSubclass(Event::class.java)
            plugin.server.pluginManager.registerEvent(
                eventType,
                this,
                EventPriority.MONITOR,
                EventExecutor { _, _ -> markServerReady() },
                plugin,
            )
        }.onFailure { error ->
            // Servers without the event are expected; any other failure leaves readiness callbacks waiting.
            if (error !is ClassNotFoundException) plugin.logger.log(Level.SEVERE, "Could not listen for server readiness", error)
        }
    }

    /** Announces the portable join event once every other listener has handled the native one. */
    @EventHandler(priority = EventPriority.MONITOR)
    fun publishPlayerJoined(event: PlayerJoinEvent) {
        library?.let { PlayerLifecyclePublisher.joined(it, event.player.uniqueId, event.player.name) }
    }

    /** Announces the portable quit event while the player is still resolvable. */
    @EventHandler(priority = EventPriority.MONITOR)
    fun publishPlayerLeft(event: PlayerQuitEvent) {
        library?.let { PlayerLifecyclePublisher.left(it, event.player.uniqueId, event.player.name) }
    }

    /** Tells authorized players about updates and shows the journal notice after joining. */
    @EventHandler
    fun onAdministratorJoin(event: PlayerJoinEvent) {
        val player = event.player
        val canSeeJournal = player.isOp || player.hasPermission("pnlibrary.events.view")
        if (reminders.canSeeUpdates(player)) reminders.remind(player, withTitle = true)
        if (canSeeJournal) showJournalNotice(player)
    }

    private fun showJournalNotice(player: Player) {
        val runtime = library ?: return
        runtime.runtimeTaskScope().schedule(TaskSpec.builder()
            .execution(TaskExecution.entity(player))
            .delay(Duration.ofMillis(50))
            .action task@{
                val active = library ?: return@task
                if (!player.isOnline) return@task
                val audience = active.audiences.player(player.uniqueId) ?: return@task
                active.journalJoinNotice().forEach(audience::sendMessage)
            }
            .build())
    }

    /** Closing the plugin registration releases all its module commands and task scopes. */
    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        library?.plugins?.unregister(event.plugin)
    }

    private fun markServerReady() {
        if (!serverReady.compareAndSet(false, true)) return
        readyCallbacks.toList().forEach { callback ->
            runCatchingNonFatal(callback::run).onFailure { plugin.logger.log(Level.SEVERE, "A startup task failed after the server became ready", it) }
        }
        readyCallbacks.clear()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        HandlerList.unregisterAll(this)
        reminders.close()
        readyCallbacks.clear()
        library = null
    }
}
