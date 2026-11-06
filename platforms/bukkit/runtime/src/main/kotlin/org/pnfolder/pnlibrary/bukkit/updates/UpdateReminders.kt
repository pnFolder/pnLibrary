package org.pnfolder.pnlibrary.bukkit.updates

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.api.tasks.TaskHandle
import org.pnfolder.pnlibrary.api.tasks.TaskSpec
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.bukkit.BukkitTitle
import org.pnfolder.pnlibrary.bukkit.commands.BukkitControlCommand
import org.pnfolder.pnlibrary.core.lifecycle.runtimeTaskScope
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogView
import org.pnfolder.pnlibrary.core.updates.policy.UpdateDefaults
import java.time.Duration

/**
 * Tells administrators in chat about updates: once when they join and again every [UpdateDefaults.REMINDER_INTERVAL]
 * while an update is available or waits for a restart. It reads the state the library already knows, so it never
 * asks GitHub, and it stops by itself when the release is gone. Neither the reminder nor its interval can be turned
 * off by an administrator.
 */
internal class UpdateReminders(
    private val plugin: Plugin,
    private val library: () -> PnLibrary?,
) : AutoCloseable {
    private var timer: TaskHandle? = null

    fun start(): UpdateReminders = apply {
        val active = library() ?: return@apply
        timer = active.runtimeTaskScope().schedule(
            TaskSpec.builder()
                .name("update reminders")
                .description("Напоминание администраторам об обновлениях.")
                .cancellable(false)
                .runnableNow(false)
                .execution(TaskExecution.global())
                .delay(UpdateDefaults.REMINDER_INTERVAL)
                .interval(UpdateDefaults.REMINDER_INTERVAL)
                .action { plugin.server.onlinePlayers.filter(::canSeeUpdates).forEach { remind(it, withTitle = false) } }
                .build(),
        )
    }

    fun canSeeUpdates(player: Player): Boolean = player.isOp || player.hasPermission("*") ||
        player.hasPermission("pnlibrary.admin") || player.hasPermission("pnlibrary.updates.notify")

    /** Sends the update notice to [player] on the player's own thread; a title is shown only on join. */
    fun remind(player: Player, withTitle: Boolean) {
        val active = library() ?: return
        active.runtimeTaskScope().schedule(
            TaskSpec.builder()
                .execution(TaskExecution.entity(player))
                .delay(Duration.ofMillis(50))
                .action { send(active, player, withTitle) }
                .build(),
        )
    }

    private fun send(active: PnLibrary, player: Player, withTitle: Boolean) {
        if (!player.isOnline) return
        val audience = active.audiences.player(player.uniqueId) ?: return
        val snapshots = active.updates.all().map { it.snapshot }
        val notice = UpdateCatalogView.announcement(snapshots, BukkitControlCommand.ROUTES, PnLibraryIdentity.SUPPORT_URL)
        if (notice.isEmpty()) return
        notice.forEach(audience::sendMessage)
        if (!withTitle) return
        val pending = snapshots.count { it.state == UpdateState.UPDATE_AVAILABLE || it.state == UpdateState.UPDATE_STAGED }
        val (title, subtitle) = BukkitTitle.updatesPending(pending)
        @Suppress("DEPRECATION")
        player.sendTitle(title, subtitle)
    }

    override fun close() {
        timer?.cancel()
        timer = null
    }
}
