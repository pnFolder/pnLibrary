package org.pnfolder.pnlibrary.bukkit

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.api.tasks.TaskSpec
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.bukkit.commands.BukkitControlCommand
import org.pnfolder.pnlibrary.core.runtime.journalJoinNotice
import org.pnfolder.pnlibrary.core.runtime.runtimeTaskScope
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogView
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

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

    /** Notifies authorized players about available or staged updates after joining, in the catalog's style. */
    @EventHandler
    fun onAdministratorJoin(event: PlayerJoinEvent) {
        val player = event.player
        val canSeeUpdates = player.isOp || player.hasPermission("pnlibrary.updates.notify")
        val canSeeJournal = player.isOp || player.hasPermission("pnlibrary.events.view")
        if (!canSeeUpdates && !canSeeJournal) return
        val runtime = library ?: return
        runtime.runtimeTaskScope().schedule(TaskSpec.builder()
            .execution(TaskExecution.entity(player))
            .delay(Duration.ofMillis(50))
            .action task@{
                val active = library ?: return@task
                if (!player.isOnline) return@task
                val audience = active.audiences.player(player.uniqueId) ?: return@task
                if (canSeeJournal) active.journalJoinNotice().forEach(audience::sendMessage)
                if (!canSeeUpdates) return@task
                val snapshots = active.updates.all().map { it.snapshot }
                val notice = UpdateCatalogView.announcement(snapshots, BukkitControlCommand.ROUTES, PnLibraryIdentity.SUPPORT_URL)
                if (notice.isEmpty()) return@task
                notice.forEach(audience::sendMessage)
                val pending = snapshots.count { it.state == UpdateState.UPDATE_AVAILABLE || it.state == UpdateState.UPDATE_STAGED }
                showTitle(player, pending)
            }
            .build())
    }

    /** The two-argument title is the only one 1.8 servers have; newer servers merely deprecate it. */
    @Suppress("DEPRECATION")
    private fun showTitle(player: Player, pending: Int) {
        val (title, subtitle) = BukkitTitle.updatesPending(pending)
        player.sendTitle(title, subtitle)
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
}
