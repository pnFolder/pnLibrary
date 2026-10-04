package ru.privatenull.pnlibrary.core.metrics

import ru.privatenull.pnlibrary.api.metrics.ErrorReporter
import ru.privatenull.pnlibrary.api.metrics.TelemetryError
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local error pipeline that sanitizes and deduplicates events before forwarding them.
 * Remote providers are deliberately optional; local capture remains available when they are off.
 */
internal class ErrorPipeline(
    private val delegate: ErrorReporter? = null,
    private val maxStackFrames: Int = 80,
) : ErrorReporter {
    private val closed = AtomicBoolean(false)
    private val seen = ConcurrentHashMap.newKeySet<String>()

    override fun capture(error: TelemetryError) {
        if (closed.get()) return
        val safe = sanitize(error)
        val fingerprint = listOf(safe.type, safe.message, safe.stackTrace.joinToString("\n"), safe.operation)
            .joinToString("|")
        if (seen.add(fingerprint)) runCatching { delegate?.capture(safe) }
    }

    override fun installGlobalHandler() {
        delegate?.installGlobalHandler()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) runCatching { delegate?.close() }
    }

    private fun sanitize(error: TelemetryError): TelemetryError = error.copy(
        message = redact(error.message),
        stackTrace = error.stackTrace.take(maxStackFrames).map { redact(it).orEmpty() },
        attributes = error.attributes.mapValues { (_, value) -> redact(value?.toString()) },
    )

    private fun redact(value: String?): String? = value
        ?.replace(Regex("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]+"), "$1[redacted]")
        ?.replace(Regex("(?i)([?&](?:api_?key|token|secret)=)[^&\\s]+"), "$1[redacted]")
        ?.replace(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"), "[uuid]")
        ?.replace(Regex("(?i)(/home/|/Users/|C:\\\\Users\\\\)[^/\\\\\\s]+"), "$1[user]")
}
