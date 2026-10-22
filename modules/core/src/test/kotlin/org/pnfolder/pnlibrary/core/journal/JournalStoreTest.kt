package org.pnfolder.pnlibrary.core.journal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class JournalStoreTest {
    @TempDir lateinit var directory: Path

    private val day = Instant.parse("2026-10-19T10:00:00Z")
    private val retention = mapOf(
        JournalLevel.INFO to Duration.ofDays(7),
        JournalLevel.WARNING to Duration.ofDays(30),
        JournalLevel.ERROR to Duration.ofDays(90),
    )

    private fun store(at: Instant = day) =
        JournalStore(directory, retention, Clock.fixed(at, ZoneOffset.UTC))

    private fun record(level: JournalLevel, source: String, message: String, at: Instant = day) =
        JournalRecord(at.toEpochMilli(), level, source, "LOG", message, mapOf("player" to "Steve"))

    @Test
    fun `records round trip newest first and filter by level and source`() {
        val journal = store()
        journal.append(record(JournalLevel.INFO, "shop", "opened", day))
        journal.append(record(JournalLevel.ERROR, "shop", "payout failed", day.plusSeconds(60)))
        journal.append(record(JournalLevel.WARNING, "pnlibrary", "slow disk", day.plusSeconds(30)))

        val all = journal.read()
        assertEquals(listOf("payout failed", "slow disk", "opened"), all.map { it.message })
        assertEquals(mapOf("player" to "Steve"), all.first().details)

        val errors = journal.read(levels = setOf(JournalLevel.ERROR))
        assertEquals(listOf("payout failed"), errors.map { it.message })

        val shop = journal.read(source = "shop")
        assertEquals(listOf("payout failed", "opened"), shop.map { it.message })
    }

    @Test
    fun `a damaged block is skipped and the rest of the file survives`() {
        val journal = store()
        journal.append(record(JournalLevel.ERROR, "shop", "first"))
        journal.append(record(JournalLevel.ERROR, "shop", "second"))
        val file = onlyFile("error")
        val bytes = Files.readAllBytes(file)
        bytes[12] = (bytes[12].toInt() xor 0x7F).toByte()
        Files.write(file, bytes)

        assertEquals(listOf("second"), journal.read(levels = setOf(JournalLevel.ERROR)).map { it.message })
    }

    @Test
    fun `an interrupted write at the end of the file leaves earlier records readable`() {
        val journal = store()
        journal.append(record(JournalLevel.ERROR, "shop", "complete"))
        val file = onlyFile("error")
        Files.write(file, byteArrayOf(0, 0, 1, 0, 9, 9), java.nio.file.StandardOpenOption.APPEND)

        assertEquals(listOf("complete"), journal.read(levels = setOf(JournalLevel.ERROR)).map { it.message })
    }

    @Test
    fun `retention removes old info but keeps errors that are still within their longer limit`() {
        store(day).apply {
            append(record(JournalLevel.INFO, "shop", "routine"))
            append(record(JournalLevel.ERROR, "shop", "important"))
        }

        val eightDaysLater = store(day.plus(Duration.ofDays(8)))
        assertEquals(1, eightDaysLater.prune())

        assertEquals(emptyList<JournalRecord>(), eightDaysLater.read(levels = setOf(JournalLevel.INFO)))
        assertEquals(listOf("important"), eightDaysLater.read(levels = setOf(JournalLevel.ERROR)).map { it.message })
        assertTrue(Files.exists(onlyFile("error")))
    }

    @Test
    fun `summary counts the last day per level and carries no message text`() {
        val journal = store()
        journal.append(record(JournalLevel.ERROR, "shop", "secret player data", day.minusSeconds(60)))
        journal.append(record(JournalLevel.ERROR, "shop", "another", day.minusSeconds(30)))
        journal.append(record(JournalLevel.ERROR, "shop", "too old", day.minus(Duration.ofHours(30))))

        @Suppress("UNCHECKED_CAST")
        val errors = journal.summary().getValue("error") as Map<String, Any?>

        assertEquals(2, errors["recordsLast24h"])
        assertEquals(90L, errors["retentionDays"])
        assertEquals(day.minusSeconds(30).toEpochMilli(), errors["newestAtMillis"])
        assertFalse(journal.summary().toString().contains("secret"))
    }

    @Test
    fun `a whole stack trace with its causes survives storage`() {
        val journal = store()
        val trace = JournalRecord.stackTraceOf(IllegalStateException("outer", RuntimeException("inner cause")))
        journal.append(JournalRecord(day.toEpochMilli(), JournalLevel.ERROR, "shop", "ERROR", "failed",
            mapOf("exception" to "IllegalStateException", JournalRecord.STACKTRACE to trace)))

        val stored = journal.read().single().details.getValue(JournalRecord.STACKTRACE)

        assertEquals(trace, stored)
        assertTrue(stored.contains("Caused by: java.lang.RuntimeException: inner cause"))
    }

    private fun onlyFile(group: String): Path =
        Files.list(directory).use { stream ->
            stream.filter { it.fileName.toString().startsWith("$group-") }
                .collect(java.util.stream.Collectors.toList()).single()
        }

    @Test
    fun `segments rotate and disk retention keeps the newest events`() {
        val journal = JournalStore(directory, retention, Clock.fixed(day, ZoneOffset.UTC),
            maximumSegmentBytes = 180, maximumDiskBytes = 700)
        repeat(40) { n -> journal.append(record(JournalLevel.INFO, "shop", "event-$n", day.plusSeconds(n.toLong()))) }
        val bytes = Files.list(directory).use { files -> files.mapToLong(Files::size).sum() }
        assertTrue(bytes <= 700)
        assertEquals(listOf("event-39", "event-38", "event-37"), journal.read(limit = 3).map { it.message })
    }

    @Test
    fun `streaming selection preserves newest timestamps even when records arrive out of order`() {
        val journal = store()
        listOf(3L, 1L, 5L, 2L, 4L).forEach { n ->
            journal.append(record(JournalLevel.INFO, "shop", "$n", day.plusSeconds(n)))
        }
        assertEquals(listOf("5", "4"), journal.read(limit = 2).map { it.message })
    }

    @Test
    fun `read budget exhaustion is visible instead of reading an entire oversized history`() {
        val journal = JournalStore(directory, retention, Clock.fixed(day, ZoneOffset.UTC), maximumReadBytes = 8)
        journal.append(record(JournalLevel.ERROR, "shop", "error"))
        assertTrue(journal.read().isEmpty())
        val errors = journal.summary().getValue("error") as Map<*, *>
        assertEquals(true, errors["readBudgetExhausted"])
        assertEquals(true, errors["countCapped"])
    }
}
