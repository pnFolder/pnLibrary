package ru.privatenull.pnlibrary.spi.platform

/** Native resource shutdown boundary owned by a platform adapter. */
interface PlatformShutdown : AutoCloseable {
    /** Removes native hooks and releases platform-owned resources. */
    override fun close()
}
