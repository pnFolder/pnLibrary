package ru.privatenull.pnlibrary.spi.platform

import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.spi.metrics.NoopMetricsFactory
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory
import java.nio.file.Path
import java.util.UUID

/**
 * Runtime-only boundary between the shared engine and a native platform.
 * Consumer plugins must use [PnLibrary] instead of depending on this SPI.
 */
interface PlatformAdapter : AutoCloseable {
    val type: PlatformType
    val id: String get() = type.id
    val implementationName: String get() = type.displayName
    val isProxy: Boolean get() = type.isProxy
    val isServer: Boolean get() = type.isServer

    val dataFolder: Path? get() = null
    val metricsFactory: PlatformMetricsFactory get() = NoopMetricsFactory

    fun bind(library: PnLibrary) = Unit

    fun log(owner: Any, level: LogLevel, message: String, error: Throwable? = null) {
        val prefix = "[pnLibrary/${level.name}] "
        if (error == null) System.out.println(prefix + message)
        else {
            System.err.println(prefix + message)
            error.printStackTrace(System.err)
        }
    }

    fun console(owner: Any, message: String) = log(owner, LogLevel.INFO, message)
    fun ownerDetails(owner: Any): Map<String, String> = emptyMap()
    fun details(): Map<String, Any?>
    /** Rich report-only snapshot. Sensitive values must only be returned when explicitly allowed. */
    fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> = details()
    /** Installs a passive bridge for warnings/errors emitted directly by the native platform. */
    fun observeNativeLogs(observer: ((Any, LogLevel, String, Throwable?) -> Unit)?) = Unit
    fun executeGlobal(task: Runnable)
    fun executeReply(recipient: Any, task: Runnable)
    /** Executes one already-resolved action on the platform's safe player thread. */
    fun executePlayerAction(owner: Any, playerId: UUID, action: PlatformPlayerAction) {
        throw UnsupportedOperationException("Player actions are unavailable on $implementationName")
    }
    override fun close()
}
