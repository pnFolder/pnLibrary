package ru.privatenull.pnlibrary.api.actions

import ru.privatenull.pnlibrary.api.config.ConfigPolymorphic
import ru.privatenull.pnlibrary.api.config.ConfigKey
import ru.privatenull.pnlibrary.api.config.ConfigType
import ru.privatenull.pnlibrary.api.config.ConfigTypes

@ConfigPolymorphic(discriminator = "type")
@ConfigTypes(
    ConfigType(ValueCondition::class, "compare", aliases = ["value"]),
    ConfigType(EnabledCondition::class, "flag", aliases = ["enabled"]),
    ConfigType(PermissionCondition::class, "permission", aliases = ["perm"]),
    ConfigType(ChanceCondition::class, "chance"),
    ConfigType(NotCondition::class, "not"),
)
/**
 * A configuration-friendly predicate evaluated against one [ActionContext].
 *
 * Conditions are polymorphic values selected by their `type` field. Implementations
 * should be side-effect free because compound conditions may evaluate them repeatedly
 * across separate executions of the same action graph.
 */
fun interface ActionCondition {
    /** Returns whether [context] satisfies this condition. */
    fun matches(context: ActionContext): Boolean
}

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
data class EnabledCondition(
    @field:ConfigKey("name")
    val flag: String = "",
    @field:ConfigKey("equals")
    val enabled: Boolean = true,
) : ActionCondition {
    override fun matches(context: ActionContext): Boolean {
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
data class PermissionCondition(
    @field:ConfigKey("node")
    val permission: String = "",
) : ActionCondition {
    override fun matches(context: ActionContext): Boolean =
        permission.isNotBlank() && context.player.hasPermission(permission)
}

/**
 * Matches randomly with the configured [probability].
 *
 * @property probability inclusive configuration range from `0.0` to `1.0`; `0.0`
 * never matches and `1.0` always matches
 * @throws IllegalArgumentException when [probability] is outside the supported range
 */
data class ChanceCondition(
    val probability: Double = 1.0,
) : ActionCondition {
    init { require(probability in 0.0..1.0) { "Chance probability must be between 0.0 and 1.0" } }
    override fun matches(context: ActionContext): Boolean = Math.random() < probability
}

/**
 * Negates [check]; a missing check is treated as false and therefore matches.
 *
 * @property check predicate to negate, or `null` for an always-matching condition
 */
data class NotCondition(
    @field:ConfigKey("check")
    val check: ActionCondition? = null,
) : ActionCondition {
    override fun matches(context: ActionContext): Boolean = !(check?.matches(context) ?: false)
}

/** Comparison operation used by [ValueCondition]. */
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
    /** Whether a nonblank value exists; does not use [ValueCondition.value]. */
    PRESENT,
    /** Whether the value is missing or blank; does not use [ValueCondition.value]. */
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
data class ValueCondition(
    val source: String = "",
    @field:ConfigKey("operator")
    val operator: Comparison = Comparison.EQUALS,
    val value: String = "",
    val ignoreCase: Boolean = true,
) : ActionCondition {
    override fun matches(context: ActionContext): Boolean {
        val raw = context.value(source)
        if (operator == Comparison.PRESENT) return raw != null && raw.toString().isNotBlank()
        if (operator == Comparison.ABSENT) return raw == null || raw.toString().isBlank()
        val actual = raw?.toString() ?: return false
        val left = if (ignoreCase) actual.lowercase() else actual
        val right = if (ignoreCase) value.lowercase() else value
        fun numeric(predicate: (Double, Double) -> Boolean): Boolean {
            val a = actual.toDoubleOrNull() ?: return false
            val b = value.toDoubleOrNull() ?: return false
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
            Comparison.PRESENT, Comparison.ABSENT -> false
        }
    }
}
