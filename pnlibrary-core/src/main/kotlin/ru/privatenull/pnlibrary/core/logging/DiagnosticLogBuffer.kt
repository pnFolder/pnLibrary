package ru.privatenull.pnlibrary.core.logging

import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticRedactor
import java.time.Instant
import java.util.ArrayDeque

/** Bounded, redacted warning/error history used by `/pndebug --logs`. */
internal class DiagnosticLogBuffer(private val capacity: Int = 2_000) {
    private val entries = ArrayDeque<Map<String, Any?>>()
    private val redactor = DiagnosticRedactor()

    @Synchronized
    fun record(platform: PlatformAdapter, owner: Any, level: LogLevel, message: String, error: Throwable?) {
        if (level != LogLevel.WARNING && level != LogLevel.ERROR) return
        val ownerInfo = platform.ownerDetails(owner)
        val entry = linkedMapOf<String, Any?>(
            "timeUtc" to Instant.now().toString(),
            "level" to level.name,
            "plugin" to redactor.redact(ownerInfo["name"] ?: owner.javaClass.simpleName).take(96),
            "message" to redactor.redact(message).take(4_096),
        )
        if (error != null) entry["exception"] = format(error)
        entries.addLast(entry)
        while (entries.size > capacity.coerceAtLeast(1)) entries.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<Map<String, Any?>> = entries.map(::LinkedHashMap)

    private fun format(error: Throwable): String {
        val text = buildString {
            var current: Throwable? = error
            var causes = 0
            while (current != null && causes++ < 4) {
                append(current.javaClass.name).append(": ").append(current.message.orEmpty()).append('\n')
                current.stackTrace.take(24).forEach { append("  at ").append(it).append('\n') }
                current = current.cause
            }
        }
        return redactor.redact(text).take(16_384)
    }
}
