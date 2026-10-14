package ru.privatenull.pnlibrary.spi.platform

import java.nio.file.Path

/** Native filesystem location used by the shared runtime for durable state. */
interface PlatformStorage {
    val dataFolder: Path? get() = null
}
