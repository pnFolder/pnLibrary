package ru.privatenull.pnlibrary.bungee

import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.CommandSender
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.plugin.Command
import net.md_5.bungee.api.plugin.Plugin
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.metrics.PlatformMetricsFactory
import ru.privatenull.pnlibrary.api.platform.PlatformAdapter
import ru.privatenull.pnlibrary.api.platform.PlatformVariant
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticCommandEvent
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticCommandExecutor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level

/**
 * Platform adapter targeting BungeeCord / Waterfall proxy servers.
 */
class BungeePlatformAdapter(
    val plugin: Plugin,
) : PlatformAdapter {

    private val closedFlag = AtomicBoolean(false)

    override val variant: PlatformVariant
        get() = when {
            plugin.proxy.name.equals("Waterfall", ignoreCase = true) -> PlatformVariant.WATERFALL
            plugin.proxy.name.equals("XCord", ignoreCase = true) -> PlatformVariant.XCORD
            else -> PlatformVariant.BUNGEECORD
        }
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
        diagnosticCommands = DiagnosticCommandExecutor(library)
        plugin.proxy.pluginManager.registerCommand(plugin, debugCommand)
    }

    override fun details(): Map<String, Any?> {
        val proxy = plugin.proxy
        val data = linkedMapOf<String, Any?>()

        data["proxyName"] = proxy.name
        data["proxyVersion"] = proxy.version
        data["onlinePlayersCount"] = proxy.onlineCount
        data["registeredServersCount"] = proxy.servers.size
        data["registeredServerNames"] = proxy.servers.keys.toList()

        val pluginList = mutableListOf<Map<String, Any?>>()
        for (pl in proxy.pluginManager.plugins) {
            val pDesc = pl.description
            pluginList.add(linkedMapOf(
                "name" to pDesc.name,
                "version" to pDesc.version,
                "mainClass" to pDesc.main,
                "author" to pDesc.author,
            ))
        }
        data["plugins"] = pluginList
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
        closedFlag.set(true)
        plugin.proxy.pluginManager.unregisterCommand(debugCommand)
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
