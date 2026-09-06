package ru.privatenull.pnlibrary.bungee

import net.md_5.bungee.api.plugin.Plugin
import net.md_5.bungee.api.CommandSender
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.plugin.Command
import ru.privatenull.pnlibrary.api.DebugRequest
import ru.privatenull.pnlibrary.api.PlatformAdapter
import ru.privatenull.pnlibrary.api.PlatformMetricsFactory
import ru.privatenull.pnlibrary.api.LogLevel
import ru.privatenull.pnlibrary.core.PnLibraryImpl
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level

/**
 * Platform adapter targeting BungeeCord / Waterfall proxy servers.
 */
class BungeePlatformAdapter(
    val plugin: Plugin,
) : PlatformAdapter {

    private val closedFlag = AtomicBoolean(false)

    override val id: String get() = "bungeecord"
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
    private var library: PnLibraryImpl? = null
    private val debugCommand = object : Command("pndebug", "pnlibrary.debug", "pnlib") {
        override fun execute(sender: CommandSender, args: Array<String>) {
            val active = library ?: return sender.sendMessage(TextComponent("${ChatColor.RED}pnLibrary is not ready"))
            val request = runCatching { DebugRequest.parse(args, false) }.getOrElse {
                return sender.sendMessage(TextComponent("${ChatColor.YELLOW}/pndebug [all|plugin] [--full|--config|--logs] [--local]"))
            }
            sender.sendMessage(TextComponent("${ChatColor.GRAY}Collecting encrypted diagnostic report..."))
            plugin.proxy.scheduler.runAsync(plugin) {
                runCatching { active.generateReport(request) }
                    .onSuccess { result ->
                        val output = result.uploadReceipt?.link ?: result.localFile.toString()
                        sender.sendMessage(TextComponent("${ChatColor.GREEN}Report ready: $output"))
                    }
                    .onFailure { sender.sendMessage(TextComponent("${ChatColor.RED}Report failed: ${it.message}")) }
            }
        }
    }

    fun attachLibrary(runtime: PnLibraryImpl) {
        library = runtime
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
        library = null
    }
}
