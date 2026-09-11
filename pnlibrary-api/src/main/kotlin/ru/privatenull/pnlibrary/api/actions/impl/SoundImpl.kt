package ru.privatenull.pnlibrary.api.actions.impl

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import ru.privatenull.pnlibrary.api.actions.Action

class SoundImpl(
    var key: String = "minecraft:entity.experience_orb.pickup",
    var source: Sound.Source = Sound.Source.MASTER,
    var volume: Float = 1f,
    var pitch: Float = 1f,
    var target: Action.Target = Action.Target.PLAYER,
) : Action {
    override fun execute(context: Action.Context) {
        val sound = Sound.sound(Key.key(key), source, volume, pitch)
        if (!context.target(target).playSound(sound)) {
            context.logger.warning("Sound action is unsupported by the current platform: $key")
        }
    }
}
