package org.pnfolder.pnlibrary.api.world

import net.kyori.adventure.key.Key
import java.time.Duration

/**
 * Immutable description of a temporary status effect on a player.
 *
 * The [key] is an Adventure key so the API never exposes Bukkit, BungeeCord, Velocity or version-specific Minecraft types;
 * platform adapters resolve it to the native effect.
 *
 * ```kotlin
 * val speed = StatusEffect(Key.key("minecraft:speed"), Duration.ofSeconds(10), amplifier = 1)
 * player.applyEffect(speed)
 * ```
 *
 * @property key effect key, such as `minecraft:speed`
 * @property duration how long the effect stays; must be positive
 * @property amplifier zero-based level, where `0` is level I
 * @property ambient whether the platform marks the effect as ambient
 * @property particles whether the regular effect particles are visible
 * @property icon whether the client shows the effect icon
 */
data class StatusEffect(
    val key: Key,
    val duration: Duration = Duration.ofSeconds(30),
    val amplifier: Int = 0,
    val ambient: Boolean = true,
    val particles: Boolean = true,
    val icon: Boolean = true,
) {
    init {
        require(!duration.isNegative && !duration.isZero) { "Effect duration must be positive" }
        require(amplifier >= 0) { "Effect amplifier must not be negative" }
    }
}
