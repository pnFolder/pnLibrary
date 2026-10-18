package ru.privatenull.pnlibrary.spi.platform

import ru.privatenull.pnlibrary.spi.audiences.PlatformAudienceAdapter
import ru.privatenull.pnlibrary.spi.audiences.UnsupportedPlatformAudienceAdapter
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandAdapter
import ru.privatenull.pnlibrary.spi.commands.UnsupportedPlatformCommandAdapter
import ru.privatenull.pnlibrary.spi.metrics.NoopMetricsFactory
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskAdapter
import ru.privatenull.pnlibrary.spi.tasks.UnsupportedPlatformTaskAdapter
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
    PlatformReadiness, PlatformOwnership, PlatformDiagnostics, PlatformLogObservation, PlatformLogging {
    /** Durable data root required by the shared runtime. */
    val dataFolder: Path
    val metricsFactory: PlatformMetricsFactory get() = NoopMetricsFactory
    val commandAdapter: PlatformCommandAdapter get() = UnsupportedPlatformCommandAdapter
    val audienceAdapter: PlatformAudienceAdapter get() = UnsupportedPlatformAudienceAdapter
    val taskAdapter: PlatformTaskAdapter get() = UnsupportedPlatformTaskAdapter
}
