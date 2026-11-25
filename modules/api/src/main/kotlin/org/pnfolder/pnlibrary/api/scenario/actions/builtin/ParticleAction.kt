package org.pnfolder.pnlibrary.api.scenario.actions.builtin

import org.pnfolder.pnlibrary.api.world.minecraftKey
import org.pnfolder.pnlibrary.api.world.Particle
import org.pnfolder.pnlibrary.api.audience.delivery.Delivery
import org.pnfolder.pnlibrary.api.scenario.actions.Action
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult

/**
 * Spawns particles around the player.
 *
 * @property key particle key
 * @property count how many
 * @property offsetX spread along X
 * @property offsetY spread along Y
 * @property offsetZ spread along Z
 * @property speed speed or particle-specific data
 */
data class ParticleAction(
    val key: String = "flame",
    val count: Int = 1,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val offsetZ: Double = 0.0,
    val speed: Double = 0.0,
) : Action {
    override fun run(context: ActionContext): ActionResult = context.warnUnless(
        context.player.spawnParticle(Particle(minecraftKey(key), count, offsetX, offsetY, offsetZ, speed)),
        "Particle $key",
    )
}
