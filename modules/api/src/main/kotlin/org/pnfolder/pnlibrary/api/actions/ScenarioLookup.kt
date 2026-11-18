package org.pnfolder.pnlibrary.api.actions

import org.pnfolder.pnlibrary.api.requirements.Requirement
import org.pnfolder.pnlibrary.api.requirements.expression.ExpressionFunction

/**
 * Finds the actions, requirements and functions that plugins defined by name, for `call`, `ref` and expressions.
 *
 * [ActionService] keeps the definitions of one module and passes them to every context it creates.
 */
interface ScenarioLookup {
    /** The action defined under [name], or `null`. */
    fun action(name: String): Action? = null

    /** The requirement defined under [name], or `null`. */
    fun requirement(name: String): Requirement? = null

    /** The expression function defined under [name], or `null`. */
    fun function(name: String): ExpressionFunction? = null

    /** A lookup that knows nothing. */
    companion object {
        /** Finds nothing. */
        @JvmField
        val NONE: ScenarioLookup = object : ScenarioLookup {}
    }
}
