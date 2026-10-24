package org.pnfolder.pnlibrary.api.audiences

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
    fun applyEffect(effect: PlayerEffect): Delivery

    /** Spawns a particle effect around the player. */
    fun spawnParticle(particle: PlayerParticle): Delivery

    /**
     * Runs [command] as the player, or as the console when [asConsole] is `true`.
     *
     * @param command command line without the leading slash
     * @return [Delivery.Unsupported] by default, for platforms that cannot run commands
     */
    fun runCommand(command: String, asConsole: Boolean): Delivery = Delivery.Unsupported

    /**
     * Returns how many items matching [query] the player carries, or `null` when the platform has
     * no player inventory.
     */
    fun countItems(query: ItemQuery): Int? = null

    /**
     * Returns the player's experience, as levels when [asLevels] is `true` and as points otherwise,
     * or `null` when the platform has no experience.
     */
    fun experience(asLevels: Boolean): Double? = null

    /**
     * Returns the distance in blocks to the point [x], [y], [z] in [world], or `null` when the
     * player is in another world or the platform has no positions.
     */
    fun distanceTo(world: String, x: Double, y: Double, z: Double): Double? = null
}
