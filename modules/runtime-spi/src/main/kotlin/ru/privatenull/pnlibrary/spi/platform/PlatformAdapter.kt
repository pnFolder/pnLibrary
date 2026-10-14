package ru.privatenull.pnlibrary.spi.platform

import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.plugin.PluginMetadata
import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext
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
interface PlatformAdapter : AutoCloseable, PlatformIdentity, PlatformExecution {
    /** Native logging function used by the default [log] implementation. */
    val logHandler: (Any, LogLevel, String, Throwable?) -> Unit
        get() = { _, level, message, error ->
            val line = "[pnLibrary/${level.name}] $message"
            if (level == LogLevel.ERROR) System.err.println(line) else System.out.println(line)
            error?.printStackTrace(System.err)
        }

    /** Factory for native metrics sessions; defaults to a no-op implementation. */
    val metricsFactory: PlatformMetricsFactory get() = NoopMetricsFactory

    /** Native command bridge used by the shared command service. */
    val commandAdapter: PlatformCommandAdapter get() = UnsupportedPlatformCommandAdapter

    /** Native audience resolution and delivery bridge. */
    val audienceAdapter: PlatformAudienceAdapter get() = UnsupportedPlatformAudienceAdapter

    /** Native delayed and repeating task bridge. */
    val taskAdapter: PlatformTaskAdapter get() = UnsupportedPlatformTaskAdapter

    /** Routes one structured log entry to the logger associated with [owner] when possible. */
    fun log(owner: Any, level: LogLevel, message: String, error: Throwable? = null) {
        logHandler(owner, level, message, error)
    }

    /** Sends a legacy-formatted informational message to the native console. */
    fun console(owner: Any, message: String) = log(owner, LogLevel.INFO, message)

    /**
     * Returns native metadata for [owner].
     *
     * Unsupported owners are reported as warnings and return null without interrupting execution.
     */
    fun ownerMetadata(owner: Any): PluginSnapshot? = null

    /** Reports an unsupported owner without throwing an exception. */
    fun unsupportedOwner(owner: Any): PluginSnapshot? {
        log(owner, LogLevel.WARNING,
            "Не удалось получить метаданные плагина на ${type.displayName}: " +
                "объект ${owner.javaClass.name} не является поддерживаемым плагином. " +
                "Операция пропущена; работа продолжается.")
        return null
    }

    fun ownerDetails(owner: Any): Map<String, String> = ownerMetadata(owner)?.let {
        mapOf("id" to it.id, "name" to it.name, "version" to it.version,
            "authors" to it.authors.joinToString(", "))
    }.orEmpty()

    /** Creates the shared remote-policy view with native handles for this platform. */
    fun remotePolicyContext(owner: Any, metadata: PluginMetadata, values: Map<String, String>): RemotePolicyContext =
        throw UnsupportedOperationException("Remote policy is unavailable on ${type.id}")

    /** Disables the native plugin when supported; returns false on platforms without runtime unload. */
    fun disableOwner(owner: Any): Boolean = false

    /**
     * Returns whether [owner] is a native plugin instance understood by this adapter.
     * Adapters should override this when owner recognition requires more than metadata lookup.
     */
    fun acceptsOwner(owner: Any): Boolean = ownerDetails(owner).isNotEmpty()

    /** Installed native plugin names mapped to versions for dependency validation. */
    fun installedPlugins(): Map<String, String> = emptyMap()

    /** Returns a typed, non-sensitive platform snapshot for ordinary diagnostics. */
    fun snapshot(): PlatformSnapshot = PlatformSnapshot.unknown(type.displayName)

    /** Serializes [snapshot] at the compatibility boundary used by diagnostic reports. */
    fun details(): Map<String, Any?> = snapshot().asMap()

    /** Rich report-only snapshot. Sensitive values must only be returned when explicitly allowed. */
    fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> = details()

    /**
     * Installs a passive bridge for warnings and errors emitted directly by the native platform.
     * Passing `null` removes the current bridge. Implementations must avoid feeding pnLibrary's own
     * forwarded messages back into [observer].
     */
    fun observeNativeLogs(observer: ((Any, LogLevel, String, Throwable?) -> Unit)?) = Unit

    /** Removes native hooks and rejects or ignores future dispatches. */
    override fun close()
}
