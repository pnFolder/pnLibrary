package org.pnfolder.pnlibrary.api.checks

import org.pnfolder.pnlibrary.api.audiences.LibraryPlayer
import org.pnfolder.pnlibrary.api.logging.PnLogger

/**
 * What a [Check] may look at: the player, placeholders and values, and the named checks of the plugin.
 *
 * [org.pnfolder.pnlibrary.api.actions.ActionContext] implements it, so the same check works inside
 * actions and on its own.
 */
interface CheckContext {
    /** The player being checked. */
    val player: LibraryPlayer

    /** Logger for warnings about checks that cannot be evaluated. */
    val logger: PnLogger

    /** Resolves `%placeholder%`, `[[value]]` or a plain value name, or returns `null`. */
    fun read(expression: String): Any?

    /** Returns [expression] with every placeholder and value replaced, as text. */
    fun textOf(expression: String): String

    /** Returns the value stored under [name], or `null`. */
    fun value(name: String): Any?

    /** Returns the requirement declared under `checks:` with [name], or `null`. */
    fun requirement(name: String): Requirement?

    /** Names of every requirement declared under `checks:`, used in "Did you mean" hints. */
    fun requirementNames(): Set<String> = emptySet()
}
