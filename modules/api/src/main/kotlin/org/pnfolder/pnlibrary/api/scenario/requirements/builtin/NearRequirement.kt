package org.pnfolder.pnlibrary.api.scenario.requirements.builtin

import org.pnfolder.pnlibrary.api.world.Position
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict

/**
 * Met when the player is within [distance] blocks of [location].
 *
 * @property location point written as `world,x,y,z`
 * @property distance largest allowed distance in blocks, not negative
 * @throws IllegalArgumentException when [location] is not `world,x,y,z` or [distance] is negative
 */
data class NearRequirement(val location: String = "world,0,0,0", val distance: Double = 5.0) : Requirement {
    private val world: String
    private val x: Double
    private val y: Double
    private val z: Double

    init {
        require(distance >= 0) { "Distance must not be negative" }
        val parts = location.split(',').map(String::trim)
        val numbers = parts.drop(1).map(String::toDoubleOrNull)
        require(parts.size == 4 && parts[0].isNotEmpty() && numbers.none { it == null }) {
            "Location must be written as world,x,y,z but was '$location'"
        }
        world = parts[0]
        x = numbers[0] ?: 0.0
        y = numbers[1] ?: 0.0
        z = numbers[2] ?: 0.0
    }

    override fun test(context: RequirementContext): Verdict {
        val current = context.player.position()?.distanceTo(Position(world, x, y, z)) ?: return Verdict.Unmet("distance is not available here")
        return Verdict.of(current <= distance, "too far from $location")
    }
}
