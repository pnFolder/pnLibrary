package ru.privatenull.pnlibrary.bungee

import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.plugin.Plugin
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext
import ru.privatenull.pnlibrary.bungee.commands.BungeeCommandAdapter
import ru.privatenull.pnlibrary.bungee.tasks.BungeeTaskAdapter
import ru.privatenull.pnlibrary.spi.audiences.PlatformAudienceAdapter
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
import java.util.logging.Level
import ru.privatenull.pnlibrary.bungee.remote.BungeeRemotePolicyContextFactory

/** Runtime adapter for BungeeCord-compatible proxy servers. */
internal class BungeePlatformAdapter(
    val plugin: Plugin,
) : PlatformAdapter {
    private val diagnosticDetailsCollector = BungeeDiagnosticDetailsCollector(plugin)

    private val lifecycle = PlatformLifecycle()
    private val startup = StartupGate(lifecycle, ::executeGlobal)

    private val uncaughtExceptionObserver = UncaughtExceptionLogObserver(plugin)

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

    override val logHandler: (Any, LogLevel, String, Throwable?) -> Unit =
        { _, level, message, error ->
            val nativeLevel = when (level) {
                LogLevel.WARNING -> Level.WARNING
                LogLevel.ERROR -> Level.SEVERE
                else -> Level.INFO
            }

            plugin.logger.log(nativeLevel, message, error)
        }

    /** Installs an uncaught-exception observer while preserving the previous JVM handler. */
    @Synchronized
    override fun observeNativeLogs(observer: ((Any, LogLevel, String, Throwable?) -> Unit)?) {
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
    override fun ownerMetadata(owner: Any): PluginSnapshot? {
        val target = owner as? Plugin
            ?: return unsupportedOwner(owner, this)

        return target.toSnapshot()
    }

    /** Returns installed plugin names and versions for compatibility checks. */
    override fun installedPlugins(): Map<String, String> =
        plugin.proxy.pluginManager.plugins.associate { installedPlugin ->
            installedPlugin.description.name to installedPlugin.description.version
        }

    /** Collects typed proxy metadata without exposing individual player identities. */
    override fun snapshot(): PlatformSnapshot {
        val proxy = plugin.proxy

        return PlatformSnapshot(
            name = proxy.name,
            version = proxy.version,
            onlinePlayers = proxy.onlineCount,
            registeredServers = proxy.servers.keys.toList(),
            plugins = proxy.pluginManager.plugins.map { it.toSnapshot() },
        )
    }

    /** Delegates diagnostic collection with the requested privacy policy. */
    override fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> =
        diagnosticDetailsCollector.collect(snapshot(), includeSensitive)

    /** Builds the remote-policy context for a validated native plugin owner. */
    override fun remotePolicyContext(
        owner: Any,
        values: Map<String, String>,
    ): RemotePolicyContext {
        val target = owner as? Plugin
            ?: throw IllegalArgumentException(
                "Remote policy owner must be a BungeeCord plugin; " +
                    "received ${owner.javaClass.name}",
            )

        require(target in plugin.proxy.pluginManager.plugins) {
            "Remote policy owner is not registered in the BungeeCord proxy: " +
                target.javaClass.name
        }

        return BungeeRemotePolicyContextFactory.create(target, values)
    }

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

    /** Releases observers and command registrations once. */
    override fun close() {
        lifecycle.close {
            startup.discardPending()
            observeNativeLogs(null)
        }
    }

    /** Converts native plugin metadata to the shared snapshot contract. */
    private fun Plugin.toSnapshot(): PluginSnapshot {
        val metadata = description

        return PluginSnapshot(
            id = metadata.name,
            name = metadata.name,
            version = metadata.version,
            authors = listOfNotNull(metadata.author),
            mainClass = metadata.main,
        )
    }
}
