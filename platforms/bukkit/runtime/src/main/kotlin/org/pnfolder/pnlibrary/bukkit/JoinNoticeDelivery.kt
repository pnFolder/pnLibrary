package org.pnfolder.pnlibrary.bukkit

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.tasks.TaskSpec
import java.time.Duration

/** Builds the journal notice off the server thread and sends it from the player's own thread. */
internal class JoinNoticeDelivery(private val tasks: TaskScope) {
    /** Reading the journal can take long, so [build] runs asynchronously; only [send] touches the player. */
    fun deliver(player: Player, build: () -> List<Component>, send: (List<Component>) -> Unit) {
        tasks.schedule(TaskSpec.builder()
            .execution(TaskExecution.async())
            .delay(Duration.ofMillis(50))
            .action {
                val lines = build()
                if (lines.isNotEmpty()) tasks.schedule(sendTask(player, lines, send))
            }
            .build())
    }

    private fun sendTask(player: Player, lines: List<Component>, send: (List<Component>) -> Unit): TaskSpec =
        TaskSpec.builder()
            .execution(TaskExecution.entity(player))
            .action { if (player.isOnline) send(lines) }
            .build()
}
