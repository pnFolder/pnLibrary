package org.pnfolder.pnlibrary.api.scenario.expression

import org.pnfolder.pnlibrary.api.player.ItemQuery
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext
import java.util.concurrent.ThreadLocalRandom

/**
 * A small, safe calculator for conditions and numbers in configuration. It cannot touch files or run code.
 *
 * ```
 * level >= 10 && (has_vip || chance(0.25))
 * clamp(balance / 100, 1, 10) * 5
 * contains(upper(rank), 'ADMIN') ? 1 : 0
 * ```
 *
 * **Values.** A bare name reads a value of the run, then a pnLibrary placeholder. `{plugin:key}` reads a pnLibrary
 * placeholder, `%name%` an external one, `[name]` a value of the run (use it for names with dashes).
 * Text that looks like a number counts as a number.
 *
 * **Operators**, tightest first: `!` and unary `-`; `^` (power); `*` `/` `%`; `+` `-` (`+` joins text when a side is
 * not a number); `<` `<=` `>` `>=`; `==` `!=`; `&&`; `||`; `condition ? a : b`.
 * Numbers, `'text'`, `"text"`, `true`, `false`.
 *
 * **Functions:** `min`, `max`, `abs`, `round(x, digits)`, `floor`, `ceil`, `sqrt`, `pow`, `clamp(x, low, high)`,
 * `random(low, high)`, `chance(p)`, `len`, `upper`, `lower`, `contains`, `startsWith`, `endsWith`, `has(name)`,
 * `if(c, a, b)`, and about the player `permission(node)`, `level()`, `exp()`, `items(material)`,
 * `distance(world, x, y, z)`. Plugins add more with `ActionService.defineFunction`.
 *
 * ```kotlin
 * val expression = Expression.parse("level() >= 10 && permission('shop.use')")
 * val allowed: Boolean = expression.test(context)
 * ```
 */
