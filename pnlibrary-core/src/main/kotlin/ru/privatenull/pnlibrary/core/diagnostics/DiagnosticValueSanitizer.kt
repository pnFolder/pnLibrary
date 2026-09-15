package ru.privatenull.pnlibrary.core.diagnostics

import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticLevel
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
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
    ): String {
        val significant = unwrap(error)
        val frame = significant?.stackTrace?.firstOrNull(::isApplicationFrame)
            ?: significant?.stackTrace?.firstOrNull()
        val source = listOf(
            key(plugin), level.name, component, code, normalize(message),
            significant?.javaClass?.name.orEmpty(), normalize(significant?.message.orEmpty()),
            frame?.className.orEmpty(), frame?.methodName.orEmpty(),
        ).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(StandardCharsets.UTF_8))
            .take(INCIDENT_ID_BYTES)
            .joinToString("") { "%02x".format(it) }
    }

    /** Returns the first useful application frame for an exception, if available. */
    fun exceptionOrigin(error: Throwable): Map<String, Any?>? {
        val significant = unwrap(error) ?: error
        val frame = significant.stackTrace.firstOrNull(::isApplicationFrame)
            ?: significant.stackTrace.firstOrNull()
            ?: return null
        return linkedMapOf(
            "class" to frame.className,
            "method" to frame.methodName,
            "file" to frame.fileName,
            "line" to frame.lineNumber.takeIf { it >= 0 },
        )
    }

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

    private fun unwrap(error: Throwable?): Throwable? {
        var current = error ?: return null
        while ((current is java.util.concurrent.CompletionException ||
                current is java.util.concurrent.ExecutionException) && current.cause != null) {
            current = requireNotNull(current.cause)
        }
        return current
    }

    private fun isApplicationFrame(frame: StackTraceElement): Boolean =
        IGNORED_FRAME_PREFIXES.none(frame.className::startsWith)

    private fun normalize(value: String): String = value
        .replace(UUID_PATTERN, "<uuid>")
        .replace(LONG_NUMBER_PATTERN, "<number>")
        .replace(WHITESPACE_PATTERN, " ")
        .trim()

    private companion object {
        const val MAX_KEY_LENGTH = 96
        const val MAX_FIELD_NAME_LENGTH = 128
        const val MAX_VALUE_LENGTH = 4_096
        const val MAX_EXCEPTION_CHARS = 1_048_576
        const val MAX_DEPTH = 8
        const val MAX_CONTAINER_ENTRIES = 128
        const val INCIDENT_ID_BYTES = 8

        val UUID_PATTERN = Regex("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b")
        val LONG_NUMBER_PATTERN = Regex("\\b\\d{4,}\\b")
        val WHITESPACE_PATTERN = Regex("\\s+")
        val SECRET_KEY_PATTERN = Regex(
            "(?i).*(?:password|passwd|pwd|secret|token|api[-_ ]?key|authorization|cookie|private[-_ ]?key|credential).*",
        )
        val IGNORED_FRAME_PREFIXES = listOf(
            "java.", "javax.", "kotlin.", "kotlinx.", "sun.", "jdk.",
            "org.bukkit.", "net.minecraft.", "io.papermc.", "com.destroystokyo.paper.",
            "ru.privatenull.pnlibrary.",
        )
    }
}
