package ru.privatenull.pnlibrary.bungee

import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.CommandSender
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.plugin.Command
import net.md_5.bungee.api.plugin.Plugin
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticCommandEvent
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticCommandExecutor
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level

/**
 * Runtime adapter for BungeeCord-compatible proxy servers.
 *
 * BungeeCord has no player-region scheduler, so global and recipient dispatch both use its async
 * plugin scheduler. [bind] owns the `/pndebug` command registration and [close] removes it.
 */
internal class BungeePlatformAdapter(
    /** Native plugin instance that owns scheduler, logger, command, and metrics resources. */
    val plugin: Plugin,
) : PlatformAdapter {

    private val closedFlag = AtomicBoolean(false)
    private val bound = AtomicBoolean(false)

    override val type = PlatformType.BUNGEECORD
    override val implementationName: String get() = plugin.proxy.name.ifBlank { type.displayName }
    override val metricsFactory: PlatformMetricsFactory = BungeeMetricsFactory()
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
    private var diagnosticCommands: DiagnosticCommandExecutor? = null
    private val debugCommand = object : Command("pndebug", "pnlibrary.debug", "pnlib") {
        override fun execute(sender: CommandSender, args: Array<String>) {
            val executor = diagnosticCommands
                ?: return sender.sendMessage(TextComponent("${ChatColor.RED}pnLibrary is not ready"))
            executor.execute(args, false, sender.name, sender) { event ->
                sender.sendMessage(TextComponent(message(event)))
            }
        }
    }

    override fun bind(library: PnLibrary) {
        check(!closedFlag.get()) { "Bungee platform adapter is closed" }
        check(bound.compareAndSet(false, true)) { "Bungee platform adapter is already bound" }
        diagnosticCommands = DiagnosticCommandExecutor(library)
        try {
            plugin.proxy.pluginManager.registerCommand(plugin, debugCommand)
        } catch (error: Throwable) {
            diagnosticCommands = null
            bound.set(false)
            throw error
        }
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
        if (bound.compareAndSet(true, false)) {
            plugin.proxy.pluginManager.unregisterCommand(debugCommand)
        }
        diagnosticCommands = null
    }

    private fun message(event: DiagnosticCommandEvent): String = when (event) {
        DiagnosticCommandEvent.InvalidUsage ->
            "${ChatColor.YELLOW}/pndebug [all|plugin] [--full|--config|--logs] [--local]"
        is DiagnosticCommandEvent.CoolingDown ->
            "${ChatColor.YELLOW}Wait ${event.seconds}s before creating another report."
        is DiagnosticCommandEvent.Started ->
            "${ChatColor.GRAY}Collecting diagnostic report for ${event.target}..."
        is DiagnosticCommandEvent.Completed -> {
            val report = event.report
            val output = report.uploadedUrl ?: report.localFile.toString()
            val warning = report.uploadError?.let { " Upload failed; local report kept: $it" }.orEmpty()
            "${ChatColor.GREEN}Report ready: $output${if (warning.isEmpty()) "" else "${ChatColor.YELLOW}$warning"}"
        }
        is DiagnosticCommandEvent.Failed -> "${ChatColor.RED}Report failed: ${event.message}"
    }
}
