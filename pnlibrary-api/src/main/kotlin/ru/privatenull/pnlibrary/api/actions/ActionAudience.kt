package ru.privatenull.pnlibrary.api.actions

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import java.util.UUID
import java.util.function.Supplier

/**
 * A target that can receive player-facing action output.
 *
 * Implementations are provided by platform modules and hide the differences
 * between Bukkit, BungeeCord, Velocity, and any future runtime adapter.
 */
interface LibraryAudience {
    /**
     * Sends a chat message to this audience.
     *
     * @param text message component that is already deserialized by the caller
     */
    fun sendMessage(text: Component)

    /**
     * Displays an action-bar message to this audience.
     *
     * @param text message component that is already deserialized by the caller
     */
    fun actionBar(text: Component)

    /**
     * Plays a sound for every receiver in this audience.
     *
     * @return `true` when the current platform accepted the sound request for
     * every receiver, or `false` when at least one receiver/platform rejected it
     */
    fun playSound(sound: Sound): Boolean

    /** Factories for audiences whose membership is computed on demand. */
    companion object {
        /**
         * Creates an audience backed by a player supplier.
         *
         * The supplier is evaluated for every operation, which makes this useful
         * for dynamic groups such as "all online players" where membership can
         * change between action executions.
         *
         * @param players supplier returning the players that should receive each
         * operation
         */
        @JvmStatic
        fun dynamic(players: Supplier<out Iterable<LibraryPlayer>>): LibraryAudience =
            object : LibraryAudience {
                override fun sendMessage(text: Component) {
                    players.get().forEach { player -> player.sendMessage(text) }
                }

                override fun actionBar(text: Component) {
                    players.get().forEach { player -> player.actionBar(text) }
                }

                override fun playSound(sound: Sound): Boolean =
                    players.get().fold(true) { accepted, player ->
                        player.playSound(sound) && accepted
                    }
            }
    }
}

/**
 * Platform-neutral representation of an online player.
 *
 * Action implementations depend on this interface instead of server-specific
 * player classes, keeping the public API free from Bukkit/Bungee/Velocity
 * compile-time dependencies.
 */
interface LibraryPlayer : LibraryAudience {
    /** Stable player UUID supplied by the server platform. */
    val uniqueId: UUID

    /** Current visible player name. */
    val name: String

    /**
     * Checks whether the player has a platform permission.
     *
     * @param permission permission node, for example `example.feature.use`
     */
    fun hasPermission(permission: String): Boolean

    /**
     * Applies a temporary status effect to the player.
     *
     * @return `true` when the platform adapter applied the effect, or `false`
     * when the effect key is unknown/unsupported by the current platform
     */
    fun applyEffect(effect: PlayerEffect): Boolean

    /**
     * Spawns a particle effect around the player.
     *
     * @return `true` when the platform adapter spawned the particle, or `false`
     * when the particle key is unknown/unsupported by the current platform
     */
    fun spawnParticle(particle: PlayerParticle): Boolean
}

/**
 * Built-in audience selectors used by configurable actions.
 */
enum class ActionTarget {
    /** The player that triggered the action execution. */
    PLAYER,

    /** Every player exposed by the current [ActionContext]. */
    ALL_PLAYERS,
}
