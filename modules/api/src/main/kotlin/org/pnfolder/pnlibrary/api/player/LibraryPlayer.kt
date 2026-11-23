package org.pnfolder.pnlibrary.api.player

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.pointer.Pointers
import org.pnfolder.pnlibrary.api.player.Delivery
import org.pnfolder.pnlibrary.api.sender.senderPointers
import java.util.UUID

/**
 * Platform-neutral online player, an Adventure [Audience] with what Adventure lacks. Platform modules implement it; feature code never touches Bukkit, BungeeCord or
 * Velocity player classes.
 *
 * ```kotlin
 * val player = library.players.player(uuid) ?: return
 * player.applyEffect(PlayerEffect("minecraft:speed", Duration.ofSeconds(10)))
 * val diamonds = player.countItems(ItemQuery("diamond")) ?: 0
 * ```
 *
 * Optional operations return [Delivery.Unsupported] or `null` on platforms that lack them; see [capabilities].
 */
interface LibraryPlayer : Audience {
    /** Stable player UUID supplied by the server platform. */
    val uniqueId: UUID

    /** Operations this adapter supports; chat and action bar by default. */
    val capabilities: Set<PlayerCapability>
        get() = setOf(PlayerCapability.MESSAGE, PlayerCapability.ACTION_BAR)

    /** Current player name. */
    val name: String

    /** Whether the platform grants this player [permission]. */
    fun hasPermission(permission: String): Boolean

    override fun pointers(): Pointers = senderPointers({ name }, uniqueId, ::hasPermission)

    /** Applies a temporary status effect to the player. */
    fun applyEffect(effect: PlayerEffect): Delivery

    /** Spawns a particle burst around the player. */
    fun spawnParticle(particle: PlayerParticle): Delivery

    /**
     * Runs [command] as the player, or as the console when [asConsole] is `true`.
     *
     * @param command command line without the leading slash
     * @return [Delivery.Unsupported] by default, for platforms that cannot run commands
     */
    fun runCommand(command: String, asConsole: Boolean): Delivery = Delivery.Unsupported

    /** Returns how many items matching [query] the player carries, or `null` when the platform has no inventory. */
    fun countItems(query: ItemQuery): Int? = null

    /**
     * Returns the player's experience as levels when [asLevels] is `true` and as points otherwise,
     * or `null` when the platform has no experience.
     */
    fun experience(asLevels: Boolean): Double? = null

    /**
     * Returns the distance in blocks to the point [x], [y], [z] in [world], or `null` when the player is in
     * another world or the platform has no positions.
     */
    fun distanceTo(world: String, x: Double, y: Double, z: Double): Double? = null
}
