package org.pnfolder.pnlibrary.bukkit.commands

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.bukkit.BukkitTitle
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogCommands.SettingSaved

/**
 * Short feedback after a setting was saved: the setting's name as the title and what changed, such as
 * `stable → beta`, as the subtitle, fading after about two seconds, and a small chime that rises for "on" and
 * falls for "off".
 *
 * The 1.8 title API has no timings and different sound names, so both are chosen at runtime; nothing here may
 * break the command that was just completed.
 */
internal class BukkitSettingFeedback(private val plugin: Plugin) {
    /** Shows the title and plays the chime for [player]. */
    fun show(player: Player, saved: SettingSaved) {
        runCatchingNonFatal { title(player, saved) }
        runCatchingNonFatal { chime(player, notes(saved)) }
    }

    private fun title(player: Player, saved: SettingSaved) {
        val (title, subtitle) = BukkitTitle.settingSaved(saved)
        val timed = runCatchingNonFatal {
            Player::class.java.getMethod(
                "sendTitle", String::class.java, String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            ).invoke(player, title, subtitle, FADE_IN_TICKS, STAY_TICKS, FADE_OUT_TICKS)
        }.isSuccess
        if (!timed) {
            @Suppress("DEPRECATION")
            player.sendTitle(title, subtitle)
        }
    }

    /** Rising notes for turning something on or picking a value, falling notes for turning it off. */
    private fun notes(saved: SettingSaved): List<Float> = when {
        saved.to == "выключена" -> listOf(1.5f, 1.0f)
        saved.setting == "Пауза" && saved.to != "нет" -> listOf(0.8f, 1.0f)
        else -> listOf(1.0f, 1.26f, 1.5f)
    }

    private fun chime(player: Player, pitches: List<Float>) {
        val sound = if (modernSounds) "entity.experience_orb.pickup" else "random.orb"
        pitches.forEachIndexed { index, pitch ->
            val play = Runnable { if (player.isOnline) player.playSound(player.location, sound, VOLUME, pitch) }
            if (index == 0) play.run() else plugin.server.scheduler.runTaskLater(plugin, play, index * NOTE_GAP_TICKS)
        }
    }

    /** Sound names changed in 1.9; the presence of the enum constant tells which generation this server is. */
    private val modernSounds: Boolean = runCatchingNonFatal {
        Class.forName("org.bukkit.Sound").getField("ENTITY_EXPERIENCE_ORB_PICKUP")
    }.isSuccess

    private companion object {
        const val FADE_IN_TICKS = 5
        const val STAY_TICKS = 30
        const val FADE_OUT_TICKS = 10
        const val NOTE_GAP_TICKS = 3L
        const val VOLUME = 0.6f
    }
}
