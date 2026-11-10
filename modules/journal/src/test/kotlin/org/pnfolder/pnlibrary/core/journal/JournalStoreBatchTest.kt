package org.pnfolder.pnlibrary.core.journal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class JournalStoreBatchTest {
    @TempDir lateinit var directory: Path

    private val day = Instant.parse("2026-10-19T10:00:00Z")
    private val retention = JournalLevel.values().associateWith { Duration.ofDays(30) }

    private fun corpus(count: Int) = List(count) { index ->
        JournalRecord(
            day.toEpochMilli() + index,
            JournalLevel.values()[index % 3],
            "src${index % 4}",
            "LOG",
            "message $index",
            if (index % 7 == 0) mapOf("exception" to "X", JournalRecord.STACKTRACE to "trace $index") else emptyMap(),
        )
    }

    private fun store(folder: Path, clock: Clock = Clock.fixed(day, ZoneOffset.UTC)) = JournalStore(folder, retention, clock)

    private fun throwing(): (JournalRecord, Throwable) -> Unit = { _, error -> throw error }

    @Test
    fun `a batch reads back exactly like the same records appended one by one`() {
        val one = Files.createDirectory(directory.resolve("one"))
        val batch = Files.createDirectory(directory.resolve("batch"))
        val records = corpus(900)
        store(one).apply { records.forEach(::append) }
        store(batch).appendAll(records, throwing())

        val expected = store(one).read(limit = 100_000)
        assertEquals(900, expected.size)
        assertEquals(expected, store(batch).read(limit = 100_000))
        val errors = setOf(JournalLevel.ERROR)
        assertEquals(store(one).read(levels = errors, limit = 50), store(batch).read(levels = errors, limit = 50))
    }

    @Test
    fun `blocks of the old one record format and new batches live in one file`() {
        store(directory).append(JournalRecord(day.toEpochMilli(), JournalLevel.INFO, "a", "LOG", "legacy"))
        val reopened = store(directory)
        reopened.appendAll(corpus(30).filter { it.level == JournalLevel.INFO }, throwing())

        assertEquals(1, Files.list(directory).use { it.count() })
        assertTrue(reopened.read(limit = 100).any { it.message == "legacy" })
        assertEquals(11, reopened.read(limit = 100).size)
    }

    @Test
    fun `a thousand records cost at most three listings and one block per chunk`() {
        val journal = store(directory)
        val records = corpus(1_000).map { it.copy(level = JournalLevel.INFO) }
        records.chunked(250).forEach { journal.appendAll(it, throwing()) }

        assertTrue(journal.directoryListings <= 3, "listings: ${journal.directoryListings}")
        assertTrue(journal.blocksWritten.get() <= 1_000 / 250, "blocks: ${journal.blocksWritten.get()}")
        assertEquals(1_000, journal.read(limit = 100_000).size)
    }

    @Test
    fun `single appends no longer list the folder per record`() {
        val journal = store(directory)
        corpus(1_000).map { it.copy(level = JournalLevel.INFO) }.forEach(journal::append)

        assertTrue(journal.directoryListings <= 3, "listings: ${journal.directoryListings}")
        assertEquals(1_000, journal.read(limit = 100_000).size)
    }

    @Test
    fun `a full segment rolls over to the next number and survives a restart`() {
        val clock = Clock.fixed(day, ZoneOffset.UTC)
        val small = JournalStore(directory, retention, clock, maximumSegmentBytes = 2_000)
        val records = corpus(600).map { it.copy(level = JournalLevel.WARNING) }
        records.chunked(20).forEach { small.appendAll(it, throwing()) }
        val restarted = JournalStore(directory, retention, clock, maximumSegmentBytes = 2_000)
        restarted.appendAll(listOf(records.first().copy(message = "after restart")), throwing())

        val names = Files.list(directory).use { stream -> stream.map { it.fileName.toString() }.sorted().toList() }
        assertTrue(names.size > 3, names.toString())
        assertEquals(601, restarted.read(limit = 100_000).size)
    }

    @Test
    fun `the disk budget is checked again after a minute without a new segment`() {
        var now = day.toEpochMilli()
        val clock = object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant(): Instant = Instant.ofEpochMilli(now)
        }
        val journal = JournalStore(directory, retention, clock, maximumSegmentBytes = 1_000_000, maximumDiskBytes = 1_000_000)
        val info = corpus(1).first().copy(level = JournalLevel.INFO)
        journal.append(info)
        val before = journal.directoryListings
        journal.append(info)
        assertEquals(before, journal.directoryListings)

        now += 61_000
        journal.append(info)
        assertEquals(before + 1, journal.directoryListings)
    }

    @Test
    fun `an oversized record is rejected without losing its neighbours`() {
        val journal = JournalStore(directory, retention, Clock.fixed(day, ZoneOffset.UTC), maximumSegmentBytes = 400, maximumDiskBytes = 400)
        val noise = java.util.Random(7)
        val incompressible = String(CharArray(4_000) { ('a' + noise.nextInt(26)) })
        val bad = JournalRecord(day.toEpochMilli(), JournalLevel.INFO, "a", "LOG", incompressible)
        val good = JournalRecord(day.toEpochMilli() + 1, JournalLevel.INFO, "a", "LOG", "small")
        val rejected = mutableListOf<JournalRecord>()
        journal.appendAll(listOf(good, bad)) { record, _ -> rejected += record }

        assertEquals(listOf("small"), journal.read().map { it.message })
        assertEquals(listOf(bad), rejected)
    }

    @Test
    fun `fingerprint keeps its old value and is computed once`() {
        val record = JournalRecord(1, JournalLevel.ERROR, "shop", "ERROR", "boom\nsecond line", mapOf("exception" to "java.lang.Boom"))
        val key = listOf("ERROR", "shop", "boom", "java.lang.Boom").joinToString("\u0000")
        assertEquals("%08x".format(key.hashCode()), record.fingerprint)
        assertSame(record.fingerprint, record.fingerprint)
        val short = JournalRecord(1, JournalLevel.INFO, "", "LOG", "")
        val shortKey = listOf("INFO", "", "", "").joinToString("\u0000")
        assertEquals("%08x".format(shortKey.hashCode()), short.fingerprint)
        repeat(300) { i ->
            val r = JournalRecord(i.toLong(), JournalLevel.WARNING, "s$i", "LOG", "m$i")
            val k = listOf("WARNING", "s$i", "m$i", "").joinToString("\u0000")
            assertEquals("%08x".format(k.hashCode()), r.fingerprint)
        }
    }

    @Test
    fun `join notice is built once for a wave of joins within the cache period`() {
        val journal = store(directory)
        journal.append(JournalRecord(day.toEpochMilli(), JournalLevel.ERROR, "shop", "ERROR", "payout failed"))
        var now = day.toEpochMilli() + 60_000
        val queries = JournalQueries(journal, PnLibraryConfig(), day.toEpochMilli(), "/pn events") { now }

        val first = queries.joinNotice()
        val listings = journal.directoryListings
        repeat(50) { assertSame(first, queries.joinNotice()) }
        assertEquals(listings, journal.directoryListings)

        now += 46_000
        queries.joinNotice()
        assertTrue(journal.directoryListings > listings)
    }
}
