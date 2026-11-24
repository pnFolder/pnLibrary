package org.pnfolder.pnlibrary.api.audience

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.pointer.Pointers
import java.util.UUID

/**
 * Platform-neutral online player: an Adventure [Audience] with a UUID. Platform modules implement it; feature code never
 * touches Bukkit, BungeeCord or Velocity player classes. Name, nickname, language and permissions are read from
 * [pointers] with the extensions in this package; what goes beyond chat is asked for with [feature].
 *
 * ```kotlin
 * val player = library.audiences.player(uuid) ?: return
 * player.sendMessage(Component.text("Hello, ${player.name}"))
 * player.feature<PlayerEffects>()?.applyEffect(PlayerEffect("minecraft:speed", Duration.ofSeconds(10)))
 * val diamonds = player.feature<PlayerInventory>()?.countItems(ItemQuery("diamond")) ?: 0
 * ```
 */
interface LibraryPlayer : Audience {
    /** Stable player UUID supplied by the server platform. */
    val uniqueId: UUID

    /**
     * Who the player is: name, nickname, language, UUID and permission check, built with [playerPointers]. Read them with the
     * `name` and `hasPermission` extensions of [Audience], the same way as for the console and any other sender.
     */
    override fun pointers(): Pointers

    /**
     * Returns this player as [type] (one of [PlayerEffects], [PlayerCommands], [PlayerInventory], [PlayerExperience],
     * [PlayerPosition]) when the platform supports it, or `null`. Wrappers that delegate to another player override it.
     */
    fun <T : Any> feature(type: Class<T>): T? = if (type.isInstance(this)) type.cast(this) else null
}

/** Returns this player as [T] when the platform supports it, or `null`: `player.feature<PlayerInventory>()?.countItems(query)`. */
inline fun <reified T : Any> LibraryPlayer.feature(): T? = feature(T::class.java)
