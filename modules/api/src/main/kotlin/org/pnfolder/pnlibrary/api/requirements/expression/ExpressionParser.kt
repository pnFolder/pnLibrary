package org.pnfolder.pnlibrary.api.requirements.expression

import org.pnfolder.pnlibrary.api.requirements.RequirementContext
import org.pnfolder.pnlibrary.api.requirements.expression.Expression.Companion.format
import org.pnfolder.pnlibrary.api.requirements.expression.Expression.Companion.normalize
import org.pnfolder.pnlibrary.api.requirements.expression.Expression.Companion.number
import org.pnfolder.pnlibrary.api.requirements.expression.Expression.Companion.required
import org.pnfolder.pnlibrary.api.requirements.expression.Expression.Companion.truthy

/** Recursive-descent parser of [Expression]; every rule is one precedence level, loosest first. */
internal class Parser(private val text: String) {
    private var position = 0

    fun parseAll(): Expression.Node {
        if (text.isBlank()) throw ExpressionException("The expression is empty")
        val node = ternary()
        skipSpaces()
        if (position < text.length) fail("Unexpected '${text[position]}'")
        return node
    }

    private fun fail(message: String): Nothing = throw ExpressionException("$message at position ${position + 1} of '$text'")

    private fun skipSpaces() {
        while (position < text.length && text[position].isWhitespace()) position++
    }

    private fun accept(token: String): Boolean {
        skipSpaces()
        if (!text.startsWith(token, position)) return false
        position += token.length
        return true
    }

    /** Accepts [token] only when no longer operator starts here, so `<` does not eat `<=`. */
    private fun acceptExact(token: String, vararg longer: String): Boolean {
        skipSpaces()
        if (longer.any { text.startsWith(it, position) }) return false
        return accept(token)
    }

    private fun ternary(): Expression.Node {
        val condition = or()
        if (!acceptExact("?")) return condition
        val yes = ternary()
        if (!accept(":")) fail("Expected ':' in a condition")
        val no = ternary()
        return Expression.Node { c -> if (truthy(condition.eval(c))) yes.eval(c) else no.eval(c) }
    }

    private fun or(): Expression.Node {
        var left = and()
        while (accept("||")) {
            val a = left
            val b = and()
            left = Expression.Node { c -> truthy(a.eval(c)) || truthy(b.eval(c)) }
        }
        return left
    }

    private fun and(): Expression.Node {
        var left = equality()
        while (accept("&&")) {
            val a = left
            val b = equality()
            left = Expression.Node { c -> truthy(a.eval(c)) && truthy(b.eval(c)) }
        }
        return left
    }

    private fun equality(): Expression.Node {
        var left = comparison()
        while (true) {
            val negate = when {
                accept("==") -> false
                accept("!=") -> true
                else -> return left
            }
            val a = left
            val b = comparison()
            left = Expression.Node { c -> same(a.eval(c), b.eval(c)) != negate }
        }
    }

    private fun comparison(): Expression.Node {
        var left = additive()
        while (true) {
            val op = listOf("<=", ">=", "<", ">").firstOrNull { if (it.length == 1) acceptExact(it, "<=", ">=") else accept(it) } ?: return left
            val a = left
            val b = additive()
            left = Expression.Node { c ->
                val x = a.eval(c)
                val y = b.eval(c)
                val order = compare(x, y)
                when (op) { "<" -> order < 0; "<=" -> order <= 0; ">" -> order > 0; else -> order >= 0 }
            }
        }
    }

    private fun additive(): Expression.Node {
        var left = multiplicative()
        while (true) {
            val plus = when {
                accept("+") -> true
                acceptExact("-") -> false
                else -> return left
            }
            val a = left
            val b = multiplicative()
            left = Expression.Node { c ->
                val x = a.eval(c)
                val y = b.eval(c)
                when {
                    !plus -> required(x) - required(y)
                    number(x) == null || number(y) == null -> format(x) + format(y)
                    else -> required(x) + required(y)
                }
            }
        }
    }

