package ru.privatenull.pnlibrary.api.actions.standard.check

import ru.privatenull.pnlibrary.api.actions.Action

/**
 * Public model namespace for reusable action checks and typed expressions.
 *
 * @property actions actions contained in this expansion
 */
data class Expansion(
    val actions: List<Action> = emptyList(),
) {

    /**
     * Conditional action branches selected by one condition.
     *
     * @property condition predicate selecting the branch
     * @property thenActions actions executed when [condition] is true
     * @property elseActions actions executed when [condition] is false
     */
    data class If(
        val condition: Condition,
        val thenActions: List<Action> = emptyList(),
        val elseActions: List<Action> = emptyList(),
    )

    /** Boolean predicate evaluated against resolved expressions. */
    sealed interface Condition {

        /**
         * Comparison between two resolved expressions.
         *
         * @property left left-hand expression
         * @property operator comparison operation
         * @property right right-hand expression
         */
        data class Comparison(
            val left: Expression,
            val operator: ComparisonOperator,
            val right: Expression,
        ) : Condition

        /**
         * Type or state check applied to one expression.
         *
         * @property expression expression to inspect
         * @property operator check operation
         */
        data class Check(
            val expression: Expression,
            val operator: CheckOperator,
        ) : Condition

        /** Conjunction that passes only when every [conditions] entry passes. */
        data class All(
            /** Conditions combined by logical AND. */
            val conditions: List<Condition>,
        ) : Condition

        /** Disjunction that passes when at least one [conditions] entry passes. */
        data class Any(
            /** Conditions combined by logical OR. */
            val conditions: List<Condition>,
        ) : Condition

        /** Logical negation of [condition]. */
        data class Not(
            /** Condition whose result is inverted. */
            val condition: Condition,
        ) : Condition
    }

    /** Typed value expression used by conditions. */
    sealed interface Expression {

        /** Literal expression containing a preconstructed [value]. */
        data class Literal(
            /** Constant configuration value. */
            val value: ConfigValue,
        ) : Expression

        /** Variable lookup expression. */
        data class Variable(
            /** Variable key resolved from the action context. */
            val key: String,
        ) : Expression

        /**
         * Built-in function invocation.
         *
         * @property function transformation to invoke
         * @property arguments ordered function arguments
         */
        data class Function(
            val function: FunctionType,
            val arguments: List<Expression> = emptyList(),
        ) : Expression

        /** Convenience factories for typed expressions. */
        companion object {

            /** Wraps an existing configuration [value] as a literal. */
            fun literal(value: ConfigValue): Expression =
                Literal(value)

            /** Creates a string literal. */
            fun literal(value: String): Expression =
                Literal(ConfigValue.String(value))

            /** Creates an integer literal from a byte. */
            fun literal(value: Byte): Expression =
                Literal(ConfigValue.Integer(value.toLong()))

            /** Creates an integer literal from a short. */
            fun literal(value: Short): Expression =
                Literal(ConfigValue.Integer(value.toLong()))

            /** Creates an integer literal from an integer. */
            fun literal(value: Int): Expression =
                Literal(ConfigValue.Integer(value.toLong()))

            /** Creates an integer literal from a long. */
            fun literal(value: Long): Expression =
                Literal(ConfigValue.Integer(value))

            /** Creates a decimal literal from a float. */
            fun literal(value: Float): Expression =
                Literal(ConfigValue.Decimal(value.toDouble()))

            /** Creates a decimal literal from a double. */
            fun literal(value: Double): Expression =
                Literal(ConfigValue.Decimal(value))

            /** Creates a boolean literal. */
            fun literal(value: Boolean): Expression =
                Literal(ConfigValue.Boolean(value))

            /** Creates the null literal. */
            fun nullValue(): Expression =
                Literal(ConfigValue.Null)

            /** Creates a lookup for variable [key]. */
            fun variable(key: String): Expression =
                Variable(key)

            /** Creates a built-in [function] invocation with ordered [arguments]. */
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

        /** String scalar value. */
        data class String(
            /** Wrapped string. */
            val value: kotlin.String,
        ) : ConfigValue

        /** Integral numeric value. */
        data class Integer(
            /** Wrapped 64-bit integer. */
            val value: Long,
        ) : ConfigValue

        /** Decimal numeric value. */
        data class Decimal(
            /** Wrapped double-precision number. */
            val value: Double,
        ) : ConfigValue

        /** Boolean scalar value. */
        data class Boolean(
            /** Wrapped boolean. */
            val value: kotlin.Boolean,
        ) : ConfigValue

        /** Explicit null value. */
        data object Null : ConfigValue

        /** Ordered structured value. */
        data class Array(
            /** Ordered child values. */
            val values: List<ConfigValue> = emptyList(),
        ) : ConfigValue

        /** Keyed structured value. */
        data class Object(
            /** Child values keyed by field name. */
            val values: Map<kotlin.String, ConfigValue> = emptyMap(),
        ) : ConfigValue

        /** Convenience factories for configuration values. */
        companion object {

            /** Creates a string value. */
            fun of(value: kotlin.String): ConfigValue =
                String(value)

            /** Creates an integer value from a byte. */
            fun of(value: Byte): ConfigValue =
                Integer(value.toLong())

            /** Creates an integer value from a short. */
            fun of(value: Short): ConfigValue =
                Integer(value.toLong())

            /** Creates an integer value from an integer. */
            fun of(value: Int): ConfigValue =
                Integer(value.toLong())

            /** Creates an integer value from a long. */
            fun of(value: Long): ConfigValue =
                Integer(value)

            /** Creates a decimal value from a float. */
            fun of(value: Float): ConfigValue =
                Decimal(value.toDouble())

            /** Creates a decimal value from a double. */
            fun of(value: Double): ConfigValue =
                Decimal(value)

            /** Creates a boolean value. */
            fun of(value: kotlin.Boolean): ConfigValue =
                Boolean(value)

            /** Returns the explicit null value. */
            fun nullValue(): ConfigValue =
                Null

            /** Creates an ordered array from [values]. */
            fun arrayOf(
                vararg values: ConfigValue,
            ): ConfigValue =
                Array(values.toList())

            /** Creates an object from keyed [values]. */
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
