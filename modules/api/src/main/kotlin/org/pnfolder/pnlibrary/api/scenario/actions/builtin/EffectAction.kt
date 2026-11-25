package org.pnfolder.pnlibrary.api.scenario.actions.builtin

import org.pnfolder.pnlibrary.api.world.minecraftKey
import org.pnfolder.pnlibrary.api.world.StatusEffect
import org.pnfolder.pnlibrary.api.audience.delivery.Delivery
import org.pnfolder.pnlibrary.api.scenario.actions.Action
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult
import java.time.Duration

/**
 * Gives the player a temporary potion effect.
 *
 * @property key effect key
 * @property duration how long it lasts
 * @property amplifier zero-based level, `0` is level I
 * @property ambient whether it counts as ambient
 * @property particles whether its particles show
 * @property icon whether its icon shows
 */
data class EffectAction(
    val key: String = "speed",
    val duration: Duration = Duration.ofSeconds(30),
    val amplifier: Int = 0,
    val ambient: Boolean = true,
    val particles: Boolean = true,
    val icon: Boolean = true,
) : Action {
    override fun run(context: ActionContext): ActionResult = context.warnUnless(
        context.player.addEffect(StatusEffect(minecraftKey(key), duration, amplifier, ambient, particles, icon)),
        "Effect $key",
    )
}
