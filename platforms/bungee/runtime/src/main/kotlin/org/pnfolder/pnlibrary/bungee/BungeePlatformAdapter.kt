package org.pnfolder.pnlibrary.bungee

import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.plugin.Plugin
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.remote.RemotePolicyContext
import org.pnfolder.pnlibrary.bungee.commands.BungeeCommandAdapter
import org.pnfolder.pnlibrary.bungee.remote.BungeeRemotePolicyContextFactory
import org.pnfolder.pnlibrary.bungee.tasks.BungeeTaskAdapter
import org.pnfolder.pnlibrary.spi.audiences.PlatformAudienceAdapter
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandAdapter
import org.pnfolder.pnlibrary.spi.metrics.PlatformMetricsFactory
import org.pnfolder.pnlibrary.spi.platform.JulLogObserver
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter
import org.pnfolder.pnlibrary.spi.platform.PlatformLifecycle
import org.pnfolder.pnlibrary.spi.platform.PlatformPlugins
import org.pnfolder.pnlibrary.spi.platform.PlatformSnapshot
import org.pnfolder.pnlibrary.spi.platform.PluginSnapshot
import org.pnfolder.pnlibrary.spi.platform.StartupGate
import org.pnfolder.pnlibrary.spi.platform.UncaughtExceptionLogObserver
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter

/** Runtime adapter for BungeeCord-compatible proxy servers. */
internal class BungeePlatformAdapter(
    val plugin: Plugin,
) : PlatformAdapter {
    private val diagnosticDetailsCollector = BungeeDiagnosticDetailsCollector(plugin)

    private val lifecycle = PlatformLifecycle()
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
    private val startup = StartupGate(lifecycle, ::executeGlobal)

    private val uncaughtExceptionObserver = UncaughtExceptionLogObserver(plugin)
    private val logObserver = JulLogObserver({ plugin.proxy.logger }, plugin) { loggerName ->
        JulLogObserver.matchPlugin(loggerName, plugin.proxy.pluginManager.plugins) { it.description.name }
    }

    override val type: PlatformType = PlatformType.BUNGEECORD
    override val dataFolder = plugin.dataFolder.toPath()

    override val implementationName: String
        get() = plugin.proxy.name.ifBlank { type.displayName }

    override val metricsFactory: PlatformMetricsFactory =
        BungeeMetricsFactory()

    override val commandAdapter: PlatformCommandAdapter =
        BungeeCommandAdapter(plugin)

    override val audienceAdapter: PlatformAudienceAdapter =
        BungeeAudienceAdapter(plugin)

    override val taskAdapter: PlatformTaskAdapter =
        BungeeTaskAdapter(plugin)

    /** Writes to the owning plugin's logger, falling back to pnLibrary's own. */
    override val logHandler: (Any, LogLevel, String, Throwable?) -> Unit =
        logObserver.logHandler { owner -> (owner as? Plugin)?.logger ?: plugin.logger }

    /** Forwards proxy and plugin warnings/errors and uncaught JVM exceptions, preserving the previous handler. */
    @Synchronized
    override fun observeNativeLogs(observer: ((Any, LogLevel, String, Throwable?) -> Unit)?) {
        if (observer != null) lifecycle.checkOpen()
        logObserver.observe(observer)
        uncaughtExceptionObserver.observe(observer)
    }

    /** Sends legacy-formatted text to the proxy console. */
    @Suppress("DEPRECATION")
    override fun console(owner: Any, message: String) {
        plugin.proxy.console.sendMessage(
            *TextComponent.fromLegacyText(message),
        )
    }

    /** Reads native plugin metadata, warning and returning null for unsupported owners. */
    override fun ownerMetadata(owner: Any): PluginSnapshot? = plugins.metadata(owner, this)

    /** Returns installed plugin names and versions for compatibility checks. */
    override fun installedPlugins(): Map<String, String> = plugins.versions()

    /** Collects typed proxy metadata without exposing individual player identities. */
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

    /** Delegates diagnostic collection with the requested privacy policy. */
    override fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> =
        diagnosticDetailsCollector.collect(snapshot(), includeSensitive)

    /** Builds the remote-policy context for a validated native plugin owner. */
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

    /** Waits until the entry point has published the runtime and native platform API. */
    override fun whenServerReady(task: Runnable) {
        startup.whenReady(task)
    }

    /** Releases startup callbacks once; other managed plugins may still use the startup grace. */
    internal fun markReady() {
        startup.markReady()
    }

    /** Dispatches recipient work through the same asynchronous proxy scheduler. */
    override fun executeReply(recipient: Any, task: Runnable) {
        executeGlobal(task)
    }

    /** Discards pending startup work, cancels native tasks and stops native log observation once. */
    override fun close() {
        lifecycle.closeAdapter(startup, taskAdapter) { observeNativeLogs(null) }
    }
}
