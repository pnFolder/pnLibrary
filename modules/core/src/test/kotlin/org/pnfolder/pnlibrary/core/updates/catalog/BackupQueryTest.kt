package org.pnfolder.pnlibrary.core.updates.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.updates.ProductId
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackupComponent
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class BackupQueryTest {
    private val now = Instant.parse("2026-10-31T12:00:00Z")

    private fun backup(at: String?, vararg products: String) = UpdateBackup(
        UUID.randomUUID(),
        at?.let(Instant::parse),
        null,
        products.map { UpdateBackupComponent(ProductId.of(it), "2.0.0", "1.0.0") },
    )

    private val old = backup("2026-10-01T10:00:00Z", "shop")
    private val middle = backup("2026-10-20T10:00:00Z", "pnlibrary", "clans")
    private val recent = backup("2026-10-30T10:00:00Z", "clans")
    private val undated = backup(null, "shop")
    private val all = listOf(old, middle, recent, undated)

    @Test
    fun `the default list is newest first and keeps every set`() {
        val shown = BackupQuery().apply(all)

        assertEquals(listOf(recent, middle, old, undated), shown)
        assertTrue(BackupQuery().isDefault)
    }

    @Test
    fun `oldest first puts the set without a date last`() {
        assertEquals(listOf(old, middle, recent, undated), BackupQuery(order = BackupOrder.OLDEST).apply(all))
    }

    @Test
    fun `a plugin keeps only the sets that contain it`() {
        assertEquals(listOf(recent, middle), BackupQuery(plugin = "CLANS").apply(all))
    }

    @Test
    fun `a start date drops older sets and those with no date`() {
        val since = BackupQuery.parseSince("7d", now, ZoneOffset.UTC)

        assertEquals(listOf(recent), BackupQuery(since = since, sinceText = "7d").apply(all))
    }

    @Test
    fun `sorting by plugin groups the sets by the first plugin name`() {
        val sorted = BackupQuery(order = BackupOrder.PLUGIN).apply(all)

        assertEquals(listOf(recent, middle, old, undated), sorted)
    }

    @Test
    fun `a date is read as days back or as a calendar day`() {
        assertEquals(Instant.parse("2026-10-24T12:00:00Z"), BackupQuery.parseSince("7d", now, ZoneOffset.UTC))
        assertEquals(Instant.parse("2026-10-15T00:00:00Z"), BackupQuery.parseSince("2026-10-15", now, ZoneOffset.UTC))
        assertNull(BackupQuery.parseSince("yesterday", now, ZoneOffset.UTC))
        assertNull(BackupQuery.parseSince("2026-13-45", now, ZoneOffset.UTC))
    }

    @Test
    fun `the command keeps the filters so a page button does not lose them`() {
        val query = BackupQuery(plugin = "clans", since = now, sinceText = "7d", order = BackupOrder.OLDEST)

        assertEquals("/pn updates backups plugin clans since 7d sort old", query.command("/pn updates backups"))
        assertEquals("/pn updates backups", BackupQuery().command("/pn updates backups"))
    }
}
