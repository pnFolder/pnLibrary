package ru.privatenull.pnlibrary.api.runtime

import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticsService
import ru.privatenull.pnlibrary.api.logging.LoggingService
import ru.privatenull.pnlibrary.api.metrics.MetricsService
import ru.privatenull.pnlibrary.api.platform.PlatformAdapter
import ru.privatenull.pnlibrary.api.tasks.TaskService
import ru.privatenull.pnlibrary.api.updates.UpdateService
import java.io.Closeable

/** Shared service owned by the single installed pnLibrary platform runtime. */
interface PnLibrary : Closeable {
    /** Runtime version used for dependency compatibility checks. */
    val version: String

    /** Checks whether this runtime provides at least the requested release. */
    fun isAtLeastVersion(minimumVersion: String): Boolean

    /** The owning plugin instance (e.g. Bukkit Plugin, Bungee Plugin, or Velocity object). */
    val owner: Any

    /** Platform adapter supplying scheduler dispatch and platform diagnostics. */
    val platform: PlatformAdapter

    /** Public diagnostic service for container registration and status updates. */
    val diagnostics: DiagnosticsService

    /** pnLibrary-owned abstraction over bStats. The project ID is supplied per plugin. */
    val metrics: MetricsService

    /** Unified native logging and startup/status boxes. */
    val logging: LoggingService

    /** Centralized mandatory updater for registered pnFolder plugins. */
    val updates: UpdateService

    /** Cross-platform task scopes with automatic lifecycle cancellation. */
    val tasks: TaskService

    /** Whether this library instance has been closed. */
    val isClosed: Boolean

    override fun close()

}
