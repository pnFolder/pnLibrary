package ru.privatenull.pnlibrary.velocity

import com.velocitypowered.api.plugin.PluginDescription
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.slf4j.Logger
import ru.privatenull.pnlibrary.api.commands.CommandRegistration
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.plugin.PluginMetadata
import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.core.updates.ProxyUpdateCommand
import ru.privatenull.pnlibrary.spi.audiences.PlatformAudienceAdapter
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandAdapter
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.spi.platform.PlatformSnapshot
import ru.privatenull.pnlibrary.spi.platform.PluginSnapshot
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskAdapter
import ru.privatenull.pnlibrary.velocity.commands.VelocityCommandAdapter
import ru.privatenull.pnlibrary.velocity.tasks.VelocityTaskAdapter
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runtime adapter for Velocity proxy servers.
 *
 * Velocity does not expose an entity-region scheduler. Global and recipient work therefore use
 * the native plugin scheduler owned by [plugin].
 */
internal class VelocityPlatformAdapter(
    val plugin: Any,
    val server: ProxyServer,
    override val metricsFactory: PlatformMetricsFactory,
    override val dataFolder: Path,
    private val logger: Logger,
) : PlatformAdapter {

    private val closed = AtomicBoolean()
    private val bound = AtomicBoolean()

    private var updateCommand: CommandRegistration? = null

    override val type: PlatformType = PlatformType.VELOCITY

    override val implementationName: String
        get() = server.version.name.ifBlank { type.displayName }

    override val commandAdapter: PlatformCommandAdapter =
        VelocityCommandAdapter(plugin, server)

    override val audienceAdapter: PlatformAudienceAdapter =
        VelocityAudienceAdapter(server)

    override val taskAdapter: PlatformTaskAdapter =
        VelocityTaskAdapter(plugin, server)

    override val logHandler: (Any, LogLevel, String, Throwable?) -> Unit =
        { _, level, message, error ->
            when (level) {
                LogLevel.WARNING -> logger.warn(message, error)
                LogLevel.ERROR -> logger.error(message, error)
                else -> logger.info(message, error)
            }
        }

    override fun console(owner: Any, message: String) {
        server.consoleCommandSource.sendMessage(
            LEGACY_SERIALIZER.deserialize(message),
        )
    }

    override fun ownerMetadata(owner: Any): PluginSnapshot? {
        val description = server.pluginManager.plugins
            .firstOrNull { it.instance.orElse(null) === owner }
            ?.description
            ?: return unsupportedOwner(owner)

        return description.toSnapshot()
    }

    override fun installedPlugins(): Map<String, String> =
        server.pluginManager.plugins.associate { installedPlugin ->
            installedPlugin.description.id to
                installedPlugin.description.version.orElse("unknown")
        }

    override fun snapshot(): PlatformSnapshot {
        val platformVersion = server.version

        return PlatformSnapshot(
            name = platformVersion.name,
            version = platformVersion.version,
            vendor = platformVersion.vendor,
            onlinePlayers = server.playerCount,
            registeredServers = server.allServers.map { it.serverInfo.name },
            plugins = server.pluginManager.plugins.map { it.description.toSnapshot() },
        )
    }

    override fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> {
        val details = snapshot().asMap().toMutableMap()
        details["pluginDependencies"] = server.pluginManager.plugins.associate { container ->
            container.description.id to container.description.dependencies.map { dependency ->
                linkedMapOf<String, Any?>(
                    "id" to dependency.id,
                    "version" to dependency.version.orElse(null),
                    "optional" to dependency.isOptional,
                )
            }
        }
        val installedPluginIds = server.pluginManager.plugins.map { it.description.id }.toSet()
        details["dependencyHealth"] = server.pluginManager.plugins.map { container ->
            val required = container.description.dependencies.filterNot { it.isOptional }
            val optional = container.description.dependencies.filter { it.isOptional }
            val missingRequired = required.map { it.id }.filterNot(installedPluginIds::contains)
            val missingOptional = optional.map { it.id }.filterNot(installedPluginIds::contains)
            linkedMapOf<String, Any?>(
                "plugin" to container.description.id,
                "missingRequired" to missingRequired,
                "missingOptional" to missingOptional,
                "healthy" to missingRequired.isEmpty(),
            )
        }
        details["pluginArtifacts"] = server.pluginManager.plugins.map { container ->
            val source = container.description.source.orElse(null)?.toFile()
            linkedMapOf<String, Any?>(
                "id" to container.description.id,
                "path" to if (includeSensitive) source?.absolutePath else "[REDACTED]",
                "sizeBytes" to source?.takeIf { it.isFile }?.length(),
                "lastModifiedUtc" to source?.takeIf { it.isFile }?.let {
                    java.time.Instant.ofEpochMilli(it.lastModified()).toString()
                },
            )
        }
        details["servers"] = server.allServers.map { connection ->
            linkedMapOf<String, Any?>(
                "name" to connection.serverInfo.name,
                "players" to connection.playersConnected.size,
            )
        }
        if (includeSensitive) {
            details["players"] = server.allPlayers.map { player ->
                linkedMapOf<String, Any?>(
                    "username" to player.username,
                    "uuid" to player.uniqueId.toString(),
                    "ping" to player.ping,
                    "server" to player.currentServer.map { it.serverInfo.name }.orElse(null),
                )
            }
        } else {
            details["players"] = "redacted"
        }
        return details
    }

    override fun remotePolicyContext(
        owner: Any,
        metadata: PluginMetadata,
        values: Map<String, String>,
    ): RemotePolicyContext =
        VelocityRemotePolicyContextFactory.create(owner, server, values)

    override fun bind(library: PnLibrary) {
        check(!closed.get()) {
            "Velocity platform adapter is closed"
        }
        check(bound.compareAndSet(false, true)) {
            "Velocity platform adapter is already bound"
        }

        updateCommand = library.commands.register(
            plugin,
            ProxyUpdateCommand(library).definition(),
        )
    }

    override fun executeGlobal(task: Runnable) {
        if (closed.get()) return

        server.scheduler.buildTask(plugin, task).schedule()
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

    private fun PluginDescription.toSnapshot(): PluginSnapshot =
        PluginSnapshot(
            id = id,
            name = name.orElse(id),
            version = version.orElse("unknown"),
            authors = authors,
        )

    private companion object {
        val LEGACY_SERIALIZER: LegacyComponentSerializer =
            LegacyComponentSerializer.legacySection()
    }
}
