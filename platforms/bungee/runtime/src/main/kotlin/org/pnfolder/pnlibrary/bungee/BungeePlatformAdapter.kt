package org.pnfolder.pnlibrary.bungee

import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.plugin.Plugin
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.proxy.NetworkProxy
import org.pnfolder.pnlibrary.api.remote.RemotePolicyContext
import org.pnfolder.pnlibrary.bungee.commands.BungeeCommandAdapter
import org.pnfolder.pnlibrary.bungee.remote.BungeeRemotePolicyContextFactory
import org.pnfolder.pnlibrary.bungee.tasks.BungeeTaskAdapter
import org.pnfolder.pnlibrary.spi.audiences.PlatformAudienceAdapter
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandAdapter
import org.pnfolder.pnlibrary.spi.metrics.PlatformMetricsFactory
import org.pnfolder.pnlibrary.spi.platform.JulLogObserver
import org.pnfolder.pnlibrary.spi.platform.PlatformPlugins
import org.pnfolder.pnlibrary.spi.platform.PlatformSnapshot
import org.pnfolder.pnlibrary.spi.platform.PluginSnapshot
import org.pnfolder.pnlibrary.spi.platform.ProxyPlatformAdapter
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter
import java.nio.file.Path
import java.util.logging.Logger

/** Runtime adapter for BungeeCord-compatible proxy servers. */
internal class BungeePlatformAdapter(
    /** The pnLibrary plugin; internal work such as global tasks belongs to it. */
    val plugin: Plugin,
    private val pnLogger: () -> PnLogger,
) : ProxyPlatformAdapter(plugin) {
    private val diagnosticDetailsCollector = BungeeDiagnosticDetailsCollector(plugin)

    private val plugins = PlatformPlugins<Plugin>(
        platformName = "BungeeCord",
        installed = { plugin.proxy.pluginManager.plugins },
        resolve = { owner -> owner as? Plugin },
    ) { installed ->
        val description = installed.description
        PluginSnapshot(
            id = description.name,
            name = description.name,
            version = description.version,
            authors = listOfNotNull(description.author),
            mainClass = description.main,
        )
    }

    private val logObserver = JulLogObserver({ plugin.proxy.logger }, plugin) { loggerName ->
        JulLogObserver.matchPlugin(loggerName, plugin.proxy.pluginManager.plugins) { it.description.name }
    }

    override val type: PlatformType = PlatformType.BUNGEECORD
    override val dataFolder = plugin.dataFolder.toPath()

    override val implementationName: String
        get() = plugin.proxy.name.ifBlank { type.displayName }

    override val metricsFactory: PlatformMetricsFactory =
        BungeeMetricsFactory(pnLogger)

    override val networkProxy: BungeeNetworkProxy = BungeeNetworkProxy(plugin.proxy)

    override val commandAdapter: PlatformCommandAdapter =
        BungeeCommandAdapter(plugin)

    override val audienceAdapter: PlatformAudienceAdapter =
        BungeeAudienceAdapter(plugin)

    override val taskAdapter: PlatformTaskAdapter =
        BungeeTaskAdapter(plugin)

    override val logHandler: (Any, LogLevel, String, Throwable?) -> Unit =
        logObserver.logHandler { owner -> (owner as? Plugin)?.logger ?: plugin.logger }

    override fun observeProxyLogs(observer: ((Any, LogLevel, String, Throwable?) -> Unit)?) {
        logObserver.observe(observer)
    }

    @Suppress("DEPRECATION")
    override fun console(owner: Any, message: String) {
        plugin.proxy.console.sendMessage(
            *TextComponent.fromLegacyText(message),
        )
    }

    override fun ownerMetadata(owner: Any): PluginSnapshot? = plugins.metadata(owner, this)

    override fun ownerDataFolder(owner: Any): Path? = plugins.find(owner)?.dataFolder?.toPath()

    override fun ownerLogger(owner: Any): Logger? = plugins.find(owner)?.logger

    override fun installedPlugins(): Map<String, String> = plugins.versions()

    override fun snapshot(): PlatformSnapshot {
        val proxy = plugin.proxy

        return PlatformSnapshot(
            name = proxy.name,
            version = proxy.version,
            onlinePlayers = proxy.onlineCount,
            registeredServers = proxy.servers.keys.toList(),
            plugins = plugins.snapshots(),
        )
    }

    override fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> =
        diagnosticDetailsCollector.collect(snapshot(), includeSensitive)

    override fun remotePolicyContext(
        owner: Any,
        values: Map<String, String>,
    ): RemotePolicyContext =
        BungeeRemotePolicyContextFactory.create(plugins.requireInstalled(owner, "Remote policy owner"), values)

    /** Dispatches work asynchronously; BungeeCord has no global main-thread scheduler. */
    override fun executeGlobal(task: Runnable) {
        if (lifecycle.isClosed) return

        plugin.proxy.scheduler.runAsync(plugin, task)
    }
}
