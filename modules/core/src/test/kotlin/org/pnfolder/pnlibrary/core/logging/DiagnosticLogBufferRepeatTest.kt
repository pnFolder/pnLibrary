package org.pnfolder.pnlibrary.core.logging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticRedactor
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticThrowables
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class DiagnosticLogBufferRepeatTest {
    private val platform = object : PlatformOwnership {
        override fun ownerDetails(owner: Any) = mapOf("name" to "shop")
    }

    private class Counting {
        val redacts = AtomicInteger()
        val fingerprints = AtomicInteger()
        private val redactor = DiagnosticRedactor()

        fun buffer() = DiagnosticLogBuffer(
            capacity = 100,
            redact = { redacts.incrementAndGet(); redactor.redact(it) },
            fingerprintOf = { context, message, error ->
                fingerprints.incrementAndGet()
                DiagnosticThrowables.fingerprint(context, message, error)
            },
        )
    }

    private fun cause(message: String, method: String) = IllegalStateException(message).apply {
        stackTrace = arrayOf(StackTraceElement("ru.shop.Menu", method, "Menu.kt", 10))
    }

    /** What the buffer must report: every record redacted and fingerprinted on its own, as the old code did. */
    private fun expected(records: List<Triple<LogLevel, String, Throwable?>>): Map<String, Pair<String, Long>> {
        val redactor = DiagnosticRedactor()
        val result = LinkedHashMap<String, Pair<String, Long>>()
        for ((level, message, error) in records) {
            val safe = redactor.redact(message).take(4_096)
            val id = DiagnosticThrowables.fingerprint(listOf(redactor.redact("shop").take(96), level.name), safe, error)
            result.merge(id, safe to 1L) { old, _ -> old.first to old.second + 1 }
        }
        return result
    }

    @Test
    fun `ten thousand identical records redact and fingerprint once`() {
        val counting = Counting()
        val buffer = counting.buffer()
        val error = cause("database unavailable", "load")

        repeat(10_000) { buffer.record(platform, this, LogLevel.ERROR, "Load failed", error) }

        // plugin name and message are redacted together on the first sight only
        assertEquals(2, counting.redacts.get())
        assertEquals(1, counting.fingerprints.get())
        val incident = buffer.snapshot().single()
        assertEquals(10_000L, (incident["occurrences"] as Map<*, *>)["count"])
    }

    @Test
    fun `repeats with different ids and numbers still fold into the same incidents as before`() {
        val records = ArrayList<Triple<LogLevel, String, Throwable?>>()
        repeat(300) { index ->
            records += Triple(LogLevel.WARNING, "Player ${UUID(index.toLong(), 7)} lost ${10_000 + index} coins", null)
            records += Triple(LogLevel.ERROR, "Load failed", cause("db down", "load"))
            records += Triple(LogLevel.ERROR, "Load failed", cause("db down ${100_000 + index}", "load"))
            records += Triple(LogLevel.ERROR, "Load failed", cause("db down", "save"))
            records += Triple(LogLevel.WARNING, "token=secret$index at https://example.invalid/$index", null)
            records += Triple(LogLevel.ERROR, "Other", null)
        }
        val counting = Counting()
        val buffer = counting.buffer()
        records.forEach { (level, message, error) -> buffer.record(platform, this, level, message, error) }

        val want = expected(records)
        val got = buffer.snapshot().associate {
            it["incidentId"] as String to ((it["message"] as String) to ((it["occurrences"] as Map<*, *>)["count"] as Long))
        }
        assertEquals(want, got)
        assertTrue(want.size >= 5, "corpus must produce several incidents: ${want.size}")
        // identical records skip the SHA-256 step: the old code fingerprinted every one of the 1800 records
        assertTrue(counting.fingerprints.get() <= records.size / 2 + 10, "fingerprints: ${counting.fingerprints.get()}")
    }

    @Test
    fun `fingerprint hex matches the old formatting`() {
        val old = { context: List<String>, message: String, error: Throwable? ->
            val significant = DiagnosticThrowables.unwrap(error)
            val frame = DiagnosticThrowables.originFrame(error)
            val normalize = { v: String ->
                v.replace(Regex("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b"), "<uuid>")
                    .replace(Regex("\\b\\d{4,}\\b"), "<number>").replace(Regex("\\s+"), " ").trim()
            }
            val source = (context + listOf(
                normalize(message), significant?.javaClass?.name.orEmpty(), normalize(significant?.message.orEmpty()),
                frame?.className.orEmpty(), frame?.methodName.orEmpty(),
            )).joinToString("\u0000")
            MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8)).take(8)
                .joinToString("") { "%02x".format(it) }
        }
        repeat(200) { i ->
            val error = if (i % 2 == 0) cause("m$i", "f$i") else null
            val context = listOf("plugin$i", "ERROR")
            assertEquals(old(context, "message $i", error), DiagnosticThrowables.fingerprint(context, "message $i", error))
        }
    }

    @Test
    fun `memory of raw keys stays bounded when every message is unique`() {
        val buffer = DiagnosticLogBuffer(capacity = 50)
        repeat(20_000) { buffer.record(platform, this, LogLevel.WARNING, "unique message number-$it x", null) }

        assertEquals(50, buffer.snapshot().size)
    }
}
