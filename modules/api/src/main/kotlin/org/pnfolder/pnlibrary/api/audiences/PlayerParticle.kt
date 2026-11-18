package org.pnfolder.pnlibrary.api.audiences

/**
 * Immutable description of a particle burst around a player. The [key] is text for the same reason as
 * [PlayerEffect.key].
 *
 * ```kotlin
 * player.spawnParticle(PlayerParticle("minecraft:flame", count = 20, offsetX = 0.5, offsetY = 1.0, offsetZ = 0.5))
 * ```
 *
 * @property key namespaced or platform-native particle key, such as `minecraft:flame` or `flame`
 * @property count how many particles to spawn; `0` is a no-op
 * @property offsetX maximum spread along the X axis
 * @property offsetY maximum spread along the Y axis
 * @property offsetZ maximum spread along the Z axis
 * @property speed particle speed, or extra data for particle types that use it
 */
data class PlayerParticle(
    val key: String,
    val count: Int = 1,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val offsetZ: Double = 0.0,
    val speed: Double = 0.0,
) {
    init {
        require(key.isNotBlank()) { "Particle key must not be blank" }
        require(count >= 0) { "Particle count must not be negative" }
    }
}
