package ru.privatenull.pnlibrary.bukkit.logging

import org.bukkit.ChatColor
import org.bukkit.plugin.java.JavaPlugin
import ru.privatenull.pnlibrary.bukkit.banner.PluginBanner
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Unified console logger for Bukkit plugins.
 */
class PluginLogger(val plugin: JavaPlugin, val developer: String) {

    val delegate: Logger = plugin.logger

    fun info(message: String) {
        line(ChatColor.AQUA, "i", message)
    }

    fun success(message: String) {
        line(ChatColor.GREEN, "✓", message)
    }

    fun warn(message: String) {
        line(ChatColor.GOLD, "⚠", message)
    }

    fun error(message: String) {
        line(ChatColor.RED, "✕", message)
    }

    fun error(message: String, error: Throwable) {
        line(ChatColor.RED, "✕", "$message: ${error.message ?: error.javaClass.simpleName}")
        delegate.log(Level.SEVERE, message, error)
    }

    fun debug(message: String) {
        delegate.fine(message)
    }

    fun mBox(title: String): MBox = MBox(this, title)
    fun box(title: String): MBox = mBox(title)

    private fun line(color: ChatColor, symbol: String, message: String) {
        val console = plugin.server.consoleSender
        console.sendMessage("$color/\\_/\\")
        console.sendMessage("$color( ${face(symbol)} )   $color$symbol $message")
        console.sendMessage("$color> ^ <")
    }

    private fun face(symbol: String): String = when (symbol) {
        "✓" -> "^.^"
        "⚠" -> "o.o"
        "✕" -> "x.x"
        else -> "i.i"
    }
}
