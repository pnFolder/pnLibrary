package org.pnfolder.pnlibrary.bukkit.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import java.io.File

/**
 * Restarts the server so staged updates apply: at once, after a countdown the players see, or when nobody is online.
 *
 * A restart needs a script that starts the server again (`restart-script` in `spigot.yml`). When that script is
 * missing the server cannot come back by itself, so it is stopped cleanly and the administrator is told to start it.
 * Only one restart can be waiting; a new request replaces it and [cancel] withdraws it.
 */
internal class ServerRestarter(private val plugin: Plugin) {
    /** When the restart happens. */
    enum class Mode { NOW, IN_FIVE_MINUTES, WHEN_EMPTY }

    @Volatile
    private var waiting: BukkitTask? = null

    /** What starting a restart did, for the answer to the administrator. */
    enum class Started { COUNTDOWN, WAITING_FOR_EMPTY }

    /** Starts a restart in [mode] and returns how it will proceed. */
    fun start(mode: Mode): Started {
        cancel()
        return when (mode) {
            Mode.NOW -> countdown(if (Bukkit.getOnlinePlayers().isEmpty()) QUICK_SECONDS else NOTICE_SECONDS)
            Mode.IN_FIVE_MINUTES -> countdown(LATER_SECONDS)
            Mode.WHEN_EMPTY -> waitForEmpty()
        }
    }

    /** Withdraws the waiting restart; false when none was waiting. */
    fun cancel(): Boolean {
        val task = waiting ?: return false
        waiting = null
        task.cancel()
        return true
    }

    private fun countdown(seconds: Int): Started {
        var left = seconds
        waiting = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (left in WARNINGS && Bukkit.getOnlinePlayers().isNotEmpty()) announce("Сервер перезапустится через ${wait(left)} для применения обновлений.")
            if (left <= 0) {
                cancel()
                restart()
            }
            left--
        }, 0L, TICKS_PER_SECOND)
        return Started.COUNTDOWN
    }

    private fun waitForEmpty(): Started {
        waiting = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                cancel()
                restart()
            }
        }, 0L, EMPTY_CHECK_TICKS)
        return Started.WAITING_FOR_EMPTY
    }

    @Suppress("DEPRECATION")
    private fun restart() {
        if (script() != null) {
            announce("Сервер перезапускается для применения обновлений.")
            Bukkit.spigot().restart()
        } else {
            announce("Сервер останавливается для применения обновлений. Запустите его снова.")
            plugin.logger.warning(
                "restart-script in spigot.yml is not set or its file is missing, so the server cannot start itself again: " +
                    "stopping it. Start it manually, or point restart-script at your start script (for example ./start.bat).",
            )
            Bukkit.shutdown()
        }
    }

    /** The restart script `spigot.yml` names, or null when none is set or the file does not exist. */
    fun script(): File? {
        val name = YamlConfiguration.loadConfiguration(File(SPIGOT_CONFIG)).getString("settings.restart-script")?.trim()
        if (name.isNullOrEmpty()) return null
        return File(name.split(' ').first()).takeIf { it.isFile }
    }

    private fun announce(text: String) {
        val line = Component.text("[pnFolder] ", ChatPalette.ACCENT).append(Component.text(text, ChatPalette.TEXT))
        Bukkit.broadcastMessage(LegacyComponentSerializer.legacySection().serialize(line))
    }

    private fun wait(seconds: Int): String = if (seconds >= SECONDS_PER_MINUTE) "${seconds / SECONDS_PER_MINUTE} мин" else "$seconds с"

    private companion object {
        const val SPIGOT_CONFIG = "spigot.yml"
        const val TICKS_PER_SECOND = 20L
        const val EMPTY_CHECK_TICKS = 200L
        const val SECONDS_PER_MINUTE = 60
        const val QUICK_SECONDS = 2
        const val NOTICE_SECONDS = 10
        const val LATER_SECONDS = 300
        val WARNINGS = setOf(300, 60, 30, 10, 5, 3, 2, 1)
    }
}
