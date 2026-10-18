package ru.privatenull.pnlibrary.api.actions

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound

/**
 * Builds an Adventure [Sound] and plays it for the selected audience.
 *
 * Unsupported platform operations do not abort the action graph; they produce a
 * warning through [ActionContext.logger]. Invalid Adventure keys fail during execution.
 *
 * @property key namespaced Adventure sound key
 * @property source mixer category used by the client
 * @property volume relative playback volume
 * @property pitch playback pitch multiplier
 * @property target audience that hears the sound
 */
data class SoundAction(
    val key: String = "minecraft:entity.experience_orb.pickup",
    val source: Sound.Source = Sound.Source.MASTER,
    val volume: Float = 1f,
    val pitch: Float = 1f,
    val target: ActionTarget = ActionTarget.PLAYER,
) : Action {
    override fun execute(context: ActionContext) {
        val sound = Sound.sound(Key.key(key), source, volume, pitch)
        if (!context.target(target).playSound(sound)) {
            context.logger.warning("Sound action is unsupported by the current platform: $key")
        }
    }
}
