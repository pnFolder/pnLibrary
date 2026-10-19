package org.pnfolder.pnlibrary.api.runtime

import org.pnfolder.pnlibrary.api.activity.ActivityService
import org.pnfolder.pnlibrary.api.audiences.AudienceService
import org.pnfolder.pnlibrary.api.config.ConfigurationService
import org.pnfolder.pnlibrary.api.diagnostics.DebugRequest
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticReport
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsService
import org.pnfolder.pnlibrary.api.diagnostics.ObservabilityService
import org.pnfolder.pnlibrary.api.events.EventService
import org.pnfolder.pnlibrary.api.logging.LoggingService
import org.pnfolder.pnlibrary.api.metrics.MetricsService
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapterRegistry
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderValueStore
import org.pnfolder.pnlibrary.api.platform.PlatformProvider
import org.pnfolder.pnlibrary.api.plugin.PluginRegistry
import org.pnfolder.pnlibrary.api.services.ServiceManager
import org.pnfolder.pnlibrary.api.updates.UpdateService
import java.io.Closeable

/**
 * Main pnLibrary facade shared by all plugins in one server process.
 *
 * Consumers obtain this interface through [PnLibraryProvider]. They must not
 * depend on `core` implementation classes, so the internals can evolve without
 * breaking installed plugins.
 */
interface PnLibrary : Closeable {
    /** Version of the installed runtime. */
    val version: String

    /** Returns `true` when this runtime is not older than [minimumVersion]. */
    fun isAtLeastVersion(minimumVersion: String): Boolean

    /** Native platform plugin instance that owns this runtime. */
    val owner: Any

    /** Validated configuration of the installed runtime. */
    val configuration: PnLibraryConfig

    /** Registry for diagnostic data contributed by consumer plugins. */
    val diagnostics: DiagnosticsService

    /** Unified diagnostics and activity entry point for support data. */
    val observability: ObservabilityService
        get() = throw UnsupportedOperationException("Observability service is not available in this runtime")

    /** Local bounded activity journal used by diagnostics and support reports. */
    val activity: ActivityService
        get() = throw UnsupportedOperationException("Activity journal is not available in this runtime")

    /** Managed bStats sessions; every plugin supplies its own project ID. */
    val metrics: MetricsService

    /** Native logging and formatted lifecycle summaries. */
    val logging: LoggingService

    /** Typed YAML configuration factory for Java and Kotlin plugins. */
    val configurations: ConfigurationService

    /** Update checks and staged downloads for registered plugins. */
    val updates: UpdateService

    /** Cross-platform player, console, native-sender, and composite audiences. */
    val audiences: AudienceService
        get() = throw UnsupportedOperationException("Audiences are not available in this runtime")

    /** Typed process-wide services with plugin-owned registrations. */
    val services: ServiceManager

    /** Mode-aware cross-platform event bus with plugin-ID-bound subscriptions. */
    val events: EventService

    /** Global registry and high-level entry point for consumer plugins. */
    val plugins: PluginRegistry

    /** Type-safe native API for the active Bukkit, Bungee, or Velocity runtime. */
    val platforms: PlatformProvider

    /**
     * Runtime-detected placeholder bridges such as PlaceholderAPI.
     *
     * Consumer plugins normally use their plugin context instead of registering adapters here.
     */
    val placeholderAdapters: PlaceholderAdapterRegistry

    /** Global registered value store available through pnLibrary placeholders. */
    val placeholderValues: PlaceholderValueStore

    /** Builds a diagnostic report from an already validated request. */
    fun createDiagnosticReport(request: DebugRequest): DiagnosticReport

    /** Whether [close] has begun and the runtime no longer accepts normal work. */
    val isClosed: Boolean

    /**
     * Stops owned plugins and services, releases resources, and clears [PnLibraryProvider].
     *
     * Closing the runtime more than once has no additional effect.
     */
    override fun close()
}
