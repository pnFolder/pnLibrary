@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicProperty", "UndocumentedPublicFunction")

package ru.privatenull.pnlibrary.api.actions.standard.check

import ru.privatenull.pnlibrary.api.actions.Action

/** Public model namespace for reusable action checks and typed expressions. */
data class Expansion(
    val actions: List<Action> = emptyList(),
) {

    /** Conditional action branches selected by one condition. */
    data class If(
        val condition: Condition,
        val thenActions: List<Action> = emptyList(),
        val elseActions: List<Action> = emptyList(),
    )

    /** Boolean predicate evaluated against resolved expressions. */
    sealed interface Condition {

        data class Comparison(
            val left: Expression,
            val operator: ComparisonOperator,
            val right: Expression,
        ) : Condition

        data class Check(
            val expression: Expression,
            val operator: CheckOperator,
        ) : Condition

        data class All(
            val conditions: List<Condition>,
        ) : Condition

        data class Any(
            val conditions: List<Condition>,
        ) : Condition

        data class Not(
            val condition: Condition,
        ) : Condition
    }

    /** Typed value expression used by conditions. */
    sealed interface Expression {

        data class Literal(
            val value: ConfigValue,
        ) : Expression

        data class Variable(
            val key: String,
        ) : Expression

        data class Function(
            val function: FunctionType,
            val arguments: List<Expression> = emptyList(),
        ) : Expression

        companion object {

            fun literal(value: ConfigValue): Expression =
                Literal(value)

            fun literal(value: String): Expression =
                Literal(ConfigValue.String(value))

            fun literal(value: Byte): Expression =
                Literal(ConfigValue.Integer(value.toLong()))

            fun literal(value: Short): Expression =
                Literal(ConfigValue.Integer(value.toLong()))

            fun literal(value: Int): Expression =
                Literal(ConfigValue.Integer(value.toLong()))

            fun literal(value: Long): Expression =
                Literal(ConfigValue.Integer(value))

            fun literal(value: Float): Expression =
                Literal(ConfigValue.Decimal(value.toDouble()))

            fun literal(value: Double): Expression =
                Literal(ConfigValue.Decimal(value))

            fun literal(value: Boolean): Expression =
                Literal(ConfigValue.Boolean(value))

            fun nullValue(): Expression =
                Literal(ConfigValue.Null)

            fun variable(key: String): Expression =
                Variable(key)

            fun function(
                function: FunctionType,
                vararg arguments: Expression,
            ): Expression =
                Function(
                    function = function,
                    arguments = arguments.toList(),
                )
        }
    }

    /** Scalar or structured value used by an expression. */
    sealed interface ConfigValue {

        data class String(
            val value: kotlin.String,
        ) : ConfigValue

        data class Integer(
            val value: Long,
        ) : ConfigValue

        data class Decimal(
            val value: Double,
        ) : ConfigValue

        data class Boolean(
            val value: kotlin.Boolean,
        ) : ConfigValue

        data object Null : ConfigValue

        data class Array(
            val values: List<ConfigValue> = emptyList(),
        ) : ConfigValue

        data class Object(
            val values: Map<kotlin.String, ConfigValue> = emptyMap(),
        ) : ConfigValue

        companion object {

            fun of(value: kotlin.String): ConfigValue =
                String(value)

            fun of(value: Byte): ConfigValue =
                Integer(value.toLong())

            fun of(value: Short): ConfigValue =
                Integer(value.toLong())

            fun of(value: Int): ConfigValue =
                Integer(value.toLong())

            fun of(value: Long): ConfigValue =
                Integer(value)

            fun of(value: Float): ConfigValue =
                Decimal(value.toDouble())

            fun of(value: Double): ConfigValue =
                Decimal(value)

            fun of(value: kotlin.Boolean): ConfigValue =
                Boolean(value)

            fun nullValue(): ConfigValue =
                Null

            fun arrayOf(
                vararg values: ConfigValue,
            ): ConfigValue =
                Array(values.toList())

            fun objectOf(
                vararg values: Pair<kotlin.String, ConfigValue>,
            ): ConfigValue =
                Object(values.toMap())
        }
    }

    /** Operators for comparing two resolved values. */
    enum class ComparisonOperator {

        EQUALS,
        NOT_EQUALS,

        GREATER_THAN,
        GREATER_THAN_OR_EQUALS,

        LESS_THAN,
        LESS_THAN_OR_EQUALS,

        CONTAINS,
        NOT_CONTAINS,

        STARTS_WITH,
        NOT_STARTS_WITH,

        ENDS_WITH,
        NOT_ENDS_WITH,

        IN,
        NOT_IN,
    }

    /** Operators for checking the type or state of one resolved value. */
    enum class CheckOperator {

        IS_NULL,
        IS_NOT_NULL,

        IS_EMPTY,
        IS_NOT_EMPTY,

        IS_STRING,
        IS_NOT_STRING,

        IS_NUMBER,
        IS_NOT_NUMBER,

        IS_BOOLEAN,
        IS_NOT_BOOLEAN,

        IS_ARRAY,
        IS_NOT_ARRAY,

        IS_OBJECT,
        IS_NOT_OBJECT,
    }

    /** Built-in transformations available to function expressions. */
    enum class FunctionType {

        /*
         * Generic
         */
        SIZE,
        GET,

        /*
         * String
         */
        LENGTH,
        LOWERCASE,
        UPPERCASE,
        TRIM,
        SPLIT,
        JOIN,
        REPLACE,
        SUBSTRING,

        /*
         * Collection
         */
        FIRST,
        LAST,

        /*
         * Object
         */
        KEYS,
        VALUES,

        /*
         * Conversion
         */
        TO_STRING,
        TO_INTEGER,
        TO_DECIMAL,
        TO_BOOLEAN,
    }
}
