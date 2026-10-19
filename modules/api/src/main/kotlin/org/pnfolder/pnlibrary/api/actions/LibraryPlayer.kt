package org.pnfolder.pnlibrary.api.actions

import org.pnfolder.pnlibrary.api.audiences.AudienceSender
import java.util.UUID

/** Operations that may be implemented by a platform player adapter. */
enum class PlayerCapability {
    MESSAGE,
    ACTION_BAR,
    SOUND,
    EFFECT,
    PARTICLE,
}

/**
 * Platform-neutral representation of an online player.
 *
 * Platform modules implement this contract; feature code must not depend on
 * Bukkit, BungeeCord, or Velocity player classes.
 */
interface LibraryPlayer : AudienceSender {
    /** Stable player UUID supplied by the server platform. */
    val uniqueId: UUID

    /** Operations supported by this adapter. */
    val capabilities: Set<PlayerCapability>
        get() = setOf(PlayerCapability.MESSAGE, PlayerCapability.ACTION_BAR)

    /** Stable textual sender identifier derived from [uniqueId]. */
    override val id: String get() = uniqueId.toString()

    /** Always `false` because this audience represents a player. */
    override val isConsole: Boolean get() = false

    /** Always `true` because this audience represents a player. */
    override val isPlayer: Boolean get() = true

    /** Current visible player name. */
    override val name: String

    /** Checks whether the player has a platform permission. */
    override fun hasPermission(permission: String): Boolean

    /** Applies a temporary status effect to the player. */
    fun applyEffect(effect: PlayerEffect): Boolean

    /** Spawns a particle effect around the player. */
    fun spawnParticle(particle: PlayerParticle): Boolean
}
