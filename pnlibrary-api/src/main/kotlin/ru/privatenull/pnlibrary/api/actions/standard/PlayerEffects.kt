package ru.privatenull.pnlibrary.api.actions

import java.time.Duration

/**
 * Immutable description of a temporary player status effect.
 *
 * The [key] is intentionally stored as a string so the API does not expose
 * Bukkit, BungeeCord, Velocity, or version-specific Minecraft types. Platform
 * adapters are responsible for resolving the key to the native effect object.
 *
 * @property key namespaced or platform-native effect key, for example
 * `minecraft:speed` or `speed`
 * @property duration amount of time the effect should stay active; must be
 * positive
 * @property amplifier zero-based effect amplifier, where `0` means level I
 * @property ambient whether the effect should be marked as ambient by the
 * platform
 * @property particles whether regular effect particles should be visible
 * @property icon whether the client-side effect icon should be visible
 */
data class PlayerEffect(
    val key: String,
    val duration: Duration = Duration.ofSeconds(30),
    val amplifier: Int = 0,
    val ambient: Boolean = true,
    val particles: Boolean = true,
    val icon: Boolean = true,
) {
    init {
        require(key.isNotBlank()) { "Effect key must not be blank" }
        require(!duration.isNegative && !duration.isZero) { "Effect duration must be positive" }
        require(amplifier >= 0) { "Effect amplifier must not be negative" }
    }
}

/**
 * Immutable description of a particle burst displayed around a player.
 *
 * Particle keys remain textual for the same reason as [PlayerEffect.key]: the
 * API module is platform-neutral, while runtime adapters translate the key into
 * native server objects.
 *
 * @property key namespaced or platform-native particle key, for example
 * `minecraft:flame` or `flame`
 * @property count number of particles to spawn; `0` is allowed and represents
 * a no-op request
 * @property offsetX maximum horizontal X-axis spread
 * @property offsetY maximum vertical Y-axis spread
 * @property offsetZ maximum horizontal Z-axis spread
 * @property speed particle speed or additional particle-specific data, depending
 * on the native particle type
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
