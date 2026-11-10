package org.pnfolder.pnlibrary.core.runtime.platform

import org.pnfolder.pnlibrary.api.tasks.TaskServiceSettings
import org.pnfolder.pnlibrary.core.audiences.PlatformAudiences
import org.pnfolder.pnlibrary.core.commands.CommandRegistry
import org.pnfolder.pnlibrary.core.commands.StandardCommandFeedback
import org.pnfolder.pnlibrary.core.tasks.ScheduledTasks
import org.pnfolder.pnlibrary.core.tasks.TaskHistoryStore
import org.pnfolder.pnlibrary.spi.audiences.PlatformAudienceAdapter
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandAdapter
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter
import java.nio.file.Path

/** Services built on top of platform adapters: commands, audiences and tasks. */
internal class PlatformServiceSet(
    commandAdapter: PlatformCommandAdapter,
    audienceAdapter: PlatformAudienceAdapter,
    taskAdapter: PlatformTaskAdapter,
    platformLogging: PlatformLogging,
    taskHistoryCapacity: Int,
    taskHistoryFile: Path? = null,
    errorLogger: (Any, String, Throwable) -> Unit,
    commandFeedback: StandardCommandFeedback = StandardCommandFeedback(),
    onAudienceFailure: (Throwable) -> Unit = {},
) {
    val tasks = ScheduledTasks(
        adapter = taskAdapter,
        settings = TaskServiceSettings(taskHistoryCapacity),
        errorLogger = errorLogger,
        store = taskHistoryFile?.let(::TaskHistoryStore),
    )
    val commands = CommandRegistry(commandAdapter, platformLogging, commandFeedback)
    val audiences = PlatformAudiences(audienceAdapter, onAudienceFailure)
}
