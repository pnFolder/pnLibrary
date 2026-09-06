package ru.privatenull.pnlibrary.velocity

import com.velocitypowered.api.proxy.ProxyServer
import ru.privatenull.pnlibrary.api.PlatformAdapter
import ru.privatenull.pnlibrary.api.PlatformMetricsFactory
import ru.privatenull.pnlibrary.api.DebugRequest
import ru.privatenull.pnlibrary.core.PnLibraryImpl
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.ConsoleCommandSource
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import java.util.concurrent.atomic.AtomicBoolean
import java.nio.file.Path
import org.slf4j.Logger
import ru.privatenull.pnlibrary.api.LogLevel

/**
 * Platform adapter targeting Velocity 3.x proxy servers.
 */
class VelocityPlatformAdapter(
    val plugin: Any,
    val server: ProxyServer,
    override val metricsFactory: PlatformMetricsFactory,
    override val dataFolder: Path,
    private val logger: Logger,
) : PlatformAdapter {

    private val closedFlag = AtomicBoolean(false)
    private var library: PnLibraryImpl? = null

    override val id: String get() = "velocity"

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
            "name" to description.name.orElse(description.id),
            "version" to description.version.orElse("неизвестна"),
            "authors" to description.authors.joinToString(", ").ifBlank { "pnFolder" },
        )
    }

    fun attachLibrary(runtime: PnLibraryImpl) {
        library = runtime
        val meta = server.commandManager.metaBuilder("pndebug").aliases("pnlib").plugin(plugin).build()
        server.commandManager.register(meta, object : SimpleCommand {
            override fun execute(invocation: SimpleCommand.Invocation) {
                val sender = invocation.source()
                if (!sender.hasPermission("pnlibrary.debug") && sender !is ConsoleCommandSource) {
                    sender.sendMessage(Component.text("Недостаточно прав."))
                    return
                }
                val request = runCatching { DebugRequest.parse(invocation.arguments(), false) }.getOrElse {
                    sender.sendMessage(Component.text("/pndebug [all|plugin] [--full|--config|--logs] [--local]"))
                    return
                }
                sender.sendMessage(Component.text("Собираю зашифрованный диагностический отчёт..."))
                server.scheduler.buildTask(plugin, Runnable {
                    runCatching { runtime.generateReport(request) }
                        .onSuccess { result -> sender.sendMessage(Component.text("Отчёт готов: ${result.uploadReceipt?.link ?: result.localFile}")) }
                        .onFailure { sender.sendMessage(Component.text("Ошибка отчёта: ${it.message}")) }
                }).schedule()
            }
        })
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

        val pluginList = mutableListOf<Map<String, Any?>>()
        for (pContainer in server.pluginManager.plugins) {
            val pDesc = pContainer.description
            pluginList.add(linkedMapOf(
                "id" to pDesc.id,
                "name" to pDesc.name.orElse(pDesc.id),
                "version" to pDesc.version.orElse("unknown"),
                "authors" to pDesc.authors,
            ))
        }
        data["plugins"] = pluginList
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
        closedFlag.set(true)
        server.commandManager.unregister("pndebug")
        library = null
    }
}
