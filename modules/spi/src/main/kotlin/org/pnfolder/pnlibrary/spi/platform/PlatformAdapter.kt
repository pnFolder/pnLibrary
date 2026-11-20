package org.pnfolder.pnlibrary.spi.platform

import org.pnfolder.pnlibrary.spi.audiences.PlatformAudienceAdapter
import org.pnfolder.pnlibrary.spi.audiences.UnsupportedPlatformAudienceAdapter
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandAdapter
import org.pnfolder.pnlibrary.spi.commands.UnsupportedPlatformCommandAdapter
import org.pnfolder.pnlibrary.spi.metrics.NoopMetricsFactory
import org.pnfolder.pnlibrary.spi.metrics.PlatformMetricsFactory
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter
import org.pnfolder.pnlibrary.spi.tasks.UnsupportedPlatformTaskAdapter
import java.nio.file.Path

/**
 * Runtime-only boundary between the shared engine and a native server platform.
 *
 * Implementations translate native ownership, logging, diagnostics, scheduling, and metrics into
 * contracts understood by `pnlibrary-core`. Consumer plugins must use `PnLibrary` instead of this
 * SPI; no source or binary compatibility is promised to ordinary consumers.
 *
 * Unless a method says otherwise, an adapter must tolerate calls from pnLibrary worker threads.
 * [close] must be idempotent and prevent newly submitted platform work where practical.
 */
interface PlatformAdapter : PlatformShutdown, PlatformIdentity, PlatformExecution, PlatformBinding,
    PlatformReadiness, PlatformOwnership, PlatformDiagnostics, PlatformLogObservation, PlatformLogging {
    /** Durable data root required by the shared runtime. */
    val dataFolder: Path
    /** Factory for native metrics; no-op by default. */
    val metricsFactory: PlatformMetricsFactory get() = NoopMetricsFactory
    /** Native command registration; unsupported by default. */
    val commandAdapter: PlatformCommandAdapter get() = UnsupportedPlatformCommandAdapter
    /** Native message delivery; unsupported by default. */
    val audienceAdapter: PlatformAudienceAdapter get() = UnsupportedPlatformAudienceAdapter
    /** Native task scheduling; unsupported by default. */
    val taskAdapter: PlatformTaskAdapter get() = UnsupportedPlatformTaskAdapter
}
