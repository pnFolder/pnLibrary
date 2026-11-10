package org.pnfolder.pnlibrary.core.placeholders

import java.util.concurrent.atomic.AtomicLong

/**
 * Matches a concrete placeholder key against a parameterized key pattern.
 *
 * Parameters use braces, for example `player.stat.{name}` matches
 * `player.stat.kills` and returns `name=kills`. A parameter consumes exactly
 * one dot-delimited segment; literal portions of a pattern are matched
 * verbatim.
 */
object PlaceholderPatternMatcher {
    private val parameter = Regex("\\{([a-zA-Z0-9_-]+)}")
    private val compilationCounter = AtomicLong()
    private val evaluationCounter = AtomicLong()

    /** How many patterns were compiled since start; tests use it to prove registration compiles once. */
    val compilations: Long get() = compilationCounter.get()

    /** How many times a compiled pattern was tried against a key; tests use it to count candidates. */
    val evaluations: Long get() = evaluationCounter.get()

    /** A pattern with its regular expression built once, at registration. */
    class Compiled internal constructor(
        private val names: List<String>,
        private val regex: Regex,
        /** How many characters of the pattern are fixed text; a pattern with more of them is the more specific one. */
        val specificity: Int,
    ) {
        /** Returns captured parameters, or `null` when [value] does not match. */
        fun match(value: String): Map<String, String>? {
            evaluationCounter.incrementAndGet()
            val result = regex.matchEntire(value) ?: return null
            return names.indices.associate { index -> names[index] to result.groupValues[index + 1] }
        }
    }

    /** Builds the matcher of [pattern]; `null` when the pattern has no parameter. */
    fun compile(pattern: String): Compiled? {
        val tokens = parameter.findAll(pattern).toList()
        if (tokens.isEmpty()) return null

        compilationCounter.incrementAndGet()
        var cursor = 0
        val expression = buildString {
            append('^')
            tokens.forEach { token ->
                append(Regex.escape(pattern.substring(cursor, token.range.first)))
                append("([^.]+)")
                cursor = token.range.last + 1
            }
            append(Regex.escape(pattern.substring(cursor)))
            append('$')
        }
        return Compiled(tokens.map { it.groupValues[1] }, Regex(expression), specificity(pattern))
    }

    /** How many characters of [pattern] are fixed text; a pattern with more of them is the more specific one. */
    fun specificity(pattern: String): Int = parameter.replace(pattern, "").length

    /** Returns captured parameters, or `null` when [value] does not match [pattern]. */
    fun match(pattern: String, value: String): Map<String, String>? = compile(pattern)?.match(value)
}
