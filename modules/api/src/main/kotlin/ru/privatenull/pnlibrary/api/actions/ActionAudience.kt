package ru.privatenull.pnlibrary.api.actions

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
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
 * Built-in audience selectors used by configurable actions.
 */
enum class ActionTarget {
    /** The player that triggered the action execution. */
    PLAYER,

    /** Every player exposed by the current [ActionContext]. */
    ALL_PLAYERS,
}
