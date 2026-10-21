package org.pnfolder.pnlibrary.api.checks.builtin

import org.pnfolder.pnlibrary.api.checks.Check
import org.pnfolder.pnlibrary.api.checks.CheckContext
import org.pnfolder.pnlibrary.api.checks.internal.CheckSerializer
import org.pnfolder.pnlibrary.api.config.ConfigSerializeWith
import org.pnfolder.pnlibrary.api.internal.Suggestions

/** Matches when every check in [checks] matches; an empty list matches. Written as `type: all`. */
@ConfigSerializeWith(CheckSerializer::class)
data class AllCheck(val checks: List<Check> = emptyList()) : Check {
    override fun matches(context: CheckContext): Boolean = checks.all { it.matches(context) }
}

/** Matches when at least one check in [checks] matches; an empty list does not. Written as `type: any`. */
@ConfigSerializeWith(CheckSerializer::class)
data class AnyCheck(val checks: List<Check> = emptyList()) : Check {
    override fun matches(context: CheckContext): Boolean = checks.any { it.matches(context) }
}

/**
 * Matches when at least [count] of [checks] match. Written as `type: at-least` with `count`.
 *
 * @throws IllegalArgumentException when [count] is negative or above the number of checks
 */
@ConfigSerializeWith(CheckSerializer::class)
data class AtLeastCheck(val count: Int = 1, val checks: List<Check> = emptyList()) : Check {
    init {
        require(count in 0..checks.size) { "at-least count ($count) must be from 0 to the number of checks (${checks.size})" }
    }

    override fun matches(context: CheckContext): Boolean {
        var matched = 0
        for (check in checks) {
            if (check.matches(context) && ++matched >= count) return true
        }
        return matched >= count
    }
}

/**
 * Asks the requirement declared under `checks:` with [name], without running its `deny` or `success`.
 *
 * Written as the name alone: `check: has-100`. An unknown name does not match and logs a warning.
 */
@ConfigSerializeWith(CheckSerializer::class)
data class NamedCheck(val name: String = "") : Check {
    override fun matches(context: CheckContext): Boolean {
        val requirement = context.requirement(name)
        if (requirement == null) {
            context.logger.warning("Unknown check '$name'.${Suggestions.hint(name, context.requirementNames())}")
            return false
        }
        return requirement.check(context)
    }
}
