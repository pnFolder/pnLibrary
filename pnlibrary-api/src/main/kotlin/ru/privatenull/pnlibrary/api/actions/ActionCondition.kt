package ru.privatenull.pnlibrary.api.actions

import ru.privatenull.pnlibrary.api.config.ConfigPolymorphic
import ru.privatenull.pnlibrary.api.config.ConfigType
import ru.privatenull.pnlibrary.api.config.ConfigTypes

@ConfigPolymorphic(discriminator = "type")
@ConfigTypes(
    ConfigType(ValueCondition::class, "value"),
    ConfigType(EnabledCondition::class, "enabled", aliases = ["flag"]),
    ConfigType(PermissionCondition::class, "permission", aliases = ["perm"]),
    ConfigType(ChanceCondition::class, "chance"),
    ConfigType(AllCondition::class, "all"),
    ConfigType(AnyCondition::class, "any"),
    ConfigType(NotCondition::class, "not"),
)
fun interface ActionCondition {
    fun matches(context: ActionContext): Boolean
}

/** Checks a boolean runtime value such as `economy-enabled`. */
data class EnabledCondition(
    val key: String = "",
    val expected: Boolean = true,
) : ActionCondition {
    override fun matches(context: ActionContext): Boolean {
        val enabled = when (val value = context.value(key)) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            else -> value?.toString()?.trim()?.lowercase() in setOf("true", "yes", "on", "1", "enabled")
        }
        return enabled == expected
    }
}

data class PermissionCondition(
    val permission: String = "",
) : ActionCondition {
    override fun matches(context: ActionContext): Boolean =
        permission.isNotBlank() && context.player.hasPermission(permission)
}

data class ChanceCondition(
    /** Probability from 0.0 to 1.0. */
    val probability: Double = 1.0,
) : ActionCondition {
    init { require(probability in 0.0..1.0) { "Chance probability must be between 0.0 and 1.0" } }
    override fun matches(context: ActionContext): Boolean = Math.random() < probability
}

data class AllCondition(val conditions: List<ActionCondition> = emptyList()) : ActionCondition {
    override fun matches(context: ActionContext): Boolean = conditions.all { it.matches(context) }
}

data class AnyCondition(val conditions: List<ActionCondition> = emptyList()) : ActionCondition {
    override fun matches(context: ActionContext): Boolean = conditions.any { it.matches(context) }
}

data class NotCondition(val condition: ActionCondition? = null) : ActionCondition {
    override fun matches(context: ActionContext): Boolean = !(condition?.matches(context) ?: false)
}
enum class Comparison {
    EQUALS, NOT_EQUALS, CONTAINS, GREATER_THAN, GREATER_OR_EQUAL, LESS_THAN, LESS_OR_EQUAL,
    PRESENT, ABSENT,
}

/** Compares one named runtime value supplied to the action execution. */
data class ValueCondition(
    val key: String = "",
    val comparison: Comparison = Comparison.EQUALS,
    val expected: String = "",
    val ignoreCase: Boolean = true,
) : ActionCondition {
    override fun matches(context: ActionContext): Boolean {
        val raw = context.value(key)
        if (comparison == Comparison.PRESENT) return raw != null && raw.toString().isNotBlank()
        if (comparison == Comparison.ABSENT) return raw == null || raw.toString().isBlank()
        val actual = raw?.toString() ?: return false
        val left = if (ignoreCase) actual.lowercase() else actual
        val right = if (ignoreCase) expected.lowercase() else expected
        fun numeric(predicate: (Double, Double) -> Boolean): Boolean {
            val a = actual.toDoubleOrNull() ?: return false
            val b = expected.toDoubleOrNull() ?: return false
            return predicate(a, b)
        }
        return when (comparison) {
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


