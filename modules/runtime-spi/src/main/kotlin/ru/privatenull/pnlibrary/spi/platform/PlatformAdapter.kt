package ru.privatenull.pnlibrary.spi.platform

import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.spi.metrics.NoopMetricsFactory
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandAdapter
import ru.privatenull.pnlibrary.spi.commands.UnsupportedPlatformCommandAdapter
import ru.privatenull.pnlibrary.spi.audiences.PlatformAudienceAdapter
import ru.privatenull.pnlibrary.spi.audiences.UnsupportedPlatformAudienceAdapter
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskAdapter
import ru.privatenull.pnlibrary.spi.tasks.UnsupportedPlatformTaskAdapter

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
    PlatformDiagnostics, PlatformLogging {

    /** Factory for native metrics sessions; defaults to a no-op implementation. */
    val metricsFactory: PlatformMetricsFactory get() = NoopMetricsFactory

    /** Native command bridge used by the shared command service. */
    val commandAdapter: PlatformCommandAdapter get() = UnsupportedPlatformCommandAdapter

    /** Native audience resolution and delivery bridge. */
    val audienceAdapter: PlatformAudienceAdapter get() = UnsupportedPlatformAudienceAdapter

    /** Native delayed and repeating task bridge. */
    val taskAdapter: PlatformTaskAdapter get() = UnsupportedPlatformTaskAdapter

    /** Removes native hooks and rejects or ignores future dispatches. */
    override fun close()
}
