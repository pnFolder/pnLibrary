package ru.privatenull.pnlibrary.bukkit

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.spi.platform.JulLogObserver
import java.util.logging.Level

/** Bridges Bukkit warnings/errors into the platform log observation contract. */
internal class BukkitNativeLogBridge(private val plugin: Plugin) {
    private val observer = JulLogObserver({ Bukkit.getLogger() }, plugin) { loggerName ->
        val installed = Bukkit.getPluginManager().plugins
        JulLogObserver.matchName(loggerName, installed.map { it.name })?.let { name -> installed.first { it.name == name } }
    }

    val logHandler: (Any, LogLevel, String, Throwable?) -> Unit =
        { owner, level, message, error ->
            val logger = (owner as? Plugin)?.logger ?: plugin.logger
            val nativeLevel = when (level) {
                LogLevel.WARNING -> Level.WARNING
                LogLevel.ERROR -> Level.SEVERE
                else -> Level.INFO
            }
            observer.runOwn { logger.log(nativeLevel, message, error) }
        }

    /** Starts or stops forwarding of server and plugin warnings/errors. */
    fun observe(value: ((Any, LogLevel, String, Throwable?) -> Unit)?) = observer.observe(value)
}
