package ru.privatenull.pnlibrary.spi.platform


/**
 * Runtime-only boundary between the shared engine and a native server platform.
 *
 * Implementations translate native ownership, logging, diagnostics, scheduling, and metrics into
 * contracts understood by `pnlibrary-core`. Consumer plugins must use [PnLibrary] instead of this
 * SPI; no source or binary compatibility is promised to ordinary consumers.
 *
 * Unless a method says otherwise, an adapter must tolerate calls from pnLibrary worker threads.
 * [close] must be idempotent and prevent newly submitted platform work where practical.
 */
interface PlatformAdapter : AutoCloseable, PlatformIdentity, PlatformExecution, PlatformOwnership,
    PlatformDiagnostics, PlatformLogging, PlatformServices {

    /** Removes native hooks and rejects or ignores future dispatches. */
    override fun close()
}
