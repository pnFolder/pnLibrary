package ru.privatenull.pnlibrary.spi.platform

import java.nio.file.Path

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
interface PlatformAdapter : PlatformShutdown, PlatformIdentity, PlatformExecution, PlatformBinding,
    PlatformReadiness, PlatformOwnership, PlatformDiagnostics, PlatformLogObservation, PlatformLogging,
    PlatformMetrics, PlatformCommands, PlatformAudience, PlatformTasks {
    /** Durable data root required by the shared runtime. */
    val dataFolder: Path
}
