package ru.privatenull.pnlibrary.core.diagnostics

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

/** Shared rules for grouping repeated failures: the significant cause, its origin frame, and a stable fingerprint. */
internal object DiagnosticThrowables {
    private const val FINGERPRINT_BYTES = 8
    private val UUID_PATTERN = Regex("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b")
    private val LONG_NUMBER_PATTERN = Regex("\\b\\d{4,}\\b")
    private val WHITESPACE_PATTERN = Regex("\\s+")
    private val IGNORED_FRAME_PREFIXES = listOf(
        "java.", "javax.", "kotlin.", "kotlinx.", "sun.", "jdk.",
        "org.bukkit.", "net.minecraft.", "io.papermc.", "com.destroystokyo.paper.",
        "ru.privatenull.pnlibrary.",
    )

    /** Strips asynchronous wrappers so the failure that actually happened is reported. */
    fun unwrap(error: Throwable?): Throwable? {
        var current = error ?: return null
        while (current is CompletionException || current is ExecutionException) {
            current = current.cause ?: break
        }
        return current
    }

    /** Returns the first frame outside the JDK, the server and pnLibrary, or the top frame when there is none. */
    fun originFrame(error: Throwable?): StackTraceElement? {
        val trace = unwrap(error)?.stackTrace ?: return null
        return trace.firstOrNull(::isApplicationFrame) ?: trace.firstOrNull()
    }

    /** Describes [originFrame] of [error] as report data, or returns `null` for an empty stack trace. */
    fun origin(error: Throwable): Map<String, Any?>? {
        val frame = originFrame(error) ?: return null
        return linkedMapOf(
            "class" to frame.className,
            "method" to frame.methodName,
            "file" to frame.fileName,
            "line" to frame.lineNumber.takeIf { it >= 0 },
        )
    }

    /**
     * Fingerprints a failure from its [context], message and significant cause.
     *
     * Identifiers and long numbers are masked, so repeats of one problem share an id.
     */
    fun fingerprint(context: List<String>, message: String, error: Throwable?): String {
        val significant = unwrap(error)
        val frame = originFrame(error)
        val source = (context + listOf(
            normalize(message),
            significant?.javaClass?.name.orEmpty(),
            normalize(significant?.message.orEmpty()),
            frame?.className.orEmpty(),
            frame?.methodName.orEmpty(),
        )).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(StandardCharsets.UTF_8))
            .take(FINGERPRINT_BYTES)
            .joinToString("") { "%02x".format(it) }
    }

    private fun isApplicationFrame(frame: StackTraceElement): Boolean =
        IGNORED_FRAME_PREFIXES.none(frame.className::startsWith)

    private fun normalize(value: String): String = value
        .replace(UUID_PATTERN, "<uuid>")
        .replace(LONG_NUMBER_PATTERN, "<number>")
        .replace(WHITESPACE_PATTERN, " ")
        .trim()
}