class Expression private constructor(
    /** The text this expression was parsed from. */
    val source: String,
    private val root: Node,
) {
    internal fun interface Node { fun eval(context: RequirementContext): Any? }

    /** Computes the value: `Double`, `Boolean`, `String`, or `null` for an unknown name. */
    fun evaluate(context: RequirementContext): Any? = root.eval(context)

    /** Computes the value and reads it as a yes/no answer. */
    fun test(context: RequirementContext): Boolean = truthy(evaluate(context))

    /** Computes the value as text, with whole numbers written without a decimal point. */
    fun text(context: RequirementContext): String = format(evaluate(context))

    override fun toString(): String = source

    /** Parsing and shared value rules. */
    companion object {
        /** Parses [text]; throws [ExpressionException] with the position of the mistake. */
        @JvmStatic
        fun parse(text: String): Expression = Expression(text, Parser(text).parseAll())

        /** Writes [value] as text: `5.0` becomes `5`, `null` becomes an empty text. */
        @JvmStatic
        fun format(value: Any?): String = when (value) {
            null -> ""
            is Double -> if (value.isFinite() && value == Math.rint(value) && Math.abs(value) < 1e15) value.toLong().toString() else value.toString()
            else -> value.toString()
        }

        internal fun number(value: Any?): Double? = when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().toDoubleOrNull()
            is Boolean -> if (value) 1.0 else 0.0
            else -> null
        }

        internal fun truthy(value: Any?): Boolean = when (value) {
            null -> false
            is Boolean -> value
            is Number -> value.toDouble() != 0.0
            is String -> value.isNotBlank() && value.trim().lowercase() !in FALSE_WORDS
            else -> true
        }

        private val FALSE_WORDS = setOf("false", "0", "no", "off", "disabled")

        internal fun required(value: Any?): Double =
            number(value) ?: throw ExpressionException("'${format(value)}' is not a number")

        internal fun normalize(value: Any?): Any? = when (value) {
            is Number -> value.toDouble()
            else -> value
        }

        internal fun builtin(name: String): ExpressionFunction? = BUILTIN[name]

        /** Whether [name] is a function of the language itself, which plugins cannot redefine. */
        @JvmStatic
        fun isBuiltin(name: String): Boolean = name in BUILTIN

        private val BUILTIN: Map<String, ExpressionFunction> = buildMap {
            fun define(vararg names: String, function: ExpressionFunction) = names.forEach { put(it, function) }
            fun ExpressionFunction.arity(count: IntRange, name: String) = ExpressionFunction { c, a ->
                if (a.size !in count) throw ExpressionException("$name() takes ${count.first}..${count.last} arguments but got ${a.size}")
                call(c, a)
            }
            define("min") { _, a -> a.minOfOrNull { required(it) } ?: throw ExpressionException("min() needs a value") }
            define("max") { _, a -> a.maxOfOrNull { required(it) } ?: throw ExpressionException("max() needs a value") }
            define("abs", function = ExpressionFunction { _, a -> Math.abs(required(a[0])) }.arity(1..1, "abs"))
            define("floor", function = ExpressionFunction { _, a -> Math.floor(required(a[0])) }.arity(1..1, "floor"))
            define("ceil", function = ExpressionFunction { _, a -> Math.ceil(required(a[0])) }.arity(1..1, "ceil"))
            define("sqrt", function = ExpressionFunction { _, a -> Math.sqrt(required(a[0])) }.arity(1..1, "sqrt"))
            define("pow", function = ExpressionFunction { _, a -> Math.pow(required(a[0]), required(a[1])) }.arity(2..2, "pow"))
            define("round", function = ExpressionFunction { _, a ->
                val scale = Math.pow(10.0, if (a.size > 1) required(a[1]) else 0.0)
                Math.round(required(a[0]) * scale) / scale
            }.arity(1..2, "round"))
            define("clamp", function = ExpressionFunction { _, a -> required(a[0]).coerceIn(required(a[1]), required(a[2])) }.arity(3..3, "clamp"))
            define("random", function = ExpressionFunction { _, a ->
                val low = required(a[0]).toLong()
                val high = required(a[1]).toLong()
                if (high < low) throw ExpressionException("random() needs low <= high")
                ThreadLocalRandom.current().nextLong(low, high + 1).toDouble()
            }.arity(2..2, "random"))
            define("chance", function = ExpressionFunction { _, a -> ThreadLocalRandom.current().nextDouble() < required(a[0]) }.arity(1..1, "chance"))
            define("len", function = ExpressionFunction { _, a -> format(a[0]).length.toDouble() }.arity(1..1, "len"))
            define("upper", function = ExpressionFunction { _, a -> format(a[0]).uppercase() }.arity(1..1, "upper"))
            define("lower", function = ExpressionFunction { _, a -> format(a[0]).lowercase() }.arity(1..1, "lower"))
            define("contains", function = ExpressionFunction { _, a -> format(a[0]).contains(format(a[1]), ignoreCase = true) }.arity(2..2, "contains"))
            define("startsWith", function = ExpressionFunction { _, a -> format(a[0]).startsWith(format(a[1]), ignoreCase = true) }.arity(2..2, "startsWith"))
            define("endsWith", function = ExpressionFunction { _, a -> format(a[0]).endsWith(format(a[1]), ignoreCase = true) }.arity(2..2, "endsWith"))
            define("has", function = ExpressionFunction { c, a -> c.value(format(a[0])) != null }.arity(1..1, "has"))
            define("if", function = ExpressionFunction { _, a -> if (truthy(a[0])) a[1] else a[2] }.arity(3..3, "if"))
            define("permission", "perm", function = ExpressionFunction { c, a -> c.player.hasPermission(format(a[0])) }.arity(1..1, "permission"))
            define("level", function = ExpressionFunction { c, _ -> c.player.experience(true) ?: 0.0 })
            define("exp", function = ExpressionFunction { c, _ -> c.player.experience(false) ?: 0.0 })
            define("items", function = ExpressionFunction { c, a -> (c.player.countItems(ItemQuery(format(a[0]))) ?: 0).toDouble() }.arity(1..1, "items"))
            define("distance", function = ExpressionFunction { c, a ->
                c.player.distanceTo(format(a[0]), required(a[1]), required(a[2]), required(a[3])) ?: Double.POSITIVE_INFINITY
            }.arity(4..4, "distance"))
        }
    }
}
