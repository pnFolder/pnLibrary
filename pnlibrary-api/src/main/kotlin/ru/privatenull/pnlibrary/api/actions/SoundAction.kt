package ru.privatenull.pnlibrary.api.actions

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import ru.privatenull.pnlibrary.api.config.ConfigType

/** Plays a namespaced Minecraft sound for the selected audience. */
@ConfigType("sound")
data class SoundAction(
    val key: String = "minecraft:entity.experience_orb.pickup",
    val source: Sound.Source = Sound.Source.MASTER,
    val volume: Float = 1f,
    val pitch: Float = 1f,
    val target: Action.Target = Action.Target.PLAYER,
) : Action {
    override fun execute(context: Action.Context) {
        val sound = Sound.sound(Key.key(key), source, volume, pitch)
        if (!context.target(target).playSound(sound)) {
            context.logger.warning("Sound action is unsupported by the current platform: $key")
        }
    }
}
