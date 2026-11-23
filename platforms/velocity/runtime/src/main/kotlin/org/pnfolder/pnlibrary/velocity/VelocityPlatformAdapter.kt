package org.pnfolder.pnlibrary.velocity

import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.remote.RemotePolicyContext
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandAdapter
import org.pnfolder.pnlibrary.spi.metrics.PlatformMetricsFactory
import org.pnfolder.pnlibrary.spi.platform.PlatformPlugins
import org.pnfolder.pnlibrary.spi.platform.PlatformSnapshot
import org.pnfolder.pnlibrary.spi.platform.PluginSnapshot
import org.pnfolder.pnlibrary.spi.platform.ProxyPlatformAdapter
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter
import org.pnfolder.pnlibrary.velocity.commands.VelocityCommandAdapter
import org.pnfolder.pnlibrary.velocity.diagnostics.VelocityDiagnosticDetailsCollector
import org.pnfolder.pnlibrary.velocity.log.VelocityLogObserver
import org.pnfolder.pnlibrary.velocity.network.VelocityNetworkProxy
import org.pnfolder.pnlibrary.velocity.network.VelocityPlayerAdapter
import org.pnfolder.pnlibrary.velocity.remote.VelocityRemotePolicyContextFactory
import org.pnfolder.pnlibrary.velocity.tasks.VelocityTaskAdapter
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.jvm.optionals.getOrDefault

/**
 * Runtime adapter for Velocity proxy servers.
 *
 * Composes native command, task, audience, plugin metadata and diagnostics bridges.
 * Startup-dependent callbacks wait for [markReady], called by the plugin entry point after
 * runtime creation and native API registration. Velocity has no entity-region scheduler.
 */
internal class VelocityPlatformAdapter(
    /** The pnLibrary plugin; every task and internal work such as global tasks runs on its behalf. */
    private val plugin: Any,
    private val proxy: ProxyServer,
    override val metricsFactory: PlatformMetricsFactory,
    override val dataFolder: Path,
) : ProxyPlatformAdapter(plugin) {
    private val nativeLogger = LoggerFactory.getLogger(plugin::class.java)
    private val diagnosticDetailsCollector = VelocityDiagnosticDetailsCollector(proxy)
    private val legacySerializer = LegacyComponentSerializer.legacySection()

    private val plugins = PlatformPlugins<PluginContainer>(
        platformName = "Velocity",
        installed = { proxy.pluginManager.plugins },
        resolve = { owner -> proxy.pluginManager.fromInstance(owner).orElse(null) },
    ) { container ->
        val description = container.description
        PluginSnapshot(
            id = description.id,
            name = description.name.getOrDefault(description.id),
            version = description.version.getOrDefault("unknown"),
            authors = description.authors,
        )
    }

    private val logObserver = VelocityLogObserver(
        plugin,
        pluginInstances = { proxy.pluginManager.plugins.mapNotNull { it.instance.orElse(null) } },
        pluginById = { id -> proxy.pluginManager.getPlugin(id.lowercase()).orElse(null)?.instance?.orElse(null) },
    )

    override val type: PlatformType = PlatformType.VELOCITY

    override val implementationName: String
        get() = proxy.version.name.ifBlank { type.displayName }

    override val networkProxy: VelocityNetworkProxy = VelocityNetworkProxy(proxy)

    override val playerAdapter: VelocityPlayerAdapter =
        VelocityPlayerAdapter(proxy)

    override val commandAdapter: PlatformCommandAdapter =
        VelocityCommandAdapter(proxy, playerAdapter)

    override val taskAdapter: PlatformTaskAdapter =
        VelocityTaskAdapter(proxy, plugin, ::warn)

    override val logHandler: (Any, LogLevel, String, Throwable?) -> Unit =
        { _, level, message, error ->
            when (level) {
                LogLevel.WARNING -> nativeLogger.warn(message, error)
                LogLevel.ERROR, LogLevel.CRITICAL -> nativeLogger.error(message, error)
                else -> nativeLogger.info(message, error)
            }
        }

    override fun observeProxyLogs(observer: ((Any, LogLevel, String, Throwable?) -> Unit)?) {
        logObserver.observe(observer)
    }

    override fun console(owner: Any, message: String) {
        proxy.consoleCommandSource.sendMessage(legacySerializer.deserialize(message))
    }

    override fun ownerMetadata(owner: Any): PluginSnapshot? = plugins.metadata(owner, this)

    override fun installedPlugins(): Map<String, String> = plugins.versions()

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
    ): RemotePolicyContext {
        val installed = plugins.requireInstalled(owner, "Remote policy owner")
        return VelocityRemotePolicyContextFactory.create(owner, installed, proxy, values)
    }

    /** Dispatches work on Velocity's scheduler on behalf of the pnLibrary plugin. */
    override fun executeGlobal(task: Runnable) {
        if (lifecycle.isClosed) return

        taskAdapter.buildTask().task(task).schedule()
    }
}
