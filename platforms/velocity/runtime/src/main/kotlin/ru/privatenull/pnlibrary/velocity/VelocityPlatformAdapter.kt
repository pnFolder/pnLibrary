package ru.privatenull.pnlibrary.velocity

import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.slf4j.Logger
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandAdapter
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.spi.platform.PlatformSnapshot
import ru.privatenull.pnlibrary.spi.platform.PluginSnapshot
import ru.privatenull.pnlibrary.spi.platform.UncaughtExceptionLogObserver
import ru.privatenull.pnlibrary.spi.platform.PlatformLifecycle
import ru.privatenull.pnlibrary.spi.platform.StartupGate
import ru.privatenull.pnlibrary.spi.platform.unsupportedOwner
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskAdapter
import ru.privatenull.pnlibrary.velocity.commands.VelocityCommandAdapter
import ru.privatenull.pnlibrary.velocity.tasks.VelocityTaskAdapter
import java.nio.file.Path
import java.time.Duration
import ru.privatenull.pnlibrary.api.tasks.TaskExecution
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskRequest
import ru.privatenull.pnlibrary.velocity.remote.VelocityRemotePolicyContextFactory

/**
 * Runtime adapter for Velocity proxy servers.
 *
 * Composes native command, task, audience, plugin metadata and diagnostics bridges.
 * Consumer tasks carry their own plugin owner; internal global/reply work belongs to [plugin].
 * Startup-dependent callbacks wait for [markReady], called by the plugin entry point after
 * runtime creation and native API registration. Velocity has no entity-region scheduler.
 */
internal class VelocityPlatformAdapter(
    private val plugin: Any,
    private val proxy: ProxyServer,
    override val metricsFactory: PlatformMetricsFactory,
    override val dataFolder: Path,
    logger: Logger,
) : PlatformAdapter {
    private val diagnosticDetailsCollector = VelocityDiagnosticDetailsCollector(proxy)
    private val lifecycle = PlatformLifecycle()
    private val plugins = VelocityPluginRegistry(proxy)
    private val startup = StartupGate(lifecycle, ::executeGlobal)

    private val uncaughtExceptionObserver = UncaughtExceptionLogObserver(plugin)

    override val type: PlatformType = PlatformType.VELOCITY

    override val implementationName: String
        get() = proxy.version.name.ifBlank { type.displayName }

    override val audienceAdapter: VelocityAudienceAdapter =
        VelocityAudienceAdapter(proxy)

    override val commandAdapter: PlatformCommandAdapter =
        VelocityCommandAdapter(proxy, audienceAdapter)

    override val taskAdapter: PlatformTaskAdapter =
        VelocityTaskAdapter(proxy)

    override val logHandler: (Any, LogLevel, String, Throwable?) -> Unit =
        { _, level, message, error ->
            when (level) {
                LogLevel.WARNING -> logger.warn(message, error)
                LogLevel.ERROR -> logger.error(message, error)
                else -> logger.info(message, error)
            }
        }

    /** Sends a legacy-formatted message to the Velocity console. */
    override fun console(owner: Any, message: String) {
        proxy.consoleCommandSource.sendMessage(
            LEGACY_SERIALIZER.deserialize(message),
        )
    }

    /** Installs or removes the process-wide uncaught-exception observer. */
    @Synchronized
    override fun observeNativeLogs(observer: ((Any, LogLevel, String, Throwable?) -> Unit)?) {
        if (observer != null) lifecycle.checkOpen()
        uncaughtExceptionObserver.observe(observer)
    }

    /** Returns metadata for a native Velocity plugin owner. */
    override fun ownerMetadata(owner: Any): PluginSnapshot? {
        return plugins.snapshot(owner) ?: unsupportedOwner(owner, this)
    }

    /** Returns installed Velocity plugin IDs and their versions. */
    override fun installedPlugins(): Map<String, String> =
        plugins.versions()

    /** Captures a lightweight, non-sensitive snapshot of the proxy. */
    override fun snapshot(): PlatformSnapshot {
        val platformVersion = proxy.version

        return PlatformSnapshot(
            name = platformVersion.name,
            version = platformVersion.version,
            vendor = platformVersion.vendor,
            onlinePlayers = proxy.playerCount,
            registeredServers = proxy.allServers.map { it.serverInfo.name },
            plugins = plugins.snapshots(),
        )
    }

    /** Collects the detailed platform report used by support diagnostics. */
    override fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> =
        diagnosticDetailsCollector.collect(snapshot(), includeSensitive)

    /** Builds the native context passed to a remotely loaded policy. */
    override fun remotePolicyContext(
        owner: Any,
        values: Map<String, String>,
    ): RemotePolicyContext {
        return VelocityRemotePolicyContextFactory.create(owner, proxy, values)
    }

    /** Schedules work on Velocity's plugin scheduler. */
    override fun executeGlobal(task: Runnable) {
        if (lifecycle.isClosed) {
            return
        }

        taskAdapter.schedule(PlatformTaskRequest(
            TaskExecution.Kind.GLOBAL, null, Duration.ZERO, null, task, plugin,
        ))
    }

    /** Defers startup-dependent work until the entry point has published the native API. */
    override fun whenServerReady(task: Runnable) {
        startup.whenReady(task)
    }

    /** Called once the runtime and Velocity API registration both succeeded. */
    internal fun markReady() {
        startup.markReady()
    }

    /** Dispatches a reply task using the proxy-wide scheduler. */
    override fun executeReply(recipient: Any, task: Runnable) {
        executeGlobal(task)
    }

    /** Discards pending startup work, cancels native tasks and removes the exception observer. */
    override fun close() {
        lifecycle.close {
            startup.discardPending()
            try {
                taskAdapter.close()
            } finally {
                observeNativeLogs(null)
            }
        }
    }

    private companion object {
        val LEGACY_SERIALIZER: LegacyComponentSerializer =
            LegacyComponentSerializer.legacySection()
    }
}
