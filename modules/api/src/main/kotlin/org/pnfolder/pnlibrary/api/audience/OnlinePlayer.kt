package org.pnfolder.pnlibrary.api.audience

import net.kyori.adventure.key.Key
import org.pnfolder.pnlibrary.api.audience.delivery.Delivery
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.pointer.Pointers
import org.pnfolder.pnlibrary.api.world.ItemMatcher
import org.pnfolder.pnlibrary.api.world.Particle
import org.pnfolder.pnlibrary.api.world.Position
import org.pnfolder.pnlibrary.api.world.StatusEffect
import java.util.UUID

/**
 * A player who is online: an Adventure [Audience] with a UUID. Platform modules implement it, so plugin code never
 * touches Bukkit, BungeeCord or Velocity player classes. Name, nickname, language and permissions are read from
 * [pointers] with the extensions in this package.
 *
 * Like every Adventure method, the ones below do nothing where the platform cannot do them: a player seen from a proxy
 * gets no effects and has no position. Delivery methods say so with [Delivery.unsupported], reads give `null`.
 *
 * ```kotlin
 * val player = library.audiences.player(uuid) ?: return
 * player.sendMessage(Component.text("Hello, ${player.name}"))
 * player.addEffect(StatusEffect(Key.key("minecraft:speed"), Duration.ofSeconds(10)))
 * val diamonds = player.countItems(ItemMatcher(Key.key("diamond"))) ?: 0
 * ```
 */
interface OnlinePlayer : Audience {
    /** Stable player UUID supplied by the server platform. */
    val uniqueId: UUID

    /**
     * Who the player is: name, nickname, language, UUID and permission check, built with [playerPointers]. Read them with the
     * `name` and `hasPermission` extensions of [Audience], the same way as for the console.
     */
    override fun pointers(): Pointers

    /** Gives the player a temporary status effect. */
    fun addEffect(effect: StatusEffect): Delivery = Delivery.unsupported()

    /** Spawns a burst of particles around the player. */
    fun spawnParticle(particle: Particle): Delivery = Delivery.unsupported()

    /** Runs [command], a command line without the leading slash, as the player. */
    fun runCommand(command: String): Delivery = Delivery.unsupported()

    /** Runs [command], a command line without the leading slash, as the console. */
    fun runConsoleCommand(command: String): Delivery = Delivery.unsupported()

    /** How many items accepted by [matcher] the player carries, or `null` when the platform cannot tell. */
    fun countItems(matcher: ItemMatcher): Int? = null

    /** The experience level, or `null` when the platform cannot tell. */
    fun experienceLevel(): Double? = null

    /** The total experience points, or `null` when the platform cannot tell. */
    fun experiencePoints(): Double? = null

    /** Where the player is now, or `null` when the platform cannot tell. */
    fun position(): Position? = null
}
