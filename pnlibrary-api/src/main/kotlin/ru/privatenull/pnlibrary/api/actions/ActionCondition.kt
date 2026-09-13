package ru.privatenull.pnlibrary.api.actions

import ru.privatenull.pnlibrary.api.config.ConfigPolymorphic
import ru.privatenull.pnlibrary.api.config.ConfigType
import ru.privatenull.pnlibrary.api.config.ConfigTypes

@ConfigPolymorphic(discriminator = "type")
@ConfigTypes(
    ConfigType(ValueCondition::class, "value"),
)
fun interface ActionCondition {
    fun matches(context: ActionContext): Boolean
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



