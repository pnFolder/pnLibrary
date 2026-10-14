package ru.privatenull.pnlibrary.spi.platform

import ru.privatenull.pnlibrary.spi.commands.PlatformCommandAdapter
import ru.privatenull.pnlibrary.spi.commands.UnsupportedPlatformCommandAdapter

interface PlatformCommands {
    val commandAdapter: PlatformCommandAdapter get() = UnsupportedPlatformCommandAdapter
}
