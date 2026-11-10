package org.pnfolder.pnlibrary.common.text

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

/**
 * Case-insensitive wildcard matching used by access policies: `*` matches any character sequence,
 * including an empty one, and every other character is matched literally.
 */
object Wildcards {
    private const val MAXIMUM_CACHED = 1024

    private val compiled = ConcurrentHashMap<String, Pattern>()
    private val compilations = AtomicInteger()

    /** How many patterns were compiled since start; used by tests to prove the cache works. */
    internal fun compilationCount(): Int = compilations.get()

    /** Returns whether the whole of [value] matches [pattern], ignoring case. */
    @JvmStatic
    fun matches(pattern: String, value: String): Boolean = patternFor(pattern).matcher(value).matches()

    // Patterns come from configuration, so the set is small; the cap only guards against a caller feeding unbounded text.
    private fun patternFor(pattern: String): Pattern {
        compiled[pattern]?.let { return it }
        if (compiled.size >= MAXIMUM_CACHED) compiled.clear()
        return compiled.computeIfAbsent(pattern) { compile(it) }
    }

    private fun compile(pattern: String): Pattern {
        compilations.incrementAndGet()
        val expression = pattern.split('*').joinToString(".*", transform = Pattern::quote)
        return Pattern.compile("^$expression$", Pattern.CASE_INSENSITIVE)
    }
}
