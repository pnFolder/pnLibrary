package ru.privatenull.pnlibrary.core.runtime

import ru.privatenull.pnlibrary.api.tasks.TaskServiceSettings
import ru.privatenull.pnlibrary.core.audiences.AudienceServiceImpl
import ru.privatenull.pnlibrary.core.commands.CommandServiceImpl
import ru.privatenull.pnlibrary.core.tasks.TaskServiceImpl
import ru.privatenull.pnlibrary.spi.audiences.PlatformAudienceAdapter
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandAdapter
import ru.privatenull.pnlibrary.spi.platform.PlatformLogging
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskAdapter

/** Creates the services that are direct translations of native platform adapters. */
internal class PlatformServiceSet(
    commandAdapter: PlatformCommandAdapter,
    audienceAdapter: PlatformAudienceAdapter,
    taskAdapter: PlatformTaskAdapter,
    platformLogging: PlatformLogging,
    taskHistoryCapacity: Int,
    errorLogger: (Any, String, Throwable) -> Unit,
) {
    val tasks = TaskServiceImpl(
        adapter = taskAdapter,
        settings = TaskServiceSettings(taskHistoryCapacity),
        errorLogger = errorLogger,
    )
    val commands = CommandServiceImpl(commandAdapter, platformLogging)
    val audiences = AudienceServiceImpl(audienceAdapter)
}
