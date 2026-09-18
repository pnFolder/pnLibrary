package ru.privatenull.pnlibrary.velocity

import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.ConsoleCommandSource
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.slf4j.Logger
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticCommandEvent
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticCommandExecutor
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runtime adapter for Velocity 3.x proxy servers.
 *
 * Velocity has no player-region scheduler, so global and recipient dispatch both use its plugin
 * scheduler. [bind] owns the `/pndebug` command registration and [close] removes it.
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
    override val type = PlatformType.VELOCITY
    override val implementationName: String get() = server.version.name.ifBlank { type.displayName }

    override fun log(owner: Any, level: LogLevel, message: String, error: Throwable?) {
        when (level) {
            LogLevel.WARNING -> if (error == null) logger.warn(message) else logger.warn(message, error)
            LogLevel.ERROR -> if (error == null) logger.error(message) else logger.error(message, error)
            else -> if (error == null) logger.info(message) else logger.info(message, error)
        }
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

    override fun bind(library: PnLibrary) {
        check(!closedFlag.get()) { "Velocity platform adapter is closed" }
        check(bound.compareAndSet(false, true)) { "Velocity platform adapter is already bound" }
        val diagnosticCommands = DiagnosticCommandExecutor(library)
        val meta = server.commandManager.metaBuilder("pndebug").aliases("pnlib").plugin(plugin).build()
        try {
            server.commandManager.register(meta, object : SimpleCommand {
                override fun execute(invocation: SimpleCommand.Invocation) {
                    val sender = invocation.source()
                    if (!sender.hasPermission("pnlibrary.debug") && sender !is ConsoleCommandSource) {
                        sender.sendMessage(Component.text("Недостаточно прав."))
                        return
                    }
                    diagnosticCommands.execute(
                        invocation.arguments(),
                        prefixed = false,
                        requesterId = sender.toString(),
                        recipient = sender,
                    ) { event -> sender.sendMessage(Component.text(message(event))) }
                }
            })
        } catch (error: Throwable) {
            bound.set(false)
            throw error
        }
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
        if (bound.compareAndSet(true, false)) {
            server.commandManager.unregister("pndebug")
        }
    }

    private fun message(event: DiagnosticCommandEvent): String = when (event) {
        DiagnosticCommandEvent.InvalidUsage ->
            "/pndebug [all|plugin] [--full|--config|--logs] [--local]"
        is DiagnosticCommandEvent.CoolingDown ->
            "Wait ${event.seconds}s before creating another report."
        is DiagnosticCommandEvent.Started ->
            "Collecting diagnostic report for ${event.target}..."
        is DiagnosticCommandEvent.Completed -> {
            val report = event.report
            val output = report.uploadedUrl ?: report.localFile.toString()
            "Report ready: $output" + (report.uploadError?.let { " (upload failed: $it)" } ?: "")
        }
        is DiagnosticCommandEvent.Failed -> "Report failed: ${event.message}"
    }
}
