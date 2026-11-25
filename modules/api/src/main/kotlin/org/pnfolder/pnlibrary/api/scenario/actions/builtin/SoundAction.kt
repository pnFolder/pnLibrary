package org.pnfolder.pnlibrary.api.scenario.actions.builtin

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import org.pnfolder.pnlibrary.api.scenario.actions.Action
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult
import org.pnfolder.pnlibrary.api.scenario.actions.ActionTarget

/**
 * Plays a sound.
 *
 * @property key namespaced sound key
 * @property source volume category on the client
 * @property volume relative volume
 * @property pitch pitch multiplier
 * @property target who hears it
 */
data class SoundAction(
    val key: String = "minecraft:entity.experience_orb.pickup",
    val source: Sound.Source = Sound.Source.MASTER,
    val volume: Float = 1f,
    val pitch: Float = 1f,
    val target: ActionTarget = ActionTarget.PLAYER,
) : Action {
    override fun run(context: ActionContext): ActionResult {
        context.target(target).playSound(Sound.sound(Key.key(key), source, volume, pitch))
        return ActionResult.Done
    }
}
