package ru.privatenull.pnlibrary.api.diagnostics

import java.util.Collections

/**
 * Per-file collection and redaction contract declared by a contributing plugin.
 *
 * Use [DiagnosticConfiguration.builder] to obtain a [Builder].
 */
class DiagnosticConfiguration private constructor(builder: Builder) {

    /** Relative path inside the plugin data folder. Validated at construction time. */
    val path: String = validatePath(builder.path)

    /** Dotted key-paths whose entire subtrees are excluded from the report. */
    val excludedPaths: List<String> = immutable(builder.excludedPaths, 64, 256)

    /** Regex patterns — values whose key or full dotted-path matches are redacted. */
    val secretKeyPatterns: List<String> = immutable(builder.secretKeyPatterns, 32, 256)

    /** Regex patterns — matching fragments inside string values are replaced. */
    val valuePatterns: List<String> = immutable(builder.valuePatterns, 32, 256)

    /** Fluent builder for one configuration-file collection policy. */
    class Builder internal constructor(internal val path: String) {
        internal val excludedPaths: MutableList<String> = mutableListOf()
        internal val secretKeyPatterns: MutableList<String> = mutableListOf()
        internal val valuePatterns: MutableList<String> = mutableListOf()

        /** Omits this exact dotted path and all of its children from the report. */
        fun exclude(dottedPath: String): Builder = apply { excludedPaths.add(dottedPath) }

        /** Redacts values whose key or full dotted path matches the given Java regex. */
        fun secretKeyRegex(expression: String): Builder = apply { secretKeyPatterns.add(expression) }

        /** Replaces regex-matched fragments in string values with `[REDACTED BY PLUGIN]`. */
        fun redactValueRegex(expression: String): Builder = apply { valuePatterns.add(expression) }

        /** Validates the path and rules and creates an immutable configuration. */
        fun build(): DiagnosticConfiguration = DiagnosticConfiguration(this)
    }

    /** Entry points and validation rules for diagnostic file declarations. */
    companion object {
        /** Canonical entry point for the fluent builder. */
        @JvmStatic
        fun builder(path: String): Builder = Builder(path)

        /** Compatibility alias retained for the current major API line. */
        @Deprecated("Use builder(path)", ReplaceWith("builder(path)"))
        @JvmStatic
        fun file(path: String): Builder = builder(path)

        private val VALID_PATH = Regex("[A-Za-z0-9_./-]+\\.(?:yml|yaml|json|properties|toml|conf)")

        private fun validatePath(value: String?): String {
            if (value == null || value.startsWith("/") || value.contains("..") || !VALID_PATH.matches(value)) {
                throw IllegalArgumentException("Invalid diagnostic configuration path: $value")
            }
            return value
        }

        private fun immutable(source: List<String>, maxItems: Int, maxLength: Int): List<String> {
            val result = mutableListOf<String>()
            for (item in source) {
                require(!item.isNullOrBlank() && item.length <= maxLength && result.size < maxItems) {
                    "Invalid diagnostic redaction rule: $item"
                }
                result.add(item.trim())
            }
            return Collections.unmodifiableList(ArrayList(result))
        }
    }
}
