package org.pnfolder.pnlibrary.api.scenario.requirements

import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.api.player.LibraryPlayer
import org.pnfolder.pnlibrary.api.scenario.expression.ExpressionFunction

/**
 * What a [Requirement] may look at: the player, the values of the invocation, placeholders and services.
 *
 * [org.pnfolder.pnlibrary.api.scenario.actions.ActionContext] implements it, so the same requirement works inside
 * an action list and on its own.
 */
interface RequirementContext {
    /** The player being judged. */
    val player: LibraryPlayer

    /** Logger for requirements that cannot be evaluated. */
    val logger: PnLogger

    /**
     * Reads [expression] as a value: one `[value]`, `{placeholder}` or `%external%`, text that contains them,
     * or a bare name (a context value first, then a pnLibrary placeholder). Returns `null` when nothing matches.
     */
    fun read(expression: String): Any?

    /** Returns [expression] with every placeholder and value replaced; unknown names stay as written. */
    fun text(expression: String): String

    /** Returns the context value called [name], or `null`. */
    fun value(name: String): Any?

    /** Returns the requirement a plugin defined under [name] with `ActionService.defineRequirement`, or `null`. */
    fun requirement(name: String): Requirement? = null

    /** Returns the expression function a plugin defined under [name] with `ActionService.defineFunction`, or `null`. */
    fun function(name: String): ExpressionFunction? = null

    /** Returns the service of [type] available to this invocation, such as the currency service, or `null`. */
    fun <T : Any> service(type: Class<T>): T?
}
