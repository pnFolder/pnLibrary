package org.pnfolder.pnlibrary.bukkit.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.bukkit.tasks.ServerScheduler
import java.io.File

/**
 * Restarts the server so staged updates apply: at once, after a countdown the players see, or when nobody is online.
 *
 * With `restart-script` in `spigot.yml` the server restarts itself. Without it the server is stopped cleanly and
 * comes back only if something outside starts it again (a hosting panel, a `start.sh` loop, Pterodactyl); the library
 * cannot tell which, so it says so without calling it a problem.
 * Only one restart can be waiting; a new request replaces it and [cancel] withdraws it.
 */
internal class ServerRestarter(private val plugin: Plugin, private val scheduler: ServerScheduler, private val log: () -> PnLogger) {
    /** When the restart happens. */
    enum class Mode { NOW, IN_FIVE_MINUTES, WHEN_EMPTY }

    @Volatile
    private var waiting: ServerScheduler.Scheduled? = null

    enum class Started { COUNTDOWN, WAITING_FOR_EMPTY }

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
        waiting = scheduler.repeat(0L, TICKS_PER_SECOND) {
            if (left in WARNINGS && Bukkit.getOnlinePlayers().isNotEmpty()) announce("Сервер перезапустится через ${wait(left)} для применения обновлений.")
            if (left <= 0) {
                cancel()
                restart()
            }
            left--
        }
        return Started.COUNTDOWN
    }

    private fun waitForEmpty(): Started {
        waiting = scheduler.repeat(0L, EMPTY_CHECK_TICKS) {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                cancel()
                restart()
            }
        }
        return Started.WAITING_FOR_EMPTY
    }

    @Suppress("DEPRECATION")
    private fun restart() {
        if (script() != null) {
            announce("Сервер перезапускается для применения обновлений.")
            Bukkit.spigot().restart()
        } else {
            announce("Сервер останавливается для применения обновлений. Если у него есть автозапуск, он включится сам.")
            log().info(
                "restart-script in spigot.yml is not set, so the server only stops: a hosting panel or a start script that " +
                    "loops starts it again. Without one, start it manually, or point restart-script at your start script.",
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
