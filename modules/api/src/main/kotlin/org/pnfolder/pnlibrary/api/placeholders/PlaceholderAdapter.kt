package org.pnfolder.pnlibrary.api.placeholders

import java.util.UUID

/** How a published prefix is answered: the text after the prefix goes in, the value (or `null`) comes out. */
fun interface PublishedAnswers {
    /** [params] is the text of the outside request without the prefix; [waitMillis] is how long to wait for a late value (0: not at all). */
    fun answer(playerId: UUID?, params: String, waitMillis: Long): String?
}

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

/** The adapters of the outside placeholder systems that are connected. */
interface PlaceholderAdapterRegistry {
    /** Registers [adapter] and returns a handle that removes and closes it. */
    fun register(adapter: PlaceholderAdapter): AutoCloseable

    /** The adapter with case-insensitive [id], or `null`. */
    fun get(id: String): PlaceholderAdapter?

    /** The adapter or a failure when it is not connected. */
    fun require(id: String): PlaceholderAdapter = get(id) ?: error("Placeholder adapter $id is unavailable")

    /** A stable snapshot of the adapters ordered by identifier. */
    fun all(): List<PlaceholderAdapter>
}
