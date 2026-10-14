package ru.privatenull.pnlibrary.spi.platform

import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskAdapter
import ru.privatenull.pnlibrary.spi.tasks.UnsupportedPlatformTaskAdapter

interface PlatformTasks {
    val taskAdapter: PlatformTaskAdapter get() = UnsupportedPlatformTaskAdapter
}
