package ru.privatenull.pnlibrary.bungee

import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.plugin.Plugin
import ru.privatenull.pnlibrary.api.commands.CommandRegistration
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.plugin.PluginMetadata
import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.bungee.commands.BungeeCommandAdapter
import ru.privatenull.pnlibrary.bungee.tasks.BungeeTaskAdapter
import ru.privatenull.pnlibrary.core.updates.ProxyUpdateCommand
import ru.privatenull.pnlibrary.spi.audiences.PlatformAudienceAdapter
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandAdapter
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.spi.platform.PlatformSnapshot
import ru.privatenull.pnlibrary.spi.platform.PluginSnapshot
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskAdapter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level

/** Runtime adapter for BungeeCord-compatible proxy servers. */
internal class BungeePlatformAdapter(
    val plugin: Plugin,
) : PlatformAdapter {

    private val closed = AtomicBoolean()
    private val bound = AtomicBoolean()

    private var updateCommand: CommandRegistration? = null

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

    @Suppress("DEPRECATION")
    override fun console(owner: Any, message: String) {
        plugin.proxy.console.sendMessage(
            *TextComponent.fromLegacyText(message),
        )
    }

    override fun ownerMetadata(owner: Any): PluginSnapshot? {
        val target = owner as? Plugin
            ?: return unsupportedOwner(owner)

        return target.toSnapshot()
    }

    override fun installedPlugins(): Map<String, String> =
        plugin.proxy.pluginManager.plugins.associate { installedPlugin ->
            installedPlugin.description.name to installedPlugin.description.version
        }

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

    override fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> {
        val details = snapshot().asMap().toMutableMap()
        details["pluginDependencies"] = plugin.proxy.pluginManager.plugins.map { installedPlugin ->
            val metadata = installedPlugin.description
            linkedMapOf<String, Any?>(
                "id" to metadata.name,
                "depends" to metadata.depends.toList().sorted(),
                "softDepends" to metadata.softDepends.toList().sorted(),
                "libraries" to metadata.libraries.toList().sorted(),
            )
        }
        details["pluginArtifacts"] = plugin.proxy.pluginManager.plugins.map { installedPlugin ->
            val metadata = installedPlugin.description
            val file = metadata.file
            linkedMapOf<String, Any?>(
                "id" to metadata.name,
                "path" to if (includeSensitive) file?.absolutePath else "[REDACTED]",
                "sizeBytes" to file?.takeIf { it.isFile }?.length(),
                "lastModifiedUtc" to file?.takeIf { it.isFile }?.let(::formatUtc),
            )
        }
        details["servers"] = plugin.proxy.servers.map { (name, info) ->
            linkedMapOf<String, Any?>(
                "name" to name,
                "address" to info.address.toString(),
                "players" to info.players.size,
            )
        }
        if (includeSensitive) {
            details["players"] = plugin.proxy.players.map { player ->
                linkedMapOf<String, Any?>(
                    "username" to player.name,
                    "uuid" to player.uniqueId.toString(),
                    "ping" to player.ping,
                    "server" to player.server?.info?.name,
                )
            }
        } else {
            details["players"] = "redacted"
        }
        return details
    }

    private fun formatUtc(file: java.io.File): String =
        java.time.Instant.ofEpochMilli(file.lastModified()).toString()

    override fun remotePolicyContext(
        owner: Any,
        metadata: PluginMetadata,
        values: Map<String, String>,
    ): RemotePolicyContext {
        val target = owner as? Plugin
            ?: throw IllegalArgumentException(
                "Remote policy owner must be a BungeeCord plugin; " +
                    "received ${owner.javaClass.name}",
            )

        return BungeeRemotePolicyContextFactory.create(target, values)
    }

    override fun bind(library: PnLibrary) {
        check(!closed.get()) {
            "Bungee platform adapter is closed"
        }
        check(bound.compareAndSet(false, true)) {
            "Bungee platform adapter is already bound"
        }

        updateCommand = library.commands.register(
            plugin,
            ProxyUpdateCommand(library).definition(),
        )
    }

    override fun executeGlobal(task: Runnable) {
        if (closed.get()) return

        plugin.proxy.scheduler.runAsync(plugin, task)
    }

    override fun executeReply(recipient: Any, task: Runnable) {
        executeGlobal(task)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        updateCommand?.close()
        updateCommand = null

        bound.set(false)
    }

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
