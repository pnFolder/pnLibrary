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
import ru.privatenull.pnlibrary.spi.platform.UncaughtExceptionLogObserver
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
    private val diagnosticDetailsCollector = VelocityDiagnosticDetailsCollector(server)
    private val closed = AtomicBoolean()
    private val bound = AtomicBoolean()

    private var updateCommand: CommandRegistration? = null
    private val uncaughtExceptionObserver = UncaughtExceptionLogObserver(plugin)

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

    /** Sends a legacy-formatted message to the Velocity console. */
    override fun console(owner: Any, message: String) {
        server.consoleCommandSource.sendMessage(
            LEGACY_SERIALIZER.deserialize(message),
        )
    }

    /** Installs or removes the process-wide uncaught-exception observer. */
    @Synchronized
    override fun observeNativeLogs(observer: ((Any, LogLevel, String, Throwable?) -> Unit)?) {
        uncaughtExceptionObserver.observe(observer)
    }

    /** Returns metadata for a native Velocity plugin owner. */
    override fun ownerMetadata(owner: Any): PluginSnapshot? {
        val description = server.pluginManager.plugins
            .firstOrNull { it.instance.orElse(null) === owner }
            ?.description
            ?: return unsupportedOwner(owner)

        return description.toSnapshot()
    }

    /** Returns installed Velocity plugin IDs and their versions. */
    override fun installedPlugins(): Map<String, String> =
        server.pluginManager.plugins.associate { installedPlugin ->
            installedPlugin.description.id to
                installedPlugin.description.version.orElse("unknown")
        }

    /** Captures a lightweight, non-sensitive snapshot of the proxy. */
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

    /** Collects the detailed platform report used by support diagnostics. */
    override fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> =
        diagnosticDetailsCollector.collect(snapshot(), includeSensitive)

    /** Builds the native context passed to a remotely loaded policy. */
    override fun remotePolicyContext(
        owner: Any,
        metadata: PluginMetadata,
        values: Map<String, String>,
    ): RemotePolicyContext =
        VelocityRemotePolicyContextFactory.create(owner, server, values)

    /** Registers pnLibrary-owned commands and marks the adapter as active. */
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

    /** Schedules work on Velocity's plugin scheduler. */
    override fun executeGlobal(task: Runnable) {
        if (closed.get()) {
            return
        }

        server.scheduler.buildTask(plugin, task).schedule()
    }

    /** Dispatches a reply task using the proxy-wide scheduler. */
    override fun executeReply(recipient: Any, task: Runnable) {
        executeGlobal(task)
    }

    /** Releases commands, observers and lifecycle state owned by the adapter. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        observeNativeLogs(null)

        updateCommand?.close()
        updateCommand = null

        bound.set(false)
    }

    /** Converts native plugin metadata to the shared immutable representation. */
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
