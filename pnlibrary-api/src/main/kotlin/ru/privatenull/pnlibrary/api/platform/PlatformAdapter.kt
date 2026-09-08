package ru.privatenull.pnlibrary.api.platform

import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.metrics.NoopMetricsFactory
import ru.privatenull.pnlibrary.api.metrics.PlatformMetricsFactory
import ru.privatenull.pnlibrary.api.runtime.PnLibrary

/**
 * The only boundary between the shared core and a concrete server platform.
 *
 * An adapter owns platform operations only: native logging, server metadata,
 * scheduler dispatch, commands, and listeners. Shared behavior belongs in
 * `core`, and an adapter must never import another platform API.
 */
interface PlatformAdapter : AutoCloseable {

    /** API family used by the current server or proxy implementation. */
    val type: PlatformType

    /** Stable family identifier used for program logic and diagnostics. */
    val id: String get() = type.id

    /**
     * Human-readable implementation name reported by the running software.
     * This may be Paper, Folia, NullCordX, a private fork, or any future fork.
     * Consumers must not use this value for compatibility decisions.
     */
    val implementationName: String get() = type.displayName

    /** Whether the current platform family is a proxy. */
    val isProxy: Boolean get() = type.isProxy

    /** Whether the current platform family is a game server. */
    val isServer: Boolean get() = type.isServer

    /** Data directory owned by the installed pnLibrary runtime. */
    val dataFolder: java.nio.file.Path? get() = null

    /** Factory for native bStats sessions on this platform. */
    val metricsFactory: PlatformMetricsFactory get() = NoopMetricsFactory

    /**
     * Binds a fully initialized runtime and registers native commands/listeners.
     * Called once by the bootstrap after the global provider is installed.
     */
    fun bind(library: PnLibrary) = Unit

    /** Writes a message through the owner's native platform logger. */
    fun log(owner: Any, level: LogLevel, message: String, error: Throwable? = null) {
        val prefix = "[pnLibrary/${level.name}] "
        if (error == null) System.out.println(prefix + message)
        else {
            System.err.println(prefix + message)
            error.printStackTrace(System.err)
        }
    }

    /** Writes an already formatted line directly to the server console. */
    fun console(owner: Any, message: String) = log(owner, LogLevel.INFO, message)

    /**
     * Returns public metadata for the plugin that owns a resource.
     * Supported keys are `id`, `name`, `version`, and `authors`.
     */
    fun ownerDetails(owner: Any): Map<String, String> = emptyMap()

    /** Returns platform-specific data for diagnostic reports. */
    fun details(): Map<String, Any?>

    /**
     * Schedules [task] to run on the server's "global" execution context:
     * - Bukkit/Spigot/Paper: the main server thread
     * - Folia: the global region scheduler
     * - BungeeCord/Waterfall: the proxy dispatch thread
     * - Velocity: the Velocity scheduler
     */
    fun executeGlobal(task: Runnable)

    /**
     * Schedules [task] to deliver a reply to [recipient].
     *
     * For a Folia player, this dispatches to the player's entity scheduler.
     * For all other cases it is equivalent to [executeGlobal].
     *
     * @param recipient The command sender or equivalent proxy object.
     */
    fun executeReply(recipient: Any, task: Runnable)

    /** Cancels platform work and unregisters native handlers. */
    override fun close()
}
