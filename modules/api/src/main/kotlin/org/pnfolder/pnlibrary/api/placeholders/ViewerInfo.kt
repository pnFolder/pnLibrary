package org.pnfolder.pnlibrary.api.placeholders

import java.util.UUID

/** Thrown inside a placeholder method when it has no value to give; the placeholder is then left unresolved. */
class NoValueException(message: String = "No value") : RuntimeException(message, null, false, false)

/**
 * Who asks for the text. Add a parameter of this type to a placeholder method and the library fills it in.
 *
 * @property playerId the player the text is for, or `null` for the console and background work
 * @property language the language of the player
 * @property viaPapi whether the request came through PlaceholderAPI
 */
class ViewerInfo(
    val playerId: UUID?,
    val language: String = DEFAULT_LANGUAGE,
    val viaPapi: Boolean = false,
) {
    /** Whether there is a player. */
    val hasPlayer: Boolean get() = playerId != null

    /** The player, or ends the placeholder quietly (it shows nothing) when there is none. */
    fun requirePlayerId(): UUID = playerId ?: throw NoValueException("This placeholder needs a player")

    override fun toString(): String = "ViewerInfo(player=$playerId, language=$language, papi=$viaPapi)"

    companion object {
        const val DEFAULT_LANGUAGE: String = "en"

        /** A viewer for [playerId]. */
        @JvmStatic
        fun of(playerId: UUID?): ViewerInfo = ViewerInfo(playerId)

        /** A viewer without a player. */
        @JvmStatic
        fun none(): ViewerInfo = ViewerInfo(null)
    }
}