    private fun multiplicative(): Expression.Node {
        var left = power()
        while (true) {
            val op = listOf("*", "/", "%").firstOrNull { accept(it) } ?: return left
            val a = left
            val b = power()
            left = Expression.Node { c ->
                val x = required(a.eval(c))
                val y = required(b.eval(c))
                when (op) {
                    "*" -> x * y
                    "/" -> if (y == 0.0) throw ExpressionException("Division by zero") else x / y
                    else -> if (y == 0.0) throw ExpressionException("Division by zero") else x % y
                }
            }
        }
    }

    private fun power(): Expression.Node {
        val base = unary()
        if (!accept("^")) return base
        val exponent = power()
        return Expression.Node { c -> Math.pow(required(base.eval(c)), required(exponent.eval(c))) }
    }

    private fun unary(): Expression.Node {
        if (acceptExact("!", "!=")) {
            val inner = unary()
            return Expression.Node { c -> !truthy(inner.eval(c)) }
        }
        if (accept("-")) {
            val inner = unary()
            return Expression.Node { c -> -required(inner.eval(c)) }
        }
        return primary()
    }

    private fun primary(): Expression.Node {
        skipSpaces()
        if (position >= text.length) fail("The expression ends too early")
        val ch = text[position]
        return when {
            ch == '(' -> {
                position++
                val inner = ternary()
                if (!accept(")")) fail("Expected ')'")
                inner
            }
            ch.isDigit() || (ch == '.' && text.getOrNull(position + 1)?.isDigit() == true) -> numberLiteral()
            ch == '\'' || ch == '"' -> stringLiteral(ch)
            ch == '{' -> bracketed('}')
            ch == '[' -> bracketed(']')
            ch == '%' -> bracketed('%')
            ch.isLetter() || ch == '_' -> nameOrCall()
            else -> fail("Unexpected '$ch'")
        }
    }

    private fun numberLiteral(): Expression.Node {
        val start = position
        while (position < text.length && (text[position].isDigit() || text[position] == '.')) position++
        val value = text.substring(start, position).toDoubleOrNull() ?: run { position = start; fail("Bad number") }
        return Expression.Node { value }
    }

    private fun stringLiteral(quote: Char): Expression.Node {
        position++
        val start = position
        while (position < text.length && text[position] != quote) position++
        if (position >= text.length) fail("A text is not closed")
        val value = text.substring(start, position)
        position++
        return Expression.Node { value }
    }

    /** `{plugin:key}`, `[name]` or `%external%`: the whole token is read by the context, as in texts. */
    private fun bracketed(close: Char): Expression.Node {
        val start = position
        position++
        val end = text.indexOf(close, position)
        if (end < 0) { position = start; fail("Expected '$close'") }
        position = end + 1
        val token = text.substring(start, position)
        return Expression.Node { c -> normalize(c.read(token)) }
    }

    private fun nameOrCall(): Expression.Node {
        val start = position
        while (position < text.length && (text[position].isLetterOrDigit() || text[position] == '_' || text[position] == '.')) position++
        val name = text.substring(start, position)
        skipSpaces()
        if (position < text.length && text[position] == '(') {
            position++
            val args = ArrayList<Expression.Node>()
            if (!accept(")")) {
                do args += ternary() while (accept(","))
                if (!accept(")")) fail("Expected ')' after the arguments of $name()")
            }
            return Expression.Node { c ->
                val function = c.function(name) ?: Expression.builtin(name) ?: throw ExpressionException("Unknown function $name()")
                normalize(function.call(c, args.map { normalize(it.eval(c)) }))
            }
        }
        return when (name) {
            "true" -> Expression.Node { true }
            "false" -> Expression.Node { false }
            "null" -> Expression.Node { null }
            else -> Expression.Node { c -> normalize(c.read(name)) }
        }
    }

    private fun same(a: Any?, b: Any?): Boolean {
        val x = number(a)
        val y = number(b)
        if (x != null && y != null && a !is Boolean && b !is Boolean) return x == y
        return format(a) == format(b)
    }

    private fun compare(a: Any?, b: Any?): Int {
        val x = number(a)
        val y = number(b)
        return if (x != null && y != null) x.compareTo(y) else format(a).compareTo(format(b), ignoreCase = true)
    }
}
