package org.pnfolder.pnlibrary.core.runtime.platform

import org.pnfolder.pnlibrary.core.commands.StandardCommandFeedback
import org.pnfolder.pnlibrary.api.tasks.TaskServiceSettings
import org.pnfolder.pnlibrary.core.audiences.AudienceServiceImpl
import org.pnfolder.pnlibrary.core.commands.CommandServiceImpl
import org.pnfolder.pnlibrary.core.commands.DEFAULT_NO_PERMISSION
import org.pnfolder.pnlibrary.core.tasks.TaskServiceImpl
import org.pnfolder.pnlibrary.spi.audiences.PlatformAudienceAdapter
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandAdapter
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter

/** Creates the services that are direct translations of native platform adapters. */
internal class PlatformServiceSet(
    commandAdapter: PlatformCommandAdapter,
    audienceAdapter: PlatformAudienceAdapter,
    taskAdapter: PlatformTaskAdapter,
    platformLogging: PlatformLogging,
    taskHistoryCapacity: Int,
    errorLogger: (Any, String, Throwable) -> Unit,
    commandFeedback: StandardCommandFeedback = StandardCommandFeedback(),
) {
    val tasks = TaskServiceImpl(
        adapter = taskAdapter,
        settings = TaskServiceSettings(taskHistoryCapacity),
        errorLogger = errorLogger,
    )
    val commands = CommandServiceImpl(commandAdapter, platformLogging, commandFeedback)
    val audiences = AudienceServiceImpl(audienceAdapter)
}
