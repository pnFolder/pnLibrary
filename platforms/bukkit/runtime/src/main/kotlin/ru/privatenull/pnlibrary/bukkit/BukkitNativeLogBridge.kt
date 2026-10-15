package ru.privatenull.pnlibrary.bukkit

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.api.logging.LogLevel
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord

/** Bridges Bukkit warnings/errors into the platform log observation contract. */
internal class BukkitNativeLogBridge(private val plugin: Plugin) {
    private val ownLogCall = ThreadLocal.withInitial { false }

    @Volatile
    private var observer: ((Any, LogLevel, String, Throwable?) -> Unit)? = null

    val logHandler: (Any, LogLevel, String, Throwable?) -> Unit =
        { owner, level, message, error ->
            val logger = (owner as? Plugin)?.logger ?: plugin.logger
            val nativeLevel = when (level) {
                LogLevel.WARNING -> Level.WARNING
                LogLevel.ERROR -> Level.SEVERE
                else -> Level.INFO
            }

            ownLogCall.set(true)
            try {
                logger.log(nativeLevel, message, error)
            } finally {
                ownLogCall.set(false)
            }
        }

    @Synchronized
    fun observe(value: ((Any, LogLevel, String, Throwable?) -> Unit)?) {
        if (observer == null && value != null) Bukkit.getLogger().addHandler(handler)
        if (observer != null && value == null) Bukkit.getLogger().removeHandler(handler)
        observer = value
    }

    private val handler = object : Handler() {
        override fun publish(record: LogRecord?) {
            if (record == null || ownLogCall.get()) return
            if (record.level.intValue() < Level.WARNING.intValue()) return

            val level = if (record.level.intValue() >= Level.SEVERE.intValue()) {
                LogLevel.ERROR
            } else {
                LogLevel.WARNING
            }
            val source = Bukkit.getPluginManager().plugins.firstOrNull { installedPlugin ->
                record.loggerName?.contains(installedPlugin.name, ignoreCase = true) == true
            } ?: plugin

            observer?.invoke(
                source,
                level,
                record.message ?: "Native platform error",
                record.thrown,
            )
        }

        override fun flush() = Unit
        override fun close() = Unit
    }
}
