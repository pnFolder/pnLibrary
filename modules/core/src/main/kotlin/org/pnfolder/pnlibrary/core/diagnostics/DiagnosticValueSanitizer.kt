package org.pnfolder.pnlibrary.core.diagnostics

import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticLevel
import java.io.PrintWriter
import java.io.StringWriter
import java.util.IdentityHashMap
import java.util.Locale

/**
 * Converts untrusted diagnostic values into bounded, redacted report data.
 *
 * Plugin contributors may return recursive containers, oversized strings, or
 * arbitrary objects. This class centralizes the limits applied at that trust
 * boundary so state storage never retains raw plugin objects or exceptions.
 */
internal class DiagnosticValueSanitizer(
    private val redactor: DiagnosticRedactor = DiagnosticRedactor(),
) {
    /** Returns a normalized map containing only report-safe values. */
    fun map(values: Map<*, *>?): Map<String, Any?> =
        map(values, depth = 0, seen = IdentityHashMap())

    /** Redacts and truncates [value] to at most [maximumLength] characters. */
    fun text(value: String?, maximumLength: Int): String =
        redactor.redact(value.orEmpty()).take(maximumLength)

    /** Normalizes a plugin or component identifier for case-insensitive lookup. */
    fun key(value: String?): String {
        val normalized = value?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return normalized.ifEmpty { "unknown" }.take(MAX_KEY_LENGTH)
    }

    /** Formats, redacts, and bounds an exception without retaining the throwable. */
    fun exception(error: Throwable): String {
        val writer = StringWriter()
        error.printStackTrace(PrintWriter(writer))
        val complete = redactor.redactStackTrace(writer.toString())
        if (complete.length <= MAX_EXCEPTION_CHARS) return complete
        return complete.take(MAX_EXCEPTION_CHARS) +
            "\n[TRUNCATED: throwable exceeded the hard $MAX_EXCEPTION_CHARS-character safety limit]"
    }

    /** Produces a stable short fingerprint for equivalent diagnostic incidents. */
    fun incidentId(
        plugin: String,
        level: DiagnosticLevel,
        component: String,
        code: String,
        message: String,
        error: Throwable?,
    ): String = DiagnosticThrowables.fingerprint(listOf(key(plugin), level.name, component, code), message, error)

    /** Returns the first useful application frame for an exception, if available. */
    fun exceptionOrigin(error: Throwable): Map<String, Any?>? = DiagnosticThrowables.origin(error)

    private fun map(
        values: Map<*, *>?,
        depth: Int,
        seen: IdentityHashMap<Any, Boolean>,
    ): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        if (values == null) return result
        if (depth >= MAX_DEPTH || seen.put(values, true) != null) {
            result["limit"] = "[recursive/depth limit]"
            return result
        }
        values.entries.take(MAX_CONTAINER_ENTRIES).forEach { (rawKey, rawValue) ->
            val key = text(rawKey?.toString(), MAX_FIELD_NAME_LENGTH)
            result[key] = if (SECRET_KEY_PATTERN.matches(key)) {
                "[REDACTED]"
            } else {
                value(rawValue, depth + 1, seen)
            }
        }
        seen.remove(values)
        return result
    }

    private fun value(value: Any?, depth: Int, seen: IdentityHashMap<Any, Boolean>): Any? {
        if (value == null || value is Number || value is Boolean) return value
        if (depth >= MAX_DEPTH) return "[depth limit]"
        if (value is Map<*, *>) return map(value, depth, seen)
        if (value is Iterable<*>) {
            if (seen.put(value, true) != null) return "[recursive reference]"
            val result = value.take(MAX_CONTAINER_ENTRIES).map { item ->
                value(item, depth + 1, seen)
            }
            seen.remove(value)
            return result
        }
        return text(value.toString(), MAX_VALUE_LENGTH)
    }

    private companion object {
        const val MAX_KEY_LENGTH = 96
        const val MAX_FIELD_NAME_LENGTH = 128
        const val MAX_VALUE_LENGTH = 4_096
        const val MAX_EXCEPTION_CHARS = 1_048_576
        const val MAX_DEPTH = 8
        const val MAX_CONTAINER_ENTRIES = 128

        val SECRET_KEY_PATTERN = Regex(
            "(?i).*(?:password|passwd|pwd|secret|token|api[-_ ]?key|authorization|cookie|private[-_ ]?key|credential).*",
        )
    }
}
