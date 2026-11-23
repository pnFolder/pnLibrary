package org.pnfolder.pnlibrary.core.runtime.platform

import org.pnfolder.pnlibrary.api.tasks.TaskServiceSettings
import org.pnfolder.pnlibrary.core.commands.engine.CommandRegistry
import org.pnfolder.pnlibrary.core.commands.engine.StandardCommandFeedback
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformPlayers
import org.pnfolder.pnlibrary.core.tasks.ScheduledTasks
import org.pnfolder.pnlibrary.core.tasks.TaskHistoryStore
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandAdapter
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging
import org.pnfolder.pnlibrary.spi.player.PlatformPlayerAdapter
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter
import java.nio.file.Path

/** Services built on top of platform adapters: commands, players and tasks. */
internal class PlatformServiceSet(
    commandAdapter: PlatformCommandAdapter,
    playerAdapter: PlatformPlayerAdapter,
    taskAdapter: PlatformTaskAdapter,
    platformLogging: PlatformLogging,
    taskHistoryCapacity: Int,
    taskHistoryFile: Path? = null,
    errorLogger: (Any, String, Throwable) -> Unit,
    commandFeedback: StandardCommandFeedback = StandardCommandFeedback(),
    onPlayerFailure: (Throwable) -> Unit = {},
) {
    val tasks = ScheduledTasks(
        adapter = taskAdapter,
        settings = TaskServiceSettings(taskHistoryCapacity),
        errorLogger = errorLogger,
        store = taskHistoryFile?.let(::TaskHistoryStore),
    )
    val commands = CommandRegistry(commandAdapter, platformLogging, commandFeedback)
    val players = PlatformPlayers(playerAdapter, onPlayerFailure)
}
