package ru.privatenull.pnlibrary.api

/**
 * Minimal contract between the platform-agnostic library core and a concrete
 * server platform (Bukkit, BungeeCord, Velocity, …).
 *
 * Implementations **must not** import classes from other platforms.
 * The interface itself lives in the API module which has zero server-platform
 * compile-time dependencies.
 */
interface PlatformAdapter : AutoCloseable {

    /**
     * A stable identifier for this adapter.
     * Examples: `"bukkit-legacy"`, `"bukkit-folia"`, `"bungee"`, `"velocity"`.
     */
    val id: String

    /** Data directory owned by the installed pnLibrary runtime. */
    val dataFolder: java.nio.file.Path? get() = null

    /** Creates the platform-specific bStats bridge. */
    val metricsFactory: PlatformMetricsFactory get() = NoopMetricsFactory

    /** Writes through the native platform logger. */
    fun log(owner: Any, level: LogLevel, message: String, error: Throwable? = null) {
        val prefix = "[pnLibrary/${level.name}] "
        if (error == null) System.out.println(prefix + message)
        else {
            System.err.println(prefix + message)
            error.printStackTrace(System.err)
        }
    }

    /** Writes one already formatted line directly to the native server console. */
    fun console(owner: Any, message: String) = log(owner, LogLevel.INFO, message)

    /** Public metadata of the plugin that owns a log message. */
    fun ownerDetails(owner: Any): Map<String, String> = emptyMap()

    /**
     * Returns a snapshot of platform-specific diagnostics to include in reports.
     * May be an empty map when no extra information is available.
     */
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

    /** Cancels pending scheduled tasks and releases held resources. */
    override fun close()
}
