package org.pnfolder.pnlibrary.api.placeholders

import org.pnfolder.pnlibrary.api.placeholders.reply.PublishedAnswers
import java.util.UUID

/** Bridge between the placeholders of the library and one outside placeholder system, such as PlaceholderAPI. */
interface PlaceholderAdapter {
    /** Stable, case-insensitive identifier. */
    val id: String

    /** Whether the outside system is there to talk to right now. */
    val available: Boolean

    /** The value the outside system gives for [expression], or `null`. */
    fun resolve(playerId: UUID?, expression: String): Any? = null

    /** Replaces the tokens the outside system owns in [text] (for PlaceholderAPI, every `%...%`); unknown ones stay. */
    fun render(playerId: UUID?, text: String): String = text

    /** Shows everything under [prefix] to the outside system, answering with [answers]. Close the handle to withdraw it. */
    fun publish(prefix: String, answers: PublishedAnswers): AutoCloseable
}
