package org.pnfolder.pnlibrary.core.diagnostics

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

// Shared rules for grouping repeated failures: the significant cause, its origin frame, and a stable fingerprint.

private const val FINGERPRINT_BYTES = 8
private const val HEX_DIGITS = "0123456789abcdef"
internal val UUID_REGEX = Regex("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b")
private val LONG_NUMBER_PATTERN = Regex("\\b\\d{4,}\\b")
private val WHITESPACE_PATTERN = Regex("\\s+")
private val IGNORED_FRAME_PREFIXES = listOf(
    "java.", "javax.", "kotlin.", "kotlinx.", "sun.", "jdk.",
    "org.bukkit.", "net.minecraft.", "io.papermc.", "com.destroystokyo.paper.",
    "org.pnfolder.pnlibrary.",
)

/** Strips asynchronous wrappers so the failure that actually happened is reported. */
internal fun Throwable?.significantCause(): Throwable? {
    var current = this ?: return null
    while (current is CompletionException || current is ExecutionException) {
        current = current.cause ?: break
    }
    return current
}

/** Returns the first frame outside the JDK, the server and pnLibrary, or the top frame when there is none. */
internal fun Throwable?.originFrame(): StackTraceElement? {
    val trace = significantCause()?.stackTrace ?: return null
    return trace.firstOrNull(::isApplicationFrame) ?: trace.firstOrNull()
}

/** Describes [originFrame] of this error as report data, or returns `null` for an empty stack trace. */
internal fun Throwable.originData(): Map<String, Any?>? {
    val frame = originFrame() ?: return null
    return linkedMapOf(
        "class" to frame.className,
        "method" to frame.methodName,
        "file" to frame.fileName,
        "line" to frame.lineNumber.takeIf { it >= 0 },
    )
}

/**
 * Fingerprints this failure (or none) from its [context], message and significant cause.
 *
 * Identifiers and long numbers are masked, so repeats of one problem share an id.
 */
internal fun Throwable?.fingerprint(context: List<String>, message: String): String {
    val significant = significantCause()
    val frame = originFrame()
    val source = (context + listOf(
        normalize(message),
        significant?.javaClass?.name.orEmpty(),
        normalize(significant?.message.orEmpty()),
        frame?.className.orEmpty(),
        frame?.methodName.orEmpty(),
    )).joinToString("\u0000")
    val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(StandardCharsets.UTF_8))
    val hex = CharArray(FINGERPRINT_BYTES * 2)
    for (index in 0 until FINGERPRINT_BYTES) {
        val value = digest[index].toInt() and 0xFF
        hex[index * 2] = HEX_DIGITS[value ushr 4]
        hex[index * 2 + 1] = HEX_DIGITS[value and 0x0F]
    }
    return String(hex)
}

private fun isApplicationFrame(frame: StackTraceElement): Boolean =
    IGNORED_FRAME_PREFIXES.none(frame.className::startsWith)

private fun normalize(value: String): String = value
    .replace(UUID_REGEX, "<uuid>")
    .replace(LONG_NUMBER_PATTERN, "<number>")
    .replace(WHITESPACE_PATTERN, " ")
    .trim()
