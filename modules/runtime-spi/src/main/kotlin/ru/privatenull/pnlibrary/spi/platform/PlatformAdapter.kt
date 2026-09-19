package ru.privatenull.pnlibrary.spi.platform

import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.spi.metrics.NoopMetricsFactory
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandAdapter
import ru.privatenull.pnlibrary.spi.commands.UnsupportedPlatformCommandAdapter
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
interface PlatformAdapter : AutoCloseable {
    /** Normalized family represented by this adapter. */
    val type: PlatformType

    /** Stable machine-readable platform identifier. */
    val id: String get() = type.id

    /** Human-readable native implementation name, such as `Paper` or `Velocity`. */
    val implementationName: String get() = type.displayName

    /** Whether this adapter represents a proxy platform. */
    val isProxy: Boolean get() = type.isProxy

    /** Whether this adapter represents a Minecraft server platform. */
    val isServer: Boolean get() = type.isServer

    /** pnLibrary's native data directory, or `null` when the platform does not expose one. */
    val dataFolder: Path? get() = null

    /** Factory for native metrics sessions; defaults to a no-op implementation. */
    val metricsFactory: PlatformMetricsFactory get() = NoopMetricsFactory

    /** Native command bridge used by the shared command service. */
    val commandAdapter: PlatformCommandAdapter get() = UnsupportedPlatformCommandAdapter

    /**
     * Binds the fully initialized [library] to commands, listeners, and other native entry points.
     * Called once during bootstrap after the runtime can safely service requests.
     */
    fun bind(library: PnLibrary) = Unit

    /** Routes one structured log entry to the logger associated with [owner] when possible. */
    fun log(owner: Any, level: LogLevel, message: String, error: Throwable? = null) {
        val prefix = "[pnLibrary/${level.name}] "
        if (error == null) System.out.println(prefix + message)
        else {
            System.err.println(prefix + message)
            error.printStackTrace(System.err)
        }
    }

    /** Sends a legacy-formatted informational message to the native console. */
    fun console(owner: Any, message: String) = log(owner, LogLevel.INFO, message)

    /**
     * Returns native metadata for [owner].
     *
     * Recognized keys are `id`, `name`, `version`, and `authors`. Unknown owner types should return
     * an empty map instead of failing.
     */
    fun ownerDetails(owner: Any): Map<String, String> = emptyMap()

    /** Returns a non-sensitive structured platform snapshot for ordinary diagnostics. */
    fun details(): Map<String, Any?>

    /** Rich report-only snapshot. Sensitive values must only be returned when explicitly allowed. */
    fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> = details()

    /**
     * Installs a passive bridge for warnings and errors emitted directly by the native platform.
     * Passing `null` removes the current bridge. Implementations must avoid feeding pnLibrary's own
     * forwarded messages back into [observer].
     */
    fun observeNativeLogs(observer: ((Any, LogLevel, String, Throwable?) -> Unit)?) = Unit

    /**
     * Dispatches [task] through the platform-safe global execution context.
     *
     * On Bukkit this is the primary/global scheduler; proxy platforms may use their general async
     * scheduler because they do not expose Bukkit-style main-thread ownership.
     */
    fun executeGlobal(task: Runnable)

    /**
     * Dispatches [task] in the execution context safe for [recipient].
     *
     * Folia implementations should use the recipient's entity scheduler. Platforms without an
     * entity execution model may delegate to [executeGlobal]. Unknown recipient types must fall
     * back safely rather than being cast unconditionally.
     */
    fun executeReply(recipient: Any, task: Runnable)

    /** Removes native hooks and rejects or ignores future dispatches. */
    override fun close()
}
