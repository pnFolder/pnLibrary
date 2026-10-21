package org.pnfolder.pnlibrary.api.actions

import org.pnfolder.pnlibrary.api.config.ConfigSerializeWith
import java.util.UUID
import java.util.regex.PatternSyntaxException

/**
 * Matches when the whole [input] fits the regular expression [regex].
 *
 * [input] is a context value or placeholder name, or plain text with `[[name]]` parts.
 *
 * @property input value to test
 * @property regex Java regular expression the entire value must match
 * @throws IllegalArgumentException when [regex] is not a valid expression
 */
@ConfigSerializeWith(ConditionSerializer::class)
data class RegexCondition(
    val input: String = "",
    val regex: String = ".*",
) : ActionCondition {
    private val pattern: Regex = try {
        Regex(regex)
    } catch (error: PatternSyntaxException) {
        throw IllegalArgumentException("Invalid regular expression '$regex': ${error.description}", error)
    }

    override fun matches(context: ActionContext): Boolean = pattern.matches(context.textOf(input))
}

/**
 * Matches when the length of [input] is from [min] to [max] characters.
 *
 * @property input value to measure
 * @property min shortest allowed length
 * @property max longest allowed length
 * @throws IllegalArgumentException when [min] is negative or above [max]
 */
@ConfigSerializeWith(ConditionSerializer::class)
data class LengthCondition(
    val input: String = "",
    val min: Int = 0,
    val max: Int = Int.MAX_VALUE,
) : ActionCondition {
    init { require(min in 0..max) { "Length range must satisfy 0 <= min <= max" } }

    override fun matches(context: ActionContext): Boolean = context.textOf(input).length in min..max
}

/** Kinds of value recognized by [TypeCondition]. */
enum class ValueType {
    /** A whole number. */
    INT,

    /** Any number, with or without a fraction. */
    DOUBLE,

    /** A UUID in the usual dashed form. */
    UUID,
}

/**
 * Matches when [input] can be read as a [type].
 *
 * @property input value to test
 * @property type expected kind of value
 */
@ConfigSerializeWith(ConditionSerializer::class)
data class TypeCondition(
    val input: String = "",
    val type: ValueType = ValueType.INT,
) : ActionCondition {
    override fun matches(context: ActionContext): Boolean {
        val text = context.textOf(input).trim()
        return when (type) {
            ValueType.INT -> text.toLongOrNull() != null
            ValueType.DOUBLE -> text.toDoubleOrNull() != null
            ValueType.UUID -> runCatching { UUID.fromString(text) }.isSuccess
        }
    }
}
