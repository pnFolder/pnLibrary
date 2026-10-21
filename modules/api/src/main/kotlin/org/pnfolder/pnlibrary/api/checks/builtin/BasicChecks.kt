package org.pnfolder.pnlibrary.api.checks.builtin

import org.pnfolder.pnlibrary.api.checks.Check
import org.pnfolder.pnlibrary.api.checks.CheckContext
import org.pnfolder.pnlibrary.api.checks.internal.CheckSerializer
import org.pnfolder.pnlibrary.api.config.ConfigKey
import org.pnfolder.pnlibrary.api.config.ConfigSerializeWith


/**
 * Interprets a named context value as a boolean and compares it with [enabled].
 *
 * Boolean values are used directly, numeric zero is false, and strings are true when
 * equal to `true`, `yes`, `on`, `1`, or `enabled` ignoring case. Missing and all other
 * values are false.
 *
 * @property flag key read from [ActionContext.values]
 * @property enabled boolean state required for a match
 */
@ConfigSerializeWith(CheckSerializer::class)
data class FlagCheck(
    @field:ConfigKey("name")
    val flag: String = "",
    @field:ConfigKey("equals")
    val enabled: Boolean = true,
) : Check {
    override fun matches(context: CheckContext): Boolean {
        val actual = when (val value = context.value(flag)) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            else -> value?.toString()?.trim()?.lowercase() in setOf("true", "yes", "on", "1", "enabled")
        }
        return actual == enabled
    }
}

/**
 * Matches when the invoking player has [permission].
 *
 * A blank permission is always rejected and is never passed to the platform adapter.
 *
 * @property permission platform permission node required from the invoking player
 */
@ConfigSerializeWith(CheckSerializer::class)
data class PermissionCheck(
    @field:ConfigKey("node")
    val permission: String = "",
) : Check {
    override fun matches(context: CheckContext): Boolean =
        permission.isNotBlank() && context.player.hasPermission(permission)
}

/**
 * Matches randomly with the configured [probability].
 *
 * @property probability inclusive configuration range from `0.0` to `1.0`; `0.0`
 * never matches and `1.0` always matches
 * @throws IllegalArgumentException when [probability] is outside the supported range
 */
@ConfigSerializeWith(CheckSerializer::class)
data class ChanceCheck(
    val probability: Double = 1.0,
) : Check {
    init { require(probability in 0.0..1.0) { "Chance must be from 0% to 100%" } }
    override fun matches(context: CheckContext): Boolean = Math.random() < probability
}

/**
 * Negates [check]; a missing check is treated as false and therefore matches.
 *
 * @property check predicate to negate, or `null` for an always-matching condition
 */
@ConfigSerializeWith(CheckSerializer::class)
data class NotCheck(
    @field:ConfigKey("check")
    val check: Check? = null,
) : Check {
    override fun matches(context: CheckContext): Boolean = !(check?.matches(context) ?: false)
}

/** Comparison operation used by [ValueCheck]. */
enum class Comparison {
    /** String equality after applying the configured case policy. */
    EQUALS,
    /** String inequality after applying the configured case policy. */
    NOT_EQUALS,
    /** Whether the actual string contains the expected string. */
    CONTAINS,
    /** Numeric greater-than comparison. */
    GREATER_THAN,
    /** Numeric greater-than-or-equal comparison. */
    GREATER_OR_EQUAL,
    /** Numeric less-than comparison. */
    LESS_THAN,
    /** Numeric less-than-or-equal comparison. */
    LESS_OR_EQUAL,
    /** Numeric equality, so `5` equals `5.0`. */
    NUMBER_EQUALS,
    /** Numeric inequality. */
    NUMBER_NOT_EQUALS,
    /** Whether a nonblank value exists; does not use [ValueCheck.value]. */
    PRESENT,
    /** Whether the value is missing or blank; does not use [ValueCheck.value]. */
    ABSENT,
}

/**
 * Compares one named runtime value with an expected string representation.
 *
 * Equality and containment operate on strings. Ordering comparisons require both the
 * actual and configured values to parse as [Double] and return false otherwise. [Comparison.PRESENT]
 * requires a nonblank value; [Comparison.ABSENT] accepts either a missing or blank value.
 *
 * @property source key read from [ActionContext.values]
 * @property operator operation applied to the context value
 * @property value right-hand operand, ignored for presence checks
 * @property ignoreCase whether textual comparisons use case-insensitive matching
 */
@ConfigSerializeWith(CheckSerializer::class)
data class ValueCheck(
    val source: String = "",
    @field:ConfigKey("operator")
    val operator: Comparison = Comparison.EQUALS,
    val value: String = "",
    val ignoreCase: Boolean = true,
) : Check {
    override fun matches(context: CheckContext): Boolean {
        val raw = context.read(source)
        if (operator == Comparison.PRESENT) return raw != null && raw.toString().isNotBlank()
        if (operator == Comparison.ABSENT) return raw == null || raw.toString().isBlank()
        val actual = raw?.toString() ?: return false
        val left = if (ignoreCase) actual.lowercase() else actual
        val right = if (ignoreCase) value.lowercase() else value
        fun numeric(predicate: (Double, Double) -> Boolean): Boolean {
            val a = actual.toDoubleOrNull() ?: return false
            val b = context.read(value)?.toString()?.toDoubleOrNull() ?: value.toDoubleOrNull() ?: return false
            return predicate(a, b)
        }
        return when (operator) {
            Comparison.EQUALS -> left == right
            Comparison.NOT_EQUALS -> left != right
            Comparison.CONTAINS -> right in left
            Comparison.GREATER_THAN -> numeric { a, b -> a > b }
            Comparison.GREATER_OR_EQUAL -> numeric { a, b -> a >= b }
            Comparison.LESS_THAN -> numeric { a, b -> a < b }
            Comparison.LESS_OR_EQUAL -> numeric { a, b -> a <= b }
            Comparison.NUMBER_EQUALS -> numeric { a, b -> a == b }
            Comparison.NUMBER_NOT_EQUALS -> numeric { a, b -> a != b }
            Comparison.PRESENT, Comparison.ABSENT -> false
        }
    }
}
