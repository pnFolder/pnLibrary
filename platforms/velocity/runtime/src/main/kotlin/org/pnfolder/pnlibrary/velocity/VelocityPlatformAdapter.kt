package org.pnfolder.pnlibrary.velocity

import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.remote.RemotePolicyContext
import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandAdapter
import org.pnfolder.pnlibrary.spi.metrics.PlatformMetricsFactory
import org.pnfolder.pnlibrary.spi.platform.PlatformSnapshot
import org.pnfolder.pnlibrary.spi.platform.PluginSnapshot
import org.pnfolder.pnlibrary.spi.platform.ProxyPlatformAdapter
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskRequest
import org.pnfolder.pnlibrary.velocity.commands.VelocityCommandAdapter
import org.pnfolder.pnlibrary.velocity.remote.VelocityRemotePolicyContextFactory
import org.pnfolder.pnlibrary.velocity.tasks.VelocityTaskAdapter
import org.slf4j.Logger
import java.nio.file.Path
import java.time.Duration

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
) : ProxyPlatformAdapter(plugin) {
    private val diagnosticDetailsCollector = VelocityDiagnosticDetailsCollector(proxy)
    private val plugins = velocityPlugins(proxy)

    private val logObserver = VelocityLogObserver(
        plugin,
        pluginInstances = { proxy.pluginManager.plugins.mapNotNull { it.instance.orElse(null) } },
        pluginById = { id -> proxy.pluginManager.getPlugin(id.lowercase()).orElse(null)?.instance?.orElse(null) },
    )

    override val type: PlatformType = PlatformType.VELOCITY

    override val implementationName: String
        get() = proxy.version.name.ifBlank { type.displayName }

    override val audienceAdapter: VelocityAudienceAdapter =
        VelocityAudienceAdapter(proxy)

    override val commandAdapter: PlatformCommandAdapter =
        VelocityCommandAdapter(proxy, audienceAdapter)

    override val taskAdapter: PlatformTaskAdapter =
        VelocityTaskAdapter(
            server = proxy,
            describeOwner = { owner ->
                owner?.let { proxy.pluginManager.fromInstance(it).orElse(null)?.description?.name?.orElse(null) }
                    ?: owner?.javaClass?.simpleName
                    ?: "неизвестен"
            },
            warning = { message -> logger.error(message) },
        )

    override val logHandler: (Any, LogLevel, String, Throwable?) -> Unit =
        { _, level, message, error ->
            logObserver.runOwn {
                when (level) {
                    LogLevel.WARNING -> logger.warn(message, error)
                    LogLevel.ERROR, LogLevel.CRITICAL -> logger.error(message, error)
                    else -> logger.info(message, error)
                }
            }
        }

    override fun console(owner: Any, message: String) {
        proxy.consoleCommandSource.sendMessage(
            LEGACY_SERIALIZER.deserialize(message),
        )
    }

    override fun observeProxyLogs(observer: ((Any, LogLevel, String, Throwable?) -> Unit)?) {
        logObserver.observe(observer)
    }

    override fun ownerMetadata(owner: Any): PluginSnapshot? = plugins.metadata(owner, this)

    override fun installedPlugins(): Map<String, String> =
        plugins.versions()

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

    override fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> =
        diagnosticDetailsCollector.collect(snapshot(), includeSensitive)

    override fun remotePolicyContext(
        owner: Any,
        values: Map<String, String>,
    ): RemotePolicyContext =
        VelocityRemotePolicyContextFactory.create(owner, plugins.requireInstalled(owner, "Remote policy owner"), proxy, values)

    override fun executeGlobal(task: Runnable) {
        if (lifecycle.isClosed) {
            return
        }

        taskAdapter.schedule(PlatformTaskRequest(
            TaskExecution.Kind.GLOBAL, null, Duration.ZERO, null, task, plugin,
        ))
    }

    private companion object {
        val LEGACY_SERIALIZER: LegacyComponentSerializer =
            LegacyComponentSerializer.legacySection()
    }
}
