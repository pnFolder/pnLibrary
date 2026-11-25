package org.pnfolder.pnlibrary.api.world

import net.kyori.adventure.key.Key

/**
 * Immutable description of a particle burst around a player. The [key] is a key for the same reason as
 * [StatusEffect.key].
 *
 * ```kotlin
 * player.spawnParticle(Particle(Key.key("minecraft:flame"), count = 20, offsetX = 0.5, offsetY = 1.0, offsetZ = 0.5))
 * ```
 *
 * @property key particle key, such as `minecraft:flame`
 * @property count how many particles to spawn; `0` is a no-op
 * @property offsetX maximum spread along the X axis
 * @property offsetY maximum spread along the Y axis
 * @property offsetZ maximum spread along the Z axis
 * @property speed particle speed, or extra data for particle types that use it
 */
data class Particle(
    val key: Key,
    val count: Int = 1,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val offsetZ: Double = 0.0,
    val speed: Double = 0.0,
) {
    init {
        require(count >= 0) { "Particle count must not be negative" }
    }
}
