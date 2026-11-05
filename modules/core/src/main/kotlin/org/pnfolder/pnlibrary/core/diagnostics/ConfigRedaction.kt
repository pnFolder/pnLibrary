package org.pnfolder.pnlibrary.core.diagnostics

import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import java.util.regex.Pattern

/**
 * The values of one configuration file that a report must hide.
 *
 * [requested] is what an administrator (`excluded-paths`, key and value patterns) or the plugin (`@ConfigSecret`,
 * its own patterns) asked for; those promises hold in every report. [redact] adds the built-in secret key names,
 * the built-in text redactor and size limits for a plaintext report.
 */
internal class ConfigRedaction(
    private val excludedPaths: Set<String>,
    private val keyPatterns: List<Pattern>,
    private val valuePatterns: List<Pattern>,
    private val redactor: DiagnosticRedactor,
) {
    /** Whether anything was asked to be hidden in this file. */
    val hasRequests: Boolean get() = excludedPaths.isNotEmpty() || keyPatterns.isNotEmpty() || valuePatterns.isNotEmpty()

    /** Hides only the requested values; everything else stays exactly as parsed. */
    fun requested(data: Map<String, Any?>): Map<String, Any?> = hideRequested(data, "")

    /** Hides the requested values, every built-in secret and redactable text, within the plaintext size limits. */
    fun redact(data: Map<String, Any?>): Map<String, Any?> = redactTree(data, "", 0)

    private fun hideRequested(data: Map<String, Any?>, parent: String): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        for ((key, value) in data) {
            val path = if (parent.isEmpty()) key else "$parent.$key"
            result[key] = when {
                path in excludedPaths -> EXCLUDED
                matchesKeyPattern(key, path) -> SECRET_KEY
                else -> hideRequestedValue(value, path)
            }
        }
        return result
    }

    private fun hideRequestedValue(value: Any?, path: String): Any? = when (value) {
        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            hideRequested(value as Map<String, Any?>, path)
        }
        is List<*> -> value.map { item -> hideRequestedValue(item, path) }
        null, is Number, is Boolean -> value
        else -> replaceValuePatterns(value.toString())
    }

    private fun redactTree(data: Map<String, Any?>, parent: String, depth: Int): Map<String, Any?> {
        if (depth >= MAX_DEPTH) return linkedMapOf("[limit]" to "[depth limit reached]")
        val result = linkedMapOf<String, Any?>()
        for ((key, value) in data.entries.take(MAX_ENTRIES_PER_MAP)) {
            val path = if (parent.isEmpty()) key else "$parent.$key"
            result[key] = when {
                path in excludedPaths -> EXCLUDED
                isBuiltInSecret(key, path) || matchesKeyPattern(key, path) -> SECRET_KEY
                else -> redactValue(value, path, depth)
            }
        }
        return result
    }

    private fun redactValue(value: Any?, path: String, depth: Int): Any? = when (value) {
        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            redactTree(value as Map<String, Any?>, path, depth + 1)
        }
        is List<*> -> value.take(MAX_ENTRIES_PER_LIST).map { item -> redactValue(item, path, depth) }
        null, is Number, is Boolean -> value
        else -> replaceValuePatterns(redactor.redact(value.toString()))
    }

    private fun replaceValuePatterns(input: String): String = valuePatterns.fold(input) { text, pattern ->
        // A pattern that fails on this input hides the whole value: a report must never leak what it could not check.
        runCatchingNonFatal { pattern.matcher(text).replaceAll(SECRET_VALUE) }.getOrDefault(SECRET_VALUE)
    }

    private fun isBuiltInSecret(key: String, path: String): Boolean =
        BUILT_IN_SECRET_KEY.matcher(key).matches() || BUILT_IN_SECRET_KEY.matcher(path).matches()

    private fun matchesKeyPattern(key: String, path: String): Boolean = keyPatterns.any { pattern ->
        runCatchingNonFatal { pattern.matcher(key).find() || pattern.matcher(path).find() }.getOrDefault(true)
    }

    companion object {
        private const val MAX_DEPTH = 10
        private const val MAX_ENTRIES_PER_MAP = 200
        private const val MAX_ENTRIES_PER_LIST = 100
        private const val MAX_PATTERN_LENGTH = 256
        private const val EXCLUDED = "[EXCLUDED BY RULE]"
        private const val SECRET_KEY = "[REDACTED SECRET KEY]"
        private const val SECRET_VALUE = "[REDACTED VALUE]"

        private val BUILT_IN_SECRET_KEY = Pattern.compile(
            "(?i).*(?:password|passwd|pwd|secret|token|api[-_ ]?key|authorization|cookie|private[-_ ]?key|credential|webhook|mysql|auth|jdbc).*",
        )

        /** Compiles case-insensitive patterns; an invalid one is skipped so one typo cannot block every report. */
        fun compile(patterns: List<String>): List<Pattern> = patterns
            .filter { it.isNotBlank() && it.length <= MAX_PATTERN_LENGTH }
            .mapNotNull { runCatchingNonFatal { Pattern.compile(it, Pattern.CASE_INSENSITIVE) }.getOrNull() }
    }
}
