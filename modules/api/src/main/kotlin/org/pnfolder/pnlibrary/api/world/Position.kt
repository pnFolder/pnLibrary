package org.pnfolder.pnlibrary.api.world

import kotlin.math.sqrt

/**
 * A point in a world of a game server.
 *
 * ```kotlin
 * val spawn = Position("world", 0.0, 64.0, 0.0)
 * val blocks = player.position()?.distanceTo(spawn)
 * ```
 *
 * @property world name of the world
 * @property x east-west coordinate
 * @property y height
 * @property z north-south coordinate
 */
data class Position(val world: String, val x: Double, val y: Double, val z: Double) {
    /** The distance in blocks to [other], or `null` when the two points are in different worlds. */
    fun distanceTo(other: Position): Double? {
        if (!world.equals(other.world, ignoreCase = true)) return null
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
