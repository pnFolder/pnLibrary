package ru.privatenull.pnlibrary.velocity

import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.slf4j.Logger
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.velocity.commands.VelocityCommandAdapter
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandAdapter
import ru.privatenull.pnlibrary.spi.audiences.PlatformAudienceAdapter
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.velocity.tasks.VelocityTaskAdapter
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskAdapter
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import ru.privatenull.pnlibrary.api.commands.CommandRegistration
import ru.privatenull.pnlibrary.core.updates.ProxyUpdateCommand

/**
 * Runtime adapter for Velocity 3.x proxy servers.
 *
 * Velocity has no player-region scheduler, so global and recipient dispatch both use its plugin
 * scheduler. Portable commands are registered by [commandAdapter].
 */
internal class VelocityPlatformAdapter(
    /** Native plugin instance used to own commands, tasks, and metrics. */
    val plugin: Any,
    /** Velocity proxy used for metadata, commands, audiences, and scheduling. */
    val server: ProxyServer,
    override val metricsFactory: PlatformMetricsFactory,
    override val dataFolder: Path,
    private val logger: Logger,
) : PlatformAdapter {

    private val closedFlag = AtomicBoolean(false)
    private val bound = AtomicBoolean(false)
    private var updateCommand: CommandRegistration? = null
    override val type = PlatformType.VELOCITY
    override val implementationName: String get() = server.version.name.ifBlank { type.displayName }
    override val commandAdapter: PlatformCommandAdapter = VelocityCommandAdapter(plugin, server)
    override val audienceAdapter: PlatformAudienceAdapter = VelocityAudienceAdapter(server)
    override val taskAdapter: PlatformTaskAdapter = VelocityTaskAdapter(plugin, server)

    override fun log(owner: Any, level: LogLevel, message: String, error: Throwable?) {
        val log: (String, Throwable?) -> Unit = when (level) {
            LogLevel.WARNING -> logger::warn
            LogLevel.ERROR -> logger::error
            else -> logger::info
        }

        log(message, error)
    }

    override fun console(owner: Any, message: String) {
        server.consoleCommandSource.sendMessage(LEGACY_SERIALIZER.deserialize(message))
    }

    override fun ownerDetails(owner: Any): Map<String, String> {
        val description = server.pluginManager.plugins
            .firstOrNull { it.instance.orElse(null) === owner }
            ?.description ?: return emptyMap()
        return linkedMapOf(
            "id" to description.id,
            "name" to description.name.orElse(description.id),
            "version" to description.version.orElse("неизвестна"),
            "authors" to description.authors.joinToString(", ").ifBlank { "pnFolder" },
        )
    }

    override fun installedPlugins(): Map<String, String> = server.pluginManager.plugins.associate {
        it.description.id to it.description.version.orElse("unknown")
    }

    override fun bind(library: PnLibrary) {
        check(!closedFlag.get()) { "Velocity platform adapter is closed" }
        check(bound.compareAndSet(false, true)) { "Velocity platform adapter is already bound" }
        updateCommand = library.commands.register(plugin, ProxyUpdateCommand(library).definition())
    }

    override fun details(): Map<String, Any?> {
        val data = linkedMapOf<String, Any?>()
        val version = server.version

        data["velocityName"] = version.name
        data["velocityVersion"] = version.version
        data["velocityVendor"] = version.vendor
        data["onlinePlayersCount"] = server.playerCount
        data["registeredServersCount"] = server.allServers.size
        data["registeredServerNames"] = server.allServers.map { it.serverInfo.name }

        data["plugins"] = server.pluginManager.plugins.map { container ->
            val description = container.description
            linkedMapOf(
                "id" to description.id,
                "name" to description.name.orElse(description.id),
                "version" to description.version.orElse("unknown"),
                "authors" to description.authors,
            )
        }
        return data
    }

    override fun executeGlobal(task: Runnable) {
        if (closedFlag.get()) return
        server.scheduler.buildTask(plugin, task).schedule()
    }

    override fun executeReply(recipient: Any, task: Runnable) {
        executeGlobal(task)
    }

    private companion object {
        val LEGACY_SERIALIZER: LegacyComponentSerializer = LegacyComponentSerializer.legacySection()
    }

    override fun close() {
        if (!closedFlag.compareAndSet(false, true)) return
        updateCommand?.close()
        updateCommand = null
        bound.set(false)
    }
}
