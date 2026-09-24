package ru.privatenull.pnlibrary.bungee

import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.plugin.Plugin
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.plugin.PluginMetadata
import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.bungee.commands.BungeeCommandAdapter
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandAdapter
import ru.privatenull.pnlibrary.spi.audiences.PlatformAudienceAdapter
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.bungee.tasks.BungeeTaskAdapter
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskAdapter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import ru.privatenull.pnlibrary.api.commands.CommandRegistration
import ru.privatenull.pnlibrary.core.updates.ProxyUpdateCommand

/**
 * Runtime adapter for BungeeCord-compatible proxy servers.
 *
 * BungeeCord has no player-region scheduler, so global and recipient dispatch both use its async
 * plugin scheduler. Portable commands are registered by [commandAdapter].
 */
internal class BungeePlatformAdapter(
    /** Native plugin instance that owns scheduler, logger, command, and metrics resources. */
    val plugin: Plugin,
) : PlatformAdapter {

    private val closedFlag = AtomicBoolean(false)
    private val bound = AtomicBoolean(false)
    private var updateCommand: CommandRegistration? = null

    override val type = PlatformType.BUNGEECORD
    override val implementationName: String get() = plugin.proxy.name.ifBlank { type.displayName }
    override val metricsFactory: PlatformMetricsFactory = BungeeMetricsFactory()
    override val commandAdapter: PlatformCommandAdapter = BungeeCommandAdapter(plugin)
    override val audienceAdapter: PlatformAudienceAdapter = BungeeAudienceAdapter(plugin)
    override val taskAdapter: PlatformTaskAdapter = BungeeTaskAdapter(plugin)
    override val dataFolder = plugin.dataFolder.toPath()

    override fun log(owner: Any, level: LogLevel, message: String, error: Throwable?) {
        val nativeLevel = when (level) {
            LogLevel.WARNING -> Level.WARNING
            LogLevel.ERROR -> Level.SEVERE
            else -> Level.INFO
        }
        if (error == null) plugin.logger.log(nativeLevel, message)
        else plugin.logger.log(nativeLevel, message, error)
    }

    @Suppress("DEPRECATION")
    override fun console(owner: Any, message: String) {
        plugin.proxy.console.sendMessage(*TextComponent.fromLegacyText(message))
    }

    override fun ownerDetails(owner: Any): Map<String, String> {
        val target = owner as? Plugin ?: return emptyMap()
        return linkedMapOf(
            "id" to target.description.name,
            "name" to target.description.name,
            "version" to target.description.version,
            "authors" to (target.description.author ?: "pnFolder"),
        )
    }
    override fun remotePolicyContext(owner: Any, metadata: PluginMetadata, values: Map<String, String>): RemotePolicyContext =
        BungeeRemotePolicyContextFactory.create(owner as Plugin, values)
    override fun installedPlugins(): Map<String, String> = plugin.proxy.pluginManager.plugins.associate {
        it.description.name to it.description.version
    }

    override fun bind(library: PnLibrary) {
        check(!closedFlag.get()) { "Bungee platform adapter is closed" }
        check(bound.compareAndSet(false, true)) { "Bungee platform adapter is already bound" }
        updateCommand = library.commands.register(plugin, ProxyUpdateCommand(library).definition())
    }

    override fun details(): Map<String, Any?> {
        val proxy = plugin.proxy
        val data = linkedMapOf<String, Any?>()

        data["proxyName"] = proxy.name
        data["proxyVersion"] = proxy.version
        data["onlinePlayersCount"] = proxy.onlineCount
        data["registeredServersCount"] = proxy.servers.size
        data["registeredServerNames"] = proxy.servers.keys.toList()

        data["plugins"] = proxy.pluginManager.plugins.map { installedPlugin ->
            val description = installedPlugin.description
            linkedMapOf(
                "name" to description.name,
                "version" to description.version,
                "mainClass" to description.main,
                "author" to description.author,
            )
        }
        return data
    }

    override fun executeGlobal(task: Runnable) {
        if (closedFlag.get()) return
        plugin.proxy.scheduler.runAsync(plugin, task)
    }

    override fun executeReply(recipient: Any, task: Runnable) {
        executeGlobal(task)
    }

    override fun close() {
        if (!closedFlag.compareAndSet(false, true)) return
        updateCommand?.close()
        updateCommand = null
        bound.set(false)
    }
}
