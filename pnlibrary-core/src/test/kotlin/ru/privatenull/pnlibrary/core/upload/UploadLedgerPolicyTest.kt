package ru.privatenull.pnlibrary.core.upload

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class UploadLedgerPolicyTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private val clock = Clock.fixed(Instant.ofEpochSecond(1_000), ZoneOffset.UTC)

    @Test
    fun `uses current time when receipt has no creation timestamp`() {
        val file = temporaryDirectory.resolve("ledger.json")
        UploadLedger(file, clock).record(receipt(createdAt = 0), deleteAfterDays = 1)

        assertTrue(Files.readString(file).contains("\"deleteAt\":87400"))
    }

    @Test
    fun `record replaces an existing backend receipt instead of duplicating it`() {
        val file = temporaryDirectory.resolve("ledger.json")
        val ledger = UploadLedger(file, clock)

        ledger.record(receipt(createdAt = 100), deleteAfterDays = 1)
        ledger.record(receipt(createdAt = 200), deleteAfterDays = 2)

        val entries = UploadLedgerPersistence(file, clock).read()
        assertEquals(1, entries.size)
        assertEquals(173_000, entries.single().deleteAt)
    }

    @Test
    fun `cleanup deletes only entries whose schedule is due`() {
        val file = temporaryDirectory.resolve("ledger.json")
        val ledger = UploadLedger(file, clock)
        ledger.record(receipt(id = "due", createdAt = 100), deleteAfterDays = 0)
        val persistence = UploadLedgerPersistence(file, clock)
        persistence.write(
            listOf(
                entry(id = "due", deleteAt = 999),
                entry(id = "future", deleteAt = 1_001),
            ),
        )
        val deletedIds = mutableListOf<String>()
        val provider = object : UploadProvider {
            override val backendId = "test"
            override fun upload(payload: String): UploadReceipt = receipt()
            override fun delete(receipt: UploadReceipt): Boolean {
                deletedIds += receipt.id
                return true
            }
        }

        assertEquals(1, ledger.cleanup(provider))
        assertEquals(listOf("due"), deletedIds)
        assertEquals(listOf("future"), persistence.read().map { it.id })
    }

    @Test
    fun `corrupt ledger is quarantined before recovery`() {
        val file = temporaryDirectory.resolve("ledger.json")
        Files.writeString(file, "not-json")

        UploadLedger(file, clock).record(receipt(), deleteAfterDays = 1)

        val quarantined = temporaryDirectory.resolve("ledger.json.corrupt-1000")
        assertEquals("not-json", Files.readString(quarantined))
        assertTrue(Files.readString(file).contains("report"))
    }

    private fun receipt(
        id: String = "report",
        createdAt: Long = 1_000,
    ) = UploadReceipt(
        link = URI.create("https://example.com/$id"),
        id = id,
        deleteToken = "delete-token",
        createdEpochSeconds = createdAt,
        expiresEpochSeconds = 0,
        backend = "test",
    )

    private fun entry(id: String, deleteAt: Long) = UploadLedgerEntry(
        backend = "test",
        link = "https://example.com/$id",
        id = id,
        token = "delete-token",
        deleteAt = deleteAt,
    )
}
